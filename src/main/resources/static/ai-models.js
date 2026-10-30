// Progressive enhancement: the full guide remains readable without JavaScript.
const form = document.querySelector('#ai-model-filter');
const select = document.querySelector('#ai-model-role');
const cards = [...document.querySelectorAll('[data-ai-model]')];
const guides = [...document.querySelectorAll('[data-ai-profession]')];
const rows = [...document.querySelectorAll('[data-ai-model-row]')];
const validRoles = new Set(['all', ...guides.map(guide => guide.dataset.aiProfession)]);
function applyRole(role = select.value) {
  if (!validRoles.has(role)) role = 'all';
  select.value = role;
  let visible = 0;
  for (const card of cards) {
    card.hidden = role !== 'all' && !card.dataset.roles.split(' ').includes(role);
    if (!card.hidden) visible++;
  }
  for (const row of rows) row.hidden = role !== 'all' && !row.dataset.roles.split(' ').includes(role);
  for (const guide of guides) guide.hidden = role !== 'all' && guide.dataset.aiProfession !== role;
  document.querySelector('.ai-profession-grid').dataset.filtered = String(role !== 'all');
  document.querySelector('#ai-model-count').textContent = `Показано ${visible} из ${cards.length} систем. Рекомендации зависят от конкретной задачи.`;
}
form.hidden = false;
form.addEventListener('submit', event => event.preventDefault());
select.addEventListener('change', () => applyRole());
document.querySelector('#ai-model-reset').addEventListener('click', () => {applyRole('all');select.focus();});
// An anchor must remain usable even if its card is outside the current filter.
function revealAnchor(hash) {
  if (!hash.startsWith('#model-')) return;
  const card = cards.find(item => '#' + item.id === hash);
  if (card?.hidden) applyRole('all');
}
document.addEventListener('click', event => {
  const link = event.target.closest?.('a[href^="#model-"]');
  if (link) revealAnchor(link.hash);
}, true);
window.addEventListener('hashchange', () => revealAnchor(location.hash));
applyRole();
revealAnchor(location.hash);
const checkedAt = Date.parse('2026-10-30T00:00:00Z');
if (Date.now() - checkedAt > 90 * 24 * 60 * 60 * 1000) document.querySelector('#model-stale-note').hidden = false;
