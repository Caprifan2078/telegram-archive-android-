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
}
