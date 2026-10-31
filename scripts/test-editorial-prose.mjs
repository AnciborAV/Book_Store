import assert from 'node:assert/strict';
import {proseParagraphs,fillProse,setupProsePreviews} from '../src/main/resources/static/editorial-prose.js';
class Element {
 constructor(tag){this.tag=tag;this.children=[];this.classList={add:()=>{}};this.listeners={};this.value='';this.label={after:el=>this.preview=el};}
 append(...els){this.children.push(...els);}replaceChildren(){this.children=[];}setAttribute(k,v){this[k]=v;}closest(){return this.label;}addEventListener(k,f){this.listeners[k]=f;}
}
globalThis.document={createElement:tag=>new Element(tag)};
assert.deepEqual(proseParagraphs('Первый\r\nВторой\n\nТретий\rЧетвёртый'),['Первый','Второй','Третий','Четвёртый']);
assert.deepEqual(proseParagraphs(' \n\t\n'),[]);
const target=new Element('div');fillProse(target,'Короткий абзац\n\n<img src=x onerror=alert(1)>');
assert.deepEqual(target.children.map(x=>x.tag),['p','p']);assert.equal(target.children[1].textContent,'<img src=x onerror=alert(1)>');
const form={elements:{description:new Element('textarea'),comment:new Element('textarea')}};
const refresh=setupProsePreviews(form);assert.equal(form.elements.comment.preview.hidden,true);
form.elements.comment.value='Первый абзац\nНовый абзац';form.elements.comment.listeners.input();
assert.equal(form.elements.comment.preview.hidden,false);assert.equal(form.elements.comment.preview.children[1].children.length,2);
form.elements.description.value='Загруженное описание';refresh();assert.equal(form.elements.description.preview.children[1].children[0].textContent,'Загруженное описание');
const long='Длинный комментарий. '.repeat(800);form.elements.comment.value=long;refresh();assert.equal(form.elements.comment.value,long);
form.elements.comment.value='';refresh();assert.equal(form.elements.comment.preview.hidden,true);
console.log('PASS: paragraphs, single Enter, CRLF, safe text, live previews, loaded/cleared fields and unchanged long input');
