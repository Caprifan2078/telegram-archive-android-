package org.openarchive.telegram;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.UUID;
import static org.junit.Assert.*;

/** Uses a separate SQLite file under app cache; does not touch normal saved notes. */
@RunWith(AndroidJUnit4.class)
public class QuoteSavedStoreIntegrationTest {
    @Test public void preservesVersionOneNotesAndLinkedQuote() throws Exception {
        Context real=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File scratch=new File(real.getCacheDir(),"quote_instrumented_"+UUID.randomUUID());
        assertTrue(scratch.mkdirs());
        File database=new File(scratch,"global_saved_notes.db");
        SQLiteDatabase old=SQLiteDatabase.openOrCreateDatabase(database,null);
        try {
            old.execSQL("CREATE TABLE saved_notes (id INTEGER PRIMARY KEY AUTOINCREMENT,created INTEGER NOT NULL,body TEXT NOT NULL,file_name TEXT,file_id TEXT,mime TEXT)");
            old.execSQL("INSERT INTO saved_notes(created,body) VALUES(1,'old important note')");
            old.setVersion(1);
        } finally {old.close();}
        Context wrapped=new ContextWrapper(real){
            @Override public File getDatabasePath(String ignored){return database;}
            @Override public SQLiteDatabase openOrCreateDatabase(String ignored,int mode,SQLiteDatabase.CursorFactory factory){
                return SQLiteDatabase.openOrCreateDatabase(database,factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String ignored,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler){
                return SQLiteDatabase.openOrCreateDatabase(database.getAbsolutePath(),factory,handler);
            }
        };
        SavedStore store=new SavedStore(wrapped);
        try {
            assertEquals(2,store.getWritableDatabase().getVersion());
            assertEquals(1,store.recent(20).size());
            assertEquals("old important note",store.recent(20).get(0).body);
            long id=store.addQuote("│ hello\n— Person · Date · Archive","archive1",55);
            assertTrue(id>0);
            java.util.List<SavedStore.Note> notes=store.recent(20);
            assertEquals(2,notes.size());
            assertEquals("archive1",notes.get(0).originArchive);
            assertEquals(55L,notes.get(0).originMessageId);
            assertEquals("old important note",notes.get(1).body);
            assertNull(notes.get(1).originArchive);
        }finally{store.close();database.delete();scratch.delete();}
    }
}
