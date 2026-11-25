package org.openarchive.telegram;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
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
        UiDevice device=UiDevice.getInstance(inst);
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
            // FLAG_SECURE may hide dialog nodes from UiAutomator on Android 15.
            // Inspect the actual dialog with Espresso without disabling security.
            long deadline=android.os.SystemClock.uptimeMillis()+20000;
            boolean visible=false;
            while(android.os.SystemClock.uptimeMillis()<deadline&&!visible){
                try{
                    androidx.test.espresso.Espresso.onView(
                        androidx.test.espresso.matcher.ViewMatchers.withText("Этот архив защищён паролем"))
                        .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                        .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
                    visible=true;
                }catch(androidx.test.espresso.NoMatchingViewException|androidx.test.espresso.NoMatchingRootException e){
                    android.os.SystemClock.sleep(100);
                }
            }
            assertTrue("Encrypted 7z should automatically show a password prompt",visible);
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withHint("Пароль от архива"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .check(androidx.test.espresso.assertion.ViewAssertions.matches(
                    androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            androidx.test.espresso.Espresso.onView(
                androidx.test.espresso.matcher.ViewMatchers.withText("Отмена"))
                .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog())
                .perform(androidx.test.espresso.action.ViewActions.click());
            inst.waitForIdleSync();
            File dir=new File(root);
            int after=dir.listFiles()==null?0:dir.listFiles().length;
            assertEquals("Password request must not leave half-extracted chat folders",before,after);
        }finally{html.delete();encrypted.delete();}
    }
}
