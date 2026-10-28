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
        File file=new File(context.getCacheDir(),"synthetic-conversation.pdf");
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            for(int i=0;i<150;i++)db.stage(sql,"Тестовый автор","2026-01-01 12:34",
                "Синтетическое сообщение номер "+i+". Длинный текст для проверки переноса строк и страниц. "
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
        } finally {db.close();context.deleteDatabase("telegram_"+key+".db");file.delete();}
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
