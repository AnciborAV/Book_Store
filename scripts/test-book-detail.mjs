import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
const root = process.argv[2] ? pathToFileURL(resolve(process.argv[2]) + '/')
  : new URL('../src/main/resources/static/', import.meta.url);
const { createBookDetailData } = await import(new URL('book-detail-data.js', root));
const { setupBookDetail } = await import(new URL('book-detail-controller.js', root));
const { createBookDetailView } = await import(new URL('book-detail-view.js', root));
const book = { id: 1, name: 'Книга', author: 'Автор', category: 'Философия', price: '120', pdfId: 'pdf' };
const review = { id: 7, rating: 5, body: 'Отзыв', author: 'Имя', created: '2026-10-30T12:00:00Z', own: true };
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { promise, resolve, reject }; };
function fixture() {
  const events = [], notices = [], loads = [];
  let user = null, handlers, onDelete, failure;
  const fail = name => { if (failure === name) throw Error('Ошибка ' + name); };
  const data = {
    async loadReviews(book) { events.push(['load', book.id]); fail('load'); return loads.length ? loads.shift() : [review]; },
    async saveReview(book, values) { events.push(['save', book.id, values]); fail('save'); },
    async deleteReview(book, review) { events.push(['delete', book.id, review.id]); fail('delete'); },
  };
  const view = { bind(value) { handlers = value; }, open: book => events.push(['open', book.id]),
    renderAccess: user => events.push(['access', user]),
    renderReviews(reviews, callback) { events.push(['reviews', reviews]); onDelete = callback; },
    resetReview: () => events.push(['reset']), showLoadError: () => events.push(['loadError']),
    setReviewError: text => events.push(['error', text]), signin: () => events.push(['signin']) };
  const controller = setupBookDetail({ data, view, getUser: () => user, toast: text => notices.push(text) });
  return { controller, handlers, events, notices, loads, data, setUser: value => { user = value; },
    fail: value => { failure = value; }, remove: value => onDelete(value) };
}
function form() {
  const button = { disabled: false };
  return { button, preventDefault() {}, target: { querySelector: () => button,
    elements: { rating: { value: '4' }, body: { value: ' Мой текст ' } } } };
}

test('review transport preserves GET, PUT JSON with numeric rating and DELETE routes', async () => {
  const calls = [], data = createBookDetailData({ send: async (...args) => { calls.push(args); return [review]; } });
  assert.deepEqual(await data.loadReviews(book), [review]);
  await data.saveReview(book, { rating: '4', body: ' Текст ' }); await data.deleteReview(book, review);
  assert.deepEqual(calls, [ ['/api/books/1/reviews'], ['/api/books/1/reviews', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"rating":4,"body":" Текст "}' }],
    ['/api/books/1/reviews/7', { method: 'DELETE' }] ]);
  const failed = createBookDetailData({ send: async () => { throw Error('network'); } });
  await assert.rejects(failed.loadReviews(book), /network/);
  await assert.rejects(failed.saveReview(book, {}), /network/);
  await assert.rejects(failed.deleteReview(book, review), /network/);
});

test('opening a book initializes the dialog before loading and reflects the current account', async () => {
  const f = fixture(); await f.controller.open(book);
  assert.deepEqual(f.events.map(e => e[0]), ['open','access','load','reviews']);
  assert.deepEqual(f.events[1], ['access', null]);
  f.setUser({ email: 'a' }); await f.controller.open(book);
  assert.deepEqual(f.events.at(-3), ['access', { email: 'a' }]);
});

test('late successful and failed responses for a different book cannot replace its reviews', async () => {
  for (const fails of [false, true]) {
    const f = fixture(), old = deferred(), current = deferred(); f.loads.push(old.promise, current.promise);
    const openingOld = f.controller.open(book);
    const openingNew = f.controller.open({ ...book, id: 2 });
    current.resolve([{ ...review, id: 8 }]); await openingNew;
    if (fails) old.reject(Error('late')); else old.resolve([review]);
    await openingOld;
    assert.deepEqual(f.events.filter(e => e[0] === 'reviews'), [['reviews', [{ ...review, id: 8 }]]]);
    assert.equal(f.events.some(e => e[0] === 'loadError'), false);
  }
});

test('selection check retains strict ID types and the existing same-ID response behavior', async () => {
  const f = fixture(), old = deferred(); f.loads.push(old.promise, []);
  const pending = f.controller.open(book); await f.controller.open({ ...book, id: '1' });
  old.resolve([review]); await pending;
  assert.deepEqual(f.events.filter(e => e[0] === 'reviews'), [['reviews', []]]);
  const g = fixture(), earlier = deferred(); g.loads.push(earlier.promise, []);
  const first = g.controller.open(book); await g.controller.open({ ...book });
  earlier.resolve([review]); await first;
  assert.deepEqual(g.events.at(-1), ['reviews', [review]]);
});

test('failed loading shows the existing message without rejecting opening the dialog', async () => {
  const f = fixture(); f.fail('load'); await f.controller.open(book);
  assert.deepEqual(f.events.at(-1), ['loadError']); assert.deepEqual(f.notices, []);
});

test('review submission before selecting a book does not send or disable the form', async () => {
  const f = fixture(), event = form(); await f.handlers.submit(event);
  assert.deepEqual(f.events, []); assert.equal(event.button.disabled, false);
});

test('saving retains the captured book, reloads reviews and restores the submit button', async () => {
  const f = fixture(), event = form(); await f.controller.open(book); f.events.length = 0;
  await f.handlers.submit(event);
  assert.deepEqual(f.events, [['error',''], ['save',1,{ rating:'4',body:' Мой текст ' }],
    ['access',null], ['load',1], ['reviews',[review]]]);
  assert.deepEqual(f.notices, ['Отзыв сохранён']); assert.equal(event.button.disabled, false);
});

test('save failure keeps the error local and does not reload or announce success', async () => {
  const f = fixture(), event = form(); await f.controller.open(book); f.events.length = 0; f.fail('save');
  await f.handlers.submit(event);
  assert.deepEqual(f.events.at(-1), ['error', 'Ошибка save']);
  assert.equal(f.events.some(e => e[0] === 'load'), false); assert.deepEqual(f.notices, []);
  assert.equal(event.button.disabled, false);
});

test('a successful save followed by failed reload retains the existing success notification', async () => {
  const f = fixture(); await f.controller.open(book); f.fail('load');
  await f.handlers.submit(form());
  assert.deepEqual(f.events.at(-1), ['loadError']); assert.deepEqual(f.notices, ['Отзыв сохранён']);
});

test('delete captures the rendered book, resets the form and reloads only after successful deletion', async () => {
  const f = fixture(); await f.controller.open(book); f.events.length = 0;
  await f.remove(review);
  assert.deepEqual(f.events.map(e => e[0]), ['delete','reset','access','load','reviews']);
  f.events.length = 0; f.fail('delete'); await f.remove(review);
  assert.deepEqual(f.events, [['delete',1,7]]); assert.deepEqual(f.notices, ['Ошибка delete']);
});

test('switching book during save preserves the original request target and ignores stale review content', async () => {
  const f = fixture(), pendingSave = deferred(), event = form();
  await f.controller.open(book);
  f.data.saveReview = async target => { f.events.push(['pendingSave', target.id]); await pendingSave.promise; };
  const saving = f.handlers.submit(event); assert.equal(event.button.disabled, true);
  f.loads.push([]); await f.controller.open({ ...book, id: 2 });
  const count = f.events.filter(e => e[0] === 'reviews').length;
  pendingSave.resolve(); await saving;
  assert.equal(f.events.filter(e => e[0] === 'reviews').length, count);
  assert.deepEqual(f.events.at(-1), ['load',1]); assert.equal(event.button.disabled, false);
});

test('sign-in delegates the existing dialog transition', () => {
  const f = fixture(); f.handlers.signin(); assert.deepEqual(f.events, [['signin']]);
});

class Element {
  constructor(tag) { this.tag = tag; this.children = []; this.attributes = {}; this.resetCount = 0; this.modalCount = 0; }
  set textContent(value) { this.text = value; this.children = []; }
  get textContent() { return this.text; }
  append(...children) { this.children.push(...children); }
  replaceChildren(...children) { this.text = ''; this.children = children; }
  setAttribute(key, value) { this.attributes[key] = String(value); }
  reset() { this.resetCount++; }
  showModal() { this.open = true; this.modalCount++; }
  close() { this.open = false; }
}
function viewFixture() {
  const nodes = new Map(), cart = [];
  const query = key => { if (!nodes.has(key)) nodes.set(key, new Element(key)); return nodes.get(key); };
  const view = createBookDetailView({ query, createElement: tag => new Element(tag),
    coverImage: () => new Element('img'), addToCart: book => cart.push(book.id) });
  return { view, query, cart };
}

test('book view preserves metadata, cart and PDF actions, loading text and dialog reuse', () => {
  const previous = globalThis.document; globalThis.document = { createElement: tag => new Element(tag) };
  try {
    const f = viewFixture(); f.view.open(book);
    const info = f.query('#book-detail').children[1];
    assert.equal(info.children[0].textContent, 'Философия'); assert.equal(info.children[1].textContent, 'Книга');
    info.children[3].onclick(); assert.deepEqual(f.cart, [1]);
    assert.equal(info.children[4].href, '/reader.html?id=pdf');
    assert.equal(info.children[5].href, '/api/java-library/pdf/download');
    assert.equal(info.children[6].rel, 'noopener'); assert.equal(info.children[7].target, '_blank');
    assert.equal(f.query('#review-list').textContent, 'Загрузка отзывов…'); assert.equal(f.query('#review-form').resetCount, 1);
    f.view.open({ ...book, price: null, pdfId: null, category: '' });
    assert.equal(f.query('#book-detail').children[1].children.length, 3);
    assert.equal(f.query('#book-detail').children[1].children[0].textContent, 'Каталог');
    assert.equal(f.query('#detail-dialog').modalCount, 1);
  } finally { globalThis.document = previous; }
});

test('review view preserves summary, stars, author text, own form values and deletion control', async () => {
  const f = viewFixture(); let deleted;
  f.view.renderReviews([review, { ...review, id: 8, rating: 4, own: false, body: '<b>Текст</b>' }], value => { deleted = value; });
  assert.equal(f.query('#review-summary').textContent, ' · 4.5 / 5 · 2');
  const rows = f.query('#review-list').children;
  assert.equal(rows[0].children[0].children[1].textContent, '★★★★★');
  assert.equal(rows[1].children[0].children[1].textContent, '★★★★☆');
  assert.equal(rows[1].children[2].textContent, '<b>Текст</b>'); assert.equal(rows[1].children.length, 3);
  assert.equal(f.query('#review-form [name=body]').value, 'Отзыв');
  assert.equal(f.query('#review-form [name=rating]').value, 5);
  await rows[0].children[3].onclick(); assert.equal(deleted, review);
  f.view.renderReviews([], () => {}); assert.equal(f.query('#review-summary').textContent, '');
  assert.match(f.query('#review-list').children[0].textContent, /Отзывов пока нет/);
  f.view.renderAccess(null); assert.equal(f.query('#review-form').hidden, true);
  f.view.renderAccess({}); assert.equal(f.query('#review-login').hidden, true);
  f.view.signin(); assert.equal(f.query('#detail-dialog').open, false); assert.equal(f.query('#auth-dialog').open, true);
  f.view.showLoadError(); assert.match(f.query('#review-list').textContent, /Не удалось загрузить отзывы/);
});
