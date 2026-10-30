import {request} from './api-client.js';
const $=s=>document.querySelector(s);
const el=(tag,text,cls)=>{const e=document.createElement(tag);if(text!==undefined)e.textContent=text;if(cls)e.className=cls;return e;};
const button=(label,run,cls='button light')=>{const e=el('button',label,cls);e.type='button';e.onclick=run;return e;};
let context,editing=null,busy=false,coverPreview;
const admin=()=>context?.getUser()?.role==='ADMIN';
const names=['title','author','description'];
function refreshSections(preferred) {
  const select=$('#managed-book-form').elements.sectionId;const value=preferred||select.value;select.replaceChildren();
  for(const section of context.getSections()){const option=el('option',section.name);option.value=section.id;select.append(option);}
  if(context.getSections().some(s=>s.id===value))select.value=value;
}
function release(){if(coverPreview){URL.revokeObjectURL(coverPreview);coverPreview=null;}}
export function updateCatalogueAdmin(){
  document.querySelectorAll('.catalogue-admin-control').forEach(e=>{e.hidden=!admin();});
  if(!admin()){if($('#managed-book-editor')?.open)$('#managed-book-editor').close();if($('#section-editor')?.open)$('#section-editor').close();}
}
export async function openBookManager(book=null,sectionId='all') {
  if(!admin()||busy)return;
  busy=true;
  try {
    editing=book?await request(`/api/catalogue/books/${book.kind}/${encodeURIComponent(book.id)}`):null;
    if(!admin())return;
    const form=$('#managed-book-form');form.reset();release();refreshSections(editing?.sectionId||sectionId);
    for(const name of names)form.elements[name].value=editing?.[name]||'';
    form.elements.price.value=editing?.price??'';
    form.elements.description.closest('label').hidden=!!editing;
    $('#managed-book-title').textContent=editing?'Редактировать книгу':'Добавить книгу';
    $('#managed-book-help').textContent='Можно сначала сохранить обложку и описание, а PDF прикрепить позже. Файлы, которые вы не меняете, сохранятся.';
    $('#managed-pdf-remove').hidden=!editing?.pdfId||editing.kind==='library';
    const image=$('#managed-book-preview');image.hidden=!editing;
    if(editing){image.src=editing.kind==='catalog'?(editing.cover?`/api/catalogue/covers/${editing.id}?v=${editing.version}-${editing.pdfVersion}`:'/covers/default.svg'):`/api/java-library/${editing.id}/cover?v=${editing.version}`;}
    else image.removeAttribute('src');
    $('#managed-book-error').textContent='';if($('#profile-dialog').open)$('#profile-dialog').close();$('#managed-book-editor').showModal();
  }catch(e){context.toast(e.message);}finally{busy=false;}
}
export function addManagementControls(card,book){
  if(!admin())return;
  const controls=el('div',undefined,'managed-book-actions');
  controls.append(button('Редактировать книгу',()=>openBookManager(book),'text-button'),button('Удалить книгу',async()=>{
    if(busy||!admin()||!confirm(`Удалить «${book.name||book.title}» из всех разделов и подборок? Загруженный PDF будет удалён. История заказов сохранится.`))return;
    busy=true;
    try{
      const current=await request(`/api/catalogue/books/${book.kind}/${encodeURIComponent(book.id)}`);
      // Do not silently delete a version newer than the visible card.
      if(current.version!==book.version||current.pdfVersion!==book.pdfVersion)throw Error('Книга изменилась. Обновите каталог перед удалением.');
      await request(`/api/admin/catalogue/books/${book.kind}/${encodeURIComponent(book.id)}?`+new URLSearchParams({version:current.version,pdfVersion:current.pdfVersion}),{method:'DELETE'});
      await changed();context.toast('Книга удалена из каталога');
    }catch(e){context.toast(e.message);}finally{busy=false;}
  },'text-button'));
  card.append(controls);
}
async function changed(){await context.reload();document.dispatchEvent(new Event('catalogue-books-changed'));}
async function openSections(){
  if(!admin()||busy)return;
  try{await context.reload();if(!admin())return;paintSections();if($('#profile-dialog').open)$('#profile-dialog').close();$('#section-editor').showModal();}
  catch(e){context.toast(e.message);}
}
function paintSections(){
  const list=$('#managed-sections');list.replaceChildren();
  for(const section of context.getSections()){
    const row=el('form',undefined,'managed-section-row');
    const label=el('label','Название раздела');const name=el('input');name.value=section.name;name.required=true;name.maxLength=80;label.append(name);
    const orderLabel=el('label','Порядок');const position=el('input');position.type='number';position.min='0';position.max='100000';position.value=section.position;orderLabel.append(position);
    const save=el('button','Сохранить','button light');save.type='submit';
    row.onsubmit=async event=>{
      event.preventDefault();if(busy||!admin())return;busy=true;save.disabled=true;
      try{await request(`/api/admin/catalogue/sections/${encodeURIComponent(section.id)}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:name.value,position:Number(position.value),version:section.version})});await changed();refreshSections();paintSections();}
      catch(e){$('#section-error').textContent=e.message;}finally{busy=false;save.disabled=false;}
    };
    const remove=button('Удалить',async()=>{
      if(busy||!admin()||!confirm(`Удалить пустой раздел «${section.name}»?`))return;busy=true;remove.disabled=true;
      try{await request(`/api/admin/catalogue/sections/${encodeURIComponent(section.id)}?version=${section.version}`,{method:'DELETE'});await changed();refreshSections();paintSections();}
      catch(e){$('#section-error').textContent=e.message;}finally{busy=false;remove.disabled=false;}
    },'text-button');
    row.append(label,orderLabel,save,remove);list.append(row);
  }
}
export function setupCatalogueAdmin(ctx){
  context=ctx;
  const tools=el('div',undefined,'catalogue-admin-control managed-toolbar');tools.hidden=true;
  tools.append(button('Добавить книгу +',()=>openBookManager(null,context.getSection())),button('Управлять разделами',openSections),button('Мои подборки',()=>document.querySelector('[data-open-editorial]').click()));
  $('#categories').after(tools);
  const profile=button('Управление книгами и разделами',()=>{$('#profile-dialog').close();$('#catalog').scrollIntoView({block:'start'});},'button light catalogue-admin-control');profile.hidden=true;$('#logout').before(profile);
  const editor=el('dialog');editor.id='managed-book-editor';editor.className='catalogue-editor';editor.setAttribute('aria-labelledby','managed-book-title');
  editor.innerHTML=`<div class="managed-editor-heading"><h2 id="managed-book-title">Книга</h2><button type="button" class="text-button" data-managed-close>Закрыть ✕</button></div>
    <p id="managed-book-help"></p><form id="managed-book-form">
    <label>Название<input name="title" maxlength="255" required autofocus /></label><label>Автор<input name="author" maxlength="255" /></label>
    <label>Раздел<select name="sectionId" required></select></label><button type="button" class="text-button" id="managed-add-section">Создать новый раздел</button>
    <label>Цена, ₽<input name="price" type="number" min="0.01" max="99999999.99" step="0.01" inputmode="decimal" /></label>
    <p>Оставьте цену пустой, если книга не продаётся. После сохранения цены появится кнопка «В корзину». Для оформления заказа добавьте остаток в панели «Управление магазином» → «Поступление на склад». Чтение и скачивание PDF остаются доступными всем.</p>
    <label>Описание<textarea name="description" rows="6" maxlength="3000"></textarea></label>
    <img id="managed-book-preview" alt="Обложка книги" hidden />
    <label>Обложка — PNG или JPEG до 2 МБ<input name="cover" type="file" accept="image/png,image/jpeg" /></label>
    <p>При добавлении PDF выбранная вами обложка сохранится. Если обложки нет, используем первую страницу PDF.</p>
    <label>PDF — до 500 МБ, без пароля<input name="pdf" type="file" accept="application/pdf,.pdf" /></label>
    <label id="managed-pdf-remove" class="managed-check" hidden><input name="removePdf" type="checkbox" />Убрать PDF, сохранив карточку и обложку</label>
    <p id="managed-book-error" class="form-error" role="alert"></p><div class="managed-editor-actions"><button type="submit" class="button dark">Сохранить книгу</button><button type="button" class="button light" data-managed-close>Отмена</button></div></form>`;
  document.body.append(editor);
  const sections=el('dialog');sections.id='section-editor';sections.className='catalogue-editor';sections.setAttribute('aria-labelledby','managed-section-title');
  sections.innerHTML=`<div class="managed-editor-heading"><h2 id="managed-section-title">Разделы библиотеки</h2><button type="button" class="text-button" data-managed-close>Закрыть ✕</button></div>
    <p>Переименовывайте разделы и меняйте их порядок. Перед удалением раздела перенесите или удалите его книги.</p>
    <form id="managed-section-create"><label>Новый раздел<input name="name" maxlength="80" required /></label><button type="submit" class="button dark">Создать раздел</button></form>
    <p id="section-error" class="form-error" role="alert"></p><div id="managed-sections"></div>`;
  document.body.append(sections);
  for(const dialog of [editor,sections]){dialog.querySelectorAll('[data-managed-close]').forEach(b=>{b.onclick=()=>{if(!busy)dialog.close();};});dialog.addEventListener('cancel',e=>{if(busy)e.preventDefault();});}
  editor.addEventListener('close',release);$('#managed-add-section').onclick=openSections;
  const form=$('#managed-book-form');
  form.elements.cover.onchange=()=>{release();const file=form.elements.cover.files[0];if(file){coverPreview=URL.createObjectURL(file);$('#managed-book-preview').src=coverPreview;$('#managed-book-preview').hidden=false;}};
  form.elements.pdf.onchange=()=>{if(form.elements.pdf.files[0])form.elements.removePdf.checked=false;};
  form.elements.removePdf.onchange=()=>{if(form.elements.removePdf.checked)form.elements.pdf.value='';};
  form.onsubmit=async event=>{
    event.preventDefault();if(busy||!admin())return;
    const pdf=form.elements.pdf.files[0],cover=form.elements.cover.files[0];
    if(pdf?.size>500*1024*1024||cover?.size>2*1024*1024){$('#managed-book-error').textContent='PDF — до 500 МБ, обложка — до 2 МБ.';return;}
    const data=new FormData(form);if(!pdf)data.delete('pdf');if(!cover)data.delete('cover');
    if(!form.elements.price.value.trim())data.delete('price');
    if(editing){data.set('version',editing.version);data.set('pdfVersion',editing.pdfVersion);}
    busy=true;const controls=[...form.elements,...editor.querySelectorAll('[data-managed-close]')];controls.forEach(e=>{e.disabled=true;});$('#managed-book-error').textContent='';
    try{await request('/api/admin/catalogue/books'+(editing?`/${editing.kind}/${encodeURIComponent(editing.id)}`:''),{method:'POST',body:data});editor.close();await changed();context.toast(pdf?'Книга сохранена. PDF доступен для чтения и скачивания':'Карточка сохранена');}
    catch(e){$('#managed-book-error').textContent=e.message;}finally{busy=false;controls.forEach(e=>{e.disabled=false;});}
  };
  $('#managed-section-create').onsubmit=async event=>{
    event.preventDefault();if(busy||!admin())return;busy=true;$('#section-error').textContent='';
    try{const section=await request('/api/admin/catalogue/sections',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:event.target.elements.name.value})});event.target.reset();await changed();refreshSections(section.id);paintSections();}
    catch(e){$('#section-error').textContent=e.message;}finally{busy=false;}
  };
}
