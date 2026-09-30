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
External websites, clicks, submissions, saved profiles, and screenshots remain
future features. Bounded text-field
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

## Workflow recording (Feature 010.07)

Recording is opt-in within the foreground session: use `record on` to start,
`record off` to stop, `record show` to inspect the trace, and `record clear` to
clear it and stop recording. Only successful `inspect <url>`, `back`,
`forward`, and `reload` commands issued while recording are kept. The trace is
bounded to the latest 20 steps, with each displayed URL capped at 512 Unicode
code points. URLs omit query strings and fragments; field fills and values are
never recorded. The trace exists only in memory for the current session and is
informational, not replayable.

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
`fill #search example query`, `record on`, `record off`, `record show`,
`record clear`, `inspect`, `status`, or `close` at the
`browser-session>` prompt. After `fill`, respond `approve` at the separate
approval prompt to apply the value.

If the browser binary or local page is unavailable, the command exits with an
actionable error and does not report a successful inspection.

## End-to-end demonstration (Feature 010.08)

Run the complete browser flow without starting a separate web server:

```powershell
./gradlew.bat run --args="browser demo"
```

KAOS starts a temporary HTTP fixture on an ephemeral `127.0.0.1` port and runs
the existing foreground browser session against it. The scripted session
visits a second page, navigates back and forward, records successful navigation
steps, attempts a field fill and denies it, then approves one fill. It displays
the recorded trace and closes the isolated browser context and fixture. The
fixture counts non-GET requests; successful completion confirms that no form
submission reached it and no data was persisted. The trace stays in memory and
omits query strings and fragments. This demo exercises only the local fixture;
it does not contact an external site or submit the form.
