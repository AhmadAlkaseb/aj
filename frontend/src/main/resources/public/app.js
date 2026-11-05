'use strict';
const $ = (s, root = document) => root.querySelector(s);
const state = {
  products: [],
  users: [],
  orders: [],
  payments: [],
  services: [],
  page: 'catalog',
  checking: false,
  loading: new Set(),
};
const labels = {
  REVIEW_REQUIRED: 'Kræver manuel afklaring',
  RESERVING: 'Kontrollerer bil',
  REQUESTED: 'Kontrollerer ordre',
  AVAILABLE: 'Ledig',
  UNAVAILABLE: 'Ikke tilgængelig',
  RESERVED: 'Reserveret',
  SOLD: 'Solgt',
  PENDING: 'Afventer',
  PAYMENT_PENDING: 'Afventer betaling',
  COMPLETED: 'Afsluttet',
  FAILED: 'Mislykket',
  REJECTED: 'Afvist',
  CANCELLED: 'Annulleret',
};
const serviceNames = {
  users: 'Kunder',
  products: 'Biler og lager',
  orders: 'Ordrer',
  payments: 'Betalinger',
  'product-view': 'Bilkatalog',
  'product-synchronizer': 'Katalogopdatering',
  keycloak: 'Login (Keycloak)',
};
const esc = (v) =>
  String(v ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
const money = (value, currency = 'DKK') =>
  value == null || !currency
    ? 'Afventer bekræftelse'
    : new Intl.NumberFormat('da-DK', {
        style: 'currency',
        currency,
        maximumFractionDigits: 2,
      }).format(value);
const num = (value) => new Intl.NumberFormat('da-DK').format(value || 0);
const date = (value) =>
  value
    ? new Intl.DateTimeFormat('da-DK', { dateStyle: 'medium' }).format(
        new Date(value.endsWith?.('Z') ? value : value + 'Z'),
      )
    : '—';
const badge = (value) => `<span class="badge ${esc(value)}">${esc(labels[value] || value)}</span>`;
const short = (guid) => esc(guid?.slice(0, 8) || '—');
const isAdmin = () => window.ajAuth?.session?.roles?.includes('admin') || false;

async function request(path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    path.endsWith('description-draft') ? 60000 : 10000,
  );
  try {
    const authHeaders = (await window.ajAuth?.headers()) || {};
    const reference = path.match(/[0-9a-f]{8}-[0-9a-f-]{27,}/i)?.[0];
    if (reference) authHeaders['X-Correlation-ID'] = reference;
    const response = await fetch('/api/v1' + path, {
      ...options,
      signal: controller.signal,
      headers: { 'Content-Type': 'application/json', ...authHeaders, ...options.headers },
    });
    const body = response.status === 204 ? null : await response.json().catch(() => null);
    if (!response.ok)
      throw new Error(
        body?.message ||
          (response.status === 404
            ? 'Oplysningerne blev ikke fundet.'
            : 'Tjenesten svarer ikke lige nu. Prøv igen om lidt.'),
      );
    return body;
  } catch (e) {
    if (e.name === 'AbortError' || e instanceof TypeError)
      throw new Error(
        'Vi kan ikke få forbindelse lige nu. Kontrollér, at tjenesterne kører, og prøv igen.',
        { cause: e },
      );
    throw e;
  } finally {
    clearTimeout(timer);
  }
}
const commandKeys = new Map();
async function send(path, method, data) {
  const fingerprint = method + path + JSON.stringify(data),
    headers = {};
  if (method === 'POST') {
    if (!commandKeys.has(fingerprint)) commandKeys.set(fingerprint, crypto.randomUUID());
    headers['Idempotency-Key'] = commandKeys.get(fingerprint);
  }
  const result = await request(path, {
    method,
    headers,
    ...(data ? { body: JSON.stringify(data) } : {}),
  });
  commandKeys.delete(fingerprint);
  return result;
}
let toastTimer;
function toast(message) {
  $('#toast').textContent = message;
  $('#toast').hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => ($('#toast').hidden = true), 5000);
}

// Original vector illustration keeps the catalogue usable without external image services.
function carSvg(color = '#829582', id = 'car') {
  return `<svg class="car-svg" viewBox="0 0 600 260" role="img" aria-label="Illustration af en bil"><defs><linearGradient id="${id}" x1="0" y1="0" x2=".3" y2="1"><stop stop-color="#e0e8da"/><stop offset=".35" stop-color="${color}"/><stop offset="1" stop-color="#344a44"/></linearGradient></defs><ellipse cx="310" cy="219" rx="255" ry="16" fill="#233b32" opacity=".13"/><path d="M44 175 69 143 171 127 235 77 Q247 67 275 67 L365 71 Q391 74 415 97 L459 134 535 150 Q556 155 560 180 L552 202 46 202Z" fill="url(#${id})" stroke="#30443d" stroke-width="2"/><path d="m186 126 63-45 48-2-2 48Zm123-47 49 3q20 1 38 17l35 31-123-3Z" fill="#2e4544"/><path d="m202 119 49-35 31-1-1 33Z" fill="#acc2bb" opacity=".6"/><path d="m319 85 35 2q23 3 39 17l22 18-96-4Z" fill="#9eb8b1" opacity=".45"/><path d="m76 144 111-12 250 2 84 20M202 138l-4 57m107-59-1 57m135-56 12 55" fill="none" stroke="#2e4840" stroke-width="1.4" opacity=".6"/><path d="m83 151-22 18 58-7 10-13Zm413 6 42 7 8 14-38-6Z" fill="#f4f3d9"/><path d="M56 186h42m411 6h42" stroke="#1c302c" stroke-width="6"/><rect x="265" y="138" width="17" height="4" rx="2" fill="#c9d7ce"/><rect x="396" y="140" width="16" height="4" rx="2" fill="#c9d7ce"/><path d="m163 134 20-6 14 9-25 6Z" fill="#30473f"/><path d="M48 202h504" stroke="#263c34" stroke-width="8"/><g fill="#1f302c" stroke="#3a4b44" stroke-width="6"><circle cx="148" cy="198" r="39"/><circle cx="462" cy="198" r="39"/></g><g fill="#a9b5ac" stroke="#6b7d72" stroke-width="4"><circle cx="148" cy="198" r="25"/><circle cx="462" cy="198" r="25"/></g><g stroke="#344c40" stroke-width="4"><path d="m148 174v48m-24-24h48m-41-17 34 34m-34 0 34-34m297-7v48m-24-24h48m-41-17 34 34m-34 0 34-34"/></g><g fill="#dce3d9"><circle cx="148" cy="198" r="6"/><circle cx="462" cy="198" r="6"/></g></svg>`;
}
$('#hero-car').innerHTML = carSvg('#c0cec0', 'hero');

function empty(title, message, action = '') {
  return `<div class="empty"><span class="empty-symbol">↗</span><h3>${esc(title)}</h3><p>${esc(message)}</p>${action}</div>`;
}
function showModal(title, body, eyebrow = 'AJ MOTORS') {
  $('#dialog-eyebrow').textContent = eyebrow;
  $('#modal-body').innerHTML = `<h2>${esc(title)}</h2>${body}`;
  $('#modal-error').hidden = true;
  if (!$('#modal').open) $('#modal').showModal();
}
function modalError(message) {
  $('#modal-error').textContent = message;
  $('#modal-error').hidden = false;
}
$('#close-modal').onclick = () => $('#modal').close();
$('#modal').addEventListener('click', (e) => {
  if (e.target === $('#modal')) {
    const r = e.target.getBoundingClientRect();
    if (e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom)
      e.target.close();
  }
});
async function submitForm(form, fn) {
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const button = $('button[type="submit"]', form);
    button.disabled = true;
    $('#modal-error').hidden = true;
    try {
      await fn(Object.fromEntries(new FormData(form)));
    } catch (error) {
      modalError(error.message);
    } finally {
      button.disabled = false;
    }
  });
}
function field(label, name, type = 'text', value = '', required = true) {
  return `<label>${esc(label)}<input name="${name}" type="${type}" value="${esc(value)}" ${required ? 'required' : ''} ${type === 'number' ? 'min="0" step="0.01"' : 'maxlength="255"'}></label>`;
}
function actions(label) {
  return `<div class="form-actions full"><button type="submit" class="primary">${esc(label)} ↗</button></div>`;
}

async function checkStatus() {
  if (state.checking) return;
  state.checking = true;
  try {
    const result = await request('/status');
    state.services = result.services;
    const down = result.services.filter((s) => s.status !== 'UP');
    $('#system-label').textContent = down.length
      ? `${down.length} tjeneste${down.length === 1 ? '' : 'r'} utilgængelig${down.length === 1 ? '' : 'e'}`
      : 'Alle systemer klar';
    $('#system-button .dot').className = 'dot' + (down.length ? ' down' : '');
    $('#outage').hidden = !down.length;
    $('#outage-title').textContent = 'En del af systemet holder en pause';
    $('#outage-message').textContent =
      `Vi kan ikke få forbindelse til: ${down.map((s) => serviceNames[s.service] || s.service).join(', ')}. Du kan stadig bruge de tilgængelige dele.`;
  } catch (e) {
    state.services = [];
    $('#system-label').textContent = 'Forbindelse afbrudt';
    $('#system-button .dot').className = 'dot down';
    $('#outage').hidden = false;
    $('#outage-title').textContent = 'Vi kan ikke få forbindelse lige nu';
    $('#outage-message').textContent =
      'Siden er klar, men forbindelsen til API Gateway er afbrudt. Start tjenesterne, eller prøv igen om lidt.';
  } finally {
    state.checking = false;
  }
}
$('#system-button').onclick = () =>
  showModal(
    'Systemstatus',
    `<p>Status opdateres automatisk hvert 10. sekund.</p><ul class="system-list"><li><span class="dot"></span>Frontend<small>Kører</small></li>${state.services.length ? state.services.map((s) => `<li><span class="dot ${s.status === 'UP' ? '' : 'down'}"></span>${esc(serviceNames[s.service])}<small>${s.status === 'UP' ? 'Klar' : 'Utilgængelig'}</small></li>`).join('') : '<li><span class="dot down"></span>API Gateway<small>Ingen forbindelse</small></li>'}</ul>`,
    'FORBINDELSE OG DRIFT',
  );
$('#retry').onclick = async () => {
  await checkStatus();
  await loadPage();
};

function renderCars() {
  const search = $('#search').value.toLowerCase().trim(),
    status = $('#status-filter').value;
  const cars = state.products.filter(
    (p) =>
      (!status || p.status === status) && `${p.brand} ${p.model}`.toLowerCase().includes(search),
  );
  $('#car-count').textContent = cars.length;
  if (!cars.length) {
    $('#cars').innerHTML = state.products.length
      ? empty(
          'Ingen biler matcher din søgning',
          'Prøv et andet mærke, en anden model eller en anden status.',
        )
      : empty(
          'Plads til din næste bil',
          'Kataloget er tomt. Opret din første bil, så er du i gang.',
          '<button id="empty-add" class="secondary">＋ Opret den første bil</button>',
        );
    if ($('#empty-add')) $('#empty-add').onclick = () => productForm();
    return;
  }
  const colors = ['#94a99b', '#a8b5c3', '#b9ac97', '#82929c'];
  $('#cars').innerHTML = cars
    .map(
      (p, i) =>
        `<article class="car-card"><div class="car-visual" style="background:${['#e5ece4', '#e8edf0', '#eeeae1', '#e4e9eb'][i % 4]}">${badge(p.status)}${carSvg(colors[i % 4], 'card' + i)}</div><div class="car-copy"><span class="car-kicker">${esc(p.brand)} · ${esc(p.year)}</span><h3>${esc(p.model)}</h3><div class="specs"><span>${num(p.mileage)} km</span><span>·</span><span>${esc(p.fuelType || 'Ikke angivet')}</span><span>·</span><span>${esc(p.transmission || '—')}</span></div><div class="car-bottom"><span class="price">${esc(money(p.price, p.currency))}</span><button class="detail-button" data-detail="${esc(p.guid)}" aria-label="Se ${esc(p.brand)} ${esc(p.model)}">↗</button></div></div></article>`,
    )
    .join('');
  document.querySelectorAll('.car-card').forEach((card, i) => {
    const url = cars[i].images?.[0];
    if (!url) return;
    try {
      if (!['http:', 'https:'].includes(new URL(url).protocol)) return;
    } catch {
      return;
    }
    const img = document.createElement('img');
    img.alt = cars[i].brand + ' ' + cars[i].model;
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    img.style.cssText = 'width:100%;height:200px;object-fit:cover';
    img.onload = () => {
      card.querySelector('.car-svg').replaceWith(img);
    };
    img.src = url;
  });
  document
    .querySelectorAll('[data-detail]')
    .forEach((b) => (b.onclick = () => carDetail(b.dataset.detail)));
}
const PRODUCT_FIELDS =
  'guid brand model year description price currency status mileage color fuelType transmission equipment images engine{type powerHp capacityLiters} performance{topSpeedKmh zeroTo100} dimensions{length width height}';
const CATALOGUE_QUERY =
  'query($filter:ProductFilter,$offset:Int,$limit:Int){products(filter:$filter,offset:$offset,limit:$limit){total hasMore items{' +
  PRODUCT_FIELDS +
  '}}}';
let catalogueOffset = 0,
  searchTimer;
function catalogueFilters() {
  const f = { search: $('#search').value, status: $('#status-filter').value || null };
  for (const key of ['minPrice', 'maxPrice', 'minYear', 'maxYear', 'maxMileage']) {
    const value = $('#filter-' + key).value;
    if (value !== '') f[key] = Number(value);
  }
  return f;
}
function scheduleSearch() {
  clearTimeout(searchTimer);
  catalogueOffset = 0;
  searchTimer = setTimeout(loadCars, 250);
}
async function loadCars() {
  if (state.loading.has('catalog')) return;
  state.loading.add('catalog');
  try {
    const data = await request('/graphql', {
      method: 'POST',
      body: JSON.stringify({
        query: CATALOGUE_QUERY,
        variables: { filter: catalogueFilters(), offset: catalogueOffset, limit: 24 },
      }),
    });
    if (data.errors)
      throw new Error('Kataloget kunne ikke hentes. Kontrollér filtre og prøv igen.');
    state.products = data.data.products.items;
    renderCars();
    $('#car-count').textContent = data.data.products.total;
    $('#previous-cars').disabled = catalogueOffset === 0;
    $('#next-cars').disabled = !data.data.products.hasMore;
  } catch (e) {
    $('#cars').innerHTML = empty(
      'Bilkataloget er midlertidigt utilgængeligt',
      e.message,
      '<button id="catalog-retry" class="secondary">Prøv igen ↻</button>',
    );
    $('#catalog-retry').onclick = loadCars;
    $('#car-count').textContent = '—';
  } finally {
    state.loading.delete('catalog');
  }
}
$('#search').oninput = scheduleSearch;
$('#status-filter').onchange = scheduleSearch;
$('#refresh-cars').onclick = loadCars;
$('#add-product').onclick = () => productForm();

async function carDetail(guid) {
  try {
    const result = await request('/graphql', {
      method: 'POST',
      body: JSON.stringify({
        query: 'query($id:ID!){product(guid:$id){' + PRODUCT_FIELDS + '}}',
        variables: { id: guid },
      }),
    });
    if (result.errors || !result.data.product) throw new Error('Bilen kunne ikke hentes.');
    const p = result.data.product;
    showModal(
      `${p.brand} ${p.model}`,
      `${carSvg('#aab9aa', 'detail')}${badge(p.status)}<div class="details-grid">${[
        ['Pris', money(p.price, p.currency)],
        ['Årgang', p.year],
        ['Kilometer', num(p.mileage) + ' km'],
        ['Drivmiddel', p.fuelType || '—'],
        ['Gearkasse', p.transmission || '—'],
        ['Farve', p.color || '—'],
        ['Motor', (p.engine?.powerHp || 0) + ' hk'],
        ['Udstyr', p.equipment?.join(', ') || '—'],
      ]
        .map(([k, v]) => `<div class="detail-spec"><small>${esc(k)}</small>${esc(v)}</div>`)
        .join(
          '',
        )}</div><p class="details-description">${esc(p.description)}</p><div class="form-actions"><button id="edit-car" class="secondary" ${['RESERVED', 'SOLD'].includes(p.status) ? 'disabled' : ''}>Redigér</button><button id="delete-car" class="secondary danger" ${['RESERVED', 'SOLD'].includes(p.status) ? 'disabled' : ''}>Slet</button><button id="reserve-car" class="primary" ${p.status !== 'AVAILABLE' ? 'disabled' : ''}>Reservér bilen ↗</button></div>`,
      `${p.brand.toUpperCase()} · ${p.year}`,
    );
    $('#edit-car').hidden = !isAdmin();
    $('#delete-car').hidden = !isAdmin();
    $('#edit-car').onclick = () => productForm(p);
    $('#delete-car').onclick = () =>
      confirmAction(
        'Slet bilen?',
        'Bilen fjernes fra kataloget. En slettepost bevares i databasen.',
        async () => {
          await send('/products/' + guid, 'DELETE');
          $('#modal').close();
          toast('Bilen er slettet. Kataloget opdateres om et øjeblik.');
          await loadCars();
        },
      );
    $('#reserve-car').onclick = () => reserveForm(p);
  } catch (e) {
    toast(e.message);
  }
}
function productForm(p = {}) {
  showModal(
    p.guid ? 'Redigér bil' : 'Opret en bil',
    `<form id="product-form">${field('Mærke', 'brand', 'text', p.brand)}${field('Model', 'model', 'text', p.model)}${field('Årgang', 'year', 'number', p.year || 2026)}${field('Pris', 'price', 'number', p.price ?? '')}${field('Valuta', 'currency', 'text', p.currency || 'DKK')}${field('Kilometer', 'mileage', 'number', p.mileage || 0)}${field('Farve', 'color', 'text', p.color || '', false)}${field('Drivmiddel', 'fuelType', 'text', p.fuelType || '', false)}${field('Gearkasse', 'transmission', 'text', p.transmission || '', false)}<label>Status<select name="status"><option value="AVAILABLE" ${p.status !== 'UNAVAILABLE' ? 'selected' : ''}>Ledig</option><option value="UNAVAILABLE" ${p.status === 'UNAVAILABLE' ? 'selected' : ''}>Ikke tilgængelig</option></select></label><label class="full">Beskrivelse<textarea name="description" rows="3">${esc(p.description || '')}</textarea></label><label class="full">Billed-URL<input name="imageUrl" type="url" value="${esc(p.images?.[0] || '')}"></label><button type="button" id="draft-description" class="secondary full">Lav AI-udkast til beskrivelse</button>${actions('Gem bil')}</form>`,
  );
  $('#draft-description').onclick = async () => {
    const b = $('#draft-description');
    b.disabled = true;
    try {
      const fields = Object.fromEntries(new FormData($('#product-form')));
      const result = await send('/products/description-draft', 'POST', {
        brand: fields.brand,
        model: fields.model,
        year: Number(fields.year),
      });
      $('[name="description"]').value = result.description;
      toast('AI-udkast indsat. Gennemlæs teksten, før du gemmer.');
    } catch (error) {
      modalError(error.message);
    } finally {
      b.disabled = false;
    }
  };
  for (const key of ['year', 'mileage']) $(`[name="${key}"]`).step = '1';
  submitForm($('#product-form'), async (d) => {
    await send('/products' + (p.guid ? '/' + p.guid : ''), p.guid ? 'PUT' : 'POST', {
      ...p,
      ...d,
      images: d.imageUrl ? [d.imageUrl] : [],
      year: Number(d.year),
      price: Number(d.price),
      mileage: Number(d.mileage),
    });
    $('#modal').close();
    toast('Bilen er gemt. Kataloget opdateres om et øjeblik.');
    setTimeout(loadCars, 1200);
  });
}
async function reserveForm(car) {
  try {
    if (!window.ajAuth?.session) throw new Error('Log ind for at fortsætte.');
    state.users = await request('/users');
    if (!state.users.length) {
      customerForm(null, () => reserveForm(car));
      return;
    }
    showModal(
      'Reservér ' + car.brand + ' ' + car.model,
      `<p>Pris: <strong>${esc(money(car.price, car.currency))}</strong>. Bilen reserveres, når lagerkontrollen er godkendt.</p><form id="reserve-form"><label class="full">Kunde<select name="userGuid" required>${state.users.map((u) => `<option value="${esc(u.guid)}">${esc(u.firstName + ' ' + u.lastName + ' · ' + u.email)}</option>`).join('')}</select></label>${field('Leveringsadresse', 'shippingAddress', 'text', '')}<label>Bemærkning<input name="notes" maxlength="255"></label>${actions('Opret reservation')}</form>`,
    );
    submitForm($('#reserve-form'), async (d) => {
      await send('/orders', 'POST', { ...d, productGuid: car.guid });
      $('#modal').close();
      toast('Ordren er oprettet. Vi kontrollerer reservationen.');
      await navigate('orders');
    });
  } catch (e) {
    toast(e.message);
  }
}
function confirmAction(title, text, action) {
  showModal(title, `<p>${esc(text)}</p><form id="confirm-form">${actions('Bekræft')}</form>`);
  submitForm($('#confirm-form'), action);
}

function customerForm(user, after) {
  const u = user || {};
  showModal(
    u.guid ? 'Redigér kunde' : 'Opret en kunde',
    `<form id="customer-form">${field('Fornavn', 'firstName', 'text', u.firstName)}${field('Efternavn', 'lastName', 'text', u.lastName)}${field('E-mail', 'email', 'email', u.email)}${field('Telefon', 'phoneNumber', 'tel', u.phoneNumber, false)}${field('Adresse', 'address', 'text', u.address, false)}${field('Postnummer', 'postalCode', 'text', u.postalCode, false)}${field('By', 'city', 'text', u.city, false)}${actions('Gem kunde')}</form>`,
  );
  submitForm($('#customer-form'), async (d) => {
    await send('/users' + (u.guid ? '/' + u.guid : ''), u.guid ? 'PUT' : 'POST', d);
    $('#modal').close();
    toast('Kunden er gemt.');
    if (after) await after();
    else await loadCustomers();
  });
}
$('#add-customer').onclick = () => customerForm();
function table(headers, rows) {
  return `<table><thead><tr>${headers.map((h) => `<th scope="col">${esc(h)}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table>`;
}
async function loadCustomers() {
  try {
    if (!window.ajAuth?.session) throw new Error('Log ind for at fortsætte.');
    state.users = await request('/users');
    $('#customer-list').innerHTML = state.users.length
      ? table(
          ['Kunde', 'Kontakt', 'Adresse', 'Handlinger'],
          state.users.map(
            (u) =>
              `<tr><td>${esc(u.firstName + ' ' + u.lastName)}<small>${short(u.guid)}</small></td><td>${esc(u.email)}<small>${esc(u.phoneNumber)}</small></td><td>${esc(u.address)}<small>${esc(u.postalCode + ' ' + u.city)}</small></td><td><button class="secondary" data-edit-user="${esc(u.guid)}">Redigér</button><button ${isAdmin() ? '' : 'hidden'} class="secondary danger" data-delete-user="${esc(u.guid)}">Slet</button></td></tr>`,
          ),
        )
      : empty('Ingen kunder endnu', 'Opret en kunde, før du reserverer en bil.');
    document
      .querySelectorAll('[data-edit-user]')
      .forEach(
        (b) =>
          (b.onclick = () => customerForm(state.users.find((u) => u.guid === b.dataset.editUser))),
      );
    document.querySelectorAll('[data-delete-user]').forEach(
      (b) =>
        (b.onclick = () =>
          confirmAction(
            'Slet kunden?',
            'Kunden skjules, og en slettepost bevares. Eksisterende ordrer bevares.',
            async () => {
              await send('/users/' + b.dataset.deleteUser, 'DELETE');
              $('#modal').close();
              await loadCustomers();
              toast('Kunden er slettet.');
            },
          )),
    );
  } catch (e) {
    $('#customer-list').innerHTML = empty('Kunder er midlertidigt utilgængelige', e.message);
  }
}
async function loadOrders() {
  try {
    state.orders = await request('/orders');
    $('#order-list').innerHTML = state.orders.length
      ? table(
          ['Ordre', 'Bil / kunde', 'Oprettet', 'Beløb', 'Status', 'Handlinger'],
          state.orders.map(
            (o) =>
              `<tr><td>#${short(o.guid)}</td><td>${short(o.productGuid)}<small>Kunde: ${short(o.userGuid)}</small></td><td>${date(o.createdAt)}</td><td>${esc(money(o.totalAmount, o.currency))}</td><td>${badge(o.status)}</td><td>${o.status === 'COMPLETED' ? `<button class="secondary" data-confirmation="${esc(o.guid)}">Hent bekræftelse</button>` : ''}${o.status === 'RESERVED' && isAdmin() ? `<button class="secondary" data-pay-order="${esc(o.guid)}">Opret betaling ↗</button>` : ''}${['PENDING', 'RESERVING', 'RESERVED'].includes(o.status) ? `<button class="secondary danger" data-cancel-order="${esc(o.guid)}">Annullér</button>` : ''}</td></tr>`,
          ),
        )
      : empty('Ingen ordrer endnu', 'Find din favorit i bilkataloget, og opret en reservation.');
    document.querySelectorAll('[data-confirmation]').forEach(
      (b) =>
        (b.onclick = async () => {
          try {
            const response = await fetch(
              '/api/v1/orders/' + b.dataset.confirmation + '/confirmation',
              { headers: await window.ajAuth.headers() },
            );
            if (!response.ok) throw new Error((await response.json()).message);
            const url = URL.createObjectURL(await response.blob());
            const a = document.createElement('a');
            a.href = url;
            a.download = 'order-' + b.dataset.confirmation + '.pdf';
            a.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
          } catch (error) {
            toast(error.message);
          }
        }),
    );
    document
      .querySelectorAll('[data-pay-order]')
      .forEach((b) => (b.onclick = () => paymentForm(b.dataset.payOrder)));
    document.querySelectorAll('[data-cancel-order]').forEach(
      (b) =>
        (b.onclick = () =>
          confirmAction(
            'Annullér ordren?',
            'Reservationen frigives, så bilen igen kan købes.',
            async () => {
              await send('/orders/' + b.dataset.cancelOrder, 'PUT', { status: 'CANCELLED' });
              $('#modal').close();
              await loadOrders();
              toast('Ordren er annulleret.');
            },
          )),
    );
  } catch (e) {
    $('#order-list').innerHTML = empty('Ordrer er midlertidigt utilgængelige', e.message);
  }
}
function paymentForm(orderGuid) {
  showModal(
    'Opret manuel betaling',
    `<p>Betalingen registreres som afventende. Du angiver derefter, om betalingen er modtaget eller mislykket.</p><form id="payment-form"><label class="full">Betalingsmetode<select name="paymentMethod"><option value="MANUAL">Manuel registrering</option><option value="BANK_TRANSFER">Bankoverførsel</option><option value="CASH">Kontant</option></select></label>${actions('Opret betaling')}</form>`,
  );
  submitForm($('#payment-form'), async (d) => {
    await send('/payments', 'POST', { ...d, orderGuid });
    $('#modal').close();
    toast('Betalingsanmodningen er oprettet. Vi afventer ordrebekræftelse.');
    await navigate('payments');
  });
}
async function loadPayments() {
  try {
    state.payments = await request('/payments');
    $('#payment-list').innerHTML = state.payments.length
      ? table(
          ['Reference', 'Ordre', 'Beløb', 'Metode', 'Status', 'Handlinger'],
          state.payments.map(
            (p) =>
              `<tr><td>#${short(p.guid)}<small>${date(p.createdAt)}</small></td><td>#${short(p.orderGuid)}</td><td>${esc(money(p.amount, p.currency))}</td><td>${esc({ MANUAL: 'Manuel', CASH: 'Kontant', BANK_TRANSFER: 'Bankoverførsel' }[p.paymentMethod] || p.paymentMethod)}</td><td>${badge(p.status)}</td><td>${p.status === 'PENDING' && isAdmin() ? `<button class="secondary" data-payment="${esc(p.guid)}" data-status="COMPLETED">Markér modtaget</button><button class="secondary danger" data-payment="${esc(p.guid)}" data-status="FAILED">Markér mislykket</button>` : esc(date(p.paidAt))}</td></tr>`,
          ),
        )
      : empty('Ingen betalinger endnu', 'Opret en betaling fra en reserveret ordre.');
    document.querySelectorAll('[data-payment]').forEach(
      (b) =>
        (b.onclick = () =>
          confirmAction(
            b.dataset.status === 'COMPLETED'
              ? 'Er betalingen modtaget?'
              : 'Er betalingen mislykket?',
            b.dataset.status === 'COMPLETED'
              ? 'Ordren afsluttes, og bilen markeres som solgt. Kontrollér, at beløbet er modtaget.'
              : 'Ordren markeres som mislykket, og bilen frigives igen.',
            async () => {
              await send('/payments/' + b.dataset.payment, 'PUT', { status: b.dataset.status });
              $('#modal').close();
              await loadPayments();
              toast('Betalingsstatus er registreret.');
            },
          )),
    );
  } catch (e) {
    $('#payment-list').innerHTML = empty('Betalinger er midlertidigt utilgængelige', e.message);
  }
}
async function loadPage() {
  return {
    catalog: loadCars,
    customers: loadCustomers,
    orders: loadOrders,
    payments: loadPayments,
  }[state.page]();
}
async function navigate(page) {
  state.page = page;
  document.querySelectorAll('.page').forEach((p) => (p.hidden = p.id !== page));
  document
    .querySelectorAll('[data-page]')
    .forEach((b) => b.classList.toggle('active', b.dataset.page === page));
  await loadPage();
}
document
  .querySelectorAll('[data-page]')
  .forEach((b) => (b.onclick = () => navigate(b.dataset.page)));
document.querySelectorAll('[data-refresh]').forEach((b) => (b.onclick = loadPage));
$('#previous-cars').onclick = () => {
  catalogueOffset = Math.max(0, catalogueOffset - 24);
  loadCars();
};
$('#next-cars').onclick = () => {
  catalogueOffset += 24;
  loadCars();
};
for (const key of ['minPrice', 'maxPrice', 'minYear', 'maxYear', 'maxMileage'])
  $('#filter-' + key).onchange = scheduleSearch;
checkStatus();
loadCars();
setInterval(checkStatus, 10000);
setInterval(() => {
  if (!document.hidden && !$('#modal').open) loadPage();
}, 5000);

function reflectIdentity() {
  $('#add-product').hidden = !isAdmin();
  $('#add-customer').hidden = !isAdmin();
  document.querySelector('[data-page="customers"]').textContent = isAdmin()
    ? 'Kunder'
    : 'Min profil';
  for (const page of ['customers', 'orders', 'payments'])
    document.querySelector('[data-page="' + page + '"]').hidden = !window.ajAuth?.session;
  if (state.page !== 'catalog') loadPage();
}
document.addEventListener('identity-ready', reflectIdentity);
reflectIdentity();
