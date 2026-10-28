import { request } from './api-client.js';
let context, generation=0, page=0;
const $=id=>document.getElementById(id);
const el=(tag,text,cls)=>{const n=document.createElement(tag);n.textContent=text;if(cls)n.className=cls;return n;};
function button(text,action){const n=el('button',text,'button light');n.type='button';n.onclick=action;return n;}
export function setupAdminReviews(ctx){
  context=ctx;
  const open=button('Отзывы и уведомления',()=>{ $('profile-dialog').close();$('admin-reviews-dialog').showModal();page=0;load();});
  open.id='open-review-inbox';open.hidden=true;$('logout').before(open);
  const d=document.createElement('dialog');d.id='admin-reviews-dialog';d.className='commerce-dialog';
  d.setAttribute('aria-labelledby','review-inbox-title');
  d.innerHTML=`<button class="close" aria-label="Закрыть">×</button><div class="eyebrow">ОБРАТНАЯ СВЯЗЬ</div>
    <h2 id="review-inbox-title">Отзывы и уведомления</h2><p id="review-inbox-summary" role="status"></p>
    <div class="feedback-controls"><button id="review-inbox-books" class="button dark">Книги</button>
    <button id="review-inbox-site" class="button light">О сайте</button><button id="review-inbox-refresh" class="button light">Обновить</button></div>
    <section class="review-mail-status"><h3>Уведомления на почту</h3><p id="review-mail-message"></p><p id="review-mail-counts"></p>
    <button id="review-mail-retry" class="button light" hidden>Повторить неудачные отправки</button></section>
    <label class="feedback-consent"><input id="review-inbox-unread" type="checkbox"> Только непрочитанные отзывы о книгах</label>
    <div id="review-inbox-list"></div><p id="review-inbox-error" role="alert"></p>`;
  document.body.append(d);d.querySelector('.close').onclick=()=>d.close();
  $('review-inbox-refresh').onclick=()=>load();
  $('review-inbox-books').onclick=()=>{page=0;load();};
  $('review-inbox-unread').onchange=()=>{page=0;load();};
  $('review-inbox-site').onclick=()=>{d.close();$('site-feedback').scrollIntoView({behavior:'smooth'});$('feedback-refresh').click();};
  $('review-mail-retry').onclick=async()=>{
    const b=$('review-mail-retry');b.disabled=true;
    try{const r=await request('/api/admin/reviews/retry-mail',{method:'POST'});await load();$('review-inbox-error').textContent=`Возвращено в очередь: ${r.queued}`;}
    catch(e){$('review-inbox-error').textContent=e.message;}finally{b.disabled=false;}
  };
}
export function updateAdminReviews(){
  generation++;const admin=context.getUser()?.role==='ADMIN';$('open-review-inbox').hidden=!admin;
  if(!admin){$('admin-reviews-dialog').close();$('review-inbox-list').replaceChildren();$('review-inbox-summary').textContent='';$('review-mail-message').textContent='';$('review-mail-counts').textContent='';}
}
async function load(){
  if(context.getUser()?.role!=='ADMIN')return;
  const stamp=++generation;const list=$('review-inbox-list');list.replaceChildren(el('p','Загрузка…'));$('review-inbox-error').textContent='';
  try{
    const [data,status]=await Promise.all([request(`/api/admin/reviews?page=${page}&unread=${$('review-inbox-unread').checked}`),request('/api/admin/reviews/status')]);
    if(stamp!==generation)return;
    $('review-inbox-summary').textContent=`Непрочитанных отзывов о книгах: ${status.unreadBooks}. Сообщений о сайте на проверке: ${status.pendingSite}.`;
    $('review-inbox-site').textContent=`О сайте (${status.pendingSite})`;
    $('review-mail-message').textContent=status.mailMessage;
    $('review-mail-counts').textContent=`Ожидают: ${status.pendingMail} · Не отправлены после 5 попыток: ${status.failedMail} · Переданы почтовому сервису: ${status.sentMail}`;
    $('review-mail-retry').hidden=!status.mailConfigured||!status.failedMail;
    list.replaceChildren();
    if(!data.items.length)list.append(el('p','Отзывов в этом списке пока нет.'));
    for(const item of data.items){
      const card=el('article','','feedback-card');
      card.append(el('h3',item.book),el('p',item.bookAuthor,'muted'),el('strong',item.author),
        el('p',`${'★'.repeat(item.rating)}${'☆'.repeat(5-item.rating)} · ${new Date(item.created).toLocaleString('ru-RU')}${item.unread?' · Не прочитан':''}`),el('p',item.body,'feedback-body'));
      if(item.unread)card.append(button('Отметить прочитанным',async e=>{
        e.currentTarget.disabled=true;
        try{await request(`/api/admin/reviews/${item.id}/read`,{method:'POST'});await load();}
        catch(error){$('review-inbox-error').textContent=error.message;e.target.disabled=false;}
      }));
      list.append(card);
    }
    const nav=el('div','','feedback-controls');
    if(page>0)nav.append(button('← Назад',()=>{page--;load();}));
    nav.append(el('span',`Всего: ${data.total} · Страница ${page+1}`));
    if((page+1)*20<data.total)nav.append(button('Далее →',()=>{page++;load();}));list.append(nav);
  }catch(e){if(stamp===generation){list.replaceChildren();$('review-inbox-error').textContent=e.message;}}
}
