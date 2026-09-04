package io.kaos.knowledge;

import java.nio.file.Path;

/** Resolves the fixed local knowledge database path owned by KAOS. */
public final class KnowledgeDatabasePath {
    public static final String DIRECTORY_SYSTEM_PROPERTY = "kaos.knowledge.data-directory";
    public static final String DIRECTORY_ENVIRONMENT_VARIABLE = "KAOS_KNOWLEDGE_DATA_DIRECTORY";
    public static final String DATABASE_FILENAME = "knowledge.db";
    private static final String DEFAULT_DIRECTORY_NAME = ".kaos";

    private KnowledgeDatabasePath() { }

    public static Path load() {
        try {
            return resolve(System.getProperty(DIRECTORY_SYSTEM_PROPERTY),
                    System.getenv(DIRECTORY_ENVIRONMENT_VARIABLE),
                    System.getProperty("user.home"));
        } catch (SecurityException exception) {
            throw new IllegalStateException("Knowledge database path could not be read.", exception);
        }
    }

    static Path resolve(String property, String environment, String userHome) {
        String configured = property != null ? property : environment;
        Path directory;
        if (configured != null) {
            if (configured.isBlank()) {
                throw new IllegalArgumentException("knowledge data directory must not be blank");
            }
            directory = Path.of(configured);
        } else {
            if (userHome == null || userHome.isBlank()) {
                throw new IllegalStateException("User home is unavailable.");
            }
            directory = Path.of(userHome, DEFAULT_DIRECTORY_NAME);
        }
        Path normalized = directory.toAbsolutePath().normalize();
        Path database = normalized.resolve(DATABASE_FILENAME).normalize();
        if (!normalized.equals(database.getParent())) {
            throw new IllegalArgumentException("knowledge database path is invalid");
        }
        return database;
    }
}
