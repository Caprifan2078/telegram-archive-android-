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
    private static android.widget.TextView findTextWithDescription(android.view.View root,String prefix){
        if(root instanceof android.widget.TextView&&root.getContentDescription()!=null&&
            root.getContentDescription().toString().startsWith(prefix))return (android.widget.TextView)root;
        if(root instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                android.widget.TextView found=findTextWithDescription(group.getChildAt(i),prefix);if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void lightThemeHeatmapRemainsReadable()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Context ctx=inst.getTargetContext();
        String preferencesName=new android.content.ComponentName(ctx,MainActivity.class).getShortClassName();
        if(preferencesName.startsWith("."))preferencesName=preferencesName.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(preferencesName,Context.MODE_PRIVATE);
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
                LinearLayout firstRow=(LinearLayout)card.getChildAt(2);
                android.widget.TextView zero=(android.widget.TextView)firstRow.getChildAt(2);
                android.widget.TextView high=(android.widget.TextView)firstRow.getChildAt(1);
                assertTrue("Light-theme empty cell must have readable text",
                    AnalyticsContrastPolicy.contrast(0xff293449,zero.getCurrentTextColor())>=4.5);
                assertTrue("Light-theme active cell must have readable text",
                    AnalyticsContrastPolicy.contrast(0xffed744b,high.getCurrentTextColor())>=4.5);
                data.days.put(java.time.LocalDate.of(2024,2,29),1L);
                Field year=MainActivity.class.getDeclaredField("analyticsCalendarYear");year.setAccessible(true);year.setInt(a,2024);
                Method calendar=MainActivity.class.getDeclaredMethod("renderAnnualCalendar",ChatTrends.Activity.class);
                calendar.setAccessible(true);calendar.invoke(a,data);
                android.widget.TextView emptyDay=findTextWithDescription(list,"2024-02-01:");
                assertNotNull("Empty calendar day must be accessible",emptyDay);
                assertTrue("Light-theme empty calendar day must be readable",
                    AnalyticsContrastPolicy.contrast(0xff293449,emptyDay.getCurrentTextColor())>=4.5);
                assertTrue("Light status bar must request dark icons",
                    (a.getWindow().getDecorView().getSystemUiVisibility()&android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)!=0);
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
