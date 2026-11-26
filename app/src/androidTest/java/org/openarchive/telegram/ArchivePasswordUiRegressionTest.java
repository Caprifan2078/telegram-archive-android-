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
    @Test public void realExternalAesZipCanOpenViaPasswordDialog() throws Exception {
        MainActivity activity=rule.getActivity();
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        File zip=File.createTempFile("external-ui-", ".zip",activity.getCacheDir());
        try{
            try(java.io.InputStream in=inst.getContext().getAssets().open("external7zip/aes256.zip");FileOutputStream out=new FileOutputStream(zip)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            byte[] original=java.nio.file.Files.readAllBytes(zip.toPath());
            java.lang.reflect.Field dialogField=MainActivity.class.getDeclaredField("archivePasswordDialog");dialogField.setAccessible(true);
            java.lang.reflect.Field keyField=MainActivity.class.getDeclaredField("activeKey");keyField.setAccessible(true);
            java.lang.reflect.Field dbField=MainActivity.class.getDeclaredField("db");dbField.setAccessible(true);
            final String oldKey=(String)keyField.get(activity);
            Intent intent=new Intent().setData(Uri.fromFile(zip));
            inst.runOnMainSync(()->activity.onActivityResult(57,Activity.RESULT_OK,intent));
            java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog> live=new java.util.concurrent.atomic.AtomicReference<>();
            long deadline=android.os.SystemClock.uptimeMillis()+20000;
            while(live.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
                inst.runOnMainSync(()->{try{android.app.AlertDialog d=(android.app.AlertDialog)dialogField.get(activity);if(d!=null&&d.isShowing())live.set(d);}catch(Exception ex){throw new AssertionError(ex);}});
                if(live.get()==null)android.os.SystemClock.sleep(100);
            }
            assertNotNull("Real AES ZIP did not request password",live.get());
            inst.runOnMainSync(()->{
                android.app.AlertDialog dialog=live.get();
                android.widget.EditText input=findPassword(dialog.getWindow().getDecorView());
                assertNotNull(input);input.setText("qa-7zip-password");
                assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            });
            java.util.concurrent.atomic.AtomicReference<String> imported=new java.util.concurrent.atomic.AtomicReference<>();
            deadline=android.os.SystemClock.uptimeMillis()+30000;
            while(imported.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
                inst.runOnMainSync(()->{try{
                    String key=(String)keyField.get(activity);
                    MessageDb db=(MessageDb)dbField.get(activity);
                    if(!oldKey.equals(key)&&db!=null&&db.totalMessages()>0)imported.set(key);
                }catch(Exception ex){throw new AssertionError(ex);}});
                if(imported.get()==null)android.os.SystemClock.sleep(100);
            }
            assertNotNull("Correct password did not import real AES ZIP",imported.get());
            assertArrayEquals("Source file changed",original,java.nio.file.Files.readAllBytes(zip.toPath()));
        }finally{zip.delete();}
    }
    @Test public void realExternalDeflate64AesZipCanOpenViaPasswordDialog() throws Exception {
        MainActivity activity=rule.getActivity();
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        File zip=File.createTempFile("external-ui-", ".zip",activity.getCacheDir());
        try{
            try(java.io.InputStream in=inst.getContext().getAssets().open("zip_codec_probe/Deflate64.zip");FileOutputStream out=new FileOutputStream(zip)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            byte[] original=java.nio.file.Files.readAllBytes(zip.toPath());
            java.lang.reflect.Field dialogField=MainActivity.class.getDeclaredField("archivePasswordDialog");dialogField.setAccessible(true);
            java.lang.reflect.Field keyField=MainActivity.class.getDeclaredField("activeKey");keyField.setAccessible(true);
            java.lang.reflect.Field dbField=MainActivity.class.getDeclaredField("db");dbField.setAccessible(true);
            final String oldKey=(String)keyField.get(activity);
            Intent intent=new Intent().setData(Uri.fromFile(zip));
            inst.runOnMainSync(()->activity.onActivityResult(57,Activity.RESULT_OK,intent));
            java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog> live=new java.util.concurrent.atomic.AtomicReference<>();
            long deadline=android.os.SystemClock.uptimeMillis()+20000;
            while(live.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
                inst.runOnMainSync(()->{try{android.app.AlertDialog d=(android.app.AlertDialog)dialogField.get(activity);if(d!=null&&d.isShowing())live.set(d);}catch(Exception ex){throw new AssertionError(ex);}});
                if(live.get()==null)android.os.SystemClock.sleep(100);
            }
            assertNotNull("Real AES Deflate64 ZIP did not request password",live.get());
            inst.runOnMainSync(()->{
                android.app.AlertDialog dialog=live.get();
                android.widget.EditText input=findPassword(dialog.getWindow().getDecorView());
                assertNotNull(input);input.setText("codec-test-only");
                assertTrue(dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
            });
            java.util.concurrent.atomic.AtomicReference<String> imported=new java.util.concurrent.atomic.AtomicReference<>();
            deadline=android.os.SystemClock.uptimeMillis()+30000;
            while(imported.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
                inst.runOnMainSync(()->{try{
                    String key=(String)keyField.get(activity);
                    MessageDb db=(MessageDb)dbField.get(activity);
                    if(!oldKey.equals(key)&&db!=null&&db.totalMessages()>0)imported.set(key);
                }catch(Exception ex){throw new AssertionError(ex);}});
                if(imported.get()==null)android.os.SystemClock.sleep(100);
            }
            assertNotNull("Correct password did not import real AES Deflate64 ZIP",imported.get());
            assertArrayEquals("Source file changed",original,java.nio.file.Files.readAllBytes(zip.toPath()));
        }finally{zip.delete();}
    }
    @Test public void realSolid7zThroughContentProviderAndPasswordDialogImportsAllTwentyPages()throws Exception{
        MainActivity activity=rule.getActivity();Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        java.lang.reflect.Field dialogField=MainActivity.class.getDeclaredField("archivePasswordDialog");dialogField.setAccessible(true);
        java.lang.reflect.Field keyField=MainActivity.class.getDeclaredField("activeKey");keyField.setAccessible(true);
        java.lang.reflect.Field dbField=MainActivity.class.getDeclaredField("db");dbField.setAccessible(true);
        String oldKey=(String)keyField.get(activity);
        Uri source=Uri.parse("content://"+ArchivePipeTestProvider.AUTHORITY+"/layout/solid-header");
        Intent intent=new Intent().setData(source);
        inst.runOnMainSync(()->activity.onActivityResult(57,Activity.RESULT_OK,intent));
        java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog> live=new java.util.concurrent.atomic.AtomicReference<>();
        long deadline=android.os.SystemClock.uptimeMillis()+30000;
        while(live.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
            inst.runOnMainSync(()->{try{android.app.AlertDialog d=(android.app.AlertDialog)dialogField.get(activity);if(d!=null&&d.isShowing())live.set(d);}catch(Exception ex){throw new AssertionError(ex);}});
            if(live.get()==null)android.os.SystemClock.sleep(100);
        }
        assertNotNull("Real solid 7z from provider did not request a password",live.get());
        inst.runOnMainSync(()->{
            android.app.AlertDialog d=live.get();
            android.widget.EditText input=findPassword(d.getWindow().getDecorView());
            assertNotNull(input);input.setText("qa-layout-secret");
            assertTrue(d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick());
        });
        java.util.concurrent.atomic.AtomicReference<String> imported=new java.util.concurrent.atomic.AtomicReference<>();
        deadline=android.os.SystemClock.uptimeMillis()+90000;
        while(imported.get()==null&&android.os.SystemClock.uptimeMillis()<deadline){
            inst.runOnMainSync(()->{try{
                String key=(String)keyField.get(activity);MessageDb db=(MessageDb)dbField.get(activity);
                if(!oldKey.equals(key)&&db!=null&&db.totalMessages()==20)imported.set(key);
            }catch(Exception ex){throw new AssertionError(ex);}});
            if(imported.get()==null)android.os.SystemClock.sleep(100);
        }
        assertNotNull("Real encrypted solid 7z through the actual import UI did not import all pages",imported.get());
        File root=new File(new File(activity.getFilesDir(),"telegram_imports"),imported.get());
        assertTrue(new File(root,"messages20.html").isFile());
        String[] media=new File(root,"round_video_messages").list();
        assertNotNull(media);assertEquals(834,media.length);
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
