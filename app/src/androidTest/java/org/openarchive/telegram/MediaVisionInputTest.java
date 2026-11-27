package org.openarchive.telegram;
import android.graphics.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;
public class MediaVisionInputTest {
    @Test public void largeLocalPhotoBecomesBoundedJpegWithoutChangingOriginal()throws Exception{
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("vision-fixture",".png",c.getCacheDir());
        Bitmap b=Bitmap.createBitmap(1600,800,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);
        try{
            try(FileOutputStream out=new FileOutputStream(file)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}
            long original=file.length();
            MediaVisionInput.Sample sample=MediaVisionInput.read(c,Uri.fromFile(file),false,new AtomicBoolean());
            assertEquals(1,sample.frames);assertEquals(original,file.length());
            Bitmap scaled=BitmapFactory.decodeByteArray(sample.jpeg,0,sample.jpeg.length);
            assertNotNull(scaled);assertTrue(scaled.getWidth()<=768);assertTrue(scaled.getHeight()<=768);
            assertTrue(Color.red(scaled.getPixel(10,10))>200);scaled.recycle();
            try{MediaVisionInput.read(c,Uri.fromFile(file),false,new AtomicBoolean(true));fail("Cancellation ignored");}catch(InterruptedException expected){}
        }finally{b.recycle();file.delete();}
    }
}
