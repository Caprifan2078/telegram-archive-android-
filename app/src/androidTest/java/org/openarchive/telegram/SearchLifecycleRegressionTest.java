package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Real Activity recreation, using a disposable synthetic chat and restored preferences. */
@RunWith(AndroidJUnit4.class)
public class SearchLifecycleRegressionTest {
    private static Object get(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private static void settle(ActivityScenario<MainActivity> scenario)throws Exception{
        final ExecutorService[] worker={null};
        scenario.onActivity(a->{try{worker[0]=(ExecutorService)get(a,"worker");}catch(Exception e){throw new AssertionError(e);}});
        CountDownLatch latch=new CountDownLatch(1);worker[0].execute(latch::countDown);
        assertTrue("worker did not finish",latch.await(30,TimeUnit.SECONDS));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    @Test public void recreationPreservesSearchResultsAndUnappliedDrafts()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="rotation_"+UUID.randomUUID().toString().replace("-","");
        String preferencesName=new android.content.ComponentName(ctx,MainActivity.class).getShortClassName();
        if(preferencesName.startsWith("."))preferencesName=preferencesName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(preferencesName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try{
                for(int i=0;i<100;i++)db.stage(sql,"Alice","2024-02-29 12:00","syntheticquery item "+i,null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);db.close();
            JSONArray chats=new JSONArray().put(new JSONObject().put("key",key).put("name","Synthetic").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString()).putString("active_archive",key).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                scenario.onActivity(a->{try{
                    set(a,"home",false);set(a,"screen","search");set(a,"q","syntheticquery");
                    set(a,"byAuthor","");set(a,"deep",true);set(a,"onlyMedia",false);
                    set(a,"returnScreen","analytics");set(a,"searchReturnsToAnalytics",true);
                    Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
                }catch(Exception e){throw new AssertionError(e);}});
                settle(scenario);settle(scenario);
                final int[] scrollY={0};
                scenario.onActivity(a->{try{
                    ScrollView view=(ScrollView)get(a,"scroll");
                    view.scrollTo(0,view.getChildAt(0).getHeight()/3);scrollY[0]=view.getScrollY();
                    assertTrue("Synthetic results must scroll",scrollY[0]>0);
                    ((EditText)get(a,"searchInput")).setText("unsent draft");
                    ((EditText)get(a,"authorInput")).setText("Draft author");
                    ((CheckBox)get(a,"mediaOnly")).setChecked(true);
                }catch(Exception e){throw new AssertionError(e);}});
                scenario.recreate();settle(scenario);settle(scenario);
                scenario.onActivity(a->{try{
                    assertEquals("syntheticquery",get(a,"q"));assertEquals("",get(a,"byAuthor"));
                    assertEquals(false,get(a,"onlyMedia"));assertEquals("analytics",get(a,"returnScreen"));
                    assertEquals(true,get(a,"searchReturnsToAnalytics"));
                    assertEquals("unsent draft",((EditText)get(a,"searchInput")).getText().toString());
                    assertEquals("Draft author",((EditText)get(a,"authorInput")).getText().toString());
                    assertTrue(((CheckBox)get(a,"mediaOnly")).isChecked());
                    assertEquals(scrollY[0],((ScrollView)get(a,"scroll")).getScrollY());
                }catch(Exception e){throw new AssertionError(e);}});
            }
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+key+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            assertTrue(edit.commit());
        }
    }
    @Test public void recreationReturnsToVisibleMessageInLongChat()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="chatrotation_"+UUID.randomUUID().toString().replace("-","");
        String name=new android.content.ComponentName(ctx,MainActivity.class).getShortClassName();
        if(name.startsWith("."))name=name.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(name,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try{
                for(int i=0;i<80;i++)db.stage(sql,"Alice","2024-02-29 12:00","Synthetic chat message "+i,null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);db.close();
            JSONArray chats=new JSONArray().put(new JSONObject().put("key",key).put("name","Synthetic").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString()).putString("active_archive",key).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                scenario.onActivity(a->{try{
                    set(a,"home",false);set(a,"screen","chat");set(a,"chatOffset",0);set(a,"autoLoaded",80);
                    Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
                }catch(Exception e){throw new AssertionError(e);}});
                settle(scenario);settle(scenario);
                final long[] messageId={-1};final int[] top={0};
                scenario.onActivity(a->{try{
                    ScrollView view=(ScrollView)get(a,"scroll");view.scrollTo(0,view.getChildAt(0).getHeight()/2);
                    Method save=MainActivity.class.getDeclaredMethod("saveReadingProgress");save.setAccessible(true);save.invoke(a);
                    messageId[0]=(long)get(a,"lastVisibleMessage");top[0]=(int)get(a,"lastVisibleTop");
                    assertTrue("A synthetic message should be visible",messageId[0]>0);
                }catch(Exception e){throw new AssertionError(e);}});
                scenario.recreate();settle(scenario);settle(scenario);
                scenario.onActivity(a->{try{
                    @SuppressWarnings("unchecked") java.util.Map<Long,android.view.View> views=
                        (java.util.Map<Long,android.view.View>)get(a,"renderedMessageViews");
                    android.view.View restored=views.get(messageId[0]);assertNotNull("Visible message must remain loaded",restored);
                    int actual=restored.getTop()-((ScrollView)get(a,"scroll")).getScrollY();
                    assertTrue("Visible message position changed: "+top[0]+" -> "+actual,Math.abs(actual-top[0])<=8);
                }catch(Exception e){throw new AssertionError(e);}});
            }
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+key+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            assertTrue(edit.commit());
        }
    }
}
