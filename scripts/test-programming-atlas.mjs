import assert from 'node:assert/strict';
import {writeFile,mkdir} from 'node:fs/promises';
import {languages,eras} from '../src/main/resources/static/programming-atlas-data.js';
import {missions,matchesLanguage,machineProgram,createMachineState,stepMachine,comparisonChoices} from '../src/main/resources/static/programming-atlas-core.js';
assert.equal(languages.length,37);
const ids=new Set(languages.map(l=>l.id));assert.equal(ids.size,37);
for(const l of languages){assert.ok(eras[l.era]);for(const k of ['name','year','idea','detail','limit','dialect','code','explain'])assert.ok(l[k],l.id+': '+k);assert.equal(new URL(l.source).protocol,'https:');for(const id of l.related)assert.ok(ids.has(id),id);for(const k of ['idea','detail','limit','explain'])assert.ok(!/[\u2013\u2014\u00ad]/u.test(l[k]),l.id);}
for(const m of Object.values(missions))for(const id of m.ids)assert.ok(ids.has(id));
const java=languages.find(l=>l.id==='java');assert.ok(matchesLanguage(java,{query:'JAVA',mission:'backend'}));assert.ok(!matchesLanguage(java,{mission:'web'}));assert.ok(!matchesLanguage(java,{query:'несуществующий'}));assert.ok(!matchesLanguage(java,{era:'0'}));
assert.deepEqual(machineProgram.flatMap(i=>i.bytes),[0xd8,0xa9,2,0x18,0x69,3,0x85,0x10,0]);
let s=createMachineState();const states=[];for(let i=0;i<6;i++){const before={...s};const next=stepMachine(s);assert.deepEqual(s,before);s=next;states.push(s);}
assert.equal(states[0].d,0);assert.equal(states[1].a,2);assert.equal(states[2].c,0);assert.equal(states[3].a,5);assert.equal(states[4].memory,5);assert.equal(s.pc,0x60a);assert.ok(s.halted);assert.equal(stepMachine(s),s);
const choices=comparisonChoices(languages);assert.equal(choices.length,9);assert.equal(choices[0].id,'lisp');assert.equal(choices[1].id,'java');
// Export actual published examples for independent compiler/runtime checks.
if(process.argv.includes('--export')){const dir=new URL('../target/atlas-examples/',import.meta.url);await mkdir(dir,{recursive:true});for(const [id,file]of [['java','AtlasExample.java'],['c','example.c'],['cplusplus','example.cpp'],['python','example.py'],['javascript','example.mjs']])await writeFile(new URL(file,dir),languages.find(l=>l.id===id).code);for(const [i,file]of [[7,'AtlasLoop.java'],[8,'AtlasStreams.java']])await writeFile(new URL(file,dir),choices[i].code);}
console.log('PASS: 37 cards, references, filters, nine comparisons, immutable six-step 6502 model');
