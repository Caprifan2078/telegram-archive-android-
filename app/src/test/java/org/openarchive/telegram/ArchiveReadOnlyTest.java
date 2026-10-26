package org.openarchive.telegram;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import static org.junit.Assert.*;
public class ArchiveReadOnlyTest {
    @Test public void recognizesSupportedExtensions(){
        for(String extension:Arrays.asList("a.zip","a.7z","a.rar","a.rar5","a.tar","a.TAR.GZ","a.tgz","a.txz","a.tar.xz","a.tar.bz2"))
            assertTrue(extension,ArchiveReadOnly.isArchive(extension));
        assertFalse(ArchiveReadOnly.isArchive("photo.jpg"));
        assertFalse(ArchiveReadOnly.isArchive("template.docx"));
        assertTrue(ArchiveReadOnly.isArchive("media/file.7z?download=1"));
        assertTrue(OfficePreviewText.supported("docs/budget.xlsx#document"));
    }
    @Test public void preventsUnsafeEntryPaths(){
        for(String name:Arrays.asList("../secret","photos/../../x","/etc/password","C:\\root.txt","x\0a",".\\x"))
            assertFalse(name,ArchiveReadOnly.safeName(name));
        assertTrue(ArchiveReadOnly.safeName("group/media/Привет.xlsx"));
    }
}
