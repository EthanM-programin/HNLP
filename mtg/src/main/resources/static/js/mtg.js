// Shared helpers for every page: API calls, escaping, flag stickers, shelf tags, toasts.
const MTG = (() => {
  async function api(path, opts = {}) {
    const init = { method: opts.method || 'GET', headers: { 'Accept': 'application/json' }, credentials: 'same-origin' };
    if (opts.body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(opts.body); }
    let res;
    try { res = await fetch(path, init); }
    catch { throw Object.assign(new Error('No connection to the warehouse system. Check your signal and try again.'), { code: 'OFFLINE' }); }
    if (res.redirected && res.url.includes('/login')) { location.href = '/login'; throw new Error('Please sign in.'); }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = null; }
    if (!res.ok) {
      const err = new Error((data && data.message) || `Request failed (${res.status})`);
      err.code = data && data.error; err.status = res.status; err.details = data && data.details;
      throw err;
    }
    return data;
  }

  const ALLERGEN = { TREE_NUT: 'tree nuts', PEANUT: 'peanuts', MILK: 'dairy', EGG: 'eggs', WHEAT: 'wheat', SOY: 'soy', FISH: 'fish', SHELLFISH: 'shellfish', SESAME: 'sesame' };
  // escapes HTML and shows allergen tags (TREE_NUT) as plain words (tree nuts)
  const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))
    .replace(/\b(TREE_NUT|PEANUT|SHELLFISH|SESAME|MILK|EGG|WHEAT|SOY|FISH)\b/g, t => ALLERGEN[t]);

  const FLAG = {
    GREEN:  { ico: '✓', word: 'Good pick' },
    YELLOW: { ico: '!', word: 'Limit' },
    RED:    { ico: '✕', word: 'Put it back' },
  };
  function sticker(flag, { mini = false, pop = false, cap = null } = {}) {
    const f = FLAG[flag] || FLAG.YELLOW;
    const word = flag === 'YELLOW' && cap ? `Limit ${cap}` : f.word;
    return `<div class="sticker ${flag}${mini ? ' mini' : ''}${pop ? ' pop' : ''}" role="img" aria-label="${esc(word)}">
      <div><div class="ico" aria-hidden="true">${f.ico}</div><div class="word">${esc(word)}</div></div></div>`;
  }

  function tag(loc, { big = false } = {}) {
    if (!loc) return '';
    if (!loc.floor) {
      return `<span class="tag stock${big ? ' big' : ''}" title="Back stockroom"><span><small>stockroom</small><b>${esc(loc.shelf)}</b></span><span><small>bin</small><b>${esc(loc.bin)}</b></span></span>`;
    }
    return `<span class="tag${big ? ' big' : ''}" title="${esc(loc.zone)}"><span><small>aisle</small><b>${esc(loc.aisle)}</b></span><span><small>shelf</small><b>${esc(loc.shelf)}</b></span><span><small>bin</small><b>${esc(loc.bin)}</b></span></span>`;
  }

  let toastTimer;
  function toast(msg) {
    let el = document.querySelector('.toast');
    if (!el) { el = document.createElement('div'); el.className = 'toast'; el.setAttribute('role', 'status'); document.body.appendChild(el); }
    el.textContent = msg; el.classList.remove('hidden');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.add('hidden'), 3200);
  }

  const lb = n => (n == null ? '–' : `${Number(n).toFixed(Number(n) % 1 ? 1 : 0)} lb`);
  const prettyCategory = c => ({ 'Canned Goods': 'Canned goods', 'Pantry': 'Pantry & grains' }[c] || c);

  async function capacity(el) {
    try {
      const c = await api('/api/capacity');
      el.textContent = c.full ? `Floor is full (${c.activeShoppers} of ${c.maxCapacity}), ${c.waiting} waiting`
                              : `${c.activeShoppers} of ${c.maxCapacity} shoppers on the floor`;
      return c;
    } catch { el.textContent = ''; }
  }

  // POST /logout directly (skips Spring's "Are you sure?" page)
  async function signOut() {
    try { await fetch('/logout', { method: 'POST', credentials: 'same-origin' }); } catch {}
  }
  document.addEventListener('click', async e => {
    const a = e.target.closest('a[data-signout]');
    if (!a) return;
    e.preventDefault();
    await signOut();
    location.href = '/';
  });

  return { api, esc, sticker, tag, toast, lb, prettyCategory, capacity, signOut };
})();
