package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real Android state transitions, using only a synthetic archive and database. */
@RunWith(AndroidJUnit4.class)
public final class QaTrashRestoreRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private Object get(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);return f.get(a);
    }
    private void set(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);f.set(a,value);
    }
    private void invoke(MainActivity a,String name,String key)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,String.class);
        m.setAccessible(true);m.invoke(a,key);
    }

    @Test public void removedArchiveIsRecoverableAndDatabaseNeverDeleted()throws Exception {
        MainActivity a=activity.getActivity();
        SharedPreferences prefs=a.getPreferences(Context.MODE_PRIVATE);
        String originalRegistry=prefs.getString("archive_registry","[]");
        String originalTrash=prefs.getString("archive_trash","[]");
        String originalActive=prefs.getString("active_archive","default");
        Object oldArchives=get(a,"archives");
        boolean oldHome=(boolean)get(a,"home");
        String key="qa_trash_"+UUID.randomUUID().toString().replace("-","");
        boolean oldHidden=prefs.getBoolean("hidden_chat_"+key,false);
        boolean oldPinned=prefs.getBoolean("pinned_chat_"+key,false);
        MessageDb synthetic=new MessageDb(a,key);
        android.database.sqlite.SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        synthetic.stage(sql,"Тестовый автор","2026-11-18 10:00:00","Сообщение должно сохраниться",null,"");
        synthetic.publishStaged(sql);
        synthetic.close();
        try{
            assertTrue(a.getDatabasePath("telegram_"+key+".db").isFile());
            JSONArray registered=new JSONArray(originalRegistry);
            JSONObject entry=new JSONObject().put("key",key).put("name","Сохранить этот чат")
                .put("tree","local:/synthetic-only");
            registered.put(entry);
            a.runOnUiThread(()->{
                try{
                    set(a,"archives",registered);set(a,"home",true);
                    prefs.edit().putString("archive_registry",registered.toString())
                        .putString("archive_trash","[]")
                        .putBoolean("pinned_chat_"+key,true)
                        .putBoolean("hidden_chat_"+key,true).commit();
                    invoke(a,"removeChatFromList",key);
                }catch(Exception error){throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertFalse(prefs.getString("archive_registry","[]").contains(key));
            JSONArray trash=new JSONArray(prefs.getString("archive_trash","[]"));
            assertEquals(1,trash.length());
            assertEquals(key,trash.getJSONObject(0).getString("key"));
            assertEquals("Сохранить этот чат",trash.getJSONObject(0).getString("name"));
            assertTrue(a.getDatabasePath("telegram_"+key+".db").isFile());
            assertTrue("Pin preference retained",prefs.getBoolean("pinned_chat_"+key,false));

            // Reload the active list as if the process had been restarted,
            // then restore using the ACTUAL visible button in the recycle bin.
            a.runOnUiThread(()->{
                try{
                    Method load=MainActivity.class.getDeclaredMethod("loadArchiveRegistry");
                    load.setAccessible(true);load.invoke(a);
                    Method show=MainActivity.class.getDeclaredMethod("showChatTrash");
                    show.setAccessible(true);show.invoke(a);
                }catch(Exception error){throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
            UiObject2 restore=device.wait(Until.findObject(By.text("Сохранить этот чат · восстановить")),5000);
            assertNotNull("Recycle bin must show an accessible restore action",restore);
            restore.click();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue("Recovered chat is registered",prefs.getString("archive_registry","[]").contains(key));
            assertEquals("[]",prefs.getString("archive_trash","missing"));
            assertFalse("Restored chat is visible",prefs.getBoolean("hidden_chat_"+key,true));
            assertTrue("Pin preference restored",prefs.getBoolean("pinned_chat_"+key,false));
            try(MessageDb reopened=new MessageDb(a,key)){
                assertEquals(1,reopened.totalMessages());
                assertEquals("Сообщение должно сохраниться",
                    reopened.page(0,1,"",false,"",false,false).get(0).body);
            }
        }finally {
            a.runOnUiThread(()->{
                prefs.edit().putString("archive_registry",originalRegistry)
                    .putString("archive_trash",originalTrash)
                    .putString("active_archive",originalActive)
                    .putBoolean("hidden_chat_"+key,oldHidden)
                    .putBoolean("pinned_chat_"+key,oldPinned).commit();
                try{set(a,"archives",oldArchives);set(a,"home",oldHome);
                    Method render=MainActivity.class.getDeclaredMethod("renderHome");
                    render.setAccessible(true);render.invoke(a);
                }catch(Exception error){throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            a.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void corruptedTrashStopsRemovalWithoutChangingRegistry()throws Exception {
        MainActivity a=activity.getActivity();
        SharedPreferences prefs=a.getPreferences(Context.MODE_PRIVATE);
        String originalTrash=prefs.getString("archive_trash","[]");
        String originalRegistry=prefs.getString("archive_registry","[]");
        Object oldArchives=get(a,"archives");
        String key="qa_badtrash_"+UUID.randomUUID().toString().replace("-","");
        try {
            JSONArray registry=new JSONArray(originalRegistry);
            registry.put(new JSONObject().put("key",key).put("name","Keep me")
                .put("tree","local:/synthetic-only"));
            a.runOnUiThread(()->{
                try{
                    prefs.edit().putString("archive_registry",registry.toString())
                        .putString("archive_trash","bad-json").commit();
                    set(a,"archives",registry);
                    invoke(a,"removeChatFromList",key);
                }catch(Exception error){throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue("Corrupt trash cannot erase active chat",
                prefs.getString("archive_registry","[]").contains(key));
            assertEquals("bad-json",prefs.getString("archive_trash",""));
        }finally{
            a.runOnUiThread(()->{
                prefs.edit().putString("archive_registry",originalRegistry)
                    .putString("archive_trash",originalTrash).commit();
                try{set(a,"archives",oldArchives);}catch(Exception error){
                    throw new AssertionError(error);}
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }
    }

    @Test public void portableBackupRefusesToSilentlyDropTrashedMessages()throws Exception {
        MainActivity a=activity.getActivity();
        SharedPreferences prefs=a.getPreferences(Context.MODE_PRIVATE);
        String old=prefs.getString("archive_trash","[]");
        try{
            prefs.edit().putString("archive_trash",
                "[{\"key\":\"qa_safe_backup\",\"name\":\"Test chat\"}]").commit();
            try{
                ArchiveBackup.requireEmptyChatTrash(prefs);
                fail("Portable backup must not claim to cover omitted chats");
            }catch(java.io.IOException expected){
                assertTrue(expected.getMessage().contains("корзине"));
            }
        }finally{prefs.edit().putString("archive_trash",old).commit();}
    }
}
