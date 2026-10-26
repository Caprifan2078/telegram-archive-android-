package org.openarchive.telegram;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Verify the actual Activity tree without relying on emulator window-focus timing.
 * Player/media UI and popup focus get separate device tests, not false green here. */
@RunWith(AndroidJUnit4.class)
public class UiSmokeTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    private View findText(View root,String target){
        if(root instanceof TextView && target.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                View match=findText(group.getChildAt(i),target);
                if(match!=null)return match;
            }
        }
        return null;
    }

    @Test public void launchesAndShowsHome(){
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AtomicBoolean ok=new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View label=findText(activity.getActivity().getWindow().getDecorView(),"Мои переписки");
            ok.set(label!=null&&label.isShown());
        });
        assertTrue("Home title should be present and visible",ok.get());
    }

    @Test public void hamburgerOpensWithoutCrash(){
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        AtomicBoolean pressed=new AtomicBoolean(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View menu=findText(activity.getActivity().getWindow().getDecorView(),"☰");
            if(menu!=null&&menu.isShown())pressed.set(menu.performClick());
        });
        assertTrue("Hamburger must be visible and handle click",pressed.get());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
