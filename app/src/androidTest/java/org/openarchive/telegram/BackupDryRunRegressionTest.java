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
                catch(IOException expected){assertTrue("Must identify a manifest/hash mismatch",expected.getMessage().contains("Метаданные ZIP"));}
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{changed.delete();}
        }
    }

    @Test public void dryRunRejectsUnexpectedFileWithoutChangingData()throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");
            byte[] before=sha(f.zip);
            File changed=File.createTempFile("dryrun-unexpected-", ".zip",ctx.getCacheDir());
            try{
                try(ZipInputStream in=new ZipInputStream(new FileInputStream(f.zip));
                    ZipOutputStream out=new ZipOutputStream(new FileOutputStream(changed))){
                    ZipEntry e; byte[] buffer=new byte[8192]; int n;
                    while((e=in.getNextEntry())!=null){
                        out.putNextEntry(new ZipEntry(e.getName()));
                        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                        out.closeEntry();
                    }
                    out.putNextEntry(new ZipEntry("unexpected.txt"));
                    out.write("synthetic".getBytes(StandardCharsets.UTF_8));out.closeEntry();
                }
                try{
                    BackupVerifier.simulate(ctx,Uri.fromFile(changed),null,ignored->{});
                    fail("Unexpected file must not pass dry-run");
                }catch(IOException expected){
                    assertTrue(expected.getMessage().contains("резервная копия"));
                }
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{changed.delete();}
        }
    }

    private File syntheticFullBackup(File metadata, int roots, String mediaPath)throws Exception{
        File full=File.createTempFile("dryrun-full-", ".zip",ctx.getCacheDir());
        JSONArray chats=new JSONArray();
        for(int i=0;i<roots;i++)chats.put(new JSONObject().put("chatIndex",i).put("prefix","media/chat-"+i+"/"));
        byte[] media="synthetic media".getBytes(StandardCharsets.UTF_8);
        JSONObject manifest=new JSONObject().put("format","telegram-archive-full").put("version",2)
            .put("chats",chats).put("files",mediaPath==null?0:1).put("bytes",mediaPath==null?0:media.length);
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(full))){
            out.putNextEntry(new ZipEntry("metadata.zip"));
            try(InputStream in=new FileInputStream(metadata)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            out.closeEntry();
            if(mediaPath!=null){out.putNextEntry(new ZipEntry(mediaPath));out.write(media);out.closeEntry();}
            out.putNextEntry(new ZipEntry("full-manifest.json"));
            out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        return full;
    }
    private void checkFullBackup(int roots,String mediaPath,boolean shouldPass)throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            File full=syntheticFullBackup(f.zip,roots,mediaPath);
            try{
                if(shouldPass){
                    BackupVerifier.DryRun result=BackupVerifier.simulate(ctx,Uri.fromFile(full),null,ignored->{});
                    assertEquals(1,result.chats);assertEquals(mediaPath==null?0:1,result.mediaFiles);
                }else{
                    try{BackupVerifier.simulate(ctx,Uri.fromFile(full),null,ignored->{});fail("Invalid full backup passed dry-run");}
                    catch(IOException expected){assertNotNull(expected.getMessage());}
                }
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{full.delete();}
        }
    }
    @Test public void fullBackupWithMatchingChatAndMediaPassesDryRun()throws Exception{
        checkFullBackup(1,"media/chat-0/example.bin",true);
    }
    @Test public void fullBackupWithMediaForMissingChatFailsDryRun()throws Exception{
        checkFullBackup(1,"media/chat-1/example.bin",false);
    }
    @Test public void fullBackupWithNoncanonicalMediaIndexFailsDryRun()throws Exception{
        checkFullBackup(1,"media/chat-00/example.bin",false);
    }
    @Test public void fullBackupWithMismatchedChatCountFailsDryRun()throws Exception{
        checkFullBackup(2,null,false);
    }

}
