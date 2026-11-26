package org.openarchive.telegram;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.List;
import java.util.UUID;

/** User-created history events must never disappear after a hard-coded row cap. */
@RunWith(AndroidJUnit4.class)
public final class RelationshipPaginationRegressionTest {
    @Test public void allSavedEventsRemainReachableBeyondTwoHundredFifty()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa_event_pages_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            db.clearStage(sql);
            long anchor=db.stage(sql,"QA Author","2026-01-01 12:00:00","Synthetic anchor",null,"");
            db.publishStaged(sql);
            for(int i=0;i<275;i++)db.addRelationshipEvent("Событие "+i,"Synthetic QA",anchor,0);
            List<MessageDb.RelationshipEvent> all=db.relationshipEvents();
            assertEquals("250-item cap silently hides saved history events",275,all.size());
            assertEquals("Событие 274",all.get(274).title);
            assertEquals("Count query must see all 275 real saved events",275,db.relationshipEventCount());
            List<MessageDb.RelationshipEvent> first=db.relationshipEventsPage(0,20);
            List<MessageDb.RelationshipEvent> middle=db.relationshipEventsPage(240,20);
            List<MessageDb.RelationshipEvent> last=db.relationshipEventsPage(260,20);
            assertEquals(20,first.size());
            assertEquals("Событие 0",first.get(0).title);
            assertEquals(20,middle.size());
            assertEquals("Событие 259",middle.get(19).title);
            assertEquals(15,last.size());
            assertEquals("Событие 274",last.get(14).title);
            assertTrue("Beyond end is empty",db.relationshipEventsPage(280,20).isEmpty());
            try{
                db.relationshipEventsPage(-1,20);
                fail("Negative pages must be rejected");
            }catch(IllegalArgumentException expected){}
            try{
                db.relationshipEventsPage(0,1000000);
                fail("Excessive page loads must be rejected");
            }catch(IllegalArgumentException expected){} 
        }finally{
            db.close();
            context.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void editAndUnlinkPreserveOriginalMessageAndOtherEvent()throws Exception{
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa_event_edit_"+UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(context,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();db.clearStage(sql);
            long anchor=db.stage(sql,"QA","2026-01-01 12:00:00","Original",null,"");
            db.stageAttachment(sql,anchor,"photos/synthetic.jpg","image");db.publishStaged(sql);
            long attachment=db.attachmentsForMessage(anchor).get(0).id;
            long first=db.addRelationshipEvent("Old","Old note",anchor,0);
            long second=db.addRelationshipEvent("Other","Other note",anchor,0);
            db.linkRelationshipMedia(first,attachment);db.linkRelationshipMedia(second,attachment);
            db.updateRelationshipEvent(first,"Edited","New note");
            MessageDb.RelationshipEvent event=db.relationshipEvents().get(0);
            assertEquals(first,event.id);assertEquals(anchor,event.anchorId);assertEquals("Edited",event.title);
            assertEquals("New note",event.note);assertEquals(1,event.linkedCount);
            db.unlinkRelationshipMedia(first,attachment);
            assertTrue(db.relationshipMedia(first).isEmpty());assertEquals(1,db.relationshipMedia(second).size());
            assertEquals(1,db.attachmentsForMessage(anchor).size());assertEquals(1,db.totalMessages());
            db.removeRelationshipEvent(first);assertEquals(second,db.relationshipEvents().get(0).id);
            assertEquals(1,db.attachmentsForMessage(anchor).size());
        }finally{db.close();context.deleteDatabase("telegram_"+key+".db");}
    }
}
