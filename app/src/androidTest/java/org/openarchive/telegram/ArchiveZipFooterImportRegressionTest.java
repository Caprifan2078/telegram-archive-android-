package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Corrupt Telegram ZIPs must not appear importable when the central-directory footer is missing. */
@RunWith(AndroidJUnit4.class)
public final class ArchiveZipFooterImportRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final byte[] HTML=(
        "<html><body><div class='history'><div class='message default' id='message1'>" +
        "<div class='body'><div class='from_name'>Synthetic</div>" +
        "<div class='text'>Private message stays local</div></div></div></div></body></html>"
    ).getBytes(StandardCharsets.UTF_8);

    private static byte[] archive(String comment) throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(output)) {
            if(comment!=null)zip.setComment(comment);
            zip.putNextEntry(new ZipEntry("chat/messages.html"));
            zip.write(HTML);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("chat/photos/synthetic.jpg"));
            zip.write(new byte[]{1,2,3,4});
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private File source(byte[] bytes,String key)throws Exception {
        File file=new File(ctx.getCacheDir(),"zip-footer-import-"+key+".zip");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}
        return file;
    }

    private void accepted(byte[] bytes)throws Exception { accepted(bytes,null); }
    private void accepted(byte[] bytes,char[] password)throws Exception {
        String key=UUID.randomUUID().toString().replace("-","");
        File original=source(bytes,key);
        File extracted=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            File selected=ArchiveFileImport.extract(ctx,Uri.fromFile(original),"zip",key,password,ignored->{});
            assertEquals(new File(extracted,"chat").getCanonicalPath(),selected.getCanonicalPath());
            assertTrue(new File(selected,"messages.html").isFile());
            assertTrue(new File(selected,"photos/synthetic.jpg").isFile());
        }finally{
            ArchiveFileImport.deleteTree(extracted);
            original.delete();
        }
    }

    private void rejected(byte[] bytes)throws Exception { rejected(bytes,null); }
    private void rejected(byte[] bytes,char[] password)throws Exception {
        String key=UUID.randomUUID().toString().replace("-","");
        File original=source(bytes,key);
        File extracted=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try {
            try {
                ArchiveFileImport.extract(ctx,Uri.fromFile(original),"zip",key,ignored->{});
                fail("Truncated ZIP was imported despite missing its central-directory footer");
            }catch(IOException expected){
                assertNotNull(expected.getMessage());
                assertTrue("The user should see a ZIP integrity error: "+expected.getMessage(),
                    expected.getMessage().toLowerCase(java.util.Locale.ROOT).contains("zip"));
            }
            assertFalse("Failed import must remove partially extracted private data",extracted.exists());
        }finally{
            ArchiveFileImport.deleteTree(extracted);
            original.delete();
        }
    }

    private byte[] encryptedArchive() throws Exception {
        File html=File.createTempFile("encrypted-telegram-", ".html",ctx.getCacheDir());
        File zip=File.createTempFile("encrypted-telegram-", ".zip",ctx.getCacheDir());
        char[] password="synthetic-test-password".toCharArray();
        try {
            try(FileOutputStream out=new FileOutputStream(html)){out.write(HTML);}
            try(net.lingala.zip4j.ZipFile writer=new net.lingala.zip4j.ZipFile(zip,password)){
                net.lingala.zip4j.model.ZipParameters p=new net.lingala.zip4j.model.ZipParameters();
                p.setFileNameInZip("chat/messages.html");
                p.setEncryptFiles(true);
                p.setEncryptionMethod(net.lingala.zip4j.model.enums.EncryptionMethod.AES);
                p.setAesKeyStrength(net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256);
                writer.addFile(html,p);
            }
            return java.nio.file.Files.readAllBytes(zip.toPath());
        }finally{html.delete();zip.delete();java.util.Arrays.fill(password,'\0');}
    }

    @Test public void encryptedTelegramZipWithFooterStillImports()throws Exception {
        accepted(encryptedArchive(),"synthetic-test-password".toCharArray());
    }

    @Test public void encryptedTelegramZipMissingFooterIsRejected()throws Exception {
        byte[] complete=encryptedArchive();
        assertTrue(complete.length>22);
        rejected(Arrays.copyOf(complete,complete.length-22),
            "synthetic-test-password".toCharArray());
    }

    @Test public void truncatedEndOfCentralDirectoryIsRejected()throws Exception {
        byte[] complete=archive(null);
        int end=complete.length-22;
        assertEquals(0x50,complete[end]&255);
        assertEquals(0x4b,complete[end+1]&255);
        assertEquals(0x05,complete[end+2]&255);
        assertEquals(0x06,complete[end+3]&255);
        rejected(Arrays.copyOf(complete,end));
    }

    @Test public void normalTelegramZipStillImports()throws Exception {
        accepted(archive(null));
    }

    @Test public void telegramZipWithFooterCommentStillImports()throws Exception {
        accepted(archive("Synthetic Telegram archive comment"));
    }

    @Test public void truncatedZipWithFooterCommentIsRejected()throws Exception {
        byte[] complete=archive("Synthetic Telegram archive comment");
        // Remove the entire EOCD and its comment but preserve valid local CRCs.
        int commentBytes="Synthetic Telegram archive comment".getBytes(StandardCharsets.UTF_8).length;
        rejected(Arrays.copyOf(complete,complete.length-22-commentBytes));
    }
}
