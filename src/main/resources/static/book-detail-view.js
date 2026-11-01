import { appendPDFControls } from './catalogue-view.js';

export function createBookDetailView({ coverImage, addToCart,
  query = selector => document.querySelector(selector),
  createElement = tag => document.createElement(tag) }) {
  const $ = query;
  function node(tag, text, className) {
    const element = createElement(tag);
    if (text !== undefined) element.textContent = text;
    if (className) element.className = className;
    return element;
  }
  function action(text, handler, className = 'button dark') {
    const button = node('button', text, className);
    button.type = 'button';
    button.onclick = handler;
    return button;
  }
  return {
    bind(handlers) {
      $('#review-signin').onclick = handlers.signin;
      $('#review-form').onsubmit = handlers.submit;
    },
    open(book) {
      const target = $('#book-detail');
      target.replaceChildren();
      const cover = coverImage(book), info = node('div');
      info.append(node('div', book.category || 'Каталог', 'eyebrow'),
        node('h2', book.name), node('p', book.author),
        ...(book.price !== null ? [action('Добавить в корзину +', () => addToCart(book))] : []));
      appendPDFControls(info, book);
      target.append(cover, info);
      $('#review-form').reset();
      $('#review-error').textContent = '';
      $('#review-list').textContent = 'Загрузка отзывов…';
      $('#review-summary').textContent = '';
      if (!$('#detail-dialog').open) $('#detail-dialog').showModal();
    },
    renderAccess(user) {
      $('#review-form').hidden = !user;
      $('#review-login').hidden = !!user;
    },
    renderReviews(reviews, onDelete) {
      const list = $('#review-list');
      list.replaceChildren();
      $('#review-summary').textContent = reviews.length
        ? ' · ' + (reviews.reduce((sum, review) => sum + review.rating, 0) / reviews.length).toFixed(1)
          + ' / 5 · ' + reviews.length
        : '';
      if (!reviews.length) list.append(node('p', 'Отзывов пока нет. Поделитесь первым впечатлением.'));
      for (const review of reviews) {
        const item = node('article', undefined, 'review');
        const top = node('div', undefined, 'review-top');
        top.append(node('strong', review.author),
          node('span', '★'.repeat(review.rating) + '☆'.repeat(5 - review.rating), 'review-stars'));
        item.append(top, node('small', new Date(review.created).toLocaleDateString('ru-RU')),
          node('p', review.body));
        if (review.own) {
          $('#review-form [name=rating]').value = review.rating;
          $('#review-form [name=body]').value = review.body;
          item.append(action('Удалить мой отзыв', () => onDelete(review), 'text-button'));
        }
        list.append(item);
      }
    },
    resetReview() { $('#review-form').reset(); },
    showLoadError() { $('#review-list').textContent = 'Не удалось загрузить отзывы. Откройте книгу ещё раз.'; },
    setReviewError(message) { $('#review-error').textContent = message; },
    signin() { $('#detail-dialog').close(); $('#auth-dialog').showModal(); },
  };
}
