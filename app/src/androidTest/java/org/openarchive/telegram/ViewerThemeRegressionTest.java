package org.openarchive.telegram;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Only synthetic screens. Never capture the protected viewer or weaken FLAG_SECURE. */
@RunWith(AndroidJUnit4.class)
public final class ViewerThemeRegressionTest {
    private static View root(android.app.Activity activity){
        ViewGroup content=activity.findViewById(android.R.id.content);
        assertTrue(content.getChildCount()>0);return content.getChildAt(0);
    }
    private static void background(android.app.Activity activity,int expected){
        assertTrue(root(activity).getBackground() instanceof ColorDrawable);
        assertEquals(expected,((ColorDrawable)root(activity).getBackground()).getColor());
    }
    private static TextView find(View root,String text){
        if(root instanceof TextView&&((TextView)root).getText().toString().contains(text))return (TextView)root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                TextView found=find(group.getChildAt(i),text);if(found!=null)return found;
            }
        }
        return null;
    }
    private static void screenshot(String name)throws Exception{
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("screencap -p /sdcard/Download/"+name))){
            byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}
        }
    }
    @Test public void themeReachesDocumentDemoAndProtectedScreens()throws Exception{
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String key=new android.content.ComponentName(ctx,MainActivity.class).getShortClassName();
        if(key.startsWith("."))key=key.substring(1);
        SharedPreferences prefs=ctx.getSharedPreferences(key,Context.MODE_PRIVATE);
        boolean had=prefs.contains("appearance_theme");int old=prefs.getInt("appearance_theme",0);
        File text=File.createTempFile("synthetic-palette-", ".txt",ctx.getCacheDir());
        try{
            java.nio.file.Files.write(text.toPath(),"Synthetic document".getBytes(StandardCharsets.UTF_8));
            for(int theme:new int[]{0,1,2}){
                assertTrue(prefs.edit().putInt("appearance_theme",theme).commit());
                ViewerPalette expected=ViewerPalette.of(theme);
                assertEquals(expected.background,ViewerUi.palette(ctx).background);
                try(ActivityScenario<DemoChatActivity> screen=ActivityScenario.launch(DemoChatActivity.class)){
                    screen.onActivity(a->{
                        background(a,expected.background);
                        TextView title=find(root(a),"Учебная переписка");
                        assertNotNull(title);assertEquals(expected.text,title.getCurrentTextColor());
                    });
                    if(theme==2){InstrumentationRegistry.getInstrumentation().waitForIdleSync();screenshot("premium-light-demo.png");}
                }
                Intent intent=new Intent(ctx,AttachmentPreviewActivity.class).setData(Uri.fromFile(text)).putExtra("display_name","QA.txt");
                try(ActivityScenario<AttachmentPreviewActivity> screen=ActivityScenario.launch(intent)){
                    screen.onActivity(a->{background(a,expected.background);TextView title=find(root(a),"QA.txt");
                        assertNotNull(title);assertEquals(expected.text,title.getCurrentTextColor());});
                    if(theme==2){InstrumentationRegistry.getInstrumentation().waitForIdleSync();screenshot("premium-light-document.png");}
                }
                try(ActivityScenario<ProtectedArchiveActivity> screen=ActivityScenario.launch(ProtectedArchiveActivity.class)){
                    screen.onActivity(a->{
                        background(a,expected.background);
                        assertTrue("Theme must never disable secure window protection",
                            (a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
                        TextView title=find(root(a),"Защищённые переписки");
                        assertNotNull(title);assertEquals(expected.text,title.getCurrentTextColor());
                    });
                }
            }
        }finally{
            SharedPreferences.Editor restore=prefs.edit();if(had)restore.putInt("appearance_theme",old);else restore.remove("appearance_theme");
            assertTrue(restore.commit());text.delete();
        }
    }
}
