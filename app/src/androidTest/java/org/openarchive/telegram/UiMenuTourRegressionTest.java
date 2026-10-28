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
        UiObject2 item=device.wait(Until.findObject(By.textContains(value)),650L);
        if(item!=null){
            Rect bounds=item.getVisibleBounds();
            if(bounds!=null&&!bounds.isEmpty())return item;
        }
        // Android AlertDialog capitalizes native action buttons even when
        // setNegativeButton("Назад") passed mixed case. UI Automator's
        // textContains is case-sensitive; fall back to actual visible buttons.
        String wanted=value.toLowerCase(java.util.Locale.ROOT);
        for(UiObject2 button:device.findObjects(By.clazz("android.widget.Button"))){
            String text=button.getText();
            if(text!=null&&text.toLowerCase(java.util.Locale.ROOT).contains(wanted)){
                Rect bounds=button.getVisibleBounds();
                if(bounds!=null&&!bounds.isEmpty())return button;
            }
        }
        return null;
    }
    private UiObject2 scrollFind(String value) {
        // A preceding lookup can leave the drawer or dialog at the bottom.
        // Search both directions within the actual scrollable bounds; swiping
        // at a fixed screen coordinate can hit a narrow dialog's scrim.
        for(int direction=0;direction<2;direction++){
            for(int n=0;n<12;n++){
                UiObject2 item=findVisible(value);
                if(item!=null)return item;
                Rect area=null;
                for(UiObject2 scrollable:device.findObjects(By.scrollable(true))){
                    Rect candidate=scrollable.getVisibleBounds();
                    if(candidate.width()>40&&candidate.height()>80){area=candidate;break;}
                }
                if(area==null)break;
                int x=area.centerX();
                int high=area.top+area.height()/4;
                int low=area.bottom-area.height()/4;
                device.swipe(x,direction==0?low:high,x,direction==0?high:low,18);
                device.waitForIdle();
            }
        }
        return findVisible(value);
    }
    private void tap(String label)throws Exception {
        UiObject2 item=scrollFind(label);
        if(item==null){
            // Diagnostic screenshot and accessibility tree for real UI issues,
            // not for hiding assertions. Only disposable synthetic test data.
            StringBuilder visible=new StringBuilder();
            for(UiObject2 textView:device.findObjects(By.clazz("android.widget.TextView"))){
                String content=textView.getText();
                if(content!=null&&visible.length()<2500)
                    visible.append('[').append(content).append(']');
            }
            android.util.Log.e("UI_MENU_TOUR","Missing: "+label+"; visible: "+visible);
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
            {"Оформление и персонализация","Умная производительность","Скорость и память"},
            {"Оформление и персонализация","Настройки переписки","Настройки переписки"},
            {"","Шрифты · изменить везде","Шрифты · оформление всего приложения"}
        };
        for(String[] path:paths){
            drawer();
            if(!path[0].isEmpty())tap(path[0]);
            tap(path[1]);
            expect(path[2]);
            if("Создать резервную копию".equals(path[1])){
                // Previously the long message could hide both backup choices.
                tap("ПЕРЕЕЗД · Полная копия");
                expect("Полная резервная копия");
                expect("Перед удалением оригиналов");
                screenshot("backup-confirm");
                // The button must remain in the confirmation after the initial
                // list has dismissed. Do not scroll the background on failure.
                UiObject2 back=findVisible("Назад");
                assertNotNull("No Back in full-backup confirmation",back);
                back.click();
                device.waitForIdle();
                expect("Создать резервную копию");
            }
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
        // Start with a DIFFERENT radio option: clicking the already selected
        // zero option can be a no-op on some widgets. Then return to zero.
        String[] modes={"Плавность ·","Экономия ·","Автоматически ·","Классический ·"};
        int[] values={1,2,3,0};
        for(int n=0;n<modes.length;n++){
            openDialog("performanceDialog");
            expect("Скорость и память");
            if(n==0)screenshot("performance-dialog");
            tap(modes[n]);
            inst.waitForIdleSync();
            int saved=activity.getActivity().getPreferences(Context.MODE_PRIVATE)
                .getInt("appearance_performance",-1);
            android.util.Log.i("QA00", "selected="+values[n]+", saved="+saved);
            assertEquals("Performance option not saved: "+values[n],values[n],saved);
        }
        String[] styles={"Обычные карточки","Стеклянный стиль"};
        for(int n=0;n<styles.length;n++){
            openDialog("glassDialog");
            tap(styles[n]);
            assertEquals("Glass option not saved",n==1,prefs.getBoolean("appearance_glass",n==0));
        }
        openDialog("performanceDialog");
        boolean old=prefs.getBoolean("appearance_less_motion",false);
        tap(old?"С анимацией":"Без анимации");
        assertEquals(!old,prefs.getBoolean("appearance_less_motion",old));
        openDialog("bubbleStyleDialog");
        expect("Скругление сообщений");
        tap("Применить");
        assertTrue(prefs.getInt("appearance_bubble_radius",-1)>=0);
        screenshot("settings-applied");
    }

    @Test public void performanceDialogMotionButtonIsFullyVisible()throws Exception {
        drawer();
        tap("Оформление и персонализация");
        tap("Умная производительность");
        expect("Скорость и память");
        screenshot("performance-action-visibility");
        boolean reduced=activity.getActivity().getPreferences(Context.MODE_PRIVATE)
            .getBoolean("appearance_less_motion",false);
        String label=reduced?"С анимацией":"Без анимации";
        UiObject2 button=findVisible(label);
        assertNotNull("Motion action is not visible: "+label,button);
        Rect visible=button.getVisibleBounds();
        float density=activity.getActivity().getResources().getDisplayMetrics().density;
        assertTrue("Motion action is clipped: visible height="+visible.height(),
            visible.height()>=Math.round(40*density));
        button.click();
        device.waitForIdle();
        assertEquals(!reduced,activity.getActivity().getPreferences(Context.MODE_PRIVATE)
            .getBoolean("appearance_less_motion",reduced));
    }

    @Test public void fontPreviewAndThemeSelectionAreClickable()throws Exception {
        openDialog("showFontStudio");
        expect("Шрифты · оформление всего приложения");
        expect("Serif · классический");
        screenshot("font-choices");
        tap("Serif · классический");
        expect("Предпросмотр.");
        close();
        openDialog("appearanceDialog");
        expect("Тема оформления");
        tap("Графит · строгая тёмная");
        assertNotNull("Activity did not return from theme recreation",
            device.wait(Until.findObject(By.textContains("Мои переписки")),8000L));
        screenshot("theme-graphite");
    }
}

