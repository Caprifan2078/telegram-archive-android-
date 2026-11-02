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

/** Isolated archives with distinct saved anchors; navigation uses real UI taps. */
@RunWith(AndroidJUnit4.class)
public class Qa01ReadingPositionTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    private static void seed(Context context,String key,String prefix){
        MessageDb db=new MessageDb(context,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();sql.beginTransaction();
            try{
                for(int i=1;i<=100;i++)db.stage(sql,"Synthetic author","2026-01-01 12:00",
                    String.format(Locale.ROOT,"QA01_%s_%03d",prefix,i),null,"");
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            db.publishStaged(sql);
        }finally{db.close();}
    }
    private void tap(String label){
        UiObject2 object=device.wait(Until.findObject(By.textContains(label)),12000);
        assertNotNull("Cannot find "+label,object);object.click();device.waitForIdle();
    }
    private void message(String marker){
        assertNotNull("Expected saved reading position near "+marker,
            device.wait(Until.findObject(By.textContains(marker)),15000));
    }
    @Test public void switchingChatsRestoresEachSavedAnchor()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String a="read_a_"+suffix,b="read_b_"+suffix;
        String name=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(name.startsWith("."))name=name.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(name,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String oldRegistry=prefs.getString("archive_registry","[]"),oldActive=prefs.getString("active_archive","default");
        try{
            seed(ctx,a,"A");seed(ctx,b,"B");
            JSONArray entries=new JSONArray()
                .put(new JSONObject().put("key",a).put("name","QA01 Reading Alpha").put("tree",""))
                .put(new JSONObject().put("key",b).put("name","QA01 Reading Beta").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",entries.toString()).putString("active_archive",a)
                .putLong("read_id_"+a,20).putInt("read_top_"+a,0)
                .putLong("read_id_"+b,75).putInt("read_top_"+b,0).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                tap("QA01 Reading Alpha");message("QA01_A_020");
                assertNull("Foreign message visible in alpha",device.findObject(By.textContains("QA01_B_")));
                device.pressBack();device.waitForIdle();
                assertEquals("Beta anchor modified while reading alpha",75,prefs.getLong("read_id_"+b,-1));
                tap("QA01 Reading Beta");message("QA01_B_075");
                assertNull("Foreign message visible in beta",device.findObject(By.textContains("QA01_A_")));
                scenario.recreate();device.waitForIdle();message("QA01_B_075");
                device.pressBack();device.waitForIdle();
                tap("QA01 Reading Alpha");message("QA01_A_020");
            }
        }finally{
            ctx.deleteDatabase("telegram_"+a+".db");ctx.deleteDatabase("telegram_"+b+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",oldRegistry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",oldActive);else edit.remove("active_archive");
            for(String key:new String[]{a,b})edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key).remove("pinned_chat_"+key);
            assertTrue(edit.commit());
        }
    }
}
