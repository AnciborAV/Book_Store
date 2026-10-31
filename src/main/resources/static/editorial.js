import { request } from './api-client.js';
import {setupProsePreviews} from './editorial-prose.js';
import {setupTextFormatting,appendStyledText,decorateProse,renderComment} from './text-formatting.js';
const $ = s => document.querySelector(s);
const element = (tag, text, cls) => {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
};
function button(text, run, cls = 'button light') {
  const b = element('button', text, cls); b.type = 'button'; b.onclick = run; return b;
}
let getUser = () => null, notify = () => {}, records = [], pending, editing, saving = false;
let cacheGeneration = 0, coverMap;
let manage = false, listName = '', query = '', loadNumber = 0, detailNumber = 0;
let refreshProsePreviews=()=>{}, textEditors={};
const admin = () => getUser()?.role === 'ADMIN';
const key = b => b.kind + ':' + b.id;
async function all(refresh = false) {
  if (!pending || refresh) {
    const generation=++cacheGeneration;
    pending = request('/api/editorial/books?recommended=false').then(data => { if(generation===cacheGeneration)records = data; renderHeroBook(); return data; })
      .catch(e => { if(generation===cacheGeneration)pending = null; throw e; });
  }
  return pending;
}
function fillCommentPreview(note, book) {
  note.classList.add('editorial-note-preview');
  const paragraph=element('div');renderComment(paragraph,book.comment,book.commentStyle);
  note.append(element('strong', 'Комментарий редактора'),paragraph);
  const more = button('Читать полностью', () => showDescription(book.kind, String(book.id)), 'text-button editorial-read-more');
  more.setAttribute('aria-label', 'Читать комментарий полностью: ' + book.title);
  note.append(more);
}
function paintNotes() {
  document.querySelectorAll('[data-editorial-key]').forEach(holder => {
    const book = records.find(b => key(b) === holder.dataset.editorialKey);
    holder.replaceChildren();
    if (book?.comment) {
      fillCommentPreview(holder, book);
    }
    holder.hidden = !book?.comment;
  });
}
// Both paper-book and PDF cards use exactly the same description/editor interface.
export function addEditorialControls(card, kind, book) {
  const actions = element('div', undefined, 'editorial-card-actions');
  actions.append(button('Описание', () => showDescription(kind, String(book.id)), 'button light description-button'),
    button('Автор', () => showDescription(kind, String(book.id), 'author'), 'button light author-button'));
  if (admin()) actions.append(button('Выбор редакции / комментарий', () => editBook(kind, String(book.id)), 'text-button'));
  const note = element('aside', undefined, 'editorial-note');
  note.dataset.editorialKey = kind + ':' + book.id; note.hidden = true;
  card.append(actions, note);
  all().then(paintNotes).catch(() => {}); // Description still retries on click if the initial request failed.
}
function picture(b) {
  const img = element('img'); img.alt = 'Обложка: ' + b.title; img.loading = 'lazy';
  img.src = b.kind === 'library' ? `/api/java-library/${b.id}/cover` : (b.pdfId || b.customCover) ? `/api/catalogue/covers/${b.id}?v=${b.pdfVersion}` : '/covers/default.svg';
  if (b.kind === 'catalog' && !b.pdfId && !b.customCover) {
    coverMap ||= fetch('/catalog-covers.json').then(r => r.json()).catch(() => ({}));
    coverMap.then(c => { img.src = c[b.coverTitle || b.title] || '/covers/default.svg'; });
  }
  img.onerror = () => { img.onerror = null; img.src = '/covers/default.svg'; };
  return img;
}
function renderHeroBook() {
  const hero = $('.reading-hero');
  if (!hero) return;
  let card = $('#hero-editorial-book');
  if (!card) { card=element('aside',undefined,'hero-editorial-book'); card.id='hero-editorial-book'; hero.append(card); }
  const chosen=records.filter(b=>b.lists.length);
  // A named list allows the owner to explicitly prioritize the cover on the homepage.
  const b=chosen.find(b=>b.lists.includes('На главной')) || chosen[0];
  const announcement=$('#owner-announcement');
  const hasAnnouncement=announcement && !announcement.hidden;
  card.hidden=hasAnnouncement || !b;
  hero.classList.toggle('hero-centered',!hasAnnouncement && !b);
  card.replaceChildren();
  if (!b || hasAnnouncement) return;
  card.setAttribute('aria-label','Книга, которую рекомендует редактор');
  card.append(element('div','РЕКОМЕНДУЮ ПРОЧИТАТЬ','owner-note-label'));
  const content=element('div',undefined,'hero-book-content');
  const info=element('div');
  info.append(element('h2',b.title),element('p',b.author,'hero-book-author'));
  if(b.comment) {const paragraph=element('div',undefined,'hero-book-comment');renderComment(paragraph,b.comment,b.commentStyle);info.append(paragraph);}
  info.append(button('О книге ↗',()=>showDescription(b.kind,b.id),'button light'));
  content.append(picture(b),info);card.append(content);
}
function renderCollection() {
  const target = $('#editorial-list'); target.replaceChildren();
  const selected = records.filter(b => b.lists.length);
  const names = [...new Set(selected.flatMap(b => b.lists))].sort((a,b) => a.localeCompare(b,'ru'));
  if (!names.includes(listName)) listName = '';
  const filters = $('#editorial-filters'); filters.replaceChildren();
  for (const name of ['', ...names]) {
    const b = button(name || 'Все рекомендации', () => { listName = name; renderCollection(); }, 'category-chip');
    b.setAttribute('aria-pressed', String(name === listName)); b.classList.toggle('active', name === listName); filters.append(b);
  }
  $('#editorial-manage').hidden = !admin();
  $('#editorial-manage').textContent = manage ? 'Показать рекомендации' : 'Управлять подборками';
  $('#editorial-manage').setAttribute('aria-pressed', String(manage));
  $('#editorial-help').textContent = manage
    ? 'Найдите книгу из любого каталога. В редакторе укажите списки и свой публичный комментарий. Пустой список убирает книгу из рекомендаций.'
    : 'Личный выбор редактора магазина. Только книги, которые он включил в подборки.';
  const term = query.toLocaleLowerCase('ru');
  const visible = (manage ? records : selected).filter(b => (!listName || b.lists.includes(listName)) && (b.title+' '+b.author).toLocaleLowerCase('ru').includes(term));
  $('#editorial-status').textContent = visible.length ? `Книг: ${visible.length}` : (query || listName ? 'По этому запросу книг нет.' : manage ? 'В каталоге пока нет книг.' : 'Редактор пока не добавил рекомендации.');
  for (const b of visible) {
    const card = element('article', undefined, 'editorial-book');
    const info = element('div'); info.append(element('span', b.kind === 'catalog' ? b.section : b.section + ' · PDF', 'eyebrow'), element('h3', b.title), element('p', b.author));
    if (b.lists.length) info.append(element('p', b.lists.join(' · '), 'editorial-membership'));
    if (b.comment) { const note=element('aside',undefined,'editorial-note'); fillCommentPreview(note,b); info.append(note); }
    const actions = element('div', undefined, 'editorial-card-actions');
    actions.append(button('Описание', () => showDescription(b.kind,b.id)));
    if (b.pdfId) { const read=element('a','Читать','button light'); read.href=`/reader.html?id=${encodeURIComponent(b.pdfId)}`; actions.append(read); const a=element('a','Скачать PDF ↓','button dark'); a.href=`/api/java-library/${b.pdfId}/download`; a.setAttribute('download',''); actions.append(a); }
    else actions.append(button('Открыть книгу', () => { $('#editorial-dialog').close(); document.dispatchEvent(new CustomEvent('editorial-open-catalog',{detail:b.id})); }));
    if (admin()) actions.append(button('Редактировать подборки', () => editBook(b.kind,b.id)));
    info.append(actions); card.append(picture(b),info); target.append(card);
  }
}
async function openCollection() {
  manage = false; query = ''; listName = ''; $('#editorial-search').value = '';
  $('#editorial-list').replaceChildren(); $('#editorial-status').textContent = 'Загружаем рекомендации…';
  $('#editorial-dialog').showModal();
  await reloadCollection();
}
async function reloadCollection() {
  const ticket=++loadNumber;
  try { await all(true); if(ticket===loadNumber) {renderCollection();paintNotes();} }
  catch(e) { if(ticket===loadNumber) $('#editorial-status').textContent=e.message; }
}
function paragraphs(holder,text) {
  for(const p of text.split(/\n\s*\n/).filter(p=>p.trim())) holder.append(element('p',p));
}
function authorView(detail) {
  const root=$('#book-authors'); root.replaceChildren();
  if(!detail.authors.length) { root.append(element('p','Биография этого автора пока не добавлена.')); return; }
  for(const a of detail.authors) {
    const article=element('article',undefined,'book-author-profile');
    const head=element('div',undefined,'book-author-head');
    if(a.portrait) {
      const figure=element('figure',undefined,'book-author-portrait');
      const img=element('img'); img.src=a.portrait.path; img.alt=a.portrait.caption+': '+a.name;
      img.width=a.portrait.width; img.height=a.portrait.height; img.loading='lazy'; img.decoding='async';
      img.onerror=()=>{ img.hidden=true; figure.prepend(element('p','Изображение временно недоступно.')); };
      const credit=element('details',undefined,'book-author-credit'); credit.append(element('summary','Об изображении'));
      const line=element('p',a.portrait.caption+'. '+a.portrait.credit+'. ');
      for(const [label,url] of [['Источник',a.portrait.source],[a.portrait.license,a.portrait.licenseUrl]]) {
        if(!url) {if(label!=='Источник')line.append(document.createTextNode(label+'. '));continue;}
        const link=element('a',label);link.href=url;link.target='_blank';link.rel='noopener noreferrer';line.append(link,document.createTextNode('. '));
      }
      line.append(document.createTextNode('Уменьшение и сжатие; без ретуши.'));credit.append(line);figure.append(img,credit);head.append(figure);
    }
    const intro=element('div');intro.append(element('h3',a.name),element('p',a.dates,'book-author-dates'));head.append(intro);
    article.append(head);const bio=element('div',undefined,'book-author-biography');paragraphs(bio,a.biography);article.append(bio);root.append(article);
  }
  if(detail.bookNote) {const note=element('aside',undefined,'book-author-work');note.append(element('h3','Книга в творческом пути'));paragraphs(note,detail.bookNote);root.append(note);}
}
export async function showDescription(kind,id,initialTab='description') {
  const ticket=++detailNumber;
  $('#description-title').textContent='Описание книги'; $('#description-author').textContent='';
  $('#description-body').textContent='Загружаем описание…'; $('#description-source').replaceChildren();
  $('#description-comment').replaceChildren(); $('#description-edit').hidden=true;
  let authorLoading=false, authorLoaded=false;
  async function selectTab(tab) {
    const author=tab==='author';
    for(const [name,selected] of [['description',!author],['author',author]]) {
      const control=$('#book-'+name+'-tab');control.setAttribute('aria-selected',String(selected));control.tabIndex=selected?0:-1;
      $('#book-'+name+'-panel').hidden=!selected;
    }
    if(!author || authorLoading || authorLoaded)return;
    authorLoading=true;$('#book-authors').textContent='Загружаем биографию…';
    try {
      const detail=await request(`/api/editorial/books/${kind}/${id}/authors`);
      if(ticket!==detailNumber || !$('#description-dialog').open)return;
      authorView(detail);authorLoaded=true;
    } catch(e) {if(ticket===detailNumber)$('#book-authors').textContent=e.message;}
    finally {authorLoading=false;}
  }
  for(const name of ['description','author']) {
    const tab=$('#book-'+name+'-tab');tab.onclick=()=>selectTab(name);
    tab.onkeydown=e=>{if(!['ArrowLeft','ArrowRight','Home','End'].includes(e.key))return;e.preventDefault();const next=e.key==='Home'?'description':e.key==='End'?'author':name==='author'?'description':'author';selectTab(next);$('#book-'+next+'-tab').focus();};
  }
  $('#book-authors').replaceChildren();
  $('#description-dialog').showModal();
  $('#description-dialog').scrollTop=0;
  selectTab(initialTab);
  try {
    const b=await request(`/api/editorial/books/${kind}/${id}`);
    if(ticket!==detailNumber || !$('#description-dialog').open)return;
    $('#description-title').textContent=b.title; $('#description-author').textContent=b.author;
    const body=$('#description-body'); body.replaceChildren();
    for(const paragraph of (b.description || 'Описание этой книги пока не добавлено.').split(/\n\s*\n/)) {
      const heading=paragraph.length<65 && !/[.!?…]$/.test(paragraph.trim());
      body.append(element(heading ? 'h3' : 'p',paragraph));
    }
    decorateProse(body,b.description,b.descriptionStyle);
    if(b.comment){const prose=element('div');renderComment(prose,b.comment,b.commentStyle);$('#description-comment').append(element('strong','Комментарий редактора'),prose);}
    $('#description-comment').hidden=!b.comment;
    $('#description-edit').hidden=!admin(); $('#description-edit').onclick=()=>editBook(kind,id);
  } catch(e) { if(ticket===detailNumber)$('#description-body').textContent=e.message; }
}
async function editBook(kind,id) {
  if(!admin() || saving)return;
  try {
    const b=await request(`/api/editorial/books/${kind}/${id}`);
    if(!admin())return;
    editing=b; const f=$('#editorial-form'); f.reset();
    $('#editorial-edit-title').textContent=b.title;
    textEditors.description.setValue(b.description,b.descriptionStyle);f.elements.lists.value=b.lists.join('\n');textEditors.comment.setValue(b.comment,b.commentStyle);refreshProsePreviews();
    $('#editorial-error').textContent='';
    $('#editorial-existing').replaceChildren();
    $('#editorial-existing').append(button('Показывать на главной',()=>{
      const labels=f.elements.lists.value.split('\n').map(x=>x.trim()).filter(Boolean);
      if(!labels.includes('На главной'))labels.unshift('На главной');
      f.elements.lists.value=labels.join('\n');
    },'category-chip'));
    $('#editorial-existing').append(element('small','Приоритет у списка «На главной». Если книг в нём несколько, показывается первая по названию. Без этого списка показывается первая рекомендация. Чтобы убрать приоритет книги, удалите строку «На главной».'));

    for(const name of [...new Set(records.flatMap(x=>x.lists))].sort()) {
      $('#editorial-existing').append(button(name,()=>{
        const labels=f.elements.lists.value.split('\n').map(x=>x.trim()).filter(Boolean);
        if(!labels.includes(name))labels.push(name); f.elements.lists.value=labels.join('\n');
      },'category-chip'));
    }
    $('#editorial-editor').showModal();
  } catch(e) {notify(e.message);}
}
export function updateEditorial() {
  $('#editorial-manage').hidden=!admin();
  if(!admin()) {manage=false;$('#editorial-editor').close();$('#description-edit').hidden=true;}
  if($('#editorial-dialog').open)renderCollection();
}
export function setupEditorial(options) {
  getUser=options.getUser;notify=options.toast;
  for(const name of ['description','comment'])textEditors[name]=setupTextFormatting($('#editorial-form').elements[name],name==='description'?'Описание книги':'Комментарий редактора');
  refreshProsePreviews=setupProsePreviews($('#editorial-form'));
  document.addEventListener('announcement-changed',renderHeroBook);
  document.addEventListener('catalogue-books-changed',()=>{all(true).then(()=>{paintNotes();if($('#editorial-dialog').open)renderCollection();}).catch(()=>{});});
  all().then(renderHeroBook).catch(renderHeroBook);
  document.querySelectorAll('a[href="#collection"], [data-open-editorial]').forEach(a=>a.addEventListener('click',e=>{e.preventDefault();openCollection();}));
  document.querySelectorAll('[data-editorial-close]').forEach(b=>b.onclick=()=>{if(b.closest('dialog').id!=='editorial-editor'||!saving)b.closest('dialog').close();});
  $('#editorial-retry').onclick=reloadCollection;
  $('#editorial-manage').onclick=()=>{if(!admin())return;manage=!manage;listName='';renderCollection();};
  $('#editorial-search').oninput=e=>{query=e.target.value;renderCollection();};
  $('#editorial-clear-lists').onclick=()=>{$('#editorial-form').elements.lists.value='';};
  $('#editorial-editor').addEventListener('cancel',e=>{if(saving)e.preventDefault();});
  $('#editorial-form').onsubmit=async e=>{
    e.preventDefault();if(saving||!editing||!admin())return;
    const f=e.target, lists=[...new Set(f.elements.lists.value.split('\n').map(x=>x.trim()).filter(Boolean))];
    if(lists.length>8 || lists.some(x=>x.length>50)){$('#editorial-error').textContent='До 8 списков, каждое название — не более 50 символов.';return;}
    saving=true;$('#editorial-save').disabled=true;$('#editorial-error').textContent='';
    try {
      await request(`/api/admin/editorial/books/${editing.kind}/${editing.id}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({version:editing.version,description:f.elements.description.value,lists,comment:f.elements.comment.value,descriptionStyle:textEditors.description.getStyles(),commentStyle:textEditors.comment.getStyles()})});
      $('#editorial-editor').close();$('#description-dialog').close();notify('Описание, подборки и комментарий опубликованы');
      await all(true);paintNotes();if($('#editorial-dialog').open)renderCollection();
    } catch(err) {$('#editorial-error').textContent=err.message;notify(err.message);}
    finally {saving=false;$('#editorial-save').disabled=false;}
  };
}
