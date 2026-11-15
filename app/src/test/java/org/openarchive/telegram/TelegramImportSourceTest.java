package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;

public class TelegramImportSourceTest {
    @Test public void detectsArchivesRegardlessOfFilename() {
        assertEquals(TelegramImportSource.Kind.ZIP,TelegramImportSource.detect("file.bin",new byte[]{'P','K',3,4}));
        assertEquals(TelegramImportSource.Kind.SEVEN_Z,TelegramImportSource.detect("file.dat",new byte[]{0x37,0x7a,(byte)0xbc,(byte)0xaf,0x27,0x1c}));
        assertEquals(TelegramImportSource.Kind.RAR,TelegramImportSource.detect("file.bin",new byte[]{'R','a','r','!',0x1a,0x07,0}));
    }
    @Test public void acceptsKnownFileExtensionsAsFallback() {
        assertEquals(TelegramImportSource.Kind.ZIP,TelegramImportSource.detect("EXPORT.ZIP",new byte[0]));
        assertEquals(TelegramImportSource.Kind.SEVEN_Z,TelegramImportSource.detect("chat.7z",null));
        assertEquals(TelegramImportSource.Kind.RAR,TelegramImportSource.detect("chat.rar",null));
        assertEquals(TelegramImportSource.Kind.HTML,TelegramImportSource.detect("messages17.html",null));
        assertEquals(TelegramImportSource.Kind.HTML,TelegramImportSource.detect("messages.htm",null));
    }
    @Test public void otherDocumentsAreNotSilentlyImported() {
        assertEquals(TelegramImportSource.Kind.PREVIEW,TelegramImportSource.detect("report.pdf",null));
        assertEquals(TelegramImportSource.Kind.PREVIEW,TelegramImportSource.detect("notes.docx",null));
        assertEquals(TelegramImportSource.Kind.PREVIEW,TelegramImportSource.detect("",new byte[0]));
    }
    @Test public void signatureOverridesMisleadingExtension() {
        assertEquals(TelegramImportSource.Kind.ZIP,TelegramImportSource.detect("chat.html",new byte[]{'P','K',3,4}));
    }
    @Test public void noUnexpectedFilenameLeakIntoChatTitle() {
        assertEquals("Переписка",TelegramImportSource.suggestedChatName("ChatExport_2026-10-23.zip"));
        assertEquals("Переписка",TelegramImportSource.suggestedChatName("messages2.html"));
        assertEquals("Семья",TelegramImportSource.suggestedChatName("Семья.zip"));
        assertEquals("Переписка",TelegramImportSource.suggestedChatName("...zip"));
        assertEquals("Переписка",TelegramImportSource.suggestedChatName(null));
    }
}
