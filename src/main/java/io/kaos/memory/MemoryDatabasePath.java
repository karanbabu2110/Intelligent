package io.kaos.memory;

import java.nio.file.Path;

/** Resolves the fixed local memory database path owned by KAOS. */
public final class MemoryDatabasePath {
    public static final String DIRECTORY_SYSTEM_PROPERTY = "kaos.memory.data-directory";
    public static final String DIRECTORY_ENVIRONMENT_VARIABLE = "KAOS_MEMORY_DATA_DIRECTORY";
    public static final String DATABASE_FILENAME = "memory.db";
    private static final String DEFAULT_DIRECTORY_NAME = ".kaos";

    private MemoryDatabasePath() {
    }

    public static Path load() {
        try {
            return resolve(
                    System.getProperty(DIRECTORY_SYSTEM_PROPERTY),
                    System.getenv(DIRECTORY_ENVIRONMENT_VARIABLE),
                    System.getProperty("user.home"));
        } catch (SecurityException exception) {
            throw MemoryStorageException.unavailable();
        }
    }

    static Path resolve(String systemProperty, String environment, String userHome) {
        String configuredDirectory = systemProperty != null ? systemProperty : environment;
        Path directory;
        if (configuredDirectory != null) {
            if (configuredDirectory.isBlank()) {
                throw new IllegalArgumentException("memory data directory must not be blank");
            }
            directory = Path.of(configuredDirectory);
        } else {
            if (userHome == null || userHome.isBlank()) {
                throw MemoryStorageException.unavailable();
            }
            directory = Path.of(userHome, DEFAULT_DIRECTORY_NAME);
        }

        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path databasePath = normalizedDirectory.resolve(DATABASE_FILENAME).normalize();
        if (!normalizedDirectory.equals(databasePath.getParent())) {
            throw new IllegalArgumentException("memory database path is invalid");
        }
        return databasePath;
    }
}
