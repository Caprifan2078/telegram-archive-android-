package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;
import java.io.File;
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

/**
 * Non-mutating visual QA. Screenshots contain ONLY synthetic conversations,
 * staged in a throwaway local database and restored after capture.
 */
@RunWith(AndroidJUnit4.class)
public final class QaVisualAuditScreenshotsTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(inst);

    private Object get(MainActivity a,String n)throws Exception{
        Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String n,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(a,v);
    }
    private void method(MainActivity a,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);
    }
    private void ui(MainActivity a,String name)throws Exception{
        inst.runOnMainSync(()->{
            try{method(a,name,new Class<?>[0]);}
            catch(Exception e){throw new AssertionError(e);}
        });
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        ((ExecutorService)get(a,"worker")).execute(latch::countDown);
        assertTrue("Worker stuck on "+get(a,"screen"),latch.await(20,TimeUnit.SECONDS));
        inst.waitForIdleSync();
        Thread.sleep(250);
    }
    private void shot(MainActivity a,String name)throws Exception{
        idle(a);
        File folder=a.getExternalFilesDir("visual-audit");
        assertNotNull(folder);assertTrue(folder.isDirectory()||folder.mkdirs());
        File png=new File(folder,name+".png");
        assertTrue("Screenshot failed: "+name,device.takeScreenshot(png));
        assertTrue("Screenshot too small: "+name,png.length()>12000);
        // Scoped storage hides Android/data from host ADB. Publish ONLY synthetic
        // emulator captures to MediaStore Pictures, which survives test-app uninstall.
        android.content.ContentValues values=new android.content.ContentValues();
        values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name+".png");
        values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/TelegramViewerVisualAudit");
        values.put(android.provider.MediaStore.Images.Media.IS_PENDING,1);
        android.net.Uri saved=a.getContentResolver().insert(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        assertNotNull("MediaStore did not create screenshot",saved);
        try(java.io.InputStream in=new java.io.FileInputStream(png);
            java.io.OutputStream out=a.getContentResolver().openOutputStream(saved)){
            assertNotNull("MediaStore output stream unavailable",out);
            byte[] buffer=new byte[32768];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
        }
        android.content.ContentValues ready=new android.content.ContentValues();
        ready.put(android.provider.MediaStore.Images.Media.IS_PENDING,0);
        a.getContentResolver().update(saved,ready,null,null);
        System.out.println("VISUAL_CAPTURE "+name+" "+png.getAbsolutePath()+" bytes="+png.length());
    }

    @Test public void captureRealMainScreens()throws Exception {
        MainActivity a=rule.getActivity();
        String key="visual"+UUID.randomUUID().toString().replace("-","");
        String key2="visual"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        MessageDb second=new MessageDb(a,key2);
        SQLiteDatabase sql=synthetic.getWritableDatabase();
        synthetic.clearStage(sql);
        String[] lines={
            "Привет! Это пример переписки для проверки интерфейса.",
            "Всё хорошо, давай обсудим фотографии и поездку.",
            "Сегодня получилось скачать наш архив сообщений.",
            "А где находится функция поиска по истории?",
            "Нажми на название чата, там собраны фотографии и файлы.",
            "Очень удобно. А экспорт в PDF есть?",
            "Да, отдельная кнопка PDF вверху переписки.",
            "Завтра сможем продолжить обсуждение?",
            "Конечно, примерно после 17:00.",
            "Отправлю список задач.",
            "Сначала надо закончить оформление.",
            "Хорошая идея, минимализм лучше перегруженных меню.",
            "Это ещё один пример текстового сообщения.",
            "Важно, чтобы можно было быстро найти нужный день."
        };
        for(int i=0;i<lines.length;i++){
            String d=String.format(java.util.Locale.ROOT,"2026-11-%02d %02d:15:00",1+i/5,10+i%5);
            synthetic.stage(sql,i%2==0?"Александр":"Мария",d,lines[i],null,"");
        }
        synthetic.publishStaged(sql);
        // Real dated events exercise the actual timeline, not only its empty state.
        java.util.List<MessageDb.Item> chatItems=synthetic.page(0,14,"",false,"",false,false);
        assertTrue("Synthetic chat missing timeline anchors",chatItems.size()>=10);
        synthetic.addRelationshipEvent("Первый разговор","Начало общей истории",chatItems.get(1).id,0);
        synthetic.addRelationshipEvent("Планы на поездку","Обсудили будущую встречу",chatItems.get(8).id,0);
        SQLiteDatabase sql2=second.getWritableDatabase();
        second.clearStage(sql2);
        second.stage(sql2,"Студент","2026-11-03 11:20:00","Завтра сдаём контрольную работу.",null,"");
        second.publishStaged(sql2);
        Object oldDb=get(a,"db"),oldKey=get(a,"activeKey"),oldName=get(a,"activeName"),
            oldHome=get(a,"home"),oldScreen=get(a,"screen"),oldFolder=get(a,"folder"),oldArchives=get(a,"archives");
        String oldActive=a.getPreferences(Context.MODE_PRIVATE).getString("active_archive","default");
        String oldRegistry=a.getPreferences(Context.MODE_PRIVATE).getString("archive_registry","[]");
        try{
            JSONArray entries=new JSONArray();
            entries.put(new JSONObject().put("key",key).put("name","Семья и друзья").put("tree",""));
            entries.put(new JSONObject().put("key",key2).put("name","Учебная группа").put("tree",""));
            inst.runOnMainSync(()->{
                try{
                    put(a,"archives",entries);put(a,"activeKey",key);put(a,"activeName","Семья и друзья");
                    put(a,"db",synthetic);put(a,"folder",null);put(a,"home",true);put(a,"screen","chat");
                    method(a,"renderHome",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"01-home");
            ui(a,"chooseFolder");shot(a,"02-import");
            device.pressBack();inst.waitForIdleSync();
            ui(a,"showSideMenu");shot(a,"03-drawer");
            device.pressBack();inst.waitForIdleSync();
            ui(a,"renderHome");
            inst.runOnMainSync(()->{
                try{method(a,"showChatActions",new Class<?>[]{String.class,String.class},key,"Семья и друзья");}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"04-chat-actions");
            device.pressBack();inst.waitForIdleSync();
            inst.runOnMainSync(()->{
                try{put(a,"home",false);put(a,"screen","chat");method(a,"showPage",new Class<?>[0]);}
                catch(Exception e){throw new AssertionError(e);}
            });
            assertNotNull("Chat must actually finish loading before a visual capture",
                device.wait(androidx.test.uiautomator.Until.findObject(
                    androidx.test.uiautomator.By.textContains("Привет! Это пример переписки")),20000));
            shot(a,"05-chat");
            ui(a,"showConversationInfo");shot(a,"06-chat-info");
            device.pressBack();inst.waitForIdleSync();
            inst.runOnMainSync(()->{
                try{View anchor=(View)get(a,"headerActions");method(a,"showConversationMenu",new Class<?>[]{View.class},anchor);}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"07-chat-menu");
            device.pressBack();inst.waitForIdleSync();
            inst.runOnMainSync(()->{
                try{put(a,"screen","media");method(a,"showPage",new Class<?>[0]);}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"08-media");
            inst.runOnMainSync(()->{
                try{put(a,"screen","relationships");method(a,"showPage",new Class<?>[0]);}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"09-chronology-home");
            inst.runOnMainSync(()->{
                try{put(a,"screen","chronology");method(a,"showPage",new Class<?>[0]);}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"10-chronology-periods");
            inst.runOnMainSync(()->{
                try{put(a,"screen","analytics");method(a,"showPage",new Class<?>[0]);}
                catch(Exception e){throw new AssertionError(e);}
            });
            shot(a,"11-analytics");
            // MediaStore screenshots stay available to CI after app uninstall.
        }finally{
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    put(a,"db",oldDb);put(a,"activeKey",oldKey);put(a,"activeName",oldName);
                    put(a,"home",oldHome);put(a,"screen",oldScreen);put(a,"folder",oldFolder);
                    put(a,"archives",oldArchives);
                    a.getPreferences(Context.MODE_PRIVATE).edit().putString("active_archive",oldActive).putString("archive_registry",oldRegistry).commit();
                    method(a,"showPage",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();second.close();
            a.deleteDatabase("telegram_"+key+".db");a.deleteDatabase("telegram_"+key2+".db");
        }
    }
}
