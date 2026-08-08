const $ = (sel) => document.querySelector(sel);
const api = "/api/v1";

let state = {
  sources: { manga: [], anime: [] },
  activeSource: null,
  detail: null,
  typeFilter: "all",
  currentView: "home",
};

let cfRetry = null;
let cfTimer = null;

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
    cfRetry = () => getJSON(url);
    openCfModal(body.challengeUrl, body.challengeUserAgent);
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

async function loadGrid(url, title) {
  const box = $("#catalog");
  box.innerHTML = `<div class="loading">${escapeHtml(t("common.loading"))}</div>`;
  try {
    const data = await getJSON(url);
    const list = data.mangas || data.animes || [];
    if (list.length === 0) { box.innerHTML = `<div class="empty">${escapeHtml(t("common.noResults"))}</div>`; return; }
    const type = state.activeSource.type;
    const cards = list.map((m) => `
      <div class="card" onclick="openEntry('${state.activeSource.id}','${type}','${encodeURIComponent(m.url)}')">
        ${m.thumbnail_url ? `<img loading="lazy" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(m.thumbnail_url)}">` : `<div class="card-img-placeholder">${escapeHtml((m.title || "?").charAt(0).toUpperCase())}</div>`}
        <div class="card-title">${escapeHtml(m.title)}</div>
      </div>`).join("");
    box.innerHTML = `<div class="row-title">${escapeHtml(title)}</div><div class="row-scroll">${cards}</div>`;
  } catch (e) {
    box.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
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
    const epThumb = (e) => {
      if (e.preview_url) return `<img class="ep-thumb-img ready" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(e.preview_url)}">`;
      if (d.thumbnail_url) return `<img class="ep-thumb-img ready" src="/api/v1/proxy?sourceId=${state.activeSource.id}&url=${encodeURIComponent(d.thumbnail_url)}">`;
      return "";
    };
    const items = state.episodes.map((e, i) => `
      <div class="ep-card" onclick="openEpisode('${encodeURIComponent(e.url)}','${escapeHtml(e.name)}')">
        <div class="ep-thumb">
          <span class="ep-num">E${e.episode_number ?? i + 1}</span>
          ${epThumb(e)}
        </div>
        <div class="ep-name">${escapeHtml(e.name)}</div>
      </div>`).join("");
    $("#entryList").innerHTML = `<div class="list-title">${escapeHtml(t("detail.episodes"))}</div>` +
      (items ? `<div class="ep-grid">${items}</div>` : `<div class="empty">${escapeHtml(t("detail.noEpisodes"))}</div>`);
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

/* ---------------- Player ---------------- */

async function openEpisode(encUrl, name, noPush) {
  state.currentView = "player";
  if (!noPush) navPush(() => { if (state.detail) renderDetail(state.detail.type, state.detailObj); });
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
          </div>
        </div>
        <div class="player-episodes" id="playerEpisodes"></div>
      </div>`;

    renderQuality(videos);
    renderPlayerEpisodes(eps, encUrl);
    if (videos.length === 1) playVideo(0);
  } catch (e) {
    main.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function renderQuality(videos) {
  const wrap = $("#qualityBtns");
  if (!wrap) return;
  wrap.innerHTML = videos.map((v, i) =>
    `<button style="margin-left:6px" onclick="playVideo(${i})">${escapeHtml(v.resolution ? v.resolution + "p" : (v.videoTitle || i + 1))}</button>`).join("");
}

function renderPlayerEpisodes(eps, currentEnc) {
  const box = $("#playerEpisodes");
  if (!box) return;
  box.innerHTML = `<div class="list-title">${escapeHtml(t("detail.episodes"))}</div>` +
    (eps.length ? eps.map((e, i) => `
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
    <div class="pref-actions">
      <button class="btn-primary" onclick="closeModal()">${escapeHtml(t("common.close"))}</button>
    </div>`;
  openModal();
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

/* ---------------- Extensions (multi-repo) ---------------- */

const DEFAULT_REPO = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json";
let REPOS = [];
let activeRepo = 0;

function loadRepos() {
  try { REPOS = JSON.parse(localStorage.getItem("miwayomi.repos") || "[]"); } catch (e) { REPOS = []; }
  if (!Array.isArray(REPOS) || REPOS.length === 0) REPOS = [DEFAULT_REPO];
  if (activeRepo >= REPOS.length) activeRepo = 0;
}
function saveRepos() { localStorage.setItem("miwayomi.repos", JSON.stringify(REPOS)); }
function shortRepo(url) {
  try { return new URL(url).hostname.replace(/^www\./, ""); } catch (e) { return url; }
}

function openExtensions() {
  loadRepos();
  activeRepo = 0;
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
function confirmAddRepo() {
  const input = $("#repoUrl");
  const url = (input && input.value.trim()) || "";
  if (!url) return;
  REPOS.push(url);
  saveRepos();
  activeRepo = REPOS.length - 1;
  renderExtensionsModal();
  loadRepo();
}
function removeRepo(i) {
  REPOS.splice(i, 1);
  saveRepos();
  if (activeRepo >= REPOS.length) activeRepo = REPOS.length - 1;
  renderExtensionsModal();
  loadRepo();
}

async function loadRepo() {
  const url = REPOS[activeRepo] || DEFAULT_REPO;
  const list = $("#repoList");
  const prevScroll = list ? list.scrollTop : 0;
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
      body: JSON.stringify({ repoUrl: REPOS[activeRepo] || DEFAULT_REPO, apk }),
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

/* ---------------- Cloudflare modal ---------------- */

async function openCfModal(url, ua) {
  $("#cfModal").classList.remove("hidden");
  const stage = $("#cfStage");
  stage.innerHTML = `<div class="cf-loading">${escapeHtml(t("cf.preparing"))}</div>`;
  try {
    const res = await fetch(`${api}/cf/start?url=${encodeURIComponent(url)}${ua ? `&ua=${encodeURIComponent(ua)}` : ""}`);
    if (!res.ok) {
      const b = await res.json().catch(() => ({}));
      stage.innerHTML = `<div class="error">${escapeHtml(t("cf.browserError", b.error || res.status))}</div>`;
      return;
    }
    stage.innerHTML = `<img id="cfShot" class="cf-shot" alt="Captura del reto">`;
    const img = $("#cfShot");
    img.addEventListener("click", cfClick);
    startCfPolling();
  } catch (e) {
    stage.innerHTML = `<div class="error">${escapeHtml(t("common.error", e.message))}</div>`;
  }
}

function startCfPolling() {
  stopCfPolling();
  cfTimer = setInterval(async () => {
    const shot = $("#cfShot");
    try {
      const u = await fetch(`${api}/cf/url`);
      if (u.ok) {
        const d = await u.json();
        const el = $("#cfUrl");
        if (el && d.url) el.textContent = t("cf.urlPrefix") + d.url;
      }
    } catch (e) { }
    if (!shot) return;
    try {
      const res = await fetch(`${api}/cf/shot`);
      if (!res.ok) return;
      const blob = await res.blob();
      if (!blob || blob.size < 100) return;
      const oldSrc = shot.src;
      shot.src = URL.createObjectURL(blob);
      if (oldSrc && oldSrc.startsWith("blob:")) URL.revokeObjectURL(oldSrc);
    } catch (e) { }
  }, 1100);
}

function stopCfPolling() {
  if (cfTimer) { clearInterval(cfTimer); cfTimer = null; }
}

async function cfClick(e) {
  const img = $("#cfShot");
  if (!img || !img.naturalWidth) return;
  const r = img.getBoundingClientRect();
  const x = Math.round((e.clientX - r.left) * (img.naturalWidth / r.width));
  const y = Math.round((e.clientY - r.top) * (img.naturalHeight / r.height));
  try {
    await fetch(`${api}/cf/click`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ x, y }),
    });
  } catch (e) { }
}

async function cfKey(key) {
  try {
    await fetch(`${api}/cf/key`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ key }),
    });
  } catch (e) { }
}

async function cfFinish() {
  stopCfPolling();
  $("#cfModal").classList.add("hidden");
  const retry = cfRetry;
  cfRetry = null;
  try {
    const res = await fetch(`${api}/cf/finish`, { method: "POST" });
    const b = await res.json().catch(() => ({}));
    console.log(t("cf.cookiesSaved", b.count));
  } catch (e) { }
  if (retry) retry().catch((e) => showError(e.message));
}

function cfClose() {
  stopCfPolling();
  $("#cfModal").classList.add("hidden");
  cfRetry = null;
}

function showError(msg) {
  const box = $("#catalog");
  if (box) box.innerHTML = `<div class="error">${escapeHtml(msg)}</div>`;
}

/* ---------------- Modal helpers ---------------- */

function openModal() { $("#modal").classList.remove("hidden"); }
function closeModal() { $("#modal").classList.add("hidden"); }

/* ---------------- Boot ---------------- */

(async function boot() {
  await loadI18n(currentLang());
  const sel = $("#langSelect");
  if (sel) sel.value = currentLang();
  try {
    await loadSources();
    setTypeFilter("all");
    renderHome();
  } catch (e) {
    $("#srcList").innerHTML = `<div class="error">${escapeHtml(t("api.error", e.message))}</div>`;
  }
})();
