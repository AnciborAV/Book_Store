// Preview only: saved text and public rendering are not changed.
export function fillPreview(target,text,kind) {
  target.replaceChildren();
  const value=String(text || '');
  const blocks=kind==='description'?value.split(/\n\s*\n/):[value];
  for(const block of blocks) {
    if(!block.trim())continue;
    const heading=kind==='description'&&block.length<65&&!/[.!?…]$/.test(block.trim());
    const node=document.createElement(heading?'h3':'p');node.textContent=block;target.append(node);
  }
}
export function setupProsePreviews(form) {
  const refreshers=[];
  for(const [name,label] of [['description','Описание книги'],['comment','Комментарий редактора']]) {
    const field=form.elements[name];
    const preview=document.createElement('section');preview.className='editorial-prose-preview';
    const title=document.createElement('h3');title.textContent='Предпросмотр: '+label.toLocaleLowerCase('ru');
    const body=document.createElement('div');body.id='editorial-preview-'+name;body.dataset.proseKind=name;
    preview.setAttribute('aria-label','Предпросмотр: '+label.toLocaleLowerCase('ru'));preview.append(title,body);
    field.closest('label').after(preview);
    const refresh=()=>{fillPreview(body,field.value,name);preview.hidden=!field.value.trim();};
    field.addEventListener('input',refresh);refreshers.push(refresh);refresh();
  }
  return ()=>refreshers.forEach(refresh=>refresh());
}
