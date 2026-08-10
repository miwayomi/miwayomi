# Contributing to miwayomi

First off, thank you for taking the time to contribute! 🎉

miwayomi is a small, focused project: a JVM server that runs Tachiyomi/Aniyomi catalog extensions without Android and exposes them through a REST API and a WebUI. The codebase is deliberately compact and pragmatic, so the bar for contributing is low — but a few conventions keep it coherent. Please read this guide and the [Code of Conduct](CODE_OF_CONDUCT.md) before you start.

## Table of contents

- [How can I contribute?](#how-can-i-contribute)
  - [Reporting bugs](#reporting-bugs)
  - [Suggesting features](#suggesting-features)
  - [Documentation](#documentation)
  - [Translations](#translations)
  - [Code](#code)
- [Getting started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Building](#building)
  - [Running in dev mode](#running-in-dev-mode)
  - [Project layout](#project-layout)
- [Development workflow](#development-workflow)
  - [Branching](#branching)
  - [Commit messages](#commit-messages)
  - [Code style & conventions](#code-style--conventions)
  - [English-only rule](#english-only-rule)
  - [Changelog & docs](#changelog--docs)
- [Testing](#testing)
- [Submitting a pull request](#submitting-a-pull-request)
- [License](#license)

## How can I contribute?

### Reporting bugs

Use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.yml). A good bug report is small and reproducible:

- The miwayomi version (release tag or commit) and your OS.
- How you run it (desktop app, plain JAR, headless, dev mode).
- Exact steps to reproduce, expected vs. actual behavior.
- Relevant logs and, for UI issues, a screenshot.

> **Security vulnerabilities must not be reported in public issues.** See [SECURITY.md](.github/SECURITY.md) for the private reporting process.

### Suggesting features

Use the [feature request template](.github/ISSUE_TEMPLATE/feature_request.yml). Describe the problem you are trying to solve and why the current behavior is not enough — a clear motivation matters more than a specific implementation.

### Documentation

The docs live in `docs/` (`docs/index.md` is the canonical markdown reference; `/docs.html` is the same content rendered by the app). Fixing typos, clarifying wording, and adding missing details are all welcome contributions.

### Translations

The WebUI is multilingual: each language is one JSON file in `server/src/main/resources/webui/lang/`. To add or improve a language, edit or add a file there and follow the instructions in [`lang/README.md`](server/src/main/resources/webui/lang/README.md).

### Code

Look for issues labeled `good first issue` or `help wanted`. If you plan a non-trivial change, open an issue or a draft PR first to discuss the approach — this avoids wasted work.

## Getting started

### Prerequisites

- **JDK 21** (Temurin recommended). Gradle downloads itself via the wrapper — you don't need a system Gradle install.
- Optional: Chrome/Chromium (or FlareSolverr) if you work on Cloudflare challenge resolution.

### Building

```bash
git clone https://github.com/miwayomi/miwayomi.git
cd miwayomi
./gradlew :server:shadowJar      # build the cross-platform fat JAR
```

The fat JAR is written to `server/build/libs/miwayomi-all.jar`.

### Running in dev mode

```bash
# Hot-ish dev run (no install step), with local data dir and fixed port:
./gradlew :server:run --args="--data ./data --port 4567"

# Or the one-command desktop launcher:
./miwayomi       # macOS / Linux
miwayomi.bat     # Windows
```

Useful CLI flags: `--port/-p`, `--host/-h`, `--data/-d`, `--flaresolverr/-f`, `--chrome`, `--no-open`. See the README for details.

### Project layout

```
miwayomi/
├── android-compat/   # Android shim (Context, SharedPreferences, Uri, Log, ...) + android.jar stub
├── core-common/      # Network, coroutines, preferences, logcat, JavaScript engine, torrents stub
├── source-api/       # Aniyomi source API ported to JVM (Manga/Anime + HttpSource)
├── server/           # Ktor app: extension loading, source managers, REST API, proxy, WebUI
├── scripts/          # Helper scripts (e.g. compile-extension.sh)
├── packaging/        # Windows installer (NSIS) build script
└── data/             # runtime data (gitignored): extensions, prefs, cache
```

## Development workflow

### Branching

Work on a short-lived, descriptive branch off `main`, and open a PR when done:

```bash
git checkout -b fix/cloudflare-modal-timeout
# or
git checkout -b feat/torrent-streaming
```

Keep branches focused on a single logical change.

### Commit messages

Commit messages **must be in English**, in the imperative mood, with a concise subject line (≤ 72 characters) and an optional body explaining *why* the change is needed.

A `type(scope): subject` prefix keeps history scannable and matches the existing style (`server:`, `WebUI:`, `Docs:`, `JarFixer:`):

```
feat(streaming): add DASH segment proxy endpoint
fix(webui): dedupe catalog results by URL
docs(readme): document the --chrome flag
refactor(extension): extract source-jar loading into its own method
chore: update gradle wrapper to 8.x
```

Good:
- `server: return 404 instead of 500 when a source is not found`
- `WebUI: keep scroll position after installing an extension`

Avoid: unrelated changes in one commit, vague subjects like `fix stuff`, and Spanish text in messages or bodies.

### Code style & conventions

- **Kotlin**: follow the style already present in the codebase — 4-space indentation, `data class` for DTOs, no wildcard imports, straightforward names. There is no external formatter configured; match the surrounding code.
- **API error strings are user-facing** (they reach the WebUI via the REST API). Keep them short, descriptive, and in English.
- **Scripts** (`scripts/`, `packaging/`, launchers `miwayomi`, `miwayomi.bat`): keep echo messages, comments, and help text in English.
- **Do not commit** runtime data (`data/`), build outputs (`build/`, `packaging/stage/`, `packaging/dist/`), or IDE/editor local files. They are gitignored — keep it that way.
- **Keep changes minimal**: a PR should contain only the changes related to its purpose.

### English-only rule

All code, identifiers, comments, log messages, documentation, and script output **must be in English**.

The only exceptions are the **language files** (`lang/*.json`) and the language selector's native-name options (e.g. `<option value="es">Español</option>`), which stay in their own language by design.

### Changelog & docs

- Update [`CHANGELOG.md`](CHANGELOG.md) for any user-visible change, under the appropriate section (`Added`, `Changed`, `Fixed`).
- If a change affects behavior, CLI flags, or the API, update the README and/or `docs/` in the same PR.

## Testing

There is no automated test suite yet, so manual verification is the expectation:

1. The project builds: `./gradlew :server:shadowJar`.
2. The server starts and the demo source works: `./gradlew :server:run --args="--data ./data --port 4567"` → open `http://127.0.0.1:4567/`.
3. If you touched the API or extension loading, exercise the affected endpoint(s) and verify the error cases too (missing params, unknown source, bad JSON, ...).
4. If you touched the WebUI, check it in the browser (manga + anime flows).

## Submitting a pull request

1. Make sure your branch is up to date with `main`.
2. Fill out the [pull request template](.github/pull_request_template.md).
3. Before opening/updating the PR, run through the checklist:

- [ ] Commit messages are in English.
- [ ] Code, comments, and log messages are in English (`lang/` files excluded).
- [ ] The project builds: `./gradlew :server:shadowJar`.
- [ ] I ran the app and verified the change.
- [ ] `CHANGELOG.md` updated for user-visible changes.
- [ ] `docs/` and/or README updated if the change affects them.
- [ ] No unrelated changes included.

Small, focused PRs are reviewed and merged much faster. If your change is large, consider splitting it.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE), the same license as the project.
