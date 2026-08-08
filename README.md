# miwayomi

A lightweight server (Ktor, JVM 21) that runs **catalog extensions in the Tachiyomi/Aniyomi format** (APK) on a JVM without Android, exposing them as a **REST API + WebUI**.

> miwayomi is an **execution engine**: it loads extensions in the Tachiyomi/Aniyomi format and serves their sources through an API. **It does not distribute, host, or recommend any content or specific sources.** Each extension is third-party software and is responsible for what it does; miwayomi only runs them and exposes their catalog interface.

## Documentation

Full documentation — the origin story, architecture, file-by-file code map,
technical requirements, REST API, streaming internals, and a guide to writing
your **own** compatible extension — lives in:

- **`docs/index.md`** — the complete markdown reference.
- **`/docs.html`** — the same documentation as a web page, served by the app
  (open the running server and click **Docs** in the top bar). It includes
  screenshots, an animated UI tour (GIF), and a section on customizing the UI.

## What it does

- Loads **APK extensions from the Tachiyomi/Aniyomi ecosystem** on the JVM (dex → jar with `dex2jar`, classes loaded with a child-first `ClassLoader`).
- Provides a minimal Android shim (`android-compat` + `android.jar` stub) so that the `android.*`/`androidx.*` classes referenced by extensions exist without a real Android system.
- REST API: catalog, search, details, chapters/episodes, pages, and image/video proxy with the headers each source requires.
- WebUI (manga + anime): extension manager, source settings, and favorites.
- Local persistence in SQLite (cookies, resolved hosts, favorites).
- **Multilingual WebUI**: one JSON file per language in `lang/` (add a file to add a language; see `lang/README.md`).
- Includes an offline built-in demo source (no network, no configuration) for testing the pipeline.

## How extensions work inside miwayomi

Tachiyomi/Aniyomi extensions are **APK files** that declare their catalog classes in the manifest `meta-data` (`tachiyomi.extension.class` / `tachiyomi.animeextension.class`). miwayomi runs them like this:

1. **Manifest**: `apk-parser` reads the manifest XML and extracts the declared classes.
2. **DEX → JAR**: `dex2jar` converts `classes.dex` to a `.jar`; a post-process fixes the bytecode so it is valid on the JVM (stackmap frames, constructors, phantom classes).
3. **Loading**: a child-first `ClassLoader` loads the classes.
4. **Instantiation**: the source factories (`SourceFactory`/`AnimeSourceFactory`) or direct sources are instantiated and registered in the manga/anime managers.
5. **API**: the catalog, details, chapters/episodes, pages, and video endpoints delegate to each loaded source.

This mechanism is **generic**: it works for any extension in that format, regardless of who distributes it or what content it handles.

## Status

- ✅ `source-api` of Tachiyomi/Aniyomi (manga + anime) compiled as a JVM module.
- ✅ `core-common` and `android-compat` adapted to JVM (includes `ContextWrapper`, `org.json`, `androidx.preference`, WebView/Handler/Looper stubs, and GraalJS).
- ✅ Loads APK extensions (dex→jar) and exposes their sources through the REST API, with a bytecode fixer for the converted jars.
- ✅ **Manga**: catalog, search, details, chapters, pages, and image proxy.
- ✅ **Anime**: catalog, details, episodes, video extraction, and **playback of every format**: HLS (hls.js + `/hls` proxy), **DASH .mpd** (dash.js + `/dash`/`/dashseg` proxy with manifest rewriting), and direct MP4/WebM.
- ✅ **Chunked streaming** (does not load large files into RAM) and **full MIME support** (video, audio, subtitles, playlists, and images) even when the CDN sends `application/octet-stream`.
- ✅ **Manual Cloudflare resolution**: WebUI modal with a headless browser (CDP): live screenshots, clicks/keys, cookie capture (`cf_clearance`) and fast reuse. Works with any source behind Cloudflare.
- ✅ FlareSolverr: full integration (client `/v1` + interceptor). Optional: if absent, the manual modal still appears.
- ✅ **SQLite persistence**: Cloudflare cookies and resolved hosts survive restarts; also favorites and reading progress.
- ✅ **Source settings via API**: exposes the preferences each extension declares (language, quality, domain, etc.).
- ✅ **Favorites and tracking**: add/remove titles and remember the last read chapter.
- ✅ **Extension manager** in the WebUI: install/uninstall from any repository with the format index, with status and per-extension grouping.
- ✅ Fixed: the `details` 500 when an extension returned a title without `url`, and the "No videos" caused by a double-encoded JSON body (generic interceptor).
- ⏳ Pending: fuller WebUI (more views), per-source JS engines, torrent streaming.

## Structure

```
miwayomi/
├── android-compat/   # Android shim (Context, SharedPreferences, Uri, Log, Bitmap, Base64, Html,
│                     #   QuickJs/Duktape via GraalJS, androidx.preference stub) + android.jar stub
├── core-common/      # Network (NetworkHelper, OkHttp helpers, interceptors), coroutines, preferences,
│                     #   logcat, JavaScriptEngine (GraalJS), torrents stub
├── source-api/       # Aniyomi source API ported to JVM (MangaSource/HttpSource + AnimeSource/AnimeHttpSource)
├── server/           # Ktor app: extension loading, source managers, REST API, proxy, WebUI
└── data/             # runtime data: data/extensions/*.apk (+ converted .jar), prefs, cache
```

## Requirements

- JDK 21 (Temurin recommended). Gradle downloads itself (wrapper).
- To solve Cloudflare challenges manually you need Chrome/Chromium
  (the one bundled with FlareSolverr in `flaresolverr/`, or `--chrome <path>`).

## How to start the server

### Runnable JAR (any OS) — easiest

Download the cross-platform JAR from the
[Releases](https://github.com/miwayomi/miwayomi/releases) page (you only need
**JDK 21**).

```bash
# Linux / macOS
java -jar miwayomi-all.jar --data ./data --port 4567

# Windows (PowerShell / CMD)
java -jar miwayomi-all.jar --data %cd%\data --port 4567
```

To build the same JAR locally instead of downloading:

```bash
./gradlew :server:shadowJar    # generates server/build/libs/miwayomi-all.jar
java -jar server/build/libs/miwayomi-all.jar --data ./data --port 4567
```

FlareSolverr is **enabled by default** at `http://127.0.0.1:8191`; if it is not
running, Cloudflare challenges fall back to the manual modal. Open
`http://localhost:4567`.

### Lightweight mode (production / VPS) — recommended

Builds an **installed distribution** (a single `.jar` with all libraries) and runs a
single `java` process **without Gradle/Kotlin daemons**. Designed for 4 GB VPS.

```bash
cd /home/asking/Escritorio/miwayomi
./gradlew :server:installDist     # generates server/build/install/server/
./gradlew --stop                  # frees the Gradle daemons (RAM)

# Start (uses the distribution if it exists; adjust RAM with MIWAYOMI_MEM)
./start.sh
```

The JVM starts with a small heap and `SerialGC` (the lightest): `-Xmx512m -Xms64m
-XX:MaxMetaspaceSize=256m -XX:+UseSerialGC` (measured: ~160-170 MB RSS at rest).
To change the RAM: `MIWAYOMI_MEM="-Xmx768m" ./start.sh`.

### Quick option (1 command, dev mode)

```bash
cd /home/asking/Escritorio/miwayomi
./start.sh
```

This starts **FlareSolverr** in the background (if available in `/tmp/flaresolverr-src`)
and **miwayomi**, leaving logs at `/tmp/miwayomi.log` and `/tmp/fs_src.log`.
`start.sh` prefers the runnable **fat jar** (`server/build/libs/miwayomi-all.jar`, a
`miwayomi-all.jar` in the folder, or `MIWAYOMI_JAR=/path/to/miwayomi-all.jar`). Without a
JAR it uses the installed distribution (`:server:installDist`); otherwise it falls back to
`gradlew :server:run`.

### Manual option (dev mode)

```bash
# Terminal 1 — FlareSolverr (optional, auto-solve of Cloudflare)
cd /tmp/flaresolverr-src && /tmp/fsvenv/bin/python src/flaresolverr.py --port 8191

# Terminal 2 — miwayomi
cd /home/asking/Escritorio/miwayomi
./gradlew :server:run --args="--data /home/asking/Escritorio/miwayomi/data --port 4567 --flaresolverr http://127.0.0.1:8191"
```

> Without FlareSolverr: omit `--flaresolverr http://127.0.0.1:8191`. The manual Cloudflare
> modal still works (it uses the bundled Chrome). If `/tmp` is cleaned, the FlareSolverr
> source is rebuilt with "option C" in the Cloudflare section.

### Verify

```bash
curl http://localhost:4567/api/v1/health        # {"status":"ok",...}
```

Open `http://localhost:4567` (WebUI).

### Shut down

```bash
pkill -f "server:run"; pkill -f "flaresolverr.py"
```

## Cloudflare bypass

Some sources are behind the Cloudflare anti-bot. miwayomi has **two ways** to unlock them:

1. **Manual resolution (recommended, always works)**: the WebUI opens a modal with the live challenge;
   you solve the captcha by hand and miwayomi captures the cookies. Requires Chrome/Chromium
   (the one bundled with FlareSolverr, or `--chrome <path>`).
2. **FlareSolverr (optional)**: tries to solve the challenge automatically with a headless browser.
   If it is not configured or fails to solve, the manual way is used.

> Chrome for the modal: miwayomi looks for Chrome in `CHROME_PATH`, `--chrome <path>`, or the usual
> locations (`/tmp/flaresolverr/_internal/chrome/chrome`, `google-chrome`, `chromium`).

### 1. (Optional) Run FlareSolverr — option A: Docker

```bash
docker run -p 8191:8191 ghcr.io/flaresolverr/flaresolverr:latest
```

### 1'. (Optional) Run FlareSolverr — option B: binary, no Docker

Download the binary from the latest release (`flaresolverr_linux_x64.tar.gz`) and run it.
On Linux it needs `xvfb`:

```bash
sudo pacman -S --noconfirm xorg-xvfb        # Arch / EndeavourOS
# or: sudo apt install xvfb                 # Debian / Ubuntu

cd /tmp/flaresolverr && ./flaresolverr --port 8191
```

> Note: if you use the binary and `/bin/sh` fails with `rl_trim_arg_from_keyseq`, move the
> `libreadline.so.8` shipped in the bundle out of the way (`mv _internal/libreadline.so.8 _internal/libreadline.so.8.bak`).

### 1''. Run FlareSolverr (option C: from source, WITHOUT Xvfb or sudo)

If you have neither `sudo` nor `docker`, you can run FlareSolverr from its source using
Chrome in real headless mode (`--headless=new`, no display required):

```bash
# 1. Download the FlareSolverr binary (ships Chrome 142 in _internal/chrome)
curl -sL -o /tmp/fs.tar.gz https://github.com/FlareSolverr/FlareSolverr/releases/download/v3.5.0/flaresolverr_linux_x64.tar.gz
tar xzf /tmp/fs.tar.gz -C /tmp/flaresolverr

# 2. Clone the source and prepare the environment
git clone --depth 1 https://github.com/FlareSolverr/FlareSolverr /tmp/flaresolverr-src
ln -sfn /tmp/flaresolverr/_internal/chrome /tmp/flaresolverr-src/src/chrome
python3 -m venv /tmp/fsvenv
/tmp/fsvenv/bin/pip install -r /tmp/flaresolverr-src/requirements.txt

# 3. Patch utils.py to avoid Xvfb (2 changes):
#    - start_xvfb_display() -> return  (no-op)
#    - in get_webdriver: in the Linux else, add options.add_argument('--headless=new')

# 4. Start
/tmp/fsvenv/bin/python /tmp/flaresolverr-src/src/flaresolverr.py --port 8191
```

### 2. Start miwayomi pointing to FlareSolverr (optional)

```bash
./gradlew :server:run --args="--data /home/asking/Escritorio/miwayomi/data --port 4567 --flaresolverr http://localhost:8191"
```

> Without `--flaresolverr` it also works: on a challenge, the WebUI opens the manual modal directly.

How it works internally:

1. `CloudflareInterceptor` detects a challenge (code 403/429/503 + Cloudflare headers or a "Just a moment" body).
2. If FlareSolverr is configured, it tries to solve it automatically (with a ~20 s limit).
3. If it fails (or there is no FlareSolverr), it returns `challengeUrl` in the error and the WebUI opens a **manual resolution modal**:
   the server launches its own headless Chrome (CDP), shows live screenshots, forwards your
   clicks/keys, and when you press "I've solved it" it **captures the cookies** (including HttpOnly ones
   like `cf_clearance`) and stores them in the cookie jar. From then on, requests pass without a challenge.

> **Key detail**: Cloudflare binds `cf_clearance` to the User-Agent that solved the challenge. That is why the
> browser solves with the Chrome UA and, after saving the cookies, the server forces that same UA
> on requests to that host (`CfResolvedUa`) so they pass without being re-challenged.

> **Known limitations**: some Cloudflare challenges (Turnstile/interactive captcha) do not
> auto-solve in headless Chrome; the manual modal solves them. Some extensions ship their own
> interceptor that creates a WebView to solve the challenge; with the no-op stub that flow can
> be slow/hang — the reliable path is the manual modal (which uses the real browser).

## REST API (v1)

| Endpoint | Description |
|---|---|
| `GET /api/v1/health` | Server status |
| `GET /api/v1/sources` | Loaded manga and anime sources |
| `GET /api/v1/manga/{id}/popular?page=N` | Popular catalog (manga) |
| `GET /api/v1/manga/{id}/search?query=...&page=N` | Search (manga) |
| `GET /api/v1/manga/{id}/details?url=...` | Title details |
| `GET /api/v1/manga/{id}/chapters?url=...` | Chapters of a title |
| `GET /api/v1/manga/{id}/pages?url=...` | Pages of a chapter |
| `GET /api/v1/anime/{id}/popular` · `search` · `details` · `episodes` · `seasons` · `hosters` · `videos` | Anime flow |
| `GET /api/v1/proxy?sourceId={id}&url=...&headers=...` | Image/video proxy with the source headers (inferred MIME) |
| `GET /api/v1/hls?sourceId={id}&url=...&headers=...` | HLS proxy: rewrites the manifest and routes segments with headers (playback) |
| `GET /api/v1/dash?sourceId={id}&url=...&headers=...` | DASH proxy: rewrites the `.mpd` and routes segments (`.mpd` playback) |
| `GET /api/v1/dashseg?base=...&rel=...` | DASH segment served by the proxy (with `$Number$` templates intact) |
| `GET /api/v1/cf/start?url=...` · `cf/shot` · `cf/url` | Manual Cloudflare resolution (headless browser + CDP) |
| `POST /api/v1/cf/click` `{x,y}` · `cf/key` `{key}` · `cf/finish` | Clicks/keys to the browser and cookie saving on solve |
| `GET /api/v1/extensions/repo?url=<index>` | List extensions from a repository (format index) |
| `POST /api/v1/extensions/install` `{repoUrl, apk}` · `POST /api/v1/extensions/uninstall` `{pkg}` | Install / uninstall an extension |
| `GET /api/v1/sources/{id}/prefs` · `POST /api/v1/sources/{id}/prefs` | Read / save source preferences |
| `GET /api/v1/favorites` · `POST /api/v1/favorites` · `DELETE /api/v1/favorites?sourceId=&url=` | Favorites |
| `GET /api/v1/favorites/check?sourceId=&url=` · `POST /api/v1/favorites/progress` | Favorite status and reading progress |

## How it works (architecture summary)

1. **AndroidCompat**: extensions reference `android.*` and `androidx.preference.*` classes. `android-compat` implements the ones that matter (SharedPreferences, Uri, Log, Context...) and an `android.jar` stub (API 30) covers the rest. A GraalJS engine replaces QuickJS/Duktape for anime extractors.
2. **source-api ported to JVM**: the source API module (originally an Android target) is adapted as pure JVM, replacing the Android `expect`s with real classes.
3. **Extension loading**: `dex2jar` converts the APK to a jar, a post-process fixes the bytecode for JVM, `ChildFirstURLClassLoader` loads it, and `SourceFactory`/`AnimeSourceFactory` instantiate the sources, which are registered in the managers.
4. **Server**: Ktor + REST endpoints + proxy with the headers the source requires (Referer/UA).

## Notes and known limitations

- Some **anime** sources depend on WebView/native JS to extract the video; GraalJS covers the `JavaScriptEngine`/QuickJs/Duktape API, but the ones using a real WebView need a headless browser.
- The streaming proxy works in chunks (without loading the whole file into RAM) and rewrites the HLS/DASH manifests for playback.
- Torrent streaming (native Android) is not supported (stub that throws "disabled").
- An extension may fail to load if its APK bundles libraries that collide with the runtime ones, or if its bytecode has patterns the fixer does not cover; in that case the error is reported and the rest keeps working.

## Translations

The WebUI is translated with one JSON file per language in `server/src/main/resources/webui/lang/`
(`en.json`, `es.json`, ...). To add a language, copy `en.json` to `<code>.json`, translate the values,
register it in the `<select id="langSelect">` of `index.html`, and rebuild. See `lang/README.md`.

## Attribution

See [NOTICE-ANIYOMI.md](NOTICE-ANIYOMI.md): `source-api` and `core-common` are adaptations of Aniyomi (Apache-2.0). The rest is new miwayomi code.

## Contributing

Contributions are welcome. By contributing, you agree to follow the
[Code of Conduct](CODE_OF_CONDUCT.md) and the project's rules on attribution,
disclaimers, and third-party rights (see the [Legal disclaimer](#legal-disclaimer)).
Do not submit or redistribute content or extensions you do not own.

## Legal disclaimer

miwayomi is an **execution engine**, not a content service. Please read this carefully.

**No content, no distribution.** miwayomi does not create, host, store, index, link to, distribute, or recommend any content, media, source, or extension repository. It ships no content itself. The built-in demo source is fully offline and returns no real data.

**Third-party extensions.** Extensions in the Tachiyomi/Aniyomi format are third-party software, written, maintained, and distributed by their own authors. miwayomi merely loads and runs them at the request of the user and exposes their catalog interface. The behavior of each extension and the sites it interacts with are the sole responsibility of that extension's author and of the user who installs it.

**User responsibility.** You are solely responsible for:

- the extensions you install and the content you access,
- complying with the laws of your jurisdiction and the terms of service of any website or service you interact with,
- not using this software to infringe copyright, circumvent access controls you are not authorized to bypass, or redistribute content you do not have rights to.

**No affiliation.** miwayomi is not affiliated with, endorsed by, sponsored by, or a fork of Tachiyomi, Aniyomi, or any related project. Its design is inspired by the Tachiyomi/Aniyomi open-source ecosystem, and portions of their source APIs are reused under the Apache-2.0 license — see `NOTICE-ANIYOMI.md` for attribution.

**Trademarks and content.** All product names, logos, brands, cover art, titles, and other marks that appear in this project or in its screenshots belong to their respective owners and are used only for identification or illustration purposes.

**No warranty.** This software is provided "as is", without warranty of any kind, express or implied. The author(s) are not liable for any damages arising from the use of this software.

**If you use this software, you do so at your own risk and under your own responsibility.**
