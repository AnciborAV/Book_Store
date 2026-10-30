import {readFile} from 'node:fs/promises';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const source=await readFile(new URL('../src/main/resources/static/ai-models.js',import.meta.url),'utf8');
const metadata=JSON.parse(await readFile(new URL('../docs/ai-models-sources.json',import.meta.url),'utf8'));
class Element {
 constructor(dataset={}){this.dataset=dataset;this.hidden=true;this.value='all';this.listeners={};}
 addEventListener(type,handler){this.listeners[type]=handler;}focus(){this.focused=true;}
}
function fixture(now='2026-10-30T12:00:00Z') {
 const cards=metadata.models.map(m=>Object.assign(new Element({aiModel:m.id,roles:m.roles.join(' ')}),{id:'model-'+m.id}));
 const guides=metadata.professions.map(p=>new Element({aiProfession:p.id}));
 const rows=metadata.models.map(m=>new Element({aiModelRow:m.id,roles:m.roles.join(' ')}));
 const nodes=Object.fromEntries(['#ai-model-filter','#ai-model-role','.ai-profession-grid','#ai-model-count','#ai-model-reset','#model-stale-note'].map(id=>[id,new Element()]));
 const docEvents={},winEvents={},location={hash:''};
 const context={Set,Date:class extends Date{static now(){return Date.parse(now);}},location,document:{querySelector:s=>nodes[s],querySelectorAll:s=>s==='[data-ai-model]'?cards:s==='[data-ai-profession]'?guides:rows,addEventListener:(type,fn)=>docEvents[type]=fn},window:{addEventListener:(type,fn)=>winEvents[type]=fn}};
 vm.createContext(context);vm.runInContext(source,context);
 return {cards,guides,rows,nodes,docEvents,winEvents,location};
}
const f=fixture(),select=f.nodes['#ai-model-role'];
assert.equal(f.nodes['#ai-model-filter'].hidden,false);assert.equal(f.cards.filter(c=>!c.hidden).length,10);assert.equal(f.guides.filter(c=>!c.hidden).length,9);
for(const profession of metadata.professions){
 select.value=profession.id;select.listeners.change();
 assert.deepEqual(f.cards.filter(c=>!c.hidden).map(c=>c.dataset.aiModel).sort(),[...profession.models].sort());
 assert.equal(f.guides.filter(c=>!c.hidden).length,1);assert.equal(f.guides.find(c=>!c.hidden).dataset.aiProfession,profession.id);
 assert.deepEqual(f.cards.map(c=>c.hidden),f.rows.map(c=>c.hidden));assert.match(f.nodes['#ai-model-count'].textContent,new RegExp(`Показано ${profession.models.length} из 10`));
}
select.value='legal';select.listeners.change();assert.equal(f.cards.find(c=>c.id==='model-runway').hidden,true);
f.docEvents.click({target:{closest:()=>({hash:'#model-runway'})}});assert.equal(select.value,'all');assert.equal(f.cards.every(c=>!c.hidden),true);
select.value='design';select.listeners.change();f.location.hash='#model-deepseek';f.winEvents.hashchange();assert.equal(select.value,'all');
select.value='invalid';select.listeners.change();assert.equal(select.value,'all');
select.value='math';select.listeners.change();f.nodes['#ai-model-reset'].listeners.click();assert.equal(select.value,'all');assert.equal(select.focused,true);assert.equal(f.rows.every(r=>!r.hidden),true);
let prevented=false;f.nodes['#ai-model-filter'].listeners.submit({preventDefault(){prevented=true;}});assert.equal(prevented,true);
assert.equal(f.nodes['#model-stale-note'].hidden,true);assert.equal(fixture('2027-02-01T00:00:00Z').nodes['#model-stale-note'].hidden,false);
console.log('PASS AI models: nine profession routes, card/table consistency, count, reset, hidden anchors, invalid selection and dated review');
