package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.security.MessageDigest;
import java.util.UUID;
import static org.junit.Assert.*;

/** The backup source must never be written while checking FTS integrity. */
@RunWith(AndroidJUnit4.class)
public class BackupIntegrityAndroid15RegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private byte[] digest(File path)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(path)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}
        return hash.digest();
    }
    @Test public void ftsIntegrityCheckLeavesAuthenticatedSourceUnchanged()throws Exception{
        File dir=new File(ctx.getCacheDir(),"backup-check-"+UUID.randomUUID());assertTrue(dir.mkdirs());
        File source=new File(dir,"source.sqlite");
        try{
            SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(source,null);
            try{
                db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY, body TEXT)");
                db.execSQL("CREATE VIRTUAL TABLE messages_fts USING fts4(body)");
                db.execSQL("INSERT INTO messages(body) VALUES('Synthetic preserved message')");
                db.execSQL("INSERT INTO messages_fts(body) VALUES('Synthetic preserved message')");
            }finally{db.close();}
            byte[] before=digest(source);
            ArchiveBackup.verifyDb(source,"messages");
            assertArrayEquals("Authenticated backup bytes must not change",before,digest(source));
            String[] files=dir.list();assertNotNull(files);assertEquals("Temporary verification copy must be removed",1,files.length);
        }finally{SQLiteDatabase.deleteDatabase(source);ArchiveFileImport.deleteTree(dir);}
    }
    @Test public void corruptSnapshotIsRejectedWithoutChangingItsBytes()throws Exception{
        File dir=new File(ctx.getCacheDir(),"backup-bad-"+UUID.randomUUID());assertTrue(dir.mkdirs());
        File source=new File(dir,"bad.sqlite");
        try{
            try(FileOutputStream out=new FileOutputStream(source)){out.write("not a database".getBytes("UTF-8"));}
            byte[] before=digest(source);
            try{ArchiveBackup.verifyDb(source,"messages");fail("Corrupt SQLite must be refused");}
            catch(Exception expected){}
            assertArrayEquals(before,digest(source));
            String[] files=dir.list();assertNotNull(files);assertEquals(1,files.length);
        }finally{SQLiteDatabase.deleteDatabase(source);ArchiveFileImport.deleteTree(dir);}
    }
}
