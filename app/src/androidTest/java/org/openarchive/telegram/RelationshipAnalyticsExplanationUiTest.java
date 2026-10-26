package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Screens are rendered with a generated empty archive, never with private chats. */
@RunWith(AndroidJUnit4.class)
public class RelationshipAnalyticsExplanationUiTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private static Object get(MainActivity a,String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String name,Object value)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);f.set(a,value);
    }
    private static boolean contains(View root,String value){
        if(root instanceof TextView && ((TextView)root).getText().toString().contains(value))return true;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++)if(contains(group.getChildAt(i),value))return true;
        }
        return false;
    }
    @Test public void relationshipScreenShowsReasonsForAllSevenActions()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Context ctx=inst.getTargetContext();
        MainActivity a=activity.getActivity();
        ExecutorService worker=(ExecutorService)get(a,"worker");
        String key="explain"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(ctx,key);
        MessageDb original=(MessageDb)get(a,"db");
        String originalKey=(String)get(a,"activeKey"),originalScreen=(String)get(a,"screen");
        boolean originalHome=(boolean)get(a,"home");
        try{
            inst.runOnMainSync(()->{
                try {
                    set(a,"db",synthetic);set(a,"activeKey",key);
                    set(a,"home",false);set(a,"screen","relationships");
                    Method render=MainActivity.class.getDeclaredMethod("renderRelationshipsPage");
                    render.setAccessible(true);render.invoke(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
            CountDownLatch idle=new CountDownLatch(1);
            worker.execute(idle::countDown);
            assertTrue("Relationships loading blocked",idle.await(8,TimeUnit.SECONDS));
            inst.waitForIdleSync();
            LinearLayout list=(LinearLayout)get(a,"list");
            for(String description:new String[]{
                RelationshipFeatureGuide.STAGES,RelationshipFeatureGuide.ECHO,
                RelationshipFeatureGuide.COMPARE,RelationshipFeatureGuide.MEMORIES,
                RelationshipFeatureGuide.CALENDARS,RelationshipFeatureGuide.CINEMA,
                RelationshipFeatureGuide.MEDIA_HEALTH})
                assertTrue("Missing visible feature explanation: "+description,
                    contains(list,description));
            assertTrue(contains(list,RelationshipFeatureGuide.INTRO));
            // Keep an external synthetic UI capture after Gradle uninstalls
            // the tested APK: app-specific external files are removed on uninstall.
            Bitmap image=inst.getUiAutomation().takeScreenshot();
            assertNotNull("Android UI screenshot unavailable",image);
            image.recycle();
            try(android.os.ParcelFileDescriptor shell=inst.getUiAutomation().executeShellCommand(
                    "screencap -p /sdcard/Download/ta_synthetic_relationships.png");
                java.io.FileInputStream output=new java.io.FileInputStream(shell.getFileDescriptor())){
                byte[] drain=new byte[1024];
                while(output.read(drain)!=-1){/* wait until shell screenshot completes */}
            }
        }finally{
            CountDownLatch done=new CountDownLatch(1);
            worker.execute(done::countDown);
            assertTrue(done.await(8,TimeUnit.SECONDS));
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",original);set(a,"activeKey",originalKey);
                    set(a,"screen",originalScreen);set(a,"home",originalHome);
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void heatmapDisplaysRealSyntheticCountsAndHourRanges()throws Exception {
        MainActivity a=activity.getActivity();
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        ChatTrends.Activity values=new ChatTrends.Activity();
        values.weekdayHours[0][0]=7;values.weekdayHours[0][3]=5;
        values.weekdayHours[0][4]=9;
        values.weekdayHours[3][22]=1;
        final LinearLayout[] holder={null};
        inst.runOnMainSync(()->{
            try{
                LinearLayout parent=new LinearLayout(a);
                parent.setOrientation(LinearLayout.VERTICAL);
                Method renderer=MainActivity.class.getDeclaredMethod(
                    "analyticsHeatmap",LinearLayout.class,ChatTrends.Activity.class);
                renderer.setAccessible(true);renderer.invoke(a,parent,values);
                holder[0]=parent;
            }catch(Exception e){throw new AssertionError(e);}
        });
        assertNotNull(holder[0]);
        assertTrue("Real sum 7+5 must be shown",contains(holder[0],"12"));
        assertTrue("Next 4-hour interval must be 9",contains(holder[0],"9"));
        assertTrue("Hour ranges must be labelled",contains(holder[0],"00–03"));
        assertTrue("Actual count must be clearly described",contains(holder[0],"ЧИСЛО сообщений"));
    }
}
