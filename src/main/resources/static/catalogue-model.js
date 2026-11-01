// Catalogue calculations are independent of the DOM, network and account storage.

export function catalogueEntry(book, index) {
  return {
    ...book,
    name: book.title,
    category: book.sectionName,
    catalogOrder: index,
    coverUrl: book.kind === 'catalog'
    ? (book.cover ? `/api/catalogue/covers/${book.id}?v=${book.version}-${book.pdfVersion}` : null)
    : `/api/java-library/${book.id}/cover?v=${book.version}`,
  };
}

export function catalogueSnapshot(data) {
  const entries = data.books.map(catalogueEntry);
  return {
    sections: data.sections,
    books: entries.filter(book => book.kind === 'catalog'),
    libraryBooks: entries.filter(book => book.kind === 'library'),
    message: data.books.length
    ? 'Найдите свою книгу по названию, автору или разделу.'
    : 'Каталог пока пуст. Добавьте первую книгу.',
  };
}

export function isCatalogueFavorite(book, favorites, libraryFavorites) {
  return (book.kind === 'library' ? libraryFavorites : favorites).has(String(book.id));
}

export function selectCatalogue({ books, libraryBooks, sections, category, filter,
  query, page, pageSize, favorites, libraryFavorites }) {
  const entries = [...books, ...libraryBooks];
  const availableSections = sections.filter(section => section.name.trim().toLocaleLowerCase('ru') !== 'каталог');
  const activeCategory = category !== 'all' && !availableSections.some(section => section.id === category)
  ? 'all' : category;
  const counts = new Map();
  for (const book of entries) {
    counts.set(book.sectionId, (counts.get(book.sectionId) || 0) + 1);
  }
  const q = query.trim().toLocaleLowerCase('ru');
  const visible = entries.filter(book =>
    (book.name + ' ' + book.author).toLocaleLowerCase('ru').includes(q)
    && (filter !== 'favorites' || isCatalogueFavorite(book, favorites, libraryFavorites))
    && (activeCategory === 'all' || book.sectionId === activeCategory));
  visible.sort((a, b) => a.catalogOrder - b.catalogOrder);
  const pages = Math.max(1, Math.ceil(visible.length / pageSize));
  const currentPage = Math.min(page, pages);
  return {
    category: activeCategory,
    categories: [{ id: 'all', name: 'Все разделы', count: entries.length },
      ...availableSections.map(section => ({ ...section, count: counts.get(section.id) || 0 }))],
    total: visible.length,
    favoriteCount: entries.filter(book => isCatalogueFavorite(book, favorites, libraryFavorites)).length,
    items: visible.slice((currentPage - 1) * pageSize, currentPage * pageSize),
    page: currentPage,
    pages,
    emptyMessage: filter === 'favorites'
    ? 'На этой полке пока нет книг. Добавляйте их сердечком в каталоге.'
    : 'Книги не найдены. Измените запрос или раздел.',
  };
}
