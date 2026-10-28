import { request, resetCsrf } from './api-client.js';
const dialog = document.querySelector('#recovery-dialog');
const form = dialog.querySelector('form');
const message = dialog.querySelector('[role=status]');
const submit = form.querySelector('[type=submit]');
let resetToken = null;
const eye = '<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/><path class="eye-slash" d="m3 3 18 18"/></svg>';
// Shared by sign-in, registration, new password and password confirmation.
for (const input of document.querySelectorAll('input[type=password]')) {
  const wrapper = document.createElement('span'); wrapper.className = 'password-field';
  input.before(wrapper); wrapper.append(input);
  const button = document.createElement('button');
  button.type = 'button'; button.className = 'password-eye'; button.innerHTML = eye;
  button.setAttribute('aria-label', 'Показать пароль'); button.setAttribute('aria-pressed', 'false');
  wrapper.append(button);
  function conceal() {
    input.type = 'password'; button.setAttribute('aria-pressed', 'false');
    button.setAttribute('aria-label', 'Показать пароль');
  }
  button.addEventListener('click', () => {
    const show = input.type === 'password'; input.type = show ? 'text' : 'password';
    button.setAttribute('aria-pressed', String(show));
    button.setAttribute('aria-label', show ? 'Скрыть пароль' : 'Показать пароль');
  });
  input.form?.addEventListener('reset', conceal);
  input.closest('dialog')?.addEventListener('close', conceal);
  document.querySelector('#auth-toggle')?.addEventListener('click', conceal);
  document.addEventListener('visibilitychange', () => { if (document.hidden) conceal(); });
}
function openRecovery(token = null) {
  resetToken = token; form.reset(); message.textContent = ''; submit.hidden = false;
  form.querySelector('[name=email]').required = !token;
  form.querySelector('[name=email]').closest('label').hidden = !!token;
  for (const input of form.querySelectorAll('[name=password], [name=confirmation]')) {
    input.required = !!token; input.closest('label').hidden = !token;
  }
  dialog.querySelector('h2').textContent = token ? 'Новый пароль' : 'Забыли пароль?';
  submit.textContent = token ? 'Сохранить новый пароль' : 'Получить письмо';
  document.querySelector('#auth-dialog').close();
  dialog.showModal();
}
document.querySelector('#forgot-password').addEventListener('click', () => {
  const email = document.querySelector('#auth-form [name=email]').value;
  openRecovery(); form.elements.email.value = email;
});
dialog.querySelector('.close').addEventListener('click', () => dialog.close());
dialog.querySelector('[data-login]').addEventListener('click', () => {
  dialog.close(); document.querySelector('#auth-dialog').showModal();
});
form.addEventListener('submit', async event => {
  event.preventDefault(); message.textContent = ''; submit.disabled = true;
  const data = Object.fromEntries(new FormData(form));
  try {
    if (resetToken && data.password !== data.confirmation) throw Error('Пароли не совпадают');
    if (resetToken && new TextEncoder().encode(data.password).length > 72) throw Error('Пароль слишком длинный: максимум 72 байта');
    await request('/api/auth/password/' + (resetToken ? 'reset' : 'request'), {
      method: 'POST', headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(resetToken ? {token: resetToken, password: data.password, confirmation: data.confirmation} : {email: data.email})
    });
    message.textContent = resetToken
      ? 'Пароль изменён. Войдите с новым паролем.'
      : 'Если этот адрес зарегистрирован, на него придёт ссылка для смены пароля. Проверьте также папку «Спам». Повторный запрос доступен через минуту.';
    if (resetToken) { resetCsrf(); resetToken = null; form.reset(); submit.hidden = true; }
  } catch (error) { message.textContent = error.message; }
  finally { submit.disabled = false; }
});
if (location.hash.startsWith('#reset-password=')) {
  const token = location.hash.slice('#reset-password='.length);
  history.replaceState(null, '', location.pathname + location.search);
  openRecovery(token);
}
