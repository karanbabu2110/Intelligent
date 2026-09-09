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
The types remain owned by `io.kaos.tool.httpget`; Epic 008 has not established a
generic tool API.

## Configuration and permissions

`kaos.tool.http.allowed-hosts` takes precedence over
`KAOS_HTTP_ALLOWED_HOSTS`. The value is a comma-separated set of exact host
names with no wildcards, ports, paths, or defaults. URL comparison is
case-insensitive after IDN-to-ASCII normalization.

Only absolute HTTPS URLs on port 443 or the implicit standard port are valid.
User information and fragments are rejected. A query string is allowed because
it is part of the exact URL shown for approval. DNS resolution occurs only after
approval; every returned address must be public according to the Java
platform's loopback, local, link-local, unspecified, and multicast checks.

## Execution boundaries

- method: one `GET`;
- redirect policy: never follow;
- connect timeout: two seconds;
- total request timeout: fifteen seconds;
- accepted status: `200` only;
- accepted media: `text/*`, `application/json`, or `application/xml`;
- accepted charset: absent or UTF-8 only;
- maximum body: 32,768 bytes, with no partial result; and
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

Checking DNS immediately before execution blocks observable private and local
destinations. It cannot guarantee that a hostname will not change between that
check and the HTTP client's own connection, so complete DNS-rebinding resistance
is not claimed.

## Verification

```powershell
./gradlew.bat test --tests 'io.kaos.tool.httpget.*' --tests 'io.kaos.app.HttpGetCommandIntegrationTest' --tests 'io.kaos.app.CommandRouterTest' --tests 'io.kaos.ai.ollama.OllamaPromptClientTest' --no-daemon --warning-mode=all
./gradlew.bat clean verifyLocal --no-daemon --warning-mode=all
```

Tests use deterministic fake HTTP and loopback Ollama boundaries. They do not
contact an installed Ollama process or public network service.

## Deliberate next checkpoint

Feature 008.02 will define `web_search` using evidence from this second concrete
tool. Tool discovery, selection across tools, shared metadata, permission
policies, execution history, and contract refinement remain assigned to later
Epic 008 features.
