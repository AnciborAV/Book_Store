import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
const root = process.argv[2] ? pathToFileURL(resolve(process.argv[2]) + '/')
  : new URL('../src/main/resources/static/', import.meta.url);
const { startApplication } = await import(new URL('app-startup.js', root));
const { setupApplicationNavigation } = await import(new URL('app-navigation.js', root));
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };

test('startup awaits covers, then profile, then catalogue, and only then opens the route', async () => {
  const covers = deferred(), profile = deferred(), books = deferred(), events = [];
  const pending = startApplication({ loadCovers: async () => { events.push('covers'); await covers.promise; },
    loadProfile: async () => { events.push('profile'); await profile.promise; },
    loadBooks: async () => { events.push('books'); await books.promise; },
    openInitialRoute: () => events.push('route'), notify: t => events.push(t) });
  assert.deepEqual(events, ['covers']); covers.resolve(); await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['covers','profile']); profile.resolve(); await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['covers','profile','books']); books.resolve(); await pending;
  assert.deepEqual(events, ['covers','profile','books','route']);
});

test('cover failures stay silent and profile failures are reported before catalogue loading', async () => {
  const events = [];
  await startApplication({ loadCovers: async () => { throw Error('covers'); },
    loadProfile: async () => { throw Error('profile'); }, loadBooks: async () => events.push('books'),
    openInitialRoute: () => events.push('route'), notify: text => events.push(text) });
  assert.deepEqual(events, ['profile','books','route']);
});

test('unexpected catalogue rejection still prevents opening a route', async () => {
  let opened = false;
  await assert.rejects(startApplication({ loadCovers: async () => {}, loadProfile: async () => {},
    loadBooks: async () => { throw Error('catalogue'); }, openInitialRoute: () => { opened = true; }, notify() {} }), /catalogue/);
  assert.equal(opened, false);
});

class Target {
  constructor() { this.listeners = new Map(); this.attributes = new Map(); this.classes = new Set(); this.style = {}; this.value = ''; this.dataset = {};
    this.classList = { add: name => this.classes.add(name), remove: name => this.classes.delete(name),
      toggle: (name, active) => active ? this.classes.add(name) : this.classes.delete(name) }; }
  addEventListener(name, handler) { const list = this.listeners.get(name) || []; list.push(handler); this.listeners.set(name, list); }
  emit(name, event = {}) { for (const handler of this.listeners.get(name) || []) handler(event); }
  setAttribute(name, value) { this.attributes.set(name, value); }
  getAttribute(name) { return this.attributes.get(name); }
  close() { this.closed = true; }
  focus() { this.focused = true; }
  scrollIntoView(options) { this.scrollOptions = options || null; this.scrollCount = (this.scrollCount || 0) + 1; }
  getBoundingClientRect() { return { left: 10, right: 90, top: 20, bottom: 80 }; }
}
function navigationFixture(hash = '') {
  const nodes = new Map(), document = new Target(), window = new Target(), media = new Target(), calls = [];
  const $ = selector => { if (!nodes.has(selector)) nodes.set(selector, new Target()); return nodes.get(selector); };
  const dialog = new Target(), close = new Target(), aiLink = new Target(); close.closest = () => dialog;
  document.querySelector = $; document.querySelectorAll = selector => ({ 'dialog': [dialog], 'dialog .close': [close], '[data-open-ai-books]': [aiLink] }[selector] || []);
  document.documentElement = new Target();
  window.location = { hash }; window.history = { replaceState: (...args) => calls.push(['replace', ...args]) };
  window.matchMedia = query => { assert.equal(query, '(max-width: 600px)'); return media; };
  let category = 'all', sections = [], aiHandlers;
  const nav = setupApplicationNavigation({ document, window, getCategory: () => category, getSections: () => sections,
    showSection: id => { category = id; calls.push(['section',id]); }, atlas: section => section.atlas,
    ai: { isAIHash: hash => ['#ai','#ai-history'].includes(hash), openAI: () => calls.push(['ai']),
      setupAINavigation: options => { aiHandlers = options; } } });
  return { nav, $, document, window, media, calls, dialog, close, aiLink,
    get aiHandlers() { return aiHandlers; }, setSections: value => { sections = value; } };
}

test('dialogs close on their close button and outside bounds, preserving inner clicks', () => {
  const f = navigationFixture(); f.close.onclick(); assert.equal(f.dialog.closed, true);
  for (const [x,y,expected] of [[10,20,false],[90,80,false],[50,50,false],[9,50,true],[50,81,true]]) {
    f.dialog.closed = false; f.dialog.emit('click', { target: f.dialog, clientX: x, clientY: y });
    assert.equal(f.dialog.closed, expected);
  }
  f.dialog.closed = false; f.dialog.emit('click', { target: {}, clientX: 0, clientY: 0 }); assert.equal(f.dialog.closed, false);
});

test('mobile menu toggles, closes on links, Escape and breakpoint, and restores focus only for Escape', () => {
  const f = navigationFixture(), toggle = f.$('#menu-toggle');
  assert.equal(f.document.documentElement.classes.has('mobile-navigation-ready'), true);
  toggle.emit('click'); assert.equal(toggle.getAttribute('aria-expanded'), 'true');
  toggle.emit('click'); assert.equal(toggle.getAttribute('aria-expanded'), 'false');
  toggle.emit('click'); f.$('#main-navigation').emit('click', { target: { closest: () => null } });
  assert.equal(toggle.getAttribute('aria-expanded'), 'true');
  f.$('#main-navigation').emit('click', { target: { closest: () => ({}) } });
  assert.equal(toggle.getAttribute('aria-expanded'), 'false'); assert.equal(toggle.focused, undefined);
  toggle.emit('click'); f.document.emit('keydown', { key: 'Escape' });
  assert.equal(toggle.getAttribute('aria-expanded'), 'false'); assert.equal(toggle.focused, true);
  toggle.focused = false; f.document.emit('keydown', { key: 'Escape' }); assert.equal(toggle.focused, false);
  toggle.emit('click'); f.media.emit('change'); assert.equal(toggle.getAttribute('aria-expanded'), 'false');
});

test('category selection leaves AI hash via replaceState and preserves other hashes', () => {
  const f = navigationFixture('#ai-history'); f.nav.selectCategory('java');
  assert.equal(f.$('#ai').hidden, true);
  assert.deepEqual(f.calls, [['replace',null,'','#catalog'],['section','java']]);
  const g = navigationFixture('#catalog'); g.nav.selectCategory('philosophy');
  assert.deepEqual(g.calls, [['section','philosophy']]);
});

test('AI book links and ensureBooks preserve section selection and scrolling', () => {
  const f = navigationFixture(); f.aiLink.emit('click');
  assert.deepEqual(f.calls, [['section','ai']]); assert.deepEqual(f.$('#catalog').scrollOptions, { block: 'start' });
  f.aiHandlers.ensureBooks(); assert.equal(f.calls.length, 1);
  f.nav.selectCategory('java'); f.aiHandlers.ensureBooks(); assert.deepEqual(f.calls.at(-1), ['section','ai']);
  f.aiHandlers.openBooks(); assert.deepEqual(f.calls.at(-1), ['section','ai']);
});

test('initial routes use the current hash and managed digital section with the existing fallback', () => {
  for (const hash of ['#ai','#ai-history','#ai-books','#digital-books','#catalog','']) {
    const f = navigationFixture(hash); f.setSections([{ id: 'custom', atlas: { id: 'digital' } }]); f.nav.openInitialRoute();
    assert.deepEqual(f.calls, hash === '#ai' || hash === '#ai-history' ? [['ai']]
      : hash === '#ai-books' ? [['section','ai']] : hash === '#digital-books' ? [['section','custom']] : []);
  }
  const missing = navigationFixture('#digital-books'); missing.nav.openInitialRoute();
  assert.deepEqual(missing.calls, []); assert.deepEqual(missing.$('#catalog').scrollOptions, { block: 'start' });
  const current = navigationFixture('#catalog'); current.window.location.hash = '#ai'; current.nav.openInitialRoute();
  assert.deepEqual(current.calls, [['ai']]);
});

test('digital hash changes resolve the current managed sections while other hashes stay with AI navigation', () => {
  const f = navigationFixture('#catalog'); f.window.emit('hashchange'); assert.equal(f.calls.length, 0);
  f.window.location.hash = '#digital-books'; f.setSections([{ id: 'renamed', atlas: { id: 'digital' } }]);
  f.window.emit('hashchange'); assert.deepEqual(f.calls, [['section','renamed']]);
});

test('entry point wires all components before profile/catalogue loads and preserves shared reload/event callbacks', async () => {
  const source = await readFile(new URL('app.js', root), 'utf8');
  const events = [], options = {}, nodes = new Map(), document = new Target();
  const $ = key => { if (!nodes.has(key)) nodes.set(key, new Target()); return nodes.get(key); };
  document.querySelector = $; document.querySelectorAll = () => [];
  let pending;
  const selection = { cart: {}, favorites: new Set(), libraryFavorites: new Set(), ready: true, busy: false, count: 0,
    async load() { events.push('selection'); }, removeMissing() {} };
  const mocks = { document, fetch: async url => { events.push(url); return { ok: true, json: async () => ({}) }; }, setTimeout() {} };
  for (const match of source.matchAll(/^import\s*\{([^}]+)\}\s*from\s*"[^"]+";/gm)) {
    for (const name of match[1].split(',').map(name => name.trim())) mocks[name] = () => {};
  }
  Object.assign(mocks, { createSelectionState: () => selection,
    setupBookDetail: value => { events.push('setupBookDetail'); options.detail = value; return { open: book => events.push(['open',book.id]) }; },
    setupAccount: value => { events.push('setupAccount'); options.account = value; return { loadProfile: async () => { events.push('profile'); } }; },
    setupApplicationNavigation: value => { events.push('navigation'); options.navigation = value; return { selectCategory() {}, openInitialRoute: () => events.push('route') }; },
    startApplication: value => { pending = startApplication(value); },
    loadCatalogue: async () => { events.push('catalogue'); return { sections: [], books: [{ id: 1 }], libraryBooks: [], message: '' }; },
    selectCatalogue: () => ({ category: 'all', page: 1 }), cartRows: () => ({ missing: [] }),
  });
  for (const name of ['setupCatalogueAdmin','setupBookOrdering','setupAbout','setupAnnouncement','setupAdminReviews','setupFeedback','setupJavaLibrary','setupCommerce','setupEditorial'])
    mocks[name] = value => { events.push(name); options[name] = value; };
  vm.runInNewContext(source.replace(/^import[^\n]+\n/gm, ''), mocks);
  await pending;
  const expected = ['setupBookDetail','setupAccount','setupCatalogueAdmin','setupBookOrdering','setupAbout','setupAnnouncement','setupAdminReviews','setupFeedback','setupJavaLibrary','setupCommerce','setupEditorial','navigation','/catalog-covers.json','profile','catalogue','route'];
  assert.deepEqual(events, expected);
  events.length = 0; await options.setupCatalogueAdmin.reload(); await options.setupCommerce.reload();
  assert.deepEqual(events, ['catalogue','selection','catalogue','selection']);
  events.length = 0; document.emit('editorial-open-catalog', { detail: '1' });
  assert.deepEqual(events, [['open',1]]); events.length = 0;
  document.emit('library-books-changed'); await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(events, ['catalogue']);
  events.length = 0; await options.setupBookOrdering.onSaved('java');
  assert.deepEqual(events, ['catalogue','selection']); assert.equal($('#search').value, '');
  assert.deepEqual(JSON.parse(JSON.stringify($('#catalog').scrollOptions)), { block: 'start' });
});
