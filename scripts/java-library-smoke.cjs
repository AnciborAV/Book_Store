const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const {spawn}=require('node:child_process');
const {mkdtempSync,createWriteStream,readFileSync,mkdirSync}=require('node:fs');
const {tmpdir}=require('node:os');
const {join}=require('node:path');
const {once}=require('node:events');
const assert=require('node:assert/strict');
const base='http://127.0.0.1:8080';
const dir=mkdtempSync(join(tmpdir(),'java-library-'));
let server,browser;
async function start(){
 server=spawn('java',['-jar','target/book-store-0.0.1-SNAPSHOT.jar','--spring.profiles.active=local',
  '--bookstore.admin.email=java-admin@example.com','--bookstore.admin.password=java-admin-password',
  `--spring.datasource.url=jdbc:h2:file:${dir}/books;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`]);
 const log=createWriteStream('target/java-library-server.log',{flags:'a'});server.stdout.pipe(log);server.stderr.pipe(log);
 for(let i=0;i<120;i++){if(server.exitCode!==null)throw Error('Server stopped');try{if((await fetch(base)).ok)return;}catch{} await new Promise(r=>setTimeout(r,500));}
 throw Error('Server startup timeout');
}
async function stop(){if(server&&server.exitCode===null){const done=once(server,'exit');server.kill('SIGTERM');await done;}}
(async()=>{
 await start();browser=await chromium.launch({headless:true});
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto(base);await page.getByRole('button',{name:'Java',exact:true}).click();
 assert.equal(await page.locator('#java-add').isVisible(),false);
 await page.locator('#account').click();await page.locator('#auth-form [name=email]').fill('java-admin@example.com');
 await page.locator('#auth-form [name=password]').fill('java-admin-password');await page.locator('#auth-submit').click();
 await page.locator('#profile-dialog').waitFor({state:'visible'});await page.locator('#profile-dialog .close').click();
 await page.locator('#java-add').click();await page.locator('#java-form [name=title]').fill('Java: первая книга');
 await page.locator('#java-form [name=pdf]').setInputFiles('src/test/resources/java-library-sample.pdf');
 await page.locator('#java-save').click();await page.locator('#java-editor').waitFor({state:'hidden'});
 await page.locator('.java-card h3').filter({hasText:'Java: первая книга'}).waitFor();
 const downloadPath=await page.locator('.java-card a').getAttribute('href');
 const downloadEvent = page.waitForEvent('download');
 await page.locator('.java-card a').click();
 assert.equal(await (await downloadEvent).failure(), null);
 assert.equal(await page.locator('.java-card a').textContent(), 'Скачать ещё раз ↓');
 assert.ok(await page.locator('.java-card a').evaluate(n=>n.classList.contains('java-downloaded')));
 await page.reload();await page.getByRole('button',{name:'Java',exact:true}).click();
 await page.locator('.java-card a.java-downloaded').waitFor();
 await page.getByRole('button',{name:'Сбросить отметку',exact:true}).click();
 assert.equal(await page.locator('.java-card a').textContent(), 'Скачать PDF ↓');
 // Admin review inbox is accessible and clearly indicates disabled email.
 await page.locator('#account').click();
 await page.locator('#open-review-inbox').click();
 await page.locator('#admin-reviews-dialog').waitFor({state:'visible'});
 await page.locator('#review-mail-message').filter({hasText:'выключены'}).waitFor();
 await page.locator('#review-inbox-site').click();
 await page.locator('#admin-reviews-dialog').waitFor({state:'hidden'});
 // Feedback is persisted, moderated, and rendered as text, including markup-like input.
 await page.locator('#feedback-form [name=body]').fill('<img src=x onerror=alert(1)> Спасибо за сайт');
 await page.locator('#feedback-form [name=publicConsent]').check();
 await page.locator('#feedback-form button[type=submit]').click();
 await page.locator('#feedback-result').filter({hasText:'Спасибо!'}).waitFor();
 await page.locator('#feedback-refresh').click();
 await page.locator('#feedback-inbox .feedback-card').waitFor();
 assert.equal(await page.locator('#feedback-inbox .feedback-card img').count(),0);
 page.once('dialog', d=>d.accept());
 await page.locator('#feedback-inbox').getByRole('button',{name:'Опубликовать',exact:true}).click();
 await page.locator('#feedback-public .feedback-card').waitFor();
 await page.locator('#site-feedback').scrollIntoViewIfNeeded();
 mkdirSync('target/browser-screenshots',{recursive:true});
 await page.screenshot({path:'target/browser-screenshots/feedback-desktop.png'});

 const source=readFileSync('src/test/resources/java-library-sample.pdf');
 assert.deepEqual(Buffer.from(await (await fetch(base+downloadPath)).arrayBuffer()),source);
 await page.getByRole('button',{name:'Редактировать',exact:true}).click();
 await page.locator('#java-form [name=title]').fill('Java: обновлённая книга');await page.locator('#java-save').click();
 await page.locator('#java-editor').waitFor({state:'hidden'});
 await page.locator('.java-card h3').filter({hasText:'Java: обновлённая книга'}).waitFor();
 mkdirSync('target/browser-screenshots',{recursive:true});
 for(const width of [1440,390]){
  await page.setViewportSize({width,height:1000});await page.locator('#java-library').scrollIntoViewIfNeeded();
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1));
  await page.screenshot({path:`target/browser-screenshots/java-${width}.png`});
 }
 await stop();await start();
 const guest=await browser.newPage();await guest.goto(base);await guest.getByRole('button',{name:'Java',exact:true}).click();
 await guest.locator('.java-card h3').filter({hasText:'Java: обновлённая книга'}).waitFor();
 assert.equal(await guest.getByRole('button',{name:'Редактировать',exact:true}).count(),0);
 await guest.locator('#feedback-public .feedback-card').waitFor();
 assert.equal(await guest.locator('#feedback-admin').isVisible(),false);
 assert.equal(await guest.locator('.java-card a').textContent(),'Скачать PDF ↓');
 assert.deepEqual(Buffer.from(await(await fetch(base+downloadPath)).arrayBuffer()),source);
 assert.deepEqual(errors,[]);console.log('Java library upload/edit/public download/restart/mobile passed');
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{await browser?.close();await stop();});
