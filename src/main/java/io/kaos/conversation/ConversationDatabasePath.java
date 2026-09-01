package io.kaos.conversation;

import java.nio.file.Path;

/** Resolves the single local conversation database path owned by KAOS. */
public final class ConversationDatabasePath {
    public static final String DIRECTORY_SYSTEM_PROPERTY = "kaos.conversation.data-directory";
    public static final String DIRECTORY_ENVIRONMENT_VARIABLE =
            "KAOS_CONVERSATION_DATA_DIRECTORY";
    public static final String DATABASE_FILENAME = "conversations.db";
    static final String DEFAULT_DIRECTORY_NAME = ".kaos";

    private ConversationDatabasePath() {
    }

    public static Path load() {
        try {
            return resolve(
                    System.getProperty(DIRECTORY_SYSTEM_PROPERTY),
                    System.getenv(DIRECTORY_ENVIRONMENT_VARIABLE),
                    System.getProperty("user.home"));
        } catch (SecurityException exception) {
            throw new IllegalStateException("Conversation database path could not be read.", exception);
        }
    }

    static Path resolve(String systemProperty, String environment, String userHome) {
        String configuredDirectory = systemProperty != null ? systemProperty : environment;
        Path directory;
        if (configuredDirectory != null) {
            if (configuredDirectory.isBlank()) {
                throw new IllegalArgumentException("conversation data directory must not be blank");
            }
            directory = Path.of(configuredDirectory);
        } else {
            if (userHome == null || userHome.isBlank()) {
                throw new IllegalStateException("User home is unavailable.");
            }
            directory = Path.of(userHome, DEFAULT_DIRECTORY_NAME);
        }

        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path databasePath = normalizedDirectory.resolve(DATABASE_FILENAME).normalize();
        if (!normalizedDirectory.equals(databasePath.getParent())) {
            throw new IllegalArgumentException("conversation database path is invalid");
        }
        return databasePath;
    }
}
