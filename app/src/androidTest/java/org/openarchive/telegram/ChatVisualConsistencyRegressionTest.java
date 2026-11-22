package org.openarchive.telegram;

import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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

/** Check actual chat hierarchy, not only model-based UX expectations. */
@RunWith(AndroidJUnit4.class)
public final class ChatVisualConsistencyRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();

    private Object get(MainActivity a,String key)throws Exception{
        Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String key,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);
    }
    private void call(MainActivity a,String method)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(a);
    }
    private TextView find(View root,String text){
        if(root instanceof TextView&&text.equals(((TextView)root).getText().toString()))return (TextView)root;
        if(root instanceof ViewGroup){
            ViewGroup g=(ViewGroup)root;
            for(int i=0;i<g.getChildCount();i++){
                TextView found=find(g.getChildAt(i),text);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch completed=new CountDownLatch(1);
        ((ExecutorService)get(a,"worker")).execute(completed::countDown);
        assertTrue("Chat load timed out",completed.await(20,TimeUnit.SECONDS));
        inst.waitForIdleSync();
    }

    @Test public void shortChatHasReadableDatesMinimalHeaderAndNoScrubber()throws Exception{
        MainActivity activity=rule.getActivity();
        String key="visual_chat_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(activity,key);
        SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        synthetic.stage(sql,"Аня","2026-11-01 10:05:00","Привет!",null,"");
        synthetic.stage(sql,"Борис","2026-11-01 10:08:00","Привет, как дела?",null,"");
        synthetic.publishStaged(sql);
        Object oldDb=get(activity,"db"),oldKey=get(activity,"activeKey"),
            oldName=get(activity,"activeName"),oldHome=get(activity,"home"),
            oldScreen=get(activity,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(activity,"db",synthetic);put(activity,"activeKey",key);
                    put(activity,"activeName","QA Chat");put(activity,"home",false);
                    put(activity,"screen","chat");call(activity,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            idle(activity);
            inst.runOnMainSync(()->{
                assertNotNull("Human-readable date missing",find(activity.getWindow().getDecorView(),"1 ноября 2026"));
                assertNotNull("Use consistent overflow icon",find(activity.getWindow().getDecorView(),"⋯"));
                assertNotNull("Title should explain media navigation",
                    find(activity.getWindow().getDecorView(),"Медиа — по названию чата"));
                try{
                    assertEquals("A short chat needs no intrusive vertical timeline",
                        View.GONE,((View)get(activity,"verticalTimeline")).getVisibility());
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            idle(activity);
            inst.runOnMainSync(()->{
                try{
                    put(activity,"db",oldDb);put(activity,"activeKey",oldKey);
                    put(activity,"activeName",oldName);put(activity,"home",oldHome);
                    put(activity,"screen",oldScreen);call(activity,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            synthetic.close();activity.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void longChatRetainsAccessibleFastNavigation()throws Exception{
        MainActivity activity=rule.getActivity();
        String key="visual_chat_long_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(activity,key);
        SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        for(int i=0;i<141;i++){
            synthetic.stage(sql,i%2==0?"Аня":"Борис",
                String.format(java.util.Locale.ROOT,"2026-11-%02d 11:%02d:00",1+i/60,i%60),
                "Синтетическое тестовое сообщение "+i,null,"");
        }
        synthetic.publishStaged(sql);
        Object oldDb=get(activity,"db"),oldKey=get(activity,"activeKey"),
            oldName=get(activity,"activeName"),oldHome=get(activity,"home"),
            oldScreen=get(activity,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(activity,"db",synthetic);put(activity,"activeKey",key);
                    put(activity,"activeName","QA Long Chat");put(activity,"home",false);
                    put(activity,"screen","chat");call(activity,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            idle(activity);
            inst.runOnMainSync(()->{
                try{
                    View scrubber=(View)get(activity,"verticalTimeline");
                    assertEquals("Large archives keep fast-jump control",View.VISIBLE,scrubber.getVisibility());
                    assertTrue("Control must remain attached",scrubber.isAttachedToWindow());
                }catch(Exception error){throw new AssertionError(error);}
            });
            assertEquals(141,synthetic.totalMessages());
        }finally{
            idle(activity);
            inst.runOnMainSync(()->{
                try{
                    put(activity,"db",oldDb);put(activity,"activeKey",oldKey);
                    put(activity,"activeName",oldName);put(activity,"home",oldHome);
                    put(activity,"screen",oldScreen);call(activity,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            synthetic.close();activity.deleteDatabase("telegram_"+key+".db");
        }
    }
}
