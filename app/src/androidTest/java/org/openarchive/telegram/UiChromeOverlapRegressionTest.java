package org.openarchive.telegram;

import android.app.Instrumentation;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Main UI chrome must not obscure messages or stack redundant controls. */
@RunWith(AndroidJUnit4.class)
public final class UiChromeOverlapRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object get(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String name,Object v)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,v);
    }
    private void call(MainActivity a,String method)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(method);
        m.setAccessible(true);m.invoke(a);
    }
    private void idle(MainActivity a)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);
        ((ExecutorService)get(a,"worker")).execute(latch::countDown);
        assertTrue("UI worker hung",latch.await(20,TimeUnit.SECONDS));
        inst.waitForIdleSync();
    }
    @Test public void longChatReservesSpaceForScrollScrubberWithoutCoveringText()throws Exception{
        MainActivity a=rule.getActivity();idle(a);
        String key="qa_gutter_"+UUID.randomUUID().toString().replace("-","");
        MessageDb synthetic=new MessageDb(a,key);
        SQLiteDatabase d=synthetic.getWritableDatabase();
        for(int i=0;i<145;i++)synthetic.stage(d,
            i%2==0?"Аня":"Борис",String.format(java.util.Locale.ROOT,"2026-11-03 11:%02d:00",i%60),
            "Тестовое длинное сообщение на всю ширину пузырька №"+i,null,"");
        synthetic.publishStaged(d);
        Object oldDb=get(a,"db"),oldKey=get(a,"activeKey"),
            oldName=get(a,"activeName"),oldHome=get(a,"home"),oldScreen=get(a,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(a,"db",synthetic);put(a,"activeKey",key);
                    put(a,"activeName","QA long chat");put(a,"home",false);
                    put(a,"screen","chat");call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    View scrubber=(View)get(a,"verticalTimeline");
                    LinearLayout messages=(LinearLayout)get(a,"list");
                    View pager=(View)get(a,"bottomPager");
                    View tabs=(View)get(a,"bottomNav");
                    assertEquals("Long chat must have scrubber",View.VISIBLE,scrubber.getVisibility());
                    assertTrue("End gutter must keep message bubbles clear of scrubber",
                        messages.getPaddingRight()>=scrubber.getLayoutParams().width);
                    assertEquals("Chat must not show second bottom navigation",View.GONE,tabs.getVisibility());
                    assertEquals("Chat must not show redundant bottom page buttons",View.GONE,pager.getVisibility());
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    put(a,"db",oldDb);put(a,"activeKey",oldKey);
                    put(a,"activeName",oldName);put(a,"home",oldHome);
                    put(a,"screen",oldScreen);call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            synthetic.close();a.deleteDatabase("telegram_"+key+".db");
        }
    }
    @Test public void navigatingFromNotesNeverLeavesComposerOverHistoryOrCinema()throws Exception{
        MainActivity a=rule.getActivity();idle(a);
        Object oldHome=get(a,"home"),oldScreen=get(a,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(a,"home",false);put(a,"screen","saved");call(a,"showPage");
                    assertEquals(View.VISIBLE,((View)get(a,"savedComposer")).getVisibility());
                    put(a,"screen","cinema");call(a,"showPage");
                    assertEquals("Cinema must not inherit notes' fixed bottom editor",View.GONE,
                        ((View)get(a,"savedComposer")).getVisibility());
                    put(a,"screen","saved");call(a,"showPage");
                    put(a,"screen","relationships");call(a,"showPage");
                    assertEquals("History must not inherit notes' bottom editor",View.GONE,
                        ((View)get(a,"savedComposer")).getVisibility());
                }catch(Exception error){throw new AssertionError(error);}
            });
        }finally{
            inst.runOnMainSync(()->{
                try{put(a,"home",oldHome);put(a,"screen",oldScreen);call(a,"showPage");}
                catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
    @Test public void favoritesHasOneBackLinkInsteadOfCrowdedGalleryFilters()throws Exception{
        MainActivity a=rule.getActivity();idle(a);
        Object oldHome=get(a,"home"),oldScreen=get(a,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(a,"home",false);put(a,"screen","favorites");
                    call(a,"configureBars");
                    LinearLayout filter=(LinearLayout)get(a,"filterBar");
                    assertEquals("One clear action, not all gallery categories",1,filter.getChildCount());
                    assertEquals("‹ В медиатеку",
                        ((android.widget.TextView)filter.getChildAt(0)).getText().toString());
                    assertTrue(filter.getChildAt(0).isClickable());
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            inst.runOnMainSync(()->{
                try{put(a,"home",oldHome);put(a,"screen",oldScreen);call(a,"showPage");}
                catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
    @Test public void openingNotesDirectlyFromMediaMustHideGalleryOverlay()throws Exception{
        MainActivity a=rule.getActivity();idle(a);
        Object oldHome=get(a,"home"),oldScreen=get(a,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(a,"home",false);put(a,"screen","media");
                    // Simulate an already populated gallery before the drawer's
                    // direct renderSaved route (which does not pass showPage).
                    ((View)get(a,"mediaRecycler")).setVisibility(View.VISIBLE);
                    ((View)get(a,"scroll")).setVisibility(View.GONE);
                    call(a,"renderSaved");
                    assertEquals("Notes must hide previous media overlay",View.GONE,
                        ((View)get(a,"mediaRecycler")).getVisibility());
                    assertEquals("Notes list must become visible",View.VISIBLE,
                        ((View)get(a,"scroll")).getVisibility());
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            inst.runOnMainSync(()->{
                try{put(a,"home",oldHome);put(a,"screen",oldScreen);call(a,"showPage");}
                catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
    @Test public void notesComposerIsOneRowAndChatHeaderControlsCannotLeakIntoNotes()throws Exception{
        MainActivity a=rule.getActivity();idle(a);
        Object oldHome=get(a,"home"),oldScreen=get(a,"screen");
        try{
            inst.runOnMainSync(()->{
                try{
                    put(a,"home",false);put(a,"screen","chat");call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
            idle(a);
            inst.runOnMainSync(()->{
                try{
                    put(a,"home",false);put(a,"screen","saved");call(a,"showPage");
                    LinearLayout composer=(LinearLayout)get(a,"savedComposer");
                    assertEquals("Notes should have just one compact composer row",1,composer.getChildCount());
                    assertEquals("Composer must contain input, attach and send",
                        3,((ViewGroup)composer.getChildAt(0)).getChildCount());
                    assertEquals("Hidden irrelevant chat action menu",View.GONE,
                        ((View)get(a,"headerActions")).getVisibility());
                    assertEquals("Hidden unrelated PDF shortcut",View.GONE,
                        ((View)get(a,"pdfButton")).getVisibility());
                    assertEquals("No permanent status line above notes",View.GONE,
                        ((View)get(a,"status")).getVisibility());
                    assertEquals("Saved notes have no bottom pager",View.GONE,
                        ((View)get(a,"bottomPager")).getVisibility());
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            inst.runOnMainSync(()->{
                try{put(a,"home",oldHome);put(a,"screen",oldScreen);call(a,"showPage");}
                catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
}
