import assert from 'node:assert/strict';
import {applyStyle,remapStyles,selectionHas,validRuns} from '../src/main/resources/static/text-style-model.js';
let runs=applyStyle('Hello world',[],0,5,{bold:true});assert.deepEqual(runs,[{start:0,end:5,bold:true}]);
runs=applyStyle('Hello world',runs,2,8,{underline:true,size:24});assert.equal(runs.length,3);assert.ok(selectionHas('Hello world',runs,0,5,'bold'));assert.ok(!selectionHas('Hello world',runs,0,8,'bold'));
assert.deepEqual(remapStyles('Hello','Hi Hello',[{start:0,end:5,bold:true}],{start:0,end:0}),[{start:0,end:8,bold:true}]);
assert.deepEqual(remapStyles('Hello','Hxllo',[{start:0,end:5,bold:true}],{start:1,end:2}),[{start:0,end:5,bold:true}]);
assert.deepEqual(remapStyles('Hello','He',[{start:0,end:5,bold:true}],{start:2,end:5}),[{start:0,end:2,bold:true}]);
assert.deepEqual(applyStyle('abc',[{start:0,end:3,bold:true}],0,3,{bold:false}),[]);
assert.deepEqual(validRuns('abc',[{start:0,end:3,color:'url(javascript:bad)',size:500}]),[]);
assert.deepEqual(applyStyle('😀 текст',[],0,2,{italic:true}),[{start:0,end:2,italic:true}]);
console.log('PASS: mixed selections, toggles, insertion/replacement/deletion, clearing, safe attributes and Unicode');
import {renderComment,appendStyledText} from '../src/main/resources/static/text-formatting.js';
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.style={};this.classList={add:()=>{}};this.own='';}
 set textContent(text){this.own=String(text);this.children=[];}get textContent(){return this.own+this.children.map(n=>n.textContent).join('');}
 replaceChildren(){this.children=[];this.own='';}append(...nodes){this.children.push(...nodes);}
}
globalThis.document={createElement:tag=>new Element(tag),createTextNode:text=>{const n=new Element('#text');n.textContent=text;return n;}};
const comment=new Element('div');renderComment(comment,'Первая строка\nПродолжение.\n\nНовый абзац.',[]);
assert.equal(comment.children.length,2);assert.equal(comment.children[0].textContent,'Первая строка\nПродолжение.');assert.equal(comment.children[1].textContent,'Новый абзац.');
const unsafe='<img src=x onerror=alert(1)>';const target=new Element('p');appendStyledText(target,unsafe,[{start:0,end:unsafe.length,bold:true,color:'url(javascript:bad)'}]);assert.equal(target.textContent,unsafe);assert.equal(target.children[0].tag,'span');assert.equal(target.children[0].style.color,undefined);
console.log('PASS: comment paragraphs retain single line breaks and markup is rendered as literal text');
