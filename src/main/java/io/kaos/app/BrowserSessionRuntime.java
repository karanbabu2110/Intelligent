package io.kaos.app;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
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
                    "Browser session is open. Commands: inspect [loopback-url], back, forward, reload, "
                            + "fill <selector> <text>, status, close.");
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
                BrowserSessionCommand.SessionInput inputCommand =
                        BrowserSessionCommand.parseInput(line);
                try {
                    switch (inputCommand.action()) {
                        case CLOSE -> {
                            context.output().println("Browser session closed.");
                            return exitCode;
                        }
                        case STATUS -> {
                            context.output().println("Browser session is open.");
                            context.output().println("URL: "
                                    + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
                        }
                        case INSPECT_CURRENT -> printPage(context, page);
                        case INSPECT_URL -> inspect(context, page, inputCommand.uri());
                        case FILL -> {
                            int fillExitCode = fill(context, page,
                                    inputCommand.selector(), inputCommand.value());
                            if (fillExitCode != KaosApplication.SUCCESS) exitCode = fillExitCode;
                        }
                        case BACK -> historyNavigation(context, page, page.goBack(
                                new Page.GoBackOptions().setTimeout(NAVIGATION_TIMEOUT_MILLIS)));
                        case FORWARD -> historyNavigation(context, page, page.goForward(
                                new Page.GoForwardOptions().setTimeout(NAVIGATION_TIMEOUT_MILLIS)));
                        case RELOAD -> {
                            page.reload(new Page.ReloadOptions()
                                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
                            printPage(context, page);
                        }
                        case INVALID_URL -> {
                            context.errorOutput().println(
                                    "Expected inspect followed by an HTTP URL on 127.0.0.1 with an explicit port.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                        case INVALID_INPUT -> {
                            context.errorOutput().println(
                                    "Expected fill <single-token-selector> <text> with at most 256 Unicode code points.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                        case UNKNOWN -> {
                            context.errorOutput().println(
                                    "Expected inspect [loopback-url], back, forward, reload, "
                                            + "fill <selector> <text>, status, or close.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                    }
                } catch (PlaywrightException exception) {
                    context.errorOutput().println(
                            "Browser command failed. Check Chromium, the selector, and that the local page is reachable.");
                    exitCode = KaosApplication.APPLICATION_ERROR;
                }
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
        printPage(context, page);
        return KaosApplication.SUCCESS;
    }

    private static void printPage(CommandContext context, Page page) {
        String title = page.title();
        String text = page.locator("body").innerText();
        context.output().println("Browser inspection complete (temporary session).");
        context.output().println("URL: " + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
        context.output().println("Title: " + prefix(oneLine(title), 256));
        context.output().println("Visible text:");
        context.output().println(BrowserInspectCommand.bound(text));
    }

    private static void historyNavigation(CommandContext context, Page page, Response response) {
        if (response == null) {
            context.output().println("No page is available in that direction in this session.");
            return;
        }
        printPage(context, page);
    }

    private static int fill(CommandContext context, Page page, String selector, String value) {
        Locator target = page.locator(selector);
        if (target.count() != 1) {
            context.errorOutput().println(
                    "Fill rejected. The selector must match exactly one field.");
            return KaosApplication.USAGE_ERROR;
        }

        String tagName = target.evaluate("element => element.tagName.toLowerCase()").toString();
        String type = "input".equals(tagName) ? target.getAttribute("type") : null;
        if (!BrowserSessionCommand.eligibleFillTarget(tagName, type,
                target.isVisible(), target.isEnabled(), target.isEditable())) {
            context.errorOutput().println(
                    "Fill rejected. Target must be one visible, enabled, editable text input or textarea.");
            return KaosApplication.USAGE_ERROR;
        }

        String maxLength = target.getAttribute("maxlength");
        if (maxLength != null) {
            try {
                int maximum = Integer.parseInt(maxLength);
                if (maximum >= 0 && value.length() > maximum) {
                    context.errorOutput().println(
                            "Fill rejected. The value exceeds this field's maximum length.");
                    return KaosApplication.USAGE_ERROR;
                }
            } catch (NumberFormatException ignored) {
                // Invalid maxlength attributes have no browser-enforced limit.
            }
        }
        target.fill(value);
        context.output().println("Filled one eligible text field. It was not submitted; value was not echoed.");
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
