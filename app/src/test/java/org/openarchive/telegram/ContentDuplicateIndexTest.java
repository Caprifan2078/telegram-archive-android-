package org.openarchive.telegram;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import static org.junit.Assert.*;

public class ContentDuplicateIndexTest {
    private static ContentDuplicateIndex.Digest hash(String data)throws IOException{
        return ContentDuplicateIndex.fingerprint(new ByteArrayInputStream(data.getBytes("UTF-8")),1024);
    }
    @Test public void duplicateBytesDifferentFilenamesMatch()throws Exception{
        ContentDuplicateIndex.Digest first=hash("same contents");
        assertEquals(first.hash,hash("same contents").hash);
        assertEquals(first.size,hash("same contents").size);
        ContentDuplicateIndex.Index index=new ContentDuplicateIndex.Index();
        index.add("images/a.jpg",first);index.add("media/b.jpg",hash("same contents"));
        assertEquals(1,index.repeated().size());assertEquals(2,index.repeated().get(0).size());
    }
    @Test public void differentBytesOrSameFilenameAreNotFalseDuplicates()throws Exception{
        ContentDuplicateIndex.Index index=new ContentDuplicateIndex.Index();
        index.add("a.jpg",hash("different"));index.add("b.jpg",hash("content"));
        index.add("a.jpg",hash("different"));
        assertTrue(index.repeated().isEmpty());
    }
    @Test public void tooBigIsRejectedBeforeFingerprintUsed()throws Exception{
        try {ContentDuplicateIndex.fingerprint(new ByteArrayInputStream(new byte[128]),64);
            fail("size should be rejected");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("лимит"));}
    }
    @Test public void cancellationStopsHashing()throws Exception{
        try{ContentDuplicateIndex.fingerprint(new ByteArrayInputStream(new byte[200]),1024,()->true);
            fail("cancelled read should abort");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("остановлена"));}
    }
    @Test public void globalBudgetCountsFailedFileReads()throws Exception{
        java.util.concurrent.atomic.AtomicLong used=new java.util.concurrent.atomic.AtomicLong();
        try(java.io.InputStream in=new ContentDuplicateIndex.BudgetedInput(new ByteArrayInputStream(new byte[200]),used,64)){
            try{ContentDuplicateIndex.fingerprint(in,300);fail("global read budget bypassed");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("лимит"));}
            assertEquals(64,used.get());
        }
    }
    @Test public void emptyFilesCanBeDetected()throws Exception{
        ContentDuplicateIndex.Digest empty=hash("");
        assertEquals(0,empty.size);assertEquals(64,empty.hash.length());
    }
}
