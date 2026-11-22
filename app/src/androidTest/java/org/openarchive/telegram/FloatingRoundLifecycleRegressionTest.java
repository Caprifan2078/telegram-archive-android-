package org.openarchive.telegram;

import android.widget.FrameLayout;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Hiding the application must not leave a zombie grey round-video overlay. */
@RunWith(AndroidJUnit4.class)
public final class FloatingRoundLifecycleRegressionTest {
    private Object read(MainActivity activity,String field)throws Exception{
        Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);return f.get(activity);
    }
    @Test public void backgroundingClosesStaleFloatingRoundSurface()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            AtomicReference<MainActivity> ref=new AtomicReference<>();
            scenario.onActivity(a->{
                try{
                    FrameLayout root=(FrameLayout)read(a,"root");
                    assertNotNull(root);
                    FrameLayout fake=new FrameLayout(a);
                    root.addView(fake,new FrameLayout.LayoutParams(120,120));
                    Field panel=MainActivity.class.getDeclaredField("floatingPanel");
                    panel.setAccessible(true);panel.set(a,fake);
                    ref.set(a);
                }catch(Exception e){throw new AssertionError(e);}
            });
            scenario.moveToState(Lifecycle.State.STARTED); // invokes onPause
            scenario.onActivity(a->{try{
                assertNull("Backgrounding must not preserve a dead overlay",read(a,"floatingPanel"));
            }catch(Exception e){throw new AssertionError(e);}});
        }
    }
}
