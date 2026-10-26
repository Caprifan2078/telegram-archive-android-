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
            try{
                ArchiveReadOnly.read(ctx,uri,"secure.zip","wrong-password-123".toCharArray(),files.get(0).name);
                fail("Wrong AES ZIP password must never return content");
            }catch(Exception expected){}
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
    /**
     * Tiny real TAR fixtures compressed offline with Python's lzma/bz2. The
     * Android minified APK deliberately contains DECODERS but not ENCODERS;
     * generating TAR.XZ/BZip2 in androidTest would fail with NoClassDefFound.
     */
    @Test public void tarXzListsAndReadsTextWithoutExtracting()throws Exception{
        verifyCompressedFixture("tar.xz",
            "/Td6WFoAAATm1rRGAgAhARYAAAB0L+Wj4Cf/AHhdADMbyc2KqWBC3VDqX/k/JQCSij3M88/OQnuLyBIdGv0/Y/4L+byQEU1FLmAadS65O1QgECK/kDWEMq+1z/IiD5pGMlp5UMYkwqys/1821XCvqlfX05Vc8gIEMvb+8FzOJDnyO1G8fRO5NS36swPY2OQiGR3DgTbgAABaEbROqE+MAQABlAGAUAAAszEn+bHEZ/sCAAAAAARZWg==",
            "XZ читается");
    }
    @Test public void tarBzip2ListsAndReadsTextWithoutExtracting()throws Exception{
        verifyCompressedFixture("tar.bz2",
            "QlpoOTFBWSZTWfVOMTkAAHJfysqEQAH9gBAAIBBnJN5AMIDCQGAACAggAHURJ6gA00GmgGTIGnqCSlGR6aQwAQYTAHFrjohBk8hCI7ZGnVzeyBDAYRub5fE+wROJgthWhNZ0nnmIVQIXqKs2LYtgNc4GtccjARvHvEi64sSQci7kinChIeqcYnI=",
            "BZip2 читается");
    }
    private void verifyCompressedFixture(String type,String base64,String value)throws Exception{
        File archive=temp(type);
        try{
            byte[] data=android.util.Base64.decode(base64,android.util.Base64.DEFAULT);
            try(FileOutputStream out=new FileOutputStream(archive)){out.write(data);}
            Uri uri=Uri.fromFile(archive);
            List<ArchiveReadOnly.Entry> items=ArchiveReadOnly.list(ctx,uri,"sample."+type,null);
            assertEquals(1,items.size());
            assertEquals("folder/test.txt",items.get(0).name);
            assertEquals(value,new String(
                ArchiveReadOnly.read(ctx,uri,"sample."+type,null,items.get(0).name),
                StandardCharsets.UTF_8));
        }finally{archive.delete();}
    }

    @Test public void encryptedSevenZRejectsMissingAndWrongPassword()throws Exception{
        File plain=temp("txt"),archive=temp("7z");
        char[] password="synthetic-7z-AES-test-123".toCharArray();
        try{
            byte[] body="Зашифрованный файл 7z AES256".getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream out=new FileOutputStream(plain)){out.write(body);}
            // Supported public Commons Compress API (1.28); writes AES256 content.
            try(SevenZOutputFile writer=new SevenZOutputFile(archive,password)){
                SevenZArchiveEntry entry=writer.createArchiveEntry(plain,"private.txt");
                writer.putArchiveEntry(entry);writer.write(body);
                writer.closeArchiveEntry();writer.finish();
            }
            Uri uri=Uri.fromFile(archive);
            try{
                byte[] wrong=ArchiveReadOnly.read(ctx,uri,"encrypted.7z",null,"private.txt");
                assertFalse("Missing password disclosed plaintext",java.util.Arrays.equals(body,wrong));
            }catch(Exception expected){}
            try{
                byte[] wrong=ArchiveReadOnly.read(ctx,uri,"encrypted.7z",
                    "wrong-pass-123".toCharArray(),"private.txt");
                assertFalse("Wrong password disclosed plaintext",java.util.Arrays.equals(body,wrong));
            }catch(Exception expected){}
            byte[] decoded=ArchiveReadOnly.read(ctx,uri,"encrypted.7z",password,"private.txt");
            assertArrayEquals("Correct 7z AES password must decrypt the exact original",body,decoded);
        }finally{
            java.util.Arrays.fill(password,'\0');
            plain.delete();archive.delete();
        }
    }

}
