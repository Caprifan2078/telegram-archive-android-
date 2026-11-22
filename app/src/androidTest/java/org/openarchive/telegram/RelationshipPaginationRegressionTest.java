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
        }finally{
            db.close();
            context.deleteDatabase("telegram_"+key+".db");
        }
    }
}
