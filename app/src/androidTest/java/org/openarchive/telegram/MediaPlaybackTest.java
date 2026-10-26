package org.openarchive.telegram;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
@RunWith(AndroidJUnit4.class)
public class MediaPlaybackTest {
    private static void le16(OutputStream out,int v)throws IOException{out.write(v&255);out.write((v>>8)&255);}
    private static void le32(OutputStream out,int v)throws IOException{le16(out,v);le16(out,v>>16);}
    @Test public void synthesizedVoicePlaybackSeekAndPause() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=new File(context.getCacheDir(),"test-voice-"+UUID.randomUUID()+".wav");
        int rate=16000,seconds=2,frames=rate*seconds;
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
            out.write(new byte[]{'R','I','F','F'});le32(out,36+frames*2);
            out.write(new byte[]{'W','A','V','E','f','m','t',' '});le32(out,16);le16(out,1);le16(out,1);
            le32(out,rate);le32(out,rate*2);le16(out,2);le16(out,16);
            out.write(new byte[]{'d','a','t','a'});le32(out,frames*2);
            for(int i=0;i<frames;i++)le16(out,(int)(Math.sin(i*2*Math.PI*330/rate)*5000));
        }
        MediaPlayer player=new MediaPlayer();
        try{
            player.setDataSource(file.getAbsolutePath());player.prepare();
            assertTrue("Voice duration missing",player.getDuration()>=1700);
            player.start();assertTrue(player.isPlaying());
            player.seekTo(700);SystemClock.sleep(80);
            player.pause();assertFalse(player.isPlaying());
            player.seekTo(1300);player.start();player.pause();
        }finally{player.release();file.delete();}
    }
}