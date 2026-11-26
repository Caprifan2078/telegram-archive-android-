package org.openarchive.telegram;
import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;
/** Independently produced and verified 7-Zip fixtures; no mock or assumed compatibility. */
public final class EncryptedZipCodecProbeTest {
    private void verify(String codec)throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("zip-codec-",".zip",ctx.getCacheDir());
        String key=UUID.randomUUID().toString().replace("-","");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        char[] password="codec-test-only".toCharArray();
        try{
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("zip_codec_probe/"+codec+".zip");
                OutputStream out=new FileOutputStream(file)){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
            try{ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",key,null,p->{});fail("Encrypted ZIP opened without password: "+codec);}
            catch(ArchiveFileImport.PasswordRequired expected){}
            assertFalse(root.exists());
            try{ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",key,"wrong-password".toCharArray(),p->{});fail("Wrong password accepted: "+codec);}
            catch(Exception expected){}
            assertFalse(root.exists());
            File chat;
            try{
                chat=ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",key,password,p->{});
            }catch(Exception ex){
                android.util.Log.e("ZipCodecProbe",codec+" failed: "+ex.getClass().getName()+": "+ex.getMessage(),ex);
                throw ex;
            }
            assertTrue(new File(chat,"messages.html").isFile());
            assertTrue(new String(java.nio.file.Files.readAllBytes(new File(chat,"messages.html").toPath()),StandardCharsets.UTF_8).contains("ZIP codec synthetic marker"));
            assertArrayEquals(before,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{
            java.util.Arrays.fill(password,'\0');ArchiveFileImport.deleteTree(root);file.delete();
        }
    }
    private void verifyTamperedAuthenticationCode(String codec)throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=File.createTempFile("zip-mac-probe-",".zip",ctx.getCacheDir());
        String key=UUID.randomUUID().toString().replace("-","");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        char[] password="codec-test-only".toCharArray();
        try{
            try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("zip_codec_probe/"+codec+".zip");
                OutputStream out=new FileOutputStream(file)){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
            }
            long macOffset;
            try(org.apache.commons.compress.archivers.zip.ZipFile metadata=
                org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(file).get()){
                org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry=metadata.getEntry("messages.html");
                assertNotNull(entry);
                assertTrue("Fixture must be encrypted",entry.getGeneralPurposeBit().usesEncryption());
                assertTrue("AES header and MAC must exist",entry.getCompressedSize()>28);
                // The WinZip AES authentication tag occupies the last 10 bytes
                // of the compressed/encrypted entry; do not change payload or password.
                macOffset=entry.getDataOffset()+entry.getCompressedSize()-1;
                assertTrue(macOffset>=0&&macOffset<file.length());
            }
            try(RandomAccessFile raf=new RandomAccessFile(file,"rw")){
                raf.seek(macOffset);int old=raf.readUnsignedByte();
                raf.seek(macOffset);raf.writeByte(old^1);
            }
            byte[] before=java.nio.file.Files.readAllBytes(file.toPath());
            try{
                ArchiveFileImport.extract(ctx,Uri.fromFile(file),"zip",key,password,p->{});
                fail("Tampered AES authentication code accepted: "+codec);
            }catch(Exception expected){}
            assertFalse("Authentication failure must remove staging",root.exists());
            assertArrayEquals("Original tampered test archive was modified",before,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{
            java.util.Arrays.fill(password,'\0');ArchiveFileImport.deleteTree(root);file.delete();
        }
    }
    @Test public void tamperedAesDeflateMacIsRejected()throws Exception{verifyTamperedAuthenticationCode("Deflate");}
    @Test public void tamperedAesDeflate64MacIsRejected()throws Exception{verifyTamperedAuthenticationCode("Deflate64");}
    @Test public void tamperedAesBZip2MacIsRejected()throws Exception{verifyTamperedAuthenticationCode("BZip2");}
    @Test public void tamperedAesLzmaMacIsRejected()throws Exception{verifyTamperedAuthenticationCode("LZMA");}
    @Test public void aesDeflate()throws Exception{verify("Deflate");}
    @Test public void aesDeflate64()throws Exception{verify("Deflate64");}
    @Test public void aesBZip2()throws Exception{verify("BZip2");}
    @Test public void aesLzma()throws Exception{verify("LZMA");}
}
