package org.openarchive.telegram;

import java.io.IOException;

/** Keep a read-only "Verify backup" operation from blessing unrelated or
 * hybrid ZIP files. A full archive and a metadata-only archive are distinct. */
final class BackupEnvelopePolicy {
    private BackupEnvelopePolicy() {}

    static boolean isFullEntry(String name, boolean directory) {
        try { BackupEntryPolicy.requireFullBackupPath(name, directory); return true; }
        catch (IOException invalid) { return false; }
    }

    static boolean isCompactEntry(String name, boolean directory) {
        try { BackupEntryPolicy.requireSafe(name, directory); }
        catch (IOException invalid) { return false; }
        if (directory) return "db/".equals(name);
        return "manifest.json".equals(name) ||
               "db/notes.sqlite".equals(name) ||
               name.matches("db/chat-[0-9]{1,5}\\.sqlite");
    }

    static void requireValid(boolean full, boolean allowedFull, boolean allowedCompact,
                             boolean outerMetadata, boolean fullManifest,
                             boolean compactManifest, boolean notesDb) throws IOException {
        if (full) {
            if (!allowedFull || !outerMetadata || !fullManifest || compactManifest || notesDb)
                throw new IOException("Неполный или смешанный формат полной резервной копии");
        } else if (!allowedCompact || !compactManifest || outerMetadata || fullManifest || !notesDb) {
            throw new IOException("Это не резервная копия Telegram Archive");
        }
    }
}
