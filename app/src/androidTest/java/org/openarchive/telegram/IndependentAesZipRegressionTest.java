package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Independently constructed WinZip AES-256 AE-2 ZIP fixture, NOT written by Zip4j.
 * Uses PBKDF2-HMAC-SHA1, AES-CTR and truncated HMAC-SHA1 exactly as 7-Zip's
 * ZIP AES-256 mode. Contains only synthetic messages.html, no user content.
 */
@RunWith(AndroidJUnit4.class)
public final class IndependentAesZipRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String ZIP_BASE64=
        "UEsDBDMAAQBjAAAAAAAAAAAAfAAAAIcAAAANAAsAbWVzc2FnZXMuaHRtbAGZBwACAEFFAwgAAAECAwQFBgcICQoLDA0OD/8I+xu/8tT8VbSagygM5g9hY7JEeU7goGUYMs6hQG5r7LcqL80yb4y/aOhl52YZZ7h7sABCeu3U/+J8nWt225eVLBEUA5eL0MfumVkj4oW2x2CAceUxM3iE6PUZDhtLQ8AxeOPg3LowsMeKKlBLAQIzADMAAQBjAAAAAAAAAAAAfAAAAIcAAAANAAsAAAAAAAAAAAAAAAAAAABtZXNzYWdlcy5odG1sAZkHAAIAQUUDCABQSwUGAAAAAAEAAQBGAAAAsgAAAAAA";
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private File root(String key){return new File(new File(ctx.getFilesDir(),"telegram_imports"),key);}
    @Test public void independentAes256ArchiveCanBeDetectedPromptedAndImported()throws Exception{
        File file=File.createTempFile("aes-zip-independent-", ".zip",ctx.getCacheDir());
        String attempted=key(),successful=key(),badKey=key();
        try{
            try(FileOutputStream output=new FileOutputStream(file)){
                output.write(Base64.decode(ZIP_BASE64,Base64.DEFAULT));
            }
            Uri source=Uri.fromFile(file);
            assertEquals("zip",ArchiveFileImport.detectFormat(ctx,source,"export.7z"));
            try{
                ArchiveFileImport.extract(ctx,source,"zip",attempted,null,p->{});
                fail("Encrypted AE-2 must request a password");
            }catch(ArchiveFileImport.PasswordRequired expected){
                assertTrue(expected.getMessage().contains("парол"));
            }
            assertFalse(root(attempted).exists());
            try{
                ArchiveFileImport.extract(ctx,source,"zip",badKey,"wrong".toCharArray(),p->{});
                fail("Wrong key must not import AE-2");
            }catch(Exception expected){
                assertFalse(root(badKey).exists());
            }
            File chat=ArchiveFileImport.extract(ctx,source,"zip",successful,
                "7zip-style-AES256".toCharArray(),p->{});
            File restored=new File(chat,"messages.html");
            assertTrue(restored.isFile());
            String recovered=new String(java.nio.file.Files.readAllBytes(restored.toPath()),
                StandardCharsets.UTF_8);
            assertTrue(recovered.contains("External AES-256 fixture imported"));
        }finally{
            ArchiveFileImport.deleteTree(root(attempted));
            ArchiveFileImport.deleteTree(root(successful));
            ArchiveFileImport.deleteTree(root(badKey));
            file.delete();
        }
    }
}
