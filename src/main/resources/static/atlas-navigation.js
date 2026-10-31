// Section IDs are generated from database records; match the canonical section names.
export function atlasForSection(section) {
  const name = String(section?.name || '').toLocaleLowerCase('ru').replaceAll('ё','е').trim();
  if (/^зарубежн(?:ая литература|ые авторы)$/.test(name)) return {id:'foreign',label:'Атлас зарубежной литературы'};
  if (/^(?:русская литература|русские авторы|российские авторы)$/.test(name)) return {id:'russian',label:'Атлас русской литературы'};
  if (section?.id === 'java' || name === 'java') return {id:'programming',label:'Атлас языков программирования',href:'/programming-atlas.html'};
  if (section?.id === 'digital-technologies' || name === 'современные цифровые технологии') return {id:'digital',label:'Атлас современных цифровых технологий',href:'/digital-atlas.html'};
  if (/^философия$/.test(name)) return {id:'philosophy',label:'Атлас философских направлений'};
  return null;
}
export function updateAtlasLink(link,section) {
  const atlas=atlasForSection(section);
  link.hidden=!atlas;
  if (!atlas) { link.removeAttribute('href'); return; }
  link.textContent=atlas.label+' ↗';
  link.href=atlas.href || '/reading-atlas.html?atlas='+atlas.id;
}
