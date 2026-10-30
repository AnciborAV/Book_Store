import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createRequire} from 'node:module';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=await readFile(new URL('../src/main/resources/static/reader.js',import.meta.url),'utf8');
const elements=new Map();
function element(){return {disabled:false,hidden:false,value:'width',textContent:'',style:{setProperty(){}},replaceChildren(){},replaceWith(){},setAttribute(){},getContext(){return{};},clientWidth:375,clientHeight:650,scrollTop:0,scrollLeft:0};}
function $(s){if(!elements.has(s))elements.set(s,element());return elements.get(s);}
const memory=new Map(),renders=[];
let failures=false;
const fake={GlobalWorkerOptions:{},TextLayer:class{render(){return Promise.resolve();}cancel(){}},getDocument(){return{destroy:async()=>{},promise:failures?Promise.reject(new Error('broken PDF')):Promise.resolve({numPages:2,getPage:async n=>({getViewport:({scale})=>({width:600*scale,height:800*scale}),streamTextContent(){return{};},render(){renders.push(n);return{promise:Promise.resolve(),cancel(){}};}})})};}};
const events={};
const document={querySelector:$,createElement:element,addEventListener:(n,f)=>events[n]=f,fullscreenEnabled:false,title:''};
const context={document,window:{document,addEventListener:(n,f)=>events[n]=f},location:{search:'?id=12345678-1234-1234-1234-123456789abc',origin:'https://test.invalid'},URL,URLSearchParams,fetch:async()=>({ok:true,json:async()=>[{title:'Test scan',version:0}]}),localStorage:{getItem:k=>memory.get(k),setItem:(k,v)=>memory.set(k,v)},devicePixelRatio:2,getComputedStyle:()=>({paddingLeft:'8'}),setTimeout,clearTimeout,console,pdfjs:fake};
vm.createContext(context);
const code=source.replace(/await import\('\/pdfjs\/[^']+'\)/g,'pdfjs');
vm.runInContext(code,context);
async function settle(){for(let i=0;i<20;i++)await Promise.resolve();}
await settle();assert.equal($('#status').textContent,'Страница 1 из 2');assert.equal($('#previous').disabled,true);
$('#next').onclick();await settle();assert.equal($('#status').textContent,'Страница 2 из 2');assert.equal($('#next').disabled,true);
vm.runInContext('go(99)',context);await settle();assert.equal($('#status').textContent,'Страница 2 из 2');
$('#zoom').value='page';$('#zoom').onchange();await settle();assert.equal($('#status').textContent,'Страница 2 из 2');
vm.runInContext('go(1);go(2);go(1)',context);await settle();assert.equal($('#status').textContent,'Страница 1 из 2');
events.keydown({target:{tagName:'BODY'},key:'ArrowRight',preventDefault(){}});await settle();assert.equal($('#status').textContent,'Страница 2 из 2');
await vm.runInContext('open()',context);assert.equal($('#status').textContent,'Страница 2 из 2');
failures=true;await vm.runInContext('open()',context);assert.equal($('#reader-error').hidden,false);assert.equal($('#next').disabled,true);
failures=false;await vm.runInContext('open()',context);assert.equal($('#reader-error').hidden,true);
console.log('PASS reader: navigation, bounds, zoom, rapid changes, keyboard, persistence, error and retry');
