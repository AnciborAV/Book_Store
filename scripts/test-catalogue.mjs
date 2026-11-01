import assert from 'node:assert/strict';
import { test } from 'node:test';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const root = process.argv[2] ? pathToFileURL(resolve(process.argv[2]) + '/')
  : new URL('../src/main/resources/static/', import.meta.url);
const { catalogueSnapshot, selectCatalogue } = await import(new URL('catalogue-model.js', root));
const sections = Object.freeze([
  { id: 'hidden', name: ' Каталог ' }, { id: 'java', name: 'Java' },
  { id: 'faith', name: 'Святоотеческое наследие' }, { id: 'empty', name: 'Пустой раздел' },
]);
const source = Object.freeze({ sections, books: Object.freeze([
  { kind: 'catalog', id: 1, title: 'Язык Java', author: 'Автор', sectionId: 'java', sectionName: 'Java', cover: false, version: 0, pdfVersion: 0 },
  { kind: 'library', id: '1', title: 'Философия Java', author: 'Брюс Эккель', sectionId: 'java', sectionName: 'Java', version: 4 },
  { kind: 'catalog', id: 2, title: 'Слова подвижнические', author: 'Исаак Сирин', sectionId: 'faith', sectionName: 'Святоотеческое наследие', cover: true, version: 2, pdfVersion: 3 },
  { kind: 'library', id: '3', title: 'Новая книга', author: 'Другой автор', sectionId: 'java', sectionName: 'Java', version: 0 },
].map(Object.freeze)) });
const snapshot = catalogueSnapshot(source);
const state = { ...snapshot, category: 'all', filter: 'all', query: '', page: 1, pageSize: 12,
  favorites: new Set(['2']), libraryFavorites: new Set(['1']) };
const keys = model => model.items.map(b => `${b.kind}:${b.id}`);

test('snapshot preserves source order and both cover URL schemes', () => {
  assert.equal(snapshot.books[0].coverUrl, null);
  assert.equal(snapshot.books[1].coverUrl, '/api/catalogue/covers/2?v=2-3');
  assert.equal(snapshot.libraryBooks[0].coverUrl, '/api/java-library/1/cover?v=4');
  assert.deepEqual(keys(selectCatalogue(state)), ['catalog:1', 'library:1', 'catalog:2', 'library:3']);
  assert.equal(source.books[0].name, undefined);
});

test('search combines title and author and ignores Russian case and outer spaces', () => {
  assert.deepEqual(keys(selectCatalogue({ ...state, query: '  ИСААК  ' })), ['catalog:2']);
  assert.deepEqual(keys(selectCatalogue({ ...state, query: 'ЭККЕЛЬ' })), ['library:1']);
  assert.equal(selectCatalogue({ ...state, query: 'JAVA' }).total, 2);
});

test('favorites keep catalogue and library identifiers in separate namespaces', () => {
  const result = selectCatalogue({ ...state, filter: 'favorites' });
  assert.deepEqual(keys(result), ['library:1', 'catalog:2']);
  assert.equal(result.favoriteCount, 2);
  assert.deepEqual(keys(selectCatalogue({ ...state, category: 'java', filter: 'favorites' })), ['library:1']);
});

test('section counters include unfiltered books and hidden Catalogue cannot stay selected', () => {
  const result = selectCatalogue({ ...state, category: 'hidden', query: 'Исаак' });
  assert.equal(result.category, 'all');
  assert.deepEqual(result.categories.map(s => [s.id, s.count]), [['all', 4], ['java', 3], ['faith', 1], ['empty', 0]]);
  assert.equal(result.total, 1);
  assert.equal(selectCatalogue({ ...state, category: 'removed' }).category, 'all');
});

test('pagination clamps to the last available page and does not reorder stored arrays', () => {
  const before = JSON.stringify(snapshot);
  const result = selectCatalogue({ ...state, page: 99, pageSize: 2 });
  assert.equal(result.page, 2); assert.equal(result.pages, 2);
  assert.deepEqual(keys(result), ['catalog:2', 'library:3']);
  assert.equal(JSON.stringify(snapshot), before);
});

test('empty results retain the original distinct shelf and search messages', () => {
  const result = selectCatalogue({ ...state, query: 'не существует', page: 99 });
  assert.equal(result.page, 1); assert.equal(result.pages, 1); assert.equal(result.total, 0);
  assert.equal(result.emptyMessage, 'Книги не найдены. Измените запрос или раздел.');
  assert.match(selectCatalogue({ ...state, filter: 'favorites', favorites: new Set(), libraryFavorites: new Set() }).emptyMessage, /^На этой полке/);
  assert.equal(catalogueSnapshot({ sections: [], books: [] }).message, 'Каталог пока пуст. Добавьте первую книгу.');
});

test('data loader uses the existing endpoint and propagates server errors', async () => {
  const { loadCatalogue } = await import(new URL('catalogue-data.js', root));
  const originalFetch = globalThis.fetch;
  const requests = [];
  try {
    globalThis.fetch = async url => {
      requests.push(url);
      return new Response(JSON.stringify(source), { headers: { 'content-type': 'application/json' } });
    };
    assert.deepEqual(await loadCatalogue(), snapshot);
    globalThis.fetch = async url => {
      requests.push(url);
      return new Response(JSON.stringify({ detail: 'Каталог временно недоступен' }),
        { status: 503, headers: { 'content-type': 'application/json' } });
    };
    await assert.rejects(loadCatalogue, /Каталог временно недоступен/);
    assert.deepEqual(requests, ['/api/catalogue', '/api/catalogue']);
  } finally { globalThis.fetch = originalFetch; }
});

test('view preserves PDF links, book actions, category selection and pagination', async () => {
  const previousDocument = globalThis.document;
  class Element {
    constructor(tag) { this.tag = tag; this.children = []; this.attributes = {}; }
    append(...children) { this.children.push(...children); }
    replaceChildren(...children) { this.children = children; }
    setAttribute(name, value) { this.attributes[name] = String(value); }
  }
  globalThis.document = { createElement: tag => new Element(tag) };
  try {
    const { appendPDFControls, renderCatalogueCategories, renderCatalogueCards, renderCataloguePager } =
      await import(new URL('catalogue-view.js', root));
    const target = new Element('div');
    appendPDFControls(target, { pdfId: 'a b' });
    assert.equal(target.children.length, 4);
    assert.equal(target.children[0].href, '/reader.html?id=a%20b');
    assert.equal(target.children[1].attributes.download, '');
    assert.equal(target.children[2].rel, 'noopener');
    let selected;
    const model = selectCatalogue({ ...state, pageSize: 1 });
    renderCatalogueCategories(target, model, true, id => { selected = id; });
    assert.equal(target.children[0].attributes['aria-pressed'], 'true');
    target.children[1].onclick();
    assert.equal(selected, 'java');
    renderCataloguePager(target, model, page => { selected = page; });
    assert.equal(target.children[0].disabled, true);
    target.children[2].onclick();
    assert.equal(selected, 2);
    const book = { ...snapshot.books[0], price: '125', availableStock: 2, pdfId: 'pdf' };
    const callbacks = { ...state, coverImage: () => new Element('img'), openBook: () => { selected = 'open'; },
      showDescription() {}, toggleFavorite: () => { selected = 'favorite'; }, toggleLibraryFavorite() {},
      addToCart: () => { selected = 'cart'; }, addEditorialControls() {}, addManagementControls() {} };
    renderCatalogueCards(target, { total: 1, items: [book] }, callbacks);
    const card = target.children[0];
    card.children[0].children[0].onclick();
    assert.equal(selected, 'open');
    card.children[0].children[1].onclick();
    assert.equal(selected, 'favorite');
    const actions = card.children.at(-1);
    actions.children[4].onclick();
    assert.equal(selected, 'cart');
    assert.equal(actions.children[5].textContent, 'Отзывы ↗');
    renderCatalogueCards(target, { total: 0, items: [], emptyMessage: 'Пусто' }, callbacks);
    assert.equal(target.children[0].textContent, 'Пусто');
  } finally { globalThis.document = previousDocument; }
});
