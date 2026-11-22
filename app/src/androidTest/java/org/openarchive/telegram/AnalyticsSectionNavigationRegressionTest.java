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

/** Tap real analytics sections with a disposable two-author Telegram-style archive. */
@RunWith(AndroidJUnit4.class)
public final class AnalyticsSectionNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation instrument=InstrumentationRegistry.getInstrumentation();

    private Object field(MainActivity activity,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);
    }
    private void put(MainActivity activity,String name,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,v);
    }
    private Object invoke(MainActivity activity,String name)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(activity);
    }
    private View find(View v,String label,boolean description){
        if(v instanceof TextView){
            TextView t=(TextView)v;
            if(!description&&label.equals(t.getText().toString()))return t;
            if(description&&label.equals(String.valueOf(t.getContentDescription())))return t;
        }
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=0;i<group.getChildCount();i++){
                View hit=find(group.getChildAt(i),label,description);
                if(hit!=null)return hit;
            }
        }
        return null;
    }
    private View has(MainActivity a,String label){
        return find(a.getWindow().getDecorView(),label,false);
    }
    private void navigate(MainActivity a,String label,String expectedSection)throws Exception{
        instrument.runOnMainSync(()->{
            try{
                View chip=find(a.getWindow().getDecorView(),"Раздел анализа: "+label,true);
                assertNotNull("Missing section navigation "+label,chip);
                assertTrue("Tab must be clickable: "+label,chip.isClickable());
                chip.performClick();
                assertEquals(expectedSection,field(a,"analyticsSection"));
                View active=find(a.getWindow().getDecorView(),"Раздел анализа: "+label,true);
                assertNotNull("Current tab must remain after render",active);
                assertTrue("Chosen section should be selected",active.isSelected());
                assertTrue("Chosen tab must stay visible",active.isShown());
            }catch(Exception e){throw new AssertionError(e);}
        });
        instrument.waitForIdleSync();
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch wait=new CountDownLatch(1);
        ((ExecutorService)field(a,"worker")).execute(wait::countDown);
        assertTrue("Analytics SQLite work stalled",wait.await(20,TimeUnit.SECONDS));
        instrument.waitForIdleSync();
    }

    @Test public void allStatisticsStayReachableWithoutOneEnormousScreen()throws Exception{
        MainActivity a=rule.getActivity();
        String key="qa_analytics_sections_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        for(int i=0;i<42;i++){
            int day=i/5+1;
            synthetic.stage(sql,i%2==0?"Анна":"Борис",
                String.format(java.util.Locale.ROOT,"2026-11-%02d %02d:30:00",day,10+(i%5)),
                i%2==0?"Встретимся завтра?":"Хорошо, до встречи!",null,"");
        }
        synthetic.publishStaged(sql);
        Object db=field(a,"db"),keyBefore=field(a,"activeKey"),
            nameBefore=field(a,"activeName"),home=field(a,"home"),
            screen=field(a,"screen"),section=field(a,"analyticsSection");
        try{
            instrument.runOnMainSync(()->{
                try{
                    put(a,"db",synthetic);put(a,"activeKey",key);put(a,"activeName","QA Analytics");
                    put(a,"home",false);put(a,"screen","analytics");
                    put(a,"analyticsSection","overview");invoke(a,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            idle(a);
            instrument.runOnMainSync(()->{
                assertNotNull(has(a,"Анализ переписки"));
                assertNotNull(has(a,"Сводка"));
                assertNull("Heatmap must be lazy",has(a,"Тепловая карта · день недели × час"));
                assertNull("Participants must be lazy",has(a,"Кто сколько написал"));
            });
            navigate(a,"Графики","trends");
            instrument.runOnMainSync(()->{
                assertNotNull(has(a,"Графики активности"));
                assertNotNull(has(a,"Вся история переписки · по годам"));
                assertNull(has(a,"Тепловая карта · день недели × час"));
            });
            navigate(a,"Ритм","rhythm");
            instrument.runOnMainSync(()->{
                assertNotNull(has(a,"Тепловая карта · день недели × час"));
                assertNull(has(a,"Вся история переписки · по годам"));
            });
            navigate(a,"Участники","people");
            instrument.runOnMainSync(()->{
                assertNotNull(has(a,"Кто сколько написал"));
                assertNotNull(has(a,"Интервал между сообщениями участников"));
                assertNull(has(a,"Самые частые слова"));
            });
            navigate(a,"Медиа и слова","content");
            instrument.runOnMainSync(()->{
                assertNotNull(has(a,"Медиатека по типам"));
                assertNotNull(has(a,"Самые частые слова"));
                assertNull(has(a,"Графики активности"));
                try{
                    assertEquals(Boolean.TRUE,invoke(a,"backWithinHistory"));
                    assertEquals("overview",field(a,"analyticsSection"));
                    assertNotNull(has(a,"Сводка"));
                }catch(Exception e){throw new AssertionError(e);}
            });
            assertEquals("Browsing analysis must not modify messages",42,synthetic.totalMessages());
        }finally{
            idle(a);
            instrument.runOnMainSync(()->{
                try{
                    put(a,"db",db);put(a,"activeKey",keyBefore);put(a,"activeName",nameBefore);
                    put(a,"home",home);put(a,"screen",screen);put(a,"analyticsSection",section);
                    invoke(a,"showPage");
                }catch(Exception error){throw new AssertionError(error);}
            });
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
}
