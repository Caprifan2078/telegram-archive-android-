package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class RelationshipScanIntegrationTest {
    @Test public void wholeArchiveMeasuresInitiativeAndActualReplyDirectionWithoutMutation(){
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="relations_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(c,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            long a=db.stage(sql,"Аня","2026-02-01 09:00","Как дела?",null,"");db.stageTelegramId(sql,a,101);
            long b=db.stage(sql,"Борис","2026-02-01 09:01","Спасибо, я рядом",null,"");db.stageTelegramId(sql,b,102);db.stageReply(sql,b,101);
            long d=db.stage(sql,"Борис","2026-02-01 17:00","Прости",null,"");db.stageTelegramId(sql,d,103);
            long e=db.stage(sql,"Аня","2026-02-01 17:02","Как тебе удобно",null,"");db.stageReply(sql,e,103);
            db.publishStaged(sql);
            AtomicBoolean cancel=new AtomicBoolean();
            RelationshipScan.Report report=RelationshipScan.read(c.getDatabasePath(DeepChatContext.dbName(key)),cancel,n->{});
            assertTrue(report.complete);assertEquals(4,report.total);assertEquals(2,report.sessions);
            assertEquals(1,report.people.get("Аня").starts);assertEquals(1,report.people.get("Борис").starts);
            assertEquals(1,report.people.get("Аня").questions);
            assertEquals(1,report.people.get("Аня").answeredStarts);
            assertEquals(1,report.people.get("Борис").answeredStarts);
            assertEquals(1,report.people.get("Аня").receivedReplies);
            assertEquals(1,report.people.get("Борис").receivedReplies);
            assertEquals(60,report.people.get("Борис").replySeconds);
            assertEquals(120,report.people.get("Аня").replySeconds);
            assertEquals(1,report.people.get("Борис").replyBuckets[0]);
            assertEquals(1,report.people.get("Аня").replyBuckets[1]);
            assertEquals(1,report.replies.get(java.util.Arrays.asList("Борис","Аня")).count);
            assertEquals(1,report.replies.get(java.util.Arrays.asList("Аня","Борис")).count);
            assertEquals(1,report.behavior.totals[3]);assertEquals(1,report.behavior.totals[4]);
            assertEquals(1,report.behavior.totals[5]);assertEquals(4,db.totalMessages());
            cancel.set(true);assertFalse(RelationshipScan.read(c.getDatabasePath(DeepChatContext.dbName(key)),cancel,n->{}).complete);
        }finally{db.close();c.deleteDatabase(DeepChatContext.dbName(key));}
    }
    @Test public void missingAndReversedTimeCannotInventSession(){
        RelationshipScan.Report r=new RelationshipScan.Report();
        r.add(1,"А","2026-02-02 09:00","",false,null);
        r.add(2,"Б","2026-02-01 09:00","",true,null);
        r.add(3,"Б","bad","",false,null);
        r.add(4,"А","2026-02-03 09:00","",false,null);
        assertEquals(1,r.sessions);assertEquals(1,r.unorderedTime);assertEquals(1,r.missingTime);assertEquals(1,r.unresolvedReplies);
    }
    @Test public void directedReplyTimingIgnoresSelfAndNegativeIntervals(){
        RelationshipScan.Report r=new RelationshipScan.Report();
        r.add(1,"А","2026-02-01 09:00","",true,"А","2026-02-01 08:00");
        r.add(2,"Б","2026-02-01 09:01","",true,"А","2026-02-01 10:00");
        r.add(3,"Б","2026-02-01 09:02","",true,"В","2026-02-01 09:01");
        assertEquals(1,r.selfReplies);assertEquals(1,r.invalidReplyTimes);
        assertEquals(1,r.people.get("В").receivedReplies);
        assertEquals(2,r.people.get("Б").explicitReplies);
        assertEquals(1,r.people.get("Б").timedReplies);
        assertEquals(60,r.people.get("Б").replySeconds);
        assertEquals(0,r.people.get("А").explicitReplies);
    }
}
