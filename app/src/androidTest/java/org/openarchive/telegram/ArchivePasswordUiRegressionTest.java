package org.openarchive.telegram;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Tests the actual Android file picker return -> encrypted-7z -> password prompt. */
@RunWith(AndroidJUnit4.class)
public final class ArchivePasswordUiRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    @Test public void pickerOpensEncryptedSevenZAndRequestsPasswordOnlyAfterDetection() throws Exception {
        MainActivity activity=rule.getActivity();
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        File html=File.createTempFile("synthetic-telegram-", ".html",activity.getCacheDir());
        File encrypted=File.createTempFile("synthetic-picker-", ".7z",activity.getCacheDir());
        String root=new File(activity.getFilesDir(),"telegram_imports").getAbsolutePath();
        int before=new File(root).listFiles()==null?0:new File(root).listFiles().length;
        try{
            String content="<div class='history'><div class='message default' id='message50'><div class='body'>"+
                "<div class='from_name'>Synthetic</div><div class='text'>Encrypted 7z file</div></div></div></div>";
            try(FileOutputStream out=new FileOutputStream(html)){
                out.write(content.getBytes(StandardCharsets.UTF_8));
            }
            try(SevenZOutputFile zip=new SevenZOutputFile(encrypted,
                    "only-test-password".toCharArray())){
                SevenZArchiveEntry e=zip.createArchiveEntry(html,"messages.html");
                zip.putArchiveEntry(e);zip.write(content.getBytes(StandardCharsets.UTF_8));
                zip.closeArchiveEntry();zip.finish();
            }
            Intent result=new Intent();result.setData(Uri.fromFile(encrypted));
            inst.runOnMainSync(()->activity.onActivityResult(57,Activity.RESULT_OK,result));
            // Secure windows can be inaccessible or temporarily unfocused on API 35.
            // Read the actual live AlertDialog on the main thread, never disable FLAG_SECURE.
            java.lang.reflect.Field field=MainActivity.class.getDeclaredField("archivePasswordDialog");
            field.setAccessible(true);
            java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog> dialog=new java.util.concurrent.atomic.AtomicReference<>();
            long deadline=android.os.SystemClock.uptimeMillis()+20000;
            while(android.os.SystemClock.uptimeMillis()<deadline&&dialog.get()==null){
                inst.runOnMainSync(()->{
                    try{
                        android.app.AlertDialog actual=(android.app.AlertDialog)field.get(activity);
                        if(actual!=null&&actual.isShowing()&&actual.getWindow()!=null)dialog.set(actual);
                    }catch(IllegalAccessException e){throw new AssertionError(e);}
                });
                if(dialog.get()==null)android.os.SystemClock.sleep(100);
            }
            assertNotNull("Encrypted 7z must open a real password dialog",dialog.get());
            inst.runOnMainSync(()->{
                android.app.AlertDialog actual=dialog.get();
                assertTrue((actual.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0);
                android.widget.EditText password=findPassword(actual.getWindow().getDecorView());
                assertNotNull("Password entry exists in secure window",password);
                assertTrue(password.isShown());
                assertEquals("Пароль от архива",password.getHint().toString());
                assertTrue(actual.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
                assertTrue("Empty password must keep the secure dialog open",actual.isShowing());
                assertNotNull(password.getError());
                assertTrue(actual.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick());
            });
            inst.waitForIdleSync();
            File dir=new File(root);
            int after=dir.listFiles()==null?0:dir.listFiles().length;
            assertEquals("Password request must not leave half-extracted chat folders",before,after);
        }finally{html.delete();encrypted.delete();}
    }
    private static android.widget.EditText findPassword(android.view.View view){
        if(view instanceof android.widget.EditText)return (android.widget.EditText)view;
        if(view instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.EditText result=findPassword(group.getChildAt(i));if(result!=null)return result;
            }
        }
        return null;
    }
}
