package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BackupDryRunRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static byte[] sha(File file)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
        return md.digest();
    }
    private final class Fixture implements AutoCloseable{
        final String key=UUID.randomUUID().toString().replace("-","");
        final SharedPreferences prefs=ctx.getSharedPreferences("dryrun-"+key,Context.MODE_PRIVATE);
        final MessageDb db=new MessageDb(ctx,key);
        final File zip=File.createTempFile("dryrun-valid-", ".zip",ctx.getCacheDir());
        final long id;
        Fixture()throws Exception{
            SQLiteDatabase sql=db.getWritableDatabase();db.clearStage(sql);
            id=db.stage(sql,"Synthetic","2024-01-01 12:00","Preserve original",null,"");
            db.publishStaged(sql);db.toggleFavorite(id);
            JSONArray chats=new JSONArray();
            chats.put(new JSONObject().put("key",key).put("name","Synthetic").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString()).commit());
            try(OutputStream out=new FileOutputStream(zip)){ArchiveBackup.write(ctx,prefs,out,ignored->{});}
        }
        void assertUnchanged(String registry)throws Exception{
            assertEquals(registry,prefs.getString("archive_registry",""));
            assertEquals(1,db.totalMessages());assertTrue(db.isFavorite(id));
            assertEquals("Preserve original",db.page(0,1,"",false,"",false,false).get(0).body);
        }
        public void close(){db.close();ctx.deleteDatabase("telegram_"+key+".db");prefs.edit().clear().commit();zip.delete();}
    }
    private File replaceManifestDigest(File source)throws Exception{
        File changed=File.createTempFile("dryrun-tampered-", ".zip",ctx.getCacheDir());
        try(ZipInputStream in=new ZipInputStream(new FileInputStream(source));
            ZipOutputStream out=new ZipOutputStream(new FileOutputStream(changed))){
            ZipEntry e;byte[] buffer=new byte[8192];
            while((e=in.getNextEntry())!=null){
                ByteArrayOutputStream contents=new ByteArrayOutputStream();int n;
                while((n=in.read(buffer))!=-1)contents.write(buffer,0,n);
                byte[] bytes=contents.toByteArray();
                if(e.getName().equals("manifest.json")){
                    JSONObject manifest=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                    manifest.getJSONArray("chats").getJSONObject(0).put("sha256","0000000000000000000000000000000000000000000000000000000000000000");
                    bytes=manifest.toString().getBytes(StandardCharsets.UTF_8);
                }
                out.putNextEntry(new ZipEntry(e.getName()));out.write(bytes);out.closeEntry();
            }
        }return changed;
    }
    @Test public void validDryRunChecksFtsWithoutChangingData()throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            BackupVerifier.DryRun report=BackupVerifier.simulate(ctx,Uri.fromFile(f.zip),null,ignored->{});
            assertEquals(1,report.chats);assertEquals(1,report.messages);assertEquals(1,report.favorites);
            assertArrayEquals(before,sha(f.zip));f.assertUnchanged(registry);
        }
    }
    @Test public void dryRunRejectsAlteredManifestDigestWithoutChangingExistingData()throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            File changed=replaceManifestDigest(f.zip);
            try{
                try{BackupVerifier.simulate(ctx,Uri.fromFile(changed),null,ignored->{});fail("Wrong DB digest must not pass dry-run");}
                catch(IOException expected){}
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{changed.delete();}
        }
    }
}
