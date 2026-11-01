import { createBookDetailData } from './book-detail-data.js';
import { createBookDetailView } from './book-detail-view.js';

export function setupBookDetail({ getUser, coverImage, addToCart, toast,
  data = createBookDetailData(), view = createBookDetailView({ coverImage, addToCart }) }) {
  let selectedBook = null;
  async function loadReviews(book) {
    view.renderAccess(getUser());
    try {
      const reviews = await data.loadReviews(book);
      // Keep the original strict ID check when switching between book dialogs.
      if (selectedBook?.id !== book.id) return;
      view.renderReviews(reviews, review => deleteReview(book, review));
    } catch (error) {
      if (selectedBook?.id === book.id) view.showLoadError();
    }
  }
  async function deleteReview(book, review) {
    try {
      await data.deleteReview(book, review);
      view.resetReview();
      await loadReviews(book);
    } catch (error) { toast(error.message); }
  }
  view.bind({
    signin: () => view.signin(),
    async submit(event) {
      event.preventDefault();
      const book = selectedBook;
      if (!book) return;
      const button = event.target.querySelector('button');
      button.disabled = true;
      view.setReviewError('');
      try {
        await data.saveReview(book, { rating: event.target.elements.rating.value,
          body: event.target.elements.body.value });
        await loadReviews(book);
        toast('Отзыв сохранён');
      } catch (error) { view.setReviewError(error.message); }
      finally { button.disabled = false; }
    },
  });
  return {
    async open(book) {
      selectedBook = book;
      view.open(book);
      await loadReviews(book);
    },
  };
}
