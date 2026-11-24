package org.openarchive.telegram;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic 110-photo/video and dated-round gallery: no real chat data. */
@RunWith(AndroidJUnit4.class)
public final class MediaInfiniteGalleryRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private static Object field(Object instance,String name)throws Exception{
        Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);
    }
    private static void set(Object instance,String name,Object value)throws Exception{
        Field f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value);
    }
    private static void invoke(Object instance,String name,Class<?>[] signature,Object...args)throws Exception{
        Method m=instance.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);m.invoke(instance,args);
    }
    private static void onUi(MainActivity activity,Runnable run){
        InstrumentationRegistry.getInstrumentation().runOnMainSync(run);
    }
    @Test public void infiniteMixedGalleryZoomAndRoundDates()throws Exception{
        MainActivity activity=rule.getActivity();
        MessageDb original=(MessageDb)field(activity,"db");
        boolean oldHome=(boolean)field(activity,"home");
        String oldScreen=(String)field(activity,"screen"),oldKind=(String)field(activity,"mediaKind");
        String key="qa_gallery_"+UUID.randomUUID().toString().replace("-","");
        MessageDb database=new MessageDb(activity,key);
        try{
            SQLiteDatabase sqlite=database.getWritableDatabase();
            database.clearStage(sqlite);
            for(int n=0;n<110;n++){
                long id=database.stage(sqlite,"QA Gallery","2026-09-01 13:10:00",
                    "Медиа "+n,null,"");
                database.stageAttachment(sqlite,id,"photos/"+n+(n%4==0?".mp4":".jpg"),n%4==0?"video":"image");
            }
            for(int n=0;n<9;n++){
                String date=n<5?"2026-09-01 14:00:00":n<8?"2026-09-02 14:00:00":"2026-09-04 14:00:00";
                long id=database.stage(sqlite,"QA Rounds",date,"Кружок "+n,null,"");
                database.stageAttachment(sqlite,id,"round_video/"+n+".mp4","round");
            }
            database.publishStaged(sqlite);
            assertEquals(110,database.mediaCount("visual"));
            assertEquals(9,database.mediaCount("round"));
            onUi(activity,()->{
                try{
                    set(activity,"db",database);set(activity,"home",false);
                    set(activity,"screen","media");set(activity,"mediaKind","visual");
                    invoke(activity,"showPage",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            waitFor(activity,"mediaLoaded",30);
            RecyclerView gallery=(RecyclerView)field(activity,"mediaRecycler");
            assertEquals(View.VISIBLE,gallery.getVisibility());
            assertEquals(View.GONE,((View)field(activity,"bottomPager")).getVisibility());
            onUi(activity,()->gallery.scrollToPosition(29));
            waitFor(activity,"mediaLoaded",60);
            onUi(activity,()->gallery.scrollToPosition(59));
            waitFor(activity,"mediaLoaded",90);
            onUi(activity,()->{
                try{invoke(activity,"changeMediaColumns",new Class<?>[]{int.class},6);}
                catch(Exception e){throw new AssertionError(e);}
            });
            assertEquals(9,field(activity,"mediaColumns"));
            onUi(activity,()->{
                try{invoke(activity,"changeMediaColumns",new Class<?>[]{int.class},-20);}
                catch(Exception e){throw new AssertionError(e);}
            });
            assertEquals(2,field(activity,"mediaColumns"));
            onUi(activity,()->{
                try{
                    set(activity,"mediaKind","round");
                    // Real filter taps reset the saved absolute media offset.
                    set(activity,"mediaOffset",0);
                    activity.getPreferences(Context.MODE_PRIVATE).edit()
                        .putBoolean("gallery_grid_round",true).commit();
                    invoke(activity,"showPage",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            waitFor(activity,"mediaLoaded",9);
            Object adapter=field(activity,"mediaAdapter");
            Field entries=adapter.getClass().getDeclaredField("entries");
            entries.setAccessible(true);
            java.util.List<?> shown=(java.util.List<?>)entries.get(adapter);
            assertTrue("01 September group missing",shown.contains("01.09.2026")||
                shown.toString().contains("01.09.2026")||shown.toString().contains("2026-09-01"));
            assertEquals("Three distinct days, not calendar fillers",12,shown.size());
        }finally{
            onUi(activity,()->{
                try{
                    set(activity,"db",original);set(activity,"home",oldHome);
                    set(activity,"screen",oldScreen);set(activity,"mediaKind",oldKind);
                    invoke(activity,"renderHome",new Class<?>[0]);
                }catch(Exception e){throw new AssertionError(e);}
            });
            database.close();activity.deleteDatabase("telegram_"+key+".db");
        }
    }
    private void waitFor(MainActivity activity,String name,int minimum)throws Exception{
        long until=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<until){
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            if((int)field(activity,name)>=minimum)return;
            Thread.sleep(150);
        }
        fail("Gallery did not incrementally load "+minimum+": "+field(activity,name));
    }
    @Test public void importHasVisibleProgressAndCanBeCleared()throws Exception{
        MainActivity activity=rule.getActivity();
        onUi(activity,()->{
            try{invoke(activity,"beginImportProgress",new Class<?>[]{String.class},"Читаю архив…");}
            catch(Exception e){throw new AssertionError(e);}
        });
        assertEquals(View.VISIBLE,((View)field(activity,"importProgressPanel")).getVisibility());
        onUi(activity,()->{
            try{invoke(activity,"updateImportProgress",new Class<?>[]{String.class},"Считано 30 сообщений");}
            catch(Exception e){throw new AssertionError(e);}
        });
        assertEquals("Считано 30 сообщений",
            ((android.widget.TextView)field(activity,"importProgressText")).getText().toString());
        onUi(activity,()->{
            try{invoke(activity,"endImportProgress",new Class<?>[0]);}
            catch(Exception e){throw new AssertionError(e);}
        });
        assertEquals(View.GONE,((View)field(activity,"importProgressPanel")).getVisibility());
    }
}
