import { request } from './api-client.js';

export function emptySelection() {
  return { cart: {}, favorites: new Set(), libraryFavorites: new Set() };
}

export function createSelectionData({ send = request, getStorage = () => localStorage } = {}) {
  return {
    loadGuest() {
      const selection = emptySelection();
      try {
        const stored = JSON.parse(getStorage().getItem('bookstore-library-favorites') || '[]');
        if (Array.isArray(stored)) selection.libraryFavorites = new Set(stored.filter(id => typeof id === 'string'));
      } catch (_) {}
      try {
        const stored = JSON.parse(getStorage().getItem('bookstore-cart') || '{}');
        for (const [id, quantity] of Object.entries(stored))
          if (/^\d+$/.test(id) && Number.isInteger(quantity) && quantity > 0 && quantity <= 99)
            selection.cart[id] = quantity;
      } catch (_) {}
      try {
        const stored = JSON.parse(getStorage().getItem('bookstore-favorites') || '[]');
        if (Array.isArray(stored)) selection.favorites = new Set(stored.map(String));
      } catch (_) {}
      return selection;
    },
    saveGuest(selection) {
      getStorage().setItem('bookstore-library-favorites', JSON.stringify([...selection.libraryFavorites]));
      getStorage().setItem('bookstore-cart', JSON.stringify(selection.cart));
      getStorage().setItem('bookstore-favorites', JSON.stringify([...selection.favorites]));
    },
    async loadAccount() {
      const data = await send('/api/selection');
      return { cart: data.cart, favorites: new Set(data.favorites.map(String)),
        libraryFavorites: new Set(data.libraryFavorites || []) };
    },
    setQuantity(id, quantity) {
      return send('/api/selection/cart/' + id, quantity ? {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ quantity }),
      } : { method: 'DELETE' });
    },
    clearCart() { return send('/api/selection/cart', { method: 'DELETE' }); },
    setFavorite(id, saved, library) {
      return send('/api/selection/' + (library ? 'library-favorites/' : 'favorites/') + id,
        { method: saved ? 'DELETE' : 'PUT' });
    },
  };
}
