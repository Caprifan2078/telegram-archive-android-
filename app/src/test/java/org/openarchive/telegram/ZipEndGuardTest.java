package org.openarchive.telegram;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

/** ZIP footer validation without Android or private files. */
public final class ZipEndGuardTest {
    private static byte[] archive(int entries, String comment)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes)) {
            if(comment!=null)zip.setComment(comment);
            for(int i=0;i<entries;i++) {
                zip.putNextEntry(new ZipEntry("entry-"+i));
                zip.write(65);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    private static int verify(byte[] data)throws Exception {
        ZipEndGuard guard=new ZipEndGuard(new ByteArrayInputStream(data));
        InputStreamHolder keep=new InputStreamHolder(guard);
        int entries=0;
        try(ZipInputStream zip=new ZipInputStream(keep)) {
            ZipEntry entry;
            byte[] buffer=new byte[4096];
            while((entry=zip.getNextEntry())!=null) {
                entries++;
                while(zip.read(buffer)!=-1) { }
                zip.closeEntry();
            }
        }
        guard.requireComplete(entries);
        return entries;
    }
    /** The archive-level reader closes its buffer, not the underlying verifier. */
    private static final class InputStreamHolder extends FilterInputStream {
        InputStreamHolder(ZipEndGuard wrapped){super(wrapped);}
        @Override public void close(){}
    }
    @Test public void validStandardZipWithCommentIsAccepted()throws Exception {
        assertEquals(3,verify(archive(3,"Archive comment")));
    }
    @Test public void missingEocdIsRejectedEvenWhenLocalCrcsAreValid()throws Exception {
        byte[] valid=archive(2,null);
        assertEquals(2,verify(valid));
        try {
            verify(Arrays.copyOf(valid,valid.length-22));
            fail("Missing ZIP end record was accepted");
        }catch(IOException expected) {
            assertTrue(expected.getMessage().contains("ZIP"));
        }
    }
    @Test public void alteredCentralDirectoryOffsetIsRejected()throws Exception {
        byte[] valid=archive(2,null);
        int footer=valid.length-22;
        valid[footer+16]=0;
        valid[footer+17]=0;
        valid[footer+18]=0;
        valid[footer+19]=0;
        try {
            verify(valid);
            fail("Broken central-directory offset was accepted");
        }catch(IOException expected) {
            assertTrue(expected.getMessage().contains("ZIP"));
        }
    }
    @Test public void zip64ManyEntriesRemainsSupported()throws Exception {
        assertEquals(65536,verify(archive(65536,null)));
    }
}
