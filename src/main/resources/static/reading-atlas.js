import {atlases} from './reading-atlas-data.js';
const $=s=>document.querySelector(s);
const make=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
const key=new URLSearchParams(location.search).get('atlas');
const atlas=atlases[key]||atlases.foreign;
const atlasKey=atlases[key]?key:'foreign';
const colors=['#bda267','#8aab98','#a18daf','#be926c'];
const cards=new Map();let scale=1,selected=null,lastFocused=null;
const viewport=$('#atlas-viewport'),map=$('#atlas-map'),space=$('#atlas-space'),dialog=$('#atlas-detail');
document.title=atlas.title+' · Переплёт';
$('#atlas-title').textContent=atlas.title;$('#atlas-kicker').textContent=atlas.kicker;$('#atlas-intro').textContent=atlas.intro;
$('.atlas-number').textContent=String(['foreign','russian','philosophy'].indexOf(atlasKey)+1).padStart(2,'0');
document.querySelectorAll('.atlas-header nav a').forEach(a=>{if(a.getAttribute('href')==='?atlas='+atlasKey)a.setAttribute('aria-current','page');});
const grid=$('#atlas-grid');grid.style.setProperty('--lanes',atlas.lanes.length);
grid.append(make('div','ЭПОХИ / КОНТЕКСТЫ','axis-heading'));
atlas.lanes.forEach((lane,i)=>{grid.append(make('div',lane,'lane-heading'));$('#atlas-lane').append(new Option(lane,String(i)));});
atlas.eras.forEach((era,r)=>{
  $('#atlas-era').append(new Option(era.title,String(r)));
  const heading=make('div',undefined,'era-heading');heading.append(make('h2',era.title),make('span',era.dates));grid.append(heading);
  atlas.lanes.forEach((_,lane)=>{
    const cell=make('div',undefined,'atlas-cell');
    const nodes=atlas.nodes.filter(n=>n.row===r&&n.lane===lane);if(!nodes.length)cell.classList.add('empty');
    nodes.forEach(n=>{
      const b=make('button',undefined,'map-card');b.type='button';b.id='node-'+n.id;b.dataset.node=n.id;b.style.setProperty('--lane-color',colors[lane]);
      b.append(make('span',String(atlas.nodes.indexOf(n)+1).padStart(2,'0'),'card-index'),make('span','↗','card-arrow'),make('h3',n.title),make('span',n.authors,'card-authors'),make('span',n.idea,'card-idea'));
      b.setAttribute('aria-haspopup','dialog');b.setAttribute('aria-controls','atlas-detail');b.onclick=()=>openDetail(n.id);cell.append(b);cards.set(n.id,b);
    });grid.append(cell);
  });
});
const normalize=s=>s.toLocaleLowerCase('ru').replaceAll('ё','е');
function filter(){
  const q=normalize($('#atlas-search').value.trim()),era=$('#atlas-era').value,lane=$('#atlas-lane').value;let count=0;
  for(const n of atlas.nodes){const match=(!q||normalize([n.title,n.authors,n.idea,n.detail,...n.works].join(' ')).includes(q))&&(era==='all'||n.row===Number(era))&&(lane==='all'||n.lane===Number(lane));cards.get(n.id).classList.toggle('dimmed',!match);cards.get(n.id).dataset.match=String(match);if(match)count++;}
  $('#atlas-status').textContent=count===atlas.nodes.length?`${count} тематических блоков`:`Найдено: ${count} из ${atlas.nodes.length}`;
}
$('#atlas-search').addEventListener('input',filter);$('#atlas-era').onchange=filter;$('#atlas-lane').onchange=filter;
$('#atlas-search').addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();const first=[...cards.values()].find(b=>b.dataset.match==='true');if(first){first.focus();first.scrollIntoView({block:'center',inline:'center'});}}});
function drawConnections(){
 const svg=$('#atlas-connections');svg.setAttribute('width',map.offsetWidth);svg.setAttribute('height',map.offsetHeight);
 svg.setAttribute('viewBox',`0 0 ${map.offsetWidth} ${map.offsetHeight}`);
 svg.replaceChildren();
 const ns='http://www.w3.org/2000/svg',defs=document.createElementNS(ns,'defs'),marker=document.createElementNS(ns,'marker');
 marker.id='atlas-arrow';marker.setAttribute('viewBox','0 0 10 10');marker.setAttribute('refX','8');marker.setAttribute('refY','5');marker.setAttribute('markerWidth','5');marker.setAttribute('markerHeight','5');marker.setAttribute('orient','auto-start-reverse');
 const tip=document.createElementNS(ns,'path');tip.setAttribute('d','M 0 0 L 10 5 L 0 10 z');tip.setAttribute('fill','#d4b376');marker.append(tip);defs.append(marker);svg.append(defs);
 const origin=map.getBoundingClientRect();
 for(const n of atlas.nodes)for(const id of n.related){
  const a=cards.get(n.id).getBoundingClientRect(),b=cards.get(id).getBoundingClientRect();
  let x1=(a.left+a.width/2-origin.left)/scale,y1=(a.bottom-origin.top)/scale,x2=(b.left+b.width/2-origin.left)/scale,y2=(b.top-origin.top)/scale;
  const path=document.createElementNS(ns,'path');
  if(Math.abs(y1-y2)<a.height/scale+30){x1=(a.right-origin.left)/scale;y1=(a.top+a.height/2-origin.top)/scale;x2=(b.left-origin.left)/scale;y2=(b.top+b.height/2-origin.top)/scale;}
  const mid=(y1+y2)/2;path.setAttribute('d',`M ${x1} ${y1} C ${x1} ${mid}, ${x2} ${mid}, ${x2} ${y2}`);path.setAttribute('marker-end','url(#atlas-arrow)');
  path.setAttribute('class','atlas-connection'+(selected&&(n.id===selected.id||id===selected.id)?' active':''));svg.append(path);
 }
}
function setScale(value,center=true){
 const old=scale;scale=Math.max(.12,Math.min(1.6,value));
 const x=(viewport.scrollLeft+viewport.clientWidth/2)/old,y=(viewport.scrollTop+viewport.clientHeight/2)/old;
 map.style.transform=`scale(${scale})`;map.style.left=Math.max(0,(viewport.clientWidth-map.offsetWidth*scale)/2)+'px';space.style.width=map.offsetWidth*scale+'px';space.style.height=map.offsetHeight*scale+'px';
 $('#zoom-value').value=Math.round(scale*100)+'%';$('#zoom-out').disabled=scale<=.12;$('#zoom-in').disabled=scale>=1.6;
 if(center){viewport.scrollLeft=x*scale-viewport.clientWidth/2;viewport.scrollTop=y*scale-viewport.clientHeight/2;}
 drawConnections();
}
function fitWidth(){setScale(Math.min(1,(viewport.clientWidth-12)/map.offsetWidth),false);viewport.scrollLeft=0;}
$('#fit-width').onclick=fitWidth;$('#fit-map').onclick=()=>{setScale(Math.min((viewport.clientWidth-12)/map.offsetWidth,(viewport.clientHeight-12)/map.offsetHeight),false);viewport.scrollTo(0,0);};
$('#zoom-in').onclick=()=>setScale(scale+.15);$('#zoom-out').onclick=()=>setScale(scale-.15);
const full=$('#atlas-fullscreen');
function syncExpanded(){full.textContent=document.fullscreenElement||document.documentElement.classList.contains('atlas-expanded')?'Свернуть карту ↙':'На весь экран ↗';requestAnimationFrame(fitWidth);}
full.onclick=async()=>{
 if(document.fullscreenElement){await document.exitFullscreen();return;}
 if(document.documentElement.classList.contains('atlas-expanded')){document.documentElement.classList.remove('atlas-expanded');syncExpanded();return;}
 try{if(!document.fullscreenEnabled)throw Error('unsupported');await document.documentElement.requestFullscreen();}
 catch{document.documentElement.classList.add('atlas-expanded');syncExpanded();}
};
document.addEventListener('fullscreenchange',syncExpanded);
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!dialog.open&&document.documentElement.classList.contains('atlas-expanded')){document.documentElement.classList.remove('atlas-expanded');syncExpanded();}});
function related(n){return atlas.nodes.filter(other=>n.related.includes(other.id)||other.related.includes(n.id));}
function openDetail(id){
 const n=atlas.nodes.find(n=>n.id===id);if(!n)return;
 if(!dialog.open)lastFocused=document.activeElement;
 selected=n;$('#detail-title').textContent=n.title;$('#detail-period').textContent=atlas.eras[n.row].title+' · '+atlas.eras[n.row].dates;$('#detail-authors').textContent=n.authors;$('#detail-text').textContent=n.detail;
 $('#detail-works').replaceChildren(...n.works.map(w=>make('li',w)));
 const links=$('#detail-links');links.replaceChildren();const neighbors=related(n);
 if(neighbors.length){links.append(make('p','СВЯЗИ И ДИАЛОГИ','related-title'));neighbors.forEach(other=>{const b=make('button',other.title+' →');b.type='button';b.onclick=()=>openDetail(other.id);links.append(b);});}
 cards.forEach((b,id)=>{b.classList.toggle('selected',id===n.id);b.classList.toggle('connected',neighbors.some(o=>o.id===id));});
 drawConnections();if(!dialog.open)dialog.showModal();dialog.scrollTop=0;$('.detail-close').focus();
}
function closeDetail(){dialog.close();}
$('.detail-close').onclick=closeDetail;
dialog.addEventListener('click',e=>{if(e.target===dialog){const r=dialog.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)closeDetail();}});
dialog.addEventListener('close',()=>{if(lastFocused?.isConnected)lastFocused.focus({preventScroll:true});});
$('#detail-locate').onclick=()=>{if(!selected)return;const b=cards.get(selected.id);dialog.close();setScale(Math.max(scale,.8),false);b.focus({preventScroll:true});b.scrollIntoView({block:'center',inline:'center',behavior:'instant'});};
$('#atlas-method').textContent=atlasKey==='philosophy'?'Содержание переработано по ранее подготовленной карте философских направлений: сохранены основные школы, представители и межкультурный охват. Дополнены пояснения и маршруты чтения. Строки отражают преимущественный исторический контекст, а не точные даты рождения каждой школы.':'Периодизация объединяет исторические эпохи, направления и культурные контексты. Европейские названия эпох не переносятся механически на литературы Азии, Африки и Америк. Один автор может принадлежать нескольким линиям.';
atlas.sources.forEach(([label,url])=>{const li=make('li'),a=make('a',label);a.href=url;a.target='_blank';a.rel='noopener noreferrer';li.append(a);$('#atlas-sources').append(li);});
filter();requestAnimationFrame(()=>{if(viewport.clientWidth<650)setScale(.85,false);else fitWidth();});
let width=viewport.clientWidth;new ResizeObserver(()=>{if(Math.abs(width-viewport.clientWidth)>10){width=viewport.clientWidth;fitWidth();}else setScale(scale,false);}).observe(viewport);
