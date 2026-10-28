import { request } from './api-client.js';
const $ = s => document.querySelector(s);
let getUser, toast, page = 0, query = '', generation = 0, editing = null, busy = false;
const el = (tag, text, cls) => {
  const n = document.createElement(tag);
  if (text !== undefined) n.textContent = text;
  if (cls) n.className = cls;
  return n;
};
const admin = () => getUser?.()?.role === 'ADMIN';
const mb = bytes => (bytes / 1024 / 1024).toLocaleString('ru', { maximumFractionDigits: 1 }) + ' МБ';
function button(text, handler, cls = 'button light') {
  const b = el('button', text, cls); b.type = 'button'; b.onclick = handler; return b;
}
export function updateJavaLibrary() {
  $('#java-add').hidden = !admin();
  if (!admin() && $('#java-editor').open) $('#java-editor').close();
  if (!$('#java-library').hidden) load();
}
export function openJavaLibrary() { load(); }
async function load() {
  const ticket = ++generation;
  $('#java-status').textContent = 'Загружаем электронную полку…';
  $('#java-list').replaceChildren(); $('#java-pages').replaceChildren();
  try {
    const data = await request('/api/java-library?' + new URLSearchParams({ page, q: query }));
    if (ticket !== generation) return;
    if (page && !data.items.length) { page = Math.max(0, Math.ceil(data.total / 12) - 1); return load(); }
    $('#java-status').textContent = data.total ? `Книг: ${data.total}. Скачивание доступно без регистрации.` : 'Полка пока пуста. Здесь появятся книги по Java для свободного скачивания.';
    for (const book of data.items) {
      const card = el('article', undefined, 'java-card');
      const img = el('img'); img.src = `/api/java-library/${book.id}/cover?v=${book.version}`;
      img.alt = 'Обложка книги «' + book.title + '»'; img.loading = 'lazy'; img.decoding = 'async';
      const frame = el('div', undefined, 'java-cover'); frame.append(img);
      const info = el('div', undefined, 'java-info');
      info.append(el('span', 'JAVA · PDF', 'eyebrow'), el('h3', book.title));
      if (book.author) info.append(el('p', book.author, 'java-author'));
      info.append(el('p', `${book.pageCount} стр. · ${mb(book.fileSize)}`, 'java-meta'));
      if (book.description) info.append(el('p', book.description, 'java-description'));
      const download = el('a', 'Скачать PDF ↓', 'button dark');
      download.href = `/api/java-library/${book.id}/download`; download.setAttribute('download', '');
      info.append(download);
      if (admin()) {
        const actions = el('div', undefined, 'java-actions');
        actions.append(button('Редактировать', () => editor(book)), button('Удалить', () => remove(book)));
        info.append(actions);
      }
      card.append(frame, info); $('#java-list').append(card);
    }
    const pages = Math.ceil(data.total / data.pageSize);
    if (pages > 1) {
      const previous = button('← Назад', () => { page--; load(); }); previous.disabled = !page;
      const next = button('Далее →', () => { page++; load(); }); next.disabled = page >= pages - 1;
      $('#java-pages').append(previous, el('span', `Страница ${page + 1} из ${pages}`), next);
    }
  } catch (e) {
    if (ticket !== generation) return;
    $('#java-status').textContent = e.message;
    $('#java-pages').append(button('Повторить загрузку', load));
  }
}
function editor(book = null) {
  editing = book;
  const form = $('#java-form'); form.reset(); $('#java-error').textContent = '';
  $('#java-editor-title').textContent = book ? 'Редактировать книгу' : 'Новая книга по Java';
  form.elements.title.value = book?.title || '';
  form.elements.author.value = book?.author || '';
  form.elements.description.value = book?.description || '';
  form.elements.pdf.required = !book;
  $('#java-file-note').textContent = book ? 'Оставьте файлы пустыми, чтобы сохранить текущий PDF и обложку. Новый PDF обновит обложку по первой странице, если не выбрана отдельная.' : 'Обложка появится автоматически с первой страницы PDF. При желании выберите отдельное изображение.';
  $('#java-current-cover').hidden = !book;
  if (book) $('#java-current-cover').src = `/api/java-library/${book.id}/cover?v=${book.version}`;
  $('#java-editor').showModal();
}
async function remove(book) {
  if (busy || !confirm(`Удалить «${book.title}» и PDF с сайта? Уже скачанные пользователями копии останутся у них.`)) return;
  busy = true;
  try {
    await request(`/api/admin/java-library/${book.id}?version=${book.version}`, { method: 'DELETE' });
    toast('Книга удалена'); await load();
  } catch (e) { toast(e.message); } finally { busy = false; }
}
export function setupJavaLibrary(options) {
  getUser = options.getUser; toast = options.toast;
  $('#java-add').onclick = () => editor();
  $('#java-close').onclick = () => { if (!busy) $('#java-editor').close(); };
  $('#java-editor').addEventListener('cancel', e => { if (busy) e.preventDefault(); });
  $('#java-search-form').onsubmit = e => {
    e.preventDefault(); query = $('#java-search').value.trim(); page = 0; load();
  };
  $('#java-form').onsubmit = async e => {
    e.preventDefault(); if (busy) return;
    const form = e.target, pdf = form.elements.pdf.files[0], cover = form.elements.cover.files[0];
    $('#java-error').textContent = '';
    if (pdf && pdf.size > 500 * 1024 * 1024) { $('#java-error').textContent = 'PDF должен быть не больше 500 МБ'; return; }
    if (cover && cover.size > 2 * 1024 * 1024) { $('#java-error').textContent = 'Обложка должна быть не больше 2 МБ'; return; }
    const data = new FormData(form);
    if (!pdf) data.delete('pdf'); if (!cover) data.delete('cover');
    if (editing) data.set('version', editing.version);
    busy = true; $('#java-save').disabled = true; $('#java-close').disabled = true;
    $('#java-save').textContent = 'Загрузка и сохранение…';
    try {
      await request('/api/admin/java-library' + (editing ? '/' + editing.id : ''), { method: 'POST', body: data });
      $('#java-editor').close(); toast('Книга опубликована и доступна для скачивания');
      page = 0; query = ''; $('#java-search').value = ''; await load();
    } catch (err) { $('#java-error').textContent = err.message; }
    finally {
      busy = false; $('#java-save').disabled = false; $('#java-close').disabled = false;
      $('#java-save').textContent = 'Сохранить и опубликовать';
    }
  };
}
