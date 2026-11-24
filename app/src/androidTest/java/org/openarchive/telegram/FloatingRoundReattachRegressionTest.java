package org.openarchive.telegram;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic chat re-render: the floating video must find the replacement inline round. */
@RunWith(AndroidJUnit4.class)
public final class FloatingRoundReattachRegressionTest {
    private static Object read(Object o,String name)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    private static void write(Object o,String name,Object value)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    private static FrameLayout tile(MainActivity a,long attachmentId)throws Exception{
        FrameLayout holder=new FrameLayout(a);
        Class<?> type=Class.forName("org.openarchive.telegram.MainActivity$RoundControls");
        Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
        Object controls=ctor.newInstance();
        write(controls,"sourceAttachmentId",attachmentId);
        write(controls,"preview",new ImageView(a));
        write(controls,"overlay",new TextView(a));
        holder.setTag(controls);
        return holder;
    }
    @Test public void removedPlayingTileMustFloatInsteadOfContinuingAsInvisibleAudio(){
        assertTrue("Detached playing round needs a bubble even without a scroll event",
            RoundPlaybackTarget.shouldAutoFloat(false,0,0,100,700));
        assertFalse("Visible attached round must remain inline",
            RoundPlaybackTarget.shouldAutoFloat(true,200,350,100,700));
        assertTrue("A fully scrolled-out attached tile also needs a bubble",
            RoundPlaybackTarget.shouldAutoFloat(true,0,100,100,700));
    }

    @SuppressWarnings("unchecked")
    @Test public void recreatedTileIsReboundByAttachmentIdNotByOldViewOrSharedFilePath()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{
                FrameLayout root=(FrameLayout)read(a,"root");
                LinearLayout rebuiltMessage=new LinearLayout(a);
                FrameLayout other=tile(a,91);
                FrameLayout correct=tile(a,92);
                rebuiltMessage.addView(other,new LinearLayout.LayoutParams(160,160));
                rebuiltMessage.addView(correct,new LinearLayout.LayoutParams(160,160));
                root.addView(rebuiltMessage,new FrameLayout.LayoutParams(-1,170));
                Map<Long,View> map=(Map<Long,View>)read(a,"renderedMessageViews");
                map.put(43L,rebuiltMessage);
                MessageDb.Attachment active=new MessageDb.Attachment();
                active.id=92;active.messageId=43;active.kind="round";active.path="same.mp4";
                write(a,"playingAttachment",active);
                write(a,"autoFloating",true);
                write(a,"autoFloatSourceHolder",new FrameLayout(a)); // detached after chat rebuild
                Method rebind=MainActivity.class.getDeclaredMethod("rebindAutoFloatingSourceIfNeeded");
                rebind.setAccessible(true);
                rebind.invoke(a);
                assertSame("Must restore new attached tile",correct,read(a,"autoFloatSourceHolder"));
                Object controls=correct.getTag();
                assertSame(read(controls,"preview"),read(a,"autoFloatSourcePoster"));
                assertSame(read(controls,"overlay"),read(a,"autoFloatSourceOverlay"));
                assertTrue("Old/out-of-window tile must not be reused",correct.isAttachedToWindow());
            }catch(Exception error){throw new AssertionError(error);}});
        }
    }
}
