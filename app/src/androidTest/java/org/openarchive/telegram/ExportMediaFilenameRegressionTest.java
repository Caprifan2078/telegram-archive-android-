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
import static org.junit.Assert.*;

/** Real local export filenames, fully synthetic data; no user media needed. */
@RunWith(AndroidJUnit4.class)
public class ExportMediaFilenameRegressionTest {
    private static void file(File dir,String name)throws Exception {
        File target=new File(dir,name);
        try(FileOutputStream out=new FileOutputStream(target)){
            out.write("synthetic".getBytes(StandardCharsets.UTF_8));
        }
    }
    @Test public void percentEncodedHashAndQuestionMarkRemainPartOfFilenames()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root=new File(ctx.getCacheDir(),"encoded-media-"+UUID.randomUUID());
        File images=new File(root,"photos");
        assertTrue(images.mkdirs());
        try{
            file(images,"photo#summer.jpg");
            file(images,"photo?summer.jpg");
            file(images,"hello world.jpg");
            file(images,"literal%23.jpg");
            DocumentFolder folder=new DocumentFolder(ctx.getContentResolver(),root);
            check(folder,"photos/photo%23summer.jpg","photo#summer.jpg");
            check(folder,"photos/photo%3Fsummer.jpg","photo?summer.jpg");
            check(folder,"./photos/hello%20world.jpg","hello world.jpg");
            check(folder,"photos/literal%2523.jpg","literal%23.jpg");
            check(folder,"photos/photo%23summer.jpg?version=1#thumbnail","photo#summer.jpg");
            check(folder,"photos/photo%3Fsummer.jpg#fragment","photo?summer.jpg");
            assertNull("Literal fragment is not part of filename",folder.resolve("photos/photo#summer.jpg"));
            assertNull("Path traversal must be denied",folder.resolve("photos/%2E%2E/secret.jpg"));
            assertNull("External URL must be denied",folder.resolve("https://evil.test/file.jpg"));
        }finally{ArchiveFileImport.deleteTree(root);}
    }
    private static void check(DocumentFolder folder,String href,String basename) {
        Uri uri=folder.resolve(href);
        assertNotNull("Can't open an actual export file: "+href,uri);
        assertEquals(basename,new File(uri.getPath()).getName());
    }
}
