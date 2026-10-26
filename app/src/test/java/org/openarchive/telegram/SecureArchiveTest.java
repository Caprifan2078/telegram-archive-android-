package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.Arrays;

public class SecureArchiveTest {
    private final char[] pass="correct-horse-battery-staple".toCharArray();
    private byte[] encrypted(byte[] plain)throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(OutputStream out=SecureArchive.encryptedOutput(bytes,pass)){out.write(plain);}
        return bytes.toByteArray();
    }
    private byte[] restored(byte[] encrypted,char[] password)throws Exception {
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(InputStream in=SecureArchive.decryptedInput(new ByteArrayInputStream(encrypted),password)){
            byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)result.write(buf,0,n);
        }
        return result.toByteArray();
    }
    @Test public void roundTripLargeStream()throws Exception {
        byte[] data=new byte[380000];new java.util.Random(42).nextBytes(data);
        assertArrayEquals(data,restored(encrypted(data),pass));
    }
    @Test public void emptyArchiveIsAuthenticated()throws Exception {
        assertArrayEquals(new byte[0],restored(encrypted(new byte[0]),pass));
    }
    @Test public void wrongPasswordFails()throws Exception {
        try{restored(encrypted(new byte[]{1,2,3}),"wrong-password".toCharArray());fail("Wrong password accepted");}
        catch(IOException expected){}
    }
    @Test public void corruptedPayloadFails()throws Exception {
        byte[] file=encrypted(new byte[90000]);file[70]^=0x40;
        try{restored(file,pass);fail("Tampered data accepted");}catch(IOException expected){}
    }
    @Test public void truncatedFileFails()throws Exception {
        byte[] file=encrypted(new byte[75000]);
        try{restored(Arrays.copyOf(file,file.length-12),pass);fail("Truncated data accepted");}
        catch(IOException expected){}
    }
}