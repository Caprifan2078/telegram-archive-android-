package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

/** Synthetic crash-window coverage, not a substitute for killing an emulator process.
 * Uses a separate test SharedPreferences and random file names only. */
@RunWith(AndroidJUnit4.class)
public class RestoreRecoveryTest {
    static String randomId(){return UUID.randomUUID().toString().replace("-","");}
    @SuppressWarnings("unchecked")
    private static void simulateProcessRestart()throws Exception {
        // Test-only: clear the in-process worker guard, leaving disk files and
        // SharedPreferences untouched, as happens after an OS process kill.
        java.lang.reflect.Field f=RestoreJournal.class.getDeclaredField("ACTIVE");
        f.setAccessible(true);((Set<String>)f.get(null)).clear();
    }
    static File createFile(File path)throws IOException {
        File parent=path.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("mkdirs failed");
        try(FileOutputStream out=new FileOutputStream(path)){out.write(new byte[]{1,2,3,4});}
        return path;
    }
    private static int containsNote(SQLiteDatabase db,long id){
        try(Cursor c=db.rawQuery("SELECT count(*) FROM saved_notes WHERE id=?",new String[]{Long.toString(id)})){
            assertTrue(c.moveToFirst());return c.getInt(0);
        }
    }
    @Test public void crashedUnpublishedRestoreDeletesOnlyNewResourcesAndImportedNote()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=randomId(),key=randomId(),name="avatar_"+randomId()+".jpg";
        SharedPreferences prefs=ctx.getSharedPreferences("restore-recovery-test-"+id,Context.MODE_PRIVATE);
        File stage=new File(new File(ctx.getFilesDir(),"telegram_imports"),"restore-"+id);
        assertTrue(stage.mkdirs());
        RestoreJournal journal=RestoreJournal.begin(ctx,stage);
        SavedStore notes=new SavedStore(ctx);long original=-1,imported=-1;
        try{
            journal.recordKeys(Collections.singletonList(key));journal.recordAsset(name);
            createFile(ctx.getDatabasePath("telegram_"+key+".db"));
            createFile(new File(stage,"media/chat-0/photo.jpg"));
            createFile(new File(new File(ctx.getFilesDir(),"personal_images"),name));
            original=notes.add("unrelated original note",null,null,null);
            SQLiteDatabase db=notes.getWritableDatabase();
            db.beginTransaction();
            try{
                imported=notes.add("imported synthetic note",null,null,null);
                RestoreJournal.recordNote(db,journal.id,imported);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            assertEquals(1,containsNote(db,original));assertEquals(1,containsNote(db,imported));
            // Simulate process termination before registry commit by opening recovery anew.
            simulateProcessRestart();
            RestoreJournal.recoverAll(ctx,prefs);
            assertFalse(ctx.getDatabasePath("telegram_"+key+".db").exists());
            assertFalse(stage.exists());
            assertFalse(new File(new File(ctx.getFilesDir(),"personal_images"),name).exists());
            assertEquals(1,containsNote(db,original));assertEquals(0,containsNote(db,imported));
            assertEquals("[]",prefs.getString("archive_registry","[]"));
        }finally{
            if(original>0)notes.delete(original);
            notes.close();ctx.deleteDatabase("telegram_"+key+".db");
            prefs.edit().clear().commit();
            if(stage.exists())ArchiveFileImport.deleteTree(stage);
            File pic=new File(new File(ctx.getFilesDir(),"personal_images"),name);pic.delete();
            simulateProcessRestart();
            RestoreJournal.recoverAll(ctx,prefs);
        }
    }
    @Test public void crashAfterRegistryCommitPreservesPublishedDatabaseMediaAndNote()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=randomId(),key=randomId();
        SharedPreferences prefs=ctx.getSharedPreferences("restore-committed-test-"+id,Context.MODE_PRIVATE);
        File stage=new File(new File(ctx.getFilesDir(),"telegram_imports"),"restore-"+id);
        assertTrue(stage.mkdirs());
        RestoreJournal journal=RestoreJournal.begin(ctx,stage);
        SavedStore notes=new SavedStore(ctx);long imported=-1;
        try{
            journal.recordKeys(Collections.singletonList(key));
            File database=createFile(ctx.getDatabasePath("telegram_"+key+".db"));
            File media=createFile(new File(stage,"media/chat-0/photo.jpg"));
            SQLiteDatabase db=notes.getWritableDatabase();db.beginTransaction();
            try{
                imported=notes.add("synthetic restored note",null,null,null);
                RestoreJournal.recordNote(db,journal.id,imported);
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            JSONArray registry=new JSONArray();registry.put(new JSONObject().put("key",key).put("name","Recovered"));
            assertTrue(prefs.edit().putString("archive_registry",registry.toString()).commit());
            simulateProcessRestart();
            RestoreJournal.recoverAll(ctx,prefs);
            assertTrue(database.exists());assertTrue(media.exists());assertEquals(1,containsNote(db,imported));
            RestoreJournal.recoverAll(ctx,prefs); // repeated recovery is idempotent
        }finally{
            if(imported>0)notes.delete(imported);
            notes.close();prefs.edit().clear().commit();
            ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(stage);
        }
    }
    @Test public void activeWorkerCannotBeTreatedAsCrashed()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=ctx.getSharedPreferences("restore-active-test-"+randomId(),Context.MODE_PRIVATE);
        RestoreJournal journal=RestoreJournal.begin(ctx,null);
        try{
            try{RestoreJournal.recoverAll(ctx,prefs);fail("Cannot recover an active job");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("Восстановление"));}
        }finally{RestoreJournal.recover(ctx,prefs,journal);prefs.edit().clear().commit();}
    }
    @Test public void partialRegistryFailsClosedWithoutDeletingFiles()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=randomId(),a=randomId(),b=randomId();
        SharedPreferences prefs=ctx.getSharedPreferences("restore-ambiguous-test-"+id,Context.MODE_PRIVATE);
        RestoreJournal journal=RestoreJournal.begin(ctx,null);
        try{
            journal.recordKeys(Arrays.asList(a,b));
            File file=createFile(ctx.getDatabasePath("telegram_"+a+".db"));
            JSONArray registry=new JSONArray().put(new JSONObject().put("key",a));
            assertTrue(prefs.edit().putString("archive_registry",registry.toString()).commit());
            simulateProcessRestart();
            try{RestoreJournal.recoverAll(ctx,prefs);fail("A partial registry must stop recovery");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("Частично"));}
            assertTrue("Ambiguous case must preserve data",file.exists());
        }finally{
            prefs.edit().putString("archive_registry","[]").commit();
            simulateProcessRestart();RestoreJournal.recoverAll(ctx,prefs);
            ctx.deleteDatabase("telegram_"+a+".db");ctx.deleteDatabase("telegram_"+b+".db");
            prefs.edit().clear().commit();
        }
    }
}
