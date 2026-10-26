package org.openarchive.telegram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.UUID;
import static org.junit.Assert.*;

/** Offline synthetic assets; checks Android framework decoders, not interactive UI/PiP. */
@RunWith(AndroidJUnit4.class)
public class MediaAssetsRegressionTest {
    private static File fixture(Context ctx,String name)throws IOException {
        File f=new File(ctx.getCacheDir(),"media-fixture-"+UUID.randomUUID()+"-"+name.replace('/','_'));
        Context source=name.startsWith("tutorial/")?ctx:InstrumentationRegistry.getInstrumentation().getContext();
        try(InputStream in=source.getAssets().open(name);
            OutputStream out=new FileOutputStream(f)){
            byte[] block=new byte[16*1024];int n;
            while((n=in.read(block))!=-1)out.write(block,0,n);
        }
        return f;
    }
    @Test public void voiceOpusSeekPauseResume()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();File f=fixture(ctx,"voice.ogg");
        MediaPlayer p=new MediaPlayer();
        try {
            p.setDataSource(f.getAbsolutePath());p.prepare();
            assertTrue("OGG/Opus duration",p.getDuration()>=1500);
            p.start();assertTrue(p.isPlaying());p.pause();assertFalse(p.isPlaying());
            p.seekTo(500);p.start();SystemClock.sleep(80);p.pause();
            assertTrue("voice position",p.getCurrentPosition()>=350);
        }finally{p.release();f.delete();}
    }
    private void verifyMp4(String file)throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();File f=fixture(ctx,file);
        MediaMetadataRetriever info=new MediaMetadataRetriever();MediaPlayer p=new MediaPlayer();
        try{
            info.setDataSource(f.getAbsolutePath());
            assertEquals("video/mp4",info.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
            Bitmap frame=info.getFrameAtTime(600000,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            assertNotNull("Missing actual decoded MP4 video frame for "+file,frame);frame.recycle();
            p.setDataSource(f.getAbsolutePath());p.prepare();
            assertTrue("MP4 duration",p.getDuration()>=1500);
            p.start();assertTrue(p.isPlaying());p.pause();assertFalse(p.isPlaying());
            p.seekTo(850);p.start();SystemClock.sleep(90);p.pause();
            assertTrue("MP4 seek",p.getCurrentPosition()>=600);
        }finally{p.release();info.release();f.delete();}
    }
    @Test public void roundVideoDecodesFrameAndSeeks()throws Exception {verifyMp4("short-round.mp4");}
    @Test public void regularVideoDecodesFrameAndSeeks()throws Exception {verifyMp4("tutorial/clip.mp4");}
    @Test public void staticPhotoDecodes()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getContext();
        try(InputStream in=ctx.getAssets().open("photo.png")){
            Bitmap decoded=BitmapFactory.decodeStream(in);
            assertNotNull("PNG decoder returned null",decoded);
            assertEquals(96,decoded.getWidth());assertEquals(64,decoded.getHeight());decoded.recycle();
        }
    }
    @Test public void actualAnimatedGifFramesAreReadable()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getContext();
        try(InputStream in=ctx.getAssets().open("animated.gif")){
            Movie m=Movie.decodeStream(in);
            assertNotNull("GIF animation was not decoded",m);
            assertTrue("No GIF duration",m.duration()>0);
            assertTrue("No GIF size",m.width()>=70&&m.height()>=70);
        }
    }
}
