# Security Policy

## Supported versions

miwayomi follows a rolling release model: only the **latest release** is actively supported. If you are on an older version, please upgrade to the latest release before reporting an issue.

| Version         | Supported |
| --------------- | --------- |
| Latest release  | ✅        |
| Older releases  | ❌        |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security vulnerabilities.**

To report a vulnerability:

1. Go to the repository's **Security** tab → **Report a vulnerability** (GitHub Security Advisories), or contact the maintainers privately.
2. Include as much of the following as possible:
   - The miwayomi version (release tag or commit hash) you are using.
   - Your OS and how you run it (desktop app, plain JAR, headless server, dev mode).
   - A clear, minimal description of the vulnerability and the steps to reproduce it.
   - The impact you believe it has (e.g. remote code execution, arbitrary file read/write, SSRF, ...).
   - A suggested fix or patch, if you have one.

### What happens next

- We will acknowledge your report within **48–72 hours**.
- We will keep you informed of the investigation and any fix.
- We will coordinate a public disclosure **after** a patched release is available.

## Scope

**In scope:**

- The miwayomi server (Ktor), REST API, WebUI, and the extension loader (`server/`, `core-common/`, `source-api/`, `android-compat/`).

**Out of scope:**

- **Third-party extensions** loaded by miwayomi. miwayomi is an execution engine; each extension is separate third-party software and is responsible for its own behavior. Report issues with a specific extension to its maintainers.
- **Upstream dependencies.** Report vulnerabilities in third-party libraries to their maintainers, although we appreciate heads-ups about issues that affect miwayomi's bundled dependencies.
