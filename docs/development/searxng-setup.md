# Run SearXNG beside KAOS

Run Ollama, SearXNG, and KAOS as separate processes. KAOS neither installs nor
manages SearXNG. Java builds and normal tests do not require Docker.

Setting `KAOS_WEB_SEARCH_SEARXNG_URL` only tells KAOS where to connect. It does
not install or start SearXNG. Complete the following steps in order.

## 1. Start Docker Desktop

Open Docker Desktop from the Windows Start menu, select Linux containers, and
wait until its engine is running. In PowerShell, check:

```powershell
docker info
docker compose version
```

Do not continue if `docker info` reports a missing `dockerDesktopLinuxEngine`
pipe or cannot connect to the daemon. Having `docker.exe` installed is not
enough; Docker Desktop must be running.

## 2. Start the repository-provided SearXNG deployment

The root [Compose file](../../compose.yaml) starts only the official SearXNG
container. Its checked-in [settings](../../docker/searxng/settings.yml) inherit
SearXNG defaults and enable HTML and JSON output. It does not containerize KAOS,
start Ollama, add Valkey, or make Gradle depend on Docker.

From the KAOS repository root:

```powershell
docker compose up -d
docker compose ps
```

Compose publishes SearXNG only on `127.0.0.1:8080`. Wait until `docker compose
ps` reports `kaos-searxng` as healthy. If it is starting or unhealthy, run
`docker compose logs --tail=80 searxng`. Logs belong to SearXNG and may contain
sensitive data; review them before sharing.

The default image selector is `latest`, matching SearXNG's official Compose
pattern. For a repeatable local deployment, set an official tag before the
first pull, for example `$env:SEARXNG_VERSION = "<reviewed-tag>"`. The checked-in
fallback secret is acceptable only for this loopback-only development service.
For longer-lived local use, set a private value before `up -d`:

```powershell
$env:SEARXNG_SECRET = [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
docker compose up -d
```

Do not expose this Compose service to another host. Production TLS, shared-use
rate limiting, persistent secret management, upstream-engine policy, upgrades,
logging and retention are separate SearXNG deployment concerns.

## 4. Verify JSON search deliberately

The following manual diagnostic sends the public query `Spring Boot` to SearXNG,
which may forward it to external engines. It bypasses KAOS approval because you
are invoking the service directly. Run it only if you accept that disclosure:

```powershell
$searchCheck = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/search?q=Spring%20Boot&format=json' -TimeoutSec 30
$searchCheck.results | Select-Object -First 5 title, url
```

Expect parsed JSON with a `results` array; an empty array is valid but may mean
upstream engines are unavailable. HTTP 403 means JSON may still be disabled.
An open port alone does not prove that JSON search works.

## 5. Check Ollama and run KAOS

Remain in the KAOS checkout. Keep Ollama and SearXNG running separately. Check
the installed model:

```powershell
ollama list
```

Start the Ollama application if it is unavailable. Use an installed model that
supports tools; the example below assumes `qwen3:4b-instruct` is installed.
Paste plain PowerShell commands, without Markdown links, escaped underscores,
or copied `PS>` / `>>` prompt markers:

```powershell
$env:KAOS_WEB_SEARCH_SEARXNG_URL = "http://127.0.0.1:8080"
$env:KAOS_OLLAMA_MODEL = "qwen3:4b-instruct"
$env:KAOS_OLLAMA_THINKING = "on"
$env:KAOS_TOOL_READ_ROOT = (Resolve-Path ".").Path
./gradlew.bat --console=plain run --args=conversation
```

Ask a current-information question such as "What is the latest stable Spring
Boot release?" Review the proposed query, then enter `approve` or `deny`.
Use `--console=plain` to prevent Gradle's progress bar from overwriting prompts
and approval text. The task remains running while the conversation awaits input.
Type `/exit` to end it; a failed search does not stop the conversation.

If the model adds an incorrect year, type `deny` and ask again with today's
explicit date. KAOS does not currently inject the current date into this tool
prompt. Do not approve a stale or otherwise changed search query.

For a project file question, name a relative file path and approve its exact
validated target. General questions can receive a direct answer.

A one-shot command is also available:

```powershell
./gradlew.bat --% --console=plain run --args="web-search \"Search for the latest stable Spring Boot release\""
```

Only one selected tool may execute per turn. Search titles/snippets are
untrusted data; result URLs are not opened. Tool-backed conversation turns are
not saved or carried into future history. Ordinary no-tool turns remain stored.

The Java-to-SearXNG hop is local for the loopback example. The complete search
is not local: SearXNG may forward the approved query to external engines.
Treat its logs and upstream retention separately from KAOS's metadata-only
audit policy.

| Symptom | Recovery |
| --- | --- |
| Search service not configured | Set the environment variable or `kaos.web-search.searxng-url` JVM property |
| Invalid configuration | Use an HTTP(S) origin without credentials, query, fragment or path prefix |
| Missing Docker Linux engine pipe | Start Docker Desktop and wait for `docker info` to succeed |
| Service unavailable | Check `docker compose ps`, then test port 8080; setting the URL alone does not start SearXNG |
| Garbled prompts / Gradle progress bars | Restart KAOS with `--console=plain` |
| Model proposes an old year | Deny and ask again with the intended date |
| Invalid response | Enable JSON; check service status and retained result fields |
| Timeout or result too large | Review the service, then make a fresh request and approval |
| No results | Rephrase the query or review configured upstream engines |

Model selection and relevance remain model/engine dependent. The automated
suite proves protocol and policy behavior with local stubs, not live search
coverage or answer quality.

## Later starts and stops

On later runs, start Docker Desktop, run `docker compose up -d` from the KAOS
repository root, check Ollama, and run KAOS. To stop search, run `docker compose
stop searxng`. To remove only its disposable container, run `docker compose down`.
Other KAOS capabilities remain usable while search is stopped.
