package org.openarchive.telegram;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Empty synthetic installation; exercises actual user navigation and lifecycle. */
@RunWith(AndroidJUnit4.class)
public class Qa00RotationLifecycleTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private final UiDevice device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

    private UiObject2 require(String text) {
        UiObject2 result=device.wait(Until.findObject(By.textContains(text)),8000);
        assertNotNull("Visible control missing: "+text,result);
        assertFalse("Control has empty visible bounds: "+text,result.getVisibleBounds().isEmpty());
        return result;
    }
    private void openAndCloseDrawer() {
        require("☰").click();
        device.waitForIdle();
        require("TELEGRAM ARCHIVE");
        device.pressBack();
        device.waitForIdle();
        assertNull("Back did not close drawer",device.wait(Until.findObject(By.text("TELEGRAM ARCHIVE")),1000));
        require("Мои переписки");
    }
    @After public void restorePortrait()throws Exception {
        device.setOrientationNatural();
        device.unfreezeRotation();
    }
    @Test public void homeAndDrawerRemainUsableAfterRotationAndBackground()throws Exception {
        require("Мои переписки");
        openAndCloseDrawer();
        device.setOrientationLeft();
        device.waitForIdle();
        assertTrue("Emulator did not rotate to landscape",device.getDisplayWidth()>device.getDisplayHeight());
        require("Мои переписки");
        openAndCloseDrawer();
        device.executeShellCommand("screencap -p /sdcard/Download/ta_qa00_landscape.png");
        device.pressHome();
        device.waitForIdle();
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launch=ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        assertNotNull("No launcher intent",launch);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(launch);
        device.waitForIdle();
        require("Мои переписки");
        openAndCloseDrawer();
        device.setOrientationNatural();
        device.waitForIdle();
        assertTrue("Emulator did not return to portrait",device.getDisplayHeight()>device.getDisplayWidth());
        require("Мои переписки");
        openAndCloseDrawer();
    }
}
