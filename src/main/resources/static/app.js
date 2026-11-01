import { createSelectionState } from "./selection-state.js";
import { cartRows } from "./cart-model.js";
import { renderCartRows, renderSelectionHints } from "./cart-view.js";
import { loadCatalogue } from "./catalogue-data.js";
import { catalogueEntry, selectCatalogue } from "./catalogue-model.js";
import { appendPDFControls, renderCatalogueCategories, renderCatalogueCards, renderCataloguePager } from "./catalogue-view.js";
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
import { request, csrf, resetCsrf } from "./api-client.js";
const $ = (s) => document.querySelector(s);
let books = [], libraryBooks = [], sections = [],
  user = null,
  register = false,
  filter = "all",
  category = "all",
  page = 1,
  selectedBook = null,
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
function action(text, handler, cls = "button dark") {
  const button = node("button", text, cls);
  button.type = "button";
  button.onclick = handler;
  return button;
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
async function openBook(book) {
  selectedBook = book;
  const target = $("#book-detail");
  target.replaceChildren();
  const cover = coverImage(book),
    info = node("div");
  info.append(
    node("div", book.category || "Каталог", "eyebrow"),
    node("h2", book.name),
    node("p", book.author),
    ...(book.price !== null ? [action("Добавить в корзину +", () => addToCart(book))] : []),
  );
  appendPDFControls(info,book);
  target.append(cover, info);
  $("#review-form").reset();
  $("#review-error").textContent = "";
  $("#review-list").textContent = "Загрузка отзывов…";
  $("#review-summary").textContent = "";
  if (!$("#detail-dialog").open) $("#detail-dialog").showModal();
  await loadReviews(book);
}
async function loadReviews(book) {
  $("#review-form").hidden = !user;
  $("#review-login").hidden = !!user;
  try {
    const reviews = await request("/api/books/" + book.id + "/reviews");
    if (selectedBook?.id !== book.id) return;
    const list = $("#review-list");
    list.replaceChildren();
    $("#review-summary").textContent = reviews.length
      ? " · " +
        (reviews.reduce((s, r) => s + r.rating, 0) / reviews.length).toFixed(
          1,
        ) +
        " / 5 · " +
        reviews.length
      : "";
    if (!reviews.length)
      list.append(
        node("p", "Отзывов пока нет. Поделитесь первым впечатлением."),
      );
    for (const review of reviews) {
      const item = node("article", undefined, "review");
      const top = node("div", undefined, "review-top");
      top.append(
        node("strong", review.author),
        node(
          "span",
          "★".repeat(review.rating) + "☆".repeat(5 - review.rating),
          "review-stars",
        ),
      );
      item.append(
        top,
        node("small", new Date(review.created).toLocaleDateString("ru-RU")),
        node("p", review.body),
      );
      if (review.own) {
        $("#review-form [name=rating]").value = review.rating;
        $("#review-form [name=body]").value = review.body;
        item.append(
          action(
            "Удалить мой отзыв",
            async () => {
              try {
                await request(
                  "/api/books/" + book.id + "/reviews/" + review.id,
                  { method: "DELETE" },
                );
                $("#review-form").reset();
                await loadReviews(book);
              } catch (e) {
                toast(e.message);
              }
            },
            "text-button",
          ),
        );
      }
      list.append(item);
    }
  } catch (e) {
    if (selectedBook?.id === book.id)
      $("#review-list").textContent =
        "Не удалось загрузить отзывы. Откройте книгу ещё раз.";
  }
}
function avatar(target) {
  target.replaceChildren();
  if (user?.hasAvatar) {
    const image = node("img");
    image.alt = "Аватарка пользователя";
    image.src = "/api/profile/avatar?t=" + Date.now();
    target.append(image);
  } else target.textContent = user?.displayName?.[0]?.toUpperCase() || "Ч";
}
async function loadProfile() {
  const previous = user?.email;
  const r = await fetch("/api/profile");
  if (r.status === 401) {
    user = null;
  } else if (r.ok) {
    user = await r.json();
  } else throw Error("Не удалось загрузить профиль");
  $("#account-label").textContent = user ? user.displayName : "Войти";
  $(".mobile-account-label").textContent = user ? "Кабинет" : "Войти";
  $("#account").setAttribute("aria-label", user ? `Личный кабинет: ${user.displayName}` : "Войти в личный кабинет");
  avatar($("#account .avatar"));
  if (user) {
    $("#profile-name").textContent = user.displayName;
    $("#profile-email").textContent = user.email;
    avatar($("#profile-avatar"));
  }
  updateEditorial();
  updateCommerce();
  updateJavaLibrary();
  updateFeedback();
  updateAdminReviews();
  updateAnnouncement();
  updateAbout();
  if (previous !== user?.email || !selection.ready) await loadSelection();
}
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
$("#account").onclick = () => {
  $(user ? "#profile-dialog" : "#auth-dialog").showModal();
};
$("#auth-toggle").onclick = () => {
  register = !register;
  $("#name-field").hidden = !register;
  $("#name-field input").required = register;
  $("#auth-title").textContent = register
    ? "Начнём вашу историю"
    : "С возвращением";
  $("#auth-hint").textContent = register
    ? "Создайте аккаунт и добавьте свою аватарку."
    : "Войдите в свой книжный мир.";
  $("#auth-submit").textContent = register ? "Создать аккаунт ↗" : "Войти ↗";
  $("#auth-toggle").textContent = register
    ? "Уже есть аккаунт? Войти"
    : "Нет аккаунта? Зарегистрироваться";
  $("#auth-form [name=password]").autocomplete = register
    ? "new-password"
    : "current-password";
  $("#auth-error").textContent = "";
  $("#forgot-password").hidden = register;
};
$("#auth-form").onsubmit = async (e) => {
  e.preventDefault();
  const button = $("#auth-submit");
  button.disabled = true;
  $("#auth-error").textContent = "";
  const data = Object.fromEntries(new FormData(e.target));
  try {
    if (register)
      await request("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
    await request("/api/auth/login", {
      method: "POST",
      body: new URLSearchParams({
        email: data.email.trim().toLowerCase(),
        password: data.password,
        "remember-me": data["remember-me"] === "true" ? "true" : "false",
      }),
    });
    resetCsrf();
    await csrf();
    await loadProfile();
    e.target.reset();
    $("#auth-dialog").close();
    $("#profile-dialog").showModal();
    toast(register ? "Аккаунт создан" : "Вы вошли в аккаунт");
  } catch (err) {
    $("#auth-error").textContent = err.message;
  } finally {
    button.disabled = false;
  }
};
$("#avatar-form").onsubmit = async (e) => {
  e.preventDefault();
  const button = e.target.querySelector("button");
  const file = e.target.file.files[0];
  $("#avatar-message").textContent = "";
  if (!file || file.size > 2 * 1024 * 1024) {
    $("#avatar-message").textContent = "Выберите PNG или JPEG до 2 МБ";
    return;
  }
  button.disabled = true;
  try {
    await request("/api/profile/avatar", {
      method: "PUT",
      body: new FormData(e.target),
    });
    await loadProfile();
    $("#avatar-message").textContent = "Аватарка сохранена";
    e.target.reset();
  } catch (err) {
    $("#avatar-message").textContent = err.message;
  } finally {
    button.disabled = false;
  }
};
$("#logout").onclick = async () => {
  if (selection.busy) {
    toast("Дождитесь сохранения выбора");
    return;
  }
  try {
    await request("/api/auth/logout", { method: "POST" });
    resetCsrf();
    await loadProfile();
    $("#profile-dialog").close();
    toast("Вы вышли из аккаунта");
  } catch (e) {
    toast(e.message);
  }
};
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
$("#review-signin").onclick = () => {
  $("#detail-dialog").close();
  $("#auth-dialog").showModal();
};
$("#review-form").onsubmit = async (e) => {
  e.preventDefault();
  const book = selectedBook;
  if (!book) return;
  const button = e.target.querySelector("button");
  button.disabled = true;
  $("#review-error").textContent = "";
  try {
    await request("/api/books/" + book.id + "/reviews", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        rating: Number(e.target.elements.rating.value),
        body: e.target.elements.body.value,
      }),
    });
    await loadReviews(book);
    toast("Отзыв сохранён");
  } catch (err) {
    $("#review-error").textContent = err.message;
  } finally {
    button.disabled = false;
  }
};
async function init() {
  try {
    const r = await fetch("/catalog-covers.json");
    if (r.ok) covers = await r.json();
  } catch (_) {}
  await loadProfile().catch((e) => toast(e.message));
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
