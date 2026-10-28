package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.LinearLayout;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** QA-00: real Android touches against empty and ephemeral synthetic chat states.
 * This test never imports or exports personal data, never contacts any service.
 */
@RunWith(AndroidJUnit4.class)
public class Qa00NavigationCoreTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final UiDevice device=UiDevice.getInstance(inst);

    private Object field(MainActivity a,String name)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(a);
    }
    private void set(MainActivity a,String name,Object v)throws Exception {
        Field f=MainActivity.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(a,v);
    }
    private void call(MainActivity a,String name)throws Exception {
        Method m=MainActivity.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(a);
    }
    private UiObject2 visible(String text) {
        UiObject2 v=device.wait(Until.findObject(By.textContains(text)),1800);
        if(v!=null){
            Rect b=v.getVisibleBounds();
            if(b!=null&&!b.isEmpty())return v;
        }
        return null;
    }
    private void tap(String label) {
        UiObject2 v=visible(label);
        assertNotNull("Real Android control inaccessible: "+label,v);
        v.click();
        device.waitForIdle();
    }
    private void shown(String label) {
        assertNotNull("Expected UI text missing: "+label,visible(label));
    }
    private void shownAfterDrawerScroll(String label) {
        // A prior search can leave the drawer below the next item.
        // Swipe only inside its visible scroll area, in both directions.
        for(int direction=0;direction<2;direction++){
            for(int i=0;i<12;i++){
                if(visible(label)!=null)return;
                Rect area=null;
                for(UiObject2 scrollable:device.findObjects(By.scrollable(true))){
                    Rect candidate=scrollable.getVisibleBounds();
                    if(candidate.width()>40&&candidate.height()>80){area=candidate;break;}
                }
                if(area==null)break;
                int x=area.centerX();
                int high=area.top+area.height()/4;
                int low=area.bottom-area.height()/4;
                device.swipe(x,direction==0?low:high,x,direction==0?high:low,20);
                device.waitForIdle();
            }
        }
        assertNotNull("Drawer item unreachable after scrolling: "+label,visible(label));
    }

    @Test public void emptyHomeOpensImportDialogAndClosesDrawerByTouchOutside() {
        shown("Мои переписки");
        shown("Добавить переписку");
        tap("Добавить переписку");
        shown("Добавить переписку"); // chooser is a real Android AlertDialog
        shown("Выбрать папку Telegram");
        device.pressBack();
        device.waitForIdle();
        shown("Мои переписки");
        tap("☰");
        shown("TELEGRAM ARCHIVE");
        // 348dp drawer leaves a safe dimmed region on Pixel 2.
        int width=device.getDisplayWidth(),height=device.getDisplayHeight();
        device.click(width-5,height/2);
        device.waitForIdle();
        assertNull("Touch on scrim must close side drawer",visible("TELEGRAM ARCHIVE"));
        shown("Мои переписки");
        tap("☰");
        shown("TELEGRAM ARCHIVE");
        device.pressBack();
        device.waitForIdle();
        assertNull("Android Back must close side drawer",visible("TELEGRAM ARCHIVE"));
        shown("Мои переписки");
    }

    @Test public void syntheticChatSwitchesTabsAndReturnsHome()throws Exception {
        MainActivity a=activity.getActivity();
        String key="qa00"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        synthetic.getWritableDatabase();
        Object dbOld=field(a,"db"),keyOld=field(a,"activeKey"),
            nameOld=field(a,"activeName"),homeOld=field(a,"home"),
            screenOld=field(a,"screen"),folderOld=field(a,"folder");
        try {
            inst.runOnMainSync(()->{
                try {
                    set(a,"db",synthetic);
                    set(a,"activeKey",key);
                    set(a,"activeName","QA00 синтетический чат");
                    set(a,"folder",null);
                    set(a,"home",false);
                    set(a,"screen","chat");
                    call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            inst.waitForIdleSync();
            shown("QA00 синтетический чат");
            shown("💬 Переписка");
            shown("▦ Медиа");
            tap("▦ Медиа");
            assertEquals("media",field(a,"screen"));
            shown("Фото, видео, голосовые и файлы");
            tap("💬 Переписка");
            assertEquals("chat",field(a,"screen"));
            shown("История сообщений · локально");
            // The help module should return to the originating chat, not home.
            inst.runOnMainSync(()->{
                try{call(a,"showHelp");}catch(Exception e){throw new AssertionError(e);}
            });
            shown("Помощь · Telegram Archive");
            device.pressBack();
            device.waitForIdle();
            assertEquals("chat",field(a,"screen"));
            assertEquals(false,field(a,"home"));
            // On a message-jump return path, the top-left Back control must
            // follow the same destination as Android's system Back.
            inst.runOnMainSync(()->{
                try{set(a,"returnScreen","analytics");}catch(Exception e){throw new AssertionError(e);}
            });
            tap("‹");
            assertEquals("Toolbar Back lost the source screen","analytics",field(a,"screen"));
            assertEquals(false,field(a,"home"));
            device.pressBack();
            device.waitForIdle();
            assertEquals(true,field(a,"home"));
            shown("Мои переписки");
        }finally{
            CountDownLatch idle=new CountDownLatch(1);
            ((ExecutorService)field(a,"worker")).execute(idle::countDown);
            assertTrue("Async UI/DB work did not finish",idle.await(12,TimeUnit.SECONDS));
            inst.runOnMainSync(()->{
                try {
                    set(a,"db",dbOld);
                    set(a,"activeKey",keyOld);
                    set(a,"activeName",nameOld);
                    set(a,"home",homeOld);
                    set(a,"screen",screenOld);
                    set(a,"folder",folderOld);
                    call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();
            a.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void repeatedChatAndMediaTabSwitchesKeepTheCorrectScreen()throws Exception {
        MainActivity a=activity.getActivity();
        String key="qa00tabs"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        synthetic.getWritableDatabase();
        Object dbOld=field(a,"db"),keyOld=field(a,"activeKey"),
            nameOld=field(a,"activeName"),homeOld=field(a,"home"),
            screenOld=field(a,"screen"),folderOld=field(a,"folder");
        try {
            inst.runOnMainSync(()->{
                try {
                    set(a,"db",synthetic);set(a,"activeKey",key);
                    set(a,"activeName","QA00 вкладки");set(a,"folder",null);
                    set(a,"home",false);set(a,"screen","chat");call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            inst.waitForIdleSync();
            for(int i=0;i<10;i++) {
                tap("▦ Медиа");
                assertEquals("media",field(a,"screen"));
                shown("Фото, видео, голосовые и файлы");
                tap("💬 Переписка");
                assertEquals("chat",field(a,"screen"));
                shown("История сообщений · локально");
            }
        }finally {
            CountDownLatch idle=new CountDownLatch(1);
            ((ExecutorService)field(a,"worker")).execute(idle::countDown);
            assertTrue("Async UI/DB work did not finish",idle.await(12,TimeUnit.SECONDS));
            inst.runOnMainSync(()->{
                try {
                    set(a,"db",dbOld);set(a,"activeKey",keyOld);
                    set(a,"activeName",nameOld);set(a,"home",homeOld);
                    set(a,"screen",screenOld);set(a,"folder",folderOld);call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void allConversationMenuDestinationsAreAccessibleOnSyntheticDb()throws Exception{
        MainActivity a=activity.getActivity();
        String key="qa00menu"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        synthetic.getWritableDatabase();
        Object dbOld=field(a,"db"),keyOld=field(a,"activeKey"),
            nameOld=field(a,"activeName"),homeOld=field(a,"home"),
            screenOld=field(a,"screen"),folderOld=field(a,"folder");
        try{
            String[][] destinations={
                {"⌕ Поиск","search"},
                {"▥ Анализ переписки","analytics"},
                {"♡ Хронология","relationships"},
                {"▤ Кинолента медиа","cinema"},
                {"▦ Медиатека","media"}
            };
            for(String[] destination:destinations){
                inst.runOnMainSync(()->{
                    try{
                        set(a,"db",synthetic);set(a,"activeKey",key);
                        set(a,"activeName","QA00 меню чата");
                        set(a,"folder",null);set(a,"home",false);
                        set(a,"screen","chat");call(a,"showPage");
                    }catch(Exception e){throw new AssertionError(e);}
                });
                inst.waitForIdleSync();
                tap("≡"); // header popup menu, not drawer
                shown(destination[0]);
                tap(destination[0]);
                assertEquals("Wrong destination for "+destination[0],
                    destination[1],field(a,"screen"));
            }
            // A second entry into the full tool drawer must exist from an
            // active conversation. The home-only hamburger is otherwise hidden.
            inst.runOnMainSync(()->{
                try{
                    set(a,"screen","chat");set(a,"home",false);call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            inst.waitForIdleSync();
            tap("≡");
            shown("Все инструменты");
            tap("Все инструменты");
            shown("TELEGRAM ARCHIVE");
            tap("Инструменты переписки");
            shownAfterDrawerScroll("В этот день");
            shownAfterDrawerScroll("Навигатор пауз");
            device.pressBack();device.waitForIdle();
        }finally{
            CountDownLatch idle=new CountDownLatch(1);
            ((ExecutorService)field(a,"worker")).execute(idle::countDown);
            assertTrue(idle.await(12,TimeUnit.SECONDS));
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",dbOld);set(a,"activeKey",keyOld);
                    set(a,"activeName",nameOld);set(a,"folder",folderOld);
                    set(a,"home",homeOld);set(a,"screen",screenOld);
                    call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
}

