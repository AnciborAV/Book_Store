import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = process.argv[2] ? pathToFileURL(resolve(process.argv[2]) + '/')
  : new URL('../src/main/resources/static/', import.meta.url);
const { createSelectionData } = await import(new URL('selection-data.js', root));
const { createSelectionState } = await import(new URL('selection-state.js', root));
const { cartRows, cartSummary, cartCount, nextCartQuantity } = await import(new URL('cart-model.js', root));
const storage = (initial = {}) => {
  const values = new Map(Object.entries(initial));
  return { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value), values };
};
function fixture(initial = {}, initialUser = null) {
  const local = storage(initial), calls = [], notices = [], events = [];
  let user = initialUser, response = { cart: { 2: 3 }, favorites: [2], libraryFavorites: ['pdf'] };
  const data = createSelectionData({ getStorage: () => local, send: async (...args) => {
    calls.push(args);
    if (response instanceof Error) throw response;
    return response;
  } });
  let state;
  state = createSelectionState({ data, getUser: () => user, notify: text => notices.push(text),
    onReset: () => events.push({ event: 'reset', cart: { ...state.cart }, ready: state.ready }),
    onChange: () => events.push({ event: 'change', cart: { ...state.cart }, ready: state.ready, busy: state.busy }) });
  return { state, data, local, calls, notices, events, login: value => { user = value; },
    respond: value => { response = value; } };
}

test('guest restoration retains quantity limits and separate favorite formats', () => {
  const f = fixture({ 'bookstore-cart': '{"1":1,"2":99,"3":100,"4":0,"5":1.5,"x":2,"6":"2"}',
    'bookstore-favorites': '[1,"2"]', 'bookstore-library-favorites': '[1,"pdf"]' });
  assert.deepEqual(f.state.cart, { 1: 1, 2: 99 });
  assert.deepEqual([...f.state.favorites], ['1', '2']);
  assert.deepEqual([...f.state.libraryFavorites], ['pdf']);
  assert.equal(f.state.ready, false);
});

test('corrupt or unavailable storage does not prevent loading the guest selection', async () => {
  const f = fixture({ 'bookstore-cart': 'null', 'bookstore-favorites': '{', 'bookstore-library-favorites': '{}' });
  await f.state.load();
  assert.deepEqual(f.state.cart, {});
  const data = createSelectionData({ getStorage: () => { throw Error('denied'); } });
  assert.deepEqual(data.loadGuest().cart, {});
  const notices = [];
  const state = createSelectionState({ data, getUser: () => null, notify: t => notices.push(t), onReset() {}, onChange() {} });
  await state.load(); await state.add({ id: 1, name: 'Книга' });
  assert.equal(state.cart[1], 1);
  assert.deepEqual(notices, ['Браузер не разрешает сохранение гостевого выбора', '«Книга» добавлена в корзину']);
});

test('guest mutations save all three storage keys without network requests', async () => {
  const f = fixture(); await f.state.load();
  await f.state.add({ id: 1, name: 'Книга' });
  await f.state.setQuantity(1, 99); await f.state.add({ id: 1, name: 'Книга' });
  assert.equal(f.state.count, 99);
  await f.state.toggleFavorite({ id: 1 }); await f.state.toggleFavorite({ id: 1 }, true);
  assert.equal(f.local.values.get('bookstore-cart'), '{"1":99}');
  assert.equal(f.local.values.get('bookstore-favorites'), '["1"]');
  assert.equal(f.local.values.get('bookstore-library-favorites'), '["1"]');
  await f.state.setQuantity(1, 0);
  assert.equal(f.state.count, 0);
  await f.state.add({ id: 2, name: 'Другая' }); await f.state.clear();
  assert.deepEqual(f.state.cart, {});
  assert.equal(f.state.favorites.has('1'), true);
  assert.equal(f.calls.length, 0);
});

test('login replaces guest selection with the account and logout restores guest storage', async () => {
  const f = fixture({ 'bookstore-cart': '{"1":2}' }); await f.state.load();
  f.login({ email: 'a' }); await f.state.load();
  assert.deepEqual(f.state.cart, { 2: 3 });
  assert.deepEqual(f.events.at(-2), { event: 'reset', cart: {}, ready: false });
  assert.equal(f.state.favorites.has('2'), true);
  assert.deepEqual(f.calls[0], ['/api/selection']);
  assert.equal(f.local.values.get('bookstore-cart'), '{"1":2}');
  f.login(null); await f.state.load();
  assert.deepEqual(f.state.cart, { 1: 2 });
});

test('failed account load leaves selection empty and mutations blocked until reloaded', async () => {
  const f = fixture({ 'bookstore-cart': '{"1":2}' }, { email: 'a' });
  f.respond(Error('Нет связи'));
  await assert.rejects(f.state.load(), /Нет связи/);
  assert.deepEqual(f.state.cart, {}); assert.equal(f.state.ready, false);
  await f.state.add({ id: 1, name: 'Книга' });
  assert.equal(f.calls.length, 1);
  assert.match(f.notices.at(-1), /Выбор ещё не загружен/);
});

test('account mutations preserve API methods, bodies and separate favorite routes', async () => {
  const f = fixture({}, { email: 'a' }); await f.state.load();
  await f.state.setQuantity(2, 4); await f.state.setQuantity(2, 0);
  await f.state.toggleFavorite({ id: 2 }); await f.state.toggleFavorite({ id: 'pdf' }, true);
  await f.state.toggleFavorite({ id: 3 }); await f.state.clear();
  assert.deepEqual(f.calls.slice(1), [
    ['/api/selection/cart/2', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"quantity":4}' }],
    ['/api/selection/cart/2', { method: 'DELETE' }],
    ['/api/selection/favorites/2', { method: 'DELETE' }],
    ['/api/selection/library-favorites/pdf', { method: 'DELETE' }],
    ['/api/selection/favorites/3', { method: 'PUT' }],
    ['/api/selection/cart', { method: 'DELETE' }],
  ]);
  assert.equal(f.local.values.size, 0);
});

test('failed writes leave cart and favorites unchanged and release the shared lock', async () => {
  const f = fixture({}, { email: 'a' }); await f.state.load(); f.respond(Error('Отказ'));
  for (const operation of [() => f.state.setQuantity(2, 9), () => f.state.clear(),
    () => f.state.add({ id: 1, name: 'Книга' }), () => f.state.toggleFavorite({ id: 2 })]) {
    await operation(); assert.deepEqual(f.state.cart, { 2: 3 });
    assert.equal(f.state.favorites.has('2'), true); assert.equal(f.state.busy, false);
  }
  assert.equal(f.notices.filter(t => t === 'Отказ').length, 4);
});

test('pending cart write blocks both cart and shelf actions until completion', async () => {
  let resolveWrite;
  const events = [], notices = [];
  const data = createSelectionData({ getStorage: () => storage(), send: url => url === '/api/selection'
    ? Promise.resolve({ cart: {}, favorites: [] }) : new Promise(resolve => { resolveWrite = resolve; }) });
  const state = createSelectionState({ data, getUser: () => ({}), notify: t => notices.push(t),
    onReset() {}, onChange: () => events.push(state.busy) });
  await state.load();
  const pending = state.add({ id: 1, name: 'Книга' });
  assert.equal(state.busy, true); assert.deepEqual(state.cart, {});
  await state.clear(); await state.toggleFavorite({ id: 2 });
  assert.deepEqual(notices, ['Дождитесь сохранения выбора', 'Дождитесь сохранения выбора']);
  resolveWrite(); await pending;
  assert.equal(state.cart[1], 1); assert.equal(state.busy, false);
  assert.deepEqual(events, [false, true]);
});

const books = [ { id: 1, name: 'Первая', author: 'Автор', price: '0.29', availableStock: 3 },
  { id: 2, name: 'Вторая', author: 'Другой', price: null, availableStock: 0 } ];

test('row calculation is pure; explicit pruning retains the existing dialog behavior', async () => {
  const f = fixture({ 'bookstore-cart': '{"1":2,"999":1}' }); await f.state.load();
  const model = cartRows(f.state.cart, books);
  assert.equal(f.state.count, 3); assert.equal(model.total, 2); assert.deepEqual(model.missing, ['999']);
  f.state.removeMissing(model.missing);
  assert.equal(f.state.count, 2);
  assert.equal(f.local.values.get('bookstore-cart'), '{"1":2,"999":1}');
  assert.equal(cartCount({ 1: 99, 2: 2 }), 101); assert.equal(nextCartQuantity(99), 99);
});

test('summary preserves cent rounding, stock warnings and existing checkout availability', () => {
  assert.deepEqual(cartSummary({ 1: 3 }, books, {}), {
    amount: 0.87, valid: true, checkoutDisabled: false,
    note: 'Оформление резервирует книги. Онлайн-оплата не подключена.' });
  for (const cart of [{ 1: 4 }, { 2: 1 }, { 999: 1 }]) {
    const model = cartSummary(cart, books, {});
    assert.equal(model.valid, false); assert.equal(model.checkoutDisabled, false);
  }
  assert.equal(cartSummary({ 1: 1 }, books, null).checkoutDisabled, true);
  assert.equal(cartSummary({}, books, {}).checkoutDisabled, true);
});

test('cart view preserves row controls, escaping, limits, hints and empty state', async () => {
  const original = globalThis.document;
  class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.attributes = {}; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(name, value) { this.attributes[name] = String(value); }
  }
  globalThis.document = { createElement: tag => new Element(tag) };
  try {
    const { renderCartRows, renderCartSummary, renderSelectionHints } = await import(new URL('cart-view.js', root));
    const target = { list: new Element('div'), total: new Element('p'), clear: new Element('button') };
    const calls = [];
    const callbacks = { coverImage: () => new Element('img'), setQuantity: (...args) => calls.push(args) };
    const book = { ...books[0], name: '<b>Книга</b>' };
    renderCartRows(target, cartRows({ 1: 99 }, [book]), callbacks);
    const info = target.list.children[0].children[1], controls = info.children[3];
    assert.equal(info.children[0].textContent, '<b>Книга</b>');
    assert.equal(controls.children[2].disabled, true);
    assert.equal(controls.children[0].attributes['aria-label'], 'Уменьшить количество: <b>Книга</b>');
    controls.children[0].onclick(); controls.children[3].onclick();
    assert.deepEqual(calls, [['1', 98], ['1', 0]]);
    assert.equal(target.total.textContent, 'Экземпляров: 99');
    renderCartRows(target, cartRows({}, books), callbacks);
    assert.equal(target.clear.disabled, true); assert.match(target.list.children[0].textContent, /пока пуста/);
    const summaryTarget = { amount: new Element('p'), checkout: new Element('button'), note: new Element('p') };
    renderCartSummary(summaryTarget, cartSummary({ 2: 1 }, books, null));
    assert.match(summaryTarget.amount.textContent, /без цены/); assert.equal(summaryTarget.checkout.disabled, true);
    const hints = { selectionNote: new Element('p'), cartHint: new Element('p') };
    renderSelectionHints(hints, null); assert.match(hints.cartHint.textContent, /Гостевая/);
    renderSelectionHints(hints, {}); assert.match(hints.selectionNote.textContent, /в аккаунте/);
  } finally { globalThis.document = original; }
});
