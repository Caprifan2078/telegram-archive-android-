package org.openarchive.telegram;

import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
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
}
