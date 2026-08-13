#!/usr/bin/env node
/*
 * Test for the "Continue watching" de-duplication logic in app.js.
 *
 * The WebUI script is a plain browser script (no module exports), so the pure
 * helpers `watchEntryKey` and `dedupeWatchByAnime` are extracted from the
 * source and evaluated in isolation, then exercised against fixture data.
 *
 * Run: node scripts/test-watch-dedup.js
 */
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

const APP_JS = path.join(__dirname, "..", "server", "src", "main", "resources", "webui", "app.js");
const src = fs.readFileSync(APP_JS, "utf8");

// Extract a top-level `function name(...) { ... }` from a JS source string,
// balancing braces so nested blocks/template literals are handled correctly.
// Returns { params, body } so the caller can rebuild the function with any
// dependency injection it needs (new Function creates global-scope functions).
function extractFn(source, name) {
  const re = new RegExp(`function\\s+${name}\\s*\\(([^)]*)\\)\\s*\\{`);
  const m = re.exec(source);
  if (!m) throw new Error(`function ${name} not found in ${path.basename(APP_JS)}`);
  const params = m[1];
  let i = source.indexOf("{", m.index);
  let depth = 0;
  let start = i;
  for (; i < source.length; i++) {
    const ch = source[i];
    if (ch === "{") depth++;
    else if (ch === "}") {
      depth--;
      if (depth === 0) break;
    }
  }
  const body = source.slice(start + 1, i);
  return { params, body };
}

const watchEntryKeySrc = extractFn(src, "watchEntryKey");
const watchEntryKey = new Function(watchEntryKeySrc.params, watchEntryKeySrc.body);
const dedupeSrc = extractFn(src, "dedupeWatchByAnime");
// dedupeWatchByAnime references watchEntryKey from its enclosing scope; inject
// it as a leading bound argument since new Function cannot capture closures.
const dedupeWatchByAnime = new Function("watchEntryKey", dedupeSrc.params, dedupeSrc.body).bind(null, watchEntryKey);

// Helpers to build fixtures
const wk = (sourceId, animeUrl, epUrl, updatedAt) => ({ sourceId, animeUrl, epUrl, updatedAt });

test("keeps only the most recent episode per anime", () => {
  const list = [
    wk("src1", "https://ex/anime/one", "/ep/3", 3000),
    wk("src1", "https://ex/anime/one", "/ep/1", 1000),
    wk("src1", "https://ex/anime/one", "/ep/2", 2000),
  ];
  const out = dedupeWatchByAnime(list);
  assert.strictEqual(out.length, 1);
  assert.strictEqual(out[0].epUrl, "/ep/3"); // most recent first
});

test("keeps different anime separate", () => {
  const list = [
    wk("src1", "https://ex/anime/one", "/ep/1", 1000),
    wk("src1", "https://ex/anime/two", "/ep/1", 2000),
  ];
  const out = dedupeWatchByAnime(list);
  assert.strictEqual(out.length, 2);
});

test("merges trailing-slash URL variations of the same anime", () => {
  const list = [
    wk("src1", "https://ex/anime/one", "/ep/1", 1000),
    wk("src1", "https://ex/anime/one/", "/ep/2", 2000),
  ];
  const out = dedupeWatchByAnime(list);
  assert.strictEqual(out.length, 1);
  assert.strictEqual(out[0].epUrl, "/ep/1"); // first (already sorted most-recent-first)
});

test("keeps the same anime from different sources separate", () => {
  const list = [
    wk("src1", "https://ex/anime/one", "/ep/1", 1000),
    wk("src2", "https://ex/anime/one", "/ep/1", 2000),
  ];
  const out = dedupeWatchByAnime(list);
  assert.strictEqual(out.length, 2);
});

test("handles empty and nullish input", () => {
  assert.deepStrictEqual(dedupeWatchByAnime([]), []);
  assert.deepStrictEqual(dedupeWatchByAnime(null), []);
});

test("caps the result at max", () => {
  const list = [];
  for (let i = 1; i <= 20; i++) list.push(wk(`src${i}`, `https://ex/anime/${i}`, `/ep/1`, i));
  const out = dedupeWatchByAnime(list, 12);
  assert.strictEqual(out.length, 12);
  assert.strictEqual(out[0].animeUrl, "https://ex/anime/1");
});

test("listWatch is wired to dedupeWatchByAnime", () => {
  assert.match(src, /async function listWatch\(\)\s*\{\s*const list = await loadWatchCache\(\);[\s\S]*?return dedupeWatchByAnime\(/);
});
