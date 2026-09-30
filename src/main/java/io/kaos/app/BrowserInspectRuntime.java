package io.kaos.app;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import java.net.URI;
import java.util.Map;

/** Playwright-backed implementation, loaded only when the browser command is invoked. */
final class BrowserInspectRuntime {
    private static final int NAVIGATION_TIMEOUT_MILLIS = 15000;

    private BrowserInspectRuntime() {
    }

    static int inspect(CommandContext context, URI uri) {
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                     .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true));
             BrowserContext browserContext = browser.newContext(
                     new Browser.NewContextOptions()
                             .setJavaScriptEnabled(false)
                             .setAcceptDownloads(false))) {
            browserContext.route("**/*", BrowserInspectRuntime::allowLoopbackOnly);
            Page page = browserContext.newPage();
            page.navigate(uri.toASCIIString(), new Page.NavigateOptions()
                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
            String title = page.title();
            String text = page.locator("body").innerText();
            context.output().println("Browser inspection complete (temporary, read-only session).");
            context.output().println("URL: " + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
            context.output().println("Title: " + prefix(oneLine(title), 256));
            context.output().println("Visible text:");
            context.output().println(BrowserInspectCommand.bound(text));
            return KaosApplication.SUCCESS;
        } catch (PlaywrightException exception) {
            context.errorOutput().println(
                    "Browser inspection failed. Check Chromium with the Gradle task 'installChromium' "
                            + "and that the local page is reachable.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private static void allowLoopbackOnly(Route route) {
        if (BrowserInspectCommand.allowRequest(route.request().url(), route.request().method(),
                route.request().resourceType())) route.resume();
        else route.abort();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    private static String prefix(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints)) + "...";
    }
}
