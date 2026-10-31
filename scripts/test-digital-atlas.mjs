import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {topics} from '../src/main/resources/static/digital-atlas-data.js';
import {scenarios,cloudModels,cloudLayers} from '../src/main/resources/static/digital-atlas-core.js';
import {atlasForSection,updateAtlasLink} from '../src/main/resources/static/atlas-navigation.js';
assert.deepEqual(topics.map(t=>t.id),['data','ai','cloud','network','blockchain','security','quantum','ux']);
const html=await readFile(new URL('../src/main/resources/static/digital-atlas.html',import.meta.url),'utf8');
const ids=new Set(topics.map(t=>t.id));
for(const t of topics){assert.ok(html.includes(`id="topic-${t.id}"`));assert.ok(html.includes(t.name));assert.ok(t.body.length>=3);for(const key of ['deck','case','limits','future','practice'])assert.ok(t[key]);for(const b of t.body){assert.ok(html.includes(b.title));assert.ok(b.text.length>130);}for(const id of t.related)assert.ok(ids.has(id));for(const source of t.sources)assert.equal(new URL(source.url).protocol,'https:');assert.ok(!/[\u2013\u2014\u00ad]/u.test(JSON.stringify(t)),t.id);}
for(const s of Object.values(scenarios)){assert.equal(s.steps.length,3);assert.ok(s.note);}
for(const m of Object.values(cloudModels))assert.equal(m.owners.length,cloudLayers.length);
assert.match(cloudModels.saas.owners.at(-1),/Заказчик/);
for(const section of [{id:'digital-technologies',name:'Другое имя'},{id:'uuid',name:'Современные цифровые технологии'}]){assert.equal(atlasForSection(section).id,'digital');const link={};updateAtlasLink(link,section);assert.equal(link.hidden,false);assert.equal(link.href,'/digital-atlas.html');}
for(const key of ['ai-atlas','ai-architects','ai-history','ai-models','ai-russia'])assert.ok(html.includes(`href="/${key}.html"`));
const assets=await readdir(new URL('../src/main/resources/static/covers/digital-atlas/',import.meta.url));assert.equal(assets.length,9);
for(const name of assets){const svg=await readFile(new URL('../src/main/resources/static/covers/digital-atlas/'+name,import.meta.url),'utf8');assert.ok(svg.includes('<title'));assert.ok(!/<script|foreignObject|https?:/i.test(svg.replace('http://www.w3.org/2000/svg','')));}
assert.ok(html.includes('href="/index.html#digital-books"'));assert.ok(!html.includes('<iframe'));assert.ok(!html.includes('onchange='));
const index=await readFile(new URL('../src/main/resources/static/index.html',import.meta.url),'utf8');assert.ok(!/name="comment"[^>]*maxlength/.test(index));
console.log('PASS: eight directions, sources, related links, nine local illustrations, catalogue navigation and cloud models');
