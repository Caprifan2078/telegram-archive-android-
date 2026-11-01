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
            // Preserve only this synthetic fixture outside the app sandbox before
            // Gradle uninstalls the test application at the end of the run.
            byte[] fixture=java.nio.file.Files.readAllBytes(file.toPath());
            String encoded=android.util.Base64.encodeToString(fixture,android.util.Base64.NO_WRAP);
            try(ParcelFileDescriptor copied=InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("echo '"+encoded+"' | base64 -d > /sdcard/Download/ta_synthetic_pdf_test.pdf && echo PDF_COPY_OK");
                java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(copied)) {
                java.io.ByteArrayOutputStream reply=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[256];int read;
                while((read=in.read(buffer))!=-1)reply.write(buffer,0,read);
                assertTrue("Could not copy synthetic PDF for independent text verification",
                    reply.toString("UTF-8").contains("PDF_COPY_OK"));
            }
        } finally {db.close();context.deleteDatabase("telegram_"+key+".db");}
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
}
