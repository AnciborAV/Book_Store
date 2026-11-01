import { nextCartQuantity } from './cart-model.js';

function node(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined) element.textContent = text;
  if (className) element.className = className;
  return element;
}
function action(text, handler, className) {
  const button = node('button', text, className);
  button.type = 'button'; button.onclick = handler;
  return button;
}
const money = value => new Intl.NumberFormat('ru-RU', { style: 'currency', currency: 'RUB' }).format(value);

export function renderCartRows({ list, total, clear }, model, { coverImage, setQuantity }) {
  list.replaceChildren();
  for (const { id, quantity, book } of model.rows) {
    const row = node('article', undefined, 'cart-row');
    row.append(coverImage(book));
    const info = node('div');
    info.append(node('h3', book.name), node('p', book.author),
      node('p', book.price === null ? 'Цена не указана' : money(book.price)));
    const controls = node('div', undefined, 'quantity-controls');
    const minus = action('−', () => setQuantity(id, quantity - 1), 'quantity-button');
    minus.setAttribute('aria-label', 'Уменьшить количество: ' + book.name);
    const plus = action('+', () => setQuantity(id, nextCartQuantity(quantity)), 'quantity-button');
    plus.disabled = quantity >= 99;
    plus.setAttribute('aria-label', 'Увеличить количество: ' + book.name);
    controls.append(minus, node('span', String(quantity)), plus,
      action('Удалить', () => setQuantity(id, 0), 'text-button'));
    info.append(controls); row.append(info); list.append(row);
  }
  if (!model.total) list.append(node('p', 'Ваша корзина пока пуста. Найдите книгу в каталоге и нажмите «В корзину».'));
  total.textContent = 'Экземпляров: ' + model.total;
  clear.disabled = !model.total;
}

export function renderCartSummary({ amount, checkout, note }, model) {
  amount.textContent = 'Сумма: ' + money(model.amount)
    + (model.valid ? '' : ' · Есть позиции без цены или доступного остатка');
  checkout.disabled = model.checkoutDisabled;
  note.textContent = model.note;
}

export function renderSelectionHints({ selectionNote, cartHint }, user) {
  selectionNote.textContent = user
    ? 'Ваш выбор сохраняется в аккаунте и доступен после повторного входа.'
    : 'Гостевой выбор хранится в этом браузере. Войдите, чтобы вести личную полку и корзину в аккаунте.';
  cartHint.textContent = user
    ? 'Корзина сохранена. Цена и доступный остаток проверяются при оформлении заказа.'
    : 'Гостевая корзина хранится отдельно от аккаунтов. Войдите для оформления заказа.';
}
