package org.openarchive.telegram;

import android.app.Instrumentation;
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

/** A large event/stage history must not render as one long continuous page. */
@RunWith(AndroidJUnit4.class)
public final class ChronologyHubRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();

    private Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String name,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,v);
    }
    private void call(MainActivity a,String method)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(a);
    }
    private View find(View node,String label){
        if(node instanceof TextView&&label.equals(((TextView)node).getText().toString()))return node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                View found=find(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        ((ExecutorService)field(a,"worker")).execute(done::countDown);
        assertTrue("History load hung",done.await(20,TimeUnit.SECONDS));
        inst.waitForIdleSync();
    }
    @Test public void sectionsOpenOnDemandWithoutAutoRenderingFullHistory()throws Exception{
        MainActivity activity=rule.getActivity();
        String key="qa_history_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(activity,key);
        synthetic.getWritableDatabase();
        Object oldDb=field(activity,"db"),oldKey=field(activity,"activeKey"),
            oldName=field(activity,"activeName"),oldHome=field(activity,"home"),
            oldScreen=field(activity,"screen"),oldOpen=field(activity,"historyEventsExpanded"),
            oldSection=field(activity,"chronologySection");
        try{
            inst.runOnMainSync(()->{try{
                put(activity,"db",synthetic);put(activity,"activeKey",key);
                put(activity,"activeName","QA History");put(activity,"home",false);
                put(activity,"screen","relationships");put(activity,"historyEventsExpanded",false);
                put(activity,"chronologySection","overview");
                call(activity,"renderRelationshipsPage");
                assertNotNull(find(activity.getWindow().getDecorView(),"01 · События переписки  ▾"));
                assertNull("Event list must be lazy",find(activity.getWindow().getDecorView(),"Лента событий  ·  0"));
                assertNotNull(find(activity.getWindow().getDecorView(),"02 · Мои периоды истории  ›"));
                assertNotNull(find(activity.getWindow().getDecorView(),"03 · Кинолента медиа по годам  ›"));
                find(activity.getWindow().getDecorView(),"01 · События переписки  ▾").performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            idle(activity);
            inst.runOnMainSync(()->{
                assertNotNull(find(activity.getWindow().getDecorView(),"01 · События переписки  ▴"));
                assertNotNull("Events appear only on request",find(activity.getWindow().getDecorView(),"Лента событий  ·  0"));
                find(activity.getWindow().getDecorView(),"02 · Мои периоды истории  ›").performClick();
                assertNotNull(find(activity.getWindow().getDecorView(),"01 · Этапы переписки  ›"));
                assertNotNull(find(activity.getWindow().getDecorView(),"02 · Периоды по датам  ›"));
                assertNotNull(find(activity.getWindow().getDecorView(),"03 · Сравнение этапов  ›"));
                assertNull("Overview must not display all stage cards",find(activity.getWindow().getDecorView(),"Подробнее об этапе  ▾"));
                find(activity.getWindow().getDecorView(),"02 · Периоды по датам  ›").performClick();
                assertNotNull(find(activity.getWindow().getDecorView(),"+ Добавить период по датам"));
                assertNotNull(find(activity.getWindow().getDecorView(),"‹ К разделам хронологии"));
                assertNotNull("The selected section needs its own heading",
                    find(activity.getWindow().getDecorView(),"Периоды по датам"));
                assertNull("Do not repeat section navigation above every detail page",
                    find(activity.getWindow().getDecorView(),"01 · Этапы переписки  ›"));
                assertNull("Chosen calendar section must not also render comparison link",
                    find(activity.getWindow().getDecorView(),"03 · Сравнение этапов  ›"));
            });
            idle(activity);
            inst.runOnMainSync(()->{
                assertNull("Detail page should remain focused after async load",
                    find(activity.getWindow().getDecorView(),"01 · Этапы переписки  ›"));
                find(activity.getWindow().getDecorView(),"‹ К разделам хронологии").performClick();
                assertNotNull("Back to hub restores the section navigation",
                    find(activity.getWindow().getDecorView(),"01 · Этапы переписки  ›"));
                assertNotNull(find(activity.getWindow().getDecorView(),"04 · Лента событий  ›"));
            });
        }finally{
            idle(activity);
            inst.runOnMainSync(()->{try{
                put(activity,"db",oldDb);put(activity,"activeKey",oldKey);
                put(activity,"activeName",oldName);put(activity,"home",oldHome);
                put(activity,"screen",oldScreen);put(activity,"historyEventsExpanded",oldOpen);
                put(activity,"chronologySection",oldSection);
                call(activity,"showPage");
            }catch(Exception e){throw new AssertionError(e);}});
            synthetic.close();activity.deleteDatabase("telegram_"+key+".db");
        }
    }
}
