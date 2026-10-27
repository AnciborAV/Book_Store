'use strict';

// Состояние страницы. Пароли и личная полка не сохраняются в localStorage.
const $ = selector => document.querySelector(selector);
const palettes = [
    ['#e7eadd', '#34584a', '#efe0b5', '☾'],
    ['#eee3d2', '#d7ac74', '#443e30', '✧'],
    ['#e9e5df', '#a16a63', '#f8e6d2', '❀'],
    ['#e1e7e6', '#68878b', '#fbefd3', '≈']
];
let user = null;
let csrfToken = null;
let registerMode = false;
let favorites = new Set();
let page = 0;
let filter = 'all';
let searchTimer;
let catalogueRequest = 0;
let toastTimer;

function element(tag, text, className) {
    const result = document.createElement(tag);
    // Пользовательский текст вставляется как текст, а не как HTML.
    if (text !== undefined) result.textContent = text;
    if (className) result.className = className;
    return result;
}

function toast(message) {
    clearTimeout(toastTimer);
    $('#toast').textContent = message;
    $('#toast').style.display = 'block';
    toastTimer = setTimeout(() => $('#toast').style.display = 'none', 3500);
}

async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf');
    if (!response.ok) throw Error('Не удалось подключиться к серверу');
    csrfToken = await response.json();
}

async function request(url, options = {}) {
    const method = options.method || 'GET';
    const headers = new Headers(options.headers || {});
    if (method !== 'GET') {
        if (!csrfToken) await refreshCsrf();
        headers.set(csrfToken.headerName, csrfToken.token);
    }
    const response = await fetch(url, {...options, headers});
    if (!response.ok) {
        const messages = {
            401: 'Войдите в аккаунт; проверьте почту и пароль',
            403: 'Сессия обновилась. Повторите действие.',
            409: 'Такая запись уже существует',
            413: 'Файл слишком большой'
        };
        let message = messages[response.status] || 'Не удалось выполнить запрос';
        try {
            const data = await response.json();
            if (data.detail) message = data.detail;
        } catch (_) { /* Ответ без JSON: используем сообщение по HTTP-статусу. */ }
        if (response.status === 403) csrfToken = null;
        throw Error(message);
    }
    if (response.status === 204) return null;
    return response.headers.get('content-type')?.match(/application\/(?:problem\+)?json/)
        ? response.json() : null;
}

async function toggleFavorite(book, button) {
    if (!user) { $('#auth-dialog').showModal(); return; }
    button.disabled = true;
    try {
        await request('/api/shelf/' + book.id, {
            method: favorites.has(String(book.id)) ? 'DELETE' : 'PUT'
        });
        await loadShelf();
        await loadBooks();
    } catch (error) { toast(error.message); }
    finally { button.disabled = false; }
}

function bookCard(book, index) {
    const [background, cover, text, symbol] = palettes[index % palettes.length];
    const card = element('article', undefined, 'book-card');
    card.style.setProperty('--bg', background);
    card.style.setProperty('--cover', cover);
    card.style.setProperty('--text', text);
    const image = element('div', undefined, 'book-image');
    const binding = element('div', undefined, 'cover');
    binding.append(element('small', book.author), element('strong', book.name),
        element('span', symbol, 'cover-symbol'), element('span', 'ПЕРЕПЛЁТ · КОЛЛЕКЦИЯ', 'cover-footer'));
    const saved = favorites.has(String(book.id));
    const button = element('button', saved ? '♥' : '♡', 'favorite' + (saved ? ' selected' : ''));
    button.setAttribute('aria-label', (saved ? 'Убрать с полки: ' : 'Добавить на полку: ') + book.name);
    button.setAttribute('aria-pressed', String(saved));
    button.onclick = () => toggleFavorite(book, button);
    image.append(binding, button);
    card.append(image, element('h3', book.name), element('p', book.author));
    return card;
}

async function loadBooks() {
    // Поздний ответ предыдущего поиска не должен заменять более свежую выдачу.
    const sequence = ++catalogueRequest;
    if (filter === 'favorites' && !user) {
        $('#books').replaceChildren();
        $('#catalog-message').textContent = 'Войдите, чтобы открыть свою полку.';
        $('#catalog-count').textContent = '';
        $('#pagination').hidden = true;
        return;
    }
    const params = new URLSearchParams({query: $('#search').value, page, size: 12,
        favorites: filter === 'favorites'});
    $('#catalog-message').textContent = 'Загружаем книги…';
    try {
        const data = await request('/books/page?' + params);
        if (sequence !== catalogueRequest) return;
        if (page > 0 && page >= data.pages) { page--; return loadBooks(); }
        $('#books').replaceChildren(...data.items.map(bookCard));
        $('#catalog-count').textContent = `Найдено: ${data.total}`;
        $('#catalog-message').textContent = data.total ? '' : 'Здесь пока нет книг. Добавьте книгу или измените поиск.';
        $('#pagination').hidden = data.pages <= 1;
        $('#page-label').textContent = `Страница ${data.page + 1} из ${data.pages}`;
        $('#previous-page').disabled = page === 0;
        $('#next-page').disabled = page + 1 >= data.pages;
    } catch (error) {
        if (sequence !== catalogueRequest) return;
        $('#books').replaceChildren();
        $('#pagination').hidden = true;
        $('#catalog-count').textContent = '';
        $('#catalog-message').textContent = error.message;
    }
}

async function loadShelf() {
    favorites = new Set(user ? (await request('/api/shelf')).map(String) : []);
    $('#favorite-count').textContent = favorites.size;
}

function renderAvatar(target) {
    target.replaceChildren();
    if (user?.hasAvatar) {
        const image = element('img');
        image.alt = 'Аватарка пользователя';
        image.src = '/api/profile/avatar?t=' + Date.now();
        target.append(image);
    } else target.textContent = user?.displayName?.[0]?.toUpperCase() || 'Ч';
}

async function loadProfile() {
    const response = await fetch('/api/profile');
    if (response.status === 401) user = null;
    else if (response.ok) user = await response.json();
    else throw Error('Не удалось загрузить профиль');
    $('#account-label').textContent = user ? user.displayName : 'Войти';
    renderAvatar($('#account .avatar'));
    if (user) {
        $('#profile-name').textContent = user.displayName;
        $('#profile-email').textContent = user.email;
        renderAvatar($('#profile-avatar'));
    }
    await loadShelf();
    await loadBooks();
}

$('#search').oninput = () => {
    clearTimeout(searchTimer);
    catalogueRequest++;
    searchTimer = setTimeout(() => { page = 0; loadBooks(); }, 250);
};
$('#previous-page').onclick = () => { page--; loadBooks(); };
$('#next-page').onclick = () => { page++; loadBooks(); };
document.querySelectorAll('[data-filter]').forEach(button => {
    button.onclick = () => {
        filter = button.dataset.filter;
        page = 0;
        document.querySelectorAll('[data-filter]').forEach(item => item.classList.toggle('active', item === button));
        loadBooks();
    };
});
document.querySelectorAll('dialog .close').forEach(button => {
    button.onclick = () => button.closest('dialog').close();
});
$('#account').onclick = () => $(user ? '#profile-dialog' : '#auth-dialog').showModal();
$('#auth-toggle').onclick = () => {
    registerMode = !registerMode;
    $('#name-field').hidden = !registerMode;
    $('#name-field input').required = registerMode;
    $('#auth-title').textContent = registerMode ? 'Начнём вашу историю' : 'С возвращением';
    $('#auth-hint').textContent = registerMode ? 'Создайте аккаунт и свою книжную полку.' : 'Войдите в свой книжный мир.';
    $('#auth-submit').textContent = registerMode ? 'Создать аккаунт ↗' : 'Войти ↗';
    $('#auth-toggle').textContent = registerMode ? 'Уже есть аккаунт? Войти' : 'Нет аккаунта? Зарегистрироваться';
    $('#auth-form [name=password]').autocomplete = registerMode ? 'new-password' : 'current-password';
    $('#auth-error').textContent = '';
};
$('#auth-form').onsubmit = async event => {
    event.preventDefault();
    const button = $('#auth-submit');
    button.disabled = true;
    $('#auth-error').textContent = '';
    const data = Object.fromEntries(new FormData(event.target));
    try {
        if (registerMode) await request('/api/auth/register', {
            method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data)
        });
        await request('/api/auth/login', {method: 'POST',
            body: new URLSearchParams({email: data.email.trim().toLowerCase(), password: data.password})});
        csrfToken = null;
        await refreshCsrf();
        await loadProfile();
        event.target.reset();
        $('#auth-dialog').close();
        $('#profile-dialog').showModal();
        toast(registerMode ? 'Аккаунт создан' : 'Вы вошли в аккаунт');
    } catch (error) { $('#auth-error').textContent = error.message; }
    finally { button.disabled = false; }
};
$('#avatar-form').onsubmit = async event => {
    event.preventDefault();
    const button = event.target.querySelector('button');
    const file = event.target.elements.file.files[0];
    $('#avatar-message').textContent = '';
    if (!file || file.size > 2 * 1024 * 1024) {
        $('#avatar-message').textContent = 'Выберите PNG или JPEG до 2 МБ'; return;
    }
    button.disabled = true;
    try {
        await request('/api/profile/avatar', {method: 'PUT', body: new FormData(event.target)});
        await loadProfile();
        $('#avatar-message').textContent = 'Аватарка сохранена';
        event.target.reset();
    } catch (error) { $('#avatar-message').textContent = error.message; }
    finally { button.disabled = false; }
};
$('#logout').onclick = async () => {
    try {
        await request('/api/auth/logout', {method: 'POST'});
        csrfToken = null;
        await loadProfile();
        $('#profile-dialog').close();
        toast('Вы вышли из аккаунта');
    } catch (error) { toast(error.message); }
};
$('#add-book').onclick = () => {
    $('#book-error').textContent = '';
    $(user ? '#book-dialog' : '#auth-dialog').showModal();
};
$('#book-form').onsubmit = async event => {
    event.preventDefault();
    const button = event.target.querySelector('button');
    button.disabled = true;
    try {
        await request('/books', {method: 'POST', headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(Object.fromEntries(new FormData(event.target)))});
        event.target.reset();
        $('#book-dialog').close();
        page = 0;
        await loadBooks();
        toast('Книга добавлена в каталог');
    } catch (error) { $('#book-error').textContent = error.message; }
    finally { button.disabled = false; }
};
loadProfile().catch(error => { toast(error.message); loadBooks(); });
