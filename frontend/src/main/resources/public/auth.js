import Keycloak from './vendor/keycloak.js';

const login = document.querySelector('#login-button');
const logout = document.querySelector('#logout-button');
const identity = document.querySelector('#identity-label');
const notice = document.querySelector('#auth-notice');
let provider;
let ready;

function failure(message) {
  notice.textContent = message;
  notice.hidden = false;
}

window.ajAuth = {
  session: null,
  ready: () => ready,
  async headers() {
    if (!provider?.authenticated) return {};
    try {
      await provider.updateToken(30);
      return { Authorization: 'Bearer ' + provider.token };
    } catch {
      provider.clearToken(); window.ajAuth.session=null; document.dispatchEvent(new Event('identity-ready'));
      identity.textContent = 'Gæst';
      login.hidden = false;
      logout.hidden = true;
      failure('Dit login er udløbet. Log ind igen.');
      return {};
    }
  }
};

async function initialize() {
  const response = await fetch('/auth/config', {cache:'no-store'});
  if (!response.ok) throw new Error('Login-konfigurationen kunne ikke hentes.');
  provider = new Keycloak(await response.json());
  // The adapter owns PKCE/state/nonce handling. Tokens remain in memory, never localStorage.
  const authenticated = await provider.init({
    onLoad:'check-sso', pkceMethod:'S256', responseMode:'query', checkLoginIframe:false,
    silentCheckSsoRedirectUri:location.origin + '/silent-check-sso.html', silentCheckSsoFallback:false
  });
  notice.hidden = true;
  if (authenticated) {
    const session = await fetch('/api/v1/auth/session', {headers:await window.ajAuth.headers(), cache:'no-store'});
    if (!session.ok) throw new Error('Login kunne ikke bekræftes af serveren. Prøv at logge ind igen.');
    const user = await session.json();
    window.ajAuth.session = user;
    if (!user.roles.includes('admin')) {
      const profile = await fetch('/api/v1/users/me', {method:'POST', headers:await window.ajAuth.headers()});
      if (!profile.ok) throw new Error('Kundeprofilen kunne ikke tilknyttes.');
      window.ajAuth.session.customerGuid = (await profile.json()).guid;
    }
    document.dispatchEvent(new Event('identity-ready'));
    identity.textContent = user.username;
    identity.title = (user.roles || []).filter(role => ['customer','admin'].includes(role)).join(', ');
    login.hidden = true;
    logout.hidden = false;
  }
}

ready = initialize().catch(error => {
  provider = undefined; window.ajAuth.session=null; document.dispatchEvent(new Event('identity-ready'));
  failure(error.message || 'Login er midlertidigt utilgængeligt.');
});
ready.finally(() => { login.disabled = false; });
login.addEventListener('click', async () => {
  try {
    await ready.catch(() => {});
    if (!provider) { ready = initialize(); await ready; }
    await provider.login({redirectUri:location.origin + '/'});
  } catch {
    provider = undefined;
    failure('Login er midlertidigt utilgængeligt. Kontrollér at Keycloak kører.');
  }
});
logout.addEventListener('click', async () => {
  try { await provider.logout({redirectUri:location.origin + '/'}); }
  catch { failure('Logout kunne ikke gennemføres. Prøv igen.'); }
});
