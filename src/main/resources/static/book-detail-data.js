import { request } from './api-client.js';

export function createBookDetailData({ send = request } = {}) {
  const reviewsPath = book => '/api/books/' + book.id + '/reviews';
  return {
    loadReviews(book) { return send(reviewsPath(book)); },
    saveReview(book, values) {
      return send(reviewsPath(book), {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rating: Number(values.rating), body: values.body }),
      });
    },
    deleteReview(book, review) {
      return send(reviewsPath(book) + '/' + review.id, { method: 'DELETE' });
    },
  };
}
