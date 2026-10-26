package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ArchivePreviewIntegrationTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File temp(String extension)throws Exception{
        return File.createTempFile("ta-readonly-test-","."+extension,ctx.getCacheDir());
    }
    @Test public void zipListsWithoutExtractingAndReadsSelectedText()throws Exception{
        File archive=temp("zip");
        try{
            try(ZipOutputStream out=new ZipOutputStream(new FileOutputStream(archive))){
                out.putNextEntry(new ZipEntry("notes/hello.txt"));
                out.write("Привет, мир".getBytes(StandardCharsets.UTF_8));out.closeEntry();
                out.putNextEntry(new ZipEntry("../evil.txt"));
                out.write("unsafe".getBytes(StandardCharsets.UTF_8));out.closeEntry();
            }
            Uri uri=Uri.fromFile(archive);
            List<ArchiveReadOnly.Entry> entries=ArchiveReadOnly.list(ctx,uri,"a.zip",null);
            assertEquals(1,entries.size());assertEquals("notes/hello.txt",entries.get(0).name);
            assertEquals("Привет, мир",new String(
                ArchiveReadOnly.read(ctx,uri,"a.zip",null,entries.get(0).name),StandardCharsets.UTF_8));
        }finally{archive.delete();}
    }
    @Test public void encryptedZipRequiresCorrectPassword()throws Exception{
        File raw=temp("txt"),zip=temp("zip");
        try{
            try(FileOutputStream out=new FileOutputStream(raw)){out.write("encrypted offline".getBytes(StandardCharsets.UTF_8));}
            char[] password="secret-key-123".toCharArray();
            ZipParameters options=new ZipParameters();options.setEncryptFiles(true);
            options.setEncryptionMethod(EncryptionMethod.AES);
            options.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            try(ZipFile archive=new ZipFile(zip,password)){archive.addFile(raw,options);}
            Uri uri=Uri.fromFile(zip);
            List<ArchiveReadOnly.Entry> files=ArchiveReadOnly.list(ctx,uri,"secure.zip",null);
            assertEquals(1,files.size());assertTrue(files.get(0).encrypted);
            try{ArchiveReadOnly.read(ctx,uri,"secure.zip",null,files.get(0).name);fail();}
            catch(IOException expected){assertTrue(expected.getMessage().contains("пароль"));}
            assertEquals("encrypted offline",new String(
                ArchiveReadOnly.read(ctx,uri,"secure.zip",password,files.get(0).name),StandardCharsets.UTF_8));
        }finally{raw.delete();zip.delete();}
    }
    @Test public void sevenZListsAndReadsOneFile()throws Exception{
        File original=temp("txt"),archive=temp("7z");
        try{
            try(FileOutputStream out=new FileOutputStream(original)){out.write("seven-file".getBytes(StandardCharsets.UTF_8));}
            try(SevenZOutputFile writer=new SevenZOutputFile(archive)){
                SevenZArchiveEntry entry=writer.createArchiveEntry(original,"sample.txt");
                writer.putArchiveEntry(entry);writer.write("seven-file".getBytes(StandardCharsets.UTF_8));
                writer.closeArchiveEntry();writer.finish();
            }
            Uri uri=Uri.fromFile(archive);
            List<ArchiveReadOnly.Entry> items=ArchiveReadOnly.list(ctx,uri,"a.7z",null);
            assertEquals(1,items.size());
            assertEquals("seven-file",new String(
                ArchiveReadOnly.read(ctx,uri,"a.7z",null,items.get(0).name),StandardCharsets.UTF_8));
        }finally{original.delete();archive.delete();}
    }
    @Test public void tarGzipListsAndReads()throws Exception{
        File archive=temp("tar.gz");
        try{
            try(TarArchiveOutputStream tar=new TarArchiveOutputStream(
                    new GZIPOutputStream(new FileOutputStream(archive)))){
                byte[] bytes="gz-entry".getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry e=new TarArchiveEntry("data/note.txt");e.setSize(bytes.length);
                tar.putArchiveEntry(e);tar.write(bytes);tar.closeArchiveEntry();
            }
            Uri uri=Uri.fromFile(archive);
            List<ArchiveReadOnly.Entry> items=ArchiveReadOnly.list(ctx,uri,"history.tar.gz",null);
            assertEquals(1,items.size());
            assertEquals("gz-entry",new String(
                ArchiveReadOnly.read(ctx,uri,"history.tar.gz",null,items.get(0).name),StandardCharsets.UTF_8));
        }finally{archive.delete();}
    }
}
