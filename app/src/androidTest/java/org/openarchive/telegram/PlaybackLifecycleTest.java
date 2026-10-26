package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.VideoView;
import androidx.test.core.app.ActivityScenario;
import androidx.lifecycle.Lifecycle;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class PlaybackLifecycleTest {
    private VideoView video(View v){
        if(v instanceof VideoView)return (VideoView)v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){
            VideoView found=video(((ViewGroup)v).getChildAt(i));if(found!=null)return found;
        }
        return null;
    }
    @Test public void leavingWhilePreparingCannotStartHiddenPlayback() throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        Context ctx=inst.getTargetContext();File file=new File(ctx.getCacheDir(),"lifecycle-round.mp4");
        try(InputStream in=inst.getContext().getAssets().open("short-round.mp4");OutputStream out=new FileOutputStream(file)){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
        }
        Intent intent=new Intent(ctx,RoundPlayerActivity.class).setData(Uri.fromFile(file));
        try(ActivityScenario<RoundPlayerActivity> scenario=ActivityScenario.launch(intent)){
            scenario.moveToState(Lifecycle.State.CREATED);
            SystemClock.sleep(700);
            AtomicBoolean playing=new AtomicBoolean(true);
            scenario.onActivity(a->playing.set(video(a.getWindow().getDecorView()).isPlaying()));
            assertFalse("Video must release its decoder in background",playing.get());
            scenario.moveToState(Lifecycle.State.RESUMED);
            long deadline=SystemClock.uptimeMillis()+7000;AtomicBoolean ready=new AtomicBoolean(false);
            while(!ready.get()&&SystemClock.uptimeMillis()<deadline){
                scenario.onActivity(a->ready.set(video(a.getWindow().getDecorView()).getDuration()>0));SystemClock.sleep(100);
            }
            assertTrue("Video should prepare again after returning",ready.get());
            scenario.onActivity(a->playing.set(video(a.getWindow().getDecorView()).isPlaying()));
            assertFalse("Returning must respect paused state",playing.get());
        }finally{file.delete();}
    }
}
