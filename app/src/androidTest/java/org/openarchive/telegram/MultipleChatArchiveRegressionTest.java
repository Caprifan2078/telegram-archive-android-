package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MultipleChatArchiveRegressionTest {
    private static void writeEntry(ZipOutputStream out,String name)throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write("<div class='history'>synthetic</div>".getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
    @Test public void multipleChatsRejectedWithoutLeavingPartialImport()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        File zip=new File(ctx.getCacheDir(),"multi-"+key+".zip");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
                writeEntry(out,"first/messages.html");
                writeEntry(out,"second/messages.html");
            }
            try {
                ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,ignored->{});
                fail("A multi-chat archive must not silently select one chat");
            } catch(java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("несколько переписок"));
            }
            assertFalse("No partial extracted archive should remain",root.exists());
        } finally { ArchiveFileImport.deleteTree(root);zip.delete(); }
    }
    @Test public void oneChatWithMultipleHtmlPartsStillAccepted()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        File zip=new File(ctx.getCacheDir(),"single-"+key+".zip");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
                writeEntry(out,"chat/messages.html");writeEntry(out,"chat/messages2.html");
            }
            File selected=ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,ignored->{});
            assertTrue(new File(selected,"messages.html").isFile());
            assertTrue(new File(selected,"messages2.html").isFile());
        } finally { ArchiveFileImport.deleteTree(root);zip.delete(); }
    }
    @Test public void deeplyNestedSecondChatIsNotSilentlyIgnored()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        File zip=new File(ctx.getCacheDir(),"deep-multi-"+key+".zip");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
                writeEntry(out,"first/messages.html");
                writeEntry(out,"a/b/c/d/e/f/second/messages.html");
            }
            try {
                ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,ignored->{});
                fail("A deeply nested second chat must not be ignored");
            } catch(java.io.IOException expected) {
                assertTrue(expected.getMessage().contains("несколько переписок"));
            }
            assertFalse("No partial extracted archive should remain",root.exists());
        } finally { ArchiveFileImport.deleteTree(root);zip.delete(); }
    }
    @Test public void deeplyNestedSingleChatIsAccepted()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        File zip=new File(ctx.getCacheDir(),"deep-single-"+key+".zip");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zip))){
                writeEntry(out,"a/b/c/d/e/f/chat/messages.html");
            }
            File selected=ArchiveFileImport.extract(ctx,Uri.fromFile(zip),"zip",key,ignored->{});
            assertTrue(new File(selected,"messages.html").isFile());
        } finally { ArchiveFileImport.deleteTree(root);zip.delete(); }
    }
}
