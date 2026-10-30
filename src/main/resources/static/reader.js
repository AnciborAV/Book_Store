const $ = selector => document.querySelector(selector);
const id = new URLSearchParams(location.search).get('id');
let pdf, task, rendering, textLayer, serial = 0, wanted = 1, shown = 0, loading = false;
let resizeTimer, storageKey;
const status = message => { $('#status').textContent = message; };
function fail(message) {
  $('#reader-error').hidden = false; $('#error-message').textContent = message;
  status('Чтение прервано. Повторите попытку или скачайте PDF.');
}
function controls() {
  $('#previous').disabled = !pdf || wanted <= 1;
  $('#next').disabled = !pdf || wanted >= pdf.numPages;
  $('#page-number').disabled = $('#zoom').disabled = !pdf;
  $('#page-form button').disabled = !pdf;
}
async function renderPage() {
  if (!pdf) return;
  const ticket = ++serial, document = pdf, number = wanted;
  rendering?.cancel(); textLayer?.cancel();
  status(`Открываем страницу ${number}…`);
  try {
    const page = await document.getPage(number);
    if (ticket !== serial) return;
    const base = page.getViewport({ scale: 1 });
    const stage = $('#stage'), padding = parseFloat(getComputedStyle(stage).paddingLeft) * 2;
    const width = Math.max(100, stage.clientWidth - padding), height = Math.max(100, stage.clientHeight - padding);
    const zoom = $('#zoom').value;
    const scale = zoom === 'width' ? width / base.width : zoom === 'page' ? Math.min(width / base.width, height / base.height) : Number(zoom);
    const viewport = page.getViewport({ scale });
    // Bound the backing canvas on high-DPI phones; zoom still changes its CSS size.
    const density = Math.min(devicePixelRatio || 1, 2, Math.sqrt(8000000 / (viewport.width * viewport.height)));
    const canvas = window.document.createElement('canvas');
    canvas.width = Math.max(1, Math.floor(viewport.width * density)); canvas.height = Math.max(1, Math.floor(viewport.height * density));
    canvas.style.width = `${viewport.width}px`; canvas.style.height = `${viewport.height}px`; canvas.id = 'page-canvas'; canvas.setAttribute('aria-hidden', 'true');
    rendering = page.render({canvasContext:canvas.getContext('2d'),viewport,transform:[density,0,0,density,0,0]});
    await rendering.promise;
    if(ticket !== serial) return;
    const paper=$('#paper'); paper.style.width=`${viewport.width}px`;paper.style.height=`${viewport.height}px`;
    $('#page-canvas').replaceWith(canvas); paper.hidden=false; $('#reader-error').hidden=true;
    const layer=$('#text-layer');layer.replaceChildren();paper.style.setProperty('--scale-factor',String(scale));paper.style.setProperty('--total-scale-factor',String(scale));
    const { TextLayer } = await import('/pdfjs/5.6.205/build/pdf.min.mjs');
    if(ticket !== serial) return;
    textLayer=new TextLayer({textContentSource:page.streamTextContent(),container:layer,viewport});
    await textLayer.render();
    if(ticket !== serial) return;
    if(shown!==number){stage.scrollTop=0;stage.scrollLeft=0;}shown=number;
    $('#page-number').value=number;status(`Страница ${number} из ${pdf.numPages}`);
    try{localStorage.setItem(storageKey,String(number));}catch(_){}
    controls();
  } catch(error) {
    if(ticket===serial && error.name!=='RenderingCancelledException' && error.name!=='AbortException') fail('Страница не загрузилась. Возможно, файл обновлён или соединение прервалось.');
  }
}
function go(number) {
  if(!pdf || !Number.isInteger(number) || number<1 || number>pdf.numPages) { $('#page-number').value=wanted;return; }
  wanted=number;controls();renderPage();
}
async function open() {
  if(loading)return;loading=true;++serial;rendering?.cancel();textLayer?.cancel();
  if(task){try{await task.destroy();}catch(_){}task=null;}pdf=null;shown=0;controls();$('#paper').hidden=true;$('#reader-error').hidden=true;
  try {
    if(!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id||''))throw Error('Некорректная ссылка на книгу. Откройте её из каталога.');
    const response=await fetch('/api/java-library/metadata?'+new URLSearchParams({ids:id}));
    if(!response.ok)throw Error('Не удалось получить сведения о книге.');
    const [book]=await response.json();if(!book)throw Error('Книга больше не доступна в библиотеке.');
    $('#reader-title').textContent=book.title;document.title=book.title+' · Переплёт';
    const download=$('#reader-download');download.href= `/api/java-library/${id}/download`;download.hidden=false;
    status('Загружаем PDF…');
    const lib=await import('/pdfjs/5.6.205/build/pdf.min.mjs');
    lib.GlobalWorkerOptions.workerSrc='/pdfjs/5.6.205/build/pdf.worker.min.mjs';
    task=lib.getDocument({url: `/api/java-library/${id}/content?version=${book.version}`,cMapUrl:'/pdfjs/5.6.205/cmaps/',cMapPacked:true,standardFontDataUrl:'/pdfjs/5.6.205/standard_fonts/',wasmUrl:'/pdfjs/5.6.205/wasm/',isEvalSupported:false,enableXfa:false,disableAutoFetch:true,disableStream:true,rangeChunkSize:262144});
    task.onPassword=()=>{fail('Этот PDF защищён паролем. Скачайте оригинал и откройте его в подходящем приложении.');task.destroy().catch(()=>{});};
    pdf=await task.promise;
    storageKey=`bookstore-reading-v1:${id}:${book.version}`;let saved=1;
    try{saved=Number(localStorage.getItem(storageKey)||1);}catch(_){}
    wanted=Number.isInteger(saved)?Math.min(pdf.numPages,Math.max(1,saved)):1;
    $('#page-number').max=pdf.numPages;$('#page-total').textContent=`из ${pdf.numPages}`;controls();await renderPage();
  } catch(error) {fail(error.message || 'Не удалось загрузить PDF. Проверьте соединение.');}
  finally{loading=false;}
}
$('#previous').onclick=()=>go(wanted-1);$('#next').onclick=()=>go(wanted+1);
$('#page-form').onsubmit=e=>{e.preventDefault();go(Number($('#page-number').value));};
$('#page-number').onchange=()=>go(Number($('#page-number').value));$('#zoom').onchange=()=>renderPage();$('#retry').onclick=open;
document.addEventListener('keydown',event=>{if(/INPUT|SELECT|TEXTAREA/.test(event.target.tagName)||event.ctrlKey||event.metaKey||event.altKey)return;if(event.key==='ArrowRight'||event.key==='ArrowLeft'){event.preventDefault();go(wanted+(event.key==='ArrowRight'?1:-1));}});
window.addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(renderPage,180);});
if(document.fullscreenEnabled){$('#fullscreen').hidden=false;$('#fullscreen').onclick=async()=>{try{if(document.fullscreenElement)await document.exitFullscreen();else await document.documentElement.requestFullscreen();}catch(_){status('Полноэкранный режим недоступен. Можно продолжить чтение в этом окне.');}};document.addEventListener('fullscreenchange',()=>{$('#fullscreen').textContent=document.fullscreenElement?'Выйти из полного экрана':'Во весь экран';});}
$('#reader-back').onclick=event=>{if(document.referrer && new URL(document.referrer).origin===location.origin && history.length>1){event.preventDefault();history.back();}};
window.addEventListener('pagehide',()=>{++serial;rendering?.cancel();textLayer?.cancel();task?.destroy().catch(()=>{});});
window.addEventListener('pageshow',event=>{if(event.persisted)open();});
open();
