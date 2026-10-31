// Saved content stays plain text. Paragraphs are created with textContent only.
export function proseParagraphs(text) {
  return String(text || '').replace(/\r\n?/g,'\n').split(/\n+/).map(line=>line.trim()).filter(Boolean);
}
export function fillProse(target,text) {
  target.classList.add('reading-prose');target.replaceChildren();
  for(const paragraph of proseParagraphs(text)) {
    const p=document.createElement('p');p.textContent=paragraph;target.append(p);
  }
}
export function setupProsePreviews(form) {
  const refreshers=[];
  for(const [name,label] of [['description','Описание книги'],['comment','Комментарий редактора']]) {
    const field=form.elements[name];
    const preview=document.createElement('section');preview.className='editorial-prose-preview';
    const title=document.createElement('h3');title.textContent='Предпросмотр: '+label.toLocaleLowerCase('ru');
    const body=document.createElement('div');body.id='editorial-preview-'+name;
    preview.setAttribute('aria-label','Предпросмотр: '+label.toLocaleLowerCase('ru'));preview.append(title,body);
    field.closest('label').after(preview);
    const refresh=()=>{fillProse(body,field.value);preview.hidden=!proseParagraphs(field.value).length;};
    field.addEventListener('input',refresh);refreshers.push(refresh);refresh();
  }
  return ()=>refreshers.forEach(refresh=>refresh());
}
