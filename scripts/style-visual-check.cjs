/* Compare the static presentation before/after CSS formatting, without running application scripts. */
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const {execFileSync} = require('node:child_process');
const {createRequire} = require('node:module');
const requirePlaywright = process.env.PLAYWRIGHT_MODULE
  ? createRequire(path.join(process.env.PLAYWRIGHT_MODULE, 'package.json')) : require;
const {chromium} = requirePlaywright('playwright');
const root = path.resolve('src/main/resources/static');
const output = path.resolve('target/style-visual');
const baseline = process.env.VISUAL_BASE_SHA;
assert.match(baseline || '', /^[0-9a-f]{40}$/);
const pages = ['ai-architects.html', 'ai-atlas.html', 'ai-history.html', 'ai-models.html',
  'ai-russia.html', 'digital-atlas.html', 'index.html', 'inside-pereplet.html',
  'pdf-download-code.html', 'pdf-reading-code.html', 'programming-atlas.html',
  'reader.html', 'reading-atlas.html', 'remember-login-code.html'];
const styles = ['about.css', 'ai-atlas.css', 'ai-models.css', 'ai-russia.css', 'ai-story.css',
  'ai.css', 'announcement.css', 'atlas-navigation.css', 'book-ordering.css', 'catalogue-admin.css',
  'digital-atlas.css', 'editorial.css', 'feedback.css', 'inside-pereplet.css', 'password-recovery.css',
  'pdf-reading-code.css', 'programming-atlas.css', 'reader.css', 'reading-atlas.css',
  'reading-text.css', 'style.css', 'text-formatting.css'];
const cache = new Map();
async function source(name, before) {
  const key = `${before}:${name}`;
  if (!cache.has(key)) {
    cache.set(key, before && /\.(css|html)$/.test(name)
      ? execFileSync('git', ['show', `${baseline}:src/main/resources/static/${name}`], {maxBuffer: 20 * 1024 * 1024})
      : await fs.readFile(path.join(root, name)));
  }
  return cache.get(key);
}
const types = {'.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8',
  '.svg':'image/svg+xml', '.png':'image/png', '.jpg':'image/jpeg', '.jpeg':'image/jpeg',
  '.woff2':'font/woff2', '.woff':'font/woff', '.ttf':'font/ttf'};
async function createPage(browser, before, width) {
  const context = await browser.newContext({viewport:{width,height:1000}, deviceScaleFactor:1,
    javaScriptEnabled:false, reducedMotion:'reduce', colorScheme:'dark'});
  await context.route('**/*', async route => {
    const url = new URL(route.request().url());
    if (url.origin !== 'https://style.test') return route.abort();
    const name = decodeURIComponent(url.pathname).replace(/^\//, '');
    if (!name || name.includes('..')) return route.fulfill({status:404,body:''});
    try {
      return route.fulfill({body:await source(name, before), contentType:types[path.extname(name)] || 'application/octet-stream'});
    } catch {
      return route.fulfill({status:404,body:''});
    }
  });
  return {context,page:await context.newPage()};
}
(async () => {
  await fs.mkdir(output,{recursive:true});
  const browser = await chromium.launch({headless:true});
  try {
    for (const width of [390,1440]) {
      const before = await createPage(browser,true,width);
      const after = await createPage(browser,false,width);
      try {
        for (const name of pages) {
          const shots=[];
          for (const [label,item] of [['before',before],['after',after]]) {
            await item.page.goto(`https://style.test/${name}`,{waitUntil:'networkidle'});
            await item.page.evaluate(() => document.fonts.ready);
            shots.push(await item.page.screenshot({path:path.join(output,`${name}-${width}-${label}.png`),
              fullPage:true, animations:'disabled'}));
          }
          assert.ok(shots[0].equals(shots[1]),`Visual difference: ${name} at ${width}`);
        }
        // CSSOM comparison also covers styles used only in script-created dialogs and cards.
        const cssoms=[];
        for (const item of [before,after]) {
          await item.page.goto('https://style.test/index.html',{waitUntil:'networkidle'});
          cssoms.push(await item.page.evaluate(async names => {
            const result={};
            for (const name of names) {
              const text=await (await fetch('/'+name)).text();
              const sheet=new CSSStyleSheet();
              sheet.replaceSync(text);
              result[name]=Array.from(sheet.cssRules,rule=>rule.cssText);
            }
            return result;
          }, styles));
        }
        assert.deepEqual(cssoms[1],cssoms[0],`CSSOM differs at ${width}`);
      } finally {
        await before.context.close();
        await after.context.close();
      }
    }
    console.log('PASS: 14 static HTML pages × 2 widths are pixel-identical; all 22 CSSOMs match.');
    console.log('Scope: scripts disabled; this does not replace authenticated interaction tests.');
  } finally {
    await browser.close();
  }
})().catch(error=>{console.error(error);process.exitCode=1;});
