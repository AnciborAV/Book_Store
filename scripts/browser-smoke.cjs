// Сквозная проверка реального JAR и файловой H2, включая повторный запуск.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const { spawn } = require("node:child_process");
const { mkdtempSync, createWriteStream, mkdirSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { once } = require("node:events");
const assert = require("node:assert/strict");
const directory = mkdtempSync(join(tmpdir(), "bookstore-smoke-"));
const base = "http://127.0.0.1:8080";
let server;
let browser;
async function start() {
  server = spawn("java", [
    "-jar",
    "target/book-store-0.0.1-SNAPSHOT.jar",
    "--spring.profiles.active=local",
    "--bookstore.admin.email=admin@example.com",
    "--bookstore.admin.password=admin-test-password",
    `--spring.datasource.url=jdbc:h2:file:${directory}/books;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`,
  ]);
  const log = createWriteStream("target/browser-server.log", { flags: "a" });
  server.stdout.pipe(log);
  server.stderr.pipe(log);
  for (let i = 0; i < 120; i++) {
    if (server.exitCode !== null)
      throw Error("Application exited before becoming ready");
    try {
      if ((await fetch(base)).ok) return;
    } catch (_) {}
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw Error("Application startup timeout");
}
async function stop() {
  if (server && server.exitCode === null) {
    const exited = once(server, "exit");
    server.kill("SIGTERM");
    await exited;
  }
}
(async () => {
  await start();
  browser = await chromium.launch({ headless: true });
  let context = await browser.newContext();
  let page = await context.newPage();
  const errors = [];
  const watch = () =>
    page.on("pageerror", (error) => errors.push(error.message));
  watch();
  async function login(email, password) {
    await page.locator("#account").click();
    await page.locator("#auth-form [name=email]").fill(email);
    await page.locator("#auth-form [name=password]").fill(password);
    await page.locator("#auth-submit").click();
    await page.locator("#profile-dialog").waitFor({ state: "visible" });
  }
  async function logout() {
    for (const id of [
      "#admin-dialog",
      "#orders-dialog",
      "#detail-dialog",
      "#cart-dialog",
    ])
      if (await page.locator(id).isVisible())
        await page.locator(id + " .close").click();
    if (!(await page.locator("#profile-dialog").isVisible()))
      await page.locator("#account").click();
    await page.locator("#logout").click();
    await page.locator("#account-label").filter({ hasText: "Войти" }).waitFor();
  }
  await page.goto(base);
  await login("admin@example.com", "admin-test-password");
  await page.locator("#profile-dialog .close").click();
  await page.locator("#add-book").click();
  await page.locator("#book-form [name=name]").fill("Проверка заказа");
  await page.locator("#book-form [name=author]").fill("Автор");
  await page.locator("#book-form button").click();
  await page.locator("#book-dialog").waitFor({ state: "hidden" });
  await page.locator("#account").click();
  await page.locator("#open-admin").click();
  await page.locator("#inventory-search").fill("Проверка заказа");
  await page.locator("#inventory-edit [name=name]").waitFor();
  await page.waitForFunction(
    () =>
      document.querySelector("#inventory-edit [name=name]").value ===
      "Проверка заказа",
  );
  await page.locator("#inventory-edit [name=price]").fill("10.25");
  await page.locator("#inventory-edit button").click();
  await page
    .locator("#toast")
    .filter({ hasText: "Карточка и цена сохранены" })
    .waitFor();
  await page.locator("#receipt-form [name=quantity]").fill("3");
  await page.locator("#receipt-form [name=note]").fill("Тестовая накладная");
  await page.locator("#receipt-form button").click();
  await page
    .locator("#stock-summary")
    .filter({ hasText: "На складе: 3" })
    .waitFor();
  await page
    .locator("#stock-history")
    .filter({ hasText: "Тестовая накладная" })
    .waitFor();
  mkdirSync("target/browser-screenshots", { recursive: true });
  await page.screenshot({
    path: "target/browser-screenshots/admin.png",
    fullPage: true,
  });
  await logout();
  await page.locator("#account").click();
  await page.locator("#auth-toggle").click();
  await page.locator("#auth-form [name=displayName]").fill("Покупатель");
  await page.locator("#auth-form [name=email]").fill("buyer@example.com");
  await page.locator("#auth-form [name=password]").fill("buyer-test-password");
  await page.locator("#auth-submit").click();
  await page.locator("#profile-dialog").waitFor({ state: "visible" });
  assert.equal(await page.locator("#open-admin").isVisible(), false);
  await page.locator("#profile-dialog .close").click();
  assert.equal(await page.locator("#add-book").isVisible(), false);
  await page.locator("#search").fill("Проверка заказа");
  await page
    .locator(".book-card")
    .filter({ hasText: "Проверка заказа" })
    .waitFor();
  await page.locator(".favorite").click();
  await page.locator(".favorite.selected").waitFor();
  await page.locator(".book-actions .button").click();
  await page.locator("#cart-count").filter({ hasText: "1" }).waitFor();
  await page.locator(".book-title").click();
  await page.locator("#review-form [name=body]").fill("Проверка отзыва");
  await page.locator("#review-form button").click();
  await page
    .locator(".review")
    .filter({ hasText: "Проверка отзыва" })
    .waitFor();
  await page.locator("#detail-dialog .close").click();
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 });
    assert.equal(
      await page.evaluate(
        () => document.documentElement.scrollWidth > innerWidth,
      ),
      false,
    );
    await page.screenshot({
      path: `target/browser-screenshots/${width}.png`,
      fullPage: true,
    });
  }
  await page.locator("#open-cart").click();
  await page.locator("#checkout").click();
  await page.locator("#orders-dialog").waitFor({ state: "visible" });
  await page.locator("#order-detail").filter({ hasText: "Создан" }).waitFor();
  let orders = await (await page.request.get(base + "/api/orders")).json();
  const first = orders[0].id;
  assert.equal(Number(orders[0].total), 10.25);
  await context.close();
  await stop();
  await start();
  context = await browser.newContext();
  page = await context.newPage();
  watch();
  await page.goto(base);
  await login("buyer@example.com", "buyer-test-password");
  await page.locator("#open-orders").click();
  await page.locator("#orders-list button").click();
  await page.locator("#order-detail").filter({ hasText: first }).waitFor();
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Отменить заказ", exact: true })
    .click();
  await page
    .locator("#order-detail > p")
    .filter({ hasText: "Отменён" })
    .waitFor();
  await page.locator("#orders-dialog .close").click();
  await page.locator("[data-filter=favorites]").click();
  await page.locator(".favorite.selected").waitFor();
  await page.locator(".book-title").click();
  await page
    .locator(".review")
    .filter({ hasText: "Проверка отзыва" })
    .waitFor();
  await page.locator("#detail-dialog .close").click();
  await page.locator(".book-actions .button").click();
  await page.locator("#cart-count").filter({ hasText: "1" }).waitFor();
  await page.locator("#open-cart").click();
  await page.locator("#checkout").click();
  await page.locator("#order-detail").filter({ hasText: "Создан" }).waitFor();
  orders = await (await page.request.get(base + "/api/orders")).json();
  const second = orders[0].id;
  assert.notEqual(first, second);
  await logout();
  await login("admin@example.com", "admin-test-password");
  await page.locator("#open-admin").click();
  await page.locator("#admin-orders").click();
  await page
    .locator(".order-row")
    .filter({ hasText: second.slice(0, 8) })
    .getByRole("button")
    .click();
  for (const status of ["Подтверждён", "Собран", "Отправлен", "Завершён"]) {
    await page
      .getByRole("button", { name: "Перевести: " + status, exact: true })
      .click();
    await page
      .locator("#order-detail > p")
      .filter({ hasText: status })
      .waitFor();
  }
  await logout();
  await login("buyer@example.com", "buyer-test-password");
  await page.locator("#open-orders").click();
  await page
    .locator(".order-row")
    .filter({ hasText: second.slice(0, 8) })
    .filter({ hasText: "Завершён" })
    .waitFor();
  await page.screenshot({
    path: "target/browser-screenshots/orders.png",
    fullPage: true,
  });
  assert.deepEqual(errors, []);
  console.log(
    "PASS: admin role, book creation, price, receipt audit, customer isolation, favorites, reviews, checkout, H2 restart, cancellation, fulfillment and purchase history.",
  );
})()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    if (browser) await browser.close();
    await stop();
  });
