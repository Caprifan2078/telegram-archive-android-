package org.openarchive.telegram;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.Assert.*;
public class LocalProfileMigrationTest {
 @Test public void migrationPreservesOldDataAndIsolatesLocalFields()throws Exception{
  Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();String key=UUID.randomUUID().toString();String other=UUID.randomUUID().toString();
  MessageDb db=new MessageDb(ctx,key);File backup=new File(ctx.getCacheDir(),key+".before.sqlite");
  try{
   SQLiteDatabase sql=db.getWritableDatabase();long id=db.stage(sql,"Аня","2026-01-01 12:00","Оригинал",null,"");db.publishStaged(sql);db.toggleFavorite(id);
   db.addPeriod("Старый этап","",new ConversationPeriods.Boundary(id,0),new ConversationPeriods.Boundary(id,1));
   sql.execSQL("DROP TABLE calendar_periods");sql.execSQL("DROP TABLE local_profile");sql.setVersion(12);db.close();
   Files.copy(ctx.getDatabasePath("telegram_"+key+".db").toPath(),backup.toPath());
   db=new MessageDb(ctx,key);assertEquals(1,db.totalMessages());assertEquals(1,db.periods().size());assertEquals(1,db.count("",false,"",false,true));
   db.saveLocalProfile("Мой друг","@local","Заметка");long period=db.saveCalendarPeriod(new CalendarPeriod(0,"Детство","", "1990-01-01","1991-01-01"));
   db.saveCalendarPeriod(new CalendarPeriod(period,"Учёба","Описание","1992-01-01","1993-01-01"));
   assertEquals("Учёба",db.calendarPeriods().get(0).name);assertEquals("Оригинал",db.page(0,1,"",false,"",false,false).get(0).body);
   MessageDb second=new MessageDb(ctx,other);try{assertEquals("",second.localProfile()[0]);assertTrue(second.calendarPeriods().isEmpty());}finally{second.close();}
   db.deleteCalendarPeriod(period);assertTrue(db.calendarPeriods().isEmpty());assertEquals(1,db.totalMessages());assertTrue(backup.length()>0);
  }finally{db.close();backup.delete();ctx.deleteDatabase("telegram_"+key+".db");ctx.deleteDatabase("telegram_"+other+".db");}
 }
}
