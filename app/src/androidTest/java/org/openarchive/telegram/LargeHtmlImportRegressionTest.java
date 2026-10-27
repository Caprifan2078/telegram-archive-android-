package org.openarchive.telegram;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic real HTML parser/import test, distinct from database-only FTS benchmarks. */
@RunWith(AndroidJUnit4.class)
public class LargeHtmlImportRegressionTest {
    @Test public void importsOneHundredThousandHtmlMessagesAndFindsLast()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");int count=100000;
        File folder=new File(ctx.getCacheDir(),"synthetic-large-"+key);assertTrue(folder.mkdirs());
        File html=new File(folder,"messages.html");MessageDb db=new MessageDb(ctx,key);
        try{
            try(Writer out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(html),StandardCharsets.UTF_8))){
                out.write("<div class='history'>");
                for(int i=1;i<=count;i++){
                    out.write("<div class='message default' id='message"+i+"'><div class='body'>");
                    out.write("<div class='from_name'>Synthetic Alice</div>");
                    out.write("<div class='date details' title='2024-02-29 12:00:00'></div>");
                    out.write("<div class='text'>syntheticmessage"+i+"</div></div></div>");
                }
                out.write("</div>");
            }
            long started=SystemClock.elapsedRealtime();
            int imported=TelegramImporter.importFiles(ctx.getContentResolver(),
                new DocumentFolder(ctx.getContentResolver(),folder),db,ignored->{});
            long elapsed=SystemClock.elapsedRealtime()-started;
            assertEquals(count,imported);assertEquals(count,db.totalMessages());
            assertEquals("syntheticmessage1",db.page(0,1,"",false,"",false,false).get(0).body);
            assertEquals("syntheticmessage100000",db.page(count-1,1,"",false,"",false,false).get(0).body);
            assertEquals(1,db.count("syntheticmessage100000",false,"",false,false));
            assertEquals(0,db.count("syntheticmessage100001",false,"",false,false));
            assertEquals(count,db.analytics().totalMessages);
            Runtime runtime=Runtime.getRuntime();
            Log.i("TG_ARCHIVE_BENCH","fixture=synthetic_real_html_100000; bytes="+html.length()+
                "; import_ms="+elapsed+"; java_heap_used_bytes="+(runtime.totalMemory()-runtime.freeMemory()));
        }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(folder);}
    }
}
