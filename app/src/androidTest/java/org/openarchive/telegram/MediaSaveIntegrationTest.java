package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Synthetic picture only; always removes the test item from the user's media library. */
@RunWith(AndroidJUnit4.class)
public class MediaSaveIntegrationTest {
    @Test public void galleryImageCopyIsByteIdentical() throws Exception {
        if(Build.VERSION.SDK_INT<29)return; // Android 8–9 use user-selected SAF destination.
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source=new File(ctx.getCacheDir(),"ta-save-test-"+System.nanoTime()+".png");
        MediaSaver.Result saved=null;
        try {
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("photo.png");
                FileOutputStream out=new FileOutputStream(source)){
                byte[] block=new byte[8192];int n;
                while((n=in.read(block))!=-1)out.write(block,0,n);
            }
            byte[] expected=java.nio.file.Files.readAllBytes(source.toPath());
            MediaSavePolicy.Spec spec=MediaSavePolicy.plan(source.getName(),"image",false);
            saved=MediaSaver.saveToLibrary(ctx,Uri.fromFile(source),spec);
            assertEquals(expected.length,saved.bytes);
            ByteArrayOutputStream actual=new ByteArrayOutputStream();
            try(InputStream input=ctx.getContentResolver().openInputStream(saved.uri)){
                assertNotNull(input);byte[] block=new byte[8192];int n;
                while((n=input.read(block))!=-1)actual.write(block,0,n);
            }
            assertArrayEquals(expected,actual.toByteArray());
        }finally {
            if(saved!=null)ctx.getContentResolver().delete(saved.uri,null,null);
            source.delete();
        }
    }
    @Test public void missingSourceDoesNotKeepOrphanMedia() throws Exception {
        if(Build.VERSION.SDK_INT<29)return;
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        MediaSavePolicy.Spec spec=MediaSavePolicy.plan("missing.png","image",false);
        try {
            MediaSaver.saveToLibrary(ctx,Uri.fromFile(new File(ctx.getCacheDir(),"does-not-exist.png")),spec);
            fail("Missing media should fail");
        }catch(java.io.IOException expected){assertNotNull(expected.getMessage());}
    }
}
