package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrowserSessionCommandTest {
    @Test
    void parsesSessionNavigationAndCurrentPageInspectionCommands() {
        assertEquals(BrowserSessionCommand.SessionAction.BACK,
                BrowserSessionCommand.parseInput("back").action());
        assertEquals(BrowserSessionCommand.SessionAction.FORWARD,
                BrowserSessionCommand.parseInput("forward").action());
        assertEquals(BrowserSessionCommand.SessionAction.RELOAD,
                BrowserSessionCommand.parseInput("reload").action());
        assertEquals(BrowserSessionCommand.SessionAction.INSPECT_CURRENT,
                BrowserSessionCommand.parseInput("inspect").action());
        assertEquals(new BrowserSessionCommand.SessionInput(
                        BrowserSessionCommand.SessionAction.INSPECT_URL,
                        URI.create("http://127.0.0.1:8080/next")),
                BrowserSessionCommand.parseInput("inspect http://127.0.0.1:8080/next"));
    }

    @Test
    void rejectsExternalInteractiveInspectionAndUnknownCommands() {
        assertEquals(BrowserSessionCommand.SessionAction.INVALID_URL,
                BrowserSessionCommand.parseInput("inspect https://example.com").action());
        assertEquals(BrowserSessionCommand.SessionAction.UNKNOWN,
                BrowserSessionCommand.parseInput("click submit").action());
    }

    @Test
    void parsesFillSelectorAndPreservesTheRequestedText() {
        assertEquals(new BrowserSessionCommand.SessionInput(
                        BrowserSessionCommand.SessionAction.FILL,
                        null,
                        "#email",
                        "user@example.test with spaces"),
                BrowserSessionCommand.parseInput("fill #email user@example.test with spaces"));
    }

    @Test
    void rejectsFillCommandsWithoutSelectorOrWithOverlongText() {
        assertEquals(BrowserSessionCommand.SessionAction.INVALID_INPUT,
                BrowserSessionCommand.parseInput("fill").action());
        assertEquals(BrowserSessionCommand.SessionAction.INVALID_INPUT,
                BrowserSessionCommand.parseInput("fill #input " + "a".repeat(257)).action());
    }

    @Test
    void allowsOnlyVisibleEditableTextInputsAndTextareas() {
        assertTrue(BrowserSessionCommand.eligibleFillTarget(
                "input", "search", true, true, true));
        assertTrue(BrowserSessionCommand.eligibleFillTarget(
                "input", null, true, true, true));
        assertTrue(BrowserSessionCommand.eligibleFillTarget(
                "textarea", null, true, true, true));
        assertFalse(BrowserSessionCommand.eligibleFillTarget(
                "input", "password", true, true, true));
        assertFalse(BrowserSessionCommand.eligibleFillTarget(
                "input", "hidden", false, true, true));
        assertFalse(BrowserSessionCommand.eligibleFillTarget(
                "input", "text", true, false, false));
        assertFalse(BrowserSessionCommand.eligibleFillTarget(
                "button", null, true, true, true));
    }

    @Test
    void grantsOnlyAnExactSingleUseApprovalResponse() {
        assertTrue(BrowserSessionCommand.approvalGranted("approve"));
        assertTrue(BrowserSessionCommand.approvalGranted(" approve "));
        assertFalse(BrowserSessionCommand.approvalGranted("Approve"));
        assertFalse(BrowserSessionCommand.approvalGranted("deny"));
        assertFalse(BrowserSessionCommand.approvalGranted(""));
        assertFalse(BrowserSessionCommand.approvalGranted(null));
    }

    @Test
    void rejectsNonLoopbackUrlBeforeStartingBrowser() {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CommandContext context = new CommandContext(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(error, true, StandardCharsets.UTF_8));

        assertEquals(KaosApplication.USAGE_ERROR,
                new BrowserSessionCommand(context).execute("https://example.com"));
        assertTrue(error.toString(StandardCharsets.UTF_8).contains("no session was started"));
    }
}
