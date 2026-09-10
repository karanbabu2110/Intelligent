package io.kaos.app;

import io.kaos.tool.StandardTools;
import io.kaos.tool.ToolRegistry;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.websearch.SearxngClient;
import java.util.Objects;
import java.util.function.Supplier;

/** Displays the registered tool catalog and privacy-safe local configuration state. */
final class ToolCatalogCommand {
    private final CommandContext context;
    private final Supplier<ToolRegistry> registryLoader;

    ToolCatalogCommand(CommandContext context,
            Supplier<ReadLocalFilePermissionValidator> fileConfiguration,
            Supplier<HttpGetPermissionValidator> httpConfiguration,
            Supplier<SearxngClient> searchConfiguration) {
        this(context, () -> StandardTools.create(fileConfiguration, httpConfiguration, searchConfiguration));
    }

    ToolCatalogCommand(CommandContext context, ToolRegistry registry) {
        this(context, () -> registry);
    }

    ToolCatalogCommand(CommandContext context, Supplier<ToolRegistry> registryLoader) {
        this.context = Objects.requireNonNull(context, "context");
        this.registryLoader = Objects.requireNonNull(registryLoader, "registryLoader");
    }

    int execute() {
        context.output().println("KAOS tool catalog:");
        for (var tool : registryLoader.get().tools()) {
            var descriptor = tool.descriptor();
            context.output().println(descriptor.name() + " | " + descriptor.purpose()
                    + " | approval: " + (descriptor.approvalRequired() ? "required" : "not required")
                    + " | configuration: " + (tool.configured() ? "configured" : "unavailable"));
        }
        context.output().println(
                "Configuration status is local-only; no file, DNS, service, or web request was made.");
        context.output().println("Every execution still requires exact validation and approval.");
        return KaosApplication.SUCCESS;
    }

}
