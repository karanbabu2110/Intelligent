package io.kaos.tool;

import io.kaos.tool.httpget.HttpGet;
import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetToolContract;
import io.kaos.tool.readlocalfile.ReadLocalFile;
import io.kaos.tool.readlocalfile.ReadLocalFilePermissionValidator;
import io.kaos.tool.readlocalfile.ReadLocalFileToolContract;
import io.kaos.tool.websearch.SearxngClient;
import io.kaos.tool.websearch.WebSearch;
import io.kaos.tool.websearch.WebSearchToolContract;
import java.util.List;
import java.util.function.Supplier;

/** Explicit local composition and operation scopes; no discovery or global service locator. */
public final class StandardTools {
    public static final List<String> LOCAL = List.of(ReadLocalFileToolContract.NAME, WebSearchToolContract.NAME);
    public static final List<String> READ_LOCAL_FILE = List.of(ReadLocalFileToolContract.NAME);
    public static final List<String> HTTP_GET = List.of(HttpGetToolContract.NAME);

    private StandardTools() { }

    public static ToolRegistry create() {
        return create(ReadLocalFilePermissionValidator::load,
                HttpGetPermissionValidator::load, SearxngClient::load);
    }

    public static ToolRegistry create(Supplier<ReadLocalFilePermissionValidator> file,
            Supplier<HttpGetPermissionValidator> http, Supplier<SearxngClient> search) {
        return new ToolRegistry(List.of(new ReadLocalFile(file), new HttpGet(http), new WebSearch(search)));
    }
}
