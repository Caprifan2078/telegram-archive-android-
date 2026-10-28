package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

/** Real UI taps, strictly synthetic empty-app test on a disposable emulator.
 * Never opens a real Telegram archive or confirms import, restore or sharing.
 */
@RunWith(AndroidJUnit4.class)
public class UiMenuTourRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(inst);

    private UiObject2 findVisible(String value) {
        UiObject2 item=device.wait(Until.findObject(By.textContains(value)),1200L);
        if(item==null)return null;
        Rect bounds=item.getVisibleBounds();
        return bounds!=null&&!bounds.isEmpty()?item:null;
    }
    private UiObject2 scrollFind(String value) {
        for(int n=0;n<12;n++){
            UiObject2 item=findVisible(value);
            if(item!=null)return item;
            int x=Math.min(device.getDisplayWidth()/3,210);
            device.swipe(x,device.getDisplayHeight()*78/100,x,device.getDisplayHeight()*26/100,18);
        }
        return null;
    }
    private void tap(String label)throws Exception {
        UiObject2 item=scrollFind(label);
        if(item==null){
            // Diagnostic screenshot and accessibility tree for real UI issues,
            // not for hiding assertions. Only disposable synthetic test data.
            screenshot("failure");
            device.executeShellCommand("uiautomator dump /sdcard/Download/ta_ui_tour_failure.xml");
        }
        assertNotNull("Not found or inaccessible: "+label,item);
        item.click();
        device.waitForIdle();
    }
    private void expect(String label) {
        assertNotNull("Missing destination: "+label,scrollFind(label));
    }
    private void screenshot(String name)throws Exception {
        device.executeShellCommand("screencap -p /sdcard/Download/ta_ui_tour_"+name+".png");
    }
    private void drawer(){
        UiObject2 menu=findVisible("☰");
        assertNotNull("Hamburger not displayed",menu);
        menu.click();
        device.waitForIdle();
        expect("TELEGRAM ARCHIVE");
    }
    private void close(){
        device.pressBack();
        device.waitForIdle();
    }
    private void openDialog(String method) {
        inst.runOnMainSync(()->{
            try{
                Method m=MainActivity.class.getDeclaredMethod(method);
                m.setAccessible(true);
                m.invoke(activity.getActivity());
            }catch(Exception e){throw new AssertionError(method,e);}
        });
        device.waitForIdle();
    }

    @Test public void drawerGroupsExpandWithRealScreenTaps()throws Exception {
        drawer();
        screenshot("drawer-home");
        String[][] groups={
            {"Архивы и резервные копии","Создать резервную копию"},
            {"Инструменты переписки","Хронология отношений"},
            {"Открытия · новые возможности","Студия оформления 2.0"},
            {"Оформление и персонализация","Умная производительность"}
        };
        for(int n=0;n<groups.length;n++){
            tap(groups[n][0]);
            expect(groups[n][1]);
            screenshot("drawer-group-"+n);
            tap(groups[n][0]);
        }
        String version=activity.getActivity().getPackageManager().getPackageInfo(
            activity.getActivity().getPackageName(),0).versionName;
        expect("Telegram Archive · "+version+" QA");
        close();
    }

    @Test public void safeDrawerDestinationsOpenWithoutCrashing()throws Exception {
        String[][] paths={
            {"Архивы и резервные копии","Импортировать чат","Добавить переписку"},
            {"Архивы и резервные копии","Создать резервную копию","Создать резервную копию"},
            {"Архивы и резервные копии","Квитанция переноса","Квитанция переноса"},
            {"Архивы и резервные копии","Восстановить архив","Восстановить переписки"},
            {"Открытия · новые возможности","Умный поиск по всем чатам","Умный поиск"},
            {"Открытия · новые возможности","Студия оформления 2.0","Студия чата 2.0"},
            {"Оформление и персонализация","Темы и оформление","Тема оформления"},
            {"Оформление и персонализация","Стеклянные карточки","Полупрозрачные карточки"},
            {"Оформление и персонализация","Умная производительность","Производительность переписки"},
            {"Оформление и персонализация","Настройки переписки","Настройки переписки"},
            {"","Шрифты · изменить везде","Шрифты · оформление всего приложения"}
        };
        for(String[] path:paths){
            drawer();
            if(!path[0].isEmpty())tap(path[0]);
            tap(path[1]);
            expect(path[2]);
            close();
        }
        drawer();
        tap("Помощь и инструкция");
        expect("Помощь · Telegram Archive");
        screenshot("help");
        close();
        expect("Мои переписки");
    }

    @Test public void settingsOptionsAreActuallyAppliedOnAndroid()throws Exception {
        SharedPreferences prefs=activity.getActivity().getPreferences(Context.MODE_PRIVATE);
        String[] modes={"Классический ·","Плавность ·","Экономия ·","Автоматически ·"};
        for(int n=0;n<modes.length;n++){
            openDialog("performanceDialog");
            expect("Производительность переписки");
            if(n==0){
                screenshot("performance-dialog");
                device.executeShellCommand("uiautomator dump /sdcard/Download/ta_ui_tour_performance.xml");
            }
            tap(modes[n]);
            assertEquals("Performance option not saved: "+n,n,prefs.getInt("appearance_performance",-1));
        }
        String[] styles={"Обычные карточки","Стеклянный стиль"};
        for(int n=0;n<styles.length;n++){
            openDialog("glassDialog");
            tap(styles[n]);
            assertEquals("Glass option not saved",n==1,prefs.getBoolean("appearance_glass",n==0));
        }
        openDialog("performanceDialog");
        boolean old=prefs.getBoolean("appearance_less_motion",false);
        tap(old?"Включить анимации":"Уменьшить анимации");
        assertEquals(!old,prefs.getBoolean("appearance_less_motion",old));
        openDialog("bubbleStyleDialog");
        expect("Скругление сообщений");
        tap("Применить");
        assertTrue(prefs.getInt("appearance_bubble_radius",-1)>=0);
        screenshot("settings-applied");
    }

    @Test public void fontPreviewAndThemeSelectionAreClickable()throws Exception {
        openDialog("showFontStudio");
        expect("Шрифты · оформление всего приложения");
        screenshot("font-choices");
        close();
        openDialog("appearanceDialog");
        expect("Тема оформления");
        tap("Графит · строгая тёмная");
        assertNotNull("Activity did not return from theme recreation",
            device.wait(Until.findObject(By.textContains("Мои переписки")),8000L));
        screenshot("theme-graphite");
    }
}
