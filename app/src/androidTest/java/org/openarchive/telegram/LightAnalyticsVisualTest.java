package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.Instrumentation;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Synthetic light-theme screenshot. Isolated emulator preferences are restored after the test. */
@RunWith(AndroidJUnit4.class)
public class LightAnalyticsVisualTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class,true,false);
    @Test public void lightThemeHeatmapRemainsReadable()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Context ctx=inst.getTargetContext();
        SharedPreferences prefs=ctx.getSharedPreferences("MainActivity",Context.MODE_PRIVATE);
        boolean had=prefs.contains("appearance_theme");int previous=prefs.getInt("appearance_theme",0);
        assertTrue(prefs.edit().putInt("appearance_theme",2).commit());
        try{
            MainActivity a=activity.launchActivity(null);inst.waitForIdleSync();
            inst.runOnMainSync(()->{try{
                Field theme=MainActivity.class.getDeclaredField("themeIndex");theme.setAccessible(true);
                assertEquals(2,theme.getInt(a));
                Field listField=MainActivity.class.getDeclaredField("list");listField.setAccessible(true);
                LinearLayout list=(LinearLayout)listField.get(a);list.removeAllViews();
                ChatTrends.Activity data=new ChatTrends.Activity();data.weekdayHours[0][0]=123;data.weekdayHours[1][4]=7;
                LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);list.addView(card);
                Method heat=MainActivity.class.getDeclaredMethod("analyticsHeatmap",LinearLayout.class,ChatTrends.Activity.class);
                heat.setAccessible(true);heat.invoke(a,card,data);
            }catch(Exception e){throw new AssertionError(e);}});
            inst.waitForIdleSync();
            try(android.os.ParcelFileDescriptor shell=inst.getUiAutomation().executeShellCommand(
                    "screencap -p /sdcard/Download/ta_synthetic_light_analytics.png");
                java.io.FileInputStream output=new java.io.FileInputStream(shell.getFileDescriptor())){
                byte[] drain=new byte[1024];while(output.read(drain)!=-1){}
            }
        }finally{
            SharedPreferences.Editor edit=prefs.edit();
            if(had)edit.putInt("appearance_theme",previous);else edit.remove("appearance_theme");
            assertTrue(edit.commit());
        }
    }
}
