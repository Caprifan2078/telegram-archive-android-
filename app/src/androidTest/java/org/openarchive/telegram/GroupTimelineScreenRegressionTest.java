package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Android screen test using only two synthetic local SQLite chat databases. */
@RunWith(AndroidJUnit4.class)
public final class GroupTimelineScreenRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private void call(MainActivity a,String name,String argument)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,String.class);
        m.setAccessible(true);m.invoke(a,argument);
    }
    private View find(View node,String exact){
        if(node instanceof TextView&&exact.equals(((TextView)node).getText().toString()))return node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                View found=find(group.getChildAt(i),exact);if(found!=null)return found;
            }
        }
        return null;
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        ((ExecutorService)field(a,"worker")).execute(latch::countDown);
        assertTrue("Timeline query should finish",latch.await(20,TimeUnit.SECONDS));
        inst.waitForIdleSync();
    }
    @Test public void combinedHistoryShowsBothSourcesAndBackReturnsToChatList()throws Exception{
        MainActivity a=rule.getActivity();
        idle(a);
        SharedPreferences prefs=a.getPreferences(android.content.Context.MODE_PRIVATE);
        String oldGroups=prefs.getString(MultiConversationRegistry.PREF_KEY,null);
        Object oldArchives=field(a,"archives"),oldHome=field(a,"home");
        Object oldScreen=field(a,"screen"),oldReturn=field(a,"returnScreen");
        Object oldGroup=field(a,"multiTimelineGroupId"),oldPage=field(a,"multiTimelinePage");
        Object oldQuota=field(a,"multiTimelinePerSource");
        String first=UUID.randomUUID().toString().replace("-","");
        String second=UUID.randomUUID().toString().replace("-","");
        MessageDb dbFirst=new MessageDb(a,first),dbSecond=new MessageDb(a,second);
        try{
            SQLiteDatabase x=dbFirst.getWritableDatabase(),y=dbSecond.getWritableDatabase();
            dbFirst.stage(x,"Вася","23.11.2026 10:00:00","Привет из Telegram",null,"");
            dbFirst.publishStaged(x);
            dbSecond.stage(y,"Вася","23.11.2026 11:00:00","Ответ второго аккаунта",null,"");
            dbSecond.publishStaged(y);
            dbFirst.close();dbSecond.close();
            JSONArray synthetic=new JSONArray();
            synthetic.put(new JSONObject().put("key",first).put("name","Личный Telegram"));
            synthetic.put(new JSONObject().put("key",second).put("name","Личный второй"));
            prefs.edit().putString(MultiConversationRegistry.PREF_KEY,"[]").commit();
            String group=MultiConversationRegistry.create(prefs,"Вася QA");
            MultiConversationRegistry.add(prefs,group,first,"Telegram",new HashSet<>(Arrays.asList(first,second)));
            MultiConversationRegistry.add(prefs,group,second,"Второй аккаунт",new HashSet<>(Arrays.asList(first,second)));
            inst.runOnMainSync(()->{
                try{set(a,"archives",synthetic);call(a,"openMultiTimeline",group);}
                catch(Exception e){throw new AssertionError(e);}
            });
            idle(a);
            inst.runOnMainSync(()->{
                View root=a.getWindow().getDecorView();
                assertNotNull(find(root,"Привет из Telegram"));
                assertNotNull(find(root,"Ответ второго аккаунта"));
                assertNotNull(find(root,"Telegram · 23.11.2026 10:00:00"));
                assertNotNull(find(root,"Второй аккаунт · 23.11.2026 11:00:00"));
                assertEquals("multi_timeline",fieldUnchecked(a,"screen"));
                a.onBackPressed();
                assertEquals("chat",fieldUnchecked(a,"screen"));
                assertEquals(Boolean.TRUE,fieldUnchecked(a,"home"));
            });
        }finally{
            dbFirst.close();dbSecond.close();
            a.deleteDatabase("telegram_"+first+".db");
            a.deleteDatabase("telegram_"+second+".db");
            SharedPreferences.Editor restore=prefs.edit();
            if(oldGroups==null)restore.remove(MultiConversationRegistry.PREF_KEY);
            else restore.putString(MultiConversationRegistry.PREF_KEY,oldGroups);
            restore.commit();
            inst.runOnMainSync(()->{
                try{
                    set(a,"archives",oldArchives);set(a,"home",oldHome);
                    set(a,"screen",oldScreen);set(a,"returnScreen",oldReturn);
                    set(a,"multiTimelineGroupId",oldGroup);set(a,"multiTimelinePage",oldPage);
                    set(a,"multiTimelinePerSource",oldQuota);
                    Method render=MainActivity.class.getDeclaredMethod("showPage");
                    render.setAccessible(true);render.invoke(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
    private Object fieldUnchecked(MainActivity a,String key){
        try{return field(a,key);}catch(Exception problem){throw new AssertionError(problem);}
    }
}
