export function nextCartQuantity(quantity) {
  return Math.min(99, quantity + 1);
}

export function cartCount(cart) {
  return Object.values(cart).reduce((sum, n) => sum + n, 0);
}

export function cartRows(cart, books) {
  const rows = [], missing = [];
  let total = 0;
  for (const [id, quantity] of Object.entries(cart)) {
    const book = books.find(book => String(book.id) === id);
    if (!book) {
      missing.push(id);
      continue;
    }
    rows.push({ id, quantity, book });
    total += quantity;
  }
  return { rows, missing, total };
}

export function cartSummary(cart, books, user) {
  let cents = 0, valid = true;
  for (const [id, quantity] of Object.entries(cart)) {
    const book = books.find(book => String(book.id) === id);
    if (!book || book.price === null || book.availableStock < quantity) {
      valid = false;
    }
    if (book?.price !== null && book) {
      cents += Math.round(Number(book.price) * 100) * quantity;
    }
  }
  return { amount: cents / 100, valid, checkoutDisabled: !user || !Object.keys(cart).length,
    note: user ? valid
    ? 'Оформление резервирует книги. Онлайн-оплата не подключена.'
    : 'Администратору нужно назначить любую положительную цену и оформить поступление недостающих книг в разделе «Управление магазином».'
    : 'Войдите, чтобы оформить заказ. Гостевая корзина не переносится в аккаунт.' };
}
