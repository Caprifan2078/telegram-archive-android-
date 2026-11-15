package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Exercise one-file HTML import and reversible hide state with private synthetic data. */
@RunWith(AndroidJUnit4.class)
public final class UnifiedImportScreenRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private final androidx.test.platform.app.InstrumentationRegistry unused=null;

    private SharedPreferences prefs(Context context){
        String name=new ComponentName(context,MainActivity.class).getShortClassName();
        return context.getSharedPreferences(name.startsWith(".")?name.substring(1):name,Context.MODE_PRIVATE);
    }
    private static Object field(Object owner,String name)throws Exception {
        Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);
    }
    private static void invoke(Object owner,String name,Class<?>[] types,Object... values)throws Exception {
        Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,values);
    }
    private static File makeFile(Context ctx,String text)throws Exception{
        File file=File.createTempFile("synthetic-telegram-", ".html",ctx.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(file)){out.write(text.getBytes(StandardCharsets.UTF_8));}
        return file;
    }
    private static final String VALID_HTML=
        "<html><body><div class='history'><div class='message default' id='message1'>"+
        "<div class='body'><div class='from_name'>Synthetic</div>"+
        "<div class='date details' title='2024-02-29 12:00:00'></div>"+
        "<div class='text'>Standalone HTML sentinel</div></div></div></div></body></html>";

    private void htmlImport(boolean valid)throws Exception {
        MainActivity a=activity.getActivity();
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);
        String before=p.getString("archive_registry","[]");
        String active=p.getString("active_archive","default");
        File source=makeFile(ctx,valid?VALID_HTML:"<html><body><p>Not a Telegram export</p></body></html>");
        byte[] initial=java.nio.file.Files.readAllBytes(source.toPath());
        String key=null;
        try{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{invoke(a,"importStandaloneHtml",new Class[]{Uri.class,String.class},Uri.fromFile(source),
                    "ChatExport_2026-10-23.html");}
                catch(Exception failure){throw new AssertionError(failure);}
            });
            ExecutorService worker=(ExecutorService)field(a,"worker");
            CountDownLatch done=new CountDownLatch(1);worker.execute(done::countDown);
            assertTrue("HTML import worker never completed",done.await(30,TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            JSONArray after=new JSONArray(p.getString("archive_registry","[]"));
            int previous=new JSONArray(before).length();
            if(valid){
                assertEquals("HTML chat was not registered",previous+1,after.length());
                JSONObject chat=after.getJSONObject(after.length()-1);
                key=chat.getString("key");
                assertEquals("Переписка",chat.getString("name"));
                MessageDb imported=new MessageDb(ctx,key);
                try {
                    assertEquals(1,imported.totalMessages());
                    assertEquals(1,imported.count("Standalone HTML sentinel",false,"",false,false));
                }finally{imported.close();}
            }else{
                assertEquals("Invalid HTML must not register a chat",previous,after.length());
            }
            assertArrayEquals("Selected user document must never be modified",initial,
                java.nio.file.Files.readAllBytes(source.toPath()));
        } finally {
            a.finish();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if(key!=null){
                ctx.deleteDatabase("telegram_"+key+".db");
                ArchiveFileImport.deleteTree(new File(new File(ctx.getFilesDir(),"telegram_imports"),key));
            }
            p.edit().putString("archive_registry",before).putString("active_archive",active).commit();
            source.delete();
        }
    }
    @Test public void standaloneHtmlIsIndexedWithoutChangingOriginal()throws Exception {
        htmlImport(true);
    }
    @Test public void invalidHtmlCannotRegisterFakeTelegramChat()throws Exception {
        htmlImport(false);
    }

    @Test public void hideAndRestoreKeepRegistryUnchanged()throws Exception {
        MainActivity a=activity.getActivity();
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);
        String key=UUID.randomUUID().toString().replace("-","");
        String previousRegistry=p.getString("archive_registry","[]");
        String previousActive=p.getString("active_archive","default");
        try {
            JSONObject synthetic=new JSONObject().put("key",key).put("name","QA reversible synthetic")
                .put("tree","");
            JSONArray entries=new JSONArray(previousRegistry);entries.put(synthetic);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try {
                    Field f=a.getClass().getDeclaredField("archives");f.setAccessible(true);f.set(a,entries);
                    invoke(a,"saveRegistry",new Class[0]);
                    invoke(a,"setChatVisibility",new Class[]{String.class,boolean.class,boolean.class},
                        key,true,false);
                }catch(Exception problem){throw new AssertionError(problem);}
            });
            assertTrue(p.getBoolean("hidden_chat_"+key,false));
            assertFalse(p.getBoolean("trashed_chat_"+key,false));
            assertEquals(entries.length(),new JSONArray(p.getString("archive_registry","[]")).length());
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                try{invoke(a,"setChatVisibility",new Class[]{String.class,boolean.class,boolean.class},
                    key,false,false);}
                catch(Exception problem){throw new AssertionError(problem);}
            });
            assertFalse(p.getBoolean("hidden_chat_"+key,false));
            assertEquals(entries.length(),new JSONArray(p.getString("archive_registry","[]")).length());
        }finally{
            p.edit().putString("archive_registry",previousRegistry)
                .putString("active_archive",previousActive)
                .remove("hidden_chat_"+key).remove("trashed_chat_"+key).commit();
            ctx.deleteDatabase("telegram_"+key+".db");
        }
    }
}
