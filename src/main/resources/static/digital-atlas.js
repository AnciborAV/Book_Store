import {scenarios,cloudModels,cloudLayers} from './digital-atlas-core.js';
const $=selector=>document.querySelector(selector);
const make=(tag,text)=>{const element=document.createElement(tag);element.textContent=text;return element;};
$('#scenario-controls').hidden=false;
$('#scenario').addEventListener('change',event=>{
 const scenario=scenarios[event.target.value];if(!scenario)return;
 const root=$('#scenario-steps');root.replaceChildren();
 scenario.steps.forEach(([title,text],i)=>{const card=make('article','');card.append(make('span',String(i+1).padStart(2,'0')),make('h3',title),make('p',text));root.append(card);});
 $('#scenario-note').textContent=scenario.note;
});
$('#cloud-model-controls').hidden=false;
$('#cloud-model-controls').addEventListener('change',event=>{
 const model=cloudModels[event.target.value];if(!model)return;
 $('#cloud-caption').textContent=model.title;const body=$('#cloud-rows');body.replaceChildren();
 cloudLayers.forEach((layer,i)=>{const row=make('tr',''),heading=make('th',layer);heading.scope='row';row.append(heading,make('td',model.owners[i]));body.append(row);});
 $('#cloud-note').textContent=model.note;
});
