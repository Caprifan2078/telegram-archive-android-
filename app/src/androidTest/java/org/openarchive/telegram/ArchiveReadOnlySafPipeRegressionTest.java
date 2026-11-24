package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real ZIP/7z AES fixtures supplied via a pipe, not a seekable local file. */
@RunWith(AndroidJUnit4.class)
public final class ArchiveReadOnlySafPipeRegressionTest {
    private final Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Context tests=InstrumentationRegistry.getInstrumentation().getContext();
    private static final String ENTRY="Telegram/messages.html";
    private static final String MESSAGE="<html>Encrypted Telegram test archive through SAF pipe</html>";

    private File fixture(boolean zip,char[] password)throws Exception{
        // The instrumentation test APK's cache directory is not guaranteed to
        // have been created on a fresh emulator before the first fixture.
        File cache=tests.getCacheDir();
        if(!cache.isDirectory()&&!cache.mkdirs())
            throw new java.io.IOException("Cannot create test fixture cache directory: "+cache);
        String name=UUID.randomUUID().toString().replace("-","")+(zip?".zip":".7z");
        File output=new File(tests.getCacheDir(),name);
        File text=File.createTempFile("synthetic-html-",".html",tests.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(text)){
                out.write(MESSAGE.getBytes(StandardCharsets.UTF_8));
            }
            if(zip){
                try(ZipFile writer=new ZipFile(output,password)){
                    ZipParameters params=new ZipParameters();
                    params.setFileNameInZip(ENTRY);
                    params.setEncryptFiles(true);
                    params.setEncryptionMethod(EncryptionMethod.AES);
                    params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                    writer.addFile(text,params);
                }
            }else{
                try(SevenZOutputFile writer=new SevenZOutputFile(output,password)){
                    SevenZArchiveEntry entry=writer.createArchiveEntry(text,ENTRY);
                    writer.putArchiveEntry(entry);
                    writer.write(MESSAGE.getBytes(StandardCharsets.UTF_8));
                    writer.closeArchiveEntry();writer.finish();
                }
            }
            assertTrue(output.length()>0&&output.length()<2L*1024*1024);
            return output;
        }finally{text.delete();}
    }
    private Set<String> temporaryCopies(){
        String[] names=target.getCacheDir().list((d,n)->n.startsWith("archive-view-"));
        return names==null?new HashSet<>():new HashSet<>(Arrays.asList(names));
    }
    private Uri uri(File file){
        return Uri.parse("content://"+ArchivePipeTestProvider.AUTHORITY+"/"+file.getName());
    }
    private void verify(boolean zip)throws Exception{
        char[] secret="synthetic-pipe-archive-password".toCharArray();
        File file=fixture(zip,secret);
        Uri uri=uri(file);
        Set<String> existing=temporaryCopies();
        try{
            // Verify the provider really uses a pipe rather than a seekable
            // descriptor, so the fallback path cannot pass by accident.
            boolean nonSeekable=false;
            try(ParcelFileDescriptor fd=target.getContentResolver().openFileDescriptor(uri,"r");
                FileInputStream in=new FileInputStream(fd.getFileDescriptor())){
                try{in.getChannel().position(0);in.getChannel().size();}
                catch(java.io.IOException ex){nonSeekable=true;}
            }
            assertTrue("Synthetic provider must produce a nonseekable descriptor",nonSeekable);
            List<ArchiveReadOnly.Entry> entries=
                ArchiveReadOnly.list(target,uri,zip?"export.zip":"export.7z",secret);
            assertEquals(1,entries.size());
            assertEquals(ENTRY,entries.get(0).name);
            if(zip)assertTrue("AES ZIP should be visibly marked as encrypted",entries.get(0).encrypted);
            byte[] actual=ArchiveReadOnly.read(target,uri,zip?"export.zip":"export.7z",secret,ENTRY);
            assertEquals(MESSAGE,new String(actual,StandardCharsets.UTF_8));
            try{
                ArchiveReadOnly.read(target,uri,zip?"export.zip":"export.7z",
                    "definitely-wrong-password".toCharArray(),ENTRY);
                fail("Wrong password must never decode encrypted entry");
            }catch(Exception expected){}
            assertTrue("Synthetic original must remain intact",file.isFile()&&file.length()>0);
        }finally{
            Arrays.fill(secret,'\0');
            file.delete();
            assertEquals("Temporary copies must be deleted after every preview",
                existing,temporaryCopies());
        }
    }
    @Test public void encryptedZipPreviewWorksThroughPipe()throws Exception{verify(true);}
    @Test public void encryptedSevenZPreviewWorksThroughPipe()throws Exception{verify(false);}
    @Test public void previewCanCopyLargeArchivesWhenStorageAllowsWithoutFillingPhone(){
        long free=8L*1024*1024*1024;
        long limit=ArchiveReadOnly.temporaryCopyLimit(free);
        assertTrue("Old 512 MiB preview cutoff must be gone",limit>3L*1024*1024*1024);
        assertTrue("Keep free space reserved",limit<free);
        assertEquals(0,ArchiveReadOnly.temporaryCopyLimit(128L*1024*1024));
    }
}
