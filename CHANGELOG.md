# Changelog

All notable changes to miwayomi.

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
