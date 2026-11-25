package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class ProtectedArchiveSessionTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final byte[] TEXT="Synthetic confidential message".getBytes(StandardCharsets.UTF_8);
    private File createZip(char[] password)throws Exception{
        File input=File.createTempFile("protected-test-input-",".html",context.getCacheDir());
        File archive=File.createTempFile("protected-test-",".zip",context.getCacheDir());
        if(!archive.delete())throw new IOException("Fixture path occupied");
        try{
            Files.write(input.toPath(),TEXT);
            try(ZipFile zip=new ZipFile(archive,password)){
                ZipParameters p=new ZipParameters();
                p.setFileNameInZip("Chat/messages.html");
                p.setEncryptFiles(true);p.setEncryptionMethod(EncryptionMethod.AES);
                p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                zip.addFile(input,p);
                p.setFileNameInZip("Chat/photos/photo.jpg");zip.addFile(input,p);
            }
            return archive;
        }catch(Exception ex){archive.delete();throw ex;}
        finally{input.delete();}
    }
    @Test public void aesZipReadsOnlyRequestedEntriesAndLeavesOriginalUnchanged()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try{
            byte[] before=Files.readAllBytes(zip.toPath());
            ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret);
            try{
                assertFalse(session.usesContainerCopy());
                assertEquals(2,session.list().size());
                Arrays.fill(secret,'\0'); // session owns its own copy
                assertArrayEquals(TEXT,session.read("Chat/messages.html",1024));
                assertArrayEquals(TEXT,session.read("Chat/photos/photo.jpg",1024));
                try{session.read("Chat/messages.html",2);fail("limit must fail");}
                catch(IOException expected){}
                try{session.read("../Chat/messages.html",1024);fail("unsafe path");}
                catch(IOException expected){}
            }finally{session.close();}
            try{session.read("Chat/messages.html",1024);fail("locked session must reject reads");}
            catch(IOException expected){}
            assertArrayEquals(before,Files.readAllBytes(zip.toPath()));
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void wrongPasswordCannotReadProtectedMessage()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try{
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip","incorrect".toCharArray())){
                try{session.read("Chat/messages.html",1024);fail("Wrong key accepted");}
                catch(IOException expected){}
            }
            try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",null)){
                try{session.read("Chat/messages.html",1024);fail("Missing key accepted");}
                catch(IOException expected){}
            }
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
    @Test public void ramMediaSupportsRandomAccessAndRefusesReadsAfterLock()throws Exception{
        byte[] input=new byte[600000];
        new java.util.Random(71).nextBytes(input);
        ProtectedMediaSource source=new ProtectedMediaSource(input.length);
        try{
            try{source.getSize();fail("Unsealed source must not be readable");}catch(IOException expected){}
            OutputStream out=source.sink();out.write(input);
            try{out.write(1);fail("Limit must be enforced");}catch(IOException expected){}
            source.seal();assertEquals(input.length,source.getSize());
            byte[] actual=new byte[330000];
            int count=source.readAt(250000,actual,7,300000);
            assertEquals(300000,count);
            assertArrayEquals(Arrays.copyOfRange(input,250000,550000),Arrays.copyOfRange(actual,7,300007));
            assertEquals(10,source.readAt(input.length-10,actual,0,20));
            assertEquals(-1,source.readAt(input.length,actual,0,1));
            assertEquals(0,source.readAt(input.length,actual,0,0));
        }finally{source.close();}
        try{source.readAt(0,new byte[1],0,1);fail("Locked source must refuse reads");}catch(IOException expected){}
        source.close();
    }

    @Test public void encryptedArchiveAttachmentCanFeedSeekableMediaWithoutPlaintextFile()throws Exception{
        char[] secret="synthetic-password".toCharArray();File zip=createZip(secret);
        try(ProtectedArchiveSession session=ProtectedArchiveSession.open(context,Uri.fromFile(zip),"zip",secret);
            ProtectedMediaSource media=ProtectedMediaSource.load(session,"Chat/photos/photo.jpg",1024)){
            byte[] output=new byte[TEXT.length];
            assertEquals(TEXT.length,media.readAt(0,output,0,output.length));
            assertArrayEquals(TEXT,output);
        }finally{zip.delete();Arrays.fill(secret,'\0');}
    }
}
