package io.kaos.app;

import io.kaos.app.config.ApplicationConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Test-only support for exercising the KAOS application boundary. */
final class KaosApplicationHarness {
    static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofSeconds(5);

    private static final String APPLICATION_NAME_ENVIRONMENT = "KAOS_APP_NAME";
    private static final List<String> INHERITED_JAVA_OPTION_ENVIRONMENT =
            List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS");

    private KaosApplicationHarness() {
    }

    static Result run(String... arguments) {
        return run(
                new ApplicationConfiguration(ApplicationConfiguration.DEFAULT_APPLICATION_NAME),
                arguments);
    }

    static Result run(ApplicationConfiguration configuration, String... arguments) {
        return capture((output, errorOutput) ->
                KaosApplication.run(arguments, configuration, output, errorOutput));
    }

    static Result launch(
            Supplier<ApplicationConfiguration> configurationLoader, String... arguments) {
        return capture((output, errorOutput) ->
                KaosApplication.launch(arguments, configurationLoader, output, errorOutput));
    }

    static Result capture(Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream capturedError = new ByteArrayOutputStream();

        try (PrintStream testOutput =
                        new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
                PrintStream testError =
                        new PrintStream(capturedError, true, StandardCharsets.UTF_8)) {
            int exitCode = invocation.invoke(testOutput, testError);
            return new Result(
                    exitCode,
                    capturedOutput.toString(StandardCharsets.UTF_8),
                    capturedError.toString(StandardCharsets.UTF_8));
        }
    }

    static Result process(String... arguments) {
        return processMain(
                KaosApplication.class, DEFAULT_PROCESS_TIMEOUT, null, arguments);
    }

    static Result processWithApplicationName(String applicationName, String... arguments) {
        Objects.requireNonNull(applicationName, "applicationName");
        return processMain(
                KaosApplication.class,
                DEFAULT_PROCESS_TIMEOUT,
                applicationName,
                arguments);
    }

    static Result processMain(
            Class<?> mainClass,
            Duration timeout,
            String applicationName,
            String... arguments) {
        Objects.requireNonNull(mainClass, "mainClass");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(arguments, "arguments");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(testClassPath());
        command.add(mainClass.getName());
        command.addAll(List.of(arguments));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.environment().remove(APPLICATION_NAME_ENVIRONMENT);
        INHERITED_JAVA_OPTION_ENVIRONMENT.forEach(processBuilder.environment()::remove);
        if (applicationName != null) {
            processBuilder.environment().put(APPLICATION_NAME_ENVIRONMENT, applicationName);
        }

        Process process;
        try {
            process = processBuilder.start();
        } catch (IOException exception) {
            throw new AssertionError(
                    "Unable to start the " + mainClass.getSimpleName() + " test process.",
                    exception);
        }

        boolean completed;
        try {
            completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            terminate(process);
            Thread.currentThread().interrupt();
            throw new AssertionError(
                    mainClass.getSimpleName() + " test process wait was interrupted.", exception);
        }

        if (!completed) {
            terminate(process);
            throw new AssertionError(
                    mainClass.getSimpleName()
                            + " test process exceeded "
                            + timeout.toMillis()
                            + " ms and was terminated.");
        }

        return new Result(
                process.exitValue(),
                read(process.getInputStream(), mainClass, "standard output"),
                read(process.getErrorStream(), mainClass, "standard error"));
    }

    private static Path javaExecutable() {
        String executableName = System.getProperty("os.name", "")
                        .toLowerCase()
                        .contains("win")
                ? "java.exe"
                : "java";
        return Path.of(System.getProperty("java.home"), "bin", executableName);
    }

    private static String testClassPath() {
        Set<String> entries = new LinkedHashSet<>();
        entries.add(codeSourcePath(KaosApplication.class));
        entries.add(codeSourcePath(KaosApplicationHarness.class));
        return String.join(File.pathSeparator, entries);
    }

    private static String codeSourcePath(Class<?> type) {
        try {
            return Path.of(type.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException | RuntimeException exception) {
            throw new AssertionError(
                    "Unable to resolve the test classpath for " + type.getSimpleName() + ".",
                    exception);
        }
    }

    private static String read(InputStream stream, Class<?> mainClass, String streamName) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError(
                    "Unable to read " + streamName + " from " + mainClass.getSimpleName() + ".",
                    exception);
        }
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    record Result(int exitCode, String standardOutput, String errorOutput) {
    }

    @FunctionalInterface
    interface Invocation {
        int invoke(PrintStream output, PrintStream errorOutput);
    }
}
