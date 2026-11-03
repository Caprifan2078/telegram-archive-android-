package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Locale;
import java.util.UUID;
import static org.junit.Assert.*;

/** Real taps and an isolated synthetic archive; no user files are accessed. */
@RunWith(AndroidJUnit4.class)
public class Qa00MediaLifecycleTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private interface ScenarioCheck { void run(ActivityScenario<MainActivity> scenario)throws Exception; }
    private void tap(String text){
        UiObject2 v=device.wait(Until.findObject(By.text(text)),15000);
        assertNotNull("Missing UI control: "+text,v);v.click();device.waitForIdle();
    }
    private void fixture(ScenarioCheck check)throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qa00life_"+UUID.randomUUID().toString().replace("-","");
        String prefsName=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(prefsName.startsWith("."))prefsName=prefsName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        try{
            MessageDb db=new MessageDb(ctx,key);
            try{
                SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
                try{
                    for(int i=1;i<=100;i++){
                        long id=db.stage(sql,"Synthetic","2026-01-01 12:00",String.format(Locale.ROOT,"QA_LIFE_%03d",i),null,"");
                        if(i==26)db.stageAttachment(sql,id,"photos/synthetic.jpg","image");
                    }
                    sql.setTransactionSuccessful();
                }finally{sql.endTransaction();}
                db.publishStaged(sql);
            }finally{db.close();}
            JSONArray entries=new JSONArray().put(new JSONObject().put("key",key).put("name","QA lifecycle chat").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",entries.toString()).putString("active_archive",key).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                tap("QA lifecycle chat");
                assertNotNull(device.wait(Until.findObject(By.textContains("QA_LIFE_")),15000));
                check.run(scenario);
            }
        }finally{
            ctx.deleteDatabase("telegram_"+key+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key).remove("pinned_chat_"+key);
            assertTrue(edit.commit());
        }
    }
    private String scrollToVisibleMarker(){
        UiObject2 area=device.wait(Until.findObject(By.clazz("android.widget.ScrollView")),15000);
        assertNotNull("Chat viewport missing",area);Rect b=area.getVisibleBounds();
        assertTrue("Chat viewport too small",b.height()>100);
        for(int i=0;i<4;i++){device.swipe(b.centerX(),b.top+b.height()*3/4,b.centerX(),b.top+b.height()/4,30);device.waitForIdle();}
        for(UiObject2 v:device.findObjects(By.textContains("QA_LIFE_"))){
            Rect pos=v.getVisibleBounds();
            if(!pos.isEmpty()&&pos.top>=b.top&&pos.bottom<=b.bottom){
                String marker=v.getText();assertFalse("No real scrolling occurred",marker.contains("_001"));return marker;
            }
        }
        throw new AssertionError("No synthetic message visible after scrolling");
    }
    private void openPhotoFilter(){
        tap("▦ Медиа");tap("Фото");
        assertNotNull("Synthetic photo tile missing",device.wait(Until.findObject(By.descContains("Фото ·")),15000));
    }
    @Test public void recreationInMediaRestoresPreviousChatPosition()throws Exception{
        fixture(scenario->{
            String marker=scrollToVisibleMarker();openPhotoFilter();
            scenario.recreate();device.waitForIdle();
            assertNotNull("Media page lost after recreation",device.wait(Until.findObject(By.descContains("Фото ·")),15000));
            tap("💬 Переписка");
            assertNotNull("Chat position lost after recreating media: "+marker,
                device.wait(Until.findObject(By.text(marker)),15000));
        });
    }
    @Test public void explicitMediaJumpWinsOverSavedChatPosition()throws Exception{
        fixture(scenario->{
            scrollToVisibleMarker();openPhotoFilter();
            UiObject2 go=device.wait(Until.findObject(By.desc("К исходному сообщению")),15000);
            assertNotNull("Jump control missing",go);go.click();device.waitForIdle();
            assertNotNull("Did not jump to the photo message",
                device.wait(Until.findObject(By.text("QA_LIFE_026")),15000));
        });
    }
    @Test public void nearbyMediaJumpIsNotOverriddenByOldReadingPosition()throws Exception{
        fixture(scenario->{
            String marker=scrollToVisibleMarker();
            UiObject2 viewport=device.wait(Until.findObject(By.clazz("android.widget.ScrollView")),15000);
            assertNotNull(viewport);Rect area=viewport.getVisibleBounds();
            // The photo belongs to #26; jump loads from #19. Place the old
            // anchor within that same loaded window, but above the target.
            for(int attempt=0;attempt<12;attempt++){
                int number=Integer.parseInt(marker.substring("QA_LIFE_".length()));
                if(number>=19)break;
                device.swipe(area.centerX(),area.top+area.height()*2/3,
                    area.centerX(),area.top+area.height()/2,35);
                device.waitForIdle();
                for(UiObject2 v:device.findObjects(By.textContains("QA_LIFE_"))){
                    Rect pos=v.getVisibleBounds();
                    if(!pos.isEmpty()&&pos.top>=area.top&&pos.bottom<=area.bottom){marker=v.getText();break;}
                }
            }
            openPhotoFilter();
            final long[] saved={-1};
            scenario.onActivity(a->{
                try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("pendingRestoreId");
                    f.setAccessible(true);saved[0]=f.getLong(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
            assertTrue("Old anchor must be in same page and sufficiently far from target: "+saved[0],
                saved[0]>=19&&saved[0]<=21);
            UiObject2 go=device.wait(Until.findObject(By.desc("К исходному сообщению")),15000);
            assertNotNull(go);go.click();device.waitForIdle();
            UiObject2 target=device.wait(Until.findObject(By.text("QA_LIFE_026")),15000);
            assertNotNull("Old anchor overrode explicit jump",target);
            UiObject2 chatViewport=device.wait(Until.findObject(By.clazz("android.widget.ScrollView")),15000);
            assertNotNull(chatViewport);Rect bounds=chatViewport.getVisibleBounds(),pos=target.getVisibleBounds();
            assertTrue("Jump target is not in upper half of chat viewport: "+pos,
                !pos.isEmpty()&&pos.top>=bounds.top&&pos.top<bounds.centerY());
        });
    }
}
