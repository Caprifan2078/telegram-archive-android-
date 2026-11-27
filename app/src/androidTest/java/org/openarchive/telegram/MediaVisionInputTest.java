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
    @Test public void jpegExifRotationAndMirrorAreAppliedWithoutChangingOriginal()throws Exception{
        android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("vision-exif",".jpg",c.getCacheDir());
        Bitmap b=Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.drawColor(Color.RED);Paint paint=new Paint();paint.setColor(Color.BLUE);canvas.drawRect(160,0,320,160,paint);
        try{
            for(int orientation:new int[]{6,2,1}){
                try(FileOutputStream out=new FileOutputStream(file)){assertTrue(b.compress(Bitmap.CompressFormat.JPEG,100,out));}
                android.media.ExifInterface exif=new android.media.ExifInterface(file.getAbsolutePath());
                exif.setAttribute(android.media.ExifInterface.TAG_ORIENTATION,Integer.toString(orientation));exif.saveAttributes();
                byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
                MediaVisionInput.Sample sample=MediaVisionInput.read(c,Uri.fromFile(file),false,new AtomicBoolean());
                Bitmap decoded=BitmapFactory.decodeByteArray(sample.jpeg,0,sample.jpeg.length);
                assertNotNull(decoded);
                try{
                    if(orientation==6){
                        assertEquals(160,decoded.getWidth());assertEquals(320,decoded.getHeight());
                        assertTrue(Color.red(decoded.getPixel(80,40))>200);assertTrue(Color.blue(decoded.getPixel(80,280))>200);
                    }else{
                        assertEquals(320,decoded.getWidth());assertEquals(160,decoded.getHeight());
                        assertTrue((orientation==2?Color.blue(decoded.getPixel(40,80)):Color.red(decoded.getPixel(40,80)))>200);
                        assertTrue((orientation==2?Color.red(decoded.getPixel(280,80)):Color.blue(decoded.getPixel(280,80)))>200);
                    }
                }finally{decoded.recycle();}
                assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
            }
        }finally{b.recycle();file.delete();}
    }
}
