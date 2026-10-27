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
            try{ArchiveReadOnly.read(ctx,uri,"secure.zip","wrong-password".toCharArray(),files.get(0).name);fail("Wrong AES password accepted");}
            catch(Exception expected){}
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


    @Test public void duplicateArchiveNamesCannotSilentlyOpenWrongFile()throws Exception{
        // Two valid ZIP entries have the same name but different bytes.
        String raw="UEsDBBQAAAAAAKkbW11X7nGSBQAAAAUAAAAIAAAAc2FtZS50eHRmaXJzdFBLAwQUAAAAAACpG1tdaREftgYAAAAGAAAACAAAAHNhbWUudHh0c2Vjb25kUEsBAhQDFAAAAAAAqRtbXVfucZIFAAAABQAAAAgAAAAAAAAAAAAAAIABAAAAAHNhbWUudHh0UEsBAhQDFAAAAAAAqRtbXWkRH7YGAAAABgAAAAgAAAAAAAAAAAAAAIABKwAAAHNhbWUudHh0UEsFBgAAAAACAAIAbAAAAFcAAAAAAA==";
        File archive=temp("zip");
        try{
            try(FileOutputStream out=new FileOutputStream(archive)){
                out.write(android.util.Base64.decode(raw,android.util.Base64.DEFAULT));
            }
            Uri uri=Uri.fromFile(archive);
            try{ArchiveReadOnly.list(ctx,uri,"duplicate.zip",null);fail("Ambiguous list accepted");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("повторяются"));}
            try{ArchiveReadOnly.read(ctx,uri,"duplicate.zip",null,"same.txt");fail("Ambiguous name opened");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("повторяются"));}
        }finally{archive.delete();}
    }

    @Test public void tarXzRejectsExcessiveDecoderMemoryBeforeAllocating()throws Exception{
        // A tiny XZ stream requests a 128 MiB LZMA2 dictionary, over our 64 MiB limit.
        String raw="/Td6WFoAAATm1rRGAgAhAR4AAACbB1FmAQALYXJjaGl2ZSB0ZXN0AANEBUKu7EeJAAEkDKYY2NgftvN9AQAAAAAEWVo=";
        File archive=temp("tar.xz");
        try{
            try(FileOutputStream out=new FileOutputStream(archive)){
                out.write(android.util.Base64.decode(raw,android.util.Base64.DEFAULT));
            }
            try{ArchiveReadOnly.list(ctx,Uri.fromFile(archive),"large-dict.tar.xz",null);fail("Over-limit XZ dictionary accepted");}
            catch(IOException expected){
                assertNotNull(expected.getMessage());
                assertTrue("Expected memory limit error: "+expected,expected.getMessage().contains("memory would be needed")&&expected.getMessage().contains("limit was"));
            }
        }finally{archive.delete();}
    }
    @Test public void zipCryptoRequiresCorrectPassword()throws Exception{
        // Generated independently with Info-ZIP -X -P on synthetic note.txt.
        String encoded="UEsDBAoACQAAAKoJb1dbmriWKAAAABwAAAAIAAAAbm90ZS50eHQyObYxWCZq+1ImtchjblT2Cnl7TUSIxZRjMAEtHNfhOkofC4bmSdhcUEsHCFuauJYoAAAAHAAAAFBLAQIeAwoACQAAAKoJb1dbmriWKAAAABwAAAAIAAAAAAAAAAEAAACkgQAAAABub3RlLnR4dFBLBQYAAAAAAQABADYAAABeAAAAAAA=";
        File zip=temp("zip");char[] password="test-fixture".toCharArray();
        try{
            try(FileOutputStream out=new FileOutputStream(zip)){out.write(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));}
            Uri uri=Uri.fromFile(zip);List<ArchiveReadOnly.Entry> files=ArchiveReadOnly.list(ctx,uri,"legacy.zip",null);
            assertEquals(1,files.size());assertEquals("note.txt",files.get(0).name);assertTrue(files.get(0).encrypted);
            try{ArchiveReadOnly.read(ctx,uri,"legacy.zip",null,files.get(0).name);fail("Missing password accepted");}catch(Exception expected){}
            try{ArchiveReadOnly.read(ctx,uri,"legacy.zip","wrong".toCharArray(),files.get(0).name);fail("Wrong password accepted");}catch(Exception expected){}
            assertEquals("Synthetic ZipCrypto example\n",new String(ArchiveReadOnly.read(ctx,uri,"legacy.zip",password,files.get(0).name),StandardCharsets.UTF_8));
        }finally{java.util.Arrays.fill(password,'\0');zip.delete();}
    }
    @Test public void sevenZEncryptedHeadersRequireCorrectPassword()throws Exception{
        // Apache Commons Compress 1.28.0 synthetic test fixture, Apache License 2.0:
        // https://github.com/apache/commons-compress/blob/rel/commons-compress-1.28.0/src/test/resources/bla.encrypted.7z
        String encoded="N3q8ryccAAPOr6vM4AEAAAAAAAA2AAAAAAAAAMPV/c15gWF4/yjZnVseP50CUVynWCTYmSp6Ke098yYgF/M6rarfE3VIk5F/VKhRnpC3g8FD942gEXEDLnd12CRgkhC4qrpznDYACE9oBNV+7PMngR+vT6qzXjm/S08zuAzS7kP6aINNqGgZl3g9JTwGgqDCfkC35h3sjatUkdayb13cMgUiSA0sB7tR2E2lxhH/Hm30QeqD4dRDXT6l81+9PEOeQSNNZkrZfSy8lRJT5MKMAQtwUajG+BlU19/tUokPbvptGnP5PjZAHGEQrlK/avb4U0GKB12F4KU4v0zskuJwfX84UYn8fymldsL2Bn9pKrRQWx9b2AxZ97CUxdydqSv8Cc2U8gw1gJBMqOy7Af8JFTjOleTEou4N+ZdZw29Sf2QM1jKMJPSjGoVkwe2T8BmB4jM1PV+Hn8PAsrwN1K7vIMhOZEQyvSmXQFDSfDudsK6kFCdyZmqJjREA5baUovT+CsnOtEOy65EqnQmHtOh0ZYHPNn2UMuvzje+LVpw5OQaHXLyqSgA9PYSEuy9fqr+exsGfYZ6FE+K7UKPc5XLI60V7z6+4Z7em7vD6qG8IQYWNdQVJDXZbTmpDw/7nk+tXITiSCnowCfCNmLHbVSAubMECBTPUE2kDx01w49txxyIXBoFgAQmAgAAHCwEAAiQG8QcBClMHznmlTwXNceUjAwEBBV0AEAAAAQAMd4CRCgFx7ooQAAA=";
        File archive=temp("7z");try(FileOutputStream out=new FileOutputStream(archive)){
            out.write(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));
        }
        try{
            Uri uri=Uri.fromFile(archive);
            try{ArchiveReadOnly.list(ctx,uri,"header.7z",null);fail("Missing password accepted");}catch(Exception expected){}
            try{ArchiveReadOnly.list(ctx,uri,"header.7z","wrong".toCharArray());fail("Wrong password accepted");}catch(Exception expected){}
            List<ArchiveReadOnly.Entry> entries=ArchiveReadOnly.list(ctx,uri,"header.7z","foo".toCharArray());
            assertEquals(2,entries.size());assertEquals("test1.xml",entries.get(0).name);assertEquals("test2.xml",entries.get(1).name);
            String body=new String(ArchiveReadOnly.read(ctx,uri,"header.7z","foo".toCharArray(),"test2.xml"),StandardCharsets.UTF_8);
            assertTrue(body.contains("<meinxml>"));assertTrue(body.contains("<leer />"));
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
            try{ArchiveReadOnly.read(ctx,uri,"encrypted.7z",null,"private.txt");fail("Missing password accepted");}catch(Exception expected){}
            try{ArchiveReadOnly.read(ctx,uri,"encrypted.7z","wrong-pass-123".toCharArray(),"private.txt");fail("Wrong password accepted");}catch(Exception expected){}
            byte[] decoded=ArchiveReadOnly.read(ctx,uri,"encrypted.7z",password,"private.txt");
            assertArrayEquals("Correct 7z AES password must decrypt the exact original",body,decoded);
        }finally{
            java.util.Arrays.fill(password,'\0');
            plain.delete();archive.delete();
        }
    }

}
