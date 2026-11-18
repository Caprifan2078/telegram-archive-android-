package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

/** Actual standalone HTML entry point: synthetic local file, isolated archive, no personal data. */
@RunWith(AndroidJUnit4.class)
public final class QaStandaloneHtmlImportRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();

    private Object field(MainActivity activity,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(activity);
    }
    private void set(MainActivity activity,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);f.set(activity,value);
    }
    private void invoke(MainActivity activity,String name,Class<?>[] argTypes,Object... args)throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name,argTypes);
        method.setAccessible(true);method.invoke(activity,args);
    }
    private void awaitWorker(MainActivity activity)throws Exception{
        CountDownLatch marker=new CountDownLatch(1);
        ((ExecutorService)field(activity,"worker")).execute(marker::countDown);
        assertTrue("HTML importer did not finish",marker.await(20,TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
    }

    @Test public void singleHtmlBecomesOrdinaryChatWithoutReplacingExistingRegistry()throws Exception {
        MainActivity activity=rule.getActivity();
        String name="QA HTML "+UUID.randomUUID().toString().substring(0,8);
        File original=new File(activity.getCacheDir(),"qa-html-"+UUID.randomUUID()+".html");
        String fixture="<div class='history'><div class='message default' id='message101'>"+
            "<div class='body'><div class='from_name'>Alice</div>"+
            "<div class='date details' title='2024-01-01 12:00:00'></div>"+
            "<div class='text'>Safe standalone HTML import</div></div></div></div>";
        try(FileOutputStream out=new FileOutputStream(original)){
            out.write(fixture.getBytes(StandardCharsets.UTF_8));
        }
        String registry=activity.getPreferences(Context.MODE_PRIVATE).getString("archive_registry","[]");
        String initialActive=activity.getPreferences(Context.MODE_PRIVATE).getString("active_archive","default");
        String oldName=(String)field(activity,"activeName");
        String oldKey=(String)field(activity,"activeKey");
        String oldScreen=(String)field(activity,"screen");
        boolean oldHome=(boolean)field(activity,"home");
        Object oldFolder=field(activity,"folder");
        JSONArray before=(JSONArray)field(activity,"archives");
        String freshKey=null;
        try {
            instrumentation.runOnMainSync(()->{
                try{
                    invoke(activity,"importSingleHtml",new Class<?>[]{Uri.class,String.class},
                        Uri.fromFile(original),name);
                }catch(Exception e){throw new AssertionError(e);}
            });
            awaitWorker(activity);
            JSONArray latest=(JSONArray)field(activity,"archives");
            assertEquals("Single HTML created one registered chat",before.length()+1,latest.length());
            JSONObject added=latest.optJSONObject(latest.length()-1);
            assertNotNull(added);
            freshKey=added.optString("key");
            assertEquals(name,added.optString("name"));
            assertTrue(added.optString("tree").startsWith("local:"));
            assertEquals(freshKey,field(activity,"activeKey"));
            assertEquals(false,field(activity,"home"));
            assertEquals("chat",field(activity,"screen"));
            assertEquals(1,((MessageDb)field(activity,"db")).totalMessages());
            assertEquals("Safe standalone HTML import",
                ((MessageDb)field(activity,"db")).page(0,1,"",false,"",false,false).get(0).body);
        }finally {
            awaitWorker(activity);
            final String cleanupKey=freshKey;
            instrumentation.runOnMainSync(()->{
                try{
                    MessageDb current=(MessageDb)field(activity,"db");
                    if(current!=null)current.close();
                    set(activity,"db",new MessageDb(activity,oldKey));
                    set(activity,"activeName",oldName);set(activity,"activeKey",oldKey);
                    set(activity,"home",oldHome);set(activity,"screen",oldScreen);
                    set(activity,"folder",oldFolder);set(activity,"archives",before);
                    activity.getPreferences(Context.MODE_PRIVATE).edit()
                        .putString("archive_registry",registry).putString("active_archive",initialActive).commit();
                    invoke(activity,"showPage",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            if(cleanupKey!=null&&!cleanupKey.isEmpty()){
                activity.deleteDatabase("telegram_"+cleanupKey+".db");
                ArchiveFileImport.deleteTree(new File(new File(activity.getFilesDir(),"telegram_imports"),cleanupKey));
            }
            original.delete();
        }
    }
}
