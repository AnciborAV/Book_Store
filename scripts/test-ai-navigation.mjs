import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import vm from 'node:vm';
class Element {
 constructor(id,dataset={}){this.id=id;this.dataset=dataset;this.attrs={};this.style={};this.listeners={};this.hidden=true;}
 setAttribute(k,v){this.attrs[k]=v;}getAttribute(k){return this.attrs[k]??null;}
 set src(v){this.attrs.src=v;}get src(){return this.attrs.src;}
 addEventListener(k,f){this.listeners[k]=f;}focus(){this.focused=true;}scrollIntoView(){this.scrolled=true;}
 getBoundingClientRect(){return {top:120};}
}
const elements=Object.fromEntries(['ai','ai-frame','ai-panel','ai-fullscreen'].map(id=>['#'+id,new Element(id)]));
const tabs=['atlas','architects','history','models'].map(view=>new Element('ai-tab-'+view,{aiView:view}));
const frame=elements['#ai-frame'];frame.contentWindow={};
const listeners={},location={hash:'#ai-history',origin:'https://bookstore.example'},scrolls=[];
let books=0;
const ctx={document:{querySelector:s=>elements[s],querySelectorAll:s=>s==='[data-ai-view]'?tabs:[]},location,window:{addEventListener:(n,f)=>listeners[n]=f,scrollY:200,scrollTo:x=>scrolls.push(x)},console};
vm.createContext(ctx);
const source=(await readFile(new URL('../src/main/resources/static/ai-navigation.js',import.meta.url),'utf8')).replaceAll('export ','');vm.runInContext(source,ctx);
ctx.books=()=>books++;vm.runInContext('setupAINavigation({openBooks:books});openAI()',ctx);
assert.equal(frame.src,'/ai-history.html');assert.equal(tabs[2].attrs['aria-selected'],'true');assert.equal(elements['#ai-panel'].attrs['aria-labelledby'],'ai-tab-history');assert.match(frame.title,/История/);
assert.equal(elements['#ai-fullscreen'].href,'/ai-history.html');
tabs[1].listeners.click();assert.equal(frame.src,'/ai-architects.html');assert.equal(tabs[1].tabIndex,0);assert.equal(tabs[2].tabIndex,-1);
let prevented=false;tabs[1].listeners.keydown({key:'ArrowRight',preventDefault(){prevented=true;}});assert.equal(prevented,true);assert.equal(tabs[2].focused,true);assert.equal(location.hash,'#ai-history');
tabs[2].listeners.keydown({key:'Home',preventDefault(){}});assert.equal(location.hash,'#ai');assert.equal(frame.src,'/ai-atlas.html');
const send=(data,origin=location.origin,from=frame.contentWindow)=>listeners.message({data,origin,source:from});
location.hash='#ai-models';listeners.hashchange();assert.equal(frame.src,'/ai-models.html');assert.equal(elements['#ai-fullscreen'].href,'/ai-models.html');assert.equal(tabs[3].attrs['aria-selected'],'true');
tabs[3].listeners.keydown({key:'ArrowRight',preventDefault(){}});assert.equal(frame.src,'/ai-atlas.html');
tabs[0].listeners.keydown({key:'End',preventDefault(){}});assert.equal(frame.src,'/ai-models.html');

send({type:'ai-atlas-height',height:21000});assert.equal(frame.style.height,'21000px');
for(const height of [NaN,Infinity,-1,100,60001,'5000'])send({type:'ai-atlas-height',height});assert.equal(frame.style.height,'21000px');
send({type:'ai-atlas-height',height:999},'https://other.example');send({type:'ai-atlas-height',height:999},location.origin,{});assert.equal(frame.style.height,'21000px');
send({type:'ai-story-view',view:'architects'});assert.equal(frame.src,'/ai-architects.html');assert.equal(location.hash,'#ai-architects');
send({type:'ai-story-view',view:'__proto__'});assert.equal(frame.src,'/ai-architects.html');
send({type:'ai-story-scroll',top:3000});assert.equal(scrolls.at(-1).top,3296);
send({type:'ai-story-books'});assert.equal(books,1);assert.equal(location.hash,'#catalog');listeners.hashchange();assert.equal(elements['#ai'].hidden,true);
location.hash='#ai-books';listeners.hashchange();assert.equal(books,2);location.hash='#ai-history';listeners.hashchange();assert.equal(elements['#ai'].hidden,false);
console.log('PASS AI navigation: direct links, tabs, keyboard, fullscreen, iframe sizing, chapter scroll, books and message validation');
// Verify the child document contract independently of the parent controller.
const childEvents={},messages=[],queued=[];
const chapter=new Element('era-alphago');chapter.getBoundingClientRect=()=>({top:900});
const anchor=new Element('chapter');anchor.hash='#era-alphago';
const catalog=new Element('catalog');const cross=new Element('cross',{storyView:'architects'});
const parent={postMessage:(data,origin)=>messages.push({data,origin})};
const childMain={getBoundingClientRect:()=>({height:18000})};
const child={parent,location:{origin:'https://bookstore.example'},requestAnimationFrame:f=>queued.push(f),ResizeObserver:class{observe(){}},document:{querySelector:()=>childMain,getElementById:id=>id==='era-alphago'?chapter:null,addEventListener:(n,f)=>childEvents[n]=f,querySelectorAll:s=>s==='a[href^="#"]'?[anchor]:s==='[data-ai-catalog]'?[catalog]:s==='[data-story-view]'?[cross]:[]},window:{scrollY:0,addEventListener:(n,f)=>childEvents[n]=f}};
vm.createContext(child);vm.runInContext(await readFile(new URL('../src/main/resources/static/ai-story.js',import.meta.url),'utf8'),child);
childEvents.load();childEvents.resize();assert.equal(queued.length,1);queued.shift()();assert.equal(messages.at(-1).data.height,18000);
anchor.listeners.click({preventDefault(){}});assert.equal(messages.at(-1).data.type,'ai-story-scroll');assert.equal(chapter.focused,true);
catalog.listeners.click({preventDefault(){}});assert.equal(messages.at(-1).data.type,'ai-story-books');
cross.listeners.click({preventDefault(){}});assert.equal(messages.at(-1).data.view,'architects');
assert.ok(messages.every(m=>m.origin===child.location.origin));
console.log('PASS AI embedded document: height coalescing, chapter focus/scroll, catalogue and cross-tab messages');
