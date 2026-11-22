package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Reproduces a multi-file Telegram HTML export with real 7z/ZIP directory entries.
 * Earlier tiny fixtures containing only messages.html didn't test real directory headers.
 */
@RunWith(AndroidJUnit4.class)
public final class RealTelegramExportStructureRegressionTest {
    private final Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final String[] DIRS={
        "css","images","js","photos","round_video_messages","stickers",
        "video_files","voice_messages"
    };
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private File staging(String key){return new File(new File(ctx.getFilesDir(),"telegram_imports"),key);}
    private byte[] html(int number){
        return ("<!DOCTYPE html><html><body><div class='history'>"+
            "<div class='message default' id='message"+number+"'><div class='body'>"+
            "<div class='from_name'>Synthetic sender</div>"+
            "<div class='text'>Part "+number+"</div>"+
            "</div></div></div></body></html>").getBytes(StandardCharsets.UTF_8);
    }
    private File makeZip()throws Exception{
        File zip=File.createTempFile("telegram-real-structure-", ".zip",ctx.getCacheDir());
        try(ZipOutputStream writer=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)))){
            writer.putNextEntry(new ZipEntry("TelegramExport/"));writer.closeEntry();
            for(String dir:DIRS){
                writer.putNextEntry(new ZipEntry("TelegramExport/"+dir+"/"));
                writer.closeEntry();
            }
            for(int i=1;i<=20;i++){
                writer.putNextEntry(new ZipEntry("TelegramExport/messages"+(i==1?"":i)+".html"));
                writer.write(html(i));writer.closeEntry();
            }
            writer.putNextEntry(new ZipEntry("TelegramExport/round_video_messages/round.mp4"));
            writer.write(new byte[]{0,1,2,3});writer.closeEntry();
        }
        return zip;
    }
    private File makeSevenZ()throws Exception{
        File root=new File(ctx.getCacheDir(),"seven-real-"+key());
        assertTrue(root.mkdirs());
        File file=File.createTempFile("telegram-real-structure-", ".7z",ctx.getCacheDir());
        File content=File.createTempFile("telegram-real-content-", ".html",ctx.getCacheDir());
        try(SevenZOutputFile writer=new SevenZOutputFile(file)){
            // SevenZ directory entries often have no trailing slash. This is valid.
            File exportDir=new File(root,"TelegramExport");
            assertTrue(exportDir.mkdir());
            SevenZArchiveEntry top=writer.createArchiveEntry(exportDir,"TelegramExport");
            writer.putArchiveEntry(top);writer.closeArchiveEntry();
            for(String dir:DIRS){
                File directory=new File(exportDir,dir);
                assertTrue(directory.mkdir());
                SevenZArchiveEntry folder=writer.createArchiveEntry(directory,"TelegramExport/"+dir);
                writer.putArchiveEntry(folder);writer.closeArchiveEntry();
            }
            for(int i=1;i<=20;i++){
                byte[] data=html(i);
                try(FileOutputStream out=new FileOutputStream(content)){out.write(data);}
                SevenZArchiveEntry entry=writer.createArchiveEntry(content,"TelegramExport/messages"+(i==1?"":i)+".html");
                writer.putArchiveEntry(entry);writer.write(data);writer.closeArchiveEntry();
            }
            byte[] video=new byte[]{0,1,2,3};
            try(FileOutputStream out=new FileOutputStream(content)){out.write(video);}
            SevenZArchiveEntry entry=writer.createArchiveEntry(content,"TelegramExport/round_video_messages/round.mp4");
            writer.putArchiveEntry(entry);writer.write(video);writer.closeArchiveEntry();
            writer.finish();
        }finally{content.delete();ArchiveFileImport.deleteTree(root);}
        return file;
    }
    private void verifyImport(File archive,String format)throws Exception{
        String id=key();File dir=staging(id);
        try{
            File selected=ArchiveFileImport.extract(ctx,Uri.fromFile(archive),format,id,null,ignored->{});
            assertEquals("TelegramExport",selected.getName());
            assertTrue(new File(selected,"messages.html").isFile());
            assertTrue(new File(selected,"messages20.html").isFile());
            assertTrue(new File(selected,"round_video_messages/round.mp4").isFile());
            MessageDb db=new MessageDb(ctx,id);
            try{
                assertEquals("All 20 HTML segments must import in number order",20,
                    TelegramImporter.importFiles(ctx.getContentResolver(),
                        new DocumentFolder(ctx.getContentResolver(),selected),db,ignored->{}));
                assertEquals(20,db.totalMessages());
            }finally{db.close();ctx.deleteDatabase("telegram_"+id+".db");}
        }finally{ArchiveFileImport.deleteTree(dir);archive.delete();}
    }
    @Test public void sevenZipDirectoriesWithoutTrailingSlashImport()throws Exception{
        verifyImport(makeSevenZ(),"7z");
    }
    @Test public void ordinaryZipDirectoriesAndTwentyHtmlFilesImport()throws Exception{
        verifyImport(makeZip(),"zip");
    }
}
