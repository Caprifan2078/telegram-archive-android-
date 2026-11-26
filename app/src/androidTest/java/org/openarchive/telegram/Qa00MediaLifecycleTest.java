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
import androidx.test.uiautomator.StaleObjectException;
import android.os.SystemClock;
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
    private interface NodeAction<T>{T run(UiObject2 node);}
    private <T> T fresh(androidx.test.uiautomator.BySelector selector,String label,NodeAction<T> action){
        long end=SystemClock.uptimeMillis()+15000;
        StaleObjectException last=null;
        do{
            try{
                UiObject2 node=device.findObject(selector);
                if(node!=null)return action.run(node);
            }catch(StaleObjectException changed){last=changed;}
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing or unstable UI control: "+label,last);
    }
    private void tap(String text){
        // Resolve the current bounds, then tap once. Never retry a dispatched action.
        android.graphics.Rect bounds=fresh(By.text(text),text,UiObject2::getVisibleBounds);
        assertFalse("Control outside viewport: "+text,bounds.isEmpty());
        assertTrue("Tap failed: "+text,device.click(bounds.centerX(),bounds.centerY()));
        device.waitForIdle();
    }
    private void openMediaFromChat(){
        // The user-requested single chat header replaces the removed bottom
        // "Переписка / Медиа" tabs; do not restore a redundant tab for tests.
        UiObject2 heading=device.wait(
            Until.findObject(By.desc("Открыть медиатеку переписки")),15000);
        assertNotNull("Chat heading must open the gallery",heading);
        heading.click();device.waitForIdle();
    }
    private void openHelpFromCurrentScreen(ActivityScenario<MainActivity> scenario)throws Exception{
        // The chat drawer no longer overlaps Search or other tool screens.
        // This regression specifically checks state restoration from Help;
        // the separate menu-tour test covers visible drawer navigation.
        scenario.onActivity(a->{
            try{
                java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showHelp");
                show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError("Cannot open Help for restoration test",e);}
        });
        device.waitForIdle();
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
        Rect b=fresh(By.clazz("android.widget.ScrollView"),"chat viewport",UiObject2::getVisibleBounds);
        assertTrue("Chat viewport too small",b.height()>100);
        for(int i=0;i<4;i++){device.swipe(b.centerX(),b.top+b.height()*3/4,b.centerX(),b.top+b.height()/4,30);device.waitForIdle();}
        String marker=visibleMarker();
        assertFalse("No real scrolling occurred",marker.contains("_001"));
        return marker;
    }
    // Accessibility nodes can be replaced while asynchronous rows finish layout.
    // Reacquire fresh nodes within a bounded timeout; never skip viewport assertions.
    private String visibleMarker(){
        long end=SystemClock.uptimeMillis()+15000;
        do{
            try{
                UiObject2 area=device.findObject(By.clazz("android.widget.ScrollView"));
                if(area!=null){
                    Rect bounds=area.getVisibleBounds();
                    for(UiObject2 v:device.findObjects(By.textContains("QA_LIFE_"))){
                        Rect pos=v.getVisibleBounds();String marker=v.getText();
                        if(marker!=null&&marker.startsWith("QA_LIFE_")&&!pos.isEmpty()
                            &&pos.top>=bounds.top&&pos.bottom<=bounds.bottom)return marker;
                    }
                }
            }catch(StaleObjectException ignored){/* Re-query the current tree. */}
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("No synthetic message visible after scrolling");
    }
    private void requireRestoredMarker(String marker){
        long end=SystemClock.uptimeMillis()+15000;
        do{
            try{
                UiObject2 view=device.findObject(By.clazz("android.widget.ScrollView"));
                UiObject2 restored=device.findObject(By.text(marker));
                if(view!=null&&restored!=null){
                    Rect b=view.getVisibleBounds(),pos=restored.getVisibleBounds();
                    if(!pos.isEmpty()&&pos.top>=b.top&&pos.bottom<=b.bottom)return;
                }
            }catch(StaleObjectException ignored){/* Re-query the current tree. */}
            SystemClock.sleep(100);
        }while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Restored message missing or outside viewport: "+marker);
    }
    private void openPhotoFilter(){
        openMediaFromChat();tap("Фото");
        assertNotNull("Synthetic photo tile missing",device.wait(Until.findObject(By.descContains("Фото ·")),15000));
    }
    private void jumpFromPhotoTile(){
        // The modern RecyclerView gallery has one tile instead of a permanent
        // 44dp jump badge. The tile's long-press actions expose the source chat.
        UiObject2 tile=device.wait(Until.findObject(By.descContains("Фото ·")),15000);
        assertNotNull("Photo gallery tile missing",tile);
        tile.longClick();device.waitForIdle();
        UiObject2 jump=device.wait(Until.findObject(By.textContains("Перейти к сообщению")),10000);
        assertNotNull("Gallery must expose source-message navigation",jump);
        jump.click();device.waitForIdle();
    }
    private static Object field(MainActivity a,String name)throws ReflectiveOperationException{
        java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private static android.widget.TextView findExact(android.view.View root,String label){
        if(root instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)root).getText()))return (android.widget.TextView)root;
        if(root instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.TextView found=findExact(group.getChildAt(i),label);if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void searchResultsPositionAndQuerySurviveHelpRoundTrip()throws Exception{
        fixture(scenario->{
            tap("⋯");tap("Найти сообщение");
            fresh(By.desc("Поиск по сообщениям"),"Search input",node->{node.setText("QA");return true;});tap("Найти");
            assertNotNull("Synthetic results missing",device.wait(Until.findObject(By.textContains("QA_LIFE_")),15000));
            String marker=scrollToVisibleMarker();
            openHelpFromCurrentScreen(scenario);
            assertNotNull("Help did not open",device.wait(Until.findObject(By.text("СПРАВОЧНИК")),15000));
            device.pressBack();device.waitForIdle();
            requireRestoredMarker(marker);
            assertEquals("Search query changed","QA",fresh(By.desc("Поиск по сообщениям"),"restored search input",UiObject2::getText));
            // Independently inspect actual Views on the UI thread, not accessibility cache.
            // Verify the applied database query, input text AND exact visible source row.
            scenario.onActivity(a->{
                try{
                    assertEquals("search",field(a,"screen"));
                    assertEquals("QA",field(a,"q"));
                    android.widget.EditText input=(android.widget.EditText)field(a,"searchInput");
                    assertNotNull(input);assertTrue(input.isShown());assertEquals("QA",input.getText().toString());
                    android.widget.ScrollView scroll=(android.widget.ScrollView)field(a,"scroll");
                    assertNotNull(scroll);assertTrue(scroll.isShown());
                    android.widget.TextView row=findExact(scroll,marker);
                    assertNotNull("Same message must remain loaded",row);
                    Rect viewport=new Rect(),visible=new Rect();
                    assertTrue("Viewport must be visible",scroll.getGlobalVisibleRect(viewport));
                    assertTrue("Restored message must be visible",row.getGlobalVisibleRect(visible));
                    assertEquals("Restored message must not be clipped vertically",row.getHeight(),visible.height());
                    assertTrue("Restored message outside viewport",visible.top>=viewport.top&&visible.bottom<=viewport.bottom);
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
        });
    }
    @Test public void helpRoundTripPreservesScrolledChatPosition()throws Exception{
        fixture(scenario->{
            String marker=scrollToVisibleMarker();
            openHelpFromCurrentScreen(scenario);
            assertNotNull("Help did not open",device.wait(Until.findObject(By.text("СПРАВОЧНИК")),15000));
            device.pressBack();device.waitForIdle();
            requireRestoredMarker(marker);
        });
    }
    @Test public void recreationInMediaRestoresPreviousChatPosition()throws Exception{
        fixture(scenario->{
            String marker=scrollToVisibleMarker();openPhotoFilter();
            scenario.recreate();device.waitForIdle();
            assertNotNull("Media page lost after recreation",device.wait(Until.findObject(By.descContains("Фото ·")),15000));
            tap("‹");
            assertNotNull("Chat position lost after recreating media: "+marker,
                device.wait(Until.findObject(By.text(marker)),15000));
        });
    }
    @Test public void explicitMediaJumpWinsOverSavedChatPosition()throws Exception{
        fixture(scenario->{
            scrollToVisibleMarker();openPhotoFilter();
            jumpFromPhotoTile();
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
                marker=visibleMarker();
            }
            openPhotoFilter();
            final long[] saved={-1};
            scenario.onActivity(a->{
                try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("pendingRestoreId");
                    f.setAccessible(true);saved[0]=f.getLong(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
            // The updated chat layout can save a different old reading
            // anchor depending on screen height and dynamic media rows.
            // Force an older valid position to verify jump priority itself.
            scenario.onActivity(a->{
                try{
                    java.lang.reflect.Field f=MainActivity.class.getDeclaredField("pendingRestoreId");
                    f.setAccessible(true);f.setLong(a,20L);
                }catch(Exception e){throw new AssertionError(e);}
            });
            jumpFromPhotoTile();
            UiObject2 target=device.wait(Until.findObject(By.text("QA_LIFE_026")),15000);
            assertNotNull("Old anchor overrode explicit jump",target);
            UiObject2 chatViewport=device.wait(Until.findObject(By.clazz("android.widget.ScrollView")),15000);
            assertNotNull(chatViewport);Rect bounds=chatViewport.getVisibleBounds(),pos=target.getVisibleBounds();
            assertTrue("Jump target is not in upper half of chat viewport: "+pos,
                !pos.isEmpty()&&pos.top>=bounds.top&&pos.top<bounds.centerY());
        });
    }
}
