// Сквозная проверка реального JAR и файловой H2, включая повторный запуск.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const { spawn } = require('node:child_process');
const { mkdtempSync, createWriteStream, mkdirSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const { once } = require('node:events');
const assert = require('node:assert/strict');
const directory = mkdtempSync(join(tmpdir(), 'bookstore-smoke-'));
const base = 'http://127.0.0.1:8080';
let server;
let browser;
async function start() {
    server = spawn('java', ['-jar', 'target/book-store-0.0.1-SNAPSHOT.jar',
        '--spring.profiles.active=local',
        `--spring.datasource.url=jdbc:h2:file:${directory}/books;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`]);
    const log = createWriteStream('target/browser-server.log', { flags: 'a' });
    server.stdout.pipe(log); server.stderr.pipe(log);
    for (let i = 0; i < 120; i++) {
        if (server.exitCode !== null) throw Error('Application exited before becoming ready');
        try { if ((await fetch(base)).ok) return; } catch (_) {}
        await new Promise(resolve => setTimeout(resolve, 500));
    }
    throw Error('Application startup timeout');
}
async function stop() {
    if (server && server.exitCode === null) {
        const exited = once(server, 'exit'); server.kill('SIGTERM'); await exited;
    }
}
(async () => {
    await start();
    browser = await chromium.launch({ headless: true });
    let context = await browser.newContext();
    let page = await context.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base);
    await page.locator('#account').click();
    await page.locator('#auth-toggle').click();
    await page.locator('[name=displayName]').fill('Читатель');
    await page.locator('#auth-form [name=email]').fill('browser@example.com');
    await page.locator('#auth-form [name=password]').fill('test-password-123');
    await page.locator('#auth-submit').click();
    await page.locator('#profile-dialog').waitFor({ state: 'visible' });
    await page.locator('#profile-dialog .close').click();
    await page.locator('#add-book').click();
    await page.locator('#book-form [name=name]').fill('Проверка сохранения');
    await page.locator('#book-form [name=author]').fill('Автор');
    await page.locator('#book-form button').click();
    await page.locator('.book-card').waitFor();
    await page.locator('.favorite').click();
    await page.locator('.favorite.selected').waitFor();
    mkdirSync('target/browser-screenshots', { recursive: true });
    for (const width of [1440, 390]) {
        await page.setViewportSize({ width, height: 1000 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
        await page.screenshot({ path: `target/browser-screenshots/${width}.png`, fullPage: true });
    }
    await context.close();
    await stop();
    await start();
    context = await browser.newContext();
    page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base);
    await page.locator('#account').click();
    await page.locator('#auth-form [name=email]').fill('browser@example.com');
    await page.locator('#auth-form [name=password]').fill('test-password-123');
    await page.locator('#auth-submit').click();
    await page.locator('#profile-dialog').waitFor({ state: 'visible' });
    await page.locator('#profile-dialog .close').click();
    await page.locator('[data-filter=favorites]').click();
    await page.locator('.favorite.selected').waitFor();
    assert.equal(await page.locator('.book-card h3').innerText(), 'Проверка сохранения');
    await page.locator('.favorite').click();
    await page.locator('#catalog-message').filter({ hasText: 'пока нет книг' }).waitFor();
    await page.locator('#account').click();
    await page.locator('#logout').click();
    await page.locator('#account-label').filter({ hasText: 'Войти' }).waitFor();
    assert.deepEqual(errors, []);
    console.log('PASS: registration, login, catalogue, shelf, restart persistence, logout, desktop/mobile overflow.');
})().catch(error => { console.error(error); process.exitCode = 1; })
    .finally(async () => { if (browser) await browser.close(); await stop(); });
