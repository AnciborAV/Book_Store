import {validRuns,cleanStyle,applyStyle,selectionHas,remapStyles} from './text-style-model.js';
const editors=new WeakMap();
export function appendStyledText(target,text,runs=[],offset=0) {
 target.replaceChildren();const end=offset+text.length;let at=offset;
 for(const r of runs){const a=Math.max(offset,r.start),b=Math.min(end,r.end);if(a>=b||a<at)continue;
  if(a>at)target.append(document.createTextNode(text.slice(at-offset,a-offset)));
  const span=document.createElement('span'),style=cleanStyle(r);span.textContent=text.slice(a-offset,b-offset);
  if(style.bold)span.style.fontWeight='700';if(style.italic)span.style.fontStyle='italic';if(style.underline)span.style.textDecoration='underline';if(style.color)span.style.color=style.color;if(style.size)span.style.fontSize=style.size+'px';target.append(span);at=b;
 }if(at<end)target.append(document.createTextNode(text.slice(at-offset)));
}
export function renderComment(target,text,runs=[]) {
 target.classList.add('editorial-comment-prose');target.replaceChildren();let cursor=0;
 for(const block of String(text||'').split(/\n\s*\n/)){if(!block.trim())continue;const start=text.indexOf(block,cursor),p=document.createElement('p');appendStyledText(p,block,validRuns(text,runs),start);target.append(p);cursor=start+block.length;}
}
export function decorateProse(target,text,runs=[]) {
 const safe=validRuns(text,runs);if(!safe.length)return;let cursor=0;
 for(const node of target.children){const start=text.indexOf(node.textContent,cursor);if(start<0)continue;const original=node.textContent;appendStyledText(node,original,safe,start);cursor=start+original.length;}
}
export function fieldStyles(field){return editors.get(field)?.getStyles()||[];}
export function setupTextFormatting(field,label) {
 let before=field.value,runs=[],edit=null;const toggles=new Map();
 const updateToggles=()=>{for(const [key,button]of toggles)button.setAttribute('aria-pressed',String(selectionHas(field.value,runs,field.selectionStart,field.selectionEnd,key)));const current=runs.find(r=>r.start<=field.selectionStart&&r.end>field.selectionStart);color.value=current?.color||'#d6b576';size.value=current?.size?String(current.size):'';};
 const toolbar=document.createElement('div');toolbar.className='text-format-toolbar';toolbar.setAttribute('role','group');toolbar.setAttribute('aria-label','Оформление: '+label);
 const status=document.createElement('span');status.className='text-format-status';status.setAttribute('role','status');
 const focus=(start,end)=>{field.focus();field.setSelectionRange(start,end);};
 const apply=patch=>{const start=field.selectionStart,end=field.selectionEnd;if(start===end){status.textContent='Сначала выделите слово или фрагмент текста.';field.focus();return;}runs=applyStyle(field.value,runs,start,end,patch);status.textContent='Оформление применено. Результат виден в предпросмотре.';focus(start,end);field.dispatchEvent(new Event('input',{bubbles:true}));};
 for(const [key,text,title] of [['bold','Ж','Жирный'],['italic','К','Курсив'],['underline','Ч','Подчёркнутый']]){const button=document.createElement('button');button.type='button';button.className='format-'+key;button.textContent=text;button.setAttribute('aria-label',title+': '+label);button.title=title;button.addEventListener('mousedown',e=>e.preventDefault());button.onclick=()=>apply({[key]:!selectionHas(field.value,runs,field.selectionStart,field.selectionEnd,key)});toggles.set(key,button);button.setAttribute('aria-pressed','false');toolbar.append(button);}
 const colorLabel=document.createElement('label');colorLabel.textContent='Цвет';const color=document.createElement('input');color.type='color';color.value='#d6b576';color.setAttribute('aria-label','Цвет текста: '+label);color.addEventListener('input',()=>apply({color:color.value}));colorLabel.append(color);toolbar.append(colorLabel);
 const sizeLabel=document.createElement('label');sizeLabel.textContent='Размер';const size=document.createElement('select');size.setAttribute('aria-label','Размер текста: '+label);size.append(new Option('Обычный',''));for(const n of [12,14,16,18,20,24,28,32])size.append(new Option(n+' px',String(n)));size.onchange=()=>apply({size:size.value?Number(size.value):null});sizeLabel.append(size);toolbar.append(sizeLabel);
 const clear=document.createElement('button');clear.type='button';clear.textContent='Снять оформление';clear.addEventListener('mousedown',e=>e.preventDefault());clear.onclick=()=>apply({bold:false,italic:false,underline:false,color:null,size:null});toolbar.append(clear,status);
 field.closest('label')?.before(toolbar);if(!toolbar.isConnected)field.before(toolbar);
 field.addEventListener('beforeinput',e=>{let start=field.selectionStart,end=field.selectionEnd;if(start===end&&e.inputType==='deleteContentBackward')start=Math.max(0,start-1);if(start===end&&e.inputType==='deleteContentForward')end=Math.min(before.length,end+1);edit={start,end};});
 field.addEventListener('input',()=>{if(field.value!==before){runs=remapStyles(before,field.value,runs,edit);before=field.value;status.textContent='';}edit=null;updateToggles();});
 field.addEventListener('select',updateToggles);field.addEventListener('keyup',updateToggles);field.addEventListener('click',updateToggles);
 field.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&['b','i','u'].includes(e.key.toLowerCase())){e.preventDefault();const key={b:'bold',i:'italic',u:'underline'}[e.key.toLowerCase()];apply({[key]:!selectionHas(field.value,runs,field.selectionStart,field.selectionEnd,key)});}});
 const api={getStyles:()=>validRuns(field.value,runs),setValue(text,styles=[]){field.value=text||'';before=field.value;runs=validRuns(before,styles);edit=null;status.textContent='';size.value='';updateToggles();}};editors.set(field,api);return api;
}
