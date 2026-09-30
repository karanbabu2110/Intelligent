package io.kaos.app;

import io.kaos.tool.httpget.HttpGetPermissionValidator;
import io.kaos.tool.httpget.HttpGetRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.TreeSet;

/** Explicit local-user hostname approvals, scoped only to research. */
final class ResearchHostApprovals {
    private static final int MAX_BYTES = 65536;
    private static final int MAX_HOSTS = 256;
    private final Path file;

    ResearchHostApprovals(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    static ResearchHostApprovals load() {
        try {
            String configured = System.getProperty("kaos.research.approved-hosts-file");
            if (configured == null) configured = System.getenv("KAOS_RESEARCH_APPROVED_HOSTS_FILE");
            if (configured != null && configured.isBlank()) throw new IllegalArgumentException();
            String home = System.getProperty("user.home");
            if (configured == null && (home == null || home.isBlank())) throw new IllegalArgumentException();
            return new ResearchHostApprovals(configured == null
                    ? Path.of(home, ".kaos", "research-approved-hosts.txt") : Path.of(configured));
        } catch (RuntimeException exception) { throw new Unavailable(); }
    }

    Path path() { return file; }

    Set<String> read() {
        try {
            if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())) throw new IOException();
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) return Set.of();
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new IOException();
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                var hosts = new TreeSet<String>();
                for (String line : text.lines().toList()) {
                    if (!line.isBlank()) hosts.add(host(line.strip()));
                }
                if (hosts.size() > MAX_HOSTS) throw new IOException();
                return Set.copyOf(hosts);
            }
        } catch (IOException | RuntimeException exception) { throw new Unavailable(); }
    }

    void remember(Set<String> approved) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            if (Files.isSymbolicLink(file.getParent())) throw new IOException();
            Path lockPath = file.resolveSibling(file.getFileName() + ".lock");
            try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    var lock = channel.tryLock()) {
                if (lock == null) throw new IOException();
                var hosts = new TreeSet<>(read());
                for (String value : approved) hosts.add(host(value));
                if (hosts.size() > MAX_HOSTS) throw new IOException();
                String content = String.join("\n", hosts) + "\n";
                if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException();
                temporary = Files.createTempFile(file.getParent(), ".research-hosts-", ".tmp");
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException exception) { throw new Unavailable(); }
        finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); }
            catch (IOException ignored) { /* An unpublished temporary file conveys no approval. */ }
        }
    }

    HttpGetPermissionValidator validator() {
        return new HttpGetPermissionValidator(read());
    }

    private static String host(String value) {
        String normalized = HttpGetPermissionValidator.validateSyntax(
                new HttpGetRequest("https://" + value + "/")).uri().getHost();
        if (!normalized.equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return normalized;
    }

    static final class Unavailable extends RuntimeException {
        Unavailable() { super("Research host approval store unavailable."); }
    }
}
