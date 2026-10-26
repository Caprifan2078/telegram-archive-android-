package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaUpgradeRegressionTest {
    @Test public void migrateNineRetainsMessagesAndFixesMediaCategories() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String filename="telegram_"+suffix+".db";
        SQLiteDatabase db=context.openOrCreateDatabase(filename,Context.MODE_PRIVATE,null);
        try {
            db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY,body TEXT)");
            db.execSQL("CREATE TABLE attachments(id INTEGER PRIMARY KEY,message_id INTEGER,path TEXT,kind TEXT)");
            db.execSQL("INSERT INTO messages(id,body) VALUES(1,'preserve')");
            db.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(1,1,'gifs/smile.gif','video')");
            db.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(2,1,'voice_messages/voice.ogg','audio')");
            db.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(3,1,'stickers/test.webp','image')");
            db.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(4,1,'video_files/test.mp4','video')");
            db.execSQL("CREATE TABLE favorites(message_id INTEGER PRIMARY KEY)");
            db.execSQL("INSERT INTO favorites(message_id) VALUES(1)");
            db.setVersion(9);
        } finally {db.close();}
        MessageDb migrated=new MessageDb(context,suffix);
        try{
            SQLiteDatabase active=migrated.getWritableDatabase();
            assertEquals(14,active.getVersion());
            try(Cursor c=active.rawQuery("SELECT kind FROM attachments ORDER BY id",null)){
                assertTrue(c.moveToFirst());assertEquals("gif",c.getString(0));
                assertTrue(c.moveToNext());assertEquals("voice",c.getString(0));
                assertTrue(c.moveToNext());assertEquals("sticker",c.getString(0));
                assertTrue(c.moveToNext());assertEquals("video",c.getString(0));
            }
            try(Cursor c=active.rawQuery("SELECT body FROM messages WHERE id=1",null)){
                assertTrue(c.moveToFirst());assertEquals("preserve",c.getString(0));
            }
            assertTrue(migrated.isFavorite(1));
        } finally {migrated.close();context.deleteDatabase(filename);}
    }
}
