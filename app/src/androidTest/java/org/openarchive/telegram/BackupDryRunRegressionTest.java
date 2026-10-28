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

    private File fullBackupWithNotes(File metadata,long[] files,JSONArray listed,boolean includeHash)throws Exception{
        File full=File.createTempFile("dryrun-notes-", ".zip",ctx.getCacheDir());
        JSONArray chats=new JSONArray().put(new JSONObject().put("chatIndex",0).put("prefix","media/chat-0/"));
        JSONObject manifest=new JSONObject().put("format","telegram-archive-full").put("version",2)
            .put("chats",chats).put("files",0).put("bytes",0);
        MediaIntegrity hash=new MediaIntegrity();
        try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(full))){
            out.putNextEntry(new ZipEntry("metadata.zip"));
            try(InputStream in=new FileInputStream(metadata)){
                byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            }
            out.closeEntry();
            for(long id:files){
                String name="note_files/"+id;byte[] bytes=("synthetic note "+id).getBytes(StandardCharsets.UTF_8);
                out.putNextEntry(new ZipEntry(name));out.write(bytes);out.closeEntry();
                hash.begin(name);hash.update(bytes,0,bytes.length);hash.end();
            }
            if(listed!=null)manifest.put("noteFiles",listed);
            if(includeHash)manifest.put("noteSha256",hash.hex());
            out.putNextEntry(new ZipEntry("full-manifest.json"));
            out.write(manifest.toString().getBytes(StandardCharsets.UTF_8));out.closeEntry();
        }
        return full;
    }
    private void checkNoteInventory(long[] files,JSONArray listed,boolean includeHash,boolean valid)throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            File full=fullBackupWithNotes(f.zip,files,listed,includeHash);
            try{
                if(valid){
                    assertEquals(1,BackupVerifier.simulate(ctx,Uri.fromFile(full),null,ignored->{}).chats);
                    BackupVerifier.verify(ctx,Uri.fromFile(full),null,ignored->{});
                }else{
                    try{BackupVerifier.simulate(ctx,Uri.fromFile(full),null,ignored->{});fail("Invalid note inventory passed dry-run");}
                    catch(IOException expected){assertTrue(expected.getMessage().contains("вложени"));}
                    try{BackupVerifier.verify(ctx,Uri.fromFile(full),null,ignored->{});fail("Invalid note inventory passed verification");}
                    catch(IOException expected){assertTrue(expected.getMessage().contains("вложени"));}
                }
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{full.delete();}
        }
    }
    @Test public void matchingNoteInventoryPassesBothChecks()throws Exception{
        checkNoteInventory(new long[]{1,2},new JSONArray().put(1).put(2),true,true);
    }
    @Test public void legacyFullBackupWithoutNotesRemainsValid()throws Exception{
        checkNoteInventory(new long[]{},null,false,true);
    }
    @Test public void wrongNoteIdFailsBothChecks()throws Exception{
        checkNoteInventory(new long[]{1},new JSONArray().put(2),true,false);
    }
    @Test public void missingNoteFileFailsBothChecks()throws Exception{
        checkNoteInventory(new long[]{1},new JSONArray().put(1).put(2),true,false);
    }
    @Test public void duplicateNoteIdFailsBothChecks()throws Exception{
        checkNoteInventory(new long[]{1,2},new JSONArray().put(1).put(1),true,false);
    }
    @Test public void unlistedNoteFileFailsBothChecks()throws Exception{
        checkNoteInventory(new long[]{1},null,false,false);
    }
    @Test public void missingNoteDigestFailsBothChecks()throws Exception{
        checkNoteInventory(new long[]{1},new JSONArray().put(1),false,false);
    }

    @Test public void restoreRejectsDuplicateNoteIdsBeforeChangingExistingData()throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            long first=900000000000001L,second=900000000000002L;
            File full=fullBackupWithNotes(f.zip,new long[]{first,second},new JSONArray().put(first).put(first),true);
            try{
                try(InputStream in=new FileInputStream(full)){
                    FullBackup.restore(ctx,f.prefs,in,ignored->{});
                    fail("Duplicate note identifier must be rejected during restore");
                }catch(IOException expected){
                    assertTrue("Must reject duplicate IDs before examining notes database",expected.getMessage().contains("повторяется"));
                }
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{full.delete();}
        }
    }

    @Test public void realFullBackupRestoresNoteAttachmentAndKeepsOriginalData()throws Exception{
        try(Fixture f=new Fixture()){
            String marker="synthetic-note-"+UUID.randomUUID();
            File folder=new File(ctx.getCacheDir(),"note-source-"+f.key);
            assertTrue(folder.mkdirs());
            File attachment=new File(folder,"sample.bin");byte[] bytes="synthetic-note-attachment".getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=new FileOutputStream(attachment)){out.write(bytes);}
            JSONArray registry=new JSONArray().put(new JSONObject().put("key",f.key).put("name","Synthetic").put("tree","local:"+folder.getAbsolutePath()));
            assertTrue(f.prefs.edit().putString("archive_registry",registry.toString()).commit());
            SavedStore notes=new SavedStore(ctx);long originalId=notes.add(marker,"sample.bin",Uri.fromFile(attachment).toString(),"application/octet-stream");
            File full=File.createTempFile("full-note-roundtrip-", ".zip",ctx.getCacheDir());
            String restoredKey=null;File restoredStage=null;
            try{
                try(OutputStream out=new FileOutputStream(full)){FullBackup.export(ctx,f.prefs,out,ignored->{});}
                assertEquals(1,FullBackup.restore(ctx,f.prefs,Uri.fromFile(full),ignored->{}));
                JSONArray result=new JSONArray(f.prefs.getString("archive_registry","[]"));assertEquals(2,result.length());
                assertEquals(f.key,result.getJSONObject(0).getString("key"));
                JSONObject restored=result.getJSONObject(1);restoredKey=restored.getString("key");
                File mediaRoot=new File(restored.getString("tree").substring("local:".length()));
                restoredStage=mediaRoot.getParentFile().getParentFile();
                assertTrue(restoredStage.getName().matches("restore-[a-f0-9]{32}"));
                MessageDb copied=new MessageDb(ctx,restoredKey);
                try{assertEquals(1,copied.totalMessages());assertTrue(copied.isFavorite(f.id));}finally{copied.close();}
                assertEquals(1,f.db.totalMessages());assertTrue(f.db.isFavorite(f.id));
                boolean originalFound=false,restoredFound=false;
                for(SavedStore.Note note:notes.recent(200))if(marker.equals(note.body)){
                    if(note.id==originalId){originalFound=true;assertEquals(Uri.fromFile(attachment).toString(),note.fileId);}
                    else{
                        assertNotNull(note.fileId);assertTrue(note.fileId.startsWith(PortableNoteFiles.PREFIX));
                        try(InputStream in=ctx.getContentResolver().openInputStream(PortableNoteFiles.openUri(ctx,note.fileId))){
                            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[128];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                            assertArrayEquals(bytes,out.toByteArray());
                        }
                        restoredFound=true;
                    }
                }
                assertTrue(originalFound);assertTrue(restoredFound);
            }finally{
                for(SavedStore.Note note:notes.recent(200))if(marker.equals(note.body))notes.delete(note.id);
                notes.close();
                if(restoredKey!=null)ctx.deleteDatabase("telegram_"+restoredKey+".db");
                if(restoredStage!=null&&restoredStage.getName().matches("restore-[a-f0-9]{32}"))ArchiveFileImport.deleteTree(restoredStage);
                ArchiveFileImport.deleteTree(folder);full.delete();
            }
        }
    }

    private byte[] encryptedCopy(File source)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(OutputStream out=SecureArchive.encryptedOutput(bytes,"synthetic-password".toCharArray());
            InputStream in=new FileInputStream(source)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
        return bytes.toByteArray();
    }
    private void checkTruncatedEncryptedRestore(boolean full)throws Exception{
        try(Fixture f=new Fixture()){
            String registry=f.prefs.getString("archive_registry","");byte[] before=sha(f.zip);
            File outer=full?syntheticFullBackup(f.zip,1,null):null;
            try{
                byte[] encrypted=encryptedCopy(full?outer:f.zip);
                byte[] truncated=java.util.Arrays.copyOf(encrypted,encrypted.length-20);
                try(InputStream in=SecureArchive.decryptedInput(new ByteArrayInputStream(truncated),"synthetic-password".toCharArray())){
                    if(full)FullBackup.restore(ctx,f.prefs,in,ignored->{});
                    else ArchiveBackup.restore(ctx,f.prefs,in,ignored->{});
                    fail("Missing authenticated final record must reject restore");
                }catch(IOException expected){assertNotNull(expected.getMessage()==null?expected.getClass():expected.getMessage());}
                f.assertUnchanged(registry);assertArrayEquals(before,sha(f.zip));
            }finally{if(outer!=null)outer.delete();}
        }
    }
    @Test public void compactRestoreRejectsMissingEncryptedFinalRecord()throws Exception{checkTruncatedEncryptedRestore(false);}
    @Test public void fullRestoreRejectsMissingEncryptedFinalRecord()throws Exception{checkTruncatedEncryptedRestore(true);}
    @Test public void validEncryptedCompactBackupRestores()throws Exception{
        try(Fixture f=new Fixture()){
            String original=f.prefs.getString("archive_registry","");String restoredKey=null;
            try{
                byte[] encrypted=encryptedCopy(f.zip);
                try(InputStream in=SecureArchive.decryptedInput(new ByteArrayInputStream(encrypted),"synthetic-password".toCharArray())){
                    assertEquals(1,ArchiveBackup.restore(ctx,f.prefs,in,ignored->{}));
                }
                JSONArray chats=new JSONArray(f.prefs.getString("archive_registry","[]"));assertEquals(2,chats.length());
                assertEquals(f.key,chats.getJSONObject(0).getString("key"));restoredKey=chats.getJSONObject(1).getString("key");
                MessageDb restored=new MessageDb(ctx,restoredKey);
                try{assertEquals(1,restored.totalMessages());assertTrue(restored.isFavorite(f.id));}finally{restored.close();}
                assertEquals(1,f.db.totalMessages());assertTrue(f.db.isFavorite(f.id));
                assertEquals("Preserve original",f.db.page(0,1,"",false,"",false,false).get(0).body);
            }finally{if(restoredKey!=null)ctx.deleteDatabase("telegram_"+restoredKey+".db");f.prefs.edit().putString("archive_registry",original).commit();}
        }
    }

}
