package io.kaos.app;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Route;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Playwright-backed foreground session, loaded only when the session command runs. */
final class BrowserSessionRuntime {
    private static final int NAVIGATION_TIMEOUT_MILLIS = 15000;

    private BrowserSessionRuntime() {
    }

    static int run(CommandContext context, URI initialUri) {
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions()
                     .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                     .setHeadless(true));
             BrowserContext browserContext = browser.newContext(
                     new Browser.NewContextOptions()
                             .setJavaScriptEnabled(false)
                             .setAcceptDownloads(false))) {
            browserContext.route("**/*", BrowserSessionRuntime::allowLoopbackOnly);
            Page page = browserContext.newPage();
            int exitCode = inspect(context, page, initialUri);
            context.output().println(
                    "Browser session is open. Commands: inspect <loopback-url>, status, close.");
            BufferedReader input = new BufferedReader(
                    new InputStreamReader(context.input(), StandardCharsets.UTF_8));
            while (true) {
                context.output().print("browser-session> ");
                context.output().flush();
                String line;
                try {
                    line = input.readLine();
                } catch (IOException exception) {
                    context.errorOutput().println("Unable to read browser session command; closing session.");
                    return KaosApplication.APPLICATION_ERROR;
                }
                if (line == null) {
                    context.output().println("Browser session closed at end of input.");
                    return exitCode;
                }
                String command = line.strip();
                if ("close".equals(command)) {
                    context.output().println("Browser session closed.");
                    return exitCode;
                }
                if ("status".equals(command)) {
                    context.output().println("Browser session is open.");
                    context.output().println("URL: "
                            + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
                    continue;
                }
                if (command.startsWith("inspect ")) {
                    URI uri = BrowserInspectCommand.parse(command.substring("inspect ".length()).strip());
                    if (uri == null) {
                        context.errorOutput().println(
                                "Expected inspect followed by an HTTP URL on 127.0.0.1 with an explicit port.");
                        exitCode = KaosApplication.USAGE_ERROR;
                        continue;
                    }
                    try {
                        int inspectionExitCode = inspect(context, page, uri);
                        if (inspectionExitCode != KaosApplication.SUCCESS) exitCode = inspectionExitCode;
                    } catch (PlaywrightException exception) {
                        context.errorOutput().println(
                                "Page inspection failed. Check Chromium and that the local page is reachable.");
                        exitCode = KaosApplication.APPLICATION_ERROR;
                    }
                    continue;
                }
                context.errorOutput().println(
                        "Expected inspect <loopback-url>, status, or close.");
                exitCode = KaosApplication.USAGE_ERROR;
            }
        } catch (PlaywrightException exception) {
            context.errorOutput().println(
                    "Browser session failed to start or close. Check Chromium with the Gradle task "
                            + "'installChromium' and that the local page is reachable.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private static int inspect(CommandContext context, Page page, URI uri) {
        page.navigate(uri.toASCIIString(), new Page.NavigateOptions()
                .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
        String title = page.title();
        String text = page.locator("body").innerText();
        context.output().println("Browser inspection complete (temporary session).");
        context.output().println("URL: " + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
        context.output().println("Title: " + prefix(oneLine(title), 256));
        context.output().println("Visible text:");
        context.output().println(BrowserInspectCommand.bound(text));
        return KaosApplication.SUCCESS;
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
