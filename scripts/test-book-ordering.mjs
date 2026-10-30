import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const nodes=new Map();let active;
class Element {
 constructor(tag='div'){this.tag=tag;this.children=[];this.dataset={};this.listeners={};this.attrs={};this.value='';this.hidden=false;this.disabled=false;this.classes=new Set();this.classList={add:(...v)=>v.forEach(x=>this.classes.add(x)),remove:(...v)=>v.forEach(x=>this.classes.delete(x))};}
 set id(v){this._id=v;nodes.set('#'+v,this);}get id(){return this._id;}
 append(...e){this.children.push(...e);}replaceChildren(...e){this.children=e;}
 setAttribute(k,v){this.attrs[k]=v;}addEventListener(k,f){this.listeners[k]=f;}
 querySelectorAll(s){return s==='[data-shelf-close]'?this.closeButtons||[]:[];}
 showModal(){this.open=true;}close(){this.open=false;this.listeners.close?.();}focus(){active=this;}
 set innerHTML(s){for(const m of s.matchAll(/<(\w+)\b([^>]*)>/g)){const e=new Element(m[1]),id=m[2].match(/id="([^"]+)"/);if(id)e.id=id[1];if(m[2].includes('data-shelf-close'))(this.closeButtons??=[]).push(e);}}
}
const $=s=>{if(!nodes.has(s))nodes.set(s,new Element());return nodes.get(s);};
const base=[{kind:'catalog',id:'1',title:'A',sectionId:'s1'},{kind:'library',id:'1',title:'X',sectionId:'s2'},{kind:'catalog',id:'2',title:'B',sectionId:'s1'},{kind:'catalog',id:'3',title:'Y',sectionId:'s2'},{kind:'library',id:'2',title:'C',sectionId:'s1'}].map(b=>({...b,author:'Автор'}));
let data={books:structuredClone(base),sections:[{id:'s1',name:'AI'},{id:'s2',name:'Java'}],orderVersion:4},user={role:'ADMIN'},requests=[],notices=[],saved=[],allowConfirm=true,rejectSave=false;
const request=async(url,options)=>{requests.push({url,options});if(!options)return structuredClone(data);if(rejectSave)throw Error('Каталог изменился. Обновите полку.');const payload=JSON.parse(options.body);data.books=payload.books.map(key=>data.books.find(b=>b.kind===key.kind&&b.id===key.id));data.orderVersion++;};
const document={querySelector:$,createElement:t=>new Element(t),body:new Element('body'),get activeElement(){return active;}};
const ctx={request,document,confirm:()=>allowConfirm,console};vm.createContext(ctx);
const source=(await readFile(new URL('../src/main/resources/static/book-ordering.js',import.meta.url),'utf8')).replace(/^import[^\n]+\n/,'').replaceAll('export ','');vm.runInContext(source,ctx);
ctx.options={getUser:()=>user,toast:s=>notices.push(s),getSection:()=> 's1',createCover:()=>new Element('img'),onSaved:async section=>saved.push(section)};
vm.runInContext('setupBookOrdering(options);updateBookOrderingAdmin()',ctx);assert.equal($('#open-shelf-order').hidden,false);
await vm.runInContext('openBookOrdering()',ctx);
const dialog=$('#shelf-order-dialog'),grid=$('#shelf-order-grid');const titles=()=>grid.children.filter(e=>e.tag==='article').map(e=>e.children.find(c=>c.tag==='h3').textContent);
assert.equal(dialog.open,true);assert.deepEqual(titles(),['A','B','C']);assert.ok(grid.children.every(c=>c.draggable));
const first=grid.children[0],second=grid.children[1];const dt={setData(){}};first.listeners.dragstart({dataTransfer:dt});second.listeners.dragover({preventDefault(){},dataTransfer:dt});second.listeners.drop({preventDefault(){}});
assert.deepEqual(titles(),['B','A','C']);assert.equal(requests.filter(r=>r.options).length,0);assert.equal($('#shelf-order-save').disabled,false);
$('#shelf-order-reset').onclick();assert.deepEqual(titles(),['A','B','C']);assert.equal($('#shelf-order-save').disabled,true);
// Move by accessible button, then persist the full catalogue while preserving other-section slots.
grid.children[0].children.at(-1).children[1].onclick();assert.deepEqual(titles(),['B','A','C']);
await $('#shelf-order-save').onclick();const payload=JSON.parse(requests.at(-1).options.body);
assert.equal(payload.version,4);assert.deepEqual(payload.books.map(k=>k.kind+':'+k.id),['catalog:2','library:1','catalog:1','catalog:3','library:2']);assert.equal(dialog.open,false);assert.deepEqual(saved,['s1']);
await vm.runInContext('openBookOrdering()',ctx);assert.deepEqual(titles(),['B','A','C']);
grid.children[0].children.at(-1).children[1].onclick();rejectSave=true;await $('#shelf-order-save').onclick();
assert.equal(dialog.open,true);assert.match($('#shelf-order-error').textContent,/изменился/);assert.ok(grid.children.every(c=>c.draggable));assert.equal(grid.children[0].children.at(-1).children[1].disabled,false);
allowConfirm=false;dialog.listeners.cancel({preventDefault(){}});assert.equal(dialog.open,true);allowConfirm=true;dialog.listeners.cancel({preventDefault(){}});assert.equal(dialog.open,false);
user=null;vm.runInContext('updateBookOrderingAdmin()',ctx);assert.equal($('#open-shelf-order').hidden,true);const before=requests.length;await vm.runInContext('openBookOrdering()',ctx);assert.equal(requests.length,before);
// Invariants of a section move: stable other-section slots and no lost/duplicated identities.
ctx.base=base;const reordered=vm.runInContext('placeBook(base,"s1","catalog:1","library:2")',ctx);
assert.deepEqual(reordered.map(b=>b.title),['B','X','C','Y','A']);assert.equal(new Set(reordered.map(b=>b.kind+':'+b.id)).size,base.length);
assert.equal(reordered[1],base[1]);assert.equal(reordered[3],base[3]);
console.log('PASS book arrangement: drag/drop, section slots, buttons, local reset, full save, reopen, conflict recovery, discard guard and admin/logout');
