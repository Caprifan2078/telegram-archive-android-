package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** A Telegram HTML export may contain references to media that was not downloaded.
 * The app must not claim to be playing a missing file.
 */
@RunWith(AndroidJUnit4.class)
public class MissingMediaPlaybackRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    private static Object get(MainActivity a,String field) throws Exception {
        Field f=MainActivity.class.getDeclaredField(field);
        f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String field,Object value)throws Exception {
        Field f=MainActivity.class.getDeclaredField(field);
        f.setAccessible(true);f.set(a,value);
    }
    private static MessageDb.Attachment missing(String kind){
        MessageDb.Attachment a=new MessageDb.Attachment();
        a.kind=kind;a.author="Synthetic";a.date="2026-01-01";
        a.path=kind.equals("round")?"round_video/unavailable.mp4":"voice_messages/unavailable.ogg";
        return a;
    }
    private void assertStripClears() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        AtomicBoolean hidden=new AtomicBoolean(false);
        long until=SystemClock.uptimeMillis()+7000;
        while(SystemClock.uptimeMillis()<until) {
            inst.runOnMainSync(()->{
                try {
                    LinearLayout strip=(LinearLayout)get(activity.getActivity(),"playerStrip");
                    hidden.set(strip.getVisibility()==View.GONE);
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            if(hidden.get())break;
            SystemClock.sleep(100);
        }
        assertTrue("Missing media left a fake playing strip",hidden.get());
    }
    @Test public void missingVoiceDoesNotLeavePlaybackControlsVisible() throws Exception {
        MainActivity a=activity.getActivity();
        File empty=new File(a.getCacheDir(),"missing-voice-"+UUID.randomUUID());
        assertTrue(empty.mkdirs());
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try {
                    set(a,"folder",new DocumentFolder(a.getContentResolver(),empty));
                    Method voice=MainActivity.class.getDeclaredMethod("voice",MessageDb.Attachment.class,boolean.class);
                    voice.setAccessible(true);
                    LinearLayout tile=(LinearLayout)voice.invoke(a,missing("voice"),false);
                    FrameLayout root=(FrameLayout)get(a,"root");
                    root.addView(tile,new FrameLayout.LayoutParams(-2,-2));
                    LinearLayout inner=(LinearLayout)tile.getChildAt(0);
                    TextView play=(TextView)inner.getChildAt(0);
                    assertTrue(play.performClick());
                    assertEquals(View.VISIBLE,((LinearLayout)get(a,"playerStrip")).getVisibility());
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            assertStripClears();
        }finally{empty.delete();}
    }
    @Test public void galleryVoiceUsesOfflinePlayerInsteadOfExternalApp() throws Exception {
        MainActivity a=activity.getActivity();
        File empty=new File(a.getCacheDir(),"missing-gallery-voice-"+UUID.randomUUID());
        assertTrue(empty.mkdirs());
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{
                    set(a,"folder",new DocumentFolder(a.getContentResolver(),empty));
                    Method open=MainActivity.class.getDeclaredMethod("openMedia",MessageDb.Attachment.class);
                    open.setAccessible(true);
                    open.invoke(a,missing("voice"));
                    assertEquals("Media gallery must use the built-in player",View.VISIBLE,
                        ((LinearLayout)get(a,"playerStrip")).getVisibility());
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            assertStripClears();
        }finally{empty.delete();}
    }
    @Test public void missingRoundDoesNotLeavePlaybackControlsVisible() throws Exception {
        MainActivity a=activity.getActivity();
        File empty=new File(a.getCacheDir(),"missing-round-"+UUID.randomUUID());
        assertTrue(empty.mkdirs());
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{
                    set(a,"folder",new DocumentFolder(a.getContentResolver(),empty));
                    Method show=MainActivity.class.getDeclaredMethod("showFloatingRound",MessageDb.Attachment.class);
                    show.setAccessible(true);show.invoke(a,missing("round"));
                    assertEquals(View.VISIBLE,((LinearLayout)get(a,"playerStrip")).getVisibility());
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            assertStripClears();
        }finally{empty.delete();}
    }
    private void copyAsset(String asset,File target)throws Exception{
        try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(asset);
            java.io.OutputStream out=new java.io.FileOutputStream(target)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
    }
    private String expectedDuration(File file)throws Exception{
        android.media.MediaMetadataRetriever r=new android.media.MediaMetadataRetriever();
        try{
            r.setDataSource(file.getAbsolutePath());
            int seconds=(int)(Long.parseLong(r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))/1000);
            return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
        }finally{r.release();}
    }
    private TextView requestDuration(MainActivity a,File folder,String archive,String path)throws Exception{
        final TextView[] result=new TextView[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            try{
                set(a,"folder",new DocumentFolder(a.getContentResolver(),folder));set(a,"activeKey",archive);
                result[0]=new TextView(a);result[0].setText("pending");
                Method load=MainActivity.class.getDeclaredMethod("loadDuration",String.class,TextView.class);
                load.setAccessible(true);load.invoke(a,path,result[0]);
            }catch(Exception ex){throw new AssertionError(ex);}
        });
        return result[0];
    }
    private void awaitDuration(TextView target,String expected)throws Exception{
        java.util.concurrent.atomic.AtomicReference<String> value=new java.util.concurrent.atomic.AtomicReference<>("");
        long until=SystemClock.uptimeMillis()+7000;
        while(SystemClock.uptimeMillis()<until){
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->value.set(target.getText().toString()));
            if(expected.equals(value.get()))return;
            SystemClock.sleep(50);
        }
        assertEquals(expected,value.get());
    }
    @Test public void sameMediaPathInDifferentChatsHasItsOwnDuration()throws Exception{
        MainActivity a=activity.getActivity();String key=UUID.randomUUID().toString();
        File first=new File(a.getCacheDir(),"duration-a-"+key),second=new File(a.getCacheDir(),"duration-b-"+key);
        assertTrue(first.mkdirs());assertTrue(second.mkdirs());
        try{
            File one=new File(first,"same.mp4"),two=new File(second,"same.mp4");
            copyAsset("short-round.mp4",one);copyAsset("regular-video.mp4",two);
            String aDuration=expectedDuration(one),bDuration=expectedDuration(two);
            assertNotEquals("Synthetic files must have distinct durations",aDuration,bDuration);
            awaitDuration(requestDuration(a,first,"synthetic-a-"+key,"same.mp4"),aDuration);
            awaitDuration(requestDuration(a,second,"synthetic-b-"+key,"same.mp4"),bDuration);
        }finally{ArchiveFileImport.deleteTree(first);ArchiveFileImport.deleteTree(second);}
    }
    @Test public void durationIsRetriedWhenMissingFileAppears()throws Exception{
        MainActivity a=activity.getActivity();String key=UUID.randomUUID().toString();
        File folder=new File(a.getCacheDir(),"duration-missing-"+key);assertTrue(folder.mkdirs());
        try{
            awaitDuration(requestDuration(a,folder,"synthetic-"+key,"later.mp4"),"00:00");
            File media=new File(folder,"later.mp4");copyAsset("short-round.mp4",media);
            awaitDuration(requestDuration(a,folder,"synthetic-"+key,"later.mp4"),expectedDuration(media));
        }finally{ArchiveFileImport.deleteTree(folder);}
    }

    @Test public void floatingRoundResumesAfterDelayedMediaResolution()throws Exception{
        MainActivity a=activity.getActivity();String key=UUID.randomUUID().toString();
        File folder=new File(a.getCacheDir(),"floating-resume-"+key);assertTrue(folder.mkdirs());
        File file=new File(folder,"round.mp4");copyAsset("short-round.mp4",file);
        java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=(java.util.concurrent.ExecutorService)get(a,"mediaWorker");
        for(int i=0;i<2;i++)pool.execute(()->{
            started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
        try{
            assertTrue("Media workers were not blocked",started.await(5,java.util.concurrent.TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{
                    set(a,"folder",new DocumentFolder(a.getContentResolver(),folder));
                    set(a,"activeKey","synthetic-floating-"+key);set(a,"floatingStart",2000);
                    MessageDb.Attachment note=new MessageDb.Attachment();note.kind="round";note.path="round.mp4";note.author="Synthetic";
                    Method show=MainActivity.class.getDeclaredMethod("showFloatingRound",MessageDb.Attachment.class);
                    show.setAccessible(true);show.invoke(a,note);
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            // Force the old 650 ms resume timer to expire while no player exists.
            SystemClock.sleep(900);release.countDown();
            java.util.concurrent.atomic.AtomicBoolean playing=new java.util.concurrent.atomic.AtomicBoolean();
            long readyDeadline=SystemClock.uptimeMillis()+10000;
            while(!playing.get()&&SystemClock.uptimeMillis()<readyDeadline){
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    try{android.media.MediaPlayer p=(android.media.MediaPlayer)get(a,"player");playing.set(p!=null&&p.isPlaying());}
                    catch(Exception ignored){}
                });
                SystemClock.sleep(40);
            }
            assertTrue("Floating video never started",playing.get());
            java.util.concurrent.atomic.AtomicInteger position=new java.util.concurrent.atomic.AtomicInteger(-1);
            long seekDeadline=SystemClock.uptimeMillis()+1100;
            while(position.get()<1500&&SystemClock.uptimeMillis()<seekDeadline){
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    try{android.media.MediaPlayer p=(android.media.MediaPlayer)get(a,"player");if(p!=null)position.set(p.getCurrentPosition());}
                    catch(Exception ignored){}
                });
                if(position.get()<1500)SystemClock.sleep(40);
            }
            assertTrue("Floating video restarted instead of resuming: "+position.get(),position.get()>=1500);
        }finally{
            release.countDown();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{Method close=MainActivity.class.getDeclaredMethod("closeFloatingRound");close.setAccessible(true);close.invoke(a);}
                catch(Exception e){throw new AssertionError(e);}
            });
            ArchiveFileImport.deleteTree(folder);
        }
    }

}
