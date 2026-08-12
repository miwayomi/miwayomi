const $ = (sel) => document.querySelector(sel);
const api = "/api/v1";

let state = {
  sources: { manga: [], anime: [] },
  activeSource: null,
  detail: null,
  typeFilter: "all",
  currentView: "home",
  catalog: null,
  episodes: [],
  currentEncUrl: null,
  currentEpName: "",
  currentEpNumber: null,
};

// In-app navigation stack (SPA has no real browser history between views)
let navStack = [];
function navPush(restore) { navStack.push(restore); }
function goBack() {
  if (navStack.length) {
    const restore = navStack.pop();
    if (typeof restore === "function") restore();
  } else if (window.history.length > 1) {
    window.history.back();
  }
}

const DEFAULT_LANG = "en";
const AVAILABLE_LANGS = ["en", "es"];
let I18N = {};

function t(key, ...args) {
  let s = (I18N && I18N[key]) || key;
  if (args.length) args.forEach((a, i) => { s = s.split(`{${i}}`).join(String(a)); });
  return s;
}

function currentLang() {
  return localStorage.getItem("miwayomi.lang") || DEFAULT_LANG;
}

async function loadI18n(lang) {
  try {
    const res = await fetch(`/lang/${lang}.json`);
    if (res.ok) I18N = await res.json();
    else I18N = {};
  } catch (e) { I18N = {}; }
  document.documentElement.lang = lang;
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    const key = el.getAttribute("data-i18n");
    if (key && I18N[key] !== undefined) el.textContent = I18N[key];
  });
  document.querySelectorAll("[data-i18n-html]").forEach((el) => {
    const key = el.getAttribute("data-i18n-html");
    if (key && I18N[key] !== undefined) el.innerHTML = I18N[key];
  });
}

function setLang(lang) {
  localStorage.setItem("miwayomi.lang", lang);
  location.reload();
}

async function getJSON(url) {
  const res = await fetch(url);
  if (res.ok) return res.json();
  let body = null;
  try { body = await res.json(); } catch (e) { }
  if (body && body.challengeUrl) {
    throw new Error(t("cf.challengeError"));
  }
  throw new Error(`HTTP ${res.status}`);
}

function escapeHtml(str) {
  return String(str ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function setNav(name) {
  document.querySelectorAll(".nav-link").forEach((n) => n.classList.toggle("active", n.dataset.nav === name));
}

const THUMB_CACHE_KEY = "miwayomi.thumbs";
function getThumbCache() {
  try { return JSON.parse(localStorage.getItem(THUMB_CACHE_KEY) || "{}"); } catch (e) { return {}; }
}
function setThumbCache(c) {
  const keys = Object.keys(c);
  if (keys.length > 800) {
    for (const k of keys.slice(0, keys.length - 400)) delete c[k];
  }
  try { localStorage.setItem(THUMB_CACHE_KEY, JSON.stringify(c)); } catch (e) { }
}
function proxyImg(u) {
  return `/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(u)}`;
}
const THUMB_MAX_CONCURRENT = 3;
let thumbQueue = [];
let thumbActive = 0;
function enqueueThumb(img) { thumbQueue.push(img); pumpThumbs(); }
function pumpThumbs() {
  while (thumbActive < THUMB_MAX_CONCURRENT && thumbQueue.length) {
    const img = thumbQueue.shift();
    thumbActive++;
    loadChapterThumb(img).catch(() => { }).finally(() => { thumbActive--; pumpThumbs(); });
  }
}
let thumbObserver = null;
if ("IntersectionObserver" in window) {
  thumbObserver = new IntersectionObserver((entries) => {
    for (const en of entries) {
      if (en.isIntersecting) { thumbObserver.unobserve(en.target); enqueueThumb(en.target); }
    }
  }, { rootMargin: "400px" });
}
async function loadChapterThumb(img) {
  if (!img) return;
  const enc = img.dataset.ch;
  if (!enc) return;
  const ckey = `${state.activeSource.id}:${enc}`;
  const cache = getThumbCache();
  if (cache[ckey]) { img.src = proxyImg(cache[ckey]); img.classList.add("ready"); return; }
  try {
    const d = await getJSON(`${api}/manga/${state.activeSource.id}/pages?url=${enc}`);
    const p = d.pages && d.pages[0];
    if (p && p.imageUrl) {
      cache[ckey] = p.imageUrl;
      setThumbCache(cache);
      img.src = proxyImg(p.imageUrl);
      img.classList.add("ready");
    }
  } catch (e) { }
}

/* ---------------- Sidebar ---------------- */

async function loadSources() {
  const data = await getJSON(`${api}/sources`);
  state.sources = data;
  renderSources();
}

function setTypeFilter(type) {
  state.typeFilter = type;
  document.querySelectorAll(".type-tab").forEach((b) => b.classList.toggle("active", b.dataset.type === type));
  renderSources();
}

function renderSources() {
  const list = $("#srcList");
  if (!list) return;
  list.innerHTML = "";
  const q = ($("#srcFilter")?.value || "").trim().toLowerCase();

  const favBtn = document.createElement("div");
  favBtn.className = "source-item";
  favBtn.innerHTML = `<span class="badge fav">★</span> ${escapeHtml(t("nav.favorites"))}`;
  favBtn.onclick = () => showFavorites();
  list.appendChild(favBtn);

  const groups = [
    ["manga", t("nav.manga"), state.sources.manga],
    ["anime", t("nav.anime"), state.sources.anime],
  ];
  for (const [type, label, srcs] of groups) {
    if (state.typeFilter !== "all" && state.typeFilter !== type) continue;
    if (!srcs || srcs.length === 0) continue;
    let shown = srcs;
    if (q) shown = srcs.filter((s) => (s.name + " " + (s.pkg || "")).toLowerCase().includes(q));
    if (shown.length === 0) continue;

    const title = document.createElement("div");
    title.className = "group-title";
    title.textContent = label;
    list.appendChild(title);

    const byPkg = new Map();
    for (const s of shown) {
      const k = s.pkg || "";
      if (!byPkg.has(k)) byPkg.set(k, []);
      byPkg.get(k).push(s);
    }
    for (const [pkg, sarr] of byPkg) {
      if (!pkg) {
        for (const s of sarr) appendSourceFlat(list, type, s, "");
        continue;
      }
      const uniq = new Map();
      for (const s of sarr) {
        const n = s.name;
        if (!uniq.has(n)) uniq.set(n, []);
        uniq.get(n).push(s);
      }
      if (uniq.size === 1) {
        const s = sarr[0];
        const extra = sarr.length > 1 ? ` (${sarr.length})` : "";
        appendSourceFlat(list, type, s, extra);
        continue;
      }
      const g = document.createElement("div");
      g.className = "source-group";
      g.innerHTML = `<span class="badge ${type}">${type}</span> <span class="sg-name">${escapeHtml(extGroupLabel(pkg, uniq))}</span> <span class="sg-count">${sarr.length}</span> <span class="sg-chevron">▸</span>`;
      const sub = document.createElement("div");
      sub.className = "source-group-sub";
      sub.style.display = "none";
      let containsActive = false;
      for (const [name, arr] of uniq) {
        const row = document.createElement("div");
        const isActive = arr.some((x) => x.id === state.activeSource?.id);
        if (isActive) containsActive = true;
        row.className = "source-item sub" + (isActive ? " active" : "");
        row.innerHTML = `${escapeHtml(name)}${arr.length > 1 ? ` <span class="sg-count">${arr.length}</span>` : ""}`;
        row.onclick = (e) => { e.stopPropagation(); openSource(arr[0]); };
        sub.appendChild(row);
      }
      if (containsActive) {
        sub.style.display = "block";
        g.classList.add("open");
        g.querySelector(".sg-chevron").textContent = "▾";
      }
      g.onclick = () => {
        const open = sub.style.display !== "none";
        sub.style.display = open ? "none" : "block";
        g.classList.toggle("open", !open);
        g.querySelector(".sg-chevron").textContent = open ? "▸" : "▾";
      };
      list.appendChild(g);
      list.appendChild(sub);
    }
  }
}

function appendSourceFlat(list, type, s, suffix) {
  const el = document.createElement("div");
  el.className = "source-item" + (state.activeSource?.id === s.id ? " active" : "");
  el.innerHTML = `<span class="badge ${type}">${type}</span> ${escapeHtml(s.name)}${escapeHtml(suffix)}`;
  el.onclick = () => openSource(s);
  list.appendChild(el);
}

function extGroupLabel(pkg, uniq) {
  const names = [...uniq.keys()];
  if (names.length >= 2) {
    let lcp = names[0];
    for (const n of names.slice(1)) {
      let i = 0;
      while (i < lcp.length && i < n.length && lcp[i] === n[i]) i++;
      lcp = lcp.slice(0, i);
    }
    lcp = lcp.trim();
    if (lcp.length >= 2) return lcp;
  }
  const short = (pkg.split(".").pop() || pkg).replace(/[_-]+/g, " ");
  return short.charAt(0).toUpperCase() + short.slice(1);
}

/* ---------------- Home ---------------- */

async function goHome() {
  setNav("home");
  renderHome();
}

async function goBrowse(type) {
  setNav(type);
  setTypeFilter(type);
  renderHome();
}

async function renderHome() {
  state.currentView = "home";
  const main = $("#content");
  const manga = state.sources.manga || [];
  const anime = state.sources.anime || [];
  const hero = `
    <div class="hero">
      <div class="hero-bg" style="background-image:radial-gradient(700px 380px at 20% 10%, #f4752133, transparent 60%)"></div>
      <div class="hero-body">
        <h1>miwayomi</h1>
        <p>${escapeHtml(t("empty.text"))}</p>
        <div style="display:flex;gap:10px">
          <button class="btn-primary" onclick="goBrowse('manga')">${escapeHtml(t("nav.manga"))}</button>
          <button class="btn-primary" onclick="goBrowse('anime')">${escapeHtml(t("nav.anime"))}</button>
        </div>
      </div>
    </div>`;
  let html = hero;
  const watch = await listWatch();
  if (watch.length) html += rowWrap(t("player.continueWatching"), watch.map((w) => watchCard(w)).join(""));
  if (manga.length && (state.typeFilter === "all" || state.typeFilter === "manga")) {
    html += rowWrap(t("nav.manga"), manga.map((s) => sourceCard(s)).join(""));
  }
  if (anime.length && (state.typeFilter === "all" || state.typeFilter === "anime")) {
    html += rowWrap(t("nav.anime"), anime.map((s) => sourceCard(s)).join(""));
  }
  try {
    const favs = await getJSON(`${api}/favorites`);
    if (favs && favs.length) {
      html += rowWrap(t("nav.favorites"), favs.map((f) => favCard(f)).join(""));
    }
  } catch (e) { }
  main.innerHTML = html;
}

function rowWrap(title, cards) {
  return `<div class="row"><div class="row-title">${escapeHtml(title)}</div><div class="row-scroll">${cards}</div></div>`;
}

function sourceCard(s) {
  const initials = (s.name || "?").charAt(0).toUpperCase();
  return `
    <div class="card" onclick="openSourceFromCard('${s.type}','${s.id}')">
      <div class="card-img-placeholder" style="background:linear-gradient(160deg, ${phColor(s.name)}, #1d1d26)">${escapeHtml(initials)}</div>
      <div class="card-title">${escapeHtml(s.name)}</div>
      <div class="card-sub">${escapeHtml((s.lang || "").toUpperCase())} · ${escapeHtml(s.type)}</div>
    </div>`;
}

function phColor(name) {
  const colors = ["#f47521", "#6fc3ff", "#9b7bf0", "#3ddc97", "#f7b733", "#e55d87", "#5b8def", "#ff9a9e"];
  let h = 0;
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
  return colors[h % colors.length];
}

function openSourceFromCard(type, id) {
  const list = type === "manga" ? state.sources.manga : state.sources.anime;
  const s = (list || []).find((x) => String(x.id) === String(id));
  if (s) openSource(s);
}

function favCard(f) {
  const thumb = f.thumbnailUrl
    ? `<img loading="lazy" src="/api/v1/proxy?sourceId=${f.sourceId}&url=${encodeURIComponent(f.thumbnailUrl)}">`
    : `<div class="card-img-placeholder" style="background:#262631">${escapeHtml((f.title || "?").charAt(0).toUpperCase())}</div>`;
  return `
    <div class="card" onclick="openFavorite('${f.sourceId}','${f.type}','${encodeURIComponent(f.url)}')">
      ${thumb}
      <div class="card-title">${escapeHtml(f.title)}</div>
      ${f.lastReadName ? `<div class="card-sub fav-progress">▶ ${escapeHtml(f.lastReadName)}</div>` : ""}
    </div>`;
}

/* ---------------- Browse (source) ---------------- */

function openSource(source) {
  navPush(() => { setNav("home"); renderHome(); });
  renderBrowseView(source);
}

function renderBrowseView(source) {
  state.currentView = "browse";
  state.activeSource = source;
  setNav(source.type);
  renderSources();
  const main = $("#content");
  main.innerHTML = `
    <div class="browse-head">
      <h2>${escapeHtml(source.name)}</h2>
      <input type="text" id="searchInput" placeholder="${escapeHtml(t("toolbar.searchPh"))}">
      <div class="tab-pills">
        <button class="tab-pill active" onclick="loadPopular()">${escapeHtml(t("toolbar.popular"))}</button>
        <button class="tab-pill" onclick="loadLatest()">${escapeHtml(t("toolbar.latest"))}</button>
        <button class="tab-pill" onclick="openSourcePrefs()">${escapeHtml(t("toolbar.config"))}</button>
      </div>
    </div>
    <div id="catalog"><div class="loading">${escapeHtml(t("common.loading"))}</div></div>`;
  $("#searchInput").addEventListener("keydown", (e) => { if (e.key === "Enter") doSearch(); });
  loadPopular();
}

async function loadPopular() { await loadGrid(`${api}/${state.activeSource.type}/${state.activeSource.id}/popular`, t("toolbar.popular")); }
async function loadLatest() { await loadGrid(`${api}/${state.activeSource.type}/${state.activeSource.id}/latest`, t("toolbar.latest")); }
async function doSearch() {
  const q = $("#searchInput")?.value.trim();
  if (!q) return loadPopular();
  await loadGrid(`${api}/${state.activeSource.type}/${state.activeSource.id}/search?query=${encodeURIComponent(q)}`, t("toolbar.search"));
}

function catalogCard(m) {
  const type = state.activeSource.type;
  return `
    <div class="card" onclick="openEntry('${state.activeSource.id}','${type}','${encodeURIComponent(m.url)}')">
      ${m.thumbnail_url ? `<img loading="lazy" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(m.thumbnail_url)}">` : `<div class="card-img-placeholder">${escapeHtml((m.title || "?").charAt(0).toUpperCase())}</div>`}
      <div class="card-title">${escapeHtml(m.title)}</div>
    </div>`;
}

async function loadGrid(url, title) {
  const box = $("#catalog");
  if (!box) return;
  state.catalog = { url, page: 1, hasNext: true, loading: false, error: false, errorMsg: "", seen: new Set() };
  box.innerHTML = `<div class="row-title">${escapeHtml(title)}</div>` +
    `<div class="row-scroll" id="catalogGrid"></div><div id="catSentinel" class="cat-sentinel"></div>`;
  await loadMore();
  if (state.catalog && !state.catalog.loading) {
    const grid = $("#catalogGrid");
    if (grid && grid.children.length === 0) {
      if (state.catalog.error) box.innerHTML = `<div class="error">${escapeHtml(t("common.error", state.catalog.errorMsg))}</div>`;
      else box.innerHTML = `<div class="empty">${escapeHtml(t("common.noResults"))}</div>`;
      state.catalog = null;
    }
  }
}

async function loadMore() {
  const c = state.catalog;
  const grid = $("#catalogGrid");
  const sentinel = $("#catSentinel");
  if (!c || !grid || c.loading || !c.url) return;
  c.loading = true;
  if (sentinel) sentinel.innerHTML = `<div class="loading">${escapeHtml(t("common.loading"))}</div>`;
  const sep = c.url.includes("?") ? "&" : "?";
  let ok = false;
  try {
    const data = await getJSON(`${c.url}${sep}page=${c.page}`);
    const list = data.mangas || data.animes || [];
    const newItems = list.filter((m) => {
      if (!m.url || c.seen.has(m.url)) return false;
      c.seen.add(m.url);
      return true;
    });
    if (newItems.length) grid.insertAdjacentHTML("beforeend", newItems.map(catalogCard).join(""));
    c.page++;
    c.hasNext = data.hasNextPage === true && list.length > 0 && newItems.length > 0;
    ok = true;
  } catch (e) {
    c.error = true;
    c.errorMsg = e.message;
    if (sentinel) sentinel.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
  c.loading = false;
  if (!ok) return;
  const content = $("#content");
  const atBottom = !!content && (content.scrollTop + content.clientHeight >= content.scrollHeight - 700);
  if (c.hasNext && atBottom) {
    loadMore();
  } else if (sentinel) {
    sentinel.innerHTML = c.hasNext
      ? ""
      : (grid.children.length ? `<div class="end-msg">${escapeHtml(t("toolbar.end"))}</div>` : "");
  }
}

function onContentScroll() {
  const c = state.catalog;
  if (!c || !c.url || c.loading || !c.hasNext || !$("#catalogGrid")) return;
  const content = $("#content");
  if (!content) return;
  if (content.scrollTop + content.clientHeight >= content.scrollHeight - 700) loadMore();
}

/* ---------------- Detail ---------------- */

async function openEntry(sourceId, type, url, backTo) {
  navPush(backTo || (() => { if (state.activeSource) renderBrowseView(state.activeSource); }));
  const main = $("#content");
  main.innerHTML = `<div class="loading">${escapeHtml(t("common.loading"))}</div>`;
  let d = null;
  try {
    d = await getJSON(`${api}/${type}/${sourceId}/details?url=${url}`);
  } catch (e) {
    const dec = decodeURIComponent(url);
    d = { url: dec, title: dec.split("/").pop() || t("detail.entry") };
  }
  renderDetail(type, d);
}

async function renderDetail(type, d) {
  state.currentView = "detail";
  const main = $("#content");
  state.detailObj = d;
  state.detail = { type, url: d.url || "", title: d.title || t("detail.entry"), thumb: d.thumbnail_url || "" };
  const thumb = d.thumbnail_url ? `src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(d.thumbnail_url)}"` : "";
  const bg = d.thumbnail_url ? `style="background-image:url('/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(d.thumbnail_url)}')"` : "";
  main.innerHTML = `
    <button class="btn-ghost" onclick="goBack()" style="margin-bottom:12px">← ${escapeHtml(t("reader.back"))}</button>
    <div class="detail-hero">
      <div class="hero-bg" ${bg}></div>
      <div class="detail-body">
        ${d.thumbnail_url ? `<img class="detail-cover" ${thumb}>` : ""}
        <div class="detail-info">
          <h1>${escapeHtml(d.title || t("detail.entry"))}</h1>
          <div class="meta">${d.author ? escapeHtml(t("detail.author", d.author)) + "<br>" : ""}${statusText(d.status)}</div>
          <button id="favBtn" class="fav-btn" data-fav="0" onclick="toggleFavorite(this)">${escapeHtml(t("fav.add"))}</button>
          <div class="desc">${escapeHtml(d.description || "")}</div>
        </div>
      </div>
    </div>
    <div id="entryList"><div class="loading">${escapeHtml(t("detail.loadingList"))}</div></div>`;

  try {
    const chk = await getJSON(`${api}/favorites/check?sourceId=${state.activeSource.id}&url=${encodeURIComponent(d.url || "")}`);
    const btn = $("#favBtn");
    if (btn && chk.ok) { btn.dataset.fav = "1"; btn.classList.add("on"); btn.textContent = t("fav.in"); }
  } catch (e) { }

  const url = encodeURIComponent(d.url);
  if (type === "manga") {
    const data = await getJSON(`${api}/manga/${state.activeSource.id}/chapters?url=${url}`);
    const items = (data.chapters || []).map((c, i) => `
      <div class="ep-card" onclick="openChapter('${encodeURIComponent(c.url)}','${escapeHtml(c.name)}')">
        <div class="ep-thumb">
          <span class="ep-num">#${i + 1}</span>
          <img class="ep-thumb-img" data-ch="${encodeURIComponent(c.url)}" alt="">
        </div>
        <div class="ep-name">${escapeHtml(c.name)}</div>
        ${c.scanlator ? `<div class="ep-meta">${escapeHtml(c.scanlator)}</div>` : ""}
      </div>`).join("");
    $("#entryList").innerHTML = `<div class="list-title">${escapeHtml(t("detail.chapters"))}</div>` +
      (items ? `<div class="ep-grid">${items}</div>` : `<div class="empty">${escapeHtml(t("detail.noChapters"))}</div>`);
    document.querySelectorAll("#entryList .ep-thumb-img").forEach((img) => {
      const enc = img.dataset.ch;
      const cache = getThumbCache();
      if (enc && cache[`${state.activeSource.id}:${enc}`]) {
        img.src = proxyImg(cache[`${state.activeSource.id}:${enc}`]);
        img.classList.add("ready");
      } else if (thumbObserver) {
        thumbObserver.observe(img);
      } else {
        enqueueThumb(img);
      }
    });
  } else {
    state.episodes = [];
    const data = await getJSON(`${api}/anime/${state.activeSource.id}/episodes?url=${url}`);
    state.episodes = data.episodes || [];
    renderAnimeEpisodes(d, state.episodes);
  }
}

async function toggleFavorite(btn) {
  const s = state.activeSource;
  const d = state.detail;
  if (!s || !d) return;
  const isFav = btn.dataset.fav === "1";
  try {
    if (isFav) {
      await fetch(`${api}/favorites?sourceId=${s.id}&url=${encodeURIComponent(d.url)}`, { method: "DELETE" });
      btn.dataset.fav = "0";
      btn.classList.remove("on");
      btn.textContent = t("fav.add");
    } else {
      const res = await fetch(`${api}/favorites`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sourceId: String(s.id), url: d.url, title: d.title, thumbnailUrl: d.thumb || null, type: d.type }),
      });
      const data = await res.json().catch(() => ({}));
      if (!data.ok) { alert(t("fav.saveError", data.error || t("common.unknown"))); return; }
      btn.dataset.fav = "1";
      btn.classList.add("on");
      btn.textContent = t("fav.in");
    }
  } catch (e) {
    alert(t("common.error", e.message));
  }
}

/* ---------------- Favorites ---------------- */

async function showFavorites() {
  state.currentView = "favorites";
  setNav("favorites");
  const main = $("#content");
  main.innerHTML = `<div class="loading">${escapeHtml(t("fav.loading"))}</div>`;
  try {
    const favs = await getJSON(`${api}/favorites`);
    if (!favs || favs.length === 0) {
      main.innerHTML = `<div class="empty"><h2>${escapeHtml(t("fav.emptyTitle"))}</h2><p>${escapeHtml(t("fav.emptyText"))}</p></div>`;
      return;
    }
    const byType = { manga: [], anime: [] };
    for (const f of favs) (byType[f.type] || (byType[f.type] = [])).push(f);
    let html = "";
    for (const [type, list] of Object.entries(byType)) {
      if (!list || !list.length) continue;
      html += rowWrap(type === "manga" ? t("nav.manga") : t("nav.anime"), list.map((f) => favCard(f)).join(""));
    }
    main.innerHTML = html || `<div class="empty">${escapeHtml(t("fav.emptyTitle"))}</div>`;
  } catch (e) {
    main.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function openFavorite(sourceId, type, url) {
  state.activeSource = { id: sourceId, type, name: "" };
  openEntry(sourceId, type, url, () => { setNav("favorites"); showFavorites(); });
}

/* ---------------- Reader ---------------- */

async function openChapter(encUrl, name) {
  state.currentView = "reader";
  navPush(() => { if (state.detail) renderDetail(state.detail.type, state.detailObj); });
  const main = $("#content");
  main.innerHTML = `<div class="loading">${escapeHtml(t("reader.loadingPages"))}</div>`;
  try {
    const data = await getJSON(`${api}/manga/${state.activeSource.id}/pages?url=${encUrl}`);
    const pages = data.pages;
    if (!pages || pages.length === 0) { main.innerHTML = `<div class="error">${escapeHtml(t("reader.noPages"))}</div>`; return; }

    if (state.detail && state.detail.url) {
      try {
        await fetch(`${api}/favorites/progress`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            sourceId: String(state.activeSource.id),
            url: state.detail.url,
            lastReadUrl: decodeURIComponent(encUrl),
            lastReadName: name,
          }),
        });
      } catch (e) { }
    }
    main.innerHTML = `
      <div class="reader-top">
        <button class="btn-ghost" onclick="goBack()">← ${escapeHtml(t("reader.back"))}</button>
        <h2 style="margin:0">${escapeHtml(name)}</h2>
      </div>
      <div class="viewer" id="viewer">
        ${pages.map((p) => `
          <img loading="lazy" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(p.imageUrl || "")}">`).join("")}
      </div>`;
  } catch (e) {
    main.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

/* ---------------- Player helpers / order / watch ---------------- */

function episodeOrder() {
  return localStorage.getItem("miwayomi.episodeOrder") || "newest";
}
function setEpisodeOrder(o) {
  localStorage.setItem("miwayomi.episodeOrder", o);
}
function sortEpisodes(list) {
  const arr = (list || []).slice();
  const hasNum = arr.some((e) => e.episode_number != null && e.episode_number !== 0);
  if (hasNum) arr.sort((a, b) => (Number(a.episode_number) || 0) - (Number(b.episode_number) || 0));
  return episodeOrder() === "oldest" ? arr : arr.reverse();
}
function toggleDetailOrder() {
  setEpisodeOrder(episodeOrder() === "oldest" ? "newest" : "oldest");
  if (state.detailObj && state.episodes) renderAnimeEpisodes(state.detailObj, state.episodes);
}
function togglePlayerOrder() {
  setEpisodeOrder(episodeOrder() === "oldest" ? "newest" : "oldest");
  renderPlayerEpisodes(state.episodes || [], state.currentEncUrl);
}
function setAutoPlay(v) { localStorage.setItem("miwayomi.autoPlayNext", v ? "1" : "off"); }
function setAutoSource(v) { localStorage.setItem("miwayomi.autoSource", v ? "1" : "off"); }

function renderAnimeEpisodes(d, eps) {
  const list = sortEpisodes(eps || []);
  const orderLabel = episodeOrder() === "oldest" ? t("player.orderOldest") : t("player.orderNewest");
  const epThumb = (e) => {
    if (e.preview_url) return `<img class="ep-thumb-img ready" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(e.preview_url)}">`;
    if (d && d.thumbnail_url) return `<img class="ep-thumb-img ready" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(d.thumbnail_url)}">`;
    return "";
  };
  const items = list.map((e, i) => `
    <div class="ep-card" onclick="openEpisode('${encodeURIComponent(e.url)}','${escapeHtml(e.name)}')">
      <div class="ep-thumb">
        <span class="ep-num">E${e.episode_number ?? i + 1}</span>
        ${epThumb(e)}
      </div>
      <div class="ep-name">${escapeHtml(e.name)}</div>
    </div>`).join("");
  $("#entryList").innerHTML = `<div class="list-title">${escapeHtml(t("detail.episodes"))}
      <button class="order-btn" onclick="toggleDetailOrder()" title="${escapeHtml(t("player.orderHint"))}">${escapeHtml(t("player.orderIcon"))} ${escapeHtml(orderLabel)}</button>
    </div>` +
    (items ? `<div class="ep-grid">${items}</div>` : `<div class="empty">${escapeHtml(t("detail.noEpisodes"))}</div>`);
}

function pickBestVideo(videos) {
  let best = 0, bestRes = -1;
  (videos || []).forEach((v, i) => {
    const res = parseInt(v.resolution, 10) || 0;
    if (res > bestRes) { bestRes = res; best = i; }
  });
  return best;
}

function fmtTime(s) {
  s = Math.max(0, Math.floor(s || 0));
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  const mm = m < 10 ? "0" + m : "" + m;
  const ss = sec < 10 ? "0" + sec : "" + sec;
  return h > 0 ? `${h}:${mm}:${ss}` : `${m}:${ss}`;
}

let toastTimer = null;
function toast(msg) {
  const el = $("#resumeToast");
  if (!el) return;
  el.textContent = msg;
  el.classList.remove("hidden");
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { const x = $("#resumeToast"); if (x) x.classList.add("hidden"); }, 4000);
}

/* ---------------- Watch history / resume (server DB) ---------------- */

function safeDecode(s) { try { return decodeURIComponent(s); } catch (e) { return s; } }
function watchEntryKey(sourceId, animeUrl, epUrl) { return `${sourceId}|||${animeUrl}|||${epUrl}`; }
function currentWatchEntry() {
  if (!state.activeSource || !state.detail || !state.currentEncUrl) return null;
  const key = watchEntryKey(state.activeSource.id, safeDecode(state.detail.url), safeDecode(state.currentEncUrl));
  return { key, sourceId: String(state.activeSource.id), type: "anime", animeUrl: safeDecode(state.detail.url),
    epUrl: safeDecode(state.currentEncUrl), animeTitle: state.detail.title || "",
    epName: state.currentEpName || "", thumb: state.detail.thumb || "", time: 0, duration: 0,
    updatedAt: Date.now(), completed: false, episodeNumber: state.currentEpNumber != null ? state.currentEpNumber : null };
}

let watchCache = null;
let watchCacheAt = 0;
async function loadWatchCache(force) {
  if (watchCache !== null && !force && Date.now() - watchCacheAt < 20000) return watchCache;
  try {
    const res = await fetch(`${api}/watch`);
    if (res.ok) { watchCache = await res.json(); watchCacheAt = Date.now(); }
  } catch (e) { }
  return watchCache || [];
}
function savedWatchFor(sourceId, animeUrl, epUrl) {
  const key = watchEntryKey(sourceId, animeUrl, epUrl);
  return (watchCache || []).find((w) => watchEntryKey(w.sourceId, w.animeUrl, w.epUrl) === key) || null;
}
function upsertWatchCache(w) {
  watchCache = watchCache || [];
  const key = watchEntryKey(w.sourceId, w.animeUrl, w.epUrl);
  const i = watchCache.findIndex((x) => watchEntryKey(x.sourceId, x.animeUrl, x.epUrl) === key);
  const entry = { sourceId: w.sourceId, animeUrl: w.animeUrl, epUrl: w.epUrl, animeTitle: w.animeTitle,
    epName: w.epName, thumb: w.thumb, timeSeconds: w.time, durationSeconds: w.duration,
    updatedAt: w.updatedAt, completed: w.completed || false, episodeNumber: w.episodeNumber };
  if (i >= 0) watchCache[i] = Object.assign({}, watchCache[i], entry);
  else watchCache.unshift(entry);
  watchCache = watchCache.slice(0, 40);
}
function removeWatchCache(w) {
  watchCache = (watchCache || []).filter((x) => watchEntryKey(x.sourceId, x.animeUrl, x.epUrl) !== watchEntryKey(w.sourceId, w.animeUrl, w.epUrl));
}

async function saveWatchProgress(time, duration) {
  const e = currentWatchEntry(); if (!e) return;
  e.time = Math.max(0, time || 0); e.duration = duration || 0;
  e.updatedAt = Date.now();
  upsertWatchCache(e);
  try {
    await fetch(`${api}/watch`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sourceId: e.sourceId, animeUrl: e.animeUrl, epUrl: e.epUrl,
        animeTitle: e.animeTitle, epName: e.epName, thumb: e.thumb,
        timeSeconds: e.time, durationSeconds: e.duration, completed: e.completed,
        episodeNumber: e.episodeNumber,
      }),
    });
    syncToAniList(e);
  } catch (err) { }
}
async function clearWatchProgress() {
  const e = currentWatchEntry(); if (!e) return;
  removeWatchCache(e);
  try {
    await fetch(`${api}/watch?sourceId=${encodeURIComponent(e.sourceId)}&animeUrl=${encodeURIComponent(e.animeUrl)}&epUrl=${encodeURIComponent(e.epUrl)}`, { method: "DELETE" });
  } catch (err) { }
}
async function listWatch() {
  const list = await loadWatchCache();
  return (list || []).filter((w) => w && w.epUrl).slice(0, 12);
}
function watchCard(w) {
  const pct = w.durationSeconds ? Math.min(100, Math.round((w.timeSeconds / w.durationSeconds) * 100)) : 0;
  const thumb = w.thumb
    ? `<img loading="lazy" src="/api/v1/proxy?sourceId=${w.sourceId}&url=${encodeURIComponent(w.thumb)}">`
    : `<div class="card-img-placeholder">${escapeHtml((w.animeTitle || "?").charAt(0).toUpperCase())}</div>`;
  return `
    <div class="card watch-card" onclick="openWatch('${w.sourceId}','${encodeURIComponent(w.animeUrl)}','${encodeURIComponent(w.epUrl)}','${escapeHtml(w.epName)}')">
      ${thumb}
      <div class="card-title">${escapeHtml(w.animeTitle)}</div>
      <div class="card-sub watch-sub">${escapeHtml(w.epName)}</div>
      <div class="watch-bar"><div class="watch-bar-fill" style="width:${pct}%"></div></div>
    </div>`;
}
async function openWatch(sourceId, animeUrl, epUrl, epName) {
  navPush(() => { setNav("home"); renderHome(); });
  state.activeSource = { id: sourceId, type: "anime", name: "" };
  const main = $("#content");
  main.innerHTML = `<div class="loading">${escapeHtml(t("common.loading"))}</div>`;
  try {
    const d = await getJSON(`${api}/anime/${sourceId}/details?url=${encodeURIComponent(safeDecode(animeUrl))}`);
    await renderDetail("anime", d);
    openEpisode(encodeURIComponent(safeDecode(epUrl)), epName, true);
  } catch (e) {
    main.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function nextEpisode() {
  const eps = sortEpisodes(state.episodes || []);
  if (!eps.length || !state.currentEncUrl) return null;
  const cur = decodeURIComponent(state.currentEncUrl);
  const idx = eps.findIndex((e) => decodeURIComponent(e.url) === cur);
  if (idx < 0 || idx >= eps.length - 1) return null;
  return eps[idx + 1];
}
function attemptPlay(video) {
  if (!video) return;
  const p = video.play && video.play();
  if (p && p.catch) p.catch(() => { });
}
function maybeResume(video) {
  const e = currentWatchEntry();
  if (!e) return;
  const saved = savedWatchFor(e.sourceId, e.animeUrl, e.epUrl);
  if (!saved) return;
  const savedTime = saved.timeSeconds || 0;
  const savedDur = saved.durationSeconds || 0;
  const nearEnd = savedDur > 0 && savedTime > savedDur - 15;
  if (savedTime < 10 || nearEnd) return;
  const sec = savedTime;
  const doResume = () => {
    try { if (!video.currentTime || video.currentTime < sec - 2) video.currentTime = sec; } catch (_) { }
    attemptPlay(video);
  };
  if (video.readyState >= 1) doResume();
  else video.addEventListener("loadedmetadata", doResume, { once: true });
  toast(t("player.resuming", fmtTime(sec)));
}
function bindVideoEvents(video) {
  if (video.__bound) return;
  video.__bound = true;
  let lastSave = 0;
  video.addEventListener("timeupdate", () => {
    const now = Date.now();
    if (now - lastSave < 3000) return;
    lastSave = now;
    saveWatchProgress(video.currentTime, video.duration);
  });
  video.addEventListener("pause", () => saveWatchProgress(video.currentTime, video.duration));
  video.addEventListener("ended", () => {
    clearWatchProgress();
    if (localStorage.getItem("miwayomi.autoPlayNext") !== "off") {
      const nx = nextEpisode();
      if (nx) openEpisode(encodeURIComponent(nx.url), nx.name, true);
    }
  });
}
function restartEpisode() {
  const e = currentWatchEntry();
  if (e) clearWatchProgress();
  const x = $("#resumeToast"); if (x) x.classList.add("hidden");
  const v = $("#videoPlayer");
  if (v) { try { v.currentTime = 0; } catch (_) { } attemptPlay(v); }
}

/* ---------------- AniList sync ---------------- */

const ANILIST_API = "https://graphql.anilist.co/graphql";
function anilistClientId() { return (localStorage.getItem("miwayomi.anilist.clientId") || "").trim(); }
function anilistToken() { return localStorage.getItem("miwayomi.anilist.token") || ""; }
function anilistSyncOn() { return localStorage.getItem("miwayomi.anilist.sync") !== "off"; }
function anilistMediaCache() { try { return JSON.parse(localStorage.getItem("miwayomi.anilist.media") || "{}"); } catch (e) { return {}; } }
function anilistSaveMediaCache(c) { try { localStorage.setItem("miwayomi.anilist.media", JSON.stringify(c)); } catch (e) { } }

async function anilistGraphql(query, vars) {
  const res = await fetch(ANILIST_API, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Accept": "application/json", "Authorization": "Bearer " + anilistToken() },
    body: JSON.stringify({ query, variables: vars }),
  });
  if (!res.ok) throw new Error("AniList HTTP " + res.status);
  return res.json();
}

async function anilistUsername() {
  try {
    const d = await anilistGraphql("query { Viewer { name } }", {});
    return d && d.data && d.data.Viewer ? d.data.Viewer.name : null;
  } catch (e) { return null; }
}

async function syncToAniList(entry) {
  if (!anilistSyncOn()) return;
  if (!anilistToken()) return;
  if (!entry || !entry.episodeNumber || !entry.animeTitle) return;
  try {
    const cache = anilistMediaCache();
    const mkey = `${entry.sourceId}|||${entry.animeUrl}`;
    let mediaId = cache[mkey];
    if (!mediaId) {
      const s = await anilistGraphql("query($s: String) { Media(search: $s, type: ANIME) { id } }", { s: entry.animeTitle });
      const media = s && s.data && s.data.Media;
      if (!media || !media.id) return;
      mediaId = media.id;
      cache[mkey] = mediaId;
      anilistSaveMediaCache(cache);
    }
    let synced = {};
    try { synced = JSON.parse(localStorage.getItem("miwayomi.anilist.synced") || "{}"); } catch (e) { }
    if ((synced[mkey] || 0) >= entry.episodeNumber) return;
    const status = entry.completed ? "COMPLETED" : "CURRENT";
    await anilistGraphql(
      "mutation($id: Int, $p: Int, $st: MediaListStatus) { SaveMediaListEntry(mediaId: $id, progress: $p, status: $st) { id progress } }",
      { id: mediaId, p: entry.episodeNumber, st: status },
    );
    synced[mkey] = entry.episodeNumber;
    localStorage.setItem("miwayomi.anilist.synced", JSON.stringify(synced));
  } catch (e) {
    console.log("[miwayomi] AniList sync failed: " + e.message);
  }
}

function connectAniList() {
  const cid = anilistClientId();
  if (!cid) { alert(t("anilist.needClientId")); return; }
  const redirect = encodeURIComponent(location.origin + "/index.html");
  const url = `https://anilist.co/api/v2/oauth/authorize?client_id=${encodeURIComponent(cid)}&response_type=token&redirect_uri=${redirect}`;
  window.open(url, "_blank", "width=520,height=720");
}

function disconnectAniList() {
  localStorage.removeItem("miwayomi.anilist.token");
  localStorage.removeItem("miwayomi.anilist.media");
  localStorage.removeItem("miwayomi.anilist.synced");
  refreshAniListUi();
  toast(t("anilist.disconnected"));
}

async function refreshAniListUi() {
  const status = $("#anilistStatus");
  if (!status) return;
  const cid = anilistClientId();
  const token = anilistToken();
  if (!cid) { status.innerHTML = `<span class="anilist-off">${escapeHtml(t("anilist.noClient"))}</span>`; return; }
  if (!token) { status.innerHTML = `<span class="anilist-off">${escapeHtml(t("anilist.notConnected"))}</span>`; return; }
  const name = await anilistUsername();
  status.innerHTML = name
    ? `<span class="anilist-on">${escapeHtml(t("anilist.connectedAs", name))}</span>`
    : `<span class="anilist-off">${escapeHtml(t("anilist.tokenInvalid"))}</span>`;
}

function setAniListSync(v) { localStorage.setItem("miwayomi.anilist.sync", v ? "1" : "off"); }
function setAniListClientId(v) {
  localStorage.setItem("miwayomi.anilist.clientId", v);
  refreshAniListUi();
}

function handleAniListHash() {
  const h = location.hash || "";
  if (!h.includes("access_token=")) return;
  const params = new URLSearchParams(h.replace(/^#/, ""));
  const token = params.get("access_token");
  if (token) {
    localStorage.setItem("miwayomi.anilist.token", token);
    try { history.replaceState(null, "", location.pathname + location.search); } catch (e) { location.hash = ""; }
    refreshAniListUi();
    toast(t("anilist.connected"));
  }
}

/* ---------------- Player ---------------- */

async function openEpisode(encUrl, name, noPush) {
  state.currentView = "player";
  if (!noPush) navPush(() => { if (state.detail) renderDetail(state.detail.type, state.detailObj); });
  state.currentEncUrl = encUrl;
  state.currentEpName = name || "";
  const main = $("#content");
  main.innerHTML = `<div class="loading">${escapeHtml(t("player.fetching"))}</div>`;
  try {
    const data = await getJSON(`${api}/anime/${state.activeSource.id}/videos?url=${encUrl}`);
    const videos = data.videos || [];
    if (!videos || videos.length === 0) { main.innerHTML = `<div class="error">${escapeHtml(t("player.noVideos"))}</div>`; return; }

    let eps = [];
    if (state.detail && state.detail.url) {
      try {
        const e = await getJSON(`${api}/anime/${state.activeSource.id}/episodes?url=${encodeURIComponent(state.detail.url)}`);
        eps = e.episodes || [];
      } catch (err) { }
    }
    state.episodes = eps;
    window.__videos = videos;
    const curEp = (eps || []).find((e) => decodeURIComponent(e.url) === decodeURIComponent(encUrl));
    state.currentEpNumber = curEp && curEp.episode_number != null ? curEp.episode_number : null;
    await loadWatchCache();

    const autoNext = localStorage.getItem("miwayomi.autoPlayNext") !== "off";
    const autoSrc = localStorage.getItem("miwayomi.autoSource") !== "off";

    main.innerHTML = `
      <div class="reader-top">
        <button class="btn-ghost" onclick="goBack()">← ${escapeHtml(t("reader.back"))}</button>
        <div class="player-title">${escapeHtml(name)}</div>
      </div>
      <div class="player-wrap">
        <div class="player-main">
          <video id="videoPlayer" controls></video>
          <div class="video-item" style="margin-top:12px">
            <span id="playingLabel">${escapeHtml(t("player.select"))}</span>
            <span id="qualityBtns"></span>
            <button class="btn-ghost" onclick="restartEpisode()" title="${escapeHtml(t("player.restart"))}" style="font-size:18px">↺</button>
          </div>
          <div class="player-controls">
            <label class="pctl"><input type="checkbox" id="autoPlayCb" ${autoNext ? "checked" : ""} onchange="setAutoPlay(this.checked)"> ${escapeHtml(t("player.autoNext"))}</label>
            <label class="pctl"><input type="checkbox" id="autoSrcCb" ${autoSrc ? "checked" : ""} onchange="setAutoSource(this.checked)"> ${escapeHtml(t("player.autoSource"))}</label>
          </div>
          <div id="resumeToast" class="resume-toast hidden"></div>
        </div>
        <div class="player-episodes" id="playerEpisodes"></div>
      </div>`;

    renderQuality(videos);
    renderPlayerEpisodes(eps, encUrl);
    if (autoSrc) {
      playVideo(pickBestVideo(videos));
      attemptPlay($("#videoPlayer"));
    } else if (videos.length === 1) playVideo(0);
  } catch (e) {
    main.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function renderQuality(videos) {
  const wrap = $("#qualityBtns");
  if (!wrap) return;
  wrap.innerHTML = videos.map((v, i) =>
    `<button class="qbtn" data-i="${i}" style="margin-left:6px" onclick="playVideo(${i})">${escapeHtml(v.resolution ? v.resolution + "p" : (v.videoTitle || i + 1))}</button>`).join("");
}
function setQualityActive(i) {
  document.querySelectorAll("#qualityBtns .qbtn").forEach((b) => b.classList.toggle("active", Number(b.dataset.i) === i));
}

function renderPlayerEpisodes(eps, currentEnc) {
  const box = $("#playerEpisodes");
  if (!box) return;
  const list = sortEpisodes(eps || []);
  const orderLabel = episodeOrder() === "oldest" ? t("player.orderOldest") : t("player.orderNewest");
  box.innerHTML = `<div class="list-title">${escapeHtml(t("detail.episodes"))}
      <button class="order-btn" onclick="togglePlayerOrder()" title="${escapeHtml(t("player.orderHint"))}">${escapeHtml(t("player.orderIcon"))} ${escapeHtml(orderLabel)}</button>
    </div>` +
    (list.length ? list.map((e, i) => `
      <div class="player-episode ${decodeURIComponent(currentEnc) === decodeURIComponent(e.url) ? "active" : ""}" onclick="openEpisode('${encodeURIComponent(e.url)}','${escapeHtml(e.name)}',true)">
        <span class="pe-num">E${e.episode_number ?? i + 1}</span><span>${escapeHtml(e.name)}</span>
      </div>`).join("") : `<div class="empty">${escapeHtml(t("detail.noEpisodes"))}</div>`);
}

function playVideo(i) {
  const videos = window.__videos || [];
  const v = videos[i];
  if (!v) return;
  const headers = v.headers ? Object.entries(v.headers).map(([k, val]) => `${k}: ${val}`).join("\n") : "";
  const url = encodeURIComponent(v.videoUrl || "");
  const headersEnc = encodeURIComponent(headers);
  const proxyBase = `/api/v1/proxy?sourceId=${state.activeSource.id}&url=${url}&headers=${headersEnc}`;
  const hlsProxy = `/api/v1/hls?sourceId=${state.activeSource.id}&url=${url}&headers=${headersEnc}`;
  const dashProxy = `/api/v1/dash?sourceId=${state.activeSource.id}&url=${url}&headers=${headersEnc}`;
  const label = $("#playingLabel");
  if (label) label.textContent = t("player.playing", v.videoTitle || t("player.video"));
  const video = $("#videoPlayer");
  if (!video) return;
  setQualityActive(i);
  bindVideoEvents(video);
  maybeResume(video);
  const videoUrl = v.videoUrl || "";
  const isHls = videoUrl.includes(".m3u8");
  const isDash = videoUrl.includes(".mpd");

  if (isDash && window.dashjs) {
    const player = dashjs.MediaPlayer().create();
    player.initialize(video, v.videoUrl, true);
    player.on(dashjs.MediaPlayer.events.ERROR, (e) => {
      if (e && e.error && (e.error.code === 27 || e.error.code === 25)) {
        try { player.reset(); } catch (_) { }
        const p2 = dashjs.MediaPlayer().create();
        p2.initialize(video, dashProxy, true);
      }
    });
    window.__dashPlayer = player;
  } else if (isHls && window.Hls && Hls.isSupported()) {
    const hls = new Hls({ maxBufferLength: 60 });
    hls.loadSource(hlsProxy);
    hls.attachMedia(video);
    hls.on(Hls.Events.ERROR, (evt, data) => {
      if (data.fatal) {
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hls.startLoad();
        else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hls.recoverMediaError();
        else video.controls = true;
      }
    });
  } else if (video.canPlayType("application/vnd.apple.mpegurl")) {
    video.src = hlsProxy;
  } else {
    video.src = proxyBase;
  }
}

function statusText(s) {
  const map = {
    0: t("common.unknown"), 1: t("detail.airing"), 2: t("detail.completed"),
    3: t("detail.licensed"), 4: t("detail.finished"), 5: t("detail.cancelled"), 6: t("detail.onHiatus"),
  };
  return map[s] ? t("detail.status", map[s]) : "";
}

/* ---------------- Settings ---------------- */

function openSettings() {
  const box = $("#modalBox");
  box.innerHTML = `
    <h3>${escapeHtml(t("settings.title"))}</h3>
    <div class="settings-group">
      <h4>${escapeHtml(t("settings.appearance"))}</h4>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("settings.language"))}</b><span>${escapeHtml(t("settings.languageHint"))}</span></div>
        <select onchange="setLang(this.value)">${AVAILABLE_LANGS.map((l) => `<option value="${l}" ${currentLang() === l ? "selected" : ""}>${l.toUpperCase()}</option>`).join("")}</select>
      </div>
    </div>
    <div class="settings-group">
      <h4>${escapeHtml(t("settings.server"))}</h4>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("settings.health"))}</b><span id="setHealth">…</span></div>
      </div>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("settings.extensionsDir"))}</b><span>${escapeHtml(t("settings.extensionsDirHint"))}</span></div>
      </div>
    </div>
    <div class="settings-group">
      <h4>${escapeHtml(t("anilist.title"))}</h4>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("anilist.clientId"))}</b><span>${escapeHtml(t("anilist.clientIdHint", location.origin + "/index.html"))}</span></div>
        <input id="anilistClientId" type="text" value="${escapeHtml(anilistClientId())}" placeholder="12345" onchange="setAniListClientId(this.value)" style="max-width:180px">
      </div>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("anilist.status"))}</b><span id="anilistStatus">…</span></div>
        <span>
          ${anilistToken()
            ? `<button onclick="disconnectAniList()">${escapeHtml(t("anilist.disconnect"))}</button>`
            : `<button class="btn-primary" onclick="connectAniList()">${escapeHtml(t("anilist.connect"))}</button>`}
        </span>
      </div>
      <div class="setting-row">
        <div class="st-lbl"><b>${escapeHtml(t("anilist.sync"))}</b><span>${escapeHtml(t("anilist.syncHint"))}</span></div>
        <input type="checkbox" id="anilistSyncCb" ${anilistSyncOn() ? "checked" : ""} onchange="setAniListSync(this.checked)">
      </div>
    </div>
    <div class="pref-actions">
      <button class="btn-primary" onclick="closeModal()">${escapeHtml(t("common.close"))}</button>
    </div>`;
  openModal();
  refreshAniListUi();
  fetch(`${api}/health`).then((r) => r.json()).then((d) => {
    const el = $("#setHealth");
    if (el) el.textContent = `${d.service} · manga ${d.mangaSources} · anime ${d.animeSources}`;
  }).catch(() => { });
}

/* ---------------- Source prefs ---------------- */

async function openSourcePrefs() {
  const s = state.activeSource;
  if (!s) return;
  const box = $("#modalBox");
  box.innerHTML = `<div class="loading">${escapeHtml(t("prefs.loading"))}</div>`;
  openModal();
  try {
    const d = await getJSON(`${api}/sources/${s.id}/prefs`);
    box.innerHTML = renderPrefs(s, d);
  } catch (e) {
    box.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function renderPrefs(s, d) {
  if (!d.configurable) {
    return `<h3 style="margin-top:0">${escapeHtml(t("prefs.title", s.name))}</h3><p class="cf-help">${escapeHtml(t("prefs.notConfigurable"))}</p><button class="btn-primary" onclick="closeModal()">${escapeHtml(t("common.close"))}</button>`;
  }
  if (d.error) {
    return `<h3 style="margin-top:0">${escapeHtml(t("prefs.title", s.name))}</h3><p class="error">${escapeHtml(d.error)}</p><button class="btn-primary" onclick="closeModal()">${escapeHtml(t("common.close"))}</button>`;
  }
  if (!d.prefs || !d.prefs.length) {
    return `<h3 style="margin-top:0">${escapeHtml(t("prefs.title", s.name))}</h3><p class="cf-help">${escapeHtml(t("prefs.none"))}</p><button class="btn-primary" onclick="closeModal()">${escapeHtml(t("common.close"))}</button>`;
  }
  const rows = d.prefs.map((p) => {
    const key = escapeHtml(p.key || "");
    const title = escapeHtml(p.title || p.key || "");
    const summary = p.summary && p.summary !== "%s" ? `<div class="pref-summary">${escapeHtml(p.summary)}</div>` : "";
    const lbl = `<div class="pref-lbl"><b>${title}</b>${summary}</div>`;
    switch (p.type) {
      case "switch":
        return `<label class="pref-row">${lbl}<input type="checkbox" data-key="${key}" ${p.value === "true" ? "checked" : ""}></label>`;
      case "text":
        return `<label class="pref-row">${lbl}<input type="text" data-key="${key}" value="${escapeHtml(p.value || "")}"></label>`;
      case "list": {
        const opts = (p.labels || []).map((l, i) =>
          `<option value="${escapeHtml((p.values || [])[i] ?? l)}" ${p.value === (p.values || [])[i] ? "selected" : ""}>${escapeHtml(l)}</option>`).join("");
        return `<label class="pref-row">${lbl}<select data-key="${key}">${opts}</select></label>`;
      }
      case "multi": {
        const sel = new Set((p.value || "").split(","));
        const opts = (p.values || []).map((v, i) =>
          `<label class="pref-check"><input type="checkbox" data-key="${key}" data-multi value="${escapeHtml(v)}" ${sel.has(v) ? "checked" : ""}>${escapeHtml((p.labels || [])[i] || v)}</label>`).join("");
        return `<div class="pref-row pref-multi">${lbl}<div>${opts}</div></div>`;
      }
      default: return "";
    }
  }).join("");
  return `<h3 style="margin-top:0">${escapeHtml(t("prefs.title", s.name))}</h3>
    <div class="prefs">${rows}</div>
    <div class="pref-actions">
      <button class="btn-primary" onclick="saveSourcePrefs()">${escapeHtml(t("common.save"))}</button>
      <button onclick="closeModal()">${escapeHtml(t("common.cancel"))}</button>
    </div>`;
}

async function saveSourcePrefs() {
  const s = state.activeSource;
  const body = {};
  document.querySelectorAll("#modalBox [data-key]").forEach((el) => {
    const key = el.dataset.key;
    if (el.dataset.multi !== undefined) {
      if (!body[key]) body[key] = [];
      if (el.checked) body[key].push(el.value);
    } else if (el.type === "checkbox") {
      body[key] = el.checked;
    } else {
      body[key] = el.value;
    }
  });
  for (const k of Object.keys(body)) if (Array.isArray(body[k])) body[k] = body[k].join(",");
  try {
    const res = await fetch(`${api}/sources/${s.id}/prefs`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({}));
    if (data.ok) closeModal();
    else alert(t("prefs.saveError", data.error || t("common.unknown")));
  } catch (e) {
    alert("Error: " + e.message);
  }
}

/* ---------------- Extensions (repos stored in the DB) ---------------- */

let REPOS = [];
let activeRepo = 0;

// Repository URLs are persisted server-side (SQLite) so the user doesn't have
// to re-enter them, and no repository is loaded by default.
async function loadRepos() {
  REPOS = [];
  try {
    const d = await getJSON(`${api}/extensions/repos`);
    REPOS = Array.isArray(d.repos) ? d.repos.filter(Boolean) : [];
  } catch (e) { REPOS = []; }
  if (activeRepo >= REPOS.length) activeRepo = 0;
}
async function saveRepos() {
  try {
    await fetch(`${api}/extensions/repos`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ repos: REPOS }),
    });
  } catch (e) { }
}
function shortRepo(url) {
  try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return url; }
}

async function openExtensions() {
  activeRepo = 0;
  await loadRepos();
  renderExtensionsModal();
  openModal();
  loadRepo();
}

function renderExtensionsModal() {
  const box = $("#modalBox");
  box.innerHTML = `
    <h3>${escapeHtml(t("ext.title"))}</h3>
    <p class="cf-help">${t("ext.help")}</p>
    <div class="repo-tabs">
      ${REPOS.map((r, i) => `<span class="repo-tab ${i === activeRepo ? "active" : ""}" onclick="switchRepo(${i})">${escapeHtml(shortRepo(r))}${REPOS.length > 1 ? ` <span onclick="event.stopPropagation();removeRepo(${i})" style="cursor:pointer;opacity:.7">✕</span>` : ""}</span>`).join("")}
      <span class="repo-tab" onclick="showAddRepo()">＋ ${escapeHtml(t("ext.addRepo"))}</span>
    </div>
    <div id="repoAdd" style="display:none" class="repo-add">
      <input id="repoUrl" type="text" placeholder="${escapeHtml(t("ext.repoPh"))}">
      <button class="btn-primary" onclick="confirmAddRepo()">${escapeHtml(t("ext.add"))}</button>
      <button onclick="hideAddRepo()">${escapeHtml(t("common.cancel"))}</button>
    </div>
    <div class="repo-filters">
      <input id="repoSearch" type="text" placeholder="${escapeHtml(t("ext.searchPh"))}" oninput="filterRepo()" class="repo-search">
      <select id="repoLang" onchange="filterRepo()" class="repo-lang" title="${escapeHtml(t("ext.filterLang"))}"></select>
    </div>
    <div id="repoList" class="repo-list"><div class="loading">${escapeHtml(t("ext.loading"))}</div></div>`;
}

function switchRepo(i) { activeRepo = i; renderExtensionsModal(); loadRepo(); }
function showAddRepo() { const el = $("#repoAdd"); if (el) el.style.display = "flex"; }
function hideAddRepo() { const el = $("#repoAdd"); if (el) el.style.display = "none"; }
async function confirmAddRepo() {
  const input = $("#repoUrl");
  const url = (input && input.value.trim()) || "";
  if (!url) return;
  REPOS.push(url);
  await saveRepos();
  activeRepo = REPOS.length - 1;
  renderExtensionsModal();
  loadRepo();
}
async function removeRepo(i) {
  REPOS.splice(i, 1);
  await saveRepos();
  if (activeRepo >= REPOS.length) activeRepo = REPOS.length - 1;
  renderExtensionsModal();
  loadRepo();
}

async function loadRepo() {
  const list = $("#repoList");
  const prevScroll = list ? list.scrollTop : 0;
  const url = REPOS[activeRepo];
  if (!url) {
    // No repository configured: show only the locally installed extensions.
    if (list) list.innerHTML = `<div class="loading">${escapeHtml(t("ext.loading"))}</div>`;
    try {
      const data = await getJSON(`${api}/extensions/installed`);
      window.__repo = data.extensions || [];
      if (!window.__repo.length) {
        if (list) list.innerHTML = `<div class="empty">${escapeHtml(t("ext.noRepos"))}</div>`;
        return;
      }
      populateLangFilter();
      renderRepo(window.__repo, $("#repoSearch")?.value || "", $("#repoLang")?.value || "");
      if (list) list.scrollTop = prevScroll;
    } catch (e) {
      if (list) list.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
    }
    return;
  }
  if (list) list.innerHTML = `<div class="loading">${escapeHtml(t("ext.consulting"))}</div>`;
  try {
    const data = await getJSON(`${api}/extensions/repo?url=${encodeURIComponent(url)}`);
    window.__repo = data.extensions || [];
    populateLangFilter();
    renderRepo(window.__repo, $("#repoSearch")?.value || "", $("#repoLang")?.value || "");
    if (list) list.scrollTop = prevScroll;
  } catch (e) {
    if (list) list.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

const SOURCES_MAX_SHOWN = 3;
function summarizeSources(sources) {
  if (!sources || !sources.length) return "";
  const unique = [];
  const seen = {};
  for (const s of sources) {
    const n = s.name || "";
    if (!seen[n]) { seen[n] = true; unique.push(n); }
  }
  let str;
  if (unique.length <= SOURCES_MAX_SHOWN) {
    str = unique.join(", ");
  } else {
    const extra = unique.length - SOURCES_MAX_SHOWN;
    str = unique.slice(0, SOURCES_MAX_SHOWN).join(", ") + " " + t("ext.more", extra);
  }
  return " · " + str;
}

function populateLangFilter() {
  const sel = $("#repoLang");
  if (!sel) return;
  const entries = window.__repo || [];
  const langs = [...new Set(entries.map((e) => e.lang).filter(Boolean))].sort();
  const current = sel.value;
  sel.innerHTML = `<option value="">${escapeHtml(t("ext.allLangs"))}</option>` +
    langs.map((l) => `<option value="${escapeHtml(l)}">${escapeHtml(l)}</option>`).join("");
  if (current && langs.includes(current)) sel.value = current;
}

function renderRepo(entries, q, lang) {
  const list = $("#repoList");
  if (!list) return;
  const filt = q.trim().toLowerCase();
  const langF = (lang || "").trim();
  const filtered = entries.filter((e) =>
    (!filt || (e.name + " " + e.pkg + " " + e.lang).toLowerCase().includes(filt)) &&
    (!langF || (e.lang || "") === langF));
  const byLang = {};
  for (const e of filtered) (byLang[e.lang] = byLang[e.lang] || []).push(e);
  const langOrder = Object.keys(byLang).sort();
  if (langOrder.length === 0) { list.innerHTML = `<div class="empty">${escapeHtml(t("common.noResults"))}</div>`; return; }
  list.innerHTML = langOrder.map((lang) => `
    <div class="group-title">${escapeHtml(lang)} (${byLang[lang].length})</div>
    ${byLang[lang].map((e) => `
      <div class="repo-item">
        <div class="repo-info">
          <strong>${escapeHtml(e.name)}</strong> ${e.nsfw ? '<span class="badge nsfw">NSFW</span>' : ""}
          <div class="repo-meta">${escapeHtml(e.version || "")}${summarizeSources(e.sources)}<br>${escapeHtml(e.pkg)}</div>
        </div>
        ${e.installed
          ? `<span class="repo-actions"><button class="btn-primary" disabled>${escapeHtml(t("ext.installed"))}</button><button class="btn-danger" onclick="uninstallExt('${escapeHtml(e.pkg).replace(/'/g, "\\'")}', this)">${escapeHtml(t("ext.uninstall"))}</button></span>`
          : `<button class="btn-primary" onclick="installExt('${escapeHtml(e.apk).replace(/'/g, "\\'")}', this)">${escapeHtml(t("ext.install"))}</button>`}
      </div>`).join("")}
  `).join("");
}

function filterRepo() {
  renderRepo(window.__repo || [], $("#repoSearch")?.value || "", $("#repoLang")?.value || "");
}

async function installExt(apk, btn) {
  btn.disabled = true;
  btn.textContent = t("ext.installing");
  try {
    const res = await fetch(`${api}/extensions/install`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ repoUrl: REPOS[activeRepo] || "", apk }),
    });
    const data = await res.json().catch(() => ({}));
    if (data.ok) {
      btn.textContent = t("ext.installed");
      loadSources();
      loadRepo();
      if (state.currentView === "home") renderHome();
    } else {
      btn.textContent = "Error";
      btn.disabled = false;
      alert(t("ext.installError", data.error || t("common.unknown")));
    }
  } catch (e) {
    btn.textContent = "Error";
    btn.disabled = false;
    alert(t("common.error", e.message));
  }
}

async function uninstallExt(pkg, btn) {
  if (!confirm(t("ext.confirmUninstall", pkg))) return;
  btn.disabled = true;
  btn.textContent = t("ext.uninstalling");
  try {
    const res = await fetch(`${api}/extensions/uninstall`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pkg }),
    });
    const data = await res.json().catch(() => ({}));
    if (data.ok) {
      loadSources();
      loadRepo();
      if (state.currentView === "home") renderHome();
    } else {
      btn.textContent = t("ext.uninstall");
      btn.disabled = false;
      alert(t("ext.uninstallError", data.error || t("common.unknown")));
    }
  } catch (e) {
    btn.textContent = t("ext.uninstall");
    btn.disabled = false;
    alert(t("common.error", e.message));
  }
}

/* ---------------- Global search ---------------- */

function globalSearchGo() {
  const q = $("#globalSearch")?.value.trim();
  if (!q) { goHome(); return; }
  if (state.activeSource) {
    const input = $("#searchInput");
    if (input) input.value = q;
    doSearch();
  } else {
    const first = (state.sources.manga && state.sources.manga[0]) || (state.sources.anime && state.sources.anime[0]);
    if (first) {
      openSource(first);
      const input = $("#searchInput");
      if (input) input.value = q;
      doSearch();
    }
  }
}

/* ---------------- Modal helpers ---------------- */

function openModal() { $("#modal").classList.remove("hidden"); }
function closeModal() { $("#modal").classList.add("hidden"); }

/* ---------------- Update banner ---------------- */

async function checkUpdate() {
  try {
    const d = await getJSON(`${api}/update`);
    if (!d || !d.available) return;
    let banner = document.getElementById("updateBanner");
    if (!banner) {
      banner = document.createElement("div");
      banner.id = "updateBanner";
      banner.className = "update-banner";
      document.body.appendChild(banner);
    }
    const ver = d.latestVersion || "";
    const hint = d.downloaded
      ? `<span class="ub-hint">${escapeHtml(t("update.restart"))}</span>`
      : (d.url ? `<a class="ub-hint" href="${escapeHtml(d.url)}" target="_blank">${escapeHtml(t("update.openUrl"))}</a>` : "");
    banner.innerHTML =
      `<span class="ub-icon">⬆</span>` +
      `<span class="ub-text"><b>${escapeHtml(t("update.title"))}</b> ${escapeHtml(t("update.msg", ver))} ${hint}</span>` +
      `<button class="ub-close" onclick="dismissUpdate()">${escapeHtml(t("update.dismiss"))}</button>`;
  } catch (e) { }
}

function dismissUpdate() {
  const b = document.getElementById("updateBanner");
  if (b) b.remove();
}

/* ---------------- Boot ---------------- */

(async function boot() {
  handleAniListHash();
  await loadI18n(currentLang());
  const sel = $("#langSelect");
  if (sel) sel.value = currentLang();
  const contentEl = $("#content");
  if (contentEl) contentEl.addEventListener("scroll", onContentScroll);
  checkUpdate();
  try {
    await loadSources();
    setTypeFilter("all");
    renderHome();
  } catch (e) {
    $("#srcList").innerHTML = `<div class="error">${escapeHtml(t("api.error", e.message))}</div>`;
  }
})();
