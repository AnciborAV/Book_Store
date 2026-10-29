// Resize the embedded atlas without a second scrollbar; direct viewing also works.
function reportHeight() {
  if (parent !== window) parent.postMessage({type: 'ai-atlas-height', height: document.querySelector('main').getBoundingClientRect().height}, location.origin);
}
new ResizeObserver(reportHeight).observe(document.querySelector('main'));
window.addEventListener('load', reportHeight);
