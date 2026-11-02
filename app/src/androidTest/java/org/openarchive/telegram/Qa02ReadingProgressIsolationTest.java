package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import static org.junit.Assert.*;

/** QA-02: isolated reading positions, no Telegram account or personal data. */
@RunWith(AndroidJUnit4.class)
public class Qa02ReadingProgressIsolationTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private static Object field(MainActivity a,String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String name,Object value)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private static void invoke(MainActivity a,String name,Class<?>[] types,Object...args)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);
    }
    private static void seed(Context ctx,String key,int count)throws Exception {
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            try{
                for(int i=0;i<count;i++){
                    db.stage(sql,"QA02 author","2025-01-03 12:00",
                        "QA02_"+key+"_MESSAGE_"+i,null,"");
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
        }finally{db.close();}
    }
    @Test public void openingAnotherChatClearsPriorVisibleAnchor()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String keyA="qa02a_"+suffix,keyB="qa02b_"+suffix;
        String name=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(name.startsWith("."))name=name.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(name,Context.MODE_PRIVATE);
        boolean hadReg=prefs.contains("archive_registry"),hadKey=prefs.contains("active_archive");
        String oldReg=prefs.getString("archive_registry","[]"),oldKey=prefs.getString("active_archive","default");
        try{
            seed(ctx,keyA,30);seed(ctx,keyB,2);
            JSONArray chats=new JSONArray()
                .put(new JSONObject().put("key",keyA).put("name","QA02 Alpha").put("tree",""))
                .put(new JSONObject().put("key",keyB).put("name","QA02 Beta").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString())
                .putString("active_archive",keyA).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                assertNotNull("Home not shown",device.wait(Until.findObject(By.textContains("Мои переписки")),8000L));
                // Set a real anchor from the previous chat, then switch.
                scenario.onActivity(a->{
                    try{
                        invoke(a,"activateArchive",new Class[]{String.class},keyA);
                    }catch(Exception e){throw new AssertionError(e);}
                });
                assertNotNull(device.wait(Until.findObject(By.textContains("QA02_"+keyA+"_MESSAGE_")),8000L));
                scenario.onActivity(a->{
                    try{
                        invoke(a,"saveReadingProgress",new Class[0]);
                        assertTrue("No message anchor in first chat",(long)field(a,"lastVisibleMessage")>0);
                        invoke(a,"activateArchive",new Class[]{String.class},keyB);
                        assertEquals("A previous chat's reading anchor leaked into the new chat",
                            -1L,field(a,"lastVisibleMessage"));
                        assertEquals("Prior chat's pixel offset leaked",0,field(a,"lastVisibleTop"));
                    }catch(Exception e){throw new AssertionError(e);}
                });
                assertNotNull("Second chat did not render",device.wait(
                    Until.findObject(By.textContains("QA02_"+keyB+"_MESSAGE_")),8000L));
                scenario.onActivity(a->{
                    try{
                        // A screen with only a non-message placeholder must not
                        // overwrite the saved per-chat reading position.
                        String pref="read_id_"+keyB;
                        long before=prefs.getLong(pref,-1);
                        LinearLayout list=(LinearLayout)field(a,"list");
                        list.removeAllViews();
                        list.addView(new TextView(a));
                        set(a,"lastVisibleMessage",912345L);
                        invoke(a,"saveReadingProgress",new Class[0]);
                        assertEquals("Placeholder saved stale foreign message ID",
                            before,prefs.getLong(pref,-1));
                    }catch(Exception e){throw new AssertionError(e);}
                });
            }
        }finally{
            SharedPreferences.Editor edit=prefs.edit();
            if(hadReg)edit.putString("archive_registry",oldReg);else edit.remove("archive_registry");
            if(hadKey)edit.putString("active_archive",oldKey);else edit.remove("active_archive");
            for(String key:new String[]{keyA,keyB}){
                edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key);
                ctx.deleteDatabase("telegram_"+key+".db");
            }
            assertTrue(edit.commit());
        }
    }
}

