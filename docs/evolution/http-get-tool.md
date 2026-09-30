# `http_get` Second Tool

## Outcome

Feature 008.01 adds one bounded external-read path to the single KAOS
application. The `http-get <question>` command lets fixed-loopback Ollama either
answer directly or request one `http_get` URL. A request is not authority: KAOS
must validate configured scope, display the exact normalized URL, and obtain
one explicit local-user approval before any DNS or HTTP operation.

## Contract and flow

```text
user question
  -> local Ollama receives only the http_get definition
  <- ordinary answer, or http_get({"url":"https://allowed.example/path"})
  -> syntax and exact-host allowlist validation
  -> exact URL and disclosure approval prompt
  -> post-approval public-address DNS validation
  -> one bounded foreground HTTPS GET
  -> one structured untrusted tool result
  -> local Ollama continuation with no advertised tools
  <- one final answer
```

The request contains exactly one textual `url`. The successful result contains
that URL, normalized media type, complete content, and exact UTF-8 byte count.
The concrete types remain owned by `io.kaos.tool.httpget` and participate in the
shared `KaosTool`, permission-policy and content-free history contracts.

## Configuration and permissions

`kaos.tool.http.allowed-hosts` takes precedence over
`KAOS_HTTP_ALLOWED_HOSTS`. The value is a comma-separated set of exact host
names with no wildcards, ports, paths, or defaults. URL comparison is
case-insensitive after IDN-to-ASCII normalization.

Only absolute HTTPS URLs on port 443 or the implicit standard port are valid.
User information and fragments are rejected. A query string is allowed because
it is part of the exact URL shown for approval. Recognized credential-bearing
query keys (tokens, passwords, API keys, signatures and sessions) are rejected,
including percent-encoded parameter names. DNS resolution occurs only after
approval; every returned address must be public according to the Java
platform's loopback, local, link-local, unspecified, and multicast checks.

## Execution boundaries

- method: one `GET`;
- redirect policy: never follow;
- connect timeout: two seconds;
- total request timeout: fifteen seconds including DNS and the entire body;
- accepted status: `200` only;
- accepted media: `text/plain`, `text/html`, `application/json`, or `application/xml`;
- accepted charset: absent or UTF-8 only;
- maximum raw body: 512 KiB, with no partial result, followed by a 64 KiB model-text bound; and
- credentials, cookies, caller headers, retry, cache, persistence, background
  execution, multiple URLs, and chaining: unsupported.

Content is strictly decoded as UTF-8 and rejected when blank or when it contains
unsafe control characters. It is untrusted tool data supplied only to the local
Ollama continuation for the current answer.

## Failures, privacy, and recovery

Known configuration, request, permission, resolution, transport, timeout,
redirect, status, media, size, UTF-8, content, interruption, and state failures
map to stable diagnostics without echoing the URL or response. Only the local
approval prompt intentionally displays the exact URL. The final audit contains
a random target identity, decision, and broad outcome—never URL or content.

Denial or cancellation performs no DNS lookup, HTTP request, or model
continuation. An approved grant permits one execution attempt. Recovery is a
new question, model request, validation, and approval; KAOS never retries.

Feature 019.01 hardens this shared path for [verified research](verified-web-research.md).
Every DNS answer must pass the public-address policy. One validated address is
passed directly to the connection; there is no second independent hostname
lookup and no alternate-address fallback. TLS still verifies the original
hostname using the platform trust roots. Raw IP URLs and special-purpose address
ranges are rejected. Percent-escaped paths and queries retain their exact meaning;
normalization removes the explicit default port and is idempotent.

Apache HttpClient 5.6.4 supplies protocol parsing, TLS and connection-time DNS
binding. Both HTTP and Java socket-level SOCKS proxy selection are disabled.
Automatic retries, redirects, authentication, cookies and decompression are
disabled. Responses have at most 32 headers and 4096 characters per protocol line.
Rejected bodies are aborted rather than drained. A foreground-owned virtual task
allows the caller to cancel DNS/connection/body work at the total deadline.
An OS DNS lookup that ignores interruption can finish later, but cancellation is
checked after resolution and cannot initiate a late source request. There is no
background research job.

## Verification

```powershell
./gradlew.bat test --tests 'io.kaos.tool.httpget.*' --tests 'io.kaos.app.HttpGetCommandIntegrationTest' --tests 'io.kaos.app.CommandRouterTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Tests use deterministic fake HTTP and loopback Ollama boundaries. They do not
contact an installed Ollama process or public network service.

## Research reuse

The focused research command now composes search with up to three individually
bound HTTP grants authorized by explicitly remembered research host approvals. The
standalone `http-get` command remains one URL and one continuation. See
[verified web research](verified-web-research.md) for the implemented first slice
and the remaining Epic 019 work.

HTTP status failures never retain response bodies. Fixed content-free reasons
distinguish redirects, 401 unauthorized, 403 forbidden, 404 not found and 429
rate limited; other non-200 statuses remain `REQUEST_FAILED`.

Raw responses are streamed up to 512 KiB, with an early `Content-Length` check.
HTML is reduced to readable bounded text before becoming model evidence, with
scripts, styles, navigation, footers and common advertisement/consent blocks
removed. The model-facing text limit is 64 KiB, so a large page can succeed when
its extracted text is smaller. Stable retrieval categories are `SUCCESS`,
`TOO_LARGE`, `HTTP_ERROR`, `TIMEOUT`, `UNAVAILABLE` and `UNSUPPORTED_CONTENT`.
Failures retain safe HTTP status, content type, final URL and response-header
diagnostics. Transient unavailable and timeout failures receive at most two
short-backoff retries; HTTP, size and content failures are not retried.
