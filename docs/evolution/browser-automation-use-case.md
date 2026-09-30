# Browser Automation Use Case

Feature 010.01 establishes one useful, read-only browser workflow inside the
existing KAOS command-line application:

```text
kaos browser inspect http://127.0.0.1:8080/
```

KAOS opens a headless Chromium browser with a fresh, non-persistent context,
loads one user-selected loopback page, and prints its URL, title, and visible
text. Text output is capped at 4,000 Unicode code points. The browser and
context close when the command ends.

## Safety boundary

- Accept only `http://127.0.0.1` URLs with an explicit port and no user-info.
- Allow only HTTP GET document requests to `127.0.0.1`; other methods,
  redirects to other hosts, and all subresource requests are blocked.
- Disable JavaScript and downloads.
- Do not load a saved profile, cookies, credentials, or persistent storage.
- Do not click, type, submit forms, download files, or modify the page.
- Do not print query strings or fragments from the final URL.

This slice is intended for inspecting a local application or development page.
External websites, interactive actions, approval prompts, session reuse,
screenshots, and workflow recording remain future features. A local page can
still return data or perform server-side effects for HTTP GET requests; use a
local service whose behavior you understand.

## Run

Install the Chromium binary pinned by Playwright Java 1.63.0 once:

```powershell
./gradlew.bat installChromium
```

The KAOS command does not download browser engines on startup; this explicit
task installs the Chromium engine it uses.

Start a local server bound to `127.0.0.1`, then run:

```powershell
./gradlew.bat run --args="browser inspect http://127.0.0.1:8080/"
```

If the browser binary or local page is unavailable, the command exits with an
actionable error and does not report a successful inspection.
