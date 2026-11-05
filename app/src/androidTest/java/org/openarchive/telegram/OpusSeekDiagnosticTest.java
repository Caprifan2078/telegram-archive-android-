package org.openarchive.telegram;

import android.content.Context;
import android.media.MediaPlayer;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OpusSeekDiagnosticTest {
    @Test public void seekWaitsForCompletionBeforeReadingPosition()throws Exception {
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests=InstrumentationRegistry.getInstrumentation().getContext();
        File file=new File(target.getCacheDir(),"opus-seek-"+UUID.randomUUID()+".ogg");
        MediaPlayer player=new MediaPlayer();
        try {
            try(InputStream in=tests.getAssets().open("voice.ogg");
                FileOutputStream out=new FileOutputStream(file)) {
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            player.setDataSource(file.getAbsolutePath());player.prepare();
            assertTrue(player.getDuration()>=1500);
            player.start();player.pause();
            CountDownLatch done=new CountDownLatch(1);
            player.setOnSeekCompleteListener(ignored->done.countDown());
            player.seekTo(500);
            assertTrue("Seek completion callback not received",done.await(8,TimeUnit.SECONDS));
            int position=player.getCurrentPosition();
            assertTrue("Position after completed seek: "+position,position>=350);
        } finally { player.release();file.delete(); }
    }
}
