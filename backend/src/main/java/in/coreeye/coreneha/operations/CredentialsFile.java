package in.coreeye.coreneha.operations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;

/** Writes one-time temporary passwords to an owner-only file in the operator's secrets folder. Never to logs. */
final class CredentialsFile {

    record Entry(String username, String temporaryPassword, String description) {
    }

    private CredentialsFile() {
    }

    static Path write(String outputDir, String name, String header, List<Entry> entries) throws IOException {
        Path dir = Path.of(outputDir);
        Files.createDirectories(dir);
        Path file = dir.resolve(name + "-" + Instant.now().toString().replace(":", "").replace(".", "") + ".txt");
        StringBuilder sb = new StringBuilder(header).append("\n\n");
        sb.append(String.format("%-28s %-20s %s%n", "USERNAME", "TEMPORARY PASSWORD", "WHO"));
        for (Entry e : entries) {
            sb.append(String.format("%-28s %-20s %s%n", e.username(), e.temporaryPassword(), e.description()));
        }
        sb.append("\nEach password works once: the first sign-in asks for a new password and an authenticator app (TOTP).\n");
        Files.writeString(file, sb.toString());
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX file system (not the case on the server)
        }
        return file;
    }
}
