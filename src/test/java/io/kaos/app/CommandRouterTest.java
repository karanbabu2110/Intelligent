package io.kaos.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CommandRouterTest {
    @Test
    void dispatchesOnlyTheSelectedCommand() {
        ByteArrayOutputStream standardBytes = new ByteArrayOutputStream();
        AtomicInteger commandCalls = new AtomicInteger();
        CommandRouter router = router(standardBytes, commandCalls);

        int exitCode = router.route(new String[] {"status"});

        assertEquals(KaosApplication.SUCCESS, exitCode);
        assertEquals(0, commandCalls.get());
        assertEquals(
                "KAOS application baseline is running." + System.lineSeparator(),
                standardBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsAnUnknownCommandWithoutInvokingAHandler() {
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        AtomicInteger commandCalls = new AtomicInteger();
        CommandRouter.Command command = () -> {
            commandCalls.incrementAndGet();
            return KaosApplication.SUCCESS;
        };
        CommandContext context = context(errorBytes);
        CommandRouter router = new CommandRouter(
                context, argument -> command.execute(), command,
                command, argument -> command.execute(), command);

        int exitCode = router.route(new String[] {"unknown"});

        assertEquals(KaosApplication.USAGE_ERROR, exitCode);
        assertEquals(0, commandCalls.get());
        assertEquals(
                "Unknown command. Run 'kaos help' for usage." + System.lineSeparator(),
                errorBytes.toString(StandardCharsets.UTF_8));
    }

    private static CommandRouter router(
            ByteArrayOutputStream standardBytes,
            AtomicInteger commandCalls) {
        CommandRouter.Command command = () -> {
            commandCalls.incrementAndGet();
            return KaosApplication.SUCCESS;
        };
        return new CommandRouter(
                context(standardBytes, new ByteArrayOutputStream()),
                argument -> command.execute(),
                command,
                command,
                argument -> command.execute(),
                command);
    }

    private static CommandContext context(ByteArrayOutputStream errorBytes) {
        return context(new ByteArrayOutputStream(), errorBytes);
    }

    private static CommandContext context(
            ByteArrayOutputStream standardBytes,
            ByteArrayOutputStream errorBytes) {
        return new CommandContext(
                new ApplicationConfiguration("KAOS"),
                InputStream.nullInputStream(),
                new PrintStream(standardBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errorBytes, true, StandardCharsets.UTF_8));
    }
}
