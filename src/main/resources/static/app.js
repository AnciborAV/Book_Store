import { setupBookDetail } from "./book-detail-controller.js";
import { setupAccount } from "./account-controller.js";
import { createSelectionState } from "./selection-state.js";
import { cartRows } from "./cart-model.js";
import { renderCartRows, renderSelectionHints } from "./cart-view.js";
import { loadCatalogue } from "./catalogue-data.js";
import { catalogueEntry, selectCatalogue } from "./catalogue-model.js";
import { renderCatalogueCategories, renderCatalogueCards, renderCataloguePager } from "./catalogue-view.js";
import {updateAtlasLink,atlasForSection} from "./atlas-navigation.js";
import {setupBookOrdering, updateBookOrderingAdmin} from "./book-ordering.js";
import {openAI, isAIHash, setupAINavigation, updateAISection} from "./ai-navigation.js";
import { setupCatalogueAdmin, updateCatalogueAdmin, addManagementControls, openBookManager } from "./catalogue-admin.js";
import { setupAbout, updateAbout } from "./about.js";
import { setupEditorial, updateEditorial, addEditorialControls, showDescription } from "./editorial.js";
import { setupAdminReviews, updateAdminReviews } from "./admin-reviews.js";
import { setupAnnouncement, updateAnnouncement } from "./announcement.js";
import { setupFeedback, updateFeedback } from "./feedback.js";
import { setupJavaLibrary, updateJavaLibrary, openJavaLibrary } from "./java-library.js";
import { setupCommerce, updateCommerce } from "./commerce.js";
import { request } from "./api-client.js";
const $ = (s) => document.querySelector(s);
let books = [], libraryBooks = [], sections = [],
  user = null,
  filter = "all",
  category = "all",
  page = 1,
  covers = {};
const pageSize = 12;
const selection = createSelectionState({
  getUser: () => user,
  notify: toast,
  onReset: render,
  onChange: () => {
    render();
    if ($('#cart-dialog').open) renderCart();
  },
});
const node = (tag, text, cls) => {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
};
function toast(message) {
  $("#toast").textContent = message;
  $("#toast").style.display = "block";
  setTimeout(() => ($("#toast").style.display = "none"), 3500);
}
function coverImage(book) {
  const image = node("img", undefined, "catalog-cover");
  image.src = book.coverUrl || covers[book.coverTitle || book.name] || "/covers/default.svg";
  image.alt = "Обложка: " + book.name + " — " + book.author;
  image.loading = "lazy";
  image.decoding = "async";
  image.onerror = () => {
    image.onerror = null;
    image.src = "/covers/default.svg";
  };
  image.width = 240;
  image.height = 350;
  return image;
}
const setCartQuantity = (id, quantity) => selection.setQuantity(id, quantity);
const addToCart = book => selection.add(book);
const toggleFavorite = book => selection.toggleFavorite(book);
const toggleLibraryFavorite = book => selection.toggleFavorite(book, true);
async function loadSelection() {
  await selection.load();
  renderSelectionHints({ selectionNote: $('#selection-note'), cartHint: $('.cart-hint') }, user);
}
function updateCartCount() {
  updateEditorial();
  updateCommerce();
  updateJavaLibrary();
  updateCatalogueAdmin();
  updateBookOrderingAdmin();
  $('#cart-count').textContent = selection.count;
}
function renderCart() {
  const model = cartRows(selection.cart, books);
  selection.removeMissing(model.missing);
  renderCartRows({ list: $('#cart-items'), total: $('#cart-total'), clear: $('#clear-cart') },
    model, { coverImage, setQuantity: setCartQuantity });
  updateCartCount();
}
function render() {
  $("#library-layout").classList.remove('ai-books-layout'); $("#java-library").hidden = true;
  for (const selector of [".catalog-tools", "#selection-note", "#catalog-message", "#books", "#pagination", ".catalog-bottom", ".catalog-top .search"]) $(selector).hidden = false;
  const model = selectCatalogue({ books, libraryBooks, sections, category, filter,
    query: $("#search").value, page, pageSize, favorites: selection.favorites, libraryFavorites: selection.libraryFavorites });
  category = model.category;
  page = model.page;
  const currentSection = sections.find(section => section.id === category);
  updateAtlasLink($("#catalog-atlas"), currentSection);
  updateAISection(currentSection);
  $("#catalog-count").textContent = `Найдено: ${model.total}`;
  $("#favorite-count").textContent = model.favoriteCount;
  renderCatalogueCategories($("#categories"), model, user?.role === 'ADMIN', id => {
    $("#ai").hidden = true;
    if (isAIHash(location.hash)) history.replaceState(null, '', '#catalog');
    category = id; page = 1; render();
  });
  renderCatalogueCards($("#books"), model, { coverImage, openBook, showDescription,
    favorites: selection.favorites, libraryFavorites: selection.libraryFavorites, toggleFavorite, toggleLibraryFavorite, addToCart,
    addEditorialControls, addManagementControls });
  renderCataloguePager($("#pagination"), model, nextPage => {
    page = nextPage; render(); $("#catalog").scrollIntoView();
  });
  updateCartCount();
}
async function loadBooks() {
  try {
    const data = await loadCatalogue();
    sections = data.sections;
    books = data.books;
    libraryBooks = data.libraryBooks;
    $("#catalog-message").textContent = data.message;
    render();
  } catch (e) {
    $("#catalog-message").textContent =
      "Каталог недоступен. Проверьте подключение к серверу.";
  }
}
const bookDetail = setupBookDetail({ getUser: () => user, coverImage, addToCart, toast });
const openBook = book => bookDetail.open(book);
const account = setupAccount({
  getUser: () => user,
  setUser: value => { user = value; },
  isSelectionReady: () => selection.ready,
  isSelectionBusy: () => selection.busy,
  loadSelection,
  onProfileChange: () => {
    updateEditorial();
    updateCommerce();
    updateJavaLibrary();
    updateFeedback();
    updateAdminReviews();
    updateAnnouncement();
    updateAbout();
  },
  toast,
});
$("#search").oninput = () => {
  page = 1;
  render();
};
document.querySelectorAll("[data-filter]").forEach(
  (b) =>
    (b.onclick = () => {
      filter = b.dataset.filter;
      page = 1;
      document
        .querySelectorAll("[data-filter]")
        .forEach((x) => x.classList.toggle("active", x === b));
      render();
    }),
);
document
  .querySelectorAll("dialog .close")
  .forEach((b) => (b.onclick = () => b.closest("dialog").close()));
document.querySelectorAll("dialog").forEach((d) =>
  d.addEventListener("click", (e) => {
    if (e.target === d) {
      const r = d.getBoundingClientRect();
      if (
        e.clientX < r.left ||
        e.clientX > r.right ||
        e.clientY < r.top ||
        e.clientY > r.bottom
      )
        d.close();
    }
  }),
);
$("#add-book").onclick = () => openBookManager(null,category);
$("#book-form").onsubmit = async (e) => {
  e.preventDefault();
  const button = e.target.querySelector("button");
  button.disabled = true;
  try {
    await request("/books", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(Object.fromEntries(new FormData(e.target))),
    });
    e.target.reset();
    $("#book-dialog").close();
    await loadBooks();
    toast("Книга добавлена в каталог");
  } catch (err) {
    $("#book-error").textContent = err.message;
  } finally {
    button.disabled = false;
  }
};
$("#open-cart").onclick = () => {
  renderCart();
  $("#cart-dialog").showModal();
};
$("#clear-cart").onclick = () => selection.clear();
$("#continue-shopping").onclick = () => {
  $("#cart-dialog").close();
  $("#catalog").scrollIntoView();
};
async function init() {
  try {
    const r = await fetch("/catalog-covers.json");
    if (r.ok) covers = await r.json();
  } catch (_) {}
  await account.loadProfile().catch((e) => toast(e.message));
  await loadBooks();
  if (isAIHash(location.hash)) openAI();
  else if (location.hash === "#ai-books") openAIBooks();
  else if (location.hash === "#digital-books") openDigitalBooks();
}
setupCatalogueAdmin({getUser:()=>user,toast,getSections:()=>sections,getSection:()=>category,reload:async()=>{await loadBooks();await loadSelection();}});
setupBookOrdering({getUser:()=>user,toast,getSection:()=>category,createCover:b=>coverImage(catalogueEntry(b,0)),onSaved:async section=>{
  category=section;page=1;filter='all';$('#search').value='';
  document.querySelectorAll('[data-filter]').forEach(button=>button.classList.toggle('active',button.dataset.filter==='all'));
  await loadBooks();await loadSelection();$('#catalog').scrollIntoView({block:'start'});
}});
setupAbout({ getUser: () => user, toast });
setupAnnouncement({ getUser: () => user });
setupAdminReviews({ getUser: () => user });
setupFeedback({ getUser: () => user });
setupJavaLibrary({ getUser: () => user, toast, getFavorites: () => selection.libraryFavorites, toggleFavorite: toggleLibraryFavorite, isSelectionReady: () => selection.ready && !selection.busy });
setupCommerce({
  getUser: () => user,
  getBooks: () => books,
  getCart: () => selection.cart,
  toast,
  reload: async () => {
    await loadBooks();
    await loadSelection();
  },
});
setupEditorial({ getUser: () => user, toast });
document.addEventListener("library-books-changed",loadBooks);
document.addEventListener("editorial-open-catalog", e => { const book = books.find(b => String(b.id) === e.detail); if (book) openBook(book); });
init();

// Enhance navigation only after the controls are wired; links remain available without JS.
const menuToggle = $("#menu-toggle");
const mainNavigation = $("#main-navigation");
function closeMobileMenu() {
  menuToggle.setAttribute("aria-expanded", "false");
}
menuToggle.addEventListener("click", () => {
  menuToggle.setAttribute("aria-expanded", String(menuToggle.getAttribute("aria-expanded") !== "true"));
});
mainNavigation.addEventListener("click", (event) => {
  if (event.target.closest("a")) closeMobileMenu();
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && menuToggle.getAttribute("aria-expanded") === "true") {
    closeMobileMenu();
    menuToggle.focus();
  }
});
window.matchMedia("(max-width: 600px)").addEventListener("change", closeMobileMenu);
document.documentElement.classList.add("mobile-navigation-ready");

function openAIBooks() {
  $("#ai").hidden = true;
  category = "ai"; page = 1; render();
  $("#catalog").scrollIntoView({block: "start"});
}
document.querySelectorAll('[data-open-ai-books]').forEach(link => link.addEventListener('click', openAIBooks));
setupAINavigation({openBooks:openAIBooks,ensureBooks:()=>{if(category !== 'ai') openAIBooks();}});

// Return from the digital atlas to the matching managed catalogue section.
function openDigitalBooks() {
  const section=sections.find(section=>atlasForSection(section)?.id === 'digital');
  if (!section) { $('#catalog').scrollIntoView({block:'start'}); return; }
  $('#ai').hidden=true;category=section.id;page=1;render();
  $('#catalog').scrollIntoView({block:'start'});
}
window.addEventListener('hashchange',()=>{if(location.hash === '#digital-books')openDigitalBooks();});
