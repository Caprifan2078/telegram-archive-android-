package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.io.*;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Tiny synthetic Telegram export exercises the real parser, SQLite and portable media ZIP.
 * Generated data only: no personal chat content is used in CI. */
@RunWith(AndroidJUnit4.class)
public class ArchiveIntegrationTest {
    @Test public void parseGifVoiceRoundAndMeasureSearch() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        File folder=new File(ctx.getCacheDir(),"synthetic-"+id);
        assertTrue(folder.mkdirs());
        File html=new File(folder,"messages.html");
        StringBuilder source=new StringBuilder("<html><body><div class='history'>");
        final int MESSAGES=480;
        for(int i=0;i<MESSAGES;i++){
            String media=(i%40==0)?"<a href='animations/animation"+i+".mp4'>GIF</a>":
                (i%40==1)?"<a href='gifs/sticker"+i+".gif'>GIF</a>":
                (i%40==2)?"<a href='round_video_messages/video"+i+".mp4'>Round</a>":
                (i%40==3)?"<a href='voice_messages/voice"+i+".ogg'>Voice</a>":
                (i%40==4)?"<a href='photos/photo"+i+".jpg'>Photo</a>":"";
            source.append("<div class='message default'><div class='body'><div class='from_name'>Тест</div>")
              .append("<div class='date details' title='2026-10-23 12:00'>12:00</div>")
              .append("<div class='text'>слово проверка номер ").append(i).append("</div>")
              .append(media).append("</div></div>");
        }
        source.append("</div></body></html>");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){w.write(source.toString());}
        MessageDb db=new MessageDb(ctx,id);
        try{
            long begin=SystemClock.elapsedRealtimeNanos();
            int imported=TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),db,msg->{});
            long importMs=(SystemClock.elapsedRealtimeNanos()-begin)/1000000L;
            assertEquals(MESSAGES,imported);
            assertEquals(MESSAGES,db.totalMessages());
            assertEquals(24,db.mediaCount("gif"));
            assertEquals(12,db.mediaCount("round"));
            assertEquals(12,db.mediaCount("voice"));
            long start=SystemClock.elapsedRealtimeNanos();
            for(int i=0;i<40;i++)assertEquals(40,db.page(i*10,40,"",false,"",false,false).size());
            long pageMs=(SystemClock.elapsedRealtimeNanos()-start)/1000000L;
            start=SystemClock.elapsedRealtimeNanos();
            for(int i=0;i<15;i++)assertEquals(MESSAGES,db.count("проверк",false,"",false,false));
            long searchMs=(SystemClock.elapsedRealtimeNanos()-start)/1000000L;
            Log.i("TG_ARCHIVE_BENCH","synthetic_messages="+MESSAGES+" import_ms="+importMs+
                " page40_avg_ms="+(pageMs/40.0)+" fts_search_avg_ms="+(searchMs/15.0));
            assertEquals("gif",TelegramImporter.classify("animations/a.mp4",Jsoup.parse("<div class='message'><a href='animations/a.mp4'>gif</a></div>").selectFirst("a")));
            assertEquals("video",TelegramImporter.classify("video_files/a.mp4",Jsoup.parse("<a></a>").selectFirst("a")));
        }finally{
            db.close();ctx.deleteDatabase("telegram_"+id+".db");ArchiveFileImport.deleteTree(folder);
        }
    }

    @Test public void fullBackupRoundtripPreservesMediaPinsFavorites() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        String prefsName="tg-integration-"+key;
        SharedPreferences prefs=ctx.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
        File folder=new File(ctx.getCacheDir(),"full-src-"+key);assertTrue(folder.mkdirs());
        File mediaDir=new File(folder,"photos");assertTrue(mediaDir.mkdirs());
        File photo=new File(mediaDir,"sample.jpg");
        byte[] sample="synthetic-photo-content".getBytes("UTF-8");
        try(FileOutputStream out=new FileOutputStream(photo)){out.write(sample);}
        File html=new File(folder,"messages.html");
        try(Writer out=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){
            out.write("<div class='history'><div class='message default'><div class='body'>"+
                "<div class='from_name'>Тест</div><div class='text'>Сохранить</div>"+
                "<a href='photos/sample.jpg'>Photo</a></div></div></div>");
        }
        android.graphics.Bitmap generated=android.graphics.Bitmap.createBitmap(16,16,android.graphics.Bitmap.Config.ARGB_8888);
        generated.eraseColor(android.graphics.Color.CYAN);
        File originalAvatar=new File(folder,"avatar-input.jpg");
        try(FileOutputStream avatarOut=new FileOutputStream(originalAvatar)){generated.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,avatarOut);}
        generated.recycle();
        String customName=PersonalImages.save(ctx,android.net.Uri.fromFile(originalAvatar),key);
        String avatarKey=PersonalImages.avatarKey(key,"Тест");
        MessageDb old=new MessageDb(ctx,key);
        File backup=new File(ctx.getCacheDir(),"integration-"+key+".zip");
        String restoredKey=null;
        try{
            assertEquals(1,TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),old,msg->{}));
            long msgId=old.page(0,1,"",false,"",false,false).get(0).id;
            old.togglePinned(msgId);
            old.toggleFavorite(msgId);
            JSONArray registry=new JSONArray();
            JSONObject chat=new JSONObject();chat.put("key",key);chat.put("name","Тестовый чат");chat.put("tree","local:"+folder.getAbsolutePath());
            registry.put(chat);
            assertTrue(prefs.edit().putString("archive_registry",registry.toString())
                .putString(avatarKey,customName).commit());
            long started=SystemClock.elapsedRealtimeNanos();
            try(FileOutputStream out=new FileOutputStream(backup)){FullBackup.export(ctx,prefs,out,msg->{});}
            long exportMs=(SystemClock.elapsedRealtimeNanos()-started)/1000000L;
            try(FileInputStream input=new FileInputStream(backup)){
                BackupVerifier.Result result=BackupVerifier.verifyStream(input,msg->{});
                assertTrue(result.full);assertTrue(result.files>=4);
            }
            int n=FullBackup.restore(ctx,prefs,android.net.Uri.fromFile(backup),msg->{});
            assertEquals(1,n);
            JSONArray restored=new JSONArray(prefs.getString("archive_registry","[]"));
            assertEquals(2,restored.length());
            JSONObject entry=restored.getJSONObject(1);
            restoredKey=entry.getString("key");
            assertEquals(customName,prefs.getString(PersonalImages.avatarKey(restoredKey,"Тест"),""));
            assertNotNull(PersonalImages.load(ctx,customName));
            MessageDb fresh=new MessageDb(ctx,restoredKey);
            try{
                assertEquals(1,fresh.totalMessages());
                assertEquals(1,fresh.pinned().size());
                assertEquals(1,fresh.count("",false,"",false,true));
                assertEquals(1,fresh.mediaCount("image"));
            }finally{fresh.close();}
            File restoredFolder=new File(entry.getString("tree").substring(6));
            File restoredPhoto=new File(restoredFolder,"photos/sample.jpg");
            assertTrue(restoredPhoto.isFile());
            byte[] found=new byte[sample.length];
            try(FileInputStream in=new FileInputStream(restoredPhoto)){assertEquals(found.length,in.read(found));}
            assertArrayEquals(sample,found);
            Log.i("TG_ARCHIVE_BENCH","synthetic_full_backup_export_ms="+exportMs+
                " archive_bytes="+backup.length()+" media_file_bytes="+sample.length);
            ArchiveFileImport.deleteTree(restoredFolder.getParentFile().getParentFile().getName().startsWith("restore-")?restoredFolder.getParentFile().getParentFile():restoredFolder);
        }finally{
            old.close();ctx.deleteDatabase("telegram_"+key+".db");
            if(restoredKey!=null)ctx.deleteDatabase("telegram_"+restoredKey+".db");
            prefs.edit().clear().commit();
            ArchiveFileImport.deleteTree(folder);backup.delete();
            new File(new File(ctx.getFilesDir(),"personal_images"),customName).delete();
        }
    }

    /** Regression: an inconsistent outer manifest must not register any chat or alter
     * existing preferences. This uses synthetic data and does not require personal media. */
    @Test public void incompleteFullBackupDoesNotTouchRegistry() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-", "");
        SharedPreferences prefs=ctx.getSharedPreferences("tg-corrupt-"+id,Context.MODE_PRIVATE);
        final String original="[]";
        assertTrue(prefs.edit().putString("archive_registry",original).commit());
        File fake=new File(ctx.getCacheDir(),"incomplete-"+id+".zip");
        try{
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(fake))){
                zip.putNextEntry(new java.util.zip.ZipEntry("metadata.zip"));
                zip.write("synthetic-placeholder".getBytes("UTF-8"));zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("full-manifest.json"));
                zip.write("{\"format\":\"telegram-archive-full\",\"version\":2,\"chats\":[],\"files\":1,\"bytes\":3}".getBytes("UTF-8"));
                zip.closeEntry();
            }
            try{
                FullBackup.restore(ctx,prefs,android.net.Uri.fromFile(fake),msg->{});
                fail("Incomplete media tree must be rejected before metadata restore");
            }catch(IOException expected){
                assertTrue(expected.getMessage().contains("медиа"));
            }
            assertEquals(original,prefs.getString("archive_registry",""));
        }finally{
            fake.delete();prefs.edit().clear().commit();
        }
    }

    @Test public void mismatchedFullBackupRootsDoNotTouchRegistry() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-", "");
        SharedPreferences prefs=ctx.getSharedPreferences("tg-roots-"+id,Context.MODE_PRIVATE);
        final String original="[]";
        assertTrue(prefs.edit().putString("archive_registry",original).commit());
        File fake=new File(ctx.getCacheDir(),"wrong-roots-"+id+".zip");
        try{
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(fake))){
                zip.putNextEntry(new java.util.zip.ZipEntry("metadata.zip"));
                zip.write("synthetic-placeholder".getBytes("UTF-8"));zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("full-manifest.json"));
                zip.write("{\"format\":\"telegram-archive-full\",\"version\":2,\"chats\":[{\"chatIndex\":0,\"prefix\":\"media/chat-99/\"}],\"files\":0,\"bytes\":0}".getBytes("UTF-8"));
                zip.closeEntry();
            }
            try{
                FullBackup.restore(ctx,prefs,android.net.Uri.fromFile(fake),msg->{});
                fail("Incorrect media root must be rejected before metadata restore");
            }catch(IOException expected){
                assertTrue(expected.getMessage().contains("каталогов"));
            }
            assertEquals(original,prefs.getString("archive_registry",""));
        }finally{fake.delete();prefs.edit().clear().commit();}
    }

    @Test public void tamperedMediaHashRejectedBeforeRegistryChange() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        SharedPreferences prefs=ctx.getSharedPreferences("sha-check-"+id,Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putString("archive_registry","[]").commit());
        File fake=new File(ctx.getCacheDir(),"tampered-"+id+".zip");
        try{
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(fake))){
                zip.putNextEntry(new java.util.zip.ZipEntry("media/chat-0/photo.jpg"));
                zip.write(new byte[]{1,2,3,4});zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("metadata.zip"));
                zip.write("no-metadata".getBytes("UTF-8"));zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("full-manifest.json"));
                zip.write(("{\"format\":\"telegram-archive-full\",\"version\":2,"+
                    "\"chats\":[{\"chatIndex\":0,\"prefix\":\"media/chat-0/\"}],"+
                    "\"files\":1,\"bytes\":4,\"mediaSha256\":\""+
                    "0000000000000000000000000000000000000000000000000000000000000000\"}").getBytes("UTF-8"));
                zip.closeEntry();
            }
            try{
                FullBackup.restore(ctx,prefs,new FileInputStream(fake),msg->{});
                fail("Tampered media transcript must be rejected");
            }catch(IOException expected){assertTrue(expected.getMessage().contains("Контрольная сумма медиа"));}
            assertEquals("[]",prefs.getString("archive_registry",""));
            try(InputStream in=new FileInputStream(fake)){
                BackupVerifier.verifyStream(in,msg->{});fail("Verifier must reject tampered media even if ZIP CRC is valid");
            }catch(IOException expected){assertTrue(expected.getMessage().contains("Контрольная сумма медиа"));}
        }finally{fake.delete();prefs.edit().clear().commit();}
    }

    @Test public void changedAvatarChecksumRejectedWithoutOverwritingSettings()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        SharedPreferences prefs=ctx.getSharedPreferences("tg-custom-integrity-"+id,Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putString("archive_registry","[]").commit());
        File zipFile=new File(ctx.getCacheDir(),"bad-avatar-"+id+".zip");
        try{
            MediaIntegrity empty=new MediaIntegrity();
            try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile))){
                zip.putNextEntry(new java.util.zip.ZipEntry("metadata.zip"));
                zip.write("invalid-metadata".getBytes("UTF-8"));zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("custom/avatar.jpg"));
                zip.write(new byte[]{2,4,6,8});zip.closeEntry();
                zip.putNextEntry(new java.util.zip.ZipEntry("full-manifest.json"));
                String text="{\"format\":\"telegram-archive-full\",\"version\":2,"+
                    "\"chats\":[],\"files\":0,\"bytes\":0,"+
                    "\"mediaSha256\":\""+empty.hex()+"\","+
                    "\"customSha256\":\"0000000000000000000000000000000000000000000000000000000000000000\"}";
                zip.write(text.getBytes("UTF-8"));zip.closeEntry();
            }
            try(InputStream in=new FileInputStream(zipFile)){
                FullBackup.restore(ctx,prefs,in,msg->{});fail("Mutated avatar backup passed validation");
            }catch(IOException expected){assertTrue(expected.getMessage().contains("аватаров"));}
            assertEquals("[]",prefs.getString("archive_registry",""));
        }finally{zipFile.delete();prefs.edit().clear().commit();}
    }
    @Test public void interruptedBackupStreamNeverPublishesRegistry()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String id=UUID.randomUUID().toString().replace("-","");
        SharedPreferences prefs=ctx.getSharedPreferences("tg-stream-cut-"+id,Context.MODE_PRIVATE);
        assertTrue(prefs.edit().putString("archive_registry","[]").commit());
        ByteArrayOutputStream encoded=new ByteArrayOutputStream();
        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(encoded)){
            zip.putNextEntry(new java.util.zip.ZipEntry("media/chat-0/large.mp4"));
            byte[] chunk=new byte[16384];new java.util.Random(49).nextBytes(chunk);
            for(int i=0;i<10;i++)zip.write(chunk);
            zip.closeEntry();
        }
        byte[] complete=encoded.toByteArray();
        InputStream interrupted=new FilterInputStream(new ByteArrayInputStream(complete)){
            int remaining=Math.max(128,complete.length/2);
            @Override public int read(byte[] b,int off,int len)throws IOException{
                if(remaining<=0)throw new IOException("Simulated network/storage interruption");
                int read=super.read(b,off,Math.min(len,remaining));
                if(read>0)remaining-=read;return read;
            }
            @Override public int read()throws IOException{
                if(remaining<=0)throw new IOException("Simulated interruption");
                int b=super.read();if(b!=-1)remaining--;return b;
            }
        };
        try{
            FullBackup.restore(ctx,prefs,interrupted,msg->{});
            fail("Truncated archive unexpectedly restored");
        }catch(IOException expected){assertNotNull(expected.getMessage());}
        finally{assertEquals("[]",prefs.getString("archive_registry",""));prefs.edit().clear().commit();}
    }

}
