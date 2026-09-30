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
External websites, clicks, submissions, saved profiles,
screenshots, and workflow recording remain future features. Bounded text-field
filling is documented below. A local page can still return data or perform
server-side effects for HTTP GET requests; use a local service whose behavior
you understand.

## Temporary session control (Feature 010.02), navigation (Feature 010.03)

`kaos browser session <loopback-url>` opens the same isolated context but keeps
it alive in the foreground. The prompt accepts:

- `inspect` to inspect the current page, or `inspect <loopback-url>` to load and inspect another local page;
- `back`, `forward`, and `reload` to navigate the current page's history;
- `status` to show the current safe URL; and
- `close` to close the context and exit.

End-of-input also closes the session. Navigation uses the same loopback-only
GET-document policy as one-shot inspection. Session state exists only in the
current process and is discarded on close; KAOS does not use or save a browser
profile. History controls stay within that temporary session. The session is
headless.

## Bounded text input (Feature 010.04)

The session also accepts `fill <selector> <text>` for one visible, enabled,
editable `<textarea>` or `<input>` of type `text`, `email`, `search`, `tel`, or
`url`. The selector must match exactly one element and the value is limited to
256 Unicode code points. Passwords, hidden or readonly fields, other input
types, and ambiguous selectors are rejected. Feature 010.05 adds a separate
approval prompt for each fill. The prompt shows only the character count; type
exactly `approve` to authorize that one fill. Any other response or end of
input cancels it. KAOS does not echo the value, click a control, or submit a
form. The target is checked again after approval. JavaScript remains disabled
and the filled value exists only in the temporary page context.

## Navigation failure recovery (Feature 010.06)

After the first page loads, the session keeps its last successfully loaded
loopback URL in memory. If `inspect`, `back`, `forward`, or `reload` fails,
KAOS attempts to load that URL again, reports whether recovery succeeded, and
keeps the session open for another command. Transport failures and HTTP 400 or
higher responses count as failed navigation. Retry the failed navigation after
the local page is available again. Recovery does not persist across session
close. If the initial page cannot load, the command exits because there is no
known-good page to restore.

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

For a foreground session, run:

```powershell
./gradlew.bat run --args="browser session http://127.0.0.1:8080/"
```

Then enter `inspect http://127.0.0.1:8080/other`, `back`, `forward`, `reload`,
`fill #search example query`, `inspect`, `status`, or `close` at the
`browser-session>` prompt. After `fill`, respond `approve` at the separate
approval prompt to apply the value.

If the browser binary or local page is unavailable, the command exits with an
actionable error and does not report a successful inspection.
