import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import vm from 'node:vm';
const source=await readFile(new URL('../src/main/resources/static/ai-russia.js',import.meta.url),'utf8');
const checked=Date.parse('2026-10-30T00:00:00Z');
for(const [days,hidden] of [[0,true],[90,true],[91,false]]){
 const note={hidden:true};
 vm.runInNewContext(source,{Date:{parse:Date.parse,now:()=>checked+days*86400000},document:{querySelector:s=>{assert.equal(s,'#ru-stale-note');return note;}}});
 assert.equal(note.hidden,hidden);
}
console.log('PASS AI Russia: dated overview reminder after 90 days');
