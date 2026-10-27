import { request } from "./api-client.js";

const $ = (s) => document.querySelector(s);
const labels = {
  CREATED: "Создан",
  CONFIRMED: "Подтверждён",
  PACKED: "Собран",
  SHIPPED: "Отправлен",
  COMPLETED: "Завершён",
  CANCELLED: "Отменён",
};
const nextStatus = {
  CREATED: "CONFIRMED",
  CONFIRMED: "PACKED",
  PACKED: "SHIPPED",
  SHIPPED: "COMPLETED",
};
const money = (value) =>
  new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB" }).format(
    value,
  );
const date = (value) => new Date(value).toLocaleString("ru-RU");
const el = (tag, text, cls) => {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
};
function button(text, action) {
  const b = el("button", text, "button dark small-button");
  b.type = "button";
  b.onclick = async () => {
    b.disabled = true;
    try {
      await action();
    } catch (e) {
      notify(e.message);
    } finally {
      b.disabled = false;
    }
  };
  return b;
}
function json(method, body) {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}
let context,
  notify,
  inventory = [],
  orderPage = 0,
  movementPage = 0,
  adminOrders = false;
const retryCache = new Map();
// Persist retry identity across reloads; a network retry must not create another order/receipt.
function retryKey(kind, fingerprint) {
  const key = "bookstore-" + kind + "-" + context.getUser().email;
  let saved = retryCache.get(key);
  try {
    saved = JSON.parse(sessionStorage.getItem(key) || "null") || saved;
  } catch (_) {}
  if (!saved || saved.fingerprint !== fingerprint) {
    saved = { fingerprint, key: crypto.randomUUID() };
    try {
      sessionStorage.setItem(key, JSON.stringify(saved));
    } catch (_) {}
  }
  retryCache.set(key, saved);
  return saved.key;
}
function clearRetry(kind) {
  retryCache.delete("bookstore-" + kind + "-" + context.getUser().email);
  try {
    sessionStorage.removeItem(
      "bookstore-" + kind + "-" + context.getUser().email,
    );
  } catch (_) {}
}
export function setupCommerce(options) {
  context = options;
  notify = options.toast;
  $("#open-orders").onclick = async () => {
    adminOrders = false;
    orderPage = 0;
    $("#profile-dialog").close();
    $("#orders-dialog").showModal();
    await loadOrders().catch((e) => notify(e.message));
  };
  $("#open-admin").onclick = async () => {
    $("#profile-dialog").close();
    $("#admin-dialog").showModal();
    await loadInventory().catch((e) => notify(e.message));
  };
  $("#admin-orders").onclick = async () => {
    $("#admin-dialog").close();
    adminOrders = true;
    orderPage = 0;
    $("#orders-dialog").showModal();
    await loadOrders().catch((e) => notify(e.message));
  };
  $("#admin-movements").onclick = async () => {
    movementPage = 0;
    await loadMovements().catch((e) => notify(e.message));
  };
  $("#inventory-search").oninput = renderInventoryOptions;
  $("#inventory-book").onchange = fillBook;
  $("#inventory-edit").onsubmit = async (e) => {
    e.preventDefault();
    const form = e.target;
    const b = form.querySelector("button");
    b.disabled = true;
    try {
      const data = Object.fromEntries(new FormData(form));
      await request(
        "/api/admin/books/" + $("#inventory-book").value,
        json("PUT", { ...data, price: data.price }),
      );
      await context.reload();
      await loadInventory();
      notify("Карточка и цена сохранены");
    } catch (err) {
      notify(err.message);
    } finally {
      b.disabled = false;
    }
  };
  $("#receipt-form").onsubmit = async (e) => {
    e.preventDefault();
    const form = e.target,
      b = form.querySelector("button");
    b.disabled = true;
    try {
      const id = $("#inventory-book").value;
      const data = {
        quantity: Number(form.elements.quantity.value),
        note: form.elements.note.value,
      };
      data.requestKey = retryKey("receipt", JSON.stringify({ id, ...data }));
      await request("/api/admin/books/" + id + "/receipts", json("POST", data));
      clearRetry("receipt");
      form.reset();
      await context.reload();
      await loadInventory();
      await loadMovements();
      notify("Поступление зарегистрировано");
    } catch (err) {
      notify(err.message);
    } finally {
      b.disabled = false;
    }
  };
  $("#checkout").onclick = async () => {
    const b = $("#checkout");
    b.disabled = true;
    try {
      const result = await request(
        "/api/orders",
        json("POST", { requestKey: retryKey("checkout", "checkout") }),
      );
      clearRetry("checkout");
      await context.reload();
      $("#cart-dialog").close();
      adminOrders = false;
      orderPage = 0;
      $("#profile-dialog").close();
      $("#orders-dialog").showModal();
      await loadOrders();
      await showOrder(result.order.id);
      notify("Заказ создан. Книги зарезервированы.");
    } catch (err) {
      notify(err.message);
    } finally {
      updateCommerce();
    }
  };
}
export function updateCommerce() {
  if (!context) return;
  const user = context.getUser();
  $("#open-orders").hidden = !user;
  $("#open-admin").hidden = user?.role !== "ADMIN";
  $("#add-book").hidden = user?.role !== "ADMIN";
  const books = context.getBooks(),
    cart = context.getCart();
  let cents = 0,
    valid = true;
  for (const [id, q] of Object.entries(cart)) {
    const b = books.find((b) => String(b.id) === id);
    if (!b || b.price === null || b.availableStock < q) valid = false;
    if (b?.price !== null && b) cents += Math.round(Number(b.price) * 100) * q;
  }
  $("#cart-amount").textContent =
    "Сумма: " +
    money(cents / 100) +
    (valid ? "" : " · Есть позиции без цены или доступного остатка");
  $("#checkout").disabled = !user || !Object.keys(cart).length;
  $("#checkout-note").textContent = user
    ? valid
      ? "Оформление резервирует книги. Онлайн-оплата не подключена."
      : "Администратору нужно назначить любую положительную цену и оформить поступление недостающих книг в разделе «Управление магазином»."
    : "Войдите, чтобы оформить заказ. Гостевая корзина не переносится в аккаунт.";
}
async function loadInventory() {
  inventory = await request("/api/admin/inventory");
  renderInventoryOptions();
}
function renderInventoryOptions() {
  const select = $("#inventory-book"),
    previous = select.value,
    q = $("#inventory-search").value.toLocaleLowerCase("ru");
  select.replaceChildren();
  for (const b of inventory.filter((b) =>
    (b.name + " " + b.author).toLocaleLowerCase("ru").includes(q),
  )) {
    const o = el("option", b.name + " — " + b.author);
    o.value = b.bookId;
    select.append(o);
  }
  if ([...select.options].some((o) => o.value === previous))
    select.value = previous;
  fillBook();
}
function fillBook() {
  const book = inventory.find(
    (b) => String(b.bookId) === $("#inventory-book").value,
  );
  for (const form of [$("#inventory-edit"), $("#receipt-form")])
    for (const field of form.elements) field.disabled = !book;
  if (!book) {
    $("#stock-summary").textContent = "Книги не найдены";
    return;
  }
  const form = $("#inventory-edit");
  for (const key of ["name", "author", "category", "price"])
    form.elements[key].value = book[key] ?? "";
  $("#stock-summary").textContent =
    `На складе: ${book.stock} · В резерве: ${book.reserved} · Доступно: ${book.available}`;
}
async function loadOrders() {
  $("#order-detail").replaceChildren();
  $("#orders-list").textContent = "Загрузка…";
  $("#orders-heading").textContent = adminOrders
    ? "Все заказы"
    : "Мои заказы и покупки";
  const orders = await request(
    (adminOrders ? "/api/admin/orders" : "/api/orders") + "?page=" + orderPage,
  );
  const target = $("#orders-list");
  target.replaceChildren();
  if (!orders.length) target.append(el("p", "На этой странице заказов нет."));
  for (const o of orders) {
    const row = el("article", undefined, "order-row");
    row.append(
      el("strong", "№ " + o.id.slice(0, 8)),
      el("span", labels[o.status], "status-badge"),
      el("p", date(o.createdAt) + " · " + money(o.total)),
    );
    if (adminOrders) row.append(el("p", o.buyerName + " · " + o.buyerEmail));
    row.append(button("Состав и история", () => showOrder(o.id)));
    target.append(row);
  }
  const pager = $("#orders-pagination");
  pager.replaceChildren();
  if (orderPage > 0)
    pager.append(
      button("← Назад", async () => {
        orderPage--;
        await loadOrders();
      }),
    );
  pager.append(el("span", "Страница " + (orderPage + 1)));
  if (orders.length === 50)
    pager.append(
      button("Далее →", async () => {
        orderPage++;
        await loadOrders();
      }),
    );
}
async function showOrder(id) {
  const d = await request("/api/orders/" + id),
    o = d.order,
    target = $("#order-detail");
  target.replaceChildren();
  target.append(
    el("h3", "Заказ " + o.id),
    el("p", labels[o.status] + " · " + money(o.total)),
  );
  const list = el("ul");
  for (const item of d.items)
    list.append(
      el(
        "li",
        `${item.name} — ${item.author}: ${item.quantity} × ${money(item.unitPrice)}`,
      ),
    );
  target.append(list);
  const emailStatus = await request("/api/orders/" + id + "/email");
  target.append(el("p", emailStatus.message));
  target.append(button("Обновить статус письма", () => showOrder(id)));
  target.append(el("h4", "История статусов"));
  const history = el("ol");
  for (const event of d.events)
    history.append(
      el(
        "li",
        `${date(event.createdAt)} · ${labels[event.status]} · ${event.actor}`,
      ),
    );
  target.append(history);
  const actions = el("div", undefined, "commerce-actions");
  if (adminOrders && nextStatus[o.status])
    actions.append(
      button("Перевести: " + labels[nextStatus[o.status]], async () => {
        await request(
          "/api/admin/orders/" + id + "/status",
          json("PUT", { status: nextStatus[o.status] }),
        );
        await context.reload();
        await loadOrders();
        await showOrder(id);
      }),
    );
  if (["CREATED", "CONFIRMED", "PACKED"].includes(o.status))
    actions.append(
      button("Отменить заказ", async () => {
        if (!confirm("Отменить заказ и освободить резерв книг?")) return;
        await request(
          adminOrders
            ? "/api/admin/orders/" + id + "/status"
            : "/api/orders/" + id + "/cancel",
          adminOrders
            ? json("PUT", { status: "CANCELLED" })
            : { method: "POST" },
        );
        await context.reload();
        await loadOrders();
        await showOrder(id);
      }),
    );
  target.append(actions);
  target.scrollIntoView({ block: "nearest" });
}
async function loadMovements() {
  const rows = await request("/api/admin/stock-movements?page=" + movementPage),
    target = $("#stock-history");
  target.replaceChildren();
  target.append(el("h3", "Поступления и движения склада"));
  const names = {
    RECEIPT: "Поступление",
    RESERVE: "Резерв",
    RELEASE: "Снятие резерва",
    SHIP: "Отправка",
  };
  if (!rows.length) target.append(el("p", "Записей пока нет."));
  for (const m of rows) {
    const row = el("article", undefined, "order-row");
    row.append(
      el("strong", names[m.kind] + " · " + m.bookTitle),
      el("p", `${date(m.createdAt)} · ${m.actor}`),
      el(
        "p",
        `Изменение остатка: ${m.stockDelta}; резерва: ${m.reservedDelta}. После операции: ${m.stockAfter} / ${m.reservedAfter}.`,
      ),
      el("p", m.note),
    );
    if (m.orderId) row.append(el("small", "Заказ: " + m.orderId));
    target.append(row);
  }
  const pager = el("div", undefined, "commerce-actions");
  if (movementPage > 0)
    pager.append(
      button("← Назад", async () => {
        movementPage--;
        await loadMovements();
      }),
    );
  if (rows.length === 50)
    pager.append(
      button("Далее →", async () => {
        movementPage++;
        await loadMovements();
      }),
    );
  target.append(pager);
}
