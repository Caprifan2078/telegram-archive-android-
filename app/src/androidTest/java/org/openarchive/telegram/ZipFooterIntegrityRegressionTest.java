package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

/** A ZIP missing its central-directory end marker is incomplete even if all local entries have valid CRCs. */
@RunWith(AndroidJUnit4.class)
public final class ZipFooterIntegrityRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final class Fixture implements AutoCloseable {
        final String key=UUID.randomUUID().toString().replace("-","");
        final SharedPreferences prefs=ctx.getSharedPreferences("footer-"+key,Context.MODE_PRIVATE);
        final File media=new File(ctx.getCacheDir(),"footer-media-"+key);
        final MessageDb db=new MessageDb(ctx,key);
        Fixture()throws Exception {
            assertTrue(media.mkdir());
            SQLiteDatabase sqlite=db.getWritableDatabase();
            db.clearStage(sqlite);
            db.stage(sqlite,"Synthetic","2024-01-01","Keep this chat",null,"");
            db.publishStaged(sqlite);
            JSONArray chats=new JSONArray().put(new JSONObject()
                .put("key",key).put("name","Synthetic")
                .put("tree","local:"+media.getAbsolutePath()));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString()).commit());
        }
        byte[] backup(boolean full)throws Exception {
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            if(full) FullBackup.export(ctx,prefs,output,ignored->{});
            else ArchiveBackup.write(ctx,prefs,output,ignored->{});
            return output.toByteArray();
        }
        @Override public void close(){
            db.close();
            ctx.deleteDatabase("telegram_"+key+".db");
            prefs.edit().clear().commit();
            ArchiveFileImport.deleteTree(media);
        }
    }

    private void rejectMissingEndRecord(boolean full)throws Exception {
        try(Fixture f=new Fixture()) {
            byte[] complete=f.backup(full);
            // The JVM ZipOutputStream emitted the standard 22-byte EOCD with no comment.
            assertTrue(complete.length>22);
            int end=complete.length-22;
            assertEquals(0x50,complete[end]&255);
            assertEquals(0x4b,complete[end+1]&255);
            assertEquals(0x05,complete[end+2]&255);
            assertEquals(0x06,complete[end+3]&255);
            BackupVerifier.Result verified=BackupVerifier.verifyStream(
                new ByteArrayInputStream(complete),ignored->{});
            assertEquals(full,verified.full);
            byte[] truncated=Arrays.copyOf(complete,end);
            try {
                BackupVerifier.verifyStream(new ByteArrayInputStream(truncated),ignored->{});
                fail("Fast verification accepted backup missing the ZIP end-of-central-directory record");
            } catch(IOException expected) {
                assertNotNull(expected.getMessage());
            }
            assertEquals(1,f.db.totalMessages());
        }
    }

    @Test public void compactBackupWithoutZipEndRecordMustFailVerification()throws Exception {
        rejectMissingEndRecord(false);
    }

    @Test public void fullBackupWithoutZipEndRecordMustFailVerification()throws Exception {
        rejectMissingEndRecord(true);
    }

    private void rejectIncompleteDirectRestore(boolean full)throws Exception {
        try(Fixture f=new Fixture()) {
            String original=f.prefs.getString("archive_registry","");
            byte[] complete=f.backup(full);
            byte[] truncated=Arrays.copyOf(complete,complete.length-22);
            try {
                if(full) FullBackup.restore(ctx,f.prefs,
                    new ByteArrayInputStream(truncated),ignored->{});
                else ArchiveBackup.restore(ctx,f.prefs,
                    new ByteArrayInputStream(truncated),ignored->{});
                fail("A truncated ZIP must never publish restored chats");
            }catch(IOException expected) {
                assertNotNull(expected.getMessage());
                assertTrue(expected.getMessage(),expected.getMessage().contains("ZIP"));
            }
            assertEquals("Existing registry must not change on error",
                original,f.prefs.getString("archive_registry",""));
            assertEquals(1,f.db.totalMessages());
        }
    }

    @Test public void compactRestoreRejectsTruncatedZipBeforeRegistryCommit()throws Exception {
        rejectIncompleteDirectRestore(false);
    }
    @Test public void fullRestoreRejectsTruncatedZipBeforeRegistryCommit()throws Exception {
        rejectIncompleteDirectRestore(true);
    }

    private void rejectIncompleteDryRun(boolean full)throws Exception {
        try(Fixture f=new Fixture()) {
            String original=f.prefs.getString("archive_registry","");
            byte[] complete=f.backup(full);
            File broken=new File(ctx.getCacheDir(),"footer-broken-"+f.key+".zip");
            try {
                try(FileOutputStream output=new FileOutputStream(broken)) {
                    output.write(complete,0,complete.length-22);
                }
                try {
                    BackupVerifier.simulate(ctx,android.net.Uri.fromFile(broken),null,ignored->{});
                    fail("Read-only dry-run must reject truncated ZIP");
                }catch(IOException expected) {
                    assertNotNull(expected.getMessage());
                    assertTrue(expected.getMessage(),expected.getMessage().contains("ZIP"));
                }
                assertEquals(original,f.prefs.getString("archive_registry",""));
                assertEquals(1,f.db.totalMessages());
            }finally{broken.delete();}
        }
    }
    @Test public void compactDryRunRejectsTruncatedZip()throws Exception {
        rejectIncompleteDryRun(false);
    }
    @Test public void fullDryRunRejectsTruncatedZip()throws Exception {
        rejectIncompleteDryRun(true);
    }

}
