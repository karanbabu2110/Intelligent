package io.kaos.app;

/**
 * The single process entry point for the evolving KAOS application.
 */
public final class KaosApplication {
    private static final String STARTUP_MESSAGE = "KAOS application baseline is running.";

    private KaosApplication() {
    }

    public static void main(String[] args) {
        System.out.println(startupMessage());
    }

    static String startupMessage() {
        return STARTUP_MESSAGE;
    }
}
