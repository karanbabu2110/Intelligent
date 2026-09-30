package io.kaos.app.browser;

import io.kaos.app.KaosApplication;

import io.kaos.app.CommandContext;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Playwright-backed foreground session, loaded only when the session command runs. */
final class BrowserSessionRuntime {
    private static final int NAVIGATION_TIMEOUT_MILLIS = 15000;
    private static final int MAX_RECORDED_STEPS = 20;
    private static final int MAX_RECORDED_URL_CODE_POINTS = 512;

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
            AtomicReference<URI> selectedNavigation = new AtomicReference<>(initialUri);
            Page page = browserContext.newPage();
            BrowserRequestGuard.attach(browserContext, page, selectedNavigation);
            int exitCode = inspect(context, page, initialUri, selectedNavigation);
            URI lastKnownGoodUri = URI.create(page.url());
            List<URI> navigationHistory = new ArrayList<>();
            navigationHistory.add(URI.create(page.url()));
            int historyIndex = 0;
            Deque<String> recordedSteps = new ArrayDeque<>();
            boolean recording = false;
            context.output().println(
                    "Browser session is open. Commands: inspect [loopback-url], back, forward, reload, "
                            + "fill <selector> <text>, record on|off|show|clear, status, close.");
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
                        case INSPECT_URL -> {
                            inspect(context, page, inputCommand.uri(), selectedNavigation);
                            lastKnownGoodUri = URI.create(page.url());
                            while (navigationHistory.size() > historyIndex + 1) {
                                navigationHistory.remove(navigationHistory.size() - 1);
                            }
                            navigationHistory.add(lastKnownGoodUri);
                            historyIndex = navigationHistory.size() - 1;
                            if (recording) recordStep(recordedSteps, "inspect", page);
                        }
                        case FILL -> {
                            int fillExitCode = fill(context, input, page,
                                    inputCommand.selector(), inputCommand.value());
                            if (fillExitCode != KaosApplication.SUCCESS) exitCode = fillExitCode;
                        }
                        case BACK -> {
                            URI target = historyIndex > 0
                                    ? navigationHistory.get(historyIndex - 1) : URI.create(page.url());
                            selectedNavigation.set(target);
                            Response response = page.goBack(new Page.GoBackOptions()
                                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
                            ensureSuccessfulNavigation(response);
                            historyNavigation(context, page, response);
                            if (response != null) {
                                historyIndex = Math.max(0, historyIndex - 1);
                                lastKnownGoodUri = URI.create(page.url());
                                if (recording) recordStep(recordedSteps, "back", page);
                            }
                        }
                        case FORWARD -> {
                            URI target = historyIndex + 1 < navigationHistory.size()
                                    ? navigationHistory.get(historyIndex + 1) : URI.create(page.url());
                            selectedNavigation.set(target);
                            Response response = page.goForward(new Page.GoForwardOptions()
                                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
                            ensureSuccessfulNavigation(response);
                            historyNavigation(context, page, response);
                            if (response != null) {
                                historyIndex = Math.min(navigationHistory.size() - 1, historyIndex + 1);
                                lastKnownGoodUri = URI.create(page.url());
                                if (recording) recordStep(recordedSteps, "forward", page);
                            }
                        }
                        case RELOAD -> {
                            selectedNavigation.set(URI.create(page.url()));
                            Response response = page.reload(new Page.ReloadOptions()
                                    .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
                            ensureSuccessfulNavigation(response);
                            printPage(context, page);
                            lastKnownGoodUri = URI.create(page.url());
                            if (recording) recordStep(recordedSteps, "reload", page);
                        }
                        case RECORD_ON -> {
                            recording = true;
                            context.output().println(
                                    "Workflow recording started. Only successful navigation steps are recorded.");
                        }
                        case RECORD_OFF -> {
                            recording = false;
                            context.output().println("Workflow recording stopped.");
                        }
                        case RECORD_SHOW -> printRecordedSteps(context, recordedSteps);
                        case RECORD_CLEAR -> {
                            recordedSteps.clear();
                            recording = false;
                            context.output().println("Recorded workflow cleared; recording stopped.");
                        }
                        case INVALID_URL -> {
                            context.errorOutput().println(
                                    "Expected inspect followed by an HTTP URL on 127.0.0.1 with an explicit port.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                        case INVALID_INPUT -> {
                            context.errorOutput().println(
                                    "Expected fill <single-token-selector> <text> with at most 256 Unicode code points, "
                                            + "or record on, off, show, or clear.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                        case UNKNOWN -> {
                            context.errorOutput().println(
                                    "Expected inspect [loopback-url], back, forward, reload, "
                                            + "fill <selector> <text>, record on|off|show|clear, status, or close.");
                            exitCode = KaosApplication.USAGE_ERROR;
                        }
                    }
                } catch (PlaywrightException | BrowserNavigationFailureException exception) {
                    if (isNavigationAction(inputCommand.action())) {
                        context.errorOutput().println(
                                "Browser navigation failed. Check that the local page is available.");
                        if (restoreLastKnownGoodPage(
                                context, page, lastKnownGoodUri, selectedNavigation)) {
                            context.output().println(
                                    "Recovered the last successfully loaded page. The session remains open.");
                        } else {
                            context.errorOutput().println(
                                    "Could not restore the last successfully loaded page. The session remains open.");
                        }
                    } else {
                        context.errorOutput().println(
                                "Browser command failed. Check Chromium, the selector, and that the local page is reachable.");
                    }
                    exitCode = KaosApplication.APPLICATION_ERROR;
                }
            }
        } catch (PlaywrightException | BrowserNavigationFailureException exception) {
            context.errorOutput().println(
                    "Browser session failed to start or close. Check Chromium with the Gradle task "
                            + "'installChromium' and that the local page is reachable.");
            return KaosApplication.APPLICATION_ERROR;
        }
    }

    private static int inspect(
            CommandContext context,
            Page page,
            URI uri,
            AtomicReference<URI> selectedNavigation) {
        selectedNavigation.set(uri);
        Response response = page.navigate(uri.toASCIIString(), new Page.NavigateOptions()
                .setTimeout(NAVIGATION_TIMEOUT_MILLIS));
        ensureSuccessfulNavigation(response);
        printPage(context, page);
        return KaosApplication.SUCCESS;
    }

    private static void printPage(CommandContext context, Page page) {
        String title = page.title();
        String text = BrowserPageText.extract(page);
        context.output().println("Browser inspection complete (temporary session).");
        context.output().println("URL: " + BrowserInspectCommand.withoutQueryAndFragment(page.url()));
        context.output().println("Title: " + prefix(oneLine(title), 256));
        context.output().println("Visible text:");
        context.output().println(text);
    }

    private static void historyNavigation(CommandContext context, Page page, Response response) {
        if (response == null) {
            context.output().println("No page is available in that direction in this session.");
            return;
        }
        printPage(context, page);
    }

    private static boolean restoreLastKnownGoodPage(
            CommandContext context,
            Page page,
            URI lastKnownGoodUri,
            AtomicReference<URI> selectedNavigation) {
        try {
            selectedNavigation.set(lastKnownGoodUri);
            Response response = page.navigate(lastKnownGoodUri.toASCIIString(),
                    new Page.NavigateOptions().setTimeout(NAVIGATION_TIMEOUT_MILLIS));
            ensureSuccessfulNavigation(response);
            printPage(context, page);
            return true;
        } catch (PlaywrightException | BrowserNavigationFailureException exception) {
            return false;
        }
    }

    private static boolean isNavigationAction(BrowserSessionCommand.SessionAction action) {
        return switch (action) {
            case INSPECT_URL, BACK, FORWARD, RELOAD -> true;
            default -> false;
        };
    }

    private static void recordStep(Deque<String> recordedSteps, String action, Page page) {
        if (recordedSteps.size() == MAX_RECORDED_STEPS) recordedSteps.removeFirst();
        String safeUrl = BrowserInspectCommand.withoutQueryAndFragment(page.url());
        recordedSteps.addLast(action + " "
                + prefix(safeUrl, MAX_RECORDED_URL_CODE_POINTS - 3));
    }

    private static void printRecordedSteps(CommandContext context, Deque<String> recordedSteps) {
        if (recordedSteps.isEmpty()) {
            context.output().println("No workflow steps recorded in this session.");
            return;
        }
        context.output().println("Recorded workflow (last " + recordedSteps.size()
                + " successful navigation steps; in-memory only):");
        int number = 1;
        for (String step : recordedSteps) context.output().println(number++ + ". " + step);
    }

    private static void ensureSuccessfulNavigation(Response response) {
        if (response != null && response.status() >= 400) {
            throw new BrowserNavigationFailureException();
        }
    }

    private static final class BrowserNavigationFailureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static int fill(
            CommandContext context, BufferedReader input, Page page, String selector, String value) {
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

        context.output().printf(
                "Approve filling one eligible local text field with %d code points? "
                        + "KAOS will not repeat the value. Type approve or deny: ",
                value.codePointCount(0, value.length()));
        context.output().flush();
        String response;
        try {
            response = input.readLine();
        } catch (IOException exception) {
            context.errorOutput().println("Fill cancelled; approval could not be read.");
            return KaosApplication.APPLICATION_ERROR;
        }
        if (!BrowserSessionCommand.approvalGranted(response)) {
            context.output().println("Fill cancelled; approval was not granted.");
            return KaosApplication.SUCCESS;
        }

        if (target.count() != 1) {
            context.output().println("Fill cancelled; the field changed before approval completed.");
            return KaosApplication.SUCCESS;
        }
        tagName = target.evaluate("element => element.tagName.toLowerCase()").toString();
        type = "input".equals(tagName) ? target.getAttribute("type") : null;
        if (!BrowserSessionCommand.eligibleFillTarget(tagName, type,
                target.isVisible(), target.isEnabled(), target.isEditable())) {
            context.output().println("Fill cancelled; the field is no longer eligible.");
            return KaosApplication.SUCCESS;
        }
        target.fill(value);
        context.output().println(
                "Filled one eligible text field. It was not submitted; KAOS did not repeat the value.");
        return KaosApplication.SUCCESS;
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
