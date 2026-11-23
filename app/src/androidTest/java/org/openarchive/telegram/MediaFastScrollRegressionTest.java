package org.openarchive.telegram;

import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.view.MotionEvent;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fast scrubber must jump across unloaded media, not just already loaded thumbnails. */
@RunWith(AndroidJUnit4.class)
public final class MediaFastScrollRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object get(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private void method(MainActivity a,String name,Class<?>[] types,Object...args)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);
        m.setAccessible(true);m.invoke(a,args);
    }
    private void waitFor(MainActivity a,String name,int expected)throws Exception{
        long deadline=System.currentTimeMillis()+25000;
        while(System.currentTimeMillis()<deadline){
            inst.waitForIdleSync();
            if((int)get(a,name)>=expected)return;
            Thread.sleep(140);
        }
        fail(name+" never reached "+expected+"; actual "+get(a,name));
    }
    private void waitForNewGallery(MainActivity a,int total)throws Exception{
        long deadline=System.currentTimeMillis()+25000;
        while(System.currentTimeMillis()<deadline){
            inst.waitForIdleSync();
            if((int)get(a,"mediaTotal")==total&&
                (int)get(a,"mediaWindowStart")==0&&(int)get(a,"mediaLoaded")>=30)return;
            Thread.sleep(140);
        }
        fail("Gallery did not reset and load "+total+" items");
    }
    @Test public void fractionMathClampsEndpointsAndRejectsInvalidValues(){
        assertEquals(0,MediaFastScroller.indexForFraction(-.2f,200));
        assertEquals(0,MediaFastScroller.indexForFraction(Float.NaN,200));
        assertEquals(199,MediaFastScroller.indexForFraction(1.4f,200));
        assertEquals(100,MediaFastScroller.indexForFraction(.5025f,200));
        assertEquals(0,MediaFastScroller.indexForFraction(.8f,0));
        assertEquals(0,MediaFastScroller.indexForFraction(.8f,1));
        assertEquals(1f,MediaFastScroller.fractionForIndex(199,200),.00001f);
    }
    @Test public void everyMediaCategoryHasRealRightScrubberAndCanJumpBeyondInitialThirty()throws Exception{
        MainActivity a=rule.getActivity();
        String key="fastscroll"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        Object oldDb=get(a,"db"),oldHome=get(a,"home"),oldScreen=get(a,"screen"),
            oldKind=get(a,"mediaKind"),oldKey=get(a,"activeKey");
        String[] kinds={"image","video","round","voice","audio","file","gif","sticker"};
        try{
            SQLiteDatabase writable=synthetic.getWritableDatabase();
            for(int k=0;k<kinds.length;k++){
                for(int i=0;i<78;i++){
                    long id=synthetic.stage(writable,"QA",String.format(java.util.Locale.ROOT,
                        "2026-11-%02d 11:00:00",1+(i%25)),"Тестовое медиа "+k+":"+i,null,"");
                    synthetic.stageAttachment(writable,id,kinds[k]+"/"+i+
                        ("image".equals(kinds[k])?".jpg":".ogg"),kinds[k]);
                }
            }
            synthetic.publishStaged(writable);
            String[] filters={"image","video","round","voice","audio","file","gif","sticker","visual"};
            for(String kind:filters){
                final int expectedCount="visual".equals(kind)?156:78;
                inst.runOnMainSync(()->{
                    try{
                        put(a,"db",synthetic);put(a,"activeKey",key);
                        put(a,"home",false);put(a,"screen","media");put(a,"mediaKind",kind);
                        method(a,"showPage",new Class<?>[0]);
                    }catch(Exception ex){throw new AssertionError(ex);}
                });
                waitForNewGallery(a,expectedCount);
                inst.runOnMainSync(()->{
                    try{
                        View slider=(View)get(a,"mediaFastScroller");
                        assertEquals("Fast scrubber missing for "+kind,View.VISIBLE,slider.getVisibility());
                        assertEquals(expectedCount,get(a,"mediaTotal"));
                        method(a,"jumpToMediaFraction",new Class<?>[]{float.class},.76f);
                    }catch(Exception ex){throw new AssertionError(ex);}
                });
                int expectedStart=MediaFastScroller.indexForFraction(.76f,expectedCount);
                waitFor(a,"mediaWindowStart",expectedStart-2);
                inst.runOnMainSync(()->{
                    try{
                        int start=(int)get(a,"mediaWindowStart");
                        assertTrue("Should load near the requested 76% position: "+kind,
                            Math.abs(start-expectedStart)<=1);
                        assertTrue("Keep recycled gallery bounded",((RecyclerView)get(a,"mediaRecycler")).getAdapter().getItemCount()<70);
                    }catch(Exception ex){throw new AssertionError(ex);}
                });
            }
        }finally{
            inst.runOnMainSync(()->{
                try{
                    put(a,"db",oldDb);put(a,"activeKey",oldKey);
                    put(a,"home",oldHome);put(a,"screen",oldScreen);
                    put(a,"mediaKind",oldKind);method(a,"showPage",new Class<?>[0]);
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
}
