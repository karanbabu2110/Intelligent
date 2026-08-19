package io.kaos.app.config;

/**
 * Immutable configuration required to start the current KAOS application.
 *
 * @param applicationName safe display name used by the local startup diagnostic
 */
public record ApplicationConfiguration(String applicationName) {
    public static final String DEFAULT_APPLICATION_NAME = "KAOS";
    public static final String NAME_SYSTEM_PROPERTY = "kaos.app.name";
    public static final String NAME_ENVIRONMENT_VARIABLE = "KAOS_APP_NAME";
    public static final int MAX_APPLICATION_NAME_CODE_POINTS = 64;

    public ApplicationConfiguration {
        applicationName = validateName(applicationName, "application name");
    }

    /**
     * Loads the current local process configuration.
     *
     * <p>The system property has precedence over the environment variable. If
     * neither exists, the safe default is used.</p>
     *
     * @return validated application configuration
     * @throws IllegalStateException if the process configuration cannot be read
     * @throws IllegalArgumentException if a configured value is invalid
     */
    public static ApplicationConfiguration load() {
        try {
            return resolve(
                    System.getProperty(NAME_SYSTEM_PROPERTY),
                    System.getenv(NAME_ENVIRONMENT_VARIABLE));
        } catch (SecurityException exception) {
            throw new IllegalStateException(
                    "Unable to read local KAOS application configuration.", exception);
        }
    }

    static ApplicationConfiguration resolve(String systemPropertyValue, String environmentValue) {
        if (systemPropertyValue != null) {
            return new ApplicationConfiguration(
                    validateName(systemPropertyValue, "system property '" + NAME_SYSTEM_PROPERTY + "'"));
        }
        if (environmentValue != null) {
            return new ApplicationConfiguration(
                    validateName(
                            environmentValue,
                            "environment variable '" + NAME_ENVIRONMENT_VARIABLE + "'"));
        }
        return new ApplicationConfiguration(DEFAULT_APPLICATION_NAME);
    }

    private static String validateName(String value, String source) {
        if (value == null) {
            throw new IllegalArgumentException(source + " must not be null.");
        }

        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(source + " must not be blank.");
        }

        int length = normalized.codePointCount(0, normalized.length());
        if (length > MAX_APPLICATION_NAME_CODE_POINTS) {
            throw new IllegalArgumentException(
                    source + " must contain at most " + MAX_APPLICATION_NAME_CODE_POINTS + " characters.");
        }

        boolean containsUnsafeCharacter = normalized.codePoints()
                .anyMatch(codePoint -> !isAllowedNameCharacter(codePoint));
        if (containsUnsafeCharacter) {
            throw new IllegalArgumentException(
                    source
                            + " may contain only letters, numbers, spaces, periods, underscores, or hyphens.");
        }

        return normalized;
    }

    private static boolean isAllowedNameCharacter(int codePoint) {
        int characterType = Character.getType(codePoint);
        return Character.isLetterOrDigit(codePoint)
                || characterType == Character.NON_SPACING_MARK
                || characterType == Character.COMBINING_SPACING_MARK
                || characterType == Character.ENCLOSING_MARK
                || codePoint == ' '
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '-';
    }
}
