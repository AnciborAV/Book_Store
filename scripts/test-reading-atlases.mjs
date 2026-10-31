import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {atlases} from '../src/main/resources/static/reading-atlas-data.js';
import {atlasForSection,updateAtlasLink} from '../src/main/resources/static/atlas-navigation.js';
for (const [key,a] of Object.entries(atlases)) {
 const ids=new Set(a.nodes.map(n=>n.id));assert.equal(ids.size,a.nodes.length,key+': unique IDs');
 for(const n of a.nodes){assert.ok(a.eras[n.row]&&a.lanes[n.lane],key+': valid coordinates');assert.ok(n.detail&&n.works.length&&n.authors);for(const id of n.related)assert.ok(ids.has(id),`${key}: dangling link ${id}`);}
 for(const [,url]of a.sources)assert.equal(new URL(url).protocol,'https:');
}
for(const [name,id]of [['Российские авторы','russian'],['Русская литература','russian'],['Русские авторы','russian'],['Зарубежные авторы','foreign'],['Зарубежная литература','foreign'],['Философия','philosophy']])assert.equal(atlasForSection({id:'generated-42',name}).id,id);
for(const name of ['Java','История','Гражданское право','AI','Все разделы',''])assert.equal(atlasForSection({name}),null);
const link={removeAttribute(k){delete this[k]}};updateAtlasLink(link,{name:'Философия'});assert.equal(link.href,'/reading-atlas.html?atlas=philosophy');assert.equal(link.hidden,false);updateAtlasLink(link,{name:'Java'});assert.equal(link.hidden,true);assert.equal(link.href,undefined);
const html=await readFile(new URL('../src/main/resources/static/index.html',import.meta.url),'utf8');assert.ok(html.indexOf('Моя полка')<html.indexOf('id="catalog-atlas"')&&html.indexOf('id="catalog-atlas"')<html.indexOf('id="catalog-count"'));
assert.ok(!html.includes('id="sort"'));
console.log('PASS: atlas data integrity, links, dynamic section names, switching and toolbar placement');
