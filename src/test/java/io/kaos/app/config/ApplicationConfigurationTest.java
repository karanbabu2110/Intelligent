package io.kaos.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApplicationConfigurationTest {
    @Test
    void usesTheSafeDefaultWhenNoOverrideExists() {
        ApplicationConfiguration configuration = ApplicationConfiguration.resolve(null, null);

        assertEquals(ApplicationConfiguration.DEFAULT_APPLICATION_NAME, configuration.applicationName());
    }

    @Test
    void usesTheEnvironmentValueWhenTheSystemPropertyIsAbsent() {
        ApplicationConfiguration configuration =
                ApplicationConfiguration.resolve(null, "Local KAOS");

        assertEquals("Local KAOS", configuration.applicationName());
    }

    @Test
    void givesTheSystemPropertyPrecedenceOverTheEnvironment() {
        ApplicationConfiguration configuration =
                ApplicationConfiguration.resolve("Property KAOS", "Environment KAOS");

        assertEquals("Property KAOS", configuration.applicationName());
    }

    @Test
    void trimsOuterWhitespaceFromAConfiguredName() {
        ApplicationConfiguration configuration =
                ApplicationConfiguration.resolve("  Local KAOS  ", null);

        assertEquals("Local KAOS", configuration.applicationName());
    }

    @Test
    void rejectsABlankConfiguredValueInsteadOfFallingBack() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationConfiguration.resolve("   ", "Environment KAOS"));

        assertTrue(exception.getMessage().contains(ApplicationConfiguration.NAME_SYSTEM_PROPERTY));
        assertTrue(exception.getMessage().contains("must not be blank"));
    }

    @Test
    void rejectsDiagnosticInjectionCharacters() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationConfiguration.resolve("KAOS\nInjected", null));

        assertTrue(exception.getMessage().contains("may contain only"));
    }

    @Test
    void rejectsAnOversizedName() {
        String oversizedName = "K".repeat(ApplicationConfiguration.MAX_APPLICATION_NAME_CODE_POINTS + 1);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ApplicationConfiguration.resolve(null, oversizedName));

        assertTrue(exception.getMessage().contains(ApplicationConfiguration.NAME_ENVIRONMENT_VARIABLE));
        assertTrue(exception.getMessage().contains("at most 64"));
    }

    @Test
    void acceptsInternationalLettersWithoutAllowingControlCharacters() {
        ApplicationConfiguration configuration =
                ApplicationConfiguration.resolve("KAOS भारत", null);

        assertEquals("KAOS भारत", configuration.applicationName());
    }
}
