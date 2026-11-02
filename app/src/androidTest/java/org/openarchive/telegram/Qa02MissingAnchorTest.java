package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
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

/** A stale message anchor, as after a changed re-import, must use saved offset. */
@RunWith(AndroidJUnit4.class)
public class Qa02MissingAnchorTest {
    @Test public void missingAnchorUsesSavedReadingOffset()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String key="qa02missing_"+UUID.randomUUID().toString().replace("-","");
        String prefName=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(prefName.startsWith("."))prefName=prefName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(prefName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String oldRegistry=prefs.getString("archive_registry","[]"),oldActive=prefs.getString("active_archive","default");
        try{
            MessageDb db=new MessageDb(ctx,key);
            try{
                SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
                try{
                    for(int i=1;i<=100;i++)db.stage(sql,"Synthetic author","2026-01-01 12:00",
                        String.format(Locale.ROOT,"QA02_MISSING_%03d",i),null,"");
                    sql.setTransactionSuccessful();
                }finally{sql.endTransaction();}
                db.publishStaged(sql);
            }finally{db.close();}
            JSONArray archives=new JSONArray().put(new JSONObject().put("key",key)
                .put("name","QA02 Missing Anchor").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",archives.toString())
                .putString("active_archive",key).putLong("read_id_"+key,999999L)
                .putInt("read_offset_"+key,0).putInt("read_top_"+key,0).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                UiObject2 chat=device.wait(Until.findObject(By.textContains("QA02 Missing Anchor")),12000);
                assertNotNull("Chat card is missing",chat);chat.click();device.waitForIdle();
                assertNotNull("Missing anchor should fall back to saved offset at the start",
                    device.wait(Until.findObject(By.textContains("QA02_MISSING_001")),15000));
                assertNull("Missing anchor unexpectedly jumped to the last message",
                    device.findObject(By.textContains("QA02_MISSING_100")));
            }
        }finally{
            ctx.deleteDatabase("telegram_"+key+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",oldRegistry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",oldActive);else edit.remove("active_archive");
            edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key);
            assertTrue(edit.commit());
        }
    }
}
