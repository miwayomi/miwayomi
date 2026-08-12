<div align="center">

# 🚀 miwayomi

**Run Tachiyomi/Aniyomi catalog extensions on your PC / Mac / Linux — no Android emulator, no phone.**

A lightweight server (Ktor, **JVM 21**) that loads **catalog extensions in the Tachiyomi/Aniyomi format** (APK) directly on your computer and exposes them as a **REST API + WebUI** — manga **and** anime, with HLS/DASH streaming, a manual Cloudflare bypass, and a multilingual interface.

<p align="center">
  <a href="https://github.com/miwayomi/miwayomi/releases"><img alt="GitHub release" src="https://img.shields.io/github/v/release/miwayomi/miwayomi?color=blue&label=release"></a>
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/License-Apache%202.0-blue"></a>
  <img alt="Java" src="https://img.shields.io/badge/Java-21-orange">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-purple">
  <a href="https://github.com/miwayomi/miwayomi/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/miwayomi/miwayomi"></a>
  <a href="https://github.com/miwayomi/miwayomi/forks"><img alt="GitHub forks" src="https://img.shields.io/github/forks/miwayomi/miwayomi"></a>
  <a href="https://github.com/miwayomi/miwayomi/issues"><img alt="GitHub issues" src="https://img.shields.io/github/issues/miwayomi/miwayomi"></a>
  <a href="https://github.com/miwayomi/miwayomi/pulls"><img alt="GitHub pull requests" src="https://img.shields.io/github/issues-pr/miwayomi/miwayomi"></a>
  <a href="https://github.com/miwayomi/miwayomi/graphs/contributors"><img alt="GitHub contributors" src="https://img.shields.io/github/contributors/miwayomi/miwayomi"></a>
</p>

> **⚠️ Legal note:** miwayomi is an **execution engine**. It does not distribute, host, or recommend any content or specific sources. Each extension is third-party software and is responsible for what it does; miwayomi only runs them and exposes their catalog interface. See the full [Legal disclaimer](#legal-disclaimer).

</div>

---

## Table of contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
  - [1. Desktop app (recommended)](#1-desktop-app-recommended)
  - [2. Plain JAR (any OS)](#2-plain-jar-any-os)
  - [3. Headless server (production / VPS)](#3-headless-server-production--vps)
  - [Windows installer](#windows-installer)
  - [Build from source](#build-from-source)
- [Usage](#usage)
  - [CLI options](#cli-options)
  - [Auto-update](#auto-update)
  - [Verify and shut down](#verify-and-shut-down)
- [Cloudflare bypass](#cloudflare-bypass)
- [REST API](#rest-api-v1)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Status & roadmap](#status--roadmap)
- [Documentation](#documentation)
- [Translations](#translations)
- [Contributing](#contributing)
- [Security](#security)
- [Attribution](#attribution)
- [Legal disclaimer](#legal-disclaimer)
- [License](#license)

---

## Features

- **Loads APK extensions from the Tachiyomi/Aniyomi ecosystem** on the JVM (dex → jar with `dex2jar`, classes loaded with a child-first `ClassLoader`). Converted bytecode is **auto-repaired on first load**, and extensions whose repository publishes a clean desktop **JVM jar** use it directly instead of being converted.
- **Manga**: catalog, search, details, chapters, pages, and image proxy.
- **Anime**: catalog, details, episodes, video extraction, and **playback of every format**: HLS (hls.js + `/hls` proxy), **DASH .mpd** (dash.js + `/dash`/`/dashseg` proxy with manifest rewriting), and direct MP4/WebM.
- **Chunked streaming** (does not load large files into RAM) and **full MIME support** (video, audio, subtitles, playlists, and images) even when the CDN sends `application/octet-stream`.
- **Manual Cloudflare resolution**: WebUI modal with a headless browser (CDP): live screenshots, clicks/keys, cookie capture (`cf_clearance`) and fast reuse.
- **FlareSolverr** integration (client `/v1` + interceptor), optional.
- **SQLite persistence**: Cloudflare cookies and resolved hosts survive restarts; also favorites, reading progress, anime watch history, installed extensions, and your repository URLs.
- **Source settings via API**: exposes the preferences each extension declares (language, quality, domain, ...).
- **Anime player with history**: invert episode order (newest/oldest), auto-play the next episode, auto-select the best video source, and a **"Continue watching"** home row that resumes exactly where you left off (progress stored in SQLite).
- **AniList sync (optional)**: connect your own AniList account from Settings to push your watched-episode progress automatically.
- **Extension manager** in the WebUI: install/uninstall from any repository, with status and per-extension grouping; installed extensions and your repository URLs are saved in the database and restored on every start.
- **Multilingual WebUI**: one JSON file per language in `lang/` (add a file to add a language).
- Offline built-in demo source (no network, no configuration) for testing the pipeline.

## Requirements

- **JDK 21** (Temurin recommended). Gradle downloads itself via the wrapper.
- Chrome/Chromium — only if you want the **manual Cloudflare bypass** (the one bundled with FlareSolverr in `flaresolverr/`, or `--chrome <path>`).

## Installation

### 1. Desktop app (recommended)

`./miwayomi` and `miwayomi.bat` start everything with a single command: they pick a **free port**, start the server with all defaults, open a **dedicated app window** (Chrome/Edge/Chromium `--app` — no tabs or URL bar) and stop when you close the window.

```bash
./miwayomi      # macOS / Linux (run or double-click)
miwayomi.bat    # Windows (double-click)
```

### 2. Plain JAR (any OS)

Download the cross-platform JAR from the [Releases](https://github.com/miwayomi/miwayomi/releases) page (you only need **JDK 21**) and run it — the port is optional (it picks a free one) and it opens your default browser with the real URL:

```bash
java -jar miwayomi-all.jar
```

Force a specific port with `--port 4567`; use `--no-open` for headless/server use.

### 3. Headless server (production / VPS)

```bash
cd miwayomi
./gradlew :server:installDist && ./gradlew --stop
./start.sh       # headless, logs in /tmp
```

The JVM runs with a small heap + `SerialGC` (`-Xmx512m -Xms64m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC`, ~160–170 MB RSS at rest). Tune RAM with `MIWAYOMI_MEM="-Xmx768m" ./start.sh`. Dev mode (no install): `./gradlew :server:run --args="--data ./data --port 4567"`.

### Windows installer

Get a native Windows `miwayomi-setup-<version>.exe` in two ways:

**A) Build it locally from Linux/macOS with NSIS** (no Windows machine needed):

```bash
sudo apt install nsis   # Debian / Ubuntu
brew install nsis       # macOS (Homebrew)
yay -S nsis             # Arch / EndeavourOS (the nsis package is in the AUR)

./packaging/build-installer.sh <version>
# → packaging/dist/miwayomi-setup-<version>.exe
```

The installer bundles the JAR + launcher and creates Start Menu / desktop shortcuts. To make it fully standalone (bundles a JRE so the target PC does not need Java installed), pass a Windows JDK zip:

```bash
JRE_ZIP=temurin-21-jdk_windows-x64_bin.zip ./packaging/build-installer.sh <version>
```

**B) Automatically on every release** via GitHub Actions (`.github/workflows/build-installer.yml`): on a tag `v*`, a `windows-latest` runner (WiX pre-installed) builds `miwayomi-<version>.exe` with `jpackage` and attaches it to the release. The installed app opens the dedicated app window (Edge/Chrome `--app`) and stops when the window is closed.

### Build from source

```bash
git clone https://github.com/miwayomi/miwayomi.git
cd miwayomi
./gradlew :server:shadowJar     # builds the fat JAR (server/build/libs/miwayomi-all.jar)
```

## Usage

### CLI options

| Flag | Default | Meaning |
| ---- | ------- | ------- |
| `--port`, `-p` | auto | Listen port; omit for an automatic free port. |
| `--host`, `-h` | `0.0.0.0` | Listen address. |
| `--data`, `-d` | `./data` | Data directory (extensions, prefs, cache). |
| `--flaresolverr`, `-f` | `http://127.0.0.1:8191` | FlareSolverr URL (blank disables). |
| `--chrome` | auto | Chrome/Chromium path for the manual Cloudflare modal. |
| `--no-open` | off | Do not open a browser on start (headless). |

### Auto-update

On startup the server checks GitHub for a newer release. If one exists it **downloads the new JAR** automatically and the WebUI shows a banner ("New version vX.Y.Z available — close and relaunch to apply"). The next launch applies it (the previous JAR is kept as `miwayomi-all.jar.bak`). Status: `GET /api/v1/update`.

### Verify and shut down

```bash
curl http://localhost:4567/api/v1/health   # {"status":"ok",...}
```

Open `http://localhost:4567` (WebUI).

```bash
pkill -f "miwayomi-all.jar"     # or close the app window / stop the launcher
pkill -f "flaresolverr.py"      # optional FlareSolverr
```

## Cloudflare bypass

Some sources are behind the Cloudflare anti-bot. miwayomi has **two ways** to unlock them:

1. **Manual resolution (recommended, always works)**: the WebUI opens a modal with the live challenge; you solve the captcha by hand and miwayomi captures the cookies. Requires Chrome/Chromium (the one bundled with FlareSolverr, or `--chrome <path>`).
2. **FlareSolverr (optional)**: tries to solve the challenge automatically with a headless browser. If it is not configured or fails to solve, the manual way is used.

> Chrome for the modal: miwayomi looks for Chrome in `CHROME_PATH`, `--chrome <path>`, or the usual locations (`/tmp/flaresolverr/_internal/chrome/chrome`, `google-chrome`, `chromium`).

### 1. (Optional) Run FlareSolverr — option A: Docker

```bash
docker run -p 8191:8191 ghcr.io/flaresolverr/flaresolverr:latest
```

### 1'. (Optional) Run FlareSolverr — option B: binary, no Docker

Download the binary from the latest release (`flaresolverr_linux_x64.tar.gz`) and run it. On Linux it needs `xvfb`:

```bash
sudo pacman -S --noconfirm xorg-xvfb        # Arch / EndeavourOS
# or: sudo apt install xvfb                 # Debian / Ubuntu

cd /tmp/flaresolverr && ./flaresolverr --port 8191
```

> Note: if you use the binary and `/bin/sh` fails with `rl_trim_arg_from_keyseq`, move the `libreadline.so.8` shipped in the bundle out of the way (`mv _internal/libreadline.so.8 _internal/libreadline.so.8.bak`).

### 1''. Run FlareSolverr (option C: from source, WITHOUT Xvfb or sudo)

If you have neither `sudo` nor `docker`, you can run FlareSolverr from its source using Chrome in real headless mode (`--headless=new`, no display required):

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
3. If it fails (or there is no FlareSolverr), it returns `challengeUrl` in the error and the WebUI opens a **manual resolution modal**: the server launches its own headless Chrome (CDP), shows live screenshots, forwards your clicks/keys, and when you press "I've solved it" it **captures the cookies** (including HttpOnly ones like `cf_clearance`) and stores them in the cookie jar. From then on, requests pass without a challenge.

> **Key detail**: Cloudflare binds `cf_clearance` to the User-Agent that solved the challenge. That is why the browser solves with the Chrome UA and, after saving the cookies, the server forces that same UA on requests to that host (`CfResolvedUa`) so they pass without being re-challenged.

> **Known limitations**: some Cloudflare challenges (Turnstile/interactive captcha) do not auto-solve in headless Chrome; the manual modal solves them. Some extensions ship their own interceptor that creates a WebView to solve the challenge; with the no-op stub that flow can be slow/hang — the reliable path is the manual modal (which uses the real browser).

## REST API (v1)

| Endpoint | Description |
|---|---|
| `GET /api/v1/health` | Server status |
| `GET /api/v1/sources` | Loaded manga and anime sources |
| `GET /api/v1/update` | Current version, latest GitHub release, update status |
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
| `GET /api/v1/extensions/repo?url=<index>` | List extensions from a repository index (`index.min.json` / `index.pb`) |
| `GET /api/v1/extensions/installed` | List locally installed extensions (restored from the database) |
| `GET /api/v1/extensions/repos` · `POST /api/v1/extensions/repos` `{repos:[...]}` | Load / save the user's repository URLs (persisted in the database) |
| `POST /api/v1/extensions/install` `{repoUrl, apk}` · `POST /api/v1/extensions/uninstall` `{pkg}` | Install / uninstall an extension (registered in the database) |
| `GET /api/v1/sources/{id}/prefs` · `POST /api/v1/sources/{id}/prefs` | Read / save source preferences |
| `GET /api/v1/favorites` · `POST /api/v1/favorites` · `DELETE /api/v1/favorites?sourceId=&url=` | Favorites |
| `GET /api/v1/favorites/check?sourceId=&url=` · `POST /api/v1/favorites/progress` | Favorite status and reading progress |
| `GET /api/v1/watch` | Anime watch history ("Continue watching") |
| `POST /api/v1/watch` | Save/update anime watch progress |
| `DELETE /api/v1/watch?sourceId=&animeUrl=&epUrl=` | Remove a watch-history entry |

## How it works

**How extensions run inside miwayomi**

Tachiyomi/Aniyomi extensions are **APK files** that declare their catalog classes in the manifest `meta-data` (`tachiyomi.extension.class` / `tachiyomi.animeextension.class`). miwayomi runs them like this:

1. **Manifest**: `apk-parser` reads the manifest XML and extracts the declared classes.
2. **Bytecode**: if the repository publishes a desktop **JVM jar** alongside the APK, miwayomi uses it directly (no conversion). Otherwise `dex2jar` converts `classes.dex` to a `.jar`.
3. **Repair**: `JarFixer` automatically fixes any jar on first load — it corrects the invalid `invokespecial <init>` owner that DEX→JVM conversion introduces (which otherwise causes `VerifyError` and HTTP 500 on search) and leaves valid bytecode untouched.
4. **Loading**: a child-first `ClassLoader` loads the classes.
5. **Instantiation**: the source factories (`SourceFactory`/`AnimeSourceFactory`) or direct sources are instantiated and registered in the manga/anime managers.
6. **API**: the catalog, details, chapters/episodes, pages, and video endpoints delegate to each loaded source.
7. **Persistence**: every installed extension is registered in the server's SQLite database (`extensions` table) and restored on startup, so the installed list survives restarts; repository index URLs you add are saved too and come back automatically.

This mechanism is **generic**: it works for any extension in that format, regardless of who distributes it or what content it handles.

**Architecture summary**

1. **AndroidCompat**: extensions reference `android.*` and `androidx.preference.*` classes. `android-compat` implements the ones that matter (SharedPreferences, Uri, Log, Context...) and an `android.jar` stub (API 30) covers the rest. A GraalJS engine replaces QuickJS/Duktape for anime extractors.
2. **source-api ported to JVM**: the source API module (originally an Android target) is adapted as pure JVM, replacing the Android `expect`s with real classes.
3. **Extension loading**: miwayomi uses the repository's desktop JVM jar when one is published (otherwise `dex2jar` converts the APK), `JarFixer` auto-repairs the bytecode on first load, `ChildFirstURLClassLoader` loads it, and `SourceFactory`/`AnimeSourceFactory` instantiate the sources, which are registered in the managers. Installed extensions and your repository URLs are stored in SQLite and restored on every start.
4. **Server**: Ktor + REST endpoints + proxy with the headers the source requires (Referer/UA).

## Project structure

```
miwayomi/
├── android-compat/   # Android shim (Context, SharedPreferences, Uri, Log, Bitmap, Base64, Html,
│                     #   QuickJs/Duktape via GraalJS, androidx.preference stub) + android.jar stub
├── core-common/      # Network (NetworkHelper, OkHttp helpers, interceptors), coroutines, preferences,
│                     #   logcat, JavaScriptEngine (GraalJS), torrents stub
├── source-api/       # Aniyomi source API ported to JVM (MangaSource/HttpSource + AnimeSource/AnimeHttpSource)
├── server/           # Ktor app: extension loading, source managers, REST API, proxy, WebUI
└── data/             # runtime data: data/extensions/*.apk (+ converted or repo JVM jars), prefs, cache
```

## Status & roadmap

- ✅ `source-api` of Tachiyomi/Aniyomi (manga + anime) compiled as a JVM module.
- ✅ `core-common` and `android-compat` adapted to JVM (includes `ContextWrapper`, `org.json`, `androidx.preference`, WebView/Handler/Looper stubs, and GraalJS).
- ✅ Loads APK extensions (dex→jar, auto-repaired on first load) and clean desktop JVM jars (used directly when a repository publishes them), exposed through the REST API.
- ✅ **Manga**: catalog, search, details, chapters, pages, and image proxy.
- ✅ **Anime**: catalog, details, episodes, video extraction, and **playback of every format**: HLS, DASH .mpd, and direct MP4/WebM.
- ✅ **Chunked streaming** and **full MIME support**.
- ✅ **Manual Cloudflare resolution**: WebUI modal with a headless browser (CDP).
- ✅ **FlareSolverr** integration, optional.
- ✅ **SQLite persistence**: Cloudflare cookies, resolved hosts, favorites, reading progress, installed extensions, and repository URLs.
- ✅ **Source settings via API**.
- ✅ **Favorites and tracking**: add/remove titles and remember the last read chapter.
- ✅ **Extension manager** in the WebUI (browse a repository, install/uninstall, and your repository URLs persist in the database).
- ✅ Extensions loaded **directly from source** (Kotlin → JVM jars), see `scripts/compile-extension.sh`.
- ⏳ **Docker image** (GHCR + Docker Hub, multi-arch) planned for the near future — see [issue #3](https://github.com/miwayomi/miwayomi/issues/3).
- ⏳ Pending: fuller WebUI (more views), per-source JS engines, torrent streaming.

## Documentation

Full documentation — the origin story, architecture, file-by-file code map, technical requirements, REST API, streaming internals, and a guide to writing your **own** compatible extension — lives in:

- **`docs/index.md`** — the complete markdown reference.
- **`/docs.html`** — the same documentation as a web page, served by the app (open the running server and click **Docs** in the top bar). It includes screenshots, an animated UI tour (GIF), and a section on customizing the UI.

## Translations

The WebUI is translated with one JSON file per language in `server/src/main/resources/webui/lang/` (`en.json`, `es.json`, ...). To add a language, copy `en.json` to `<code>.json`, translate the values, register it in the `<select id="langSelect">` of `index.html`, and rebuild. See `lang/README.md`.

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for the full guide (dev setup, conventions, and the pull request checklist). By contributing, you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md) and the project's rules on attribution, disclaimers, and third-party rights (see the [Legal disclaimer](#legal-disclaimer)). Do not submit or redistribute content or extensions you do not own.

## Security

Found a security issue? Please report it **privately** — see [SECURITY.md](SECURITY.md) for the supported versions and the reporting process. Do **not** open a public issue for vulnerabilities.

## Attribution

See [NOTICE-ANIYOMI.md](NOTICE-ANIYOMI.md): `source-api` and `core-common` are adaptations of Aniyomi (Apache-2.0). The rest is new miwayomi code.

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

## License

miwayomi is licensed under the **Apache License, Version 2.0**. See [`LICENSE`](LICENSE) for the full license text.

Portions of the codebase are adapted from [Aniyomi](https://github.com/aniyomiorg/aniyomi) (Apache-2.0); see [`NOTICE-ANIYOMI.md`](NOTICE-ANIYOMI.md) for attribution.
