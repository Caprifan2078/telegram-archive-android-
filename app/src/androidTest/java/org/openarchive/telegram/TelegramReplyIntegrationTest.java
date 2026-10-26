package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.*;

/** Only synthetic Telegram HTML; checks different numbered files and safe migration. */
@RunWith(AndroidJUnit4.class)
public class TelegramReplyIntegrationTest {
 @Test public void crossFileRepliesResolveOnlyRealTelegramIds() throws Exception {
  Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
  String key="replytest_"+UUID.randomUUID().toString().replace("-","");
  File folder=new File(ctx.getCacheDir(),"reply-html-"+key);assertTrue(folder.mkdirs());
  write(new File(folder,"messages.html"),
   "<div class='history'>"+
   "<div class='message default' id='message101'><div class='body'><div class='text'>Исходное сообщение</div></div></div>"+
   "<div class='message default' id='message202'><div class='body'><div class='reply_to details'>In reply to <a href='messages2.html#go_to_message303'>this message</a></div><div class='text'>Ответ из первой части</div></div></div>"+
   "</div>");
  write(new File(folder,"messages2.html"),
   "<div class='history'>"+
   "<div class='message default' id='message303'><div class='body'><div class='from_name'>Никита</div><div class='text'>Оригинал из второй части</div></div></div>"+
   "<div class='message default' id='message404'><div class='body'><div class='reply_to details'><a href='#go_to_message999'>missing</a></div><div class='text'>Ответ без оригинала</div></div></div>"+
   "</div>");
  MessageDb db=new MessageDb(ctx,key);
  try {
   assertEquals(4,TelegramImporter.importFiles(ctx.getContentResolver(),new DocumentFolder(ctx.getContentResolver(),folder),db,x->{}));
   List<MessageDb.Item> items=db.page(0,10,"",false,"",false,false);
   assertEquals(4,items.size());
   java.util.Map<Long,MessageDb.ReplyInfo> replies=db.replyPreviews(items);
   assertEquals(2,replies.size());
   assertEquals(items.get(2).id,replies.get(items.get(1).id).targetId);
   assertEquals(303,replies.get(items.get(1).id).telegramId);
   assertEquals("Никита",replies.get(items.get(1).id).author);
   assertTrue(replies.get(items.get(1).id).preview.contains("Оригинал"));
   assertEquals(-1,replies.get(items.get(3).id).targetId);
   assertEquals(999,replies.get(items.get(3).id).telegramId);
   SQLiteDatabase sql=db.getReadableDatabase();
   try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM telegram_ids",null)){
    assertTrue(c.moveToFirst());assertEquals(4,c.getInt(0));
   }
  }finally{db.close();ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(folder);}
 }
 @Test public void migrateExistingDbWithoutChangingUserMessages() {
  Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
  String key="replymig_"+UUID.randomUUID().toString().replace("-","");
  MessageDb before=new MessageDb(ctx,key);
  try{
   SQLiteDatabase d=before.getWritableDatabase();
   d.execSQL("INSERT INTO messages(id,author,date_text,body) VALUES(7,'A','2023-01-01','Привет')");
   d.execSQL("INSERT INTO favorites(message_id) VALUES(7)");
   d.execSQL("DROP TABLE message_replies");d.execSQL("DROP TABLE telegram_ids");
   d.execSQL("DROP TABLE staged_reply_ids");d.execSQL("DROP TABLE staged_telegram_ids");
   d.setVersion(12);
  }finally{before.close();}
  MessageDb after=new MessageDb(ctx,key);
  try{
   assertEquals(1,after.totalMessages());
   assertEquals(1,after.count("",false,"",false,true));
   assertTrue(after.replyPreviews(after.page(0,10,"",false,"",false,false)).isEmpty());
  }finally{after.close();ctx.deleteDatabase("telegram_"+key+".db");}
 }
 private static void write(File file,String html)throws Exception{
  try(FileOutputStream out=new FileOutputStream(file)){out.write(html.getBytes(StandardCharsets.UTF_8));}
 }
}
