package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic 7z import tests; never uses real user archives. */
@RunWith(AndroidJUnit4.class)
public class SevenZTelegramImportRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String HTML="<div class='history'><div class='message default' id='message1'><div class='body'><div class='from_name'>Synthetic Alice</div><div class='text'>Synthetic 7z chat</div></div></div></div>";
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private File directory(String key){return new File(new File(ctx.getFilesDir(),"telegram_imports"),key);}
    private File fixture(String name,char[] password)throws Exception{
        File plain=File.createTempFile("7z-plain-", ".html",ctx.getCacheDir());
        File archive=File.createTempFile("7z-import-", ".7z",ctx.getCacheDir());
        try{
            try(FileOutputStream out=new FileOutputStream(plain)){out.write(HTML.getBytes(StandardCharsets.UTF_8));}
            try(SevenZOutputFile writer=password==null?new SevenZOutputFile(archive):new SevenZOutputFile(archive,password)){
                SevenZArchiveEntry e=writer.createArchiveEntry(plain,name);
                writer.putArchiveEntry(e);writer.write(HTML.getBytes(StandardCharsets.UTF_8));writer.closeArchiveEntry();
                if(name.equals("Export/messages.html")){
                    SevenZArchiveEntry second=writer.createArchiveEntry(plain,"Export/messages2.html");
                    writer.putArchiveEntry(second);writer.write(HTML.replace("message1","message2").replace("Synthetic 7z chat","Second synthetic message").getBytes(StandardCharsets.UTF_8));writer.closeArchiveEntry();
                    SevenZArchiveEntry media=writer.createArchiveEntry(plain,"Export/photos/example.txt");
                    writer.putArchiveEntry(media);writer.write("Synthetic media".getBytes(StandardCharsets.UTF_8));writer.closeArchiveEntry();
                }
                writer.finish();
            }
            return archive;
        }catch(Exception e){archive.delete();throw e;}
        finally{plain.delete();}
    }
    private void verifyImport(char[] password)throws Exception{
        String key=key();File archive=fixture("Export/messages.html",password);
        long originalLength=archive.length();
        try{
            File root=ArchiveFileImport.extract(ctx,Uri.fromFile(archive),"7z",key,password,ignored->{});
            assertTrue(new File(root,"messages.html").isFile());
            MessageDb db=new MessageDb(ctx,key);
            try{
                int count=TelegramImporter.importFiles(ctx.getContentResolver(),
                    new DocumentFolder(ctx.getContentResolver(),root),db,ignored->{});
                assertEquals(2,count);assertEquals(2,db.totalMessages());
                assertTrue(new File(root,"photos/example.txt").isFile());
                assertTrue(db.page(0,1,"",false,"",false,false).get(0).body.contains("Synthetic 7z chat"));
            }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");}
            assertEquals(originalLength,archive.length());
        }finally{ArchiveFileImport.deleteTree(directory(key));archive.delete();}
    }
    @Test public void plainSevenZActuallyImportsTelegramHtml()throws Exception{verifyImport(null);}
    @Test public void plainSevenZWithEmptyPasswordImportsTelegramHtml()throws Exception{verifyImport(new char[0]);}
    @Test public void encryptedSevenZActuallyImportsTelegramHtml()throws Exception{
        char[] password="synthetic-7z-import".toCharArray();
        try{verifyImport(password);}finally{java.util.Arrays.fill(password,'\0');}
    }
    @Test public void wrongAndMissingPasswordRollbackWithoutTouchingPreviousImport()throws Exception{
        char[] password="synthetic-7z-import".toCharArray();
        File archive=fixture("messages.html",password);String old=key();File existing=directory(old);
        assertTrue(existing.mkdirs());File marker=new File(existing,"preserve.txt");
        try(FileOutputStream out=new FileOutputStream(marker)){out.write(42);}
        try{
            for(char[] wrong:new char[][]{null,"wrong-password".toCharArray()}){
                String next=key();
                try{ArchiveFileImport.extract(ctx,Uri.fromFile(archive),"7z",next,wrong,ignored->{});fail("Missing/wrong password accepted");}
                catch(Exception expected){assertFalse("Failed staging must be removed",directory(next).exists());}
            }
            assertTrue("Previous archive must not be removed",marker.isFile());
            assertTrue("Original must remain present",archive.isFile());
        }finally{java.util.Arrays.fill(password,'\0');archive.delete();ArchiveFileImport.deleteTree(existing);}
    }
    @Test public void parentTraversalFailsAndRollsBackStaging()throws Exception{
        File archive=fixture("../messages.html",null);String next=key();
        try{
            try{ArchiveFileImport.extract(ctx,Uri.fromFile(archive),"7z",next,null,ignored->{});fail("Traversal must be rejected");}
            catch(Exception expected){assertFalse(directory(next).exists());}
        }finally{archive.delete();ArchiveFileImport.deleteTree(directory(next));}
    }
}
