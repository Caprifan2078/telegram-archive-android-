package org.openarchive.telegram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.VideoView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.io.*;
import java.util.UUID;

/** Verifies that Telegram's circular MP4 launches in the app player, not just a raw codec. */
@RunWith(AndroidJUnit4.class)
public class RoundPlayerActivityTest {
    private static VideoView findVideo(View node){
        if(node instanceof VideoView)return (VideoView)node;
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                VideoView v=findVideo(group.getChildAt(i));if(v!=null)return v;
            }
        }
        return null;
    }
    @Test public void roundVideoActivityOpensAndAllowsSeeking()throws Exception{
        Context testContext=InstrumentationRegistry.getInstrumentation().getContext();
        Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file=new File(target.getCacheDir(),"round-ui-test-"+UUID.randomUUID()+".mp4");
        try(InputStream in=testContext.getAssets().open("short-round.mp4");OutputStream out=new FileOutputStream(file)){
            byte[] buffer=new byte[16384];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        }
        Activity activity=null;
        try{
            Intent intent=new Intent(target,RoundPlayerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.fromFile(file));
            activity=InstrumentationRegistry.getInstrumentation().startActivitySync(intent);
            assertTrue(activity instanceof RoundPlayerActivity);
            VideoView video=findVideo(activity.getWindow().getDecorView());
            assertNotNull("App's VideoView missing",video);
            int attempts=0;
            while(video.getDuration()<=0&&attempts++<40)SystemClock.sleep(100);
            assertTrue("MP4 not prepared in activity",video.getDuration()>1000);
            final VideoView player=video;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->player.seekTo(900));
            SystemClock.sleep(170);
            assertTrue("Seek is not active in full-screen player",player.getCurrentPosition()>=600);
        }finally{
            if(activity!=null){Activity finalActivity=activity;
                InstrumentationRegistry.getInstrumentation().runOnMainSync(finalActivity::finish);}
            file.delete();
        }
    }
}
