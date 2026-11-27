package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual SQLite archive and exact character coverage; deterministic generator
 * isolates the scanner from inference quality. Real weights tested separately. */
public class FullChatModelScanIntegrationTest {
    @Test public void allTextAndAllLogicalBlocksAreReadWithoutChangingDatabase()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa_full_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long first=db.stage(sql,"А","2026-01-01",String.join("",Collections.nCopies(10000,"я")),null,"");
            long second=db.stage(sql,"Б","2026-01-02",String.join("",Collections.nCopies(10000,"ю")),null,"");
            long third=db.stage(sql,"А","2026-01-03","😀конец",null,"");
            db.publishStaged(sql);
            List<Long> completed=new ArrayList<>();
            FullChatModelScan.Report report=FullChatModelScan.read(ctx.getDatabasePath(DeepChatContext.dbName(key)),"Что произошло?",true,new AtomicBoolean(),p->{
                assertTrue(p.length()<=1300);
                StringBuilder note=new StringBuilder();for(Long id:FullChatModelScan.ids(p))note.append("[#").append(id).append("] ");
                return note.toString();
            },new FullChatModelScan.Progress(){
                public void update(long rows,long total,long blocks){}
                public void block(long index,String note){completed.add(index);}
            });
            assertTrue(report.complete);assertEquals(20006,report.totalChars);assertEquals(report.totalChars,report.chars);
            assertEquals(3,report.rows);assertEquals(3,report.blocks);
            assertEquals(Arrays.asList(1L,2L,3L),completed);
            assertEquals(new LinkedHashSet<>(Arrays.asList(first,second,third)),FullChatModelScan.ids(report.notes));
            assertEquals(3,report.sources.size());assertEquals(3,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void embeddedNulAndEmojiDoNotTruncateFullScanOrCharacterCount()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa_unicode_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            String body="до"+(char)0+"после😀";
            long id=db.stage(sql,"А","2026-01-01",body,null,"");
            db.publishStaged(sql);
            List<String> prompts=new ArrayList<>();
            FullChatModelScan.Report report=FullChatModelScan.read(ctx.getDatabasePath(DeepChatContext.dbName(key)),"Что после?",true,new AtomicBoolean(),p->{
                prompts.add(p);return "[#"+id+"]";
            },(a,b,c)->{});
            assertEquals(body.codePointCount(0,body.length()),report.totalChars);
            assertEquals(report.totalChars,report.chars);
            assertTrue(report.complete);
            assertTrue(prompts.stream().anyMatch(p->p.contains("после😀")));
            assertFalse(prompts.stream().anyMatch(p->p.indexOf(0)>=0));
            assertEquals(1,db.totalMessages());
        }finally{db.close();ctx.deleteDatabase(DeepChatContext.dbName(key));}
    }
}
