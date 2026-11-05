package org.openarchive.telegram;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.SystemClock;
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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class VoiceSeekUiRegressionTest {
    @Test public void voiceHasSeekControlAndPausedSeekKeepsPause()throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests=InstrumentationRegistry.getInstrumentation().getContext();
        UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String key="seekui_"+UUID.randomUUID().toString().replace("-","");
        String prefsName=new ComponentName(ctx,MainActivity.class).getShortClassName();
        if(prefsName.startsWith("."))prefsName=prefsName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(prefsName,Context.MODE_PRIVATE);
        boolean hadRegistry=prefs.contains("archive_registry"),hadActive=prefs.contains("active_archive");
        String registry=prefs.getString("archive_registry","[]"),active=prefs.getString("active_archive","default");
        File root=new File(ctx.getCacheDir(),key);assertTrue(root.mkdirs());
        try {
            try(InputStream in=tests.getAssets().open("voice-long.ogg");FileOutputStream out=new FileOutputStream(new File(root,"voice.ogg"))) {
                byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);
            }
            MessageDb db=new MessageDb(ctx,key);
            try {
                SQLiteDatabase sql=db.getWritableDatabase();
                long id=db.stage(sql,"Synthetic","2024-01-01 12:00","Voice test",null,"");
                db.stageAttachment(sql,id,"voice.ogg","voice");db.publishStaged(sql);
            } finally {db.close();}
            JSONArray entries=new JSONArray().put(new JSONObject().put("key",key).put("name","Voice seek QA").put("tree","local:"+root.getAbsolutePath()));
            assertTrue(prefs.edit().putString("archive_registry",entries.toString()).putString("active_archive",key).commit());
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                UiObject2 chat=device.wait(Until.findObject(By.text("Voice seek QA")),15000);
                assertNotNull("Synthetic chat missing",chat);chat.click();
                UiObject2 seek=device.wait(Until.findObject(By.desc("Перемотка голосового сообщения")),15000);
                assertNotNull("Voice seek control missing",seek);
                UiObject2 play=device.wait(Until.findObject(By.text("▶")),15000);
                assertNotNull("Voice play missing",play);play.click();
                assertNotNull("Voice did not start",device.wait(Until.findObject(By.text("❚❚")),15000));
                UiObject2 pause=device.findObject(By.text("❚❚"));pause.click();
                assertNotNull("Voice did not pause",device.wait(Until.findObject(By.text("▶")),10000));
                seek=device.wait(Until.findObject(By.desc("Перемотка голосового сообщения")),5000);
                assertNotNull(seek);assertTrue("Seek should be enabled",seek.isEnabled());
                Rect b=seek.getVisibleBounds();device.click(b.left+b.width()*3/4,b.centerY());
                long deadline=SystemClock.uptimeMillis()+8000;
                final int[] position={-1};final boolean[] playing={true};
                while(SystemClock.uptimeMillis()<deadline){
                    scenario.onActivity(a->{try{
                        java.lang.reflect.Field f=MainActivity.class.getDeclaredField("player");f.setAccessible(true);
                        MediaPlayer p=(MediaPlayer)f.get(a);if(p!=null){position[0]=p.getCurrentPosition();playing[0]=p.isPlaying();}
                    }catch(Exception ignored){}});
                    if(position[0]>=350)break;SystemClock.sleep(100);
                }
                assertTrue("Voice did not seek: "+position[0],position[0]>=350);
                assertFalse("Paused voice restarted while seeking",playing[0]);
                UiObject2 resume=device.findObject(By.text("▶"));assertNotNull(resume);resume.click();
                assertNotNull("Voice did not resume",device.wait(Until.findObject(By.text("❚❚")),5000));
            }
        } finally {
            ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(root);
            SharedPreferences.Editor edit=prefs.edit();
            if(hadRegistry)edit.putString("archive_registry",registry);else edit.remove("archive_registry");
            if(hadActive)edit.putString("active_archive",active);else edit.remove("active_archive");
            edit.remove("read_offset_"+key).remove("read_id_"+key).remove("read_top_"+key).remove("pinned_chat_"+key);
            assertTrue(edit.commit());
        }
    }
}
