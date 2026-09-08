package io.kaos.tool.readlocalfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReadLocalFileRequestTest {
    @Test
    void preservesSupportedRelativePathsExactly() {
        List<String> paths = List.of(
                "notes.txt", "README.md", "logs/app.LOG", "src/Main.java",
                "build.gradle", "settings.gradle.kts", "src/App.kt", "data.json",
                "config.xml", "config.yaml", "config.yml", "app.properties", "data.csv");

        for (String path : paths) {
            assertEquals(path, new ReadLocalFileRequest(path).path());
        }
    }

    @Test
    void acceptsTheExactUnicodePathLimit() {
        String path = "a".repeat(ReadLocalFileRequest.MAX_PATH_CODE_POINTS - 5) + ".java";

        assertEquals(ReadLocalFileRequest.MAX_PATH_CODE_POINTS,
                new ReadLocalFileRequest(path).path().codePointCount(0, path.length()));
    }

    @Test
    void rejectsNullBlankOversizedAndControlContainingPaths() {
        assertThrows(IllegalArgumentException.class, () -> new ReadLocalFileRequest(null));
        assertThrows(IllegalArgumentException.class, () -> new ReadLocalFileRequest(" \t"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileRequest(
                        "a".repeat(ReadLocalFileRequest.MAX_PATH_CODE_POINTS - 4) + ".java"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadLocalFileRequest("safe\nprivate.java"));
    }

    @Test
    void rejectsAbsoluteEscapingAndAmbiguousPaths() {
        List<String> paths = List.of(
                "/etc/hosts.txt", "\\\\server\\share\\file.txt", "C:\\secret.txt",
                "../secret.txt", "src/../secret.txt", "./Main.java", "src//Main.java",
                "src/", "file.txt:stream");

        for (String path : paths) {
            assertThrows(IllegalArgumentException.class, () -> new ReadLocalFileRequest(path));
        }
    }

    @Test
    void rejectsUnsupportedOrMissingExtensions() {
        for (String path : List.of("legacy.doc", "document.docx", "paper.pdf", "Dockerfile")) {
            assertThrows(IllegalArgumentException.class, () -> new ReadLocalFileRequest(path));
        }
    }

    @Test
    void diagnosticsDoNotRevealTheRequestedPath() {
        String privatePath = "private/customer-records.java";
        ReadLocalFileRequest request = new ReadLocalFileRequest(privatePath);

        assertFalse(request.toString().contains(privatePath));
        assertEquals("ReadLocalFileRequest[path=REDACTED]", request.toString());
    }
}
