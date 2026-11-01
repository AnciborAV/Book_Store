import assert from 'node:assert/strict';
import { test, after } from 'node:test';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = process.argv[2] ? pathToFileURL(resolve(process.argv[2]) + '/')
  : new URL('../src/main/resources/static/', import.meta.url);
const { createAccountData } = await import(new URL('account-data.js', root));
const { createAccountView } = await import(new URL('account-view.js', root));
const { setupAccount } = await import(new URL('account-controller.js', root));
const NativeFormData = globalThis.FormData;
class TestFormData {
  constructor(form) { this.entries = Object.entries(form.values || {}); }
  [Symbol.iterator]() { return this.entries[Symbol.iterator](); }
}
globalThis.FormData = TestFormData;
after(() => { globalThis.FormData = NativeFormData; });

function fixture() {
  let user = null, profile = { email: 'reader@example.org', displayName: 'Читатель' }, ready = true, busy = false;
  let failure;
  const events = [], notices = [];
  let handlers;
  const call = async (name, value) => {
    events.push([name, value]);
    if (failure === name) throw Error('Ошибка ' + name);
  };
  const data = {
    async loadProfile() { await call('profile'); return profile; },
    register: values => call('register', values), login: values => call('login', values),
    renewCsrf: () => call('csrf'), saveAvatar: form => call('avatar', form), logout: () => call('logout'),
  };
  const view = {
    bind(value) { handlers = value; }, open: value => events.push(['open', value]),
    renderProfile: value => events.push(['render', value]), renderRegistration: value => events.push(['registration', value]),
    setAuthBusy: value => events.push(['authBusy', value]), setAuthError: value => events.push(['authError', value]),
    completeAuth(form) { form.reset(); events.push(['authComplete']); },
    setAvatarMessage: value => events.push(['avatarMessage', value]), closeProfile: () => events.push(['closeProfile']),
  };
  const controller = setupAccount({ data, view, getUser: () => user, setUser: value => { user = value; },
    isSelectionReady: () => ready, isSelectionBusy: () => busy,
    loadSelection: () => call('selection'), onProfileChange: () => events.push(['updated']), toast: t => notices.push(t) });
  return { controller, handlers, events, notices, get user() { return user; },
    setUser: value => { user = value; }, setProfile: value => { profile = value; },
    setReady: value => { ready = value; }, setBusy: value => { busy = value; }, fail: value => { failure = value; } };
}
function form(values = {}) {
  const button = { disabled: false };
  const target = { values, resetCount: 0, reset() { this.resetCount++; },
    querySelector: () => button, file: { files: [] } };
  return { target, button, preventDefault() {} };
}

 test('profile transport distinguishes guests, successful responses and server errors', async () => {
  for (const [response, expected] of [ [{ status: 401, ok: false }, null],
    [{ status: 200, ok: true, json: async () => ({ email: 'a' }) }, { email: 'a' }] ]) {
    const data = createAccountData({ fetchProfile: async () => response });
    assert.deepEqual(await data.loadProfile(), expected);
  }
  const failed = createAccountData({ fetchProfile: async () => ({ status: 500, ok: false }) });
  await assert.rejects(failed.loadProfile(), /Не удалось загрузить профиль/);
  const network = createAccountData({ fetchProfile: async () => { throw Error('network'); } });
  await assert.rejects(network.loadProfile(), /network/);
});

test('transport retains registration JSON, login normalization and remember-me values', async () => {
  const calls = [];
  const data = createAccountData({ send: async (...args) => calls.push(args) });
  const values = { email: ' Reader@Example.ORG ', password: ' Pass ', displayName: 'Имя', 'remember-me': 'true' };
  await data.register(values); await data.login(values); await data.login({ ...values, 'remember-me': undefined });
  assert.deepEqual(calls[0], ['/api/auth/register', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(values) }]);
  assert.equal(calls[1][0], '/api/auth/login'); assert.equal(calls[1][1].method, 'POST');
  assert.deepEqual(Object.fromEntries(calls[1][1].body), { email: 'reader@example.org', password: ' Pass ', 'remember-me': 'true' });
  assert.equal(calls[2][1].body.get('remember-me'), 'false');
});

test('CSRF renewal and logout preserve order and do not reset the token on failed logout', async () => {
  const events = [];
  const data = createAccountData({ send: async () => events.push('logout'),
    clearCsrf: () => events.push('reset'), refreshCsrf: async () => events.push('refresh') });
  await data.renewCsrf(); await data.logout();
  assert.deepEqual(events, ['reset', 'refresh', 'logout', 'reset']);
  const failed = createAccountData({ send: async () => { throw Error('denied'); }, clearCsrf: () => events.push('unexpected') });
  await assert.rejects(failed.logout(), /denied/); assert.equal(events.includes('unexpected'), false);
  const body = new NativeFormData(); let sent;
  await createAccountData({ send: async (...args) => { sent = args; } }).saveAvatar(body);
  assert.equal(sent[0], '/api/profile/avatar'); assert.equal(sent[1].method, 'PUT'); assert.equal(sent[1].body, body);
});

test('profile load refreshes dependent UI before selection and avoids reloading a ready unchanged account', async () => {
  const f = fixture(); await f.controller.loadProfile();
  assert.deepEqual(f.events.map(e => e[0]), ['profile', 'render', 'updated', 'selection']);
  f.events.length = 0; await f.controller.loadProfile();
  assert.deepEqual(f.events.map(e => e[0]), ['profile', 'render', 'updated']);
  f.events.length = 0; f.setReady(false); await f.controller.loadProfile();
  assert.equal(f.events.at(-1)[0], 'selection');
  f.events.length = 0; f.setReady(true); f.setProfile(null); await f.controller.loadProfile();
  assert.equal(f.user, null); assert.equal(f.events.at(-1)[0], 'selection');
});

test('failed profile retrieval preserves the previous account and does not redraw', async () => {
  const f = fixture(); const user = { email: 'old' }; f.setUser(user); f.fail('profile');
  await assert.rejects(f.controller.loadProfile(), /Ошибка profile/);
  assert.equal(f.user, user); assert.deepEqual(f.events.map(e => e[0]), ['profile']);
});

test('opening and switching authentication mode delegates to the matching view', () => {
  const f = fixture(); f.handlers.open(); f.setUser({ email: 'a' }); f.handlers.open();
  f.handlers.toggle(); f.handlers.toggle();
  assert.deepEqual(f.events, [['open', null], ['open', { email: 'a' }], ['registration', true], ['registration', false]]);
});

test('registration logs in, renews CSRF, loads the account and only then completes the form', async () => {
  const f = fixture(), event = form({ email: 'a', password: 'b' });
  f.handlers.toggle(); f.events.length = 0;
  await f.handlers.submitAuth(event);
  assert.deepEqual(f.events.map(e => e[0]), ['authBusy','authError','register','login','csrf','profile','render','updated','selection','authComplete','authBusy']);
  assert.equal(event.target.resetCount, 1); assert.equal(f.notices.at(-1), 'Аккаунт создан');
  assert.deepEqual(f.events.at(-1), ['authBusy', false]);
});

test('login failures at each step stop later operations, keep the form and release the submit button', async () => {
  for (const step of ['register', 'login', 'csrf', 'profile', 'selection']) {
    const f = fixture(), event = form({ email: 'a', password: 'b' });
    f.handlers.toggle(); f.events.length = 0; f.fail(step);
    await f.handlers.submitAuth(event);
    assert.equal(event.target.resetCount, 0); assert.equal(f.notices.length, 0);
    assert.deepEqual(f.events.at(-2), ['authError', 'Ошибка ' + step]);
    assert.deepEqual(f.events.at(-1), ['authBusy', false]);
    assert.equal(f.events.some(e => e[0] === 'authComplete'), false);
  }
  const f = fixture(); await f.handlers.submitAuth(form({ email: 'a', password: 'b' }));
  assert.equal(f.events.some(e => e[0] === 'register'), false);
  assert.equal(f.notices.at(-1), 'Вы вошли в аккаунт');
});

test('avatar size validation preserves the exact 2 MiB boundary and prevents invalid requests', async () => {
  for (const size of [undefined, 2 * 1024 * 1024 + 1]) {
    const f = fixture(), event = form();
    event.target.file.files = size === undefined ? [] : [{ size }];
    await f.handlers.submitAvatar(event);
    assert.deepEqual(f.events, [['avatarMessage', ''], ['avatarMessage', 'Выберите PNG или JPEG до 2 МБ']]);
    assert.equal(event.button.disabled, false);
  }
  const f = fixture(), event = form(); event.target.file.files = [{ size: 2 * 1024 * 1024 }];
  await f.handlers.submitAvatar(event);
  assert.equal(event.target.resetCount, 1); assert.equal(event.button.disabled, false);
  assert.deepEqual(f.events.map(e => e[0]), ['avatarMessage','avatar','profile','render','updated','selection','avatarMessage']);
  assert.deepEqual(f.events.at(-1), ['avatarMessage', 'Аватарка сохранена']);
});

test('avatar failures preserve the form and restore the upload button', async () => {
  for (const step of ['avatar', 'profile', 'selection']) {
    const f = fixture(), event = form(); event.target.file.files = [{ size: 1 }]; f.fail(step);
    await f.handlers.submitAvatar(event);
    assert.equal(event.target.resetCount, 0); assert.equal(event.button.disabled, false);
    assert.deepEqual(f.events.at(-1), ['avatarMessage', 'Ошибка ' + step]);
  }
});

test('logout is blocked during selection saving and closes the profile only after successful reload', async () => {
  const f = fixture(); f.setBusy(true); await f.handlers.logout();
  assert.deepEqual(f.events, []); assert.equal(f.notices.at(-1), 'Дождитесь сохранения выбора');
  f.setBusy(false); f.setUser({ email: 'a' }); f.setProfile(null); await f.handlers.logout();
  assert.equal(f.user, null);
  assert.deepEqual(f.events.map(e => e[0]), ['logout','profile','render','updated','selection','closeProfile']);
  assert.equal(f.notices.at(-1), 'Вы вышли из аккаунта');
  for (const step of ['logout','profile','selection']) {
    const g = fixture(); g.setUser({ email: 'a' }); g.setProfile(null); g.fail(step); await g.handlers.logout();
    assert.equal(g.events.some(e => e[0] === 'closeProfile'), false);
    assert.equal(g.notices.at(-1), 'Ошибка ' + step);
  }
});

test('view retains labels, safe text, avatar refresh, registration fields and dialog transitions', () => {
  class Element {
    constructor() { this.children = []; this.attributes = {}; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(key, value) { this.attributes[key] = value; }
    showModal() { this.open = true; }
    close() { this.open = false; }
  }
  const nodes = new Map(); const $ = key => { if (!nodes.has(key)) nodes.set(key, new Element()); return nodes.get(key); };
  const view = createAccountView({ query: $, createElement: () => new Element(), now: () => 123 });
  const handler = () => {}; view.bind({open:handler, toggle:handler, submitAuth:handler, submitAvatar:handler, logout:handler});
  assert.equal($('#auth-form').onsubmit, handler); assert.equal($('#logout').onclick, handler);
  view.renderProfile(null); assert.equal($('#account-label').textContent, 'Войти');
  assert.equal($('#account .avatar').textContent, 'Ч');
  view.renderProfile({ displayName: '<b>Имя</b>', email: 'a', hasAvatar: true });
  assert.equal($('#profile-name').textContent, '<b>Имя</b>');
  assert.equal($('#account .avatar').children[0].src, '/api/profile/avatar?t=123');
  assert.equal($('#profile-avatar').children[0].alt, 'Аватарка пользователя');
  view.renderProfile({ displayName: 'андрей', email: 'a', hasAvatar: false });
  assert.equal($('#account .avatar').textContent, 'А'); assert.equal($('#account .avatar').children.length, 0);
  view.renderRegistration(true); assert.equal($('#name-field input').required, true);
  assert.equal($('#auth-form [name=password]').autocomplete, 'new-password'); assert.equal($('#forgot-password').hidden, true);
  view.renderRegistration(false); assert.equal($('#name-field').hidden, true);
  assert.equal($('#auth-form [name=password]').autocomplete, 'current-password');
  view.open(null); assert.equal($('#auth-dialog').open, true);
  const event = form(); view.completeAuth(event.target);
  assert.equal(event.target.resetCount, 1); assert.equal($('#auth-dialog').open, false); assert.equal($('#profile-dialog').open, true);
  view.setAuthBusy(true); view.setAuthError('Ошибка'); view.setAvatarMessage('Ответ'); view.closeProfile();
  assert.equal($('#auth-submit').disabled, true); assert.equal($('#auth-error').textContent, 'Ошибка');
  assert.equal($('#avatar-message').textContent, 'Ответ'); assert.equal($('#profile-dialog').open, false);
});
