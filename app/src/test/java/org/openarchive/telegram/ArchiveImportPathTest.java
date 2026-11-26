package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public final class ArchiveImportPathTest {
    @Test public void safeWindowsAndDotPaths()throws Exception{
        assertEquals("Chat/messages.html",ArchiveImportPath.normalize(".\\Chat\\messages.html",false));
        assertEquals("Chat/photos/",ArchiveImportPath.normalize("./Chat/photos",true));
        assertEquals("",ArchiveImportPath.normalize("./",true));
    }
    @Test public void maliciousPathsRemainRejected(){
        for(String raw:new String[]{"../messages.html","./../x","Chat\\..\\x","C:\\x","/x","\\\\server\\x","a//b","a/./b","x\u0000y","./"}){
            try{ArchiveImportPath.normalize(raw,false);fail("Accepted "+raw);}catch(java.io.IOException expected){}
        }
    }
    @Test public void backupPathsRemainStrict(){
        try{BackupEntryPolicy.requireSafe(".\\Chat\\messages.html",false);fail();}catch(java.io.IOException expected){}
    }
}
