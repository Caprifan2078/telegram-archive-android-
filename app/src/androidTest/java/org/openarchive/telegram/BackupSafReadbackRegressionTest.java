package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Provider can acknowledge writing but truncate or corrupt bytes before the next read. */
@RunWith(AndroidJUnit4.class)
public final class BackupSafReadbackRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final class Fixture implements AutoCloseable {
        final String key=UUID.randomUUID().toString().replace("-","");
        final SharedPreferences prefs=ctx.getSharedPreferences("readback-"+key,Context.MODE_PRIVATE);
        final MessageDb db=new MessageDb(ctx,key);
        final File media=new File(ctx.getCacheDir(),"readback-"+key);
        Fixture()throws Exception {
            assertTrue(media.mkdir());
            SQLiteDatabase sql=db.getWritableDatabase();
            db.clearStage(sql);
            long id=db.stage(sql,"Synthetic","2024-03-01","Preserve this message",null,"");
            db.publishStaged(sql);
            db.toggleFavorite(id);
            assertTrue(prefs.edit().putString("archive_registry",new JSONArray()
                .put(new JSONObject().put("key",key).put("name","Synthetic")
                    .put("tree","local:"+media.getAbsolutePath())).toString()).commit());
        }
        @Override public void close() {
            db.close();ctx.deleteDatabase("telegram_"+key+".db");
            prefs.edit().clear().commit();ArchiveFileImport.deleteTree(media);
        }
    }

    private static final class SimulatedSaf implements BackupExportGate.Target {
        final ByteArrayOutputStream raw=new ByteArrayOutputStream();
        final boolean broken,unreadable;
        int readAttempts;
        SimulatedSaf(boolean damaged,boolean deniesRead) {
            broken=damaged;unreadable=deniesRead;
        }
        @Override public OutputStream openWrite(){return raw;}
        @Override public InputStream openRead()throws IOException {
            readAttempts++;
            if(unreadable)throw new IOException("SAF source not readable");
            byte[] bytes=raw.toByteArray();
            if(broken)bytes=Arrays.copyOf(bytes,Math.max(0,bytes.length-22));
            return new ByteArrayInputStream(bytes);
        }
    }

    private void exercise(boolean full,boolean encrypted,boolean corrupt,boolean unreadable)
            throws Exception {
        try(Fixture f=new Fixture()) {
            final String before=f.prefs.getString("archive_registry","");
            final char[] password=encrypted?"synthetic-password".toCharArray():null;
            SimulatedSaf saf=new SimulatedSaf(corrupt,unreadable);
            try {
                BackupVerifier.Result result=BackupExportGate.saveAndVerify(
                    ctx,f.prefs,saf,full,password,ignored->{});
                if(corrupt||unreadable)fail("Export announced success without reading complete destination");
                assertNotNull(result);
                assertTrue("Verified archive must contain entries",result.files>0);
                assertEquals(full,result.full);
                assertEquals("Completed export must be read back",1,saf.readAttempts);
            } catch(IOException problem) {
                if(!corrupt&&!unreadable)throw problem;
                assertTrue("A partial/unreadable backup must be refused",problem.getMessage()!=null);
            }
            assertEquals(before,f.prefs.getString("archive_registry",""));
            assertEquals(1,f.db.totalMessages());
            assertEquals(1,f.db.count("",false,"",false,true));
        }
    }

    @Test public void fullZipCannotBeMarkedSavedIfProviderTruncatesIt()throws Exception {
        exercise(true,false,true,false);
    }
    @Test public void compactZipCannotBeMarkedSavedIfProviderTruncatesIt()throws Exception {
        exercise(false,false,true,false);
    }
    @Test public void encryptedFullCannotBeMarkedSavedIfProviderTruncatesIt()throws Exception {
        exercise(true,true,true,false);
    }
    @Test public void providerReadDenialCannotBeMarkedVerified()throws Exception {
        exercise(false,false,false,true);
    }
    @Test public void completeCompactZipPassesReadback()throws Exception {
        exercise(false,false,false,false);
    }
    @Test public void completeFullZipPassesReadback()throws Exception {
        exercise(true,false,false,false);
    }
    @Test public void completeEncryptedFullPassesReadback()throws Exception {
        exercise(true,true,false,false);
    }
}
