// The same document works on its own and inside the site's AI tab.
const main = document.querySelector('main');
let pending = false;
function reportHeight() {
  if (parent === window || pending) return;
  pending = true;
  requestAnimationFrame(() => {
    pending = false;
    parent.postMessage({type:'ai-atlas-height',height:Math.ceil(main.getBoundingClientRect().height)},location.origin);
  });
}
if ('ResizeObserver' in window) new ResizeObserver(reportHeight).observe(main);
window.addEventListener('load', reportHeight);
window.addEventListener('resize', reportHeight);
document.addEventListener('toggle', reportHeight, true);
// Local chapter links scroll the outer page when embedded, keeping a single scrollbar.
for (const link of document.querySelectorAll('a[href^="#"]')) {
  link.addEventListener('click', event => {
    if (parent === window) return;
    const target = document.getElementById(link.hash.slice(1));
    if (!target) return;
    event.preventDefault();
    target.tabIndex = -1;
    target.focus({preventScroll:true});
    parent.postMessage({type:'ai-story-scroll',top:Math.max(0,target.getBoundingClientRect().top+window.scrollY)},location.origin);
  });
}
for (const link of document.querySelectorAll('[data-ai-catalog]')) {
  link.addEventListener('click', event => {
    if (parent === window) return;
    event.preventDefault();
    parent.postMessage({type:'ai-story-books'},location.origin);
  });
}

for (const link of document.querySelectorAll('[data-story-view]')) {
  link.addEventListener('click', event => {
    if (parent === window) return;
    event.preventDefault();
    parent.postMessage({type:'ai-story-view',view:link.dataset.storyView},location.origin);
  });
}
