let token = null;
export function resetCsrf() { token = null; }
export async function csrf(){const r=await fetch('/api/auth/csrf');if(!r.ok)throw Error('Не удалось подключиться к серверу');token=await r.json();}
export async function request(url,options={}){
 const method=options.method||'GET';const headers=new Headers(options.headers||{});
 if(method!=='GET'){if(!token)await csrf();headers.set(token.headerName,token.token);}
 const r=await fetch(url,{...options,headers});
 if(!r.ok){let message=r.status===401?(url==='/api/auth/login'?'Неверная почта или пароль':'Войдите в аккаунт, чтобы сохранить выбор'):r.status===409?'Такая запись уже существует':r.status===403?'Сессия обновилась. Повторите действие.':r.status===413?'Файл слишком большой':'Не удалось выполнить запрос';try{const data=await r.json();if(data.detail)message=data.detail;}catch(_){}if(r.status===403){token=null;}throw Error(message);}
 return r.status===204?null:r.headers.get('content-type')?.includes('application/json')?r.json():null;
}
