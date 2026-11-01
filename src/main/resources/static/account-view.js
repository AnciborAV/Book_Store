export function createAccountView({ query = selector => document.querySelector(selector),
  createElement = tag => document.createElement(tag), now = () => Date.now() } = {}) {
  const $ = query;
  function avatar(target, user) {
    target.replaceChildren();
    if (user?.hasAvatar) {
      const image = createElement('img');
      image.alt = 'Аватарка пользователя';
      image.src = '/api/profile/avatar?t=' + now();
      target.append(image);
    } else {
      target.textContent = user?.displayName?.[0]?.toUpperCase() || 'Ч';
    }
  }
  return {
    bind(handlers) {
      $('#account').onclick = handlers.open;
      $('#auth-toggle').onclick = handlers.toggle;
      $('#auth-form').onsubmit = handlers.submitAuth;
      $('#avatar-form').onsubmit = handlers.submitAvatar;
      $('#logout').onclick = handlers.logout;
    },
    open(user) {
      $(user ? '#profile-dialog' : '#auth-dialog').showModal();
    },
    renderProfile(user) {
      $('#account-label').textContent = user ? user.displayName : 'Войти';
      $('.mobile-account-label').textContent = user ? 'Кабинет' : 'Войти';
      $('#account').setAttribute('aria-label', user ? `Личный кабинет: ${user.displayName}` : 'Войти в личный кабинет');
      avatar($('#account .avatar'), user);
      if (user) {
        $('#profile-name').textContent = user.displayName;
        $('#profile-email').textContent = user.email;
        avatar($('#profile-avatar'), user);
      }
    },
    renderRegistration(register) {
      $('#name-field').hidden = !register;
      $('#name-field input').required = register;
      $('#auth-title').textContent = register ? 'Начнём вашу историю' : 'С возвращением';
      $('#auth-hint').textContent = register ? 'Создайте аккаунт и добавьте свою аватарку.' : 'Войдите в свой книжный мир.';
      $('#auth-submit').textContent = register ? 'Создать аккаунт ↗' : 'Войти ↗';
      $('#auth-toggle').textContent = register ? 'Уже есть аккаунт? Войти' : 'Нет аккаунта? Зарегистрироваться';
      $('#auth-form [name=password]').autocomplete = register ? 'new-password' : 'current-password';
      $('#auth-error').textContent = '';
      $('#forgot-password').hidden = register;
    },
    setAuthBusy(busy) {
      $('#auth-submit').disabled = busy;
    },
    setAuthError(message) {
      $('#auth-error').textContent = message;
    },
    completeAuth(form) {
      form.reset();
      $('#auth-dialog').close();
      $('#profile-dialog').showModal();
    },
    setAvatarMessage(message) {
      $('#avatar-message').textContent = message;
    },
    closeProfile() {
      $('#profile-dialog').close();
    },
  };
}
