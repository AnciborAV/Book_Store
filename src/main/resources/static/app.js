'use strict';
const $ = s => document.querySelector(s);
const demo = [
 {id:'demo-1',name:'Мастер и Маргарита',author:'Михаил Булгаков'},
 {id:'demo-2',name:'Маленький принц',author:'Антуан де Сент-Экзюпери'},
 {id:'demo-3',name:'Гордость и предубеждение',author:'Джейн Остин'},
 {id:'demo-4',name:'Тихие дни',author:'Выбор редакции'}
];
const palettes=[['#e7eadd','#34584a','#efe0b5','☾'],['#eee3d2','#d7ac74','#443e30','✧'],['#e9e5df','#a16a63','#f8e6d2','❀'],['#e1e7e6','#68878b','#fbefd3','≈']];
let books=[], user=null, token=null, register=false, filter='all', favorites=new Set();
try { favorites=new Set(JSON.parse(localStorage.getItem('bookstore-favorites') || '[]')); } catch (_) {}
const node=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};
function toast(message){$('#toast').textContent=message;$('#toast').style.display='block';setTimeout(()=>$('#toast').style.display='none',3500);}
async function csrf(){const r=await fetch('/api/auth/csrf');if(!r.ok)throw Error('Не удалось подключиться к серверу');token=await r.json();}
async function request(url,options={}){
 const method=options.method||'GET';const headers=new Headers(options.headers||{});
 if(method!=='GET'){if(!token)await csrf();headers.set(token.headerName,token.token);}
 const r=await fetch(url,{...options,headers});
 if(!r.ok){let message=r.status===401?'Неверная почта или пароль':r.status===409?'Такая запись уже существует':r.status===403?'Сессия обновилась. Повторите действие.':r.status===413?'Файл слишком большой':'Не удалось выполнить запрос';try{const data=await r.json();if(data.detail)message=data.detail;}catch(_){}if(r.status===403){token=null;}throw Error(message);}
 return r.status===204?null:r.headers.get('content-type')?.includes('application/json')?r.json():null;
}
function render(){
 const q=$('#search').value.toLocaleLowerCase('ru');
 const visible=books.filter(b=>(b.name+' '+b.author).toLocaleLowerCase('ru').includes(q)&&(filter!=='favorites'||favorites.has(String(b.id))));
 $('#books').replaceChildren();$('#catalog-count').textContent=`Найдено: ${visible.length}`;$('#favorite-count').textContent=favorites.size;
 if(!visible.length)$('#books').append(node('p',filter==='favorites'?'Нажмите на сердечко у книги, чтобы сохранить её на этом устройстве.':'Книги не найдены. Попробуйте другой запрос.'));
 visible.forEach(b=>{const i=books.indexOf(b)%palettes.length;const [bg,cover,text,symbol]=palettes[i];const card=node('article',undefined,'book-card');card.style.setProperty('--bg',bg);card.style.setProperty('--cover',cover);card.style.setProperty('--text',text);
 const image=node('div',undefined,'book-image'), binding=node('div',undefined,'cover');binding.append(node('small',b.author),node('strong',b.name),node('span',symbol,'cover-symbol'),node('span','ПЕРЕПЛЁТ · КОЛЛЕКЦИЯ','cover-footer'));image.append(binding);
 const saved=favorites.has(String(b.id));const button=node('button',saved?'♥':'♡','favorite'+(saved?' selected':''));button.setAttribute('aria-label',(saved?'Убрать с полки: ':'Добавить на полку: ')+b.name);button.setAttribute('aria-pressed',saved);button.onclick=()=>{saved?favorites.delete(String(b.id)):favorites.add(String(b.id));try{localStorage.setItem('bookstore-favorites',JSON.stringify([...favorites]));}catch(_){toast('Браузер не разрешает сохранение полки');}render();};image.append(button);card.append(image,node('h3',b.name),node('p',b.author));const meta=node('div',undefined,'book-meta');meta.append(node('span',String(b.id).startsWith('demo')?'ПОДБОРКА РЕДАКЦИИ':'В КАТАЛОГЕ'),node('span','↗'));card.append(meta);$('#books').append(card);
 });
}
async function loadBooks(){try{const data=await request('/books');books=data.length?data:demo;$('#catalog-message').textContent=data.length?'':'Знакомство с магазином: пример оформления полки. Добавьте свою первую книгу в каталог.';render();}catch(e){$('#catalog-message').textContent='Каталог недоступен. Проверьте подключение к серверу.';}}
function avatar(target){target.replaceChildren();if(user?.hasAvatar){const image=node('img');image.alt='Аватарка пользователя';image.src='/api/profile/avatar?t='+Date.now();target.append(image);}else target.textContent=user?.displayName?.[0]?.toUpperCase()||'Ч';}
async function loadProfile(){const r=await fetch('/api/profile');if(r.status===401){user=null;}else if(r.ok){user=await r.json();}else throw Error('Не удалось загрузить профиль');$('#account-label').textContent=user?user.displayName:'Войти';avatar($('#account .avatar'));if(user){$('#profile-name').textContent=user.displayName;$('#profile-email').textContent=user.email;avatar($('#profile-avatar'));}}
$('#search').oninput=render;
document.querySelectorAll('[data-filter]').forEach(b=>b.onclick=()=>{filter=b.dataset.filter;document.querySelectorAll('[data-filter]').forEach(x=>x.classList.toggle('active',x===b));render();});
document.querySelectorAll('dialog .close').forEach(b=>b.onclick=()=>b.closest('dialog').close());
document.querySelectorAll('dialog').forEach(d=>d.addEventListener('click',e=>{if(e.target===d){const r=d.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)d.close();}}));
$('#account').onclick=()=>{$(user?'#profile-dialog':'#auth-dialog').showModal();};
$('#auth-toggle').onclick=()=>{register=!register;$('#name-field').hidden=!register;$('#name-field input').required=register;$('#auth-title').textContent=register?'Начнём вашу историю':'С возвращением';$('#auth-hint').textContent=register?'Создайте аккаунт и добавьте свою аватарку.':'Войдите в свой книжный мир.';$('#auth-submit').textContent=register?'Создать аккаунт ↗':'Войти ↗';$('#auth-toggle').textContent=register?'Уже есть аккаунт? Войти':'Нет аккаунта? Зарегистрироваться';$('#auth-form [name=password]').autocomplete=register?'new-password':'current-password';$('#auth-error').textContent='';};
$('#auth-form').onsubmit=async e=>{e.preventDefault();const button=$('#auth-submit');button.disabled=true;$('#auth-error').textContent='';const data=Object.fromEntries(new FormData(e.target));try{if(register)await request('/api/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(data)});await request('/api/auth/login',{method:'POST',body:new URLSearchParams({email:data.email.trim().toLowerCase(),password:data.password})});token=null;await csrf();await loadProfile();e.target.reset();$('#auth-dialog').close();$('#profile-dialog').showModal();toast(register?'Аккаунт создан':'Вы вошли в аккаунт');}catch(err){$('#auth-error').textContent=err.message;}finally{button.disabled=false;}};
$('#avatar-form').onsubmit=async e=>{e.preventDefault();const button=e.target.querySelector('button');const file=e.target.file.files[0];$('#avatar-message').textContent='';if(!file||file.size>2*1024*1024){$('#avatar-message').textContent='Выберите PNG или JPEG до 2 МБ';return;}button.disabled=true;try{await request('/api/profile/avatar',{method:'PUT',body:new FormData(e.target)});await loadProfile();$('#avatar-message').textContent='Аватарка сохранена';e.target.reset();}catch(err){$('#avatar-message').textContent=err.message;}finally{button.disabled=false;}};
$('#logout').onclick=async()=>{try{await request('/api/auth/logout',{method:'POST'});token=null;await loadProfile();$('#profile-dialog').close();toast('Вы вышли из аккаунта');}catch(e){toast(e.message);}};
$('#add-book').onclick=()=>$('#book-dialog').showModal();
$('#book-form').onsubmit=async e=>{e.preventDefault();const button=e.target.querySelector('button');button.disabled=true;try{await request('/books',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(Object.fromEntries(new FormData(e.target)))});e.target.reset();$('#book-dialog').close();await loadBooks();toast('Книга добавлена в каталог');}catch(err){$('#book-error').textContent=err.message;}finally{button.disabled=false;}};
loadBooks();loadProfile().catch(e=>toast(e.message));
