import { request, csrf, resetCsrf } from './api-client.js';

export function createAccountData({ send = request, fetchProfile = () => fetch('/api/profile'),
  refreshCsrf = csrf, clearCsrf = resetCsrf } = {}) {
  return {
    async loadProfile() {
      const response = await fetchProfile();
      if (response.status === 401) return null;
      if (response.ok) return response.json();
      throw Error('Не удалось загрузить профиль');
    },
    register(data) {
      return send('/api/auth/register', { method: 'POST',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
    },
    login(data) {
      return send('/api/auth/login', { method: 'POST', body: new URLSearchParams({
        email: data.email.trim().toLowerCase(), password: data.password,
        'remember-me': data['remember-me'] === 'true' ? 'true' : 'false',
      }) });
    },
    async renewCsrf() { clearCsrf(); await refreshCsrf(); },
    saveAvatar(form) { return send('/api/profile/avatar', { method: 'PUT', body: form }); },
    async logout() { await send('/api/auth/logout', { method: 'POST' }); clearCsrf(); },
  };
}
