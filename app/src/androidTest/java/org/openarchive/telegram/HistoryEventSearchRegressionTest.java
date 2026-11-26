package org.openarchive.telegram;

import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** User notes remain attached while searching far back in a paginated chronology. */
@RunWith(AndroidJUnit4.class)
public final class HistoryEventSearchRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void set(MainActivity a,String name,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,v);
    }
    private void call(MainActivity a,String name)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);
    }
    private View find(View node,String label){
        if(node instanceof TextView&&label.equals(((TextView)node).getText().toString()))return node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                View found=find(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void findsOldCyrillicEventBeyondFirstPageAndKeepsOriginalNotes()throws Exception{
        MainActivity a=rule.getActivity();
        String key="historySearch"+UUID.randomUUID().toString().replace("-","");
        MessageDb source=new MessageDb(a,key);
        Object oldDb=field(a,"db"),oldHome=field(a,"home"),oldScreen=field(a,"screen");
        Object oldKey=field(a,"activeKey"),oldExpand=field(a,"historyEventsExpanded");
        try{
            SQLiteDatabase sql=source.getWritableDatabase();
            for(int i=0;i<86;i++){
                long msg=source.stage(sql,"QA","2026-11-10 10:00:00","Контрольная запись "+i,null,"");
            }
            source.publishStaged(sql);
            for(int i=0;i<86;i++){
                source.addRelationshipEvent(i==64?"Встреча с Машей":"Этап номер "+i,
                    i==64?"Важное событие в Москве":"Комментарий "+i,i+1,0);
            }
            assertEquals(86,source.relationshipEventCount());
            HistoryEventSearch.Result matches=HistoryEventSearch.find(source,"мАшЕй");
            assertEquals(1,matches.matches.size());
            assertEquals("Встреча с Машей",matches.matches.get(0).title);
            assertEquals(65,matches.matches.get(0).anchorId);
            assertFalse(matches.incomplete);
            assertEquals(1,HistoryEventSearch.find(source,"мОскВе").matches.size());
            assertEquals(86,source.relationshipEventCount());
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",source);set(a,"activeKey",key);
                    set(a,"home",false);set(a,"screen","relationships");
                    set(a,"historyEventsExpanded",false);
                    call(a,"showPage");
                    assertNotNull("Chronology needs a visible direct event search",
                        find(a.getWindow().getDecorView(),"Найти"));
                }catch(Exception ex){throw new AssertionError(ex);}
            });
        }finally{
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",oldDb);set(a,"home",oldHome);set(a,"screen",oldScreen);
                    set(a,"activeKey",oldKey);set(a,"historyEventsExpanded",oldExpand);
                    call(a,"showPage");
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            source.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void findsCyrillicEventBeyondThreeThousandWithoutTruncatingTheArchive()throws Exception{
        MainActivity a=rule.getActivity();
        String key="historyDeep"+UUID.randomUUID().toString().replace("-","");
        MessageDb source=new MessageDb(a,key);
        try{
            SQLiteDatabase sql=source.getWritableDatabase();
            long anchor=source.stage(sql,"QA","2026-11-10 10:00:00",
                "Исходное сообщение не меняется",null,"");
            source.publishStaged(sql);
            // Large, completely local fixture: one message, >3000 personal events.
            // A single SQLite transaction keeps this test fast even on Android 11.
            sql.beginTransaction();
            try{
                for(int i=0;i<3205;i++){
                    ContentValues row=new ContentValues();
                    row.put("title",i==3204?"Старое событие Ёжик":"Обычный эпизод "+i);
                    row.put("note",i==3204?"Редкая запись в конце истории":"Пустое описание");
                    row.put("anchor_id",anchor);
                    row.put("anchor_offset",0);
                    row.put("created_at",i);
                    sql.insertOrThrow("relationship_events",null,row);
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            assertEquals(3205,source.relationshipEventCount());
            HistoryEventSearch.Result found=HistoryEventSearch.find(source,"ёЖиК");
            assertEquals("A search must reach records after page 3000",1,found.matches.size());
            assertEquals("Старое событие Ёжик",found.matches.get(0).title);
            assertEquals(anchor,found.matches.get(0).anchorId);
            assertFalse("All events were examined",found.incomplete);
            assertEquals(3205,found.scanned);
        }finally{
            source.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }


    @Test public void exactlySixtyMatchesAreCompleteUntilAnAdditionalMatchExists()throws Exception{
        MainActivity a=rule.getActivity();
        String key="historycap"+UUID.randomUUID().toString().replace("-","");
        MessageDb source=new MessageDb(a,key);
        try{
            SQLiteDatabase sql=source.getWritableDatabase();
            long anchor=source.stage(sql,"QA","2026-11-10 10:00:00","Original",null,"");
            source.publishStaged(sql);
            sql.beginTransaction();
            try{
                for(int i=0;i<100;i++){
                    ContentValues row=new ContentValues();
                    row.put("title",i<60?"Встреча "+i:"Другой эпизод "+i);
                    row.put("note","");row.put("anchor_id",anchor);
                    row.put("anchor_offset",0);row.put("created_at",i);
                    sql.insertOrThrow("relationship_events",null,row);
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            HistoryEventSearch.Result exact=HistoryEventSearch.find(source,"ВСТРЕЧА");
            assertEquals(60,exact.matches.size());
            assertFalse("Unrelated events must not imply hidden matches",exact.incomplete);
            assertEquals(100,exact.scanned);
            source.addRelationshipEvent("Встреча дополнительная","",anchor,0);
            HistoryEventSearch.Result truncated=HistoryEventSearch.find(source,"встреча");
            assertEquals(60,truncated.matches.size());
            assertTrue("A real 61st hit must indicate truncation",truncated.incomplete);
            assertEquals(101,truncated.scanned);
            assertEquals(101,source.relationshipEventCount());
        }finally{
            source.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
}
