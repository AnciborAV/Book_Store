import { atlasForSection } from './atlas-navigation.js';
import { isAIHash, openAI, setupAINavigation } from './ai-navigation.js';

export function setupApplicationNavigation({ getCategory, getSections, showSection,
  document: doc = document, window: win = window,
  ai = { isAIHash, openAI, setupAINavigation }, atlas = atlasForSection, dialogs, closeButtons }) {
  const $ = selector => doc.querySelector(selector);
  const all = selector => doc.querySelectorAll(selector);
  (closeButtons ?? all('dialog .close')).forEach(button => {
    button.onclick = () => button.closest('dialog').close();
  });
  (dialogs ?? all('dialog')).forEach(dialog => dialog.addEventListener('click', event => {
    if (event.target !== dialog) {
      return;
    }
    const rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right
    || event.clientY < rect.top || event.clientY > rect.bottom) {
      dialog.close();
    }
  }));
  const menuToggle = $('#menu-toggle');
  function closeMobileMenu() {
    menuToggle.setAttribute('aria-expanded', 'false');
  }
  menuToggle.addEventListener('click', () => {
    menuToggle.setAttribute('aria-expanded', String(menuToggle.getAttribute('aria-expanded') !== 'true'));
  });
  $('#main-navigation').addEventListener('click', event => {
    if (event.target.closest('a')) {
      closeMobileMenu();
    }
  });
  doc.addEventListener('keydown', event => {
    if (event.key === 'Escape' && menuToggle.getAttribute('aria-expanded') === 'true') {
      closeMobileMenu();
      menuToggle.focus();
    }
  });
  win.matchMedia('(max-width: 600px)').addEventListener('change', closeMobileMenu);
  doc.documentElement.classList.add('mobile-navigation-ready');
  function openAIBooks() {
    $('#ai').hidden = true;
    showSection('ai');
    $('#catalog').scrollIntoView({ block: 'start' });
  }
  function openDigitalBooks() {
    const section = getSections().find(section => atlas(section)?.id === 'digital');
    if (!section) {
      $('#catalog').scrollIntoView({ block: 'start' });
      return;
    }
    $('#ai').hidden = true;
    showSection(section.id);
    $('#catalog').scrollIntoView({ block: 'start' });
  }
  all('[data-open-ai-books]').forEach(link => link.addEventListener('click', openAIBooks));
  ai.setupAINavigation({ openBooks: openAIBooks, ensureBooks: () => {
      if (getCategory() !== 'ai') {
        openAIBooks();
      }
    }
  });
  win.addEventListener('hashchange', () => {
    if (win.location.hash === '#digital-books') {
      openDigitalBooks();
    }
  });
  return {
    selectCategory(id) {
      $('#ai').hidden = true;
      if (ai.isAIHash(win.location.hash)) {
        win.history.replaceState(null, '', '#catalog');
      }
      showSection(id);
    },
    openInitialRoute() {
      if (ai.isAIHash(win.location.hash)) {
        ai.openAI();
      } else
      if (win.location.hash === '#ai-books') {
        openAIBooks();
      } else
      if (win.location.hash === '#digital-books') {
        openDigitalBooks();
      }
    },
  };
}
