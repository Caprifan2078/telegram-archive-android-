package org.openarchive.telegram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CollectionsDatabaseTest {
    @Test public void importKeepsCollectionsAndNeverCopiesMedia()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        MessageDb db=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase sqlite=db.getWritableDatabase();
            ContentValues message=new ContentValues();message.put("id",20);message.put("body","hello");
            sqlite.insertOrThrow("messages",null,message);
            ContentValues file=new ContentValues();file.put("id",45);file.put("message_id",20);
            file.put("path","photos/a.jpg");file.put("kind","image");
            sqlite.insertOrThrow("attachments",null,file);
            MessageDb.Attachment attachment=new MessageDb.Attachment();
            attachment.id=45;attachment.messageId=20;attachment.path="photos/a.jpg";attachment.kind="image";
            long id=db.createCollection("Любимое");
            db.addToCollection(id,attachment);db.addToCollection(id,attachment);
            assertEquals(1,db.collections().get(0).count);
            assertEquals(1,db.collectionMedia(id,100).size());
            db.removeFromCollection(id,45);
            assertTrue(db.collectionMedia(id,100).isEmpty());
            assertEquals(0,db.collectionMedia(id,100).size());
            assertTrue(db.getReadableDatabase().getVersion()>=9);
        }finally{db.close();ctx.deleteDatabase("telegram_"+suffix+".db");}
    }
    @Test public void firstGenerationDatabaseMigratesWithoutMissingCollectionTable()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String dbName="telegram_"+suffix+".db";
        SQLiteDatabase old=ctx.openOrCreateDatabase(dbName,Context.MODE_PRIVATE,null);
        try{
            old.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT)");
            old.execSQL("INSERT INTO messages (id,author,body) VALUES (7,'Example','preserved')");
            old.setVersion(1);
        }finally{old.close();}
        MessageDb current=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase upgraded=current.getWritableDatabase();
            assertEquals(10,upgraded.getVersion());
            try(Cursor c=upgraded.rawQuery("SELECT body FROM messages WHERE id=7",null)){
                assertTrue(c.moveToFirst());assertEquals("preserved",c.getString(0));
            }
            assertTrue(current.collections().isEmpty());
        }finally{current.close();ctx.deleteDatabase(dbName);}
    }
    @Test public void deletingCollectionNeverRemovesActualMedia()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        MessageDb database=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase d=database.getWritableDatabase();
            d.execSQL("INSERT INTO messages(id,body) VALUES(9,'important')");
            d.execSQL("INSERT INTO attachments(id,message_id,path,kind) VALUES(18,9,'photos/keep.jpg','image')");
            MessageDb.Attachment file=new MessageDb.Attachment();
            file.id=18;file.path="photos/keep.jpg";file.messageId=9;
            long id=database.createCollection("temp");database.addToCollection(id,file);
            assertEquals(1,database.deleteCollection(id));
            assertEquals(0,database.deleteCollection(id));
            assertTrue(database.collections().isEmpty());
            try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM attachments WHERE id=18",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
            try(Cursor c=d.rawQuery("SELECT COUNT(*) FROM messages WHERE id=9",null)){
                assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));
            }
        }finally{database.close();ctx.deleteDatabase("telegram_"+suffix+".db");}
    }
    @Test public void openingVersionEightMigratesWithoutDeletingExistingMessages()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String dbName="telegram_"+suffix+".db";
        SQLiteDatabase database=ctx.openOrCreateDatabase(dbName,Context.MODE_PRIVATE,null);
        try{
            database.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY,body TEXT)");
            database.execSQL("INSERT INTO messages (id,body) VALUES (123,'keep')");
            database.setVersion(8);
        }finally{database.close();}
        MessageDb db=new MessageDb(ctx,suffix);
        try{
            SQLiteDatabase upgraded=db.getWritableDatabase();
            try(Cursor c=upgraded.rawQuery("SELECT body FROM messages WHERE id=123",null)){
                assertTrue(c.moveToFirst());assertEquals("keep",c.getString(0));
            }
            assertEquals(10,upgraded.getVersion());
            assertEquals(0,db.collections().size());
        }finally{db.close();ctx.deleteDatabase(dbName);}
    }
}
