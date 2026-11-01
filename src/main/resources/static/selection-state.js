import { createSelectionData, emptySelection } from './selection-data.js';
import { nextCartQuantity, cartCount } from './cart-model.js';

export function createSelectionState({ getUser, notify, onChange, onReset,
  data = createSelectionData() }) {
  const state = { ...data.loadGuest(), ready: false, busy: false };
  function saveGuest() {
    try { data.saveGuest(state); }
    catch (_) { notify('Браузер не разрешает сохранение гостевого выбора'); }
  }
  async function change(operation) {
    if (state.busy) { notify('Дождитесь сохранения выбора'); return; }
    if (!state.ready) {
      notify('Выбор ещё не загружен. Обновите страницу и повторите действие.'); return;
    }
    state.busy = true;
    try {
      await operation();
      onChange();
    } catch (e) { notify(e.message); }
    finally { state.busy = false; }
  }
  async function setQuantity(id, quantity) {
    id = String(id);
    await change(async () => {
      if (getUser()) await data.setQuantity(id, quantity);
      if (quantity) state.cart[id] = quantity;
      else delete state.cart[id];
      if (!getUser()) saveGuest();
    });
  }
  return {
    get cart() { return state.cart; },
    get favorites() { return state.favorites; },
    get libraryFavorites() { return state.libraryFavorites; },
    get ready() { return state.ready; },
    get busy() { return state.busy; },
    get count() { return cartCount(state.cart); },
    async load() {
      state.ready = false;
      if (getUser()) {
        Object.assign(state, emptySelection());
        onReset();
        Object.assign(state, await data.loadAccount());
      } else Object.assign(state, data.loadGuest());
      state.ready = true;
      onChange();
    },
    setQuantity,
    async add(book) {
      const id = String(book.id);
      await change(async () => {
        const quantity = nextCartQuantity(state.cart[id] || 0);
        if (getUser()) await data.setQuantity(id, quantity);
        state.cart[id] = quantity;
        if (!getUser()) saveGuest();
        notify('«' + book.name + '» добавлена в корзину');
      });
    },
    async clear() {
      await change(async () => {
        if (getUser()) await data.clearCart();
        state.cart = {};
        if (!getUser()) saveGuest();
      });
    },
    async toggleFavorite(book, library = false) {
      const id = String(book.id);
      await change(async () => {
        const favorites = library ? state.libraryFavorites : state.favorites;
        const saved = favorites.has(id);
        if (getUser()) await data.setFavorite(id, saved, library);
        saved ? favorites.delete(id) : favorites.add(id);
        if (!getUser()) saveGuest();
      });
    },
    // The existing dialog drops entries absent from the loaded catalogue only when opened/rendered.
    removeMissing(ids) { for (const id of ids) delete state.cart[id]; },
  };
}
