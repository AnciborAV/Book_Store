import {languages,eras} from './programming-atlas-data.js';
import {matchesLanguage,missions,machineProgram,createMachineState,stepMachine,hex,comparisonChoices} from './programming-atlas-core.js';
const $=selector=>document.querySelector(selector);
const make=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
const cards=new Map(),groups=new Map(),byId=new Map(languages.map(language=>[language.id,language]));
const dialog=$('#language-detail');let selected=null,lastFocus=null,copyTicket=0;
function paragraph(parent,text){for(const block of text.split(/\n\s*\n/).filter(Boolean))parent.append(make('p',block));}
for(const era of eras){
  $('#language-era').append(new Option(era.title,era.id));
  const group=make('section',undefined,'language-era');group.id='era-'+era.id;
  const heading=make('div',undefined,'language-era-heading');heading.append(make('span',era.dates),make('h3',era.title));
  const grid=make('div',undefined,'language-grid');group.append(heading,grid);$('#language-groups').append(group);groups.set(era.id,group);
  for(const language of languages.filter(l=>l.era===Number(era.id))){
    const card=make('button',undefined,'language-card');card.type='button';card.id='language-'+language.id;
    if(language.id==='java')card.classList.add('language-card-java');
    card.setAttribute('aria-haspopup','dialog');card.setAttribute('aria-controls','language-detail');
    card.append(make('span',language.year,'language-year'),make('span',language.name,'language-name'),make('span',language.idea,'language-idea'));
    const tags=make('span',undefined,'language-tags');language.uses.slice(0,3).forEach(tag=>tags.append(make('span',tag)));card.append(tags,make('span','История и код ↗','language-open'));
    card.onclick=()=>openLanguage(language.id);grid.append(card);cards.set(language.id,card);
  }
}
for(const value of [...new Set(languages.flatMap(l=>l.paradigms))].sort())$('#language-paradigm').append(new Option(value,value));
for(const value of [...new Set(languages.flatMap(l=>l.uses))].sort())$('#language-use').append(new Option(value,value));
function filter(){
 const filters={query:$('#language-search').value,era:$('#language-era').value,paradigm:$('#language-paradigm').value,use:$('#language-use').value,mission:$('#language-mission').value};let count=0;
 for(const language of languages){const match=matchesLanguage(language,filters);cards.get(language.id).hidden=!match;if(match)count++;}
 for(const group of groups.values())group.hidden=![...group.querySelectorAll('.language-card')].some(card=>!card.hidden);
 $('#language-count').textContent=count===languages.length?`${count} карточек`:`Найдено: ${count} из ${languages.length}`;
 $('#language-empty').hidden=count!==0;
 $('#mission-note').textContent=missions[filters.mission]?.note||'Задача помогает выбрать направление; конкретный язык зависит также от команды, платформы и существующего кода.';
}
function resetFilters(){for(const id of ['language-era','language-paradigm','language-use'])$('#'+id).value='all';$('#language-search').value='';$('#language-mission').value='';filter();}
$('.language-controls').addEventListener('submit',event=>event.preventDefault());
$('#language-search').addEventListener('input',filter);
for(const id of ['language-era','language-paradigm','language-use','language-mission'])$('#'+id).addEventListener('change',filter);
$('#language-reset').onclick=()=>{resetFilters();$('#language-search').focus();};
$('#language-search').addEventListener('keydown',event=>{if(event.key==='Enter'){event.preventDefault();const first=[...cards.values()].find(card=>!card.hidden);if(first){first.focus();first.scrollIntoView({block:'center'});}}});
function openLanguage(id){
 const language=byId.get(id);if(!language)return;
 if(!dialog.open)lastFocus=document.activeElement;
 selected=language;copyTicket++;$('#language-copy-status').textContent='';
 $('#language-detail-year').textContent=language.year;$('#language-detail-title').textContent=language.name;$('#language-detail-people').textContent=language.people;
 const tags=$('#language-detail-tags');tags.replaceChildren();[...language.paradigms,...language.uses].forEach(tag=>tags.append(make('span',tag)));
 const body=$('#language-detail-body');body.replaceChildren();paragraph(body,language.detail);
 $('#language-detail-limit').textContent=language.limit;$('#language-detail-dialect').textContent=language.dialect;$('#language-detail-code').textContent=language.code;$('#language-detail-explain').textContent=language.explain;
 $('#language-detail-source').href=language.source;
 const related=$('#language-related');related.replaceChildren();for(const id of language.related){const button=make('button',byId.get(id).name+' ↗');button.type='button';button.onclick=()=>openLanguage(id);related.append(button);}
 if(!dialog.open)dialog.showModal();dialog.scrollTop=0;$('#language-close').focus();
 history.replaceState(null,'',location.pathname+location.search+'#language-'+id);
}
function closeLanguage(){dialog.close();}
$('#language-close').onclick=closeLanguage;
dialog.addEventListener('close',()=>{copyTicket++;history.replaceState(null,'',location.pathname+location.search);if(lastFocus?.isConnected&&!lastFocus.hidden)lastFocus.focus();else $('#language-search').focus();});
$('#language-locate').onclick=()=>{const card=cards.get(selected.id);if(card.hidden)resetFilters();dialog.close();card.focus();card.scrollIntoView({block:'center'});};
$('#language-copy').onclick=async()=>{
 const ticket=++copyTicket,code=selected?.code;if(!code)return;
 try{await navigator.clipboard.writeText(code);if(ticket===copyTicket)$('#language-copy-status').textContent='Код скопирован.';}
 catch{if(ticket===copyTicket)$('#language-copy-status').textContent='Не удалось скопировать автоматически. Выделите текст примера и скопируйте его обычным способом.';}
};
let machine=createMachineState();
for(const instruction of machineProgram){const item=make('li');item.append(make('span',hex(instruction.address,4),'instruction-address'),make('code',instruction.bytes.map(byte=>hex(byte).slice(1)).join(' '),'instruction-bytes'),make('code',instruction.code,'instruction-code'));$('#machine-list').append(item);}
function paintMachine(){
 $('#machine-pc').textContent=hex(machine.pc,4);$('#machine-a').textContent=String(machine.a);$('#machine-c').textContent=String(machine.c);$('#machine-d').textContent=String(machine.d);$('#machine-memory').textContent=String(machine.memory);$('#machine-binary').textContent=machine.memory.toString(2).padStart(8,'0');
 [...$('#machine-list').children].forEach((item,i)=>{item.classList.toggle('instruction-next',i===machine.step&&!machine.halted);item.classList.toggle('instruction-done',i<machine.step);if(i===machine.step&&!machine.halted)item.setAttribute('aria-current','step');else item.removeAttribute('aria-current');});
 $('#machine-step').disabled=machine.halted;
 if(machine.step)$('#machine-status').textContent=`Шаг ${machine.step} из ${machineProgram.length}. ${machineProgram[machine.step-1].note}`;
 else $('#machine-status').textContent='Начальные значения для демонстрации: A = 0, C = 1, D = 1. Первые команды явно устанавливают нужные режимы.';
}
$('#machine-step').onclick=()=>{machine=stepMachine(machine);paintMachine();};$('#machine-reset').onclick=()=>{machine=createMachineState();paintMachine();};
const choices=comparisonChoices(languages),choiceMap=new Map(choices.map(choice=>[choice.id,choice]));
for(const id of ['compare-left','compare-right'])for(const choice of choices)$('#'+id).append(new Option(choice.name,choice.id));
$('#compare-left').value='lisp';$('#compare-right').value='java';
function paintComparison(){
 const root=$('#comparison-panels');root.replaceChildren();
 for(const side of ['left','right']){const choice=choiceMap.get($('#compare-'+side).value);const panel=make('article',undefined,'comparison-panel');panel.append(make('h3',choice.name),make('p',choice.dialect,'comparison-dialect'));const pre=make('pre');pre.tabIndex=0;pre.append(make('code',choice.code));panel.append(pre,make('p',choice.explain,'comparison-explain'),make('div','Проверяемый результат: 55','comparison-result'));root.append(panel);}
}
$('#compare-left').onchange=paintComparison;$('#compare-right').onchange=paintComparison;
filter();paintMachine();paintComparison();
const deepLink=location.hash.match(/^#language-([a-z]+)$/);if(deepLink&&byId.has(deepLink[1]))openLanguage(deepLink[1]);
