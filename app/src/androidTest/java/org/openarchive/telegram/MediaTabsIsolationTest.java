package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.UUID;

/** Prevent mixed voice/music, round/video and sticker/photo filters. */
@RunWith(AndroidJUnit4.class)
public class MediaTabsIsolationTest {
 @Test public void eachCategoryContainsOnlyItsOwnType(){
   Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
   String key=UUID.randomUUID().toString().replace("-","");
   MessageDb helper=new MessageDb(ctx,key);
   try{
     SQLiteDatabase db=helper.getWritableDatabase();
     String[] kinds={"voice","audio","round","video","gif","sticker","image","file"};
     long id=1;
     for(String kind:kinds){db.execSQL("INSERT INTO messages (id,author,date_text,body) VALUES (?,?,?,?)",
         new Object[]{id,"Anonymous","2026-10-01T12:00:00","Synthetic"});
       db.execSQL("INSERT INTO attachments (id,message_id,path,kind) VALUES (?,?,?,?)",
         new Object[]{id,id,kind+"/sample-"+id+".bin",kind}); id++;}
     for(String kind:kinds){
       assertEquals("Wrong count for "+kind,1,helper.mediaCount(kind));
       assertEquals("Only one item belongs to "+kind,1,helper.mediaPage(kind,0,50).size());
       assertEquals(kind,helper.mediaPage(kind,0,50).get(0).kind);
     }
     assertEquals(1,helper.mediaCount("photos"));
     assertEquals(1,helper.mediaCount("videos"));
     assertEquals(8,helper.mediaCount("all"));
   }finally{helper.close();ctx.deleteDatabase("telegram_"+key+".db");}
 }
}
