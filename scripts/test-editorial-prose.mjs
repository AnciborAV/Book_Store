import assert from 'node:assert/strict';
import {fillPreview,setupProsePreviews} from '../src/main/resources/static/editorial-prose.js';
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.classList={add:()=>{}};this.listeners={};this.dataset={};this.value='';this.label={after:el=>this.preview=el};}
 append(...els){this.children.push(...els);}replaceChildren(){this.children=[];}setAttribute(k,v){this[k]=v;}closest(){return this.label;}addEventListener(k,f){this.listeners[k]=f;}
}
globalThis.document={createElement:tag=>new Element(tag),createTextNode:text=>({tag:'#text',textContent:text})};
const target=new Element('div');fillPreview(target,'Первый абзац.\nПродолжение того же абзаца.\n\nВторой абзац.','description');
assert.equal(target.children.length,2);assert.equal(target.children[0].tag,'p');
fillPreview(target,'Комментарий.\nНовая строка.','comment');assert.equal(target.children.length,1);assert.equal(target.children[0].textContent,'Комментарий.\nНовая строка.');
fillPreview(target,'<img src=x onerror=alert(1)>.','comment');assert.equal(target.children[0].textContent,'<img src=x onerror=alert(1)>.');
const form={elements:{description:new Element('textarea'),comment:new Element('textarea')}};
const refresh=setupProsePreviews(form);assert.equal(form.elements.comment.preview.hidden,true);
form.elements.comment.value='Первый абзац\nНовый абзац';form.elements.comment.listeners.input();
assert.equal(form.elements.comment.preview.hidden,false);assert.equal(form.elements.comment.preview.children[1].children.length,1);
form.elements.description.value='Загруженное описание';refresh();assert.equal(form.elements.description.preview.children[1].children[0].textContent,'Загруженное описание');
const long='Длинный комментарий. '.repeat(800);form.elements.comment.value=long;refresh();assert.equal(form.elements.comment.value,long);
form.elements.comment.value='';refresh();assert.equal(form.elements.comment.preview.hidden,true);
console.log('PASS: unchanged paragraph rules, safe text, live previews, loaded/cleared fields and unchanged long input');
