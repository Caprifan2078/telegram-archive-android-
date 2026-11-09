package org.openarchive.telegram;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The full backup must never silently omit a corrupt or unreadable personal-images directory. */
@RunWith(AndroidJUnit4.class)
public final class FullBackupCustomAssetSafetyRegressionTest {
    private final Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();

    private final class Fixture implements AutoCloseable {
        final String key=UUID.randomUUID().toString().replace("-","");
        final File sandbox=new File(target.getCacheDir(),"custom-assets-"+key);
        final File chat=new File(sandbox,"synthetic-chat");
        final File personal=new File(sandbox,"personal_images");
        final Context isolated;
        final SharedPreferences prefs=target.getSharedPreferences("custom-assets-"+key,Context.MODE_PRIVATE);
        final MessageDb db=new MessageDb(target,key);
        final long favorite;
        final String registry;

        Fixture() throws Exception {
            assertTrue(sandbox.mkdir());
            assertTrue(chat.mkdir());
            isolated=new ContextWrapper(target) {
                @Override public File getFilesDir(){return sandbox;}
            };
            SQLiteDatabase sql=db.getWritableDatabase();
            db.clearStage(sql);
            favorite=db.stage(sql,"Synthetic","2024-06-01","Keep original chat",null,"");
            db.publishStaged(sql);
            db.toggleFavorite(favorite);
            registry=new JSONArray().put(new JSONObject()
                .put("key",key).put("name","Synthetic")
                .put("tree","local:"+chat.getAbsolutePath())).toString();
            assertTrue(prefs.edit().putString("archive_registry",registry).commit());
        }
        ByteArrayOutputStream backup() throws Exception {
            ByteArrayOutputStream written=new ByteArrayOutputStream();
            FullBackup.export(isolated,prefs,written,ignored->{});
            return written;
        }
        void verifyUnchanged(){
            assertEquals(registry,prefs.getString("archive_registry",""));
            assertEquals(1,db.totalMessages());
            assertTrue(db.isFavorite(favorite));
        }
        @Override public void close(){
            db.close();
            target.deleteDatabase("telegram_"+key+".db");
            prefs.edit().clear().commit();
            ArchiveFileImport.deleteTree(sandbox);
        }
    }

    @Test public void regularFileInsteadOfImageDirectoryMustNotBeTreatedAsEmpty()throws Exception {
        try(Fixture f=new Fixture()){
            assertTrue(f.personal.createNewFile());
            try{
                f.backup();
                fail("A file at personal_images cannot be silently treated as an empty folder");
            }catch(IOException expected){
                assertTrue(String.valueOf(expected.getMessage()),
                    expected.getMessage()!=null && expected.getMessage().contains("изображен"));
            }
            f.verifyUnchanged();
        }
    }

    @Test public void genuinelyMissingImageDirectoryIsAllowed()throws Exception {
        try(Fixture f=new Fixture()){
            assertFalse(f.personal.exists());
            byte[] bytes=f.backup().toByteArray();
            BackupVerifier.Result report=BackupVerifier.verifyStream(new ByteArrayInputStream(bytes),ignored->{});
            assertTrue(report.full);
            assertTrue(report.files>=2);
            f.verifyUnchanged();
        }
    }

    @Test public void existingEmptyImageDirectoryIsAllowed()throws Exception {
        try(Fixture f=new Fixture()){
            assertTrue(f.personal.mkdir());
            byte[] bytes=f.backup().toByteArray();
            BackupVerifier.Result report=BackupVerifier.verifyStream(new ByteArrayInputStream(bytes),ignored->{});
            assertTrue(report.full);
            f.verifyUnchanged();
        }
    }

    @Test public void validImageIsIncludedInFullBackup()throws Exception {
        try(Fixture f=new Fixture()){
            assertTrue(f.personal.mkdir());
            byte[] image=new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,(byte)0xd9};
            try(FileOutputStream out=new FileOutputStream(new File(f.personal,"synthetic.jpg"))){out.write(image);}
            boolean found=false;
            try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(f.backup().toByteArray()))){
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null) {
                    if(entry.getName().equals("custom/synthetic.jpg")){
                        ByteArrayOutputStream read=new ByteArrayOutputStream();
                        byte[] buffer=new byte[16];int n;
                        while((n=zip.read(buffer))!=-1)read.write(buffer,0,n);
                        assertArrayEquals(image,read.toByteArray());
                        found=true;
                    }
                    zip.closeEntry();
                }
            }
            assertTrue("Original user image must remain present in full backup",found);
            f.verifyUnchanged();
        }
    }
}
