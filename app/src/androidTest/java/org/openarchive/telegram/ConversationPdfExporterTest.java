package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic messages only; verifies that a multi-page PDF can actually be rendered. */
@RunWith(AndroidJUnit4.class)
public class ConversationPdfExporterTest {
    @Test public void exportsLongConversationIntoReadablePages() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="pdf_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        File file=new File(context.getExternalFilesDir(null),"synthetic-conversation.pdf");
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            for(int i=0;i<150;i++)db.stage(sql,"Тестовый автор","2026-01-01 12:34",
                "QA_PDF_MESSAGE_"+i+" Синтетическое сообщение номер "+i+". Длинный текст для проверки переноса строк и страниц. "
                +"Emoji 😀; unicode и кириллица.",null,"");
            db.publishStaged(sql);
            int pages;
            try(FileOutputStream out=new FileOutputStream(file)){
                pages=ConversationPdfExporter.write(db,"Синтетическая переписка",out);
            }
            assertTrue("Long conversation must span multiple pages",pages>1);
            assertTrue("PDF should contain data",file.length()>1000);
            // This fixture contains synthetic text only. Emit bounded chunks so
            // the host can independently inspect it after Gradle uninstalls the app.
            byte[] fixture=java.nio.file.Files.readAllBytes(file.toPath());
            String encoded=android.util.Base64.encodeToString(fixture,android.util.Base64.NO_WRAP);
            android.util.Log.i("TA_PDF_FIXTURE","BEGIN "+encoded.length());
            for(int offset=0;offset<encoded.length();offset+=2000){
                android.util.Log.i("TA_PDF_FIXTURE","PART "+offset+" "+encoded.substring(offset,Math.min(offset+2000,encoded.length())));
                android.os.SystemClock.sleep(40); // Avoid logd dropping bursts of synthetic test data.
            }
            android.util.Log.i("TA_PDF_FIXTURE","END");
            try(ParcelFileDescriptor descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(descriptor)){
                assertEquals(pages,renderer.getPageCount());
                for(int i:new int[]{0,pages-1})try(PdfRenderer.Page page=renderer.openPage(i)){
                    Bitmap bitmap=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);
                    try{page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        boolean ink=false;
                        for(int y=35;y<bitmap.getHeight()-35&&!ink;y+=3)
                            for(int x=35;x<bitmap.getWidth()-35;x+=3)
                                if(bitmap.getPixel(x,y)!=android.graphics.Color.WHITE){ink=true;break;}
                        assertTrue("Page is blank",ink);
                    }finally{bitmap.recycle();}
                }
            }
        } finally {db.close();context.deleteDatabase("telegram_"+key+".db");}
    }
    @Test public void exportsAllPhotoAttachmentsAndMissingMediaWithoutChangingSources()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="pdf_media_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        File folder=new File(context.getCacheDir(),key);assertTrue(folder.mkdirs());
        File image=new File(folder,"red.png"),image2=new File(folder,"green.png"),pdf=new File(folder,"visual.pdf");
        try{
            for(int i=0;i<2;i++){
                Bitmap b=Bitmap.createBitmap(320,220,Bitmap.Config.ARGB_8888);b.eraseColor(i==0?android.graphics.Color.RED:android.graphics.Color.GREEN);
                try(FileOutputStream out=new FileOutputStream(i==0?image:image2)){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}
            }
            long id=db.stage(db.getWritableDatabase(),"Анна","2026-01-01 12:34","Два фото",null,"");
            db.stageAttachment(db.getWritableDatabase(),id,"red.png","image");
            db.stageAttachment(db.getWritableDatabase(),id,"green.png","image");
            db.stageAttachment(db.getWritableDatabase(),id,"missing.jpg","image");
            db.publishStaged(db.getWritableDatabase());
            ConversationPdfExporter.Result result;
            try(FileOutputStream out=new FileOutputStream(pdf)){result=ConversationPdfExporter.write(db,"QA фото",out,context,new DocumentFolder(context.getContentResolver(),folder),true);}
            assertEquals(2,result.previews);assertEquals(1,result.missing);assertEquals(1,db.totalMessages());
            int red=0,green=0;
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
                for(int i=0;i<renderer.getPageCount();i++)try(PdfRenderer.Page page=renderer.openPage(i)){
                    Bitmap b=Bitmap.createBitmap(page.getWidth(),page.getHeight(),Bitmap.Config.ARGB_8888);
                    try{page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        for(int y=0;y<b.getHeight();y+=4)for(int x=0;x<b.getWidth();x+=4){
                            int px=b.getPixel(x,y);int r=android.graphics.Color.red(px),g=android.graphics.Color.green(px),bl=android.graphics.Color.blue(px);
                            if(r>220&&g<40&&bl<40)red++;if(g>220&&r<40&&bl<40)green++;
                        }
                    }finally{b.recycle();}
                }
            }
            assertTrue("Red photo must really render",red>100);assertTrue("Green photo must really render",green>100);
            assertTrue(image.length()>0&&image2.length()>0);
        }finally{db.close();context.deleteDatabase("telegram_"+key+".db");image.delete();image2.delete();pdf.delete();folder.delete();}
    }
    @Test public void videoAndRoundPreviewsActuallyEnterPdf()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context fixtures=InstrumentationRegistry.getInstrumentation().getContext();
        String key="pdf_video_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);File folder=new File(ctx.getCacheDir(),key);assertTrue(folder.mkdirs());
        File video=new File(folder,"regular-video.mp4"),round=new File(folder,"short-round.mp4"),pdf=new File(folder,"video.pdf");
        try{
            for(File file:new File[]{video,round})try(java.io.InputStream in=fixtures.getAssets().open(file.getName());FileOutputStream out=new FileOutputStream(file)){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            long id=db.stage(db.getWritableDatabase(),"QA","2026-01-01 12:34","Видео и кружок",null,"");
            db.stageAttachment(db.getWritableDatabase(),id,video.getName(),"video");
            db.stageAttachment(db.getWritableDatabase(),id,round.getName(),"round");
            db.publishStaged(db.getWritableDatabase());
            ConversationPdfExporter.Result result;
            try(FileOutputStream out=new FileOutputStream(pdf)){result=ConversationPdfExporter.write(db,"QA видео",out,ctx,new DocumentFolder(ctx.getContentResolver(),folder),true);}
            assertEquals("Video and round must both produce previews",2,result.previews);assertEquals(0,result.missing);
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){
                assertEquals(result.pages,renderer.getPageCount());
                for(int i=0;i<renderer.getPageCount();i++)try(PdfRenderer.Page p=renderer.openPage(i)){
                    Bitmap b=Bitmap.createBitmap(p.getWidth(),p.getHeight(),Bitmap.Config.ARGB_8888);
                    try{p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);}finally{b.recycle();}
                }
            }
            assertEquals(1,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");video.delete();round.delete();pdf.delete();folder.delete();}
    }
    @Test public void emptyConversationStillCreatesPdf() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="pdf_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        try(java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            assertEquals(1,ConversationPdfExporter.write(db,"Пустой чат",output));
            assertTrue(output.size()>100);
        }finally{db.close();context.deleteDatabase("telegram_"+key+".db");}
    }

    @Test public void exportsOverOneThousandPagesAndReopens() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="pdf_stress_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        File file=File.createTempFile("synthetic-large-",".pdf",context.getCacheDir());
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            StringBuilder body=new StringBuilder();
            for(int line=0;line<100;line++)body.append("Строка ").append(line).append(" — QA PDF.\n");
            sql.beginTransaction();
            try{
                for(int i=0;i<650;i++)db.stage(sql,"QA","2026-01-01","MESSAGE_"+i+"\n"+body,null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
            int pages;
            try(FileOutputStream out=new FileOutputStream(file)){pages=ConversationPdfExporter.write(db,"Стресс-тест",out);}
            assertTrue("Expected over 1000 pages, actual "+pages,pages>1000);
            assertTrue(pages<=ConversationPdfExporter.MAX_PAGES);
            try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(fd)){
                assertEquals(pages,renderer.getPageCount());
                for(int i:new int[]{0,pages/2,pages-1})try(PdfRenderer.Page page=renderer.openPage(i)){
                    Bitmap image=Bitmap.createBitmap(298,421,Bitmap.Config.ARGB_8888);
                    try{page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        boolean ink=false;
                        for(int y=20;y<400&&!ink;y+=2)for(int x=20;x<278;x+=2)
                            if(image.getPixel(x,y)!=android.graphics.Color.WHITE){ink=true;break;}
                        assertTrue("Blank stress page "+i,ink);
                    }finally{image.recycle();}
                }
            }
            assertEquals(650,db.totalMessages());
        }finally{db.close();context.deleteDatabase("telegram_"+key+".db");file.delete();}
    }
}
