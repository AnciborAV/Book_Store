import { request } from './api-client.js';
import { createFeedbackCode } from './feedback-code.js';
let context, publicPage = 0, adminPage = 0, adminState = 'PENDING', generation = 0;
let refreshFeedbackLayout = () => {};
const labels = { REVIEW: 'Отзыв', ISSUE: 'Замечание', IDEA: 'Предложение' };
const $ = id => document.getElementById(id);
const el = (tag, text, cls) => {
  const n = document.createElement(tag); n.textContent = text;
  if (cls) n.className = cls;
  return n;
};
function button(text, handler) {
  const b = el('button', text, 'button light'); b.type = 'button'; b.onclick = handler; return b;
}
function setupCompactFeedback(section) {
  const column = section.querySelector('.feedback-reader-column');
  const grid = el('div', '', 'feedback-masonry');
  const pages = el('div'); pages.id = 'feedback-public-pages';
  column.append(grid, pages);
  grid.append($('feedback-public'), section.querySelector('.feedback-gallery'));
  let frame;
  const arrange = () => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => {
      // Preserve DOM reading order; dense grid packing only removes vertical holes.
      for (const item of grid.querySelectorAll('#feedback-public > *, .feedback-gallery > *')) {
        const span = Math.ceil((item.getBoundingClientRect().height + 20) / 4);
        item.style.gridRowEnd = `span ${Math.max(1, span)}`;
      }
    });
  };
  const observer = new ResizeObserver(arrange);
  refreshFeedbackLayout = () => {
    observer.disconnect();
    observer.observe(grid);
    grid.querySelectorAll('#feedback-public > *, .feedback-gallery > *').forEach(item => observer.observe(item));
    arrange();
  };
  refreshFeedbackLayout();
}
export function setupFeedback(ctx) {
  context = ctx;
  const section = document.createElement('section');
  section.id = 'site-feedback'; section.className = 'site-feedback';
  section.innerHTML = `
    <div class="eyebrow">СДЕЛАЕМ «ПЕРЕПЛЁТ» ЛУЧШЕ</div>
    <h2>Ваше мнение важно</h2>
    <p>Поделитесь впечатлением, расскажите о проблеме или предложите идею.</p>
    <div class="feedback-layout">
      <div>
        <p id="feedback-login">Чтобы оставить сообщение, войдите в аккаунт через кнопку «Войти» вверху сайта.</p>
        <form id="feedback-form" hidden>
          <label>Тип сообщения<select name="kind"><option value="REVIEW">Отзыв о сайте</option><option value="ISSUE">Замечание</option><option value="IDEA">Предложение</option></select></label>
          <label>Ваше сообщение<textarea name="body" rows="6" required maxlength="3000" placeholder="Что понравилось? Что можно улучшить?"></textarea></label>
          <p class="muted">До 3000 символов. Не указывайте пароли, адреса и другие личные данные.</p>
          <label class="feedback-consent"><input name="publicConsent" type="checkbox">Разрешаю опубликовать сообщение и моё имя на сайте после проверки администратором.</label>
          <p class="muted">Без этой отметки сообщение увидит только администратор. Электронная почта не публикуется.</p>
          <button class="button" type="submit">Отправить сообщение</button>
        </form>
        <p id="feedback-result" role="status" aria-live="polite"></p>
      </div>
      <div class="feedback-reader-column">
        <h3>Отзывы посетителей</h3>
        <div id="feedback-public" aria-live="polite"></div>

      </div>
    </div>
    <div class="feedback-gallery" aria-label="Философия и код Переплёта">
        <figure class="feedback-artwork">
          <img src="/covers/nietzsche-java.webp" width="1536" height="1024" loading="lazy" decoding="async"
               alt="Художественная иллюстрация: Фридрих Ницше пишет код Java в IntelliJ IDEA в старинной библиотеке при свечах">
          <figcaption class="feedback-artwork-caption">
            <blockquote><p>«Я говорю вам: нужно носить в себе еще хаос, чтобы быть в состоянии родить танцующую звезду».</p></blockquote>
            <p class="feedback-artwork-author">Фридрих Ницше</p>
            <cite>«Так говорил Заратустра»<br>Предисловие Заратустры, § 5</cite>
            <small>Перевод Ю. М. Антоновского</small>
          </figcaption>
        </figure>
      <figure class="feedback-artwork feedback-code">
        <a class="feedback-code-link" href="/covers/checkout-code.png" target="_blank" rel="noopener" aria-label="Открыть оригинальный скриншот кода в новой вкладке">
          <img src="/covers/checkout-code.png" width="1814" height="1310" loading="lazy" decoding="async"
               alt="Оригинальный скриншот IntelliJ IDEA: метод checkout класса OrderService, проверка корзины, резервирование книг и расчёт суммы заказа">
        </a>
        <figcaption class="feedback-artwork-caption">
          <h3>По ту сторону «Переплёта»</h3>
          <p>Фрагмент кода, который превращает выбранные книги в заказ.</p>
          <small>Java · OrderService · checkout()</small>
          <a class="text-button" href="/covers/checkout-code.png" target="_blank" rel="noopener">Рассмотреть код ↗</a>
        </figcaption>
      </figure>
    </div>
    <div id="feedback-admin" hidden>
      <h3>Сообщения администратору</h3>
      <div class="feedback-controls"><label>Показать<select id="feedback-state"><option value="PENDING">Новые</option><option value="PUBLISHED">Опубликованные</option><option value="ARCHIVED">Архив</option></select></label><button id="feedback-refresh" type="button" class="button light">Обновить сообщения</button></div>
      <p id="feedback-admin-result" role="status"></p><div id="feedback-inbox"></div>
    </div>`;
  document.querySelector('main').append(section);
  setupCompactFeedback(section);
  let requestId = crypto.randomUUID();
  $('feedback-form').onsubmit = async e => {
    e.preventDefault();
    const form = e.currentTarget, submit = form.querySelector('button[type=submit]');
    if (!form.elements.body.value.trim()) { $('feedback-result').textContent = 'Напишите сообщение.'; return; }
    submit.disabled = true; submit.textContent = 'Сохраняем…'; $('feedback-result').textContent = '';
    try {
      const data = { requestId, kind: form.elements.kind.value, body: form.elements.body.value.trim(), publicConsent: form.elements.publicConsent.checked };
      await request('/api/site-feedback', { method: 'POST', headers: {'Content-Type':'application/json'}, body: JSON.stringify(data) });
      form.reset(); requestId = crypto.randomUUID();
      $('feedback-result').textContent = data.publicConsent
        ? 'Спасибо! Сообщение сохранено и ожидает проверки перед публикацией.'
        : 'Спасибо! Ваше сообщение сохранено для администратора.';
    } catch (error) { $('feedback-result').textContent = error.message; }
    finally { submit.disabled = false; submit.textContent = 'Отправить сообщение'; }
  };
  $('feedback-state').onchange = () => { adminState = $('feedback-state').value; adminPage = 0; load(true); };
  $('feedback-refresh').onclick = () => load(true);
  load(false);
}
export function updateFeedback() {
  if (!context) return;
  generation++;
  const user = context.getUser(), admin = user?.role === 'ADMIN';
  $('feedback-form').hidden = !user; $('feedback-login').hidden = !!user;
  $('feedback-admin').hidden = !admin;
  $('feedback-inbox').replaceChildren(); $('feedback-admin-result').textContent = '';
  $('feedback-form').reset(); $('feedback-result').textContent = '';
  // Fetch private messages only on an explicit admin action.
}
async function load(admin) {
  if (admin && context.getUser()?.role !== 'ADMIN') return;
  const target = $(admin ? 'feedback-inbox' : 'feedback-public');
  const stamp = generation, page = admin ? adminPage : publicPage;
  const marker = {}; target._request = marker;
  if (!admin) $('feedback-public-pages').replaceChildren();
  target.replaceChildren(el('p','Загрузка…'));
  if (!admin) refreshFeedbackLayout();
  try {
    const data = await request(admin ? `/api/admin/site-feedback?page=${page}&state=${adminState}` : `/api/site-feedback?page=${page}`);
    if (target._request !== marker || (admin && stamp !== generation)) return;
    target.replaceChildren();
    if (!data.items.length) target.append(el('p', admin ? 'В этом разделе пока нет сообщений.' : 'Пока нет опубликованных отзывов. Ваш может стать первым.'));
    for (const item of data.items) {
      const card = el('article', '', 'feedback-card');
      card.append(el('strong', item.author), el('p', `${labels[item.kind]} · ${new Date(item.createdAt).toLocaleDateString('ru-RU')}`, 'muted'), el('p', item.body, 'feedback-body'));
      if (!admin && item.kind === 'REVIEW' && item.author.trim() === 'Мария'
          && /^Отличная\s+подборка\s+книг(?:\s|[.!…]|$)/u.test(item.body.trim())) {
        card.append(createFeedbackCode());
      }
      if (admin) {
        const controls = el('div', '', 'feedback-controls');
        card.append(el('p', item.publicConsent ? 'Автор разрешил публикацию.' : 'Только для администратора. Публикация запрещена.', 'muted'));
        const change = async (state, b) => {
          if (state === 'PUBLISHED' && !confirm('Опубликовать сообщение и имя автора для всех посетителей?')) return;
          b.disabled = true;
          try {
            await request(`/api/admin/site-feedback/${item.id}`, {method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({version:item.version,state})});
            $('feedback-admin-result').textContent = 'Статус сообщения обновлён.';
            await load(true); await load(false);
          } catch(error) { $('feedback-admin-result').textContent = error.message; }
          finally { b.disabled = false; }
        };
        if (item.publicConsent && item.state !== 'PUBLISHED') { const b=button('Опубликовать',()=>change('PUBLISHED',b)); controls.append(b); }
        if (item.state !== 'ARCHIVED') { const b=button(item.state==='PUBLISHED'?'Снять с публикации':'В архив',()=>change('ARCHIVED',b)); controls.append(b); }
        if (item.state === 'ARCHIVED') { const b=button('Вернуть в новые',()=>change('PENDING',b)); controls.append(b); }
        card.append(controls);
      }
      target.append(card);
    }
    const nav = el('div','','feedback-controls');
    const go = p => { if(admin) adminPage=p; else publicPage=p; load(admin); };
    if (page>0) nav.append(button('← Назад',()=>go(page-1)));
    nav.append(el('span', `Сообщений: ${data.total} · Страница ${page+1}`));
    if ((page+1)*20 < data.total) nav.append(button('Далее →',()=>go(page+1)));
    (admin ? target : $('feedback-public-pages')).append(nav);
  } catch(error) {
    if (target._request !== marker || (admin && stamp !== generation)) return;
    target.replaceChildren(el('p',error.message),button('Повторить',()=>load(admin)));
  } finally {
    if (!admin) refreshFeedbackLayout();
  }
}
