import { createAccountData } from './account-data.js';
import { createAccountView } from './account-view.js';

export function setupAccount({ getUser, setUser, isSelectionReady, isSelectionBusy,
  loadSelection, onProfileChange, toast, data = createAccountData(), view = createAccountView() }) {
  let register = false;
  async function loadProfile() {
    const previous = getUser()?.email;
    setUser(await data.loadProfile());
    view.renderProfile(getUser());
    onProfileChange();
    if (previous !== getUser()?.email || !isSelectionReady()) await loadSelection();
  }
  view.bind({
    open: () => view.open(getUser()),
    toggle() { register = !register; view.renderRegistration(register); },
    async submitAuth(event) {
      event.preventDefault();
      view.setAuthBusy(true);
      view.setAuthError('');
      const values = Object.fromEntries(new FormData(event.target));
      try {
        if (register) await data.register(values);
        await data.login(values);
        await data.renewCsrf();
        await loadProfile();
        view.completeAuth(event.target);
        toast(register ? 'Аккаунт создан' : 'Вы вошли в аккаунт');
      } catch (error) { view.setAuthError(error.message); }
      finally { view.setAuthBusy(false); }
    },
    async submitAvatar(event) {
      event.preventDefault();
      const button = event.target.querySelector('button');
      const file = event.target.file.files[0];
      view.setAvatarMessage('');
      if (!file || file.size > 2 * 1024 * 1024) {
        view.setAvatarMessage('Выберите PNG или JPEG до 2 МБ'); return;
      }
      button.disabled = true;
      try {
        await data.saveAvatar(new FormData(event.target));
        await loadProfile();
        view.setAvatarMessage('Аватарка сохранена');
        event.target.reset();
      } catch (error) { view.setAvatarMessage(error.message); }
      finally { button.disabled = false; }
    },
    async logout() {
      if (isSelectionBusy()) { toast('Дождитесь сохранения выбора'); return; }
      try {
        await data.logout();
        await loadProfile();
        view.closeProfile();
        toast('Вы вышли из аккаунта');
      } catch (error) { toast(error.message); }
    },
  });
  return { loadProfile };
}
