package org.openarchive.telegram;
import static org.junit.Assert.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
public class TgsPayloadTest {
    private static byte[] gz(String text)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(GZIPOutputStream g=new GZIPOutputStream(b)){g.write(text.getBytes(StandardCharsets.UTF_8));}
        return b.toByteArray();
    }
    @Test public void validLottieIsDecodedOffline()throws Exception{
        String json="{\"v\":\"5.6.6\",\"fr\":60,\"ip\":0,\"op\":120,\"layers\":[]}";
        assertEquals(json,TgsPayload.decode(new ByteArrayInputStream(gz(json))));
    }
    @Test public void invalidGzipIsRejected()throws Exception{
        try{TgsPayload.decode(new ByteArrayInputStream(new byte[]{1,2,3}));fail();}
        catch(IOException expected){}
    }
    @Test public void decompressionBombIsRejected()throws Exception{
        String huge="x".repeat(TgsPayload.MAX_JSON+100);
        try{TgsPayload.decode(new ByteArrayInputStream(gz(huge)));fail();}
        catch(IOException expected){assertTrue(expected.getMessage().contains("слишком"));}
    }
    @Test public void rawUncompressedFileIsNotMistakenForTgs()throws Exception{
        try{TgsPayload.decode(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));fail();}
        catch(IOException expected){}
    }
}
