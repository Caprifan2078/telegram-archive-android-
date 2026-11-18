package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

/** Real UI controls, isolated databases, and non-destructive chat library actions. */
@RunWith(AndroidJUnit4.class)
public final class QaUiChatNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(inst);

    private Object value(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void set(MainActivity a,String name,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,v);
    }
    private void invoke(MainActivity a,String method,Class<?>[] types,Object... args)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(method,types);m.setAccessible(true);m.invoke(a,args);
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        ((ExecutorService)value(a,"worker")).execute(latch::countDown);
        assertTrue("Async UI worker stuck",latch.await(15,TimeUnit.SECONDS));
        inst.waitForIdleSync();
    }
    private View findText(View root,String expected){
        if(root instanceof TextView&&expected.equals(((TextView)root).getText().toString()))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View match=findText(group.getChildAt(i),expected);
                if(match!=null)return match;
            }
        }
        return null;
    }

    @Test public void importHasOnlyFileOrFolderInOneEntryPoint()throws Exception {
        MainActivity a=rule.getActivity();
        inst.waitForIdleSync();
        inst.runOnMainSync(()->{
            View add=findText(a.getWindow().getDecorView(),"＋   Добавить переписку");
            assertNotNull("Single home import button",add);
            assertTrue(add.performClick());
        });
        device.waitForIdle();
        assertNotNull("One import dialog",device.wait(Until.findObject(By.text("Импорт переписки")),2500));
        assertNotNull("File option",device.findObject(By.text("Выбрать файл Telegram")));
        assertNotNull("Folder option",device.findObject(By.text("Выбрать папку Telegram")));
        assertNull("Old preview was incorrectly a third import source",device.findObject(By.textContains("Просмотреть файл или архив")));
        device.pressBack();device.waitForIdle();
    }

    @Test public void titleOpensMediaAndPdfIsSeparateChatAction()throws Exception {
        MainActivity a=rule.getActivity();
        String key="ui_nav_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        synthetic.getWritableDatabase();
        Object oldDb=value(a,"db"),oldKey=value(a,"activeKey"),oldName=value(a,"activeName"),
            oldHome=value(a,"home"),oldScreen=value(a,"screen"),oldFolder=value(a,"folder");
        try {
            inst.runOnMainSync(()->{try{
                set(a,"db",synthetic);set(a,"activeKey",key);set(a,"activeName","QA UI Chat");
                set(a,"home",false);set(a,"screen","chat");set(a,"folder",null);
                invoke(a,"showPage",new Class<?>[0]);
            }catch(Exception error){throw new AssertionError(error);}});
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    View pdf=(View)value(a,"pdfButton");
                    assertTrue("PDF dedicated header button visible",pdf.isShown());
                    TextView title=(TextView)value(a,"title");
                    assertEquals("QA UI Chat",title.getText().toString());
                    assertTrue("Tapping chat name opens its media",title.performClick());
                    assertEquals("media",value(a,"screen"));
                    assertEquals(View.GONE,pdf.getVisibility());
                }catch(Exception error){throw new AssertionError(error);}
            });
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    set(a,"screen","chat");invoke(a,"showPage",new Class<?>[0]);
                    assertTrue(((View)value(a,"pdfButton")).isShown());
                }catch(Exception error){throw new AssertionError(error);}
            });
        }finally{
            idle(a);
            inst.runOnMainSync(()->{try{
                set(a,"db",oldDb);set(a,"activeKey",oldKey);set(a,"activeName",oldName);
                set(a,"home",oldHome);set(a,"screen",oldScreen);set(a,"folder",oldFolder);
                invoke(a,"showPage",new Class<?>[0]);
            }catch(Exception error){throw new AssertionError(error);}});
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void hiddenAndRemovedChatKeepsExistingDatabase()throws Exception {
        MainActivity a=rule.getActivity();
        String key="ui_hidden_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        synthetic.getWritableDatabase();synthetic.close();
        SharedPreferences prefs=a.getPreferences(Context.MODE_PRIVATE);
        String originalRegistry=prefs.getString("archive_registry","[]");
        String originalTrash=prefs.getString("archive_trash","[]");
        String originalActive=prefs.getString("active_archive","default");
        boolean originalShowHidden=prefs.getBoolean("show_hidden_chats",false);
        JSONArray old=(JSONArray)value(a,"archives");
        try{
            JSONObject chat=new JSONObject().put("key",key).put("name","QA Hidden Chat")
                .put("tree","local:/nonexistent-testing-only");
            JSONArray updated=new JSONArray(old.toString());updated.put(chat);
            inst.runOnMainSync(()->{try{
                set(a,"archives",updated);set(a,"home",true);
                invoke(a,"renderHome",new Class<?>[0]);
                invoke(a,"showChatActions",new Class<?>[]{String.class,String.class},key,"QA Hidden Chat");
            }catch(Exception error){throw new AssertionError(error);}});
            assertNotNull("Hide action exists",device.wait(Until.findObject(By.text("Скрыть чат")),2500));
            device.findObject(By.text("Скрыть чат")).click();device.waitForIdle();
            assertTrue(prefs.getBoolean("hidden_chat_"+key,false));
            assertNull("Hidden chat must not be shown",device.findObject(By.text("QA Hidden Chat")));
            assertNotNull("Hidden chat remains recoverable",device.findObject(By.textContains("Показать скрытые чаты")));
            assertTrue("Hiding does not erase SQLite",a.getDatabasePath("telegram_"+key+".db").isFile());
            inst.runOnMainSync(()->{try{
                invoke(a,"removeChatFromList",new Class<?>[]{String.class},key);
            }catch(Exception error){throw new AssertionError(error);}});
            assertFalse("Removed library entry only",prefs.getString("archive_registry","[]").contains(key));
            assertTrue("Removing from list does not erase user SQLite",a.getDatabasePath("telegram_"+key+".db").isFile());
        }finally{
            inst.runOnMainSync(()->{
                prefs.edit().putString("archive_registry",originalRegistry)
                    .putString("archive_trash",originalTrash)
                    .putString("active_archive",originalActive)
                    .putBoolean("show_hidden_chats",originalShowHidden)
                    .remove("hidden_chat_"+key).remove("pinned_chat_"+key).commit();
                try{set(a,"archives",old);set(a,"home",true);invoke(a,"renderHome",new Class<?>[0]);}
                catch(Exception error){throw new AssertionError(error);}
            });
            a.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void removedChatRestoresFromTrashWithOriginalDatabaseAndMetadata()throws Exception {
        MainActivity activity=rule.getActivity();
        String key="qa_trash_"+UUID.randomUUID().toString().replace("-","");
        SharedPreferences prefs=activity.getPreferences(Context.MODE_PRIVATE);
        String initialRegistry=prefs.getString("archive_registry","[]");
        String initialTrash=prefs.getString("archive_trash","[]");
        String initialActive=prefs.getString("active_archive","default");
        JSONArray initialArray=(JSONArray)value(activity,"archives");
        MessageDb synthetic=new MessageDb(activity,key);
        android.database.sqlite.SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        synthetic.stage(sql,"Test Author","2026-01-01 12:00","Recover this message",null,"");
        synthetic.publishStaged(sql);synthetic.close();
        try{
            JSONObject entry=new JSONObject().put("key",key).put("name","QA Recoverable Chat")
                .put("tree","local:/test-recoverable-source");
            JSONArray withChat=new JSONArray(initialArray.toString());withChat.put(entry);
            inst.runOnMainSync(()->{try{
                set(activity,"archives",withChat);set(activity,"home",true);
                invoke(activity,"removeChatFromList",new Class<?>[]{String.class},key);
            }catch(Exception error){throw new AssertionError(error);}});
            assertFalse("Removed entry must leave active registry",prefs.getString("archive_registry","[]").contains(key));
            assertTrue("Trash must durably retain its registration metadata",
                prefs.getString("archive_trash","[]").contains(key));
            assertTrue("Original SQLite must remain after removal",
                activity.getDatabasePath("telegram_"+key+".db").isFile());
            inst.runOnMainSync(()->{try{
                invoke(activity,"restoreChatFromTrash",new Class<?>[]{String.class},key);
            }catch(Exception error){throw new AssertionError(error);}});
            assertTrue("Restore must make chat visible in registry",
                prefs.getString("archive_registry","[]").contains(key));
            assertFalse("Restored chat must not remain in trash",
                prefs.getString("archive_trash","[]").contains(key));
            JSONArray restored=(JSONArray)value(activity,"archives");
            assertEquals(initialArray.length()+1,restored.length());
            assertEquals("QA Recoverable Chat",restored.getJSONObject(restored.length()-1).getString("name"));
            assertEquals("local:/test-recoverable-source",
                restored.getJSONObject(restored.length()-1).getString("tree"));
            try(MessageDb db=new MessageDb(activity,key)){
                assertEquals("Reversible removal must preserve original messages",1,db.totalMessages());
                assertEquals("Recover this message",
                    db.page(0,1,"",false,"",false,false).get(0).body);
            }
            // Duplicate restore is a no-op: must not append a second entry.
            inst.runOnMainSync(()->{try{
                invoke(activity,"restoreChatFromTrash",new Class<?>[]{String.class},key);
            }catch(Exception error){throw new AssertionError(error);}});
            assertEquals(initialArray.length()+1,((JSONArray)value(activity,"archives")).length());
        }finally{
            inst.runOnMainSync(()->{
                prefs.edit().putString("archive_registry",initialRegistry)
                    .putString("archive_trash",initialTrash)
                    .putString("active_archive",initialActive).commit();
                try{
                    set(activity,"archives",initialArray);set(activity,"home",true);
                    invoke(activity,"renderHome",new Class<?>[0]);
                }catch(Exception error){throw new AssertionError(error);}
            });
            activity.deleteDatabase("telegram_"+key+".db");
        }
    }

}
