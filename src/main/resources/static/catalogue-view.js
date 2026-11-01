import { isCatalogueFavorite } from './catalogue-model.js';

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) {
    element.textContent = text;
  }
  if (className) {
    element.className = className;
  }
  return element;
}

function action(text, handler, className = 'button dark') {
  const button = node('button', text, className);
  button.type = 'button';
  button.onclick = handler;
  return button;
}

export function appendPDFControls(target, book) {
  if (!book.pdfId) {
    return;
  }
  const read = node('a', 'Читать', 'button light');
  read.href = `/reader.html?id=${encodeURIComponent(book.pdfId)}`;
  const download = node('a', 'Скачать PDF ↓', 'button dark');
  download.href = `/api/java-library/${book.pdfId}/download`;
  download.setAttribute('download', '');
  target.append(read, download);
  for (const [text, href] of [
    ['Как работает чтение PDF · Java', '/pdf-reading-code.html'],
    ['Как работает скачивание · Java', '/pdf-download-code.html'],
  ]) {
    const source = node('a', text, 'pdf-source-link');
    source.href = href;
    source.target = '_blank';
    source.rel = 'noopener';
    target.append(source);
  }
}

export function renderCatalogueCategories(target, model, admin, onCategory) {
  target.replaceChildren();
  for (const section of model.categories) {
    const button = action(section.name + (admin ? ` · ${section.count}` : ''),
    () => onCategory(section.id),
    'category-chip' + (model.category === section.id ? ' active' : ''));
    button.setAttribute('aria-pressed', String(model.category === section.id));
    target.append(button);
  }
}

function catalogueCard(book, callbacks) {
  const { coverImage, openBook, showDescription, favorites, libraryFavorites,
    toggleFavorite, toggleLibraryFavorite, addToCart, addEditorialControls, addManagementControls } = callbacks;
  const card = node('article', undefined, 'book-card');
  const image = node('div', undefined, 'book-image');
  const show = () => book.kind === 'library' ? showDescription('library', String(book.id)) : openBook(book);
  const open = action('', show, 'cover-button');
  open.setAttribute('aria-label', 'Подробнее: ' + book.name);
  open.append(coverImage(book));
  image.append(open);
  const saved = isCatalogueFavorite(book, favorites, libraryFavorites);
  const favorite = action(saved ? '♥' : '♡',
  () => book.kind === 'library' ? toggleLibraryFavorite(book) : toggleFavorite(book),
  'favorite' + (saved ? ' selected' : ''));
  favorite.setAttribute('aria-label', (saved ? 'Убрать с полки: ' : 'Добавить на полку: ') + book.name);
  favorite.setAttribute('aria-pressed', saved);
  image.append(favorite);
  const title = node('h3');
  title.append(action(book.name, show, 'book-title'));
  card.append(image, node('span', book.category || 'Каталог', 'category-label'), title, node('p', book.author));
  const actions = node('div', undefined, 'book-actions');
  if (book.pdfId) {
    appendPDFControls(actions, book);
  } else {
    card.append(node('p', 'PDF пока не добавлен', 'stock-caption'));
  }
  if (book.kind === 'catalog') {
    if (book.price !== null) {
      card.append(node('strong', new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' }).format(book.price), 'book-price'),
      node('p', 'Доступно: ' + book.availableStock, 'stock-caption'));
      actions.append(action('В корзину +', () => addToCart(book), 'button dark small-button'));
    }
    actions.append(action('Отзывы ↗', () => openBook(book), 'text-button'));
  }
  card.append(actions);
  addEditorialControls(card, book.kind, book);
  addManagementControls(card, book);
  return card;
}

export function renderCatalogueCards(target, model, callbacks) {
  target.replaceChildren();
  if (!model.total) {
    target.append(node('p', model.emptyMessage));
  }
  for (const book of model.items) {
    target.append(catalogueCard(book, callbacks));
  }
}

export function renderCataloguePager(target, model, onPage) {
  target.replaceChildren();
  if (model.pages <= 1) {
    return;
  }
  const previous = action('← Назад', () => onPage(model.page - 1), 'page-button');
  previous.disabled = model.page === 1;
  const next = action('Далее →', () => onPage(model.page + 1), 'page-button');
  next.disabled = model.page === model.pages;
  target.append(previous, node('span', `${model.page} / ${model.pages}`), next);
}
