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
import java.util.UUID;
import static org.junit.Assert.*;

/** User-facing help navigation on a disposable synthetic chat. */
@RunWith(AndroidJUnit4.class)
public class Qa00HelpLifecycleTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private void tap(String text){
        UiObject2 v=device.wait(Until.findObject(By.textContains(text)),15000);
        assertNotNull("Missing control "+text,v);v.click();device.waitForIdle();
    }
    private void shown(String text){assertNotNull("Missing "+text,device.wait(Until.findObject(By.textContains(text)),15000));}
    private void scrollDrawerToHelp(){
        for(int i=0;i<10;i++){
            UiObject2 item=device.findObject(By.textContains("Помощь и инструкция"));
            if(item!=null&&!item.getVisibleBounds().isEmpty()){item.click();device.waitForIdle();return;}
            UiObject2 scroll=device.findObject(By.clazz("android.widget.ScrollView"));
            assertNotNull("Drawer scroll area missing",scroll);Rect b=scroll.getVisibleBounds();
            device.swipe(b.centerX(),b.top+b.height()*3/4,b.centerX(),b.top+b.height()/4,25);device.waitForIdle();
        }
        fail("Help entry unavailable in drawer");
    }
    @Test public void recreationKeepsHelpChapterAndReturnToOriginalChat()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key="qahelp_"+UUID.randomUUID().toString().replace("-","");
        String prefsName=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(prefsName.startsWith("."))prefsName=prefsName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        try{
            MessageDb db=new MessageDb(ctx,key);
            try{SQLiteDatabase sql=db.getWritableDatabase();db.stage(sql,"Synthetic","2026-01-01 12:00","QA help message",null,"");db.publishStaged(sql);}
            finally{db.close();}
            JSONArray chats=new JSONArray().put(new JSONObject().put("key",key).put("name","QA Help Lifecycle").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString()).putString("active_archive",key).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                tap("QA Help Lifecycle");shown("QA help message");
                tap("≡");tap("Все инструменты");scrollDrawerToHelp();shown("СПРАВОЧНИК");
                tap("Первые шаги");tap("Быстрый старт: первые три шага");shown("ИНСТРУКЦИЯ");
                scenario.recreate();device.waitForIdle();shown("ИНСТРУКЦИЯ");
                device.pressBack();device.waitForIdle();shown("РАЗДЕЛ");
                device.pressBack();device.waitForIdle();shown("СПРАВОЧНИК");
                device.pressBack();device.waitForIdle();shown("QA Help Lifecycle");shown("QA help message");
            }
        }finally{
            ctx.deleteDatabase("telegram_"+key+".db");SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key).remove("pinned_chat_"+key);
            assertTrue(edit.commit());
        }
    }
}
