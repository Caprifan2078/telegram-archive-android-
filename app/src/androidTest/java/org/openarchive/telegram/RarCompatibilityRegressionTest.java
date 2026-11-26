package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Small public test archives from Junrar v8.1.2's own test resources.
 * Origin: https://github.com/junrar/junrar/tree/v8.1.2/src/test/resources/com/github/junrar
 * They contain only synthetic file1/file2 text; no user data. Public fixture password: junrar.
 */
@RunWith(AndroidJUnit4.class)
public class RarCompatibilityRegressionTest {
    private static final String RAR4="UmFyIRoHAM+QcwAADQAAAAAAAAAJl3QggCkABwAAAAcAAAACun0Zem4DYz0dMAkAIAAAAEZJTEUxLlRYVGZpbGUxDQo8d3QggCkABwAAAAcAAAAC48NfeHEDYz0dMAkAIAAAAEZJTEUyLlRYVGZpbGUyDQrEPXsAQAcA";
    private static final String RAR5="UmFyIRoHAQDz4YLrCwEFBwAGAQGAgIAATS800SUCAwuHAASHACC6fRl6gAAACUZJTEUxLlRYVAoDAgDwWYPlessBZmlsZTENCqOo3u8lAgMLhwAEhwAg48NfeIAAAAlGSUxFMi5UWFQKAwIAd+2G5XrLAWZpbGUyDQodd1ZRAwUEAA==";
    private static final String RAR5_DATA_PASSWORD="UmFyIRoHAQAzkrXlCgEFBgAFAQGAgABfIc6zUQIDMaAABoYApIMCY0kUXxnn0+uAAwEJZmlsZTEudHh0MAEAAw+rgP7jvy7bm3QlOdS3YZwFYG45NDcQ1tsYPE/SbYZEVJmc4Qhprj8yi5S4gj56t2Cqe0X/M28NnKG0ccGCC/yKLzwOLmAr44eAnrJUHXdWUQMFBAA=";
    private static final String RAR5_HEADER_PASSWORD="UmFyIRoHAQAYOJrPIQQAAAEPprqRs1Vs70VeAnJr65GiUWzJnBs88EB6pEDZCDMNebpM1FRWRidOoP8NEKunwvQXSE6qyWZSmFdTmJz5B4PRrGmc/9wgf07nAr0VnT/SUD7KGRm04mC2+uJap3bok3fPNwjtWnVbqxga+30ke8uVJYZkiuuGhz7dmPmsjcbbifv8JRtif4lMcFsoiFclxaKGgHWEAQ5iUr3A418NqLr87fq2lB4LpFyCVjVgrfNS3Ou5IdI1MBz0SPemsbqYy9mjOR0uTISWgDqgl9qBApSZxzov0pm4HbMDGpR2jEAzLnvZFtWMyUYPoVpguDM=";
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private File create(String encoded)throws Exception{
        File file=File.createTempFile("synthetic-rar-", ".rar",ctx.getCacheDir());
        try(FileOutputStream out=new FileOutputStream(file)){
            out.write(android.util.Base64.decode(encoded,android.util.Base64.DEFAULT));
        }
        return file;
    }
    private Set<String> cached(){
        String[] files=ctx.getCacheDir().list((dir,name)->name.startsWith("archive-view-"));
        return files==null?new HashSet<>():new HashSet<>(Arrays.asList(files));
    }
    private void verifyPlain(String fixture)throws Exception{
        File file=create(fixture);byte[] original=java.nio.file.Files.readAllBytes(file.toPath());Set<String> before=cached();
        try{
            Uri uri=Uri.fromFile(file);
            List<ArchiveReadOnly.Entry> entries=ArchiveReadOnly.list(ctx,uri,"fixture.rar",null);
            assertEquals(2,entries.size());
            boolean found=false;for(ArchiveReadOnly.Entry entry:entries)if(entry.name.equals("FILE1.TXT"))found=true;
            assertTrue(found);
            byte[] body=ArchiveReadOnly.read(ctx,uri,"fixture.rar",null,"FILE1.TXT");
            assertEquals(7,body.length);assertTrue(new String(body,StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).contains("file1"));
            assertArrayEquals(original,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{file.delete();assertEquals("No temporary RAR copy may remain",before,cached());}
    }
    @Test public void realRar4AndRar5ListAndReadWithoutModifyingSource()throws Exception{
        verifyPlain(RAR4);verifyPlain(RAR5);
    }
    @Test public void rar5EncryptedContentShowsLockAndChecksPassword()throws Exception{
        File file=create(RAR5_DATA_PASSWORD);byte[] original=java.nio.file.Files.readAllBytes(file.toPath());Set<String> before=cached();
        try{
            Uri uri=Uri.fromFile(file);List<ArchiveReadOnly.Entry> entries=ArchiveReadOnly.list(ctx,uri,"encrypted.rar",null);
            assertEquals(1,entries.size());assertEquals("file1.txt",entries.get(0).name);
            assertTrue("Encrypted RAR member should show its lock",entries.get(0).encrypted);
            try{ArchiveReadOnly.read(ctx,uri,"encrypted.rar",null,"file1.txt");fail("Missing password accepted");}catch(Exception expected){}
            try{ArchiveReadOnly.read(ctx,uri,"encrypted.rar","wrong".toCharArray(),"file1.txt");fail("Wrong password accepted");}catch(Exception expected){}
            assertEquals("file1\n",new String(ArchiveReadOnly.read(ctx,uri,"encrypted.rar","junrar".toCharArray(),"file1.txt"),StandardCharsets.UTF_8));
            assertArrayEquals(original,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{file.delete();assertEquals("No temporary RAR copy may remain",before,cached());}
    }
    /** Import path, not just read-only preview: extract actual encrypted RAR5 payloads
     * through the same bounded Junrar routine that handles Telegram archives. */
    @Test public void encryptedRar5ImportsWithPasswordAndRejectsWrongKey()throws Exception{
        java.lang.reflect.Method unpack=ArchiveFileImport.class.getDeclaredMethod("extractRar",
            Context.class,Uri.class,File.class,char[].class,ArchiveFileImport.Budget.class,
            ArchiveFileImport.Progress.class);
        unpack.setAccessible(true);
        for(String fixture:new String[]{RAR5_DATA_PASSWORD,RAR5_HEADER_PASSWORD}){
            File source=create(fixture);
            byte[] original=java.nio.file.Files.readAllBytes(source.toPath());
            File root=new File(ctx.getCacheDir(),"rar-import-qa-"+UUID.randomUUID().toString());
            assertTrue(root.mkdirs());
            try{
                unpack.invoke(null,ctx,Uri.fromFile(source),root,"junrar".toCharArray(),
                    new ArchiveFileImport.Budget(0,0,root),(ArchiveFileImport.Progress)info->{});
                File actual=new File(root,"file1.txt");
                assertTrue("Encrypted RAR member was not materialized",actual.isFile());
                assertEquals("file1\n",new String(java.nio.file.Files.readAllBytes(actual.toPath()),
                    StandardCharsets.UTF_8));
                assertArrayEquals("Original encrypted archive changed",original,
                    java.nio.file.Files.readAllBytes(source.toPath()));
            }finally{ArchiveFileImport.deleteTree(root);source.delete();}
        }
        File wrong=create(RAR5_DATA_PASSWORD);
        String key=UUID.randomUUID().toString().replace("-","");
        File extracted=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        try{
            try{
                ArchiveFileImport.extract(ctx,Uri.fromFile(wrong),"rar",key,
                    "bad-password".toCharArray(),message->{});
                fail("Wrong password must reject import");
            }catch(Exception expected){}
            assertFalse("Wrong password left a half-imported Telegram folder",extracted.exists());
        }finally{wrong.delete();ArchiveFileImport.deleteTree(extracted);}
    }

    @Test public void rar5EncryptedNamesRequireCorrectPassword()throws Exception{
        File file=create(RAR5_HEADER_PASSWORD);byte[] original=java.nio.file.Files.readAllBytes(file.toPath());Set<String> before=cached();
        try{
            Uri uri=Uri.fromFile(file);
            try{ArchiveReadOnly.list(ctx,uri,"encrypted.rar",null);fail("Missing password accepted");}catch(Exception expected){}
            try{ArchiveReadOnly.list(ctx,uri,"encrypted.rar","wrong".toCharArray());fail("Wrong password accepted");}catch(Exception expected){}
            List<ArchiveReadOnly.Entry> entries=ArchiveReadOnly.list(ctx,uri,"encrypted.rar","junrar".toCharArray());
            assertEquals(1,entries.size());assertEquals("file1.txt",entries.get(0).name);
            assertEquals("file1\n",new String(ArchiveReadOnly.read(ctx,uri,"encrypted.rar","junrar".toCharArray(),"file1.txt"),StandardCharsets.UTF_8));
            assertArrayEquals(original,java.nio.file.Files.readAllBytes(file.toPath()));
        }finally{file.delete();assertEquals("No temporary RAR copy may remain",before,cached());}
    }
}
