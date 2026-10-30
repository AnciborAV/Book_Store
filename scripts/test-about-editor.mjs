import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import vm from 'node:vm';
// Minimal DOM fixture for editor behavior. This is not a browser layout test.
const nodes=new Map();
class Element {
  constructor(tag='div'){this.tagName=tag.toUpperCase();this.hidden=false;this.children=[];this.attrs={};this.files=[];this.checked=false;this.disabled=false;this.listeners={};this._value='';this.textContent='';}
  set id(v){this._id=v;nodes.set('#'+v,this);}get id(){return this._id;}
  set value(v){this._value=v;if(this.type==='file' && v==='')this.files=[];}get value(){return this._value;}
  set innerHTML(html){
    this._html=html;let form;
    for(const m of html.matchAll(/<(form|input|textarea|button|p|img)\b([^>]*)>/g)){
      const e=new Element(m[1]);const attrs={};for(const a of m[2].matchAll(/([\w-]+)="([^"]*)"/g))attrs[a[1]]=a[2];
      if(attrs.id)e.id=attrs.id;if(attrs.type)e.type=attrs.type;
      if(m[1]==='form'){form=e;form.elements=[];form.reset=()=>{for(const el of form.elements){el.value='';el.checked=false;el.files=[];}};}
      if(attrs.name && form){e.name=attrs.name;form.elements.push(e);form.elements[e.name]=e;}
      if(m[2].includes('data-about-cancel')){this.cancels??=[];this.cancels.push(e);}
    }
  }
  get innerHTML(){return this._html||'';}
  setAttribute(k,v){this.attrs[k]=v;}removeAttribute(k){delete this.attrs[k];if(k==='src')delete this.src;if(k==='href')delete this.href;}
  append(...els){this.children.push(...els);}prepend(...els){this.children.unshift(...els);}before(e){this.beforeElement=e;}
  replaceChildren(...els){this.children=els;}
  querySelector(s){return this.local?.[s]||nodes.get(s);}
  querySelectorAll(s){return s==='[data-about-cancel]'?this.cancels||[]:[];}
  cloneNode(){const e=new Element(this.tagName);e.hidden=this.hidden;e.className=this.className;return e;}
  addEventListener(n,f){this.listeners[n]=f;}
  showModal(){this.open=true;}close(){this.open=false;this.listeners.close?.();}
}
const get=s=>{if(!nodes.has(s))nodes.set(s,new Element());return nodes.get(s);};
for(const s of ['#about','#about-heading','.about-story-lead','.about-story-signature','#about-catalog-link','#about-story-body','.about-author','#author-photo-dialog','#author-photo-dialog img','.about-story-development','.about-story-library','#about-load-status','#about-retry','.about-story-content','#logout','#profile-dialog'])get(s);
get('.about-author').local={img:new Element('img'),a:new Element('a')};nodes.set('.about-author-zoom',get('.about-author').local.a);
const navigation=new Element('a');let user=null,saveCount=0,failSave=false;
let content={title:'О магазине',lead:'Введение',body:'Первый\n\n<img src=x onerror=alert(1)>',signature:'Автор',linkLabel:'Каталог',photoAlt:'Фото',version:0,portrait:true,development:true,library:true};
const document={querySelector:get,querySelectorAll:s=>s==='a[href="#about"]'?[navigation]:[],createElement:t=>new Element(t),body:new Element('body')};
const request=async(url,options)=>{
  if(!options)return structuredClone(content);
  saveCount++;if(failSave)throw Error('Конфликт версий');
  const payload=JSON.parse(await options.body.get('content').text());content={...content,...payload,version:content.version+1};
  for(const s of ['portrait','development','library'])if(payload['remove'+s[0].toUpperCase()+s.slice(1)])content[s]=false;
  return structuredClone(content);
};
const context={document,request,URL,Blob,FormData,console};vm.createContext(context);
let source=await readFile(new URL('../src/main/resources/static/about.js',import.meta.url),'utf8');source=source.replace(/^import[^\n]+\n/,'').replace(/export function /g,'function ');
vm.runInContext(source,context);context.ctx={getUser:()=>user,toast:()=>{}};vm.runInContext('setupAbout(ctx)',context);
await new Promise(r=>setTimeout(r,0));
assert.equal(get('#about-heading').textContent,'О магазине');assert.equal(get('#about-story-body').children[1].textContent,'<img src=x onerror=alert(1)>');
assert.equal(get('#about-edit').hidden,true);assert.equal(get('#profile-edit-about').hidden,true);
user={role:'ADMIN'};vm.runInContext('updateAbout()',context);assert.equal(get('#about-edit').hidden,false);
await get('#profile-edit-about').onclick();const editor=get('#about-editor'),form=get('#about-editor-form');assert.equal(editor.open,true);assert.equal(form.elements.title.value,'О магазине');
get('#about-clear-text').onclick();assert.equal(form.elements.body.value,'');assert.equal(get('#about-heading').textContent,'О магазине');
editor.cancels[0].onclick();assert.equal(editor.open,false);assert.equal(saveCount,0);
await get('#about-edit').onclick();assert.equal(form.elements.body.value,content.body);
form.elements.title.value='Новый заголовок';failSave=true;await form.onsubmit({preventDefault(){}});assert.equal(editor.open,true);assert.equal(get('#about-editor-error').textContent,'Конфликт версий');assert.equal(form.elements.title.value,'Новый заголовок');
failSave=false;get('#about-clear-text').onclick();for(const s of ['portrait','development','library']){form.elements['remove-'+s].checked=true;form.elements['remove-'+s].onchange();}
await form.onsubmit({preventDefault(){}});assert.equal(editor.open,false);assert.equal(get('#about').hidden,true);assert.equal(navigation.hidden,true);assert.equal(get('.about-author').hidden,true);
await get('#profile-edit-about').onclick();assert.equal(editor.open,true);user=null;vm.runInContext('updateAbout()',context);assert.equal(editor.open,false);assert.equal(get('#profile-edit-about').hidden,true);
console.log('PASS about editor: roles, safe text, draft/cancel, failed save preservation, full deletion and logout');
