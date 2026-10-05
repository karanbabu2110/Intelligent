package io.kaos.app.research;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import io.kaos.tool.browserrender.BrowserRenderException;
import io.kaos.tool.browserrender.BrowserRenderedResult;
import io.kaos.tool.browserrender.BrowserResource;
import io.kaos.tool.browserrender.PinnedBrowserResourceFetcher;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import io.kaos.tool.httpget.HttpGetResult;
import io.kaos.tool.websearch.WebSearchRequest;
import io.kaos.tool.websearch.WebSearchResult;
import io.kaos.tool.websearch.WebSearchToolContract;
import io.kaos.app.browsersearch.BrowserSearchProvider;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Fresh-context Chromium renderer shared by approved research and browser search. */
public final class ResearchBrowserRenderer {
    static final Duration MAX_TOTAL_RUNTIME = Duration.ofSeconds(20);
    static final Duration MAX_RESOURCE_TIME = Duration.ofSeconds(10);
    static final int MAX_RESOURCE_BYTES = 512 * 1024;
    static final int MAX_TOTAL_BYTES = 1024 * 1024;
    private static final int MAX_TEXT_NODES = 20_000;
    private static final long SETTLE_MILLIS = 250;
    private static final String EXTRACT_VISIBLE_TEXT = """
            element => {
              const maxNodes = %d, maxCodePoints = %d;
              const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
              const blocks = "address,article,aside,blockquote,dd,div,dl,dt,fieldset,figcaption,figure,footer,h1,h2,h3,h4,h5,h6,header,li,main,nav,ol,p,pre,section,table,td,th,tr,ul";
              let text = "", previous = null, count = 0, codePoints = 0, node;
              while ((node = walker.nextNode()) !== null && ++count <= maxNodes) {
                const parent = node.parentElement;
                if (!parent || /^(SCRIPT|STYLE|TEMPLATE|NOSCRIPT|FORM)$/.test(parent.tagName)) continue;
                let current = parent, visible = true;
                while (current) {
                  const style = window.getComputedStyle(current);
                  if (current.hidden || style.display === "none" || style.visibility === "hidden"
                      || style.visibility === "collapse") { visible = false; break; }
                  current = current.parentElement;
                }
                if (!visible) continue;
                const block = parent.closest(blocks) || parent;
                if (previous && block !== previous && text && !text.endsWith("\\n") && codePoints < maxCodePoints) {
                  text += "\\n"; codePoints++;
                }
                previous = block;
                for (const point of (node.nodeValue || "")) {
                  if (codePoints++ === maxCodePoints) return text;
                  text += point;
                }
              }
              return text;
            }
            """.formatted(MAX_TEXT_NODES, HttpGetResult.MAX_MODEL_TEXT_CODE_POINTS);
    private static final String DISABLE_ACTIONS = """
            (() => {
              addEventListener('submit', event => event.preventDefault(), true);
              addEventListener('click', event => event.preventDefault(), true);
              window.open = () => null;
            })();
            """;

    @FunctionalInterface
    interface ResourceFetcher {
        BrowserResource fetch(URI uri, Duration timeout, int maxBytes);
        default void validateDestination(URI uri, Duration timeout) { }
    }

    private final ResourceFetcher fetcher;

    public ResearchBrowserRenderer() {
        this(new ResourceFetcher() {
            @Override public BrowserResource fetch(URI uri, Duration timeout, int maxBytes) {
                return PinnedBrowserResourceFetcher.fetch(uri, timeout, maxBytes);
            }
            @Override public void validateDestination(URI uri, Duration timeout) {
                PinnedBrowserResourceFetcher.validateDestination(uri, timeout);
            }
        });
    }

    ResearchBrowserRenderer(ResourceFetcher fetcher) {
        this.fetcher = java.util.Objects.requireNonNull(fetcher, "fetcher");
    }

    public BrowserRenderedResult render(String rawUrl) {
        URI selected;
        try {
            selected = HttpGetPermissionValidator.validateSyntax(new HttpGetRequest(rawUrl)).uri();
        } catch (RuntimeException exception) {
            throw new BrowserRenderException(BrowserRenderException.Reason.INVALID_REQUEST);
        }
        long deadline = System.nanoTime() + MAX_TOTAL_RUNTIME.toNanos();
        fetcher.validateDestination(selected, Duration.ofMillis(remainingMillis(deadline).longValue()));
        return withPage(selected, page -> {
            if (page.locator("body").count() != 1) {
                throw new BrowserRenderException(BrowserRenderException.Reason.EMPTY_CONTENT);
            }
            Object extracted = page.locator("body").evaluate(EXTRACT_VISIBLE_TEXT);
            String text = extracted == null ? "" : boundedText(extracted.toString());
            if (text.isBlank()) throw new BrowserRenderException(BrowserRenderException.Reason.EMPTY_CONTENT);
            String title = boundedMetadata(page.title(), BrowserRenderedResult.MAX_TITLE_CODE_POINTS);
            var descriptions = page.locator("meta[name='description']");
            String description = descriptions.count() == 0 ? "" : boundedMetadata(
                    descriptions.first().getAttribute("content"),
                    BrowserRenderedResult.MAX_DESCRIPTION_CODE_POINTS);
            return new BrowserRenderedResult(new HttpGetRequest(selected.toASCIIString()), text,
                    title, description);
        }, false, deadline);
    }

    /** Runs a provider inside the same pinned, isolated Chromium path as research. */
    public WebSearchResult search(WebSearchRequest request, BrowserSearchProvider provider) {
        java.util.Objects.requireNonNull(request, "request");
        java.util.Objects.requireNonNull(provider, "provider");
        long deadline = System.nanoTime() + MAX_TOTAL_RUNTIME.toNanos();
        return withPage(provider.searchUri(request), page -> {
            WebSearchResult result = provider.extract(page, request);
            if (!request.equals(result.request()) || result.results().isEmpty()
                    || result.results().stream().anyMatch(entry ->
                            !provider.provenance().equals(entry.provider()))) {
                throw new BrowserRenderException(BrowserRenderException.Reason.UNAVAILABLE);
            }
            WebSearchToolContract.encodeResult(result);
            return result;
        }, true, deadline);
    }

    private <T> T withPage(URI selected, Function<Page, T> extract, boolean search, long deadline) {
        var failure = new AtomicReference<BrowserRenderException>();
        var pageRef = new AtomicReference<Page>();
        var policy = new ResearchBrowserRequestPolicy(selected);
        var totalBytes = new int[] {0};
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                     .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true)
                     .setTimeout(remainingMillis(deadline))
                     .setArgs(List.of("--disable-background-networking", "--disable-component-update",
                             "--disable-default-apps", "--disable-extensions", "--disable-sync",
                             "--metrics-recording-only", "--no-first-run")));
             BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                     .setAcceptDownloads(false)
                     .setJavaScriptEnabled(true)
                     .setPermissions(List.of())
                     .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
                     .setUserAgent("KAOS-Research-Renderer/1.0"))) {
            context.setDefaultNavigationTimeout(remainingMillis(deadline));
            context.setDefaultTimeout(remainingMillis(deadline));
            context.addInitScript(DISABLE_ACTIONS);
            context.onDialog(dialog -> dialog.dismiss());
            context.onDownload(download -> {
                failure.compareAndSet(null,
                        new BrowserRenderException(BrowserRenderException.Reason.REQUEST_BLOCKED));
                download.cancel();
            });
            context.onPage(opened -> {
                Page selectedPage = pageRef.get();
                if (selectedPage != null && opened != selectedPage) {
                    failure.compareAndSet(null,
                            new BrowserRenderException(BrowserRenderException.Reason.REQUEST_BLOCKED));
                    opened.close();
                }
            });
            context.route("**/*", route -> handle(route, policy, deadline, totalBytes, failure, search));
            context.routeWebSocket("**/*", socket -> {
                try {
                    policy.blockedRequest();
                } catch (BrowserRenderException exception) {
                    failure.compareAndSet(null, exception);
                }
                socket.close();
            });
            Page page = context.newPage();
            pageRef.set(page);
            page.navigate(selected.toASCIIString(), new Page.NavigateOptions()
                    .setTimeout(remainingMillis(deadline))
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            throwFailure(failure);
            page.waitForTimeout(Math.min(SETTLE_MILLIS, remainingMillis(deadline)));
            throwFailure(failure);
            return extract.apply(page);
        } catch (BrowserRenderException exception) {
            throw exception;
        } catch (PlaywrightException | LinkageError exception) {
            BrowserRenderException classified = failure.get();
            if (classified != null) throw classified;
            if (Thread.currentThread().isInterrupted()) {
                throw new BrowserRenderException(BrowserRenderException.Reason.INTERRUPTED);
            }
            throw new BrowserRenderException(System.nanoTime() >= deadline
                    ? BrowserRenderException.Reason.TIMEOUT : BrowserRenderException.Reason.UNAVAILABLE);
        }
    }

    private void handle(Route route, ResearchBrowserRequestPolicy policy, long deadline,
            int[] totalBytes, AtomicReference<BrowserRenderException> failure, boolean search) {
        try {
            if (failure.get() != null) {
                route.abort("blockedbyclient");
                return;
            }
            Request request = route.request();
            URI uri = policy.authorize(request.url(), request.method(), request.resourceType(),
                    request.isNavigationRequest(), request.redirectedFrom() == null
                            ? null : request.redirectedFrom().url());
            BrowserResource resource;
            byte[] responseBody;
            while (true) {
                int remainingBytes = MAX_TOTAL_BYTES - totalBytes[0];
                if (remainingBytes <= 0) {
                    throw new BrowserRenderException(BrowserRenderException.Reason.TOO_LARGE);
                }
                Duration remainingTime = Duration.ofMillis(remainingMillis(deadline).longValue());
                int resourceLimit = Math.min(MAX_RESOURCE_BYTES, remainingBytes);
                resource = fetcher.fetch(uri,
                        remainingTime.compareTo(MAX_RESOURCE_TIME) < 0 ? remainingTime : MAX_RESOURCE_TIME,
                        resourceLimit);
                responseBody = resource.body();
                if (responseBody.length > resourceLimit) {
                    throw new BrowserRenderException(BrowserRenderException.Reason.TOO_LARGE);
                }
                totalBytes[0] += responseBody.length;
                if (totalBytes[0] > MAX_TOTAL_BYTES) {
                    throw new BrowserRenderException(BrowserRenderException.Reason.TOO_LARGE);
                }
                if (resource.status() < 300 || resource.status() >= 400) break;
                uri = policy.followRedirect(uri, resource.location());
            }
            validateResponse(request.resourceType(), uri, resource, search);
            var options = new Route.FulfillOptions().setStatus(resource.status())
                    .setBodyBytes(responseBody);
            var headers = new java.util.HashMap<String, String>();
            if (!resource.contentType().isBlank()) headers.put("Content-Type", resource.contentType());
            options.setHeaders(Map.copyOf(headers));
            route.fulfill(options);
        } catch (BrowserRenderException exception) {
            if (exception.reason() == BrowserRenderException.Reason.REQUEST_BLOCKED
                    || exception.reason() == BrowserRenderException.Reason.INVALID_REQUEST) {
                route.abort("blockedbyclient");
                return;
            }
            failure.compareAndSet(null, exception);
            route.abort("blockedbyclient");
        } catch (RuntimeException exception) {
            failure.compareAndSet(null,
                    new BrowserRenderException(BrowserRenderException.Reason.UNAVAILABLE));
            route.abort("failed");
        }
    }

    private static void validateResponse(String resourceType, URI uri, BrowserResource resource,
            boolean search) {
        if (!resource.url().equals(uri)) {
            throw new BrowserRenderException(BrowserRenderException.Reason.REQUEST_BLOCKED);
        }
        int status = resource.status();
        if (search && status == 403) throw new BrowserRenderException(BrowserRenderException.Reason.ACCESS_DENIED);
        if (search && status == 429) throw new BrowserRenderException(BrowserRenderException.Reason.RATE_LIMITED);
        if (status < 200 || status >= 300) {
            throw new BrowserRenderException(BrowserRenderException.Reason.HTTP_ERROR);
        }
        String type = resource.contentType().split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT);
        boolean supported = switch (resourceType.toLowerCase(java.util.Locale.ROOT)) {
            case "document" -> type.equals("text/html") || type.equals("application/xhtml+xml");
            case "script" -> type.equals("application/javascript") || type.equals("text/javascript")
                    || type.equals("application/ecmascript") || type.equals("text/ecmascript");
            case "stylesheet" -> type.equals("text/css");
            default -> false;
        };
        if (!supported) throw new BrowserRenderException(BrowserRenderException.Reason.UNSUPPORTED_CONTENT);
    }

    private static Double remainingMillis(long deadline) {
        long millis = Duration.ofNanos(Math.max(0, deadline - System.nanoTime())).toMillis();
        if (millis < 1) throw new BrowserRenderException(BrowserRenderException.Reason.TIMEOUT);
        return (double) millis;
    }

    private static void throwFailure(AtomicReference<BrowserRenderException> failure) {
        BrowserRenderException exception = failure.get();
        if (exception != null) throw exception;
    }

    private static String boundedText(String raw) {
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n').strip();
        var output = new StringBuilder();
        int bytes = 0;
        int points = 0;
        for (int offset = 0; offset < normalized.length();) {
            int point = normalized.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isISOControl(point) && point != '\n' && point != '\t') continue;
            int width = new String(Character.toChars(point)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + width > HttpGetResult.MAX_MODEL_TEXT_UTF8_BYTES
                    || points == HttpGetResult.MAX_MODEL_TEXT_CODE_POINTS) break;
            output.appendCodePoint(point);
            bytes += width;
            points++;
        }
        return output.toString().strip();
    }

    private static String boundedMetadata(String raw, int maxPoints) {
        if (raw == null) return "";
        String normalized = raw.replaceAll("[\\p{Cntrl}\\p{Z}]+", " ").strip();
        return normalized.codePointCount(0, normalized.length()) <= maxPoints ? normalized : "";
    }
}
