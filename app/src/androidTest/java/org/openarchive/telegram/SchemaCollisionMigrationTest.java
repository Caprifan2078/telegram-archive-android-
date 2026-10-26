package org.openarchive.telegram;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;
/** Both historical schema-13 layouts must upgrade without losing either feature family. */
public class SchemaCollisionMigrationTest {
 @Test public void upgradeReliability13RetainsProfilesAndAddsReplies(){upgrade(true);}
 @Test public void upgradeReplies13RetainsRepliesAndAddsProfiles(){upgrade(false);}
 private void upgrade(boolean reliability){
  Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();String key=UUID.randomUUID().toString();MessageDb old=new MessageDb(ctx,key);
  try{
   SQLiteDatabase d=old.getWritableDatabase();long id=old.stage(d,"Автор","2026-01-01 12:00","Оригинал",null,"");old.publishStaged(d);old.toggleFavorite(id);
   if(reliability){
    old.saveLocalProfile("Подпись","@local","Заметка");old.saveCalendarPeriod(new CalendarPeriod(0,"Учёба","", "1990-01-01","1991-01-01"));
    for(String t:new String[]{"message_replies","telegram_ids","staged_reply_ids","staged_telegram_ids"})d.execSQL("DROP TABLE "+t);
   }else{
    d.execSQL("INSERT INTO telegram_ids VALUES(?,101)",new Object[]{id});d.execSQL("INSERT INTO message_replies VALUES(?,999,NULL)",new Object[]{id});
    d.execSQL("DROP TABLE local_profile");d.execSQL("DROP TABLE calendar_periods");
   }d.setVersion(13);
  }finally{old.close();}
  MessageDb migrated=new MessageDb(ctx,key);
  try{
   assertEquals(14,migrated.getReadableDatabase().getVersion());assertEquals(1,migrated.totalMessages());assertEquals(1,migrated.count("",false,"",false,true));
   if(reliability){assertEquals("@local",migrated.localProfile()[1]);assertEquals("1990-01-01",migrated.calendarPeriods().get(0).start);assertTrue(migrated.replyPreviews(migrated.page(0,10,"",false,"",false,false)).isEmpty());}
   else{assertEquals(1,migrated.replyPreviews(migrated.page(0,10,"",false,"",false,false)).size());assertTrue(migrated.calendarPeriods().isEmpty());assertEquals("",migrated.localProfile()[0]);}
  }finally{migrated.close();ctx.deleteDatabase("telegram_"+key+".db");}
 }
}
