package org.openarchive.telegram;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** User-reported edge-to-edge drawer and strong AES-256 ZIP import regressions. */
@RunWith(AndroidJUnit4.class)
public final class DrawerAesZipRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void drawerWindowExtendsBehindStatusAndGestureBars()throws Exception{
        MainActivity activity=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            Dialog dialog=new Dialog(activity,android.R.style.Theme_Translucent_NoTitleBar);
            dialog.setContentView(new FrameLayout(activity));
            dialog.show();
            try{
                Window window=dialog.getWindow();
                MainActivity.styleDrawerWindow(window,false);
                int flags=window.getDecorView().getSystemUiVisibility();
                assertTrue((flags&View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)!=0);
                assertTrue((flags&View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)!=0);
                assertEquals(Color.TRANSPARENT,window.getStatusBarColor());
                assertEquals(Color.TRANSPARENT,window.getNavigationBarColor());
                assertEquals(WindowManager.LayoutParams.MATCH_PARENT,window.getAttributes().width);
                assertEquals(WindowManager.LayoutParams.MATCH_PARENT,window.getAttributes().height);
                if(Build.VERSION.SDK_INT>=29){
                    assertFalse(window.isStatusBarContrastEnforced());
                    assertFalse(window.isNavigationBarContrastEnforced());
                }
            }finally{dialog.dismiss();}
        });
    }

    @Test public void largeArchiveFallbackUsesFreeSpaceInsteadOfFixedTwoGigabyteLimit(){
        long free=12L*1024*1024*1024;
        long limit=ArchiveFileImport.maxTemporaryCopyForFreeBytes(free);
        assertTrue("Large Telegram exports must not have an arbitrary 2 GiB cutoff",
            limit>4L*1024*1024*1024);
        assertTrue("Keep storage reserve for Android and the extracted content",limit<free);
        assertEquals(0,ArchiveFileImport.maxTemporaryCopyForFreeBytes(128L*1024*1024));
        assertEquals("No usable space must never permit an unbounded temp copy",0,
            ArchiveFileImport.maxTemporaryCopyForFreeBytes(0));
        assertEquals("Unknown/negative free-space readings must fail closed",0,
            ArchiveFileImport.maxTemporaryCopyForFreeBytes(-1));
    }

    @Test public void fallbackOpensAes256ZipThroughCentralDirectory()throws Exception{
        char[] password="pass-strong-AES-256".toCharArray();
        File html=File.createTempFile("telegram-zip-fixture-", ".html",context.getCacheDir());
        File archive=File.createTempFile("telegram-zip-7zip-like-", ".zip",context.getCacheDir());
        File root=new File(new File(context.getFilesDir(),"telegram_imports"),
            UUID.randomUUID().toString().replace("-",""));
        try{
            try(FileOutputStream out=new FileOutputStream(html)){
                out.write(("<div class='history'><div class='message default' id='message7'>"+
                    "<div class='body'>Encrypted external ZIP test</div></div></div>")
                    .getBytes(StandardCharsets.UTF_8));
            }
            try(ZipFile writer=new ZipFile(archive,password)){
                ZipParameters parameters=new ZipParameters();
                parameters.setFileNameInZip("TelegramExport/messages.html");
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
                parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                writer.addFile(html,parameters);
            }
            assertTrue(root.mkdirs());
            ArchiveFileImport.extractZipFromCentralDirectory(context,Uri.fromFile(archive),
                root,password,new ArchiveFileImport.Budget(0,0,root),message->{});
            File restored=new File(root,"TelegramExport/messages.html");
            assertTrue(restored.isFile());
            assertTrue(restored.length()>50);
            assertTrue(new String(java.nio.file.Files.readAllBytes(restored.toPath()),
                StandardCharsets.UTF_8).contains("Encrypted external ZIP test"));
        }finally{
            ArchiveFileImport.deleteTree(root);
            html.delete();archive.delete();
            java.util.Arrays.fill(password,'\0');
        }
    }

    @Test public void fallbackRejectsWrongAes256Password()throws Exception{
        char[] secret="correct-AES-password".toCharArray();
        File html=File.createTempFile("telegram-wrong-password-", ".html",context.getCacheDir());
        File archive=File.createTempFile("telegram-wrong-password-", ".zip",context.getCacheDir());
        File root=new File(new File(context.getFilesDir(),"telegram_imports"),
            UUID.randomUUID().toString().replace("-",""));
        try{
            try(FileOutputStream out=new FileOutputStream(html)){
                out.write("Synthetic secret".getBytes(StandardCharsets.UTF_8));
            }
            try(ZipFile writer=new ZipFile(archive,secret)){
                ZipParameters parameters=new ZipParameters();
                parameters.setFileNameInZip("messages.html");
                parameters.setEncryptFiles(true);
                parameters.setEncryptionMethod(EncryptionMethod.AES);
                parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                writer.addFile(html,parameters);
            }
            assertTrue(root.mkdirs());
            try{
                ArchiveFileImport.extractZipFromCentralDirectory(context,Uri.fromFile(archive),
                    root,"wrong-password".toCharArray(),new ArchiveFileImport.Budget(0,0,root),m->{});
                fail("Wrong AES key must never extract a file");
            }catch(net.lingala.zip4j.exception.ZipException expected){
                assertFalse(new File(root,"messages.html").length()>0);
            }
        }finally{
            ArchiveFileImport.deleteTree(root);
            html.delete();archive.delete();
            java.util.Arrays.fill(secret,'\0');
        }
    }
}
