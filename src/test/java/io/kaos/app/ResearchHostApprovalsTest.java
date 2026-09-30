package io.kaos.app;

import static org.junit.jupiter.api.Assertions.*;

import io.kaos.tool.httpget.HttpGetException;
import io.kaos.tool.httpget.HttpGetRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResearchHostApprovalsTest {
    @TempDir Path directory;

    @Test void mergesNormalizesAndKeepsExactHostBoundariesAcrossReloads() throws Exception {
        Path file = directory.resolve("nested/hosts.txt");
        var store = new ResearchHostApprovals(file);
        assertTrue(store.read().isEmpty());
        store.remember(Set.of("ONE.example"));
        new ResearchHostApprovals(file).remember(Set.of("two.example"));
        assertEquals(Set.of("one.example", "two.example"), store.read());
        var validator = store.validator();
        assertDoesNotThrow(() -> validator.validate(new HttpGetRequest("https://one.example/another")));
        assertEquals(HttpGetException.Reason.DISALLOWED_HOST, assertThrows(HttpGetException.class,
                () -> validator.validate(new HttpGetRequest("https://sub.one.example/"))).reason());
        Files.writeString(file, "two.example\n");
        assertEquals(Set.of("two.example"), store.read());
    }

    @Test void malformedOrOversizedStoreAndInvalidAdditionsCannotGrantAccess() throws Exception {
        var store = new ResearchHostApprovals(directory.resolve("hosts.txt"));
        for (String invalid : new String[]{"*.example", "one.example/path", "one.example:443",
                "127.0.0.1", "user@one.example", "https://one.example", "one.example?token=x"}) {
            Files.writeString(store.path(), invalid);
            assertThrows(ResearchHostApprovals.Unavailable.class, store::read, invalid);
            Files.writeString(store.path(), "one.example\n");
            assertThrows(ResearchHostApprovals.Unavailable.class, () -> store.remember(Set.of(invalid)), invalid);
            assertEquals(Set.of("one.example"), store.read());
        }
        Files.write(store.path(), new byte[]{(byte) 0xc3, 0x28});
        assertThrows(ResearchHostApprovals.Unavailable.class, store::read);
        Files.writeString(store.path(), "x".repeat(65537));
        assertThrows(ResearchHostApprovals.Unavailable.class, store::read);
    }

    @Test void writerContentionAndUnwritableDestinationFailWithoutReplacingApprovals() throws Exception {
        var store = new ResearchHostApprovals(directory.resolve("hosts.txt"));
        store.remember(Set.of("one.example"));
        try (var channel = java.nio.channels.FileChannel.open(directory.resolve("hosts.txt.lock"),
                java.nio.file.StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertThrows(ResearchHostApprovals.Unavailable.class, () -> store.remember(Set.of("two.example")));
        }
        assertEquals(Set.of("one.example"), store.read());
        var invalid = new ResearchHostApprovals(directory.resolve("hosts.txt/child.txt"));
        assertThrows(ResearchHostApprovals.Unavailable.class, () -> invalid.remember(Set.of("two.example")));
    }
}
