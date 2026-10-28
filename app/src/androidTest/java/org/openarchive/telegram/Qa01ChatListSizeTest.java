package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
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

/** Disposable synthetic registries; navigates visible cards with real touches. */
@RunWith(AndroidJUnit4.class)
public class Qa01ChatListSizeTest {
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private UiObject2 findOrScroll(String text) {
        for(int attempt=0;attempt<25;attempt++){
            UiObject2 hit=device.wait(Until.findObject(By.textContains(text)),500);
            if(hit!=null&&!hit.getVisibleBounds().isEmpty())return hit;
            Rect area=null;
            for(UiObject2 scroll:device.findObjects(By.scrollable(true))){
                Rect b=scroll.getVisibleBounds();
                if(b.width()>40&&b.height()>80){area=b;break;}
            }
            if(area==null)break;
            device.swipe(area.centerX(),area.bottom-area.height()/4,
                area.centerX(),area.top+area.height()/4,20);
            device.waitForIdle();
        }
        return null;
    }

    private void checkCountAndLastCard(int count)throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String preferenceName=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(preferenceName.startsWith("."))preferenceName=preferenceName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(preferenceName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String oldRegistry=prefs.getString("archive_registry","[]");
        String oldActive=prefs.getString("active_archive","default");
        String prefix="qa01size_"+UUID.randomUUID().toString().replace("-","")+"_";
        JSONArray entries=new JSONArray();
        for(int i=0;i<count;i++){
            String label=String.format(Locale.ROOT,"QA01 Size %03d",i);
            entries.put(new JSONObject().put("key",prefix+i).put("name",label).put("tree",""));
        }
        try{
            assertTrue(prefs.edit().putString("archive_registry",entries.toString())
                .putString("active_archive",prefix+0).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                assertNotNull("Wrong chat count",device.wait(
                    Until.findObject(By.text("Всего чатов: "+count)),8000));
                String last=String.format(Locale.ROOT,"QA01 Size %03d",count-1);
                UiObject2 card=findOrScroll(last);
                assertNotNull("Last chat card unreachable for "+count,card);
                card.click();device.waitForIdle();
                assertNotNull("Last chat did not open",device.wait(
                    Until.findObject(By.text("История сообщений · локально")),8000));
                assertNotNull("Wrong chat heading",device.wait(Until.findObject(By.textContains(last)),8000));
                device.pressBack();device.waitForIdle();
                assertNotNull("Back did not return to chats",device.wait(
                    Until.findObject(By.text("Мои переписки")),8000));
            }
        }finally{
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",oldRegistry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",oldActive);else edit.remove("active_archive");
            for(int i=0;i<count;i++){
                String key=prefix+i;
                edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key);
                ctx.deleteDatabase("telegram_"+key+".db");
            }
            assertTrue(edit.commit());
        }
    }

    @Test public void oneChat()throws Exception {checkCountAndLastCard(1);}
    @Test public void twoChats()throws Exception {checkCountAndLastCard(2);}
    @Test public void tenChats()throws Exception {checkCountAndLastCard(10);}
    @Test public void fiftyChats()throws Exception {checkCountAndLastCard(50);}
}
