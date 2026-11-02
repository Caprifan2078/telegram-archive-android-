package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
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

/** Media filters are selected through the UI on an isolated synthetic archive. */
@RunWith(AndroidJUnit4.class)
public class Qa00MediaFilterTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(inst);
    private Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private void show(MainActivity a)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("showPage");m.setAccessible(true);m.invoke(a);
    }
    private void tap(String label){
        UiObject2 control=device.wait(Until.findObject(By.text(label)),12000);
        assertNotNull("Missing control: "+label,control);control.click();device.waitForIdle();
    }
    private void filtered(String expected,String excluded){
        assertNotNull("Expected media tile: "+expected,
            device.wait(Until.findObject(By.descContains(expected+" ·")),15000));
        assertNull("Wrong media type shown: "+excluded,device.findObject(By.descContains(excluded+" ·")));
    }
    @Test public void photoAndVideoFiltersSurviveTabRoundTrips()throws Exception{
        MainActivity a=activity.getActivity();
        String key="qa00filter"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(a,key);SQLiteDatabase sql=db.getWritableDatabase();
        long photo=db.stage(sql,"Synthetic","2026-01-01 12:00","photo",null,"");
        db.stageAttachment(sql,photo,"photos/synthetic.jpg","image");
        long video=db.stage(sql,"Synthetic","2026-01-02 12:00","video",null,"");
        db.stageAttachment(sql,video,"videos/synthetic.mp4","video");db.publishStaged(sql);
        String[] names={"db","activeKey","activeName","home","screen","folder","mediaKind"};
        Object[] old=new Object[names.length];for(int i=0;i<names.length;i++)old[i]=field(a,names[i]);
        try{
            inst.runOnMainSync(()->{
                try{set(a,"db",db);set(a,"activeKey",key);set(a,"activeName","QA00 media filters");
                    set(a,"folder",null);set(a,"home",false);set(a,"screen","chat");set(a,"mediaKind","all");show(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
            tap("▦ Медиа");tap("Фото");filtered("Фото","Видео");
            tap("💬 Переписка");tap("▦ Медиа");filtered("Фото","Видео");
            tap("Видео");filtered("Видео","Фото");
            tap("💬 Переписка");tap("▦ Медиа");filtered("Видео","Фото");
        }finally{
            CountDownLatch idle=new CountDownLatch(1);
            ((ExecutorService)field(a,"worker")).execute(idle::countDown);
            assertTrue("Worker did not finish",idle.await(12,TimeUnit.SECONDS));
            inst.runOnMainSync(()->{
                try{for(int i=0;i<names.length;i++)set(a,names[i],old[i]);show(a);}
                catch(Exception e){throw new AssertionError(e);}
            });
            db.close();a.deleteDatabase("telegram_"+key+".db");
            a.getPreferences(Context.MODE_PRIVATE).edit().remove("read_offset_"+key)
                .remove("read_id_"+key).remove("read_top_"+key).apply();
        }
    }
}
