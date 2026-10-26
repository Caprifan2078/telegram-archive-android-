package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class MediaIntegrityTest {
    private static String digest(String[] names,byte[][] files){
        MediaIntegrity md=new MediaIntegrity();
        for(int i=0;i<names.length;i++){
            md.begin(names[i]);
            byte[] b=files[i];for(int start=0;start<b.length;start+=7)
                md.update(b,start,Math.min(7,b.length-start));
            md.end();
        }
        return md.hex();
    }
    @Test public void changesNamesBytesOrderingAndPresence(){
        String[] names={"media/chat-0/photo.jpg","media/chat-0/voice.ogg"};
        byte[][] data={{1,2,3,4},{9,8,7,6}};
        String baseline=digest(names,data);
        assertEquals(baseline,digest(names,data));
        assertNotEquals(baseline,digest(new String[]{names[1],names[0]},new byte[][]{data[1],data[0]}));
        assertNotEquals(baseline,digest(new String[]{names[0],"media/chat-1/voice.ogg"},data));
        assertNotEquals(baseline,digest(names,new byte[][]{data[0],{9,8,7,0}}));
        assertNotEquals(baseline,digest(new String[]{names[0]},new byte[][]{data[0]}));
        assertEquals(64,baseline.length());
    }
    @Test public void emptyMediaAndChunkBoundaries(){
        byte[] payload=new byte[400001];Arrays.fill(payload,(byte)73);
        MediaIntegrity a=new MediaIntegrity();a.begin("media/chat-0/a.mp4");a.update(payload,0,payload.length);a.end();
        MediaIntegrity b=new MediaIntegrity();b.begin("media/chat-0/a.mp4");
        for(int k=0;k<payload.length;k+=8192)b.update(payload,k,Math.min(payload.length-k,8192));
        b.end();assertEquals(a.hex(),b.hex());
        assertEquals(digest(new String[]{"media/chat-0/empty.bin"},new byte[][]{{}}),
                     digest(new String[]{"media/chat-0/empty.bin"},new byte[][]{{}}));
    }
}
