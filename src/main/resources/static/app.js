import { setupCommerce, updateCommerce } from "./commerce.js";
import { request, csrf, resetCsrf } from "./api-client.js";
const $ = (s) => document.querySelector(s);
let books = [],
  user = null,
  register = false,
  filter = "all",
  category = "all",
  page = 1,
  selectedBook = null,
  covers = {},
  favorites = new Set();
const categoryNames = [
  "Российские авторы",
  "Зарубежные авторы",
  "История",
  "Философия",
  "Гражданское право",
];
const pageSize = 12;
let cart = {},
  selectionBusy = false,
  selectionReady = false;
function loadGuestSelection() {
  cart = {};
  favorites = new Set();
  try {
    const stored = JSON.parse(localStorage.getItem("bookstore-cart") || "{}");
    for (const [id, quantity] of Object.entries(stored))
      if (
        /^\d+$/.test(id) &&
        Number.isInteger(quantity) &&
        quantity > 0 &&
        quantity <= 99
      )
        cart[id] = quantity;
  } catch (_) {}
  try {
    const stored = JSON.parse(
      localStorage.getItem("bookstore-favorites") || "[]",
    );
    if (Array.isArray(stored)) favorites = new Set(stored.map(String));
  } catch (_) {}
}
loadGuestSelection();
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
  image.src = covers[book.name] || "/covers/default.svg";
  image.alt = "Обложка: " + book.name + " — " + book.author;
  image.loading = "lazy";
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
async function changeSelection(operation) {
  if (selectionBusy) {
    toast("Дождитесь сохранения выбора");
    return;
  }
  if (!selectionReady) {
    toast("Выбор ещё не загружен. Обновите страницу и повторите действие.");
    return;
  }
  selectionBusy = true;
  try {
    await operation();
    render();
    if ($("#cart-dialog").open) renderCart();
  } catch (e) {
    toast(e.message);
  } finally {
    selectionBusy = false;
  }
}
async function setCartQuantity(id, quantity) {
  id = String(id);
  await changeSelection(async () => {
    if (user)
      await request(
        "/api/selection/cart/" + id,
        quantity
          ? {
              method: "PUT",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ quantity }),
            }
          : { method: "DELETE" },
      );
    if (quantity) cart[id] = quantity;
    else delete cart[id];
    if (!user) saveGuestSelection();
  });
}
async function addToCart(book) {
  const id = String(book.id);
  await changeSelection(async () => {
    const quantity = Math.min(99, (cart[id] || 0) + 1);
    if (user)
      await request("/api/selection/cart/" + id, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ quantity }),
      });
    cart[id] = quantity;
    if (!user) saveGuestSelection();
    toast("«" + book.name + "» добавлена в корзину");
  });
}
async function toggleFavorite(book) {
  const id = String(book.id);
  await changeSelection(async () => {
    const saved = favorites.has(id);
    if (user)
      await request("/api/selection/favorites/" + id, {
        method: saved ? "DELETE" : "PUT",
      });
    saved ? favorites.delete(id) : favorites.add(id);
    if (!user) saveGuestSelection();
  });
}
function saveGuestSelection() {
  try {
    localStorage.setItem("bookstore-cart", JSON.stringify(cart));
    localStorage.setItem("bookstore-favorites", JSON.stringify([...favorites]));
  } catch (_) {
    toast("Браузер не разрешает сохранение гостевого выбора");
  }
}
async function loadSelection() {
  selectionReady = false;
  if (user) {
    cart = {};
    favorites = new Set();
    render();
    const data = await request("/api/selection");
    cart = data.cart;
    favorites = new Set(data.favorites.map(String));
  } else loadGuestSelection();
  selectionReady = true;
  render();
  if ($("#cart-dialog").open) renderCart();
  $("#selection-note").textContent = user
    ? "Ваш выбор сохраняется в аккаунте и доступен после повторного входа."
    : "Гостевой выбор хранится в этом браузере. Войдите, чтобы вести личную полку и корзину в аккаунте.";
  $(".cart-hint").textContent = user
    ? "Корзина сохранена. Цена и доступный остаток проверяются при оформлении заказа."
    : "Гостевая корзина хранится отдельно от аккаунтов. Войдите для оформления заказа.";
}
function updateCartCount() {
  updateCommerce();
  $("#cart-count").textContent = Object.values(cart).reduce(
    (sum, n) => sum + n,
    0,
  );
}
function renderCart() {
  const list = $("#cart-items");
  list.replaceChildren();
  let total = 0;
  for (const [id, quantity] of Object.entries(cart)) {
    const book = books.find((b) => String(b.id) === id);
    if (!book) {
      delete cart[id];
      continue;
    }
    total += quantity;
    const row = node("article", undefined, "cart-row");
    row.append(coverImage(book));
    const info = node("div");
    info.append(
      node("h3", book.name),
      node("p", book.author),
      node(
        "p",
        book.price === null
          ? "Цена не указана"
          : new Intl.NumberFormat("ru-RU", {
              style: "currency",
              currency: "RUB",
            }).format(book.price),
      ),
    );
    const controls = node("div", undefined, "quantity-controls");
    const minus = action(
      "−",
      () => setCartQuantity(id, quantity - 1),
      "quantity-button",
    );
    minus.setAttribute("aria-label", "Уменьшить количество: " + book.name);
    const plus = action(
      "+",
      () => setCartQuantity(id, Math.min(99, quantity + 1)),
      "quantity-button",
    );
    plus.disabled = quantity >= 99;
    plus.setAttribute("aria-label", "Увеличить количество: " + book.name);
    controls.append(
      minus,
      node("span", String(quantity)),
      plus,
      action("Удалить", () => setCartQuantity(id, 0), "text-button"),
    );
    info.append(controls);
    row.append(info);
    list.append(row);
  }
  if (!total)
    list.append(
      node(
        "p",
        "Ваша корзина пока пуста. Найдите книгу в каталоге и нажмите «В корзину».",
      ),
    );
  $("#cart-total").textContent = "Экземпляров: " + total;
  $("#clear-cart").disabled = !total;
  updateCartCount();
}
function renderCategories() {
  const target = $("#categories");
  target.replaceChildren();
  for (const name of ["all", ...categoryNames]) {
    const count = books.filter(
      (b) => name === "all" || b.category === name,
    ).length;
    const button = action(
      (name === "all" ? "Все разделы" : name) + " · " + count,
      () => {
        category = name;
        page = 1;
        render();
      },
      "category-chip" + (category === name ? " active" : ""),
    );
    button.setAttribute("aria-pressed", category === name);
    target.append(button);
  }
}
function render() {
  const q = $("#search").value.trim().toLocaleLowerCase("ru");
  const visible = books.filter(
    (b) =>
      (b.name + " " + b.author).toLocaleLowerCase("ru").includes(q) &&
      (filter !== "favorites" || favorites.has(String(b.id))) &&
      (category === "all" || b.category === category),
  );
  const sort = $("#sort").value;
  if (sort !== "default")
    visible.sort((a, b) => a[sort].localeCompare(b[sort], "ru"));
  const pages = Math.max(1, Math.ceil(visible.length / pageSize));
  page = Math.min(page, pages);
  $("#books").replaceChildren();
  $("#catalog-count").textContent = `Найдено: ${visible.length}`;
  $("#favorite-count").textContent = books.filter((b) =>
    favorites.has(String(b.id)),
  ).length;
  renderCategories();
  if (!visible.length)
    $("#books").append(
      node(
        "p",
        filter === "favorites"
          ? "На этой полке пока нет книг. Добавляйте их сердечком в каталоге."
          : "Книги не найдены. Измените запрос или раздел.",
      ),
    );
  visible.slice((page - 1) * pageSize, page * pageSize).forEach((b) => {
    const card = node("article", undefined, "book-card");
    const image = node("div", undefined, "book-image");
    const open = action("", () => openBook(b), "cover-button");
    open.setAttribute("aria-label", "Подробнее: " + b.name);
    open.append(coverImage(b));
    image.append(open);
    const saved = favorites.has(String(b.id));
    const button = action(
      saved ? "♥" : "♡",
      () => toggleFavorite(b),
      "favorite" + (saved ? " selected" : ""),
    );
    button.setAttribute(
      "aria-label",
      (saved ? "Убрать с полки: " : "Добавить на полку: ") + b.name,
    );
    button.setAttribute("aria-pressed", saved);
    image.append(button);
    const title = node("h3");
    title.append(action(b.name, () => openBook(b), "book-title"));
    card.append(
      image,
      node("span", b.category || "Каталог", "category-label"),
      title,
      node("p", b.author),
    );
    card.append(
      node(
        "strong",
        b.price === null
          ? "Цена уточняется"
          : new Intl.NumberFormat("ru-RU", {
              style: "currency",
              currency: "RUB",
            }).format(b.price),
        "book-price",
      ),
      node("p", "Доступно: " + b.availableStock, "stock-caption"),
    );
    const actions = node("div", undefined, "book-actions");
    actions.append(
      action("В корзину +", () => addToCart(b), "button dark small-button"),
      action("Отзывы ↗", () => openBook(b), "text-button"),
    );
    card.append(actions);
    $("#books").append(card);
  });
  const pager = $("#pagination");
  pager.replaceChildren();
  if (pages > 1) {
    const prev = action(
      "← Назад",
      () => {
        page--;
        render();
        $("#catalog").scrollIntoView();
      },
      "page-button",
    );
    prev.disabled = page === 1;
    const next = action(
      "Далее →",
      () => {
        page++;
        render();
        $("#catalog").scrollIntoView();
      },
      "page-button",
    );
    next.disabled = page === pages;
    pager.append(prev, node("span", `${page} / ${pages}`), next);
  }
  updateCartCount();
}
async function loadBooks() {
  try {
    const data = await request("/books");
    books = data;
    $("#catalog-message").textContent = data.length
      ? "Редакционная подборка известных произведений. Иллюстрированные обложки — оформление каталога."
      : "Каталог пока пуст. Добавьте первую книгу.";
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
    action("Добавить в корзину +", () => addToCart(book)),
  );
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
  avatar($("#account .avatar"));
  if (user) {
    $("#profile-name").textContent = user.displayName;
    $("#profile-email").textContent = user.email;
    avatar($("#profile-avatar"));
  }
  updateCommerce();
  if (previous !== user?.email || !selectionReady) await loadSelection();
}
$("#search").oninput = () => {
  page = 1;
  render();
};
$("#sort").onchange = () => {
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
  if (selectionBusy) {
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
$("#add-book").onclick = () => {
  if (user?.role !== "ADMIN") {
    toast("Добавлять книги может администратор");
    $("#auth-dialog").showModal();
    return;
  }
  $("#book-dialog").showModal();
};
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
$("#clear-cart").onclick = () =>
  changeSelection(async () => {
    if (user) await request("/api/selection/cart", { method: "DELETE" });
    cart = {};
    if (!user) saveGuestSelection();
  });
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
}
setupCommerce({
  getUser: () => user,
  getBooks: () => books,
  getCart: () => cart,
  toast,
  reload: async () => {
    await loadBooks();
    await loadSelection();
  },
});
init();
