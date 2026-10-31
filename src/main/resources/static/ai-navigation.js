const $ = selector => document.querySelector(selector);
const views = {
  atlas: {hash:'#ai',src:'/ai-atlas.html',title:'Атлас направлений применения искусственного интеллекта',full:'Атлас на весь экран ↗'},
  architects: {hash:'#ai-architects',src:'/ai-architects.html',title:'Архитекторы AI: фотографии и биографии исследователей',full:'Архитекторы AI на весь экран ↗'},
  models: {hash:'#ai-models',src:'/ai-models.html',title:'Модели AI: что выбрать для вашей профессии',full:'Обзор моделей AI на весь экран ↗'},
  russia: {hash:'#ai-russia',src:'/ai-russia.html',title:'AI в России: история, модели и перспективы',full:'AI в России на весь экран ↗'},
  history: {hash:'#ai-history',src:'/ai-history.html',title:'История AI: иллюстрированная хронология',full:'История AI на весь экран ↗'},
};
export const isAIHash = hash => Object.values(views).some(view => view.hash === hash);
const fromHash = hash => Object.keys(views).find(key => views[key].hash === hash) || 'atlas';
let ensureBooks = () => {};
export function updateAISection(section) {
  const active = section?.id === 'ai' || String(section?.name || '').trim().toUpperCase() === 'AI';
  $('#catalog-ai-links').hidden = !active;
  if (!active) $('#ai').hidden = true;
}
export function openAI(key = fromHash(location.hash)) {
  ensureBooks();
  const view = views[key] || views.atlas;
  const section = $('#ai'), frame = $('#ai-frame');
  section.hidden = false;
  if (frame.getAttribute('src') !== view.src) {
    frame.style.height = '1600px';
    frame.src = view.src;
  }
  frame.title = view.title;
  for (const tab of document.querySelectorAll('[data-ai-view]')) {
    const selected = views[tab.dataset.aiView] === view;
    tab.setAttribute('aria-selected', String(selected));
    tab.tabIndex = selected ? 0 : -1;
    if (selected) $('#ai-panel').setAttribute('aria-labelledby',tab.id);
  }
  const full = $('#ai-fullscreen');
  full.href = view.src;
  full.textContent = view.full;
  section.scrollIntoView({block:'start'});
}
export function setupAINavigation({openBooks,ensureBooks:ensure = () => {}}) {
  ensureBooks = ensure;
  const tabs = [...document.querySelectorAll('[data-ai-view]')];
  for (const tab of tabs) {
    tab.addEventListener('click', () => openAI(tab.dataset.aiView));
    tab.addEventListener('keydown', event => {
      let index = tabs.indexOf(tab);
      if (event.key === 'ArrowRight') index = (index + 1) % tabs.length;
      else if (event.key === 'ArrowLeft') index = (index + tabs.length - 1) % tabs.length;
      else if (event.key === 'Home') index = 0;
      else if (event.key === 'End') index = tabs.length - 1;
      else return;
      event.preventDefault();
      tabs[index].focus();
      location.hash = views[tabs[index].dataset.aiView].hash;
      openAI(tabs[index].dataset.aiView);
    });
  }
  document.querySelectorAll('a[href="#catalog"]').forEach(link => link.addEventListener('click', () => { $('#ai').hidden = true; }));
  window.addEventListener('hashchange', () => {
    if (isAIHash(location.hash)) openAI();
    else if (location.hash === '#ai-books') openBooks();
    else $('#ai').hidden = true;
  });
  window.addEventListener('message', event => {
    const frame = $('#ai-frame');
    if (event.origin !== location.origin || event.source !== frame.contentWindow) return;
    const data = event.data;
    if (data?.type === 'ai-atlas-height' && Number.isFinite(data.height) && data.height >= 200 && data.height <= 60000) {
      frame.style.height = Math.ceil(data.height) + 'px';
    } else if (data?.type === 'ai-story-scroll' && Number.isFinite(data.top) && data.top >= 0 && data.top <= 60000) {
      window.scrollTo({top:Math.max(0,window.scrollY + frame.getBoundingClientRect().top + data.top - 24),behavior:'instant'});
    } else if (data?.type === 'ai-story-view' && Object.hasOwn(views,data.view)) {
      location.hash = views[data.view].hash;
      openAI(data.view);
    } else if (data?.type === 'ai-story-books') {
      openBooks();
      location.hash = '#catalog';
    }
  });
}
