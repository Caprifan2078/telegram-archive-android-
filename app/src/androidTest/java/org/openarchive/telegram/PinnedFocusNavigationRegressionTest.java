package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.app.Instrumentation;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
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

/** Regression: jumping to any ordinary result used to rotate the pinned banner. */
@RunWith(AndroidJUnit4.class)
public class PinnedFocusNavigationRegressionTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    private static Object get(MainActivity a,String field)throws Exception{
        Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);return f.get(a);
    }
    private static void set(MainActivity a,String field,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);f.set(a,value);
    }
    private static void barrier(ExecutorService worker)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        worker.execute(done::countDown);
        assertTrue("UI worker remained blocked",done.await(6,TimeUnit.SECONDS));
    }
    @Test public void rejectedReimportShowsPersistentExplanation()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        MainActivity a=activity.getActivity();Context ctx=inst.getTargetContext();
        ExecutorService worker=(ExecutorService)get(a,"worker");barrier(worker);inst.waitForIdleSync();
        String key="importdialog_"+UUID.randomUUID().toString().replace("-","");
        java.io.File folder=new java.io.File(ctx.getCacheDir(),key);assertTrue(folder.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"messages.html"))) {
            out.write(("<div class='history'><div class='message default' id='message1'>"+
                "<div class='body'><div class='from_name'>Other</div><div class='text'>Changed</div></div></div></div>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        Object oldFolder=get(a,"folder");String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen");
        boolean oldHome=(boolean)get(a,"home");
        try {
            SQLiteDatabase sql=fresh.getWritableDatabase();
            long id=fresh.stage(sql,"Original","2024-01-01","Saved",null,"");fresh.publishStaged(sql);fresh.toggleFavorite(id);
            inst.runOnMainSync(()->{try {
                set(a,"db",fresh);set(a,"folder",new DocumentFolder(ctx.getContentResolver(),folder));
                set(a,"activeKey",key);set(a,"screen","chat");set(a,"home",false);
                Method m=MainActivity.class.getDeclaredMethod("importArchive");m.setAccessible(true);m.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();barrier(worker);inst.waitForIdleSync();
            android.view.accessibility.AccessibilityNodeInfo root=inst.getUiAutomation().getRootInActiveWindow();
            assertNotNull(root);
            assertFalse("Import error must remain visible",root.findAccessibilityNodeInfosByText("Импорт не завершён").isEmpty());
            assertEquals(1,fresh.totalMessages());assertEquals(1,fresh.count("",false,"",false,true));
        } finally {
            barrier(worker);inst.runOnMainSync(()->{try {
                set(a,"db",previous);set(a,"folder",oldFolder);set(a,"activeKey",oldKey);
                set(a,"screen",oldScreen);set(a,"home",oldHome);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");ArchiveFileImport.deleteTree(folder);
        }
    }

    @Test public void ordinaryJumpLeavesPinnedSelectionUnchanged()throws Exception{
        final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        final MainActivity a=activity.getActivity();
        final Context ctx=inst.getTargetContext();
        final ExecutorService worker=(ExecutorService)get(a,"worker");
        barrier(worker);inst.waitForIdleSync();
        final String key="pinnednav_"+UUID.randomUUID().toString().replace("-","");
        final MessageDb fresh=new MessageDb(ctx,key);
        final MessageDb previous=(MessageDb)get(a,"db");
        final String originalKey=(String)get(a,"activeKey");
        final String originalScreen=(String)get(a,"screen");
        final boolean originalHome=(boolean)get(a,"home");
        final long originalFocus=(long)get(a,"pinnedFocus");
        long first,middle,last;
        try{
            SQLiteDatabase sqlite=fresh.getWritableDatabase();
            fresh.clearStage(sqlite);
            first=fresh.stage(sqlite,"A","2026-01-01","Synthetic pinned one",null,"");
            middle=fresh.stage(sqlite,"B","2026-01-02","Synthetic ordinary result",null,"");
            last=fresh.stage(sqlite,"C","2026-01-03","Synthetic pinned two",null,"");
            fresh.publishStaged(sqlite);
            fresh.togglePinned(first);fresh.togglePinned(last);
            assertEquals(2,fresh.pinned().size());
            final long wanted=last;
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",fresh);set(a,"activeKey",key);
                    set(a,"screen","chat");set(a,"home",false);
                    set(a,"pinnedFocus",wanted);
                    Method jump=MainActivity.class.getDeclaredMethod("jump",long.class,String.class);
                    jump.setAccessible(true);jump.invoke(a,middle,"");
                }catch(Exception e){throw new AssertionError(e);}
            });
            barrier(worker);
            inst.waitForIdleSync();
            assertEquals("Jump should highlight original message",middle,(long)get(a,"highlightId"));
            assertEquals("Ordinary jump rotated pinned bar",wanted,(long)get(a,"pinnedFocus"));
        }finally{
            barrier(worker);
            inst.runOnMainSync(()->{
                try{
                    set(a,"db",previous);set(a,"activeKey",originalKey);
                    set(a,"screen",originalScreen);set(a,"home",originalHome);
                    set(a,"pinnedFocus",originalFocus);
                }catch(Exception ex){throw new AssertionError(ex);}
            });
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void returnFromResultRestoresQueryAndScroll()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        MainActivity a=activity.getActivity();Context ctx=inst.getTargetContext();
        ExecutorService worker=(ExecutorService)get(a,"worker");
        barrier(worker);inst.waitForIdleSync();
        String key="searchnav_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen");
        boolean oldHome=(boolean)get(a,"home");String oldQuery=(String)get(a,"q");
        long wanted=0;
        try{
            SQLiteDatabase sql=fresh.getWritableDatabase();
            sql.beginTransaction();
            try{
                for(int i=0;i<80;i++){
                    long id=fresh.stage(sql,"Synthetic","2026-01-01 12:00","uniquequery result "+i,null,"");
                    if(i==20)wanted=id;
                }
                sql.setTransactionSuccessful();
            }finally{sql.endTransaction();}
            fresh.publishStaged(sql);
            final long target=wanted;
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);
                set(a,"screen","search");set(a,"q","uniquequery");set(a,"deep",false);
                Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            final int[] y={0}; final String[] before={""};
            inst.runOnMainSync(()->{try{
                android.widget.ScrollView view=(android.widget.ScrollView)get(a,"scroll");
                view.scrollTo(0,view.getChildAt(0).getHeight()/3);y[0]=view.getScrollY();
                assertTrue("Synthetic results should be scrollable",y[0]>0);
                before[0]="content="+view.getChildAt(0).getHeight()+" viewport="+view.getHeight()+
                    " loaded="+get(a,"searchLoaded")+" offset="+get(a,"searchOffset")+" total="+get(a,"total");
                Method jump=MainActivity.class.getDeclaredMethod("jump",long.class,String.class);
                jump.setAccessible(true);jump.invoke(a,target,"search");
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();barrier(worker);inst.waitForIdleSync();
            assertEquals("chat",get(a,"screen"));
            inst.runOnMainSync(()->{try{
                Method back=MainActivity.class.getDeclaredMethod("restoreReturn");back.setAccessible(true);back.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("search",get(a,"screen"));assertEquals("uniquequery",get(a,"q"));
            inst.runOnMainSync(()->{try{
                android.widget.ScrollView view=(android.widget.ScrollView)get(a,"scroll");
                String after="content="+view.getChildAt(0).getHeight()+" viewport="+view.getHeight()+
                    " loaded="+get(a,"searchLoaded")+" offset="+get(a,"searchOffset")+" total="+get(a,"total");
                assertEquals("Result position must be restored; before "+before[0]+"; after "+after,y[0],view.getScrollY());
            }catch(Exception e){throw new AssertionError(e);}});
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);
                set(a,"home",oldHome);set(a,"q",oldQuery);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void switchingModesKeepsUnsavedDeepFilters()throws Exception {
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        MainActivity a=activity.getActivity();Context ctx=inst.getTargetContext();
        ExecutorService worker=(ExecutorService)get(a,"worker");barrier(worker);inst.waitForIdleSync();
        String key="mode_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen");
        boolean oldHome=(boolean)get(a,"home"),oldDeep=(boolean)get(a,"deep");
        try {
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);
                set(a,"screen","search");set(a,"deep",true);
                Method bars=MainActivity.class.getDeclaredMethod("configureBars");bars.setAccessible(true);bars.invoke(a);
                ((android.widget.EditText)get(a,"authorInput")).setText("  Мария  ");
                ((android.widget.CheckBox)get(a,"mediaOnly")).setChecked(true);
                ((android.widget.Button)get(a,"quickButton")).performClick();
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("Мария",get(a,"byAuthor"));assertEquals(true,get(a,"onlyMedia"));
            inst.runOnMainSync(()->{try{((android.widget.Button)get(a,"deepButton")).performClick();}
                catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            inst.runOnMainSync(()->{try{
                assertEquals("Мария",((android.widget.EditText)get(a,"authorInput")).getText().toString());
                assertTrue(((android.widget.CheckBox)get(a,"mediaOnly")).isChecked());
            }catch(Exception e){throw new AssertionError(e);}});
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);
                set(a,"home",oldHome);set(a,"deep",oldDeep);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    private static android.view.View described(android.view.View root,String value){
        if(root.getContentDescription()!=null&&root.getContentDescription().toString().startsWith(value))return root;
        if(root instanceof android.view.ViewGroup){
            android.view.ViewGroup group=(android.view.ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                android.view.View found=described(group.getChildAt(i),value);if(found!=null)return found;
            }
        }
        return null;
    }
    @Test public void calendarDayOpensFirstMessageAndReturnsToAnalysis()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity a=activity.getActivity();
        Context ctx=inst.getTargetContext();ExecutorService worker=(ExecutorService)get(a,"worker");
        barrier(worker);inst.waitForIdleSync();
        String key="calendar_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen");
        boolean oldHome=(boolean)get(a,"home");long first;
        try{
            SQLiteDatabase sql=fresh.getWritableDatabase();fresh.clearStage(sql);
            fresh.stage(sql,"A","2024-02-28 10:00","Other day",null,"");
            first=fresh.stage(sql,"A","2024-02-29 10:00","Leap day first",null,"");
            fresh.stage(sql,"B","2024-02-29 11:00","Leap day second",null,"");
            fresh.publishStaged(sql);final long wanted=first;
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);
                set(a,"screen","analytics");set(a,"analyticsSelectedAuthor","");
                android.widget.LinearLayout list=(android.widget.LinearLayout)get(a,"list");list.removeAllViews();
                ChatTrends.Activity data=new ChatTrends.Activity();
                data.days.put(java.time.LocalDate.of(2024,2,29),2L);
                Method render=MainActivity.class.getDeclaredMethod("renderAnnualCalendar",ChatTrends.Activity.class);
                render.setAccessible(true);render.invoke(a,data);
                android.view.View day=described(list,"2024-02-29:");assertNotNull("Leap day missing",day);
                assertTrue("Calendar day must be actionable",day.performClick());
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();barrier(worker);inst.waitForIdleSync();barrier(worker);inst.waitForIdleSync();
            assertEquals("chat",get(a,"screen"));assertEquals("analytics",get(a,"returnScreen"));
            assertEquals("First message on the exact day",wanted,(long)get(a,"highlightId"));
            inst.runOnMainSync(()->{try{
                Method back=MainActivity.class.getDeclaredMethod("restoreReturn");back.setAccessible(true);back.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();assertEquals("analytics",get(a,"screen"));
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);set(a,"home",oldHome);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void analyticsWordSearchReturnsThroughMessageToAnalytics()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity a=activity.getActivity();
        Context ctx=inst.getTargetContext();ExecutorService worker=(ExecutorService)get(a,"worker");barrier(worker);inst.waitForIdleSync();
        String key="wordroundtrip_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen"),oldAuthor=(String)get(a,"byAuthor");
        String oldQuery=(String)get(a,"q"),oldReturn=(String)get(a,"returnScreen");
        boolean oldHome=(boolean)get(a,"home"),oldDeep=(boolean)get(a,"deep"),oldMedia=(boolean)get(a,"onlyMedia");
        boolean oldParent=(boolean)get(a,"searchReturnsToAnalytics");
        try{
            SQLiteDatabase sql=fresh.getWritableDatabase();long id=fresh.stage(sql,"Alice","2024-01-01","magicword synthetic",null,"");fresh.publishStaged(sql);
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);set(a,"screen","analytics");
                set(a,"byAuthor","Bob");set(a,"onlyMedia",true);
                Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            inst.runOnMainSync(()->{try{
                Method open=MainActivity.class.getDeclaredMethod("openWordSearchFromAnalytics",String.class);
                open.setAccessible(true);open.invoke(a,"magicword");
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("search",get(a,"screen"));assertEquals("",get(a,"byAuthor"));assertEquals(false,get(a,"onlyMedia"));
            assertEquals("analytics",get(a,"returnScreen"));
            inst.runOnMainSync(()->{try{
                assertNotNull(described((android.view.View)get(a,"searchArea"),"Вернуться к анализу"));
                Method jump=MainActivity.class.getDeclaredMethod("jump",long.class,String.class);jump.setAccessible(true);jump.invoke(a,id,"search");
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();barrier(worker);inst.waitForIdleSync();
            assertEquals("chat",get(a,"screen"));
            inst.runOnMainSync(()->{try{
                Method back=MainActivity.class.getDeclaredMethod("restoreReturn");back.setAccessible(true);back.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("search",get(a,"screen"));assertEquals("analytics",get(a,"returnScreen"));assertEquals("magicword",get(a,"q"));
            inst.runOnMainSync(()->{try{
                Method back=MainActivity.class.getDeclaredMethod("restoreReturn");back.setAccessible(true);back.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();assertEquals("analytics",get(a,"screen"));
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);set(a,"home",oldHome);
                set(a,"deep",oldDeep);set(a,"onlyMedia",oldMedia);set(a,"byAuthor",oldAuthor);set(a,"q",oldQuery);
                set(a,"returnScreen",oldReturn);set(a,"searchReturnsToAnalytics",oldParent);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void deepSearchResetClearsFiltersWithoutClearingQuery()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity a=activity.getActivity();
        Context ctx=inst.getTargetContext();ExecutorService worker=(ExecutorService)get(a,"worker");barrier(worker);inst.waitForIdleSync();
        String key="deepreset_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen"),oldAuthor=(String)get(a,"byAuthor"),oldQuery=(String)get(a,"q");
        boolean oldHome=(boolean)get(a,"home"),oldDeep=(boolean)get(a,"deep"),oldMedia=(boolean)get(a,"onlyMedia");
        try{
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);set(a,"screen","search");
                set(a,"deep",true);set(a,"byAuthor","Alice");set(a,"onlyMedia",true);set(a,"q","example");
                Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            try(android.os.ParcelFileDescriptor shell=inst.getUiAutomation().executeShellCommand(
                    "screencap -p /sdcard/Download/ta_synthetic_deep_search.png");
                java.io.FileInputStream output=new java.io.FileInputStream(shell.getFileDescriptor())){
                byte[] drain=new byte[1024];while(output.read(drain)!=-1){}
            }
            inst.runOnMainSync(()->{try{
                android.view.View reset=described((android.view.View)get(a,"searchArea"),"Сбросить фильтры поиска");
                assertNotNull(reset);assertTrue(reset.performClick());
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("",get(a,"byAuthor"));assertEquals(false,get(a,"onlyMedia"));assertEquals("example",get(a,"q"));
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);set(a,"home",oldHome);
                set(a,"deep",oldDeep);set(a,"onlyMedia",oldMedia);set(a,"byAuthor",oldAuthor);set(a,"q",oldQuery);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void quickSearchShowsAndCanResetHiddenDeepFilters()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity a=activity.getActivity();
        Context ctx=inst.getTargetContext();ExecutorService worker=(ExecutorService)get(a,"worker");barrier(worker);inst.waitForIdleSync();
        String key="filtervisibility_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen"),oldAuthor=(String)get(a,"byAuthor");
        boolean oldHome=(boolean)get(a,"home"),oldDeep=(boolean)get(a,"deep"),oldMedia=(boolean)get(a,"onlyMedia");
        try{
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);
                set(a,"screen","search");set(a,"deep",false);set(a,"byAuthor","Alice");set(a,"onlyMedia",true);
                Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            inst.runOnMainSync(()->{try{
                assertNotNull("Saved deep filters must be explained in quick mode",
                    described((android.view.View)get(a,"searchArea"),"Фильтры глубокого поиска (в быстром режиме не применяются)"));
                android.view.View reset=described((android.view.View)get(a,"searchArea"),"Сбросить фильтры поиска");
                assertNotNull("Saved filters need a visible reset",reset);assertTrue(reset.performClick());
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            assertEquals("",get(a,"byAuthor"));assertEquals(false,get(a,"onlyMedia"));
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);
                set(a,"home",oldHome);set(a,"deep",oldDeep);set(a,"onlyMedia",oldMedia);set(a,"byAuthor",oldAuthor);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }

    @Test public void searchButtonsSubmitAndClearText()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();MainActivity a=activity.getActivity();
        Context ctx=inst.getTargetContext();ExecutorService worker=(ExecutorService)get(a,"worker");
        barrier(worker);inst.waitForIdleSync();
        String key="searchbuttons_"+UUID.randomUUID().toString().replace("-","");
        MessageDb fresh=new MessageDb(ctx,key),previous=(MessageDb)get(a,"db");
        String oldKey=(String)get(a,"activeKey"),oldScreen=(String)get(a,"screen");
        boolean oldHome=(boolean)get(a,"home");
        try{
            inst.runOnMainSync(()->{try{
                set(a,"db",fresh);set(a,"activeKey",key);set(a,"home",false);set(a,"screen","search");
                Method show=MainActivity.class.getDeclaredMethod("showPage");show.setAccessible(true);show.invoke(a);
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();
            inst.runOnMainSync(()->{try{
                ((android.widget.EditText)get(a,"searchInput")).setText("  Тест  ");
            }catch(Exception e){throw new AssertionError(e);}});
            inst.waitForIdleSync();
            try(android.os.ParcelFileDescriptor shell=inst.getUiAutomation().executeShellCommand(
                    "screencap -p /sdcard/Download/ta_synthetic_search.png");
                java.io.FileInputStream output=new java.io.FileInputStream(shell.getFileDescriptor())){
                byte[] drain=new byte[1024];while(output.read(drain)!=-1){}
            }
            inst.runOnMainSync(()->{try{
                android.view.View submit=described((android.view.View)get(a,"searchArea"),"Выполнить поиск");
                assertNotNull(submit);assertTrue(submit.performClick());
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();assertEquals("Тест",get(a,"q"));
            inst.runOnMainSync(()->{try{
                android.view.View clear=described((android.view.View)get(a,"searchArea"),"Очистить текст поиска");
                assertNotNull(clear);assertTrue(clear.performClick());
            }catch(Exception e){throw new AssertionError(e);}});
            barrier(worker);inst.waitForIdleSync();assertEquals("",get(a,"q"));
        }finally{
            barrier(worker);inst.runOnMainSync(()->{try{
                set(a,"db",previous);set(a,"activeKey",oldKey);set(a,"screen",oldScreen);set(a,"home",oldHome);
            }catch(Exception e){throw new AssertionError(e);}});
            fresh.close();ctx.deleteDatabase("telegram_"+key+".db");
        }
    }
}
