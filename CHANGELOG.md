# Changelog

All notable changes to miwayomi.

## [v0.2.7] - 2026-08-12

### Added
- **Extensions loaded and saved in the database**: installed extensions are now
  registered in the server's SQLite database (new `extensions` table), so the
  installed list is restored from the database on every start and shown in the
  Extensions tab without needing any repository.
- **Repository URLs persist**: repository index URLs you add from the
  Extensions tab are saved in the database and restored automatically, so you
  don't have to re-enter them next time.
- **Extensions tab improvements**: when no repository is configured it now
  lists the locally installed extensions; repositories can still be added,
  removed and switched at any time.

---

## [v0.2.6] - 2026-08-11

### Added
- **Watch history in the database**: anime watch progress is now persisted
  server-side in SQLite (new `watch_history` table and `/api/v1/watch` API),
  so "Continue watching" and exact resume positions survive restarts and work
  from any browser.
- **AniList sync**: connect your own AniList account from Settings (OAuth) and
  miwayomi pushes your watched-episode progress to AniList automatically
  (with an option to toggle it).
- **Player improvements**:
  - Invert episode order button (newest/oldest) in the detail view and the
    player episode list.
  - Auto-play the next episode when the current one finishes (toggleable).
  - Auto-select the best video source automatically (toggleable).
  - ↺ Restart button to reset the current episode.

---

## [v0.2.5] - 2026-08-11

### Fixed
- **Search HTTP 500 on some extensions**: extension jars produced by the DEX→JVM
  conversion (dex2jar) could contain an invalid `invokespecial <init>` owner on
  `new T; dup; ...` constructions, making the JVM verifier reject the filter
  classes (`VerifyError`) and every search return HTTP 500. The server now
  repairs the corrupted constructor owner to the constructed type, applies the
  repair to every extension jar on first load (tracked via a marker entry), and
  preserves the original stack map frames so valid classes are never rewritten.
- **Manga extensions from repos that publish desktop JVM jars** now use those
  jars directly instead of converting the APK, avoiding the conversion
  corruption entirely.
- **Manga extension search**: removed `IgnoreGzipInterceptor` and
  `BrotliInterceptor` from the default OkHttp client (newer manga extensions
  require a default client without them) and upgraded OkHttp to 5.4.0
  (adding `okhttp-zstd`) so `okhttp3.CompressionInterceptor` is available.
- **Newer manga extension compatibility**: added the `memo` field to
  `SManga`/`SChapter` and their implementations.

---

## [v0.2.4] - 2026-08-10

### Fixed
- **Startup gate no longer hangs**: the "Checking for updates…" screen waited for the server's GitHub check, which could take ~45s on slow networks and made the app appear stuck. It now uses only the local update state and opens the app in a few seconds regardless of network speed; updates are still applied/relaunched automatically when one is ready.

---

## [v0.2.3] - 2026-08-10

### Added
- **In-app updater (all platforms)**: the server now applies a downloaded update itself at startup by replacing the running jar (works on macOS/Linux even without a launcher script; on Windows the launcher still handles the swap because the running jar is locked). A `/api/v1/update/relaunch` endpoint restarts the server with the same command line and arguments.
- **Startup gate**: `/` now shows a "Checking for updates…" screen before the WebUI. It waits for the update check, applies/downloads/relaunches as needed, and only redirects to the app (`/index.html`) once the server is up to date.

### Fixed
- Previously the downloaded update was never applied when running `java -jar` directly on macOS/Linux (only the Windows launcher applied it). The update is now applied from within the jar itself.

---

## [v0.2.2] - 2026-08-10

### Fixed
- **Search HTTP 500**: search no longer forces `source.getFilterList()` on every request. Some extensions' `getFilterList()` throws `InstantiationError` (abstract filter classes instantiated in minified builds), which made every search return HTTP 500. Like Tachiyomi/Aniyomi, a plain text search now falls back to an empty filter list when `getFilterList()` fails.
- The 500 error handler now prints the full stack trace to ease diagnosis.
- Fixed a leftover Spanish log message (all logs are now English).

---

## [v0.2.1] - 2026-08-09

### Added
- **Extensions from source**: miwayomi can now load extensions compiled directly from Kotlin source as JVM jars (a jar with a `META-INF/miwayomi-extension.json` marker is picked up automatically from `data/extensions/`). Bytecode produced by the Kotlin compiler is clean — no `VerifyError` from dex conversion.
- `scripts/compile-extension.sh`: compiles a simple extension from its source (against the bundled JVM extension library) and packages it into a loadable jar.

### Changed
- `HttpSource.chapterPageParse` is now optional (`open`) instead of abstract, matching `extensions-lib >= 1.4` where the method was removed. This lets modern keiyoushi sources compile to JVM without implementing deprecated APIs.
- **Global dex→JVM fixer improvements** (`JarFixer`): more robust handling of R8-obfuscated APKs — constructor super-calls, phantom `new Object`, lazy-lambda `<init>` owners, and synthesized missing constructors with correct load opcodes. Applied uniformly to every APK (not per-extension).

### Fixed
- Extension loading is more reliable across the ecosystem with zero regressions (validated: manga 9 / anime 13, no errors).

---

## [v0.2.0] - 2026-08-09

### Added
- **Desktop launcher**: `./miwayomi` (macOS/Linux) and `miwayomi.bat` (Windows) — picks a free port, opens the app in its own Chrome/Edge window, and stops the server when the window closes.
- **Auto-update**: server checks GitHub for new releases, downloads the new jar, and applies it on next launch (banner + `GET /api/v1/update`).
- **Windows installer**: NSIS `miwayomi-setup.exe` (per-user install, Start Menu/Desktop shortcuts, optional bundled JRE).
- **Docs**: reorganized `README.md` and added `docs/` with direct download links.
- Optional port (`--port`/`-p`), `--host`, `--no-open` CLI options.

---

## [v0.1.4] - 2026-08-09

### Fixed
- Infinite scroll deduplication: repeated results from sources with `hasNextPage=true` forever are filtered by URL and the grid stops cleanly.

---

## [v0.1.3] - 2026-08-09

### Added
- Infinite scroll in catalogs (grid, no horizontal scroll).

---

## [v0.1.2] - 2026-08-08

### Added
- Logo in the header.

---

## [v0.1.1] - 2026-08-08

### Added
- Language filter in the extension manager.
- Working back button (SPA navigation stack).
- Install/uninstall no longer jumps to the top of the page.
