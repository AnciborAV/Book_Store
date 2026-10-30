import {request} from './api-client.js';
const $=selector=>document.querySelector(selector);
const element=(tag,text,cls)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;};
const button=(text,run,cls='button light')=>{const e=element('button',text,cls);e.type='button';e.onclick=run;return e;};
export const bookKey=book=>book.kind+':'+book.id;
export function placeBook(items,section,source,target) {
  const visible=items.filter(book=>section==='all'||book.sectionId===section);
  const from=visible.findIndex(book=>bookKey(book)===source),to=visible.findIndex(book=>bookKey(book)===target);
  if(from<0||to<0||from===to)return items;
  const next=[...visible],moved=next.splice(from,1)[0];next.splice(to,0,moved);
  let i=0;return items.map(book=>section==='all'||book.sectionId===section?next[i++]:book);
}
let context,dialog,items=[],initial=[],version=0,busy=false,dragged=null,opener;
const admin=()=>context?.getUser()?.role==='ADMIN';
const dirty=()=>items.some((book,i)=>bookKey(book)!==bookKey(initial[i]));
function sync(){
  $('#shelf-order-save').disabled=busy||!dirty();
  $('#shelf-order-reset').disabled=busy||!dirty();
  $('#shelf-order-section').disabled=busy;
  $('#shelf-order-refresh').disabled=busy;
  for(const control of dialog.querySelectorAll('[data-shelf-close]'))control.disabled=busy;
}
function closeEditor(){if(busy)return;if(dirty()&&!confirm('Закрыть расстановку без сохранения изменений?'))return;dialog.close();opener?.focus();}
export function updateBookOrderingAdmin(){
  const control=$('#open-shelf-order');if(control)control.hidden=!admin();
  if(!admin()&&dialog?.open){dialog.close();items=[];initial=[];dragged=null;}
}
async function loadShelf(preferred){
  const data=await request('/api/catalogue');
  if(!admin())return false;
  items=data.books;initial=[...items];version=data.orderVersion;
  const select=$('#shelf-order-section'),current=preferred||select.value;select.replaceChildren();
  for(const section of [{id:'all',name:'Все разделы'},...data.sections]){const option=element('option',section.name);option.value=section.id;select.append(option);}
  select.value=['all',...data.sections.map(section=>section.id)].includes(current)?current:'all';
  $('#shelf-order-error').textContent='';paint();return true;
}
export async function openBookOrdering(){
  if(busy||!admin())return;busy=true;opener=document.activeElement;
  try{if(await loadShelf(context.getSection())){if($('#profile-dialog').open)$('#profile-dialog').close();dialog.showModal();}}
  catch(e){context.toast(e.message);}finally{busy=false;paint();}
}
function move(source,target){
  if(busy||!admin())return;
  const next=placeBook(items,$('#shelf-order-section').value,source,target);
  if(next===items)return;items=next;paint(source);
  const visible=items.filter(book=>$('#shelf-order-section').value==='all'||book.sectionId===$('#shelf-order-section').value);
  const moved=visible.find(book=>bookKey(book)===source);
  $('#shelf-order-status').textContent=`«${moved.title}»: место ${visible.findIndex(book=>bookKey(book)===source)+1} из ${visible.length}. Изменения ещё не сохранены.`;
}
function paint(focusKey){
  const section=$('#shelf-order-section').value;
  const visible=items.filter(book=>section==='all'||book.sectionId===section);
  const grid=$('#shelf-order-grid');grid.replaceChildren();
  $('#shelf-order-count').textContent=`На этой полке: ${visible.length}.`;
  if(!visible.length)grid.append(element('p','В этом разделе пока нет книг.'));
  for(let i=0;i<visible.length;i++){
    const book=visible[i],key=bookKey(book);const card=element('article',undefined,'shelf-order-card');card.draggable=!busy;card.tabIndex=0;
    card.setAttribute('aria-label',`${book.title}. Место ${i+1} из ${visible.length}`);card.dataset.shelfKey=key;
    const hint=element('span','⠿ Перетащите карточку','shelf-order-handle');hint.setAttribute('aria-hidden','true');
    const image=context.createCover(book);image.draggable=false;
    const controls=element('div',undefined,'shelf-order-buttons');
    const back=button('← Раньше',()=>{if(i>0)move(key,bookKey(visible[i-1]));}),forward=button('Позже →',()=>{if(i+1<visible.length)move(key,bookKey(visible[i+1]));});
    back.disabled=busy||i===0;forward.disabled=busy||i===visible.length-1;
    back.setAttribute('aria-label','Переместить раньше: '+book.title);forward.setAttribute('aria-label','Переместить позже: '+book.title);
    for(const b of [back,forward])b.addEventListener('dragstart',event=>event.preventDefault());controls.append(back,forward);
    card.append(hint,image,element('h3',book.title),element('p',book.author),element('span',`Место ${i+1}`,'shelf-order-position'),controls);
    card.addEventListener('dragstart',event=>{if(busy||!admin()){event.preventDefault();return;}dragged=key;event.dataTransfer.effectAllowed='move';event.dataTransfer.setData('text/plain',key);card.classList.add('shelf-dragging');});
    card.addEventListener('dragover',event=>{if(dragged&&dragged!==key&&!busy){event.preventDefault();event.dataTransfer.dropEffect='move';card.classList.add('shelf-drop-target');}});
    card.addEventListener('dragleave',()=>card.classList.remove('shelf-drop-target'));
    card.addEventListener('drop',event=>{event.preventDefault();const source=dragged;dragged=null;card.classList.remove('shelf-drop-target');if(source)move(source,key);});
    card.addEventListener('dragend',()=>{dragged=null;dialog.querySelectorAll('.shelf-drop-target,.shelf-dragging').forEach(e=>e.classList.remove('shelf-drop-target','shelf-dragging'));});
    grid.append(card);if(key===focusKey)card.focus({preventScroll:true});
  }
  sync();
}
export function setupBookOrdering(options){
  context=options;
  const open=button('Расставить книги ↔',openBookOrdering,'button light catalogue-admin-control');open.id='open-shelf-order';open.hidden=true;$('.managed-toolbar').append(open);
  dialog=element('dialog');dialog.id='shelf-order-dialog';dialog.className='catalogue-editor shelf-order-editor';dialog.setAttribute('aria-labelledby','shelf-order-title');
  dialog.innerHTML=`<div class="managed-editor-heading"><h2 id="shelf-order-title">Расставить книги</h2><button type="button" class="text-button" data-shelf-close>Закрыть ✕</button></div>
    <p>Перетащите карточку на место другой книги. На телефоне и с клавиатуры используйте «Раньше» и «Позже». Новый порядок увидят все посетители после сохранения.</p>
    <p>Полка показана целиком, без поиска и страниц. Разделы наследуют порядок общего каталога; перестановка внутри раздела сохраняет относительный порядок остальных книг.</p>
    <div class="shelf-order-tools"><label>Полка<select id="shelf-order-section"></select></label><button type="button" class="button light" id="shelf-order-refresh">Обновить полку</button></div>
    <p id="shelf-order-count"></p><p id="shelf-order-error" class="form-error" role="alert"></p><p id="shelf-order-status" class="shelf-order-status" role="status" aria-live="polite" aria-atomic="true"></p>
    <div id="shelf-order-grid" class="shelf-order-grid"></div><div class="shelf-order-footer"><button type="button" class="button dark" id="shelf-order-save">Сохранить порядок</button><button type="button" class="button light" id="shelf-order-reset">Отменить правки</button><button type="button" class="text-button" data-shelf-close>Закрыть</button></div>`;
  document.body.append(dialog);
  dialog.querySelectorAll('[data-shelf-close]').forEach(b=>b.onclick=closeEditor);
  dialog.addEventListener('cancel',event=>{event.preventDefault();closeEditor();});
  dialog.addEventListener('close',()=>{dragged=null;});
  $('#shelf-order-section').onchange=()=>{$('#shelf-order-status').textContent='';paint();};
  $('#shelf-order-reset').onclick=()=>{if(busy)return;items=[...initial];$('#shelf-order-status').textContent='Правки отменены.';paint();};
  $('#shelf-order-refresh').onclick=async()=>{
    if(busy||!admin()||(dirty()&&!confirm('Обновить полку и отменить несохранённые перестановки?')))return;
    busy=true;sync();try{await loadShelf();}catch(e){$('#shelf-order-error').textContent=e.message;}finally{busy=false;paint();}
  };
  $('#shelf-order-save').onclick=async()=>{
    if(busy||!admin()||!dirty())return;busy=true;paint();$('#shelf-order-error').textContent='';
    try{
      await request('/api/admin/catalogue/order',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({version,books:items.map(({kind,id})=>({kind,id}))})});
      const section=$('#shelf-order-section').value;initial=[...items];dialog.close();context.toast('Порядок книг сохранён для всех посетителей');
      try{await context.onSaved(section);}catch(e){context.toast('Порядок сохранён. Обновите каталог: '+e.message);}
    }catch(e){$('#shelf-order-error').textContent=e.message;}finally{busy=false;paint();}
  };
}
