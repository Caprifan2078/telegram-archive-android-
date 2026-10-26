package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

/** Full restore regression with synthetic files only; doesn't touch user's archive. */
@RunWith(AndroidJUnit4.class)
public class PortableRestoreRegressionTest {
    @Test public void preservesQuotesNotesAttachmentsPeriodsAndReadMarkers()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=UUID.randomUUID().toString().replace("-","");
        SharedPreferences prefs=ctx.getSharedPreferences("portable-test-"+key,Context.MODE_PRIVATE);
        File src=new File(ctx.getCacheDir(),"portable-"+key);
        assertTrue(src.mkdirs());
        File html=new File(src,"messages.html");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(html),"UTF-8")){
            w.write("<div class='history'><div class='message default'><div class='body'>"+
                "<div class='from_name'>Example</div><div class='text'>An example quote</div></div></div></div>");
        }
        File noteAttachment=new File(src,"private-note.txt");
        byte[] payload="OFFLINE PORTABLE PERSONAL NOTE".getBytes("UTF-8");
        try(FileOutputStream out=new FileOutputStream(noteAttachment)){out.write(payload);}
        MessageDb chat=new MessageDb(ctx,key);
        SavedStore notes=new SavedStore(ctx);
        File zip=new File(ctx.getCacheDir(),"portable-backup-"+key+".zip");
        String recoveredKey=null;List<Long> insertedNotes=new ArrayList<>();File restoredFolder=null;
        try{
            TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),src),chat,text->{});
            long messageId=chat.page(0,1,"",false,"",false,false).get(0).id;
            chat.toggleFavorite(messageId);chat.togglePinned(messageId);
            chat.addPeriod("Первая встреча","Обсудили будущий проект",
                new ConversationPeriods.Boundary(messageId,0),new ConversationPeriods.Boundary(messageId,4));
            chat.saveLocalProfile("Локальное имя","@training","Не отправлять в Telegram");
            chat.saveCalendarPeriod(new CalendarPeriod(0,"До переписки","Учебный период","1990-01-01","1991-01-01"));
            chat.addRelationshipEvent("Событие","Важный момент",messageId,1);
            long quoteId=notes.addQuote("❝ sample quote — Example",key,messageId);
            long attachmentId=notes.add("Attached note","private-note.txt",Uri.fromFile(noteAttachment).toString(),"text/plain");
            insertedNotes.add(quoteId);insertedNotes.add(attachmentId);
            long originalCreated=0;
            for(SavedStore.Note note:notes.recent(200))if(note.id==quoteId)originalCreated=note.created;
            assertTrue(originalCreated>0);
            JSONArray registry=new JSONArray();registry.put(new JSONObject().put("name","Test").put("key",key).put("tree","local:"+src.getAbsolutePath()));
            assertTrue(prefs.edit().putString("archive_registry",registry.toString())
                .putBoolean("pinned_chat_"+key,true)
                .putLong("read_id_"+key,messageId).putInt("read_top_"+key,42)
                .putInt("read_offset_"+key,123).commit());
            try(OutputStream out=new FileOutputStream(zip)){FullBackup.export(ctx,prefs,out,s->{});}
            try(InputStream in=new FileInputStream(zip)){
                BackupVerifier.Result check=BackupVerifier.verifyStream(in,s->{});
                assertTrue(check.full);assertTrue(check.files>=4);
            }
            assertEquals(1,FullBackup.restore(ctx,prefs,Uri.fromFile(zip),s->{}));
            JSONArray result=new JSONArray(prefs.getString("archive_registry","[]"));
            recoveredKey=result.getJSONObject(result.length()-1).getString("key");
            restoredFolder=new File(result.getJSONObject(result.length()-1).getString("tree").substring(6));
            assertTrue(prefs.getBoolean("pinned_chat_"+recoveredKey,false));
            assertEquals(messageId,prefs.getLong("read_id_"+recoveredKey,-1));
            assertEquals(42,prefs.getInt("read_top_"+recoveredKey,-1));
            assertEquals(123,prefs.getInt("read_offset_"+recoveredKey,-1));
            MessageDb copy=new MessageDb(ctx,recoveredKey);
            try{
                assertEquals(1,copy.totalMessages());
                assertEquals(1,copy.count("",false,"",false,true));
                assertEquals(1,copy.pinned().size());
                assertEquals("Первая встреча",copy.periods().get(0).name);
                assertEquals(1,copy.relationshipEvents().size());
                assertEquals("@training",copy.localProfile()[1]);
                assertTrue(copy.replyPreviews(copy.page(0,10,"",false,"",false,false)).isEmpty());
                assertEquals("1990-01-01",copy.calendarPeriods().get(0).start);
            }finally{copy.close();}
            boolean quoteRestored=false,attachmentRestored=false;
            for(SavedStore.Note note:notes.recent(200)){
                if(note.id==quoteId||note.id==attachmentId)continue;
                if("❝ sample quote — Example".equals(note.body) && recoveredKey.equals(note.originArchive)){
                    assertEquals(messageId,note.originMessageId);
                    assertEquals(originalCreated,note.created);
                    quoteRestored=true;insertedNotes.add(note.id);
                }
                if("Attached note".equals(note.body)&&note.fileId!=null&&
                   note.fileId.startsWith(PortableNoteFiles.PREFIX)){
                    Uri restored=PortableNoteFiles.openUri(ctx,note.fileId);
                    try(InputStream stream=ctx.getContentResolver().openInputStream(restored)){
                        byte[] check=new byte[payload.length];assertEquals(check.length,stream.read(check));
                        assertArrayEquals(payload,check);
                    }
                    attachmentRestored=true;insertedNotes.add(note.id);
                }
            }
            assertTrue("Quote anchor must be rekeyed",quoteRestored);
            assertTrue("Note attachment must be portable",attachmentRestored);
        }finally{
            for(Long id:insertedNotes)notes.delete(id);
            notes.close();chat.close();ctx.deleteDatabase("telegram_"+key+".db");
            if(recoveredKey!=null)ctx.deleteDatabase("telegram_"+recoveredKey+".db");
            prefs.edit().clear().commit();
            if(restoredFolder!=null){File stage=restoredFolder.getParentFile().getParentFile();
                if(stage.getName().startsWith("restore-"))ArchiveFileImport.deleteTree(stage);}
            ArchiveFileImport.deleteTree(src);zip.delete();
        }
    }
}
