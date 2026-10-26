package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/** Pure Java release-gate tests: no Android device or personal Telegram data. */
public class BackupSafetyGateTest {
    private static void rejects(RunnableWithIo failing) {
        try { failing.run(); fail("Expected fail-closed validation"); }
        catch (IOException expected) { /* correct: archive rejected before publishing */ }
    }
    interface RunnableWithIo { void run() throws IOException; }

    @Test public void duplicateOldChatKeysWouldMisrouteAnnotations() throws Exception {
        ArchiveChatKeyPolicy.requireUnique(Arrays.asList("abc", "def", "ghi"));
        rejects(() -> ArchiveChatKeyPolicy.requireUnique(Arrays.asList("abc", "def", "abc")));
        rejects(() -> ArchiveChatKeyPolicy.requireUnique(Arrays.asList("../bad")));
        rejects(() -> ArchiveChatKeyPolicy.requireUnique(Arrays.asList("")));
    }
    @Test public void validMediaOwnedByAChat() throws Exception {
        assertEquals(0, FullBackupLayout.mediaChatIndex("media/chat-0/a.jpg"));
        assertEquals(999, FullBackupLayout.mediaChatIndex("media/chat-999/rounds/a.mp4"));
        FullBackupLayout.requireWithinChatCount(3, 4);
        FullBackupLayout.requireWithinChatCount(-1, 0);
    }
    @Test public void orphanMediaMustAbort() {
        rejects(() -> FullBackupLayout.requireWithinChatCount(3, 3));
        rejects(() -> FullBackupLayout.requireWithinChatCount(999, 4));
        rejects(() -> FullBackupLayout.requireWithinChatCount(-1, 1001));
    }
    @Test public void ambiguousMediaFoldersMustAbort() {
        for (String path : new String[]{
            "media/chat-00/a.jpg", "media/chat-01/a.jpg", "media/chat-0/",
            "media/chat-10000/a.jpg", "media/chat-a/a.jpg", "media/chat--1/a.jpg",
            "media/chat-0", "media/chat-0a/x.png", "media/chat-+1/x.jpg",
            "custom/personal.jpg", "media/x/a.jpg"})
            rejects(() -> FullBackupLayout.mediaChatIndex(path));
    }
    @Test public void noteRefsMustMatchExactly() throws Exception {
        NoteAttachmentParity.requireExact(Arrays.asList(1L, 2L, 3L),
            Arrays.asList(3L, 2L, 1L));
        NoteAttachmentParity.requireExact(Collections.emptyList(),Collections.emptyList());
    }
    @Test public void missingNoteByteIsNotPortable() {
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(11L, 12L),Arrays.asList(11L)));
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(11L),Collections.emptyList()));
    }
    @Test public void orphanAndRepeatedNoteFilesRejected() {
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(10L),Arrays.asList(10L, 12L)));
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(10L,10L),Arrays.asList(10L)));
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(10L),Arrays.asList(10L,10L)));
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(10L),Arrays.asList(0L)));
        rejects(() -> NoteAttachmentParity.requireExact(Arrays.asList(10L),Arrays.asList(-1L)));
    }
    @Test public void completeFullEnvelopeAccepted() throws Exception {
        BackupEnvelopePolicy.requireValid(true,true,false,true,true,false,false);
        assertTrue(BackupEnvelopePolicy.isFullEntry("metadata.zip",false));
        assertTrue(BackupEnvelopePolicy.isFullEntry("media/chat-0/x.tgs",false));
        assertTrue(BackupEnvelopePolicy.isFullEntry("note_files/123",false));
    }
    @Test public void randomZipAndHybridCannotPassVerifier() {
        rejects(() -> BackupEnvelopePolicy.requireValid(false,false,false,false,false,false,false));
        rejects(() -> BackupEnvelopePolicy.requireValid(true,true,false,false,true,false,false));
        rejects(() -> BackupEnvelopePolicy.requireValid(true,true,false,true,false,false,false));
        rejects(() -> BackupEnvelopePolicy.requireValid(true,true,false,true,true,true,false));
        rejects(() -> BackupEnvelopePolicy.requireValid(true,false,false,true,true,false,false));
    }
    @Test public void validCompactEnvelopeAccepted() throws Exception {
        BackupEnvelopePolicy.requireValid(false,false,true,false,false,true,true);
        assertTrue(BackupEnvelopePolicy.isCompactEntry("db/chat-0.sqlite",false));
        assertTrue(BackupEnvelopePolicy.isCompactEntry("db/notes.sqlite",false));
        assertTrue(BackupEnvelopePolicy.isCompactEntry("manifest.json",false));
    }
    @Test public void compactArchiveRequiresNotesAndManifest() {
        rejects(() -> BackupEnvelopePolicy.requireValid(false,false,true,false,false,true,false));
        rejects(() -> BackupEnvelopePolicy.requireValid(false,false,true,false,false,false,true));
        rejects(() -> BackupEnvelopePolicy.requireValid(false,false,true,true,false,true,true));
    }
    @Test public void unrelatedOrTraversalEntriesDisallowed() {
        for(String name : new String[]{"readme.txt", "../payload", "db/../evil.sqlite", "db/chat-0.db",
            "media/chat-0/a.jpg", "custom/img.jpg", "full-manifest.json", "metadata.zip"})
            assertFalse("compact file should not be accepted: "+name,BackupEnvelopePolicy.isCompactEntry(name,false));
        for(String name : new String[]{"readme.txt", "db/notes.sqlite", "manifest.json", "media/chat-0/../a.jpg"})
            assertFalse("full file should not be accepted: "+name,BackupEnvelopePolicy.isFullEntry(name,false));
    }
}
