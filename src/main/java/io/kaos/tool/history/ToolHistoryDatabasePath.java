package io.kaos.tool.history;

import java.nio.file.Path;

/** Resolves the local database owned only by tool execution history. */
public final class ToolHistoryDatabasePath {
    public static final String DIRECTORY_SYSTEM_PROPERTY = "kaos.tool-history.data-directory";
    public static final String DIRECTORY_ENVIRONMENT_VARIABLE = "KAOS_TOOL_HISTORY_DATA_DIRECTORY";
    public static final String DATABASE_FILENAME = "tool-history.db";
    private static final String DEFAULT_DIRECTORY_NAME = ".kaos";

    private ToolHistoryDatabasePath() { }

    public static Path load() {
        try {
            return resolve(System.getProperty(DIRECTORY_SYSTEM_PROPERTY),
                    System.getenv(DIRECTORY_ENVIRONMENT_VARIABLE), System.getProperty("user.home"));
        } catch (SecurityException exception) {
            throw ToolHistoryStorageException.unavailable();
        }
    }

    static Path resolve(String systemProperty, String environment, String userHome) {
        String configured = systemProperty != null ? systemProperty : environment;
        Path directory;
        if (configured != null) {
            if (configured.isBlank()) throw new IllegalArgumentException("tool history data directory must not be blank");
            directory = Path.of(configured);
        } else {
            if (userHome == null || userHome.isBlank()) throw ToolHistoryStorageException.unavailable();
            directory = Path.of(userHome, DEFAULT_DIRECTORY_NAME);
        }
        Path normalized = directory.toAbsolutePath().normalize();
        Path database = normalized.resolve(DATABASE_FILENAME).normalize();
        if (!normalized.equals(database.getParent())) {
            throw new IllegalArgumentException("tool history database path is invalid");
        }
        return database;
    }
}
