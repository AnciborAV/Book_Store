import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import vm from 'node:vm';
const nodes=new Map(),all=[];
class Element {
 constructor(tag='div'){this.tagName=tag;this.children=[];this.files=[];this.checked=false;this.disabled=false;this.hidden=false;this.listeners={};this._value='';this.className='';all.push(this);}
 set id(v){this._id=v;nodes.set('#'+v,this);}get id(){return this._id;}
 set value(v){this._value=String(v);if(this.type==='file'&&v==='')this.files=[];}get value(){return this._value;}
 set innerHTML(html){let form;for(const m of html.matchAll(/<(form|input|textarea|select|button|p|img)\b([^>]*)>/g)){
  const e=new Element(m[1]),attrs={};for(const a of m[2].matchAll(/([\w-]+)="([^"]*)"/g))attrs[a[1]]=a[2];if(attrs.id)e.id=attrs.id;if(attrs.type)e.type=attrs.type;
  if(m[1]==='form'){form=e;form.elements=[];form.reset=()=>{for(const c of form.elements){c.value='';c.checked=false;c.files=[];}};}
  if(attrs.name&&form){e.name=attrs.name;form.elements.push(e);form.elements[e.name]=e;}
  if(m[2].includes('data-managed-close')){this.closeButtons??=[];this.closeButtons.push(e);}
 }}
 append(...n){this.children.push(...n);if(this.tagName==='select'&&!this.value&&n.length)this.value=n[0].value;}after(e){this.afterElement=e;}before(e){this.beforeElement=e;}
 replaceChildren(...n){this.children=n;}setAttribute(k,v){this[k]=v;}removeAttribute(k){delete this[k];}
 closest(){return this.label??=new Element('label');}
 querySelectorAll(s){return s==='[data-managed-close]'?this.closeButtons||[]:[];}
 addEventListener(n,f){this.listeners[n]=f;}showModal(){this.open=true;}close(){this.open=false;this.listeners.close?.();}scrollIntoView(){}click(){this.onclick?.();}
}
const $=s=>{if(!nodes.has(s))nodes.set(s,new Element());return nodes.get(s);};
class FormDataFixture {
 constructor(form){this.data=new Map();for(const f of form.elements){if(f.type==='checkbox'){if(f.checked)this.data.set(f.name,'on');}else if(f.type==='file'){if(f.files[0])this.data.set(f.name,f.files[0]);}else this.data.set(f.name,f.value);}}
 get(k){return this.data.get(k);}set(k,v){this.data.set(k,String(v));}delete(k){this.data.delete(k);}
}
let user={role:'ADMIN'},record,requests=[],reloads=0,notices=[];
const sections=[{id:'s1',name:'История',position:10,version:0},{id:'s2',name:'Новая полка',position:20,version:0}];
const request=async(url,options)=>{
 requests.push({url,options});if(!options)return structuredClone(record);
 if(options.method==='POST'&&url.includes('/books')){
  const data=options.body;record={kind:'catalog',id:'123',title:data.get('title'),author:data.get('author'),description:data.get('description'),sectionId:data.get('sectionId'),version:record?record.version+1:0,pdfVersion:0,pdfId:data.get('pdf')?'pdf-123':record?.pdfId||null,cover:!!(data.get('cover')||record?.cover)};
 }
};
const document={querySelector:$,querySelectorAll:s=>s.startsWith('.')?all.filter(e=>e.className.split(' ').includes(s.slice(1))):[],createElement:t=>new Element(t),body:new Element('body'),dispatchEvent(){}};
const context={document,request,confirm:()=>true,URL,FormData:FormDataFixture,console,Event,Blob};vm.createContext(context);
const source=(await readFile(new URL('../src/main/resources/static/catalogue-admin.js',import.meta.url),'utf8')).replace(/^import[^\n]+\n/,'').replace(/export (async )?function /g,(_,a)=>(a||'')+'function ');
vm.runInContext(source,context);context.ctx={getUser:()=>user,toast:s=>notices.push(s),getSections:()=>sections,getSection:()=> 's1',reload:async()=>{reloads++;}};
vm.runInContext('setupCatalogueAdmin(ctx);updateCatalogueAdmin()',context);
await vm.runInContext('openBookManager(null,"s2")',context);
const form=$('#managed-book-form');assert.equal(form.elements.sectionId.value,'s2');assert.equal(form.elements.pdf.required,undefined); // PDF is optional.
form.elements.title.value='Моя книга';form.elements.author.value='Автор';form.elements.description.value='Описание';
const chosen=new Blob(['image'],{type:'image/png'});form.elements.cover.files=[chosen];form.elements.cover.onchange();
await form.onsubmit({preventDefault(){}});assert.equal(record.pdfId,null);assert.equal(record.cover,true);assert.equal(reloads,1);
await vm.runInContext('openBookManager({kind:"catalog",id:"123"})',context);
assert.equal(form.elements.title.value,'Моя книга');assert.equal(form.elements.sectionId.value,'s2');assert.equal(form.elements.cover.files.length,0);
form.elements.pdf.files=[{size:100,name:'book.pdf'}];form.elements.pdf.onchange();
await form.onsubmit({preventDefault(){}});
const saved=requests.filter(r=>r.options?.method==='POST').at(-1).options.body;
assert.equal(saved.get('version'),'0');assert.equal(saved.get('pdfVersion'),'0');assert.equal(saved.get('sectionId'),'s2');assert.equal(saved.get('cover'),undefined);assert.equal(record.pdfId,'pdf-123');
await vm.runInContext('openBookManager({kind:"catalog",id:"123"})',context);form.elements.pdf.files=[{size:501*1024*1024}];
const before=requests.length;await form.onsubmit({preventDefault(){}});assert.equal(requests.length,before);assert.match($('#managed-book-error').textContent,/500/);
user=null;vm.runInContext('updateCatalogueAdmin()',context);assert.equal($('#managed-book-editor').open,false);
assert.ok(all.filter(e=>e.className.split(' ').includes('catalogue-admin-control')).every(e=>e.hidden));
console.log('PASS catalogue manager: section selection, cover-only card, later PDF payload, cover preservation, limits, role/logout');
