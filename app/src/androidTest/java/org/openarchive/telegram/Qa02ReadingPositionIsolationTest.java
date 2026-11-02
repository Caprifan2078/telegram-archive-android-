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
import java.util.UUID;
import static org.junit.Assert.*;

/** QA-02: reading progress must be scoped to the chat that actually rendered
 * the message. A non-message placeholder in another chat must never inherit
 * the previous chat's message ID. All fixtures are disposable and synthetic.
 */
@RunWith(AndroidJUnit4.class)
public class Qa02ReadingPositionIsolationTest {
    private final UiDevice device=UiDevice.getInstance(
        InstrumentationRegistry.getInstrumentation());

    private void visible(String value) {
        assertNotNull("Not visible: "+value,
            device.wait(Until.findObject(By.textContains(value)),12000));
    }
    private void click(String value){
        UiObject2 v=device.wait(Until.findObject(By.textContains(value)),10000);
        assertNotNull("No control: "+value,v);
        assertFalse("Control outside viewport: "+value,v.getVisibleBounds().isEmpty());
        v.click();
        device.waitForIdle();
    }
    private static String preferenceName(Context ctx){
        String name=new ComponentName(ctx,MainActivity.class).getShortClassName();
        return name.startsWith(".")?name.substring(1):name;
    }
    private static void seed(Context ctx,String key,String body){
        MessageDb db=new MessageDb(ctx,key);
        try{
            SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            try {
                db.stage(sql,"QA02 author","2024-06-01 12:00",body,null,"");
                sql.setTransactionSuccessful();
            } finally {sql.endTransaction();}
            db.publishStaged(sql);
        } finally {db.close();}
    }
    @Test public void emptySecondChatNeverInheritsFirstChatsReadingId()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String a="qa02_read_a_"+suffix,b="qa02_read_b_"+suffix;
        SharedPreferences pref=ctx.getSharedPreferences(preferenceName(ctx),Context.MODE_PRIVATE);
        boolean oldRegistryExists=pref.contains("archive_registry"),
            oldActiveExists=pref.contains("active_archive");
        String oldRegistry=pref.getString("archive_registry","[]"),
            oldActive=pref.getString("active_archive","default");
        try{
            seed(ctx,a,"QA02_ALPHA_ONLY");
            // B intentionally has no messages; it shows the empty-state label.
            JSONArray chats=new JSONArray()
                .put(new JSONObject().put("key",a).put("name","QA02 Альфа").put("tree",""))
                .put(new JSONObject().put("key",b).put("name","QA02 Бета").put("tree",""));
            assertTrue(pref.edit().putString("archive_registry",chats.toString())
                .putString("active_archive",a).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                visible("Мои переписки");
                click("QA02 Альфа");
                visible("QA02_ALPHA_ONLY");
                device.pressBack();device.waitForIdle();
                visible("Мои переписки");
                long firstId=pref.getLong("read_id_"+a,-1);
                assertTrue("Test precondition: first chat reading ID was not saved",firstId>0);
                click("QA02 Бета");
                visible("В этой переписке пока нет сообщений.");
                device.pressBack();device.waitForIdle();
                visible("Мои переписки");
                assertFalse("A chat with zero messages inherited a stale message ID from another chat",
                    pref.contains("read_id_"+b));
                assertEquals("Original chat reading position was corrupted",
                    firstId,pref.getLong("read_id_"+a,-1));
                // Ensure a second Activity does not pick up the phantom ID.
                scenario.recreate();device.waitForIdle();
                click("QA02 Бета");
                visible("В этой переписке пока нет сообщений.");
            }
        }finally{
            SharedPreferences.Editor e=pref.edit();
            if(oldRegistryExists)e.putString("archive_registry",oldRegistry);
            else e.remove("archive_registry");
            if(oldActiveExists)e.putString("active_archive",oldActive);
            else e.remove("active_archive");
            for(String key:new String[]{a,b}){
                e.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key)
                    .remove("pinned_chat_"+key);
            }
            assertTrue(e.commit());
            ctx.deleteDatabase("telegram_"+a+".db");
            ctx.deleteDatabase("telegram_"+b+".db");
        }
    }
}
