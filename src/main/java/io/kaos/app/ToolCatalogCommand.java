package io.kaos.app;

import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetToolContract;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionException;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.websearch.SearxngClient;
import io.kaos.tool.websearch.WebSearchException;
import io.kaos.tool.websearch.WebSearchToolContract;
import java.util.Objects;
import java.util.function.Supplier;

/** Displays the fixed tool catalog and privacy-safe local configuration state. */
final class ToolCatalogCommand {
    private final CommandContext context;
    private final Supplier<ReadLocalFilePermissionValidator> fileConfiguration;
    private final Supplier<HttpGetPermissionValidator> httpConfiguration;
    private final Supplier<SearxngClient> searchConfiguration;

    ToolCatalogCommand(CommandContext context,
            Supplier<ReadLocalFilePermissionValidator> fileConfiguration,
            Supplier<HttpGetPermissionValidator> httpConfiguration,
            Supplier<SearxngClient> searchConfiguration) {
        this.context = Objects.requireNonNull(context, "context");
        this.fileConfiguration = Objects.requireNonNull(fileConfiguration, "fileConfiguration");
        this.httpConfiguration = Objects.requireNonNull(httpConfiguration, "httpConfiguration");
        this.searchConfiguration = Objects.requireNonNull(searchConfiguration, "searchConfiguration");
    }

    int execute() {
        context.output().println("KAOS tool catalog:");
        entry(ReadLocalFileToolContract.NAME, "Read one bounded local file",
                configuration(fileConfiguration));
        entry(HttpGetToolContract.NAME, "Retrieve one allowed HTTPS resource",
                configuration(httpConfiguration));
        entry(WebSearchToolContract.NAME, "Discover public URLs through SearXNG",
                configuration(searchConfiguration));
        context.output().println(
                "Configuration status is local-only; no file, DNS, service, or web request was made.");
        context.output().println("Every execution still requires exact validation and approval.");
        return KaosApplication.SUCCESS;
    }

    private void entry(String name, String purpose, String configuration) {
        context.output().println(name + " | " + purpose
                + " | approval: required | configuration: " + configuration);
    }

    private static String configuration(Supplier<?> loader) {
        try {
            return loader.get() == null ? "unavailable" : "configured";
        } catch (ReadLocalFilePermissionException
                | HttpGetException
                | WebSearchException exception) {
            return "unavailable";
        }
    }
}
