package io.kaos.app;

import java.io.BufferedReader;
import java.io.IOException;

/** Shares the conversation reader so approval cannot consume the next prompt. */
@FunctionalInterface
interface ApprovalInput {
    String read() throws IOException;

    static String readBounded(BufferedReader reader) throws IOException {
        StringBuilder value = new StringBuilder();
        boolean excessive = false;
        int character;
        while ((character = reader.read()) >= 0 && character != '\n') {
            if (value.length() < 33) value.append((char) character);
            else excessive = true;
        }
        if (character < 0) return null;
        return excessive ? "" : value.toString();
    }
}
