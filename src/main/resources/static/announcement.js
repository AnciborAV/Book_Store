import { request } from './api-client.js';
let context, version=0;
const $=id=>document.getElementById(id);
export function setupAnnouncement(ctx) {
  context=ctx;
  const open=document.createElement('button');
  open.id='edit-announcement'; open.className='button light'; open.type='button';
  open.textContent='Разместить объявление'; open.hidden=true;
  $('logout').before(open);
  const card=document.createElement('aside');
  card.id='owner-announcement'; card.hidden=true; card.setAttribute('aria-label','Объявление администратора');
  card.innerHTML='<div class="owner-note-label">ОТ АДМИНИСТРАТОРА</div><p id="owner-note-body"></p><p class="owner-signature">С уважением,<br>Анцибор Андрей</p>';
  document.querySelector('.reading-hero').append(card);
  const dialog=document.createElement('dialog'); dialog.id='announcement-editor';
  dialog.setAttribute('aria-labelledby','announcement-title');
  dialog.innerHTML=`<button type="button" class="close" aria-label="Закрыть">×</button>
    <div class="eyebrow">ОБРАЩЕНИЕ К ЧИТАТЕЛЯМ</div><h2 id="announcement-title">Объявление на сайте</h2>
    <form id="announcement-form"><label for="announcement-text">Ваш текст</label>
    <textarea id="announcement-text" maxlength="1500" rows="7" required placeholder="Дорогие читатели! Наш сайт активно развивается…"></textarea>
    <p class="announcement-help">До 1500 символов. Подпись «С уважением, Анцибор Андрей» добавится автоматически.</p>
    <p id="announcement-error" role="alert"></p><div class="announcement-actions">
    <button class="button dark" type="submit">Опубликовать</button>
    <button class="button light" type="button" id="announcement-hide">Скрыть объявление</button></div></form>`;
  document.body.append(dialog);
  dialog.querySelector('.close').onclick=()=>dialog.close();
  open.onclick=async()=>{
    open.disabled=true;
    try {
      const n=await request('/api/admin/announcement'); version=n.version;
      $('announcement-text').value=n.body; $('announcement-hide').hidden=!n.published;
      $('announcement-error').textContent=''; $('profile-dialog').close(); dialog.showModal();
    } catch(e) { open.textContent=e.message; }
    finally {open.disabled=false;}
  };
  $('announcement-form').onsubmit=e=>{e.preventDefault(); save(true);};
  $('announcement-hide').onclick=()=>save(false);
  refresh();
}
export function updateAnnouncement() {
  const admin=context.getUser()?.role==='ADMIN';
  $('edit-announcement').hidden=!admin;
  if(!admin) $('announcement-editor').close();
}
async function refresh() {
  try {
    const n=await request('/api/announcement');
    $('owner-note-body').textContent=n.body;
    $('owner-announcement').hidden=!n.published;
    document.dispatchEvent(new CustomEvent('announcement-changed'));
  } catch(_) { /* Keep the rest of the storefront available. */ }
}
async function save(published) {
  const buttons=$('announcement-form').querySelectorAll('button');
  buttons.forEach(b=>b.disabled=true); $('announcement-error').textContent='';
  try {
    await request('/api/admin/announcement',{method:'PUT',headers:{'Content-Type':'application/json'},
      body:JSON.stringify({body:$('announcement-text').value.trim(),published,version})});
    $('announcement-editor').close(); await refresh();
  } catch(e) { $('announcement-error').textContent=e.message; }
  finally { buttons.forEach(b=>b.disabled=false); }
}
