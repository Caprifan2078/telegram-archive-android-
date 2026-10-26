package org.openarchive.telegram;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ConversationPeriodsMigrationTest {
    @Test public void oldV10UpgradeRetainsMessagesFavoritesAndPhaseRecords() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="chronology_upgrade_qa";
        ctx.deleteDatabase("telegram_"+key+".db");
        MessageDb helper=new MessageDb(ctx,key);
        try {
            SQLiteDatabase db=helper.getWritableDatabase();
            db.execSQL("INSERT INTO messages(id,author,date_text,body,media,media_type) VALUES (100,'A','2024-08-01 12:00','hello','','')");
            db.execSQL("INSERT INTO messages(id,author,date_text,body,media,media_type) VALUES (200,'B','2024-08-02 12:00','reply','','')");
            db.execSQL("INSERT INTO favorites(message_id) VALUES(100)");
            db.execSQL("DROP TABLE conversation_periods");
            db.setVersion(10);
        } finally {helper.close();}
        MessageDb reopened=new MessageDb(ctx,key);
        try {
            assertEquals(2,reopened.totalMessages());
            long id=reopened.addPeriod("Знакомство","Сама придумала",
                new ConversationPeriods.Boundary(100,2),new ConversationPeriods.Boundary(200,3));
            assertTrue(id>0);assertEquals(1,reopened.periods().size());
            assertEquals(2,reopened.periodStats(reopened.periods().get(0)).messages);
            try(android.database.Cursor c=reopened.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM favorites WHERE message_id=100",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
            assertTrue(reopened.removePeriod(id));assertEquals(0,reopened.periods().size());
            assertEquals(2,reopened.totalMessages());
        } finally{reopened.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
