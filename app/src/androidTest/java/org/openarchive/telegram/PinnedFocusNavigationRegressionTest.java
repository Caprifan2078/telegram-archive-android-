package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.app.Instrumentation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Regression: jumping to any ordinary result used to rotate the pinned banner. */
@RunWith(AndroidJUnit4.class)
public class PinnedFocusNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    private static Object get(MainActivity a,String field)throws Exception{
        Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String field,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);f.set(a,value);
    }
    private static void barrier(ExecutorService worker)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        worker.execute(done::countDown);
        assertTrue("UI worker remained blocked",done.await(6,TimeUnit.SECONDS));
    }
    @Test public void ordinaryJumpLeavesPinnedSelectionUnchanged()throws Exception{
        final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        final MainActivity a=activity.getActivity();
        final Context ctx=inst.getTargetContext();
        final ExecutorService worker=(ExecutorService)get(a,"worker");
        barrier(worker);inst.waitForIdleSync();
        final String key="pinnednav_"+UUID.randomUUID().toString().replace("-","");
        final MessageDb fresh=new MessageDb(ctx,key);
        final MessageDb previous=(MessageDb)get(a,"db");
        final String originalKey=(String)get(a,"activeKey");
        final String originalScreen=(String)get(a,"screen");
        final boolean originalHome=(boolean)get(a,"home");
        final long originalFocus=(long)get(a,"pinnedFocus");
        long first,middle,last;
        try{
            SQLiteDatabase sqlite=fresh.getWritableDatabase();
            fresh.clearStage(sqlite);
            first=fresh.stage(sqlite,"A","2026-01-01","Synthetic pinned one",null,"");
            middle=fresh.stage(sqlite,"B","2026-01-02","Synthetic ordinary result",null,"");
            last=fresh.stage(sqlite,"C","2026-01-03","Synthetic pinned two",null,"");
            fresh.publishStaged(sqlite);
            fresh.togglePinned(first);fresh.togglePinned(last);
            assertEquals(2,fresh.pinned().size());
            final long wanted=last;
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",fresh);set(a,"activeKey",key);
                    set(a,"screen","chat");set(a,"home",false);
                    set(a,"pinnedFocus",wanted);
                    Method jump=MainActivity.class.getDeclaredMethod("jump",long.class,String.class);
                    jump.setAccessible(true);jump.invoke(a,middle,"");
                }catch(Exception e){throw new AssertionError(e);}
            });
            barrier(worker);
            inst.waitForIdleSync();
            assertEquals("Jump should highlight original message",middle,(long)get(a,"highlightId"));
            assertEquals("Ordinary jump rotated pinned bar",wanted,(long)get(a,"pinnedFocus"));
        }finally{
            barrier(worker);
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",previous);set(a,"activeKey",originalKey);
                    set(a,"screen",originalScreen);set(a,"home",originalHome);
                    set(a,"pinnedFocus",originalFocus);
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }
}
