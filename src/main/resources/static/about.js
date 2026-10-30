import { request } from './api-client.js';
const $ = selector => document.querySelector(selector);
const slots = ['portrait', 'development', 'library'];
const textFields = ['title', 'lead', 'body', 'signature', 'linkLabel', 'photoAlt'];
let context, editor, snapshot, busy = false, renderedVersion = -1;
const previews = new Map();
const admin = () => context?.getUser()?.role === 'ADMIN';
const photoUrl = (slot, version) => `/api/about/images/${slot}?v=${version}`;
function text(selector, value) {
  const element = $(selector); element.textContent = value; element.hidden = !value;
}
function render(content) {
  if (content.version < renderedVersion) return;
  renderedVersion = content.version;
  text('#about-heading', content.title);
  text('.about-story-lead', content.lead);
  text('.about-story-signature', content.signature);
  text('#about-catalog-link', content.linkLabel);
  const body = $('#about-story-body'); body.replaceChildren();
  for (const paragraph of content.body.split(/\n\s*\n/).filter(value => value.trim())) {
    const p = document.createElement('p'); p.textContent = paragraph; body.append(p);
  }
  for (const slot of ['development', 'library']) {
    const image = $(`.about-story-${slot}`); image.hidden = !content[slot];
    if (content[slot]) image.src = photoUrl(slot, content.version); else image.removeAttribute('src');
  }
  const figure = $('.about-author'), photo = figure.querySelector('img'), link = figure.querySelector('a');
  const enlarged = $('#author-photo-dialog img');
  figure.hidden = !content.portrait;
  if (content.portrait) {
    photo.src = enlarged.src = link.href = photoUrl('portrait', content.version);
    photo.alt = enlarged.alt = content.photoAlt || 'Фотография';
  } else {
    if ($('#author-photo-dialog').open) $('#author-photo-dialog').close();
    photo.removeAttribute('src'); enlarged.removeAttribute('src'); link.removeAttribute('href');
    photo.alt = enlarged.alt = '';
  }
  $('#about-load-status').hidden = true; $('#about-retry').hidden = true;
  $('#about').setAttribute('aria-busy', 'false');
  const empty = !content.portrait && ![content.title, content.lead, content.body, content.signature, content.linkLabel].some(value => value.trim());
  $('#about').hidden = empty;
  document.querySelectorAll('a[href="#about"]').forEach(link => { link.hidden = empty; });
}
async function load() {
  try { render(await request('/api/about')); }
  catch (_) {
    $('#about-load-status').hidden = false;
    $('#about-load-status').textContent = 'Не удалось загрузить раздел «О магазине».';
    $('#about-retry').hidden = false;
    $('#about').setAttribute('aria-busy', 'false');
  }
}
function releasePreviews() {
  for (const url of previews.values()) URL.revokeObjectURL(url);
  previews.clear();
}
function preview(slot) {
  if (previews.has(slot)) { URL.revokeObjectURL(previews.get(slot)); previews.delete(slot); }
  const form = $('#about-editor-form'), image = $(`#about-preview-${slot}`);
  const file = form.elements[slot].files[0], removed = form.elements[`remove-${slot}`].checked;
  image.hidden = removed || (!file && !snapshot[slot]);
  if (image.hidden) { image.removeAttribute('src'); return; }
  if (file) {
    const url = URL.createObjectURL(file); previews.set(slot, url); image.src = url;
  } else image.src = photoUrl(slot, snapshot.version);
}
async function openEditor() {
  if (!admin() || busy) return;
  busy = true;
  try {
    snapshot = await request('/api/admin/about');
    if (!admin()) return;
    const form = $('#about-editor-form'); form.reset(); releasePreviews();
    for (const name of textFields) form.elements[name].value = snapshot[name];
    for (const slot of slots) preview(slot);
    $('#about-editor-error').textContent = '';
    if ($('#profile-dialog').open) $('#profile-dialog').close();
    editor.showModal();
  } catch (error) { context.toast(error.message); }
  finally { busy = false; }
}
export function updateAbout() {
  for (const id of ['about-edit', 'profile-edit-about']) if ($('#'+id)) $('#'+id).hidden = !admin();
  if (!admin() && editor?.open) editor.close();
}
export function setupAbout(ctx) {
  context = ctx;
  const inline = document.createElement('button');
  inline.id = 'about-edit'; inline.type = 'button'; inline.className = 'button light';
  inline.hidden = true; inline.textContent = 'Редактировать раздел'; inline.onclick = openEditor;
  $('.about-story-content').prepend(inline);
  const profile = inline.cloneNode(true); profile.id = 'profile-edit-about'; profile.textContent = 'Редактировать «О магазине»';
  profile.onclick = openEditor; $('#logout').before(profile);
  editor = document.createElement('dialog'); editor.id = 'about-editor'; editor.setAttribute('aria-labelledby', 'about-editor-heading');
  editor.innerHTML = `<div class="about-editor-heading"><h2 id="about-editor-heading">О магазине</h2><button type="button" class="text-button" data-about-cancel>Закрыть ✕</button></div>
    <p>Измените текст и фотографии. Пустые поля будут скрыты на сайте. Изменения появятся после сохранения.</p>
    <form id="about-editor-form">
      <label>Заголовок<input name="title" maxlength="160" autofocus /></label>
      <label>Вводный текст<textarea name="lead" maxlength="1000" rows="3"></textarea></label>
      <label>Рассказ о магазине<textarea name="body" maxlength="20000" rows="12"></textarea></label>
      <p class="about-editor-hint">Разделяйте абзацы пустой строкой. Текст можно полностью удалить.</p>
      <label>Подпись<input name="signature" maxlength="160" /></label>
      <label>Текст кнопки перехода к каталогу<input name="linkLabel" maxlength="120" /></label>
      <button type="button" class="text-button" id="about-clear-text">Очистить весь текст</button>
      <div class="about-editor-images">${slots.map((slot, i) => `<fieldset><legend>${['Фотография в тексте', 'Фоновая фотография слева', 'Фоновая фотография справа'][i]}</legend>
        <img id="about-preview-${slot}" class="about-image-preview" alt="Предпросмотр фотографии" hidden />
        <label>Заменить фотографию<input name="${slot}" type="file" accept="image/png,image/jpeg" /></label>
        <label class="about-remove-photo"><input type="checkbox" name="remove-${slot}" />Удалить фотографию</label></fieldset>`).join('')}</div>
      <label>Описание фотографии для доступности<input name="photoAlt" maxlength="255" /></label>
      <p class="about-editor-hint">PNG или JPEG до 5 МБ. Фото сохраняется целиком, без обрезки.</p>
      <p id="about-editor-error" class="form-error" role="alert"></p>
      <div class="about-editor-actions"><button type="submit" class="button dark">Сохранить изменения</button><button type="button" class="button light" data-about-cancel>Отмена</button></div>
    </form>`;
  document.body.append(editor);
  editor.querySelectorAll('[data-about-cancel]').forEach(button => { button.onclick = () => { if (!busy) editor.close(); }; });
  editor.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
  editor.addEventListener('close', releasePreviews);
  const form = $('#about-editor-form');
  for (const slot of slots) {
    form.elements[slot].onchange = () => {
      const file = form.elements[slot].files[0];
      if (file && file.size > 5 * 1024 * 1024) {
        form.elements[slot].value = ''; $('#about-editor-error').textContent = 'Фотография должна быть не больше 5 МБ.';
      } else { form.elements[`remove-${slot}`].checked = false; $('#about-editor-error').textContent = ''; }
      preview(slot);
    };
    form.elements[`remove-${slot}`].onchange = () => {
      if (form.elements[`remove-${slot}`].checked) form.elements[slot].value = '';
      preview(slot);
    };
  }
  $('#about-clear-text').onclick = () => { for (const name of textFields) form.elements[name].value = ''; };
  form.onsubmit = async event => {
    event.preventDefault(); if (!admin() || busy) return;
    busy = true; const controls = [...form.elements, ...editor.querySelectorAll('[data-about-cancel]')];
    const payload = {version: snapshot.version};
    for (const name of textFields) payload[name] = form.elements[name].value;
    for (const slot of slots) payload['remove'+slot[0].toUpperCase()+slot.slice(1)] = form.elements[`remove-${slot}`].checked;
    const data = new FormData(); data.append('content', new Blob([JSON.stringify(payload)], {type:'application/json'}));
    for (const slot of slots) { const file = form.elements[slot].files[0]; if (file) data.append(slot, file); }
    controls.forEach(element => { element.disabled = true; }); $('#about-editor-error').textContent = '';
    try { const saved = await request('/api/admin/about', {method:'POST', body:data}); render(saved); editor.close(); context.toast('Раздел «О магазине» сохранён'); }
    catch (error) { $('#about-editor-error').textContent = error.message; }
    finally { busy = false; controls.forEach(element => { element.disabled = false; }); }
  };
  $('.about-author-zoom').onclick = event => { event.preventDefault(); if (!$('.about-author').hidden) $('#author-photo-dialog').showModal(); };
  $('#about-retry').onclick = load;
  load();
}
