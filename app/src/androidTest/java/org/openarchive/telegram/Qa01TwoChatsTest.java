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

/** Two synthetic archives; actual chat-card taps and message rendering. */
@RunWith(AndroidJUnit4.class)
public class Qa01TwoChatsTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private UiObject2 locate(String label) {
        for(int direction=0;direction<2;direction++) {
            for(int i=0;i<8;i++) {
                UiObject2 found=device.wait(Until.findObject(By.textContains(label)),1200);
                if(found!=null&&!found.getVisibleBounds().isEmpty())return found;
                Rect area=null;
                for(UiObject2 scroll:device.findObjects(By.scrollable(true))) {
                    Rect candidate=scroll.getVisibleBounds();
                    if(candidate.width()>40&&candidate.height()>80){area=candidate;break;}
                }
                if(area==null)break;
                int top=area.top+area.height()/4, bottom=area.bottom-area.height()/4;
                device.swipe(area.centerX(),direction==0?bottom:top,
                    area.centerX(),direction==0?top:bottom,20);
                device.waitForIdle();
            }
        }
        return null;
    }
    private void tap(String label) {
        UiObject2 item=locate(label);
        assertNotNull("Cannot reach control: "+label,item);
        item.click();device.waitForIdle();
    }
    private void requireMessage(String label) {
        assertNotNull("Message not displayed: "+label,
            device.wait(Until.findObject(By.textContains(label)),12000));
    }
    private static void seed(Context ctx,String key,String message) {
        MessageDb db=new MessageDb(ctx,key);
        try {
            SQLiteDatabase sql=db.getWritableDatabase();
            sql.beginTransaction();
            try {
                db.stage(sql,"QA01 synthetic author","2024-02-29 12:00",message,null,"");
                sql.setTransactionSuccessful();
            } finally { sql.endTransaction(); }
            db.publishStaged(sql);
        } finally { db.close(); }
    }
    @Test public void openingTwoChatsKeepsMessagesSeparateAfterRecreation()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String keyA="qa01a_"+suffix,keyB="qa01b_"+suffix;
        String name=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(name.startsWith("."))name=name.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(name,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        try {
            seed(ctx,keyA,"QA01_ONLY_ALPHA");seed(ctx,keyB,"QA01_ONLY_BETA");
            JSONArray chats=new JSONArray()
                .put(new JSONObject().put("key",keyA).put("name","QA01 Альфа").put("tree",""))
                .put(new JSONObject().put("key",keyB).put("name","QA01 Бета").put("tree",""));
            assertTrue(prefs.edit().putString("archive_registry",chats.toString())
                .putString("active_archive",keyA).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                assertNotNull(locate("Всего чатов: 2"));
                tap("QA01 Альфа");
                requireMessage("QA01_ONLY_ALPHA");
                assertNull("Wrong chat message visible",device.findObject(By.textContains("QA01_ONLY_BETA")));
                UiObject2 menu=device.wait(Until.findObject(By.desc("Меню переписки")),5000);
                assertNotNull("Conversation menu missing",menu);menu.click();device.waitForIdle();
                tap("Сохранить переписку в PDF");
                assertNotNull("PDF explanation missing",device.wait(Until.findObject(By.textContains("Вложения будут обозначены")),5000));
                tap("Отмена");
                requireMessage("QA01_ONLY_ALPHA");
                device.pressBack();device.waitForIdle();
                assertNotNull(locate("Мои переписки"));
                tap("QA01 Бета");
                requireMessage("QA01_ONLY_BETA");
                assertNull("Wrong chat message visible",device.findObject(By.textContains("QA01_ONLY_ALPHA")));
                device.executeShellCommand("screencap -p /sdcard/Download/ta_qa01_beta.png");
                scenario.recreate();
                device.waitForIdle();
                requireMessage("QA01_ONLY_BETA");
                assertNull("Wrong chat after Activity recreation",device.findObject(By.textContains("QA01_ONLY_ALPHA")));
                device.pressBack();device.waitForIdle();
                UiObject2 beta=locate("QA01 Бета");
                assertNotNull("Second chat card missing",beta);
                beta.longClick();device.waitForIdle();
                tap("Закрепить чат");
                assertTrue("Pin preference was not saved",prefs.getBoolean("pinned_chat_"+keyB,false));
                UiObject2 betaPinned=locate("QA01 Бета"), alphaUnpinned=locate("QA01 Альфа");
                assertNotNull(betaPinned);assertNotNull(alphaUnpinned);
                assertTrue("Pinned chat should appear before unpinned chat",
                    betaPinned.getVisibleBounds().top<alphaUnpinned.getVisibleBounds().top);
                scenario.recreate();device.waitForIdle();
                UiObject2 pinned=locate("QA01 Бета");
                assertNotNull("Pinned chat card missing after recreation",pinned);
                assertTrue("Pin indicator lost after recreation",pinned.getText().contains("📌"));
                pinned.longClick();device.waitForIdle();
                tap("Открепить чат");
                assertFalse("Unpin preference was not saved",prefs.getBoolean("pinned_chat_"+keyB,true));
            }
        } finally {
            ctx.deleteDatabase("telegram_"+keyA+".db");
            ctx.deleteDatabase("telegram_"+keyB+".db");
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            for(String key:new String[]{keyA,keyB}) {
                edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key).remove("pinned_chat_"+key);
            }
            assertTrue(edit.commit());
        }
    }
}
