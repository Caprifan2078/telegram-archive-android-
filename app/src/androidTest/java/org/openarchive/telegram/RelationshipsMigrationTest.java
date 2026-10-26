package org.openarchive.telegram;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RelationshipsMigrationTest {
    @Test public void legacy11UpgradePreservesFavoritesAndMessages() {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="relationships_upgrade_qa";
        ctx.deleteDatabase("telegram_"+key+".db");
        MessageDb original=new MessageDb(ctx,key);
        try{
            SQLiteDatabase d=original.getWritableDatabase();
            d.execSQL("INSERT INTO messages(id,author,date_text,body,media,media_type) VALUES(111,'A','2023-01-01','Привет','','')");
            d.execSQL("INSERT INTO favorites(message_id) VALUES(111)");
            d.execSQL("DROP TABLE relationship_events");
            d.execSQL("DROP TABLE relationship_links");
            d.setVersion(11);
        }finally{original.close();}
        MessageDb migrated=new MessageDb(ctx,key);
        try{
            assertEquals(1,migrated.totalMessages());
            try(Cursor c=migrated.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM favorites WHERE message_id=111",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
            long id=migrated.addRelationshipEvent("Знакомство","Комментарий",111,0);
            assertTrue(id>0);assertEquals(1,migrated.relationshipEvents().size());
            migrated.removeRelationshipEvent(id);
            assertTrue(migrated.relationshipEvents().isEmpty());
            assertEquals(1,migrated.totalMessages());
        }finally{migrated.close();ctx.deleteDatabase("telegram_"+key+".db");}
    }
}
