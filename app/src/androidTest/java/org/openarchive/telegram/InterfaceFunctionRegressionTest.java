package org.openarchive.telegram;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class InterfaceFunctionRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private static Field field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object call(MainActivity a,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);return m.invoke(a);}
    private static View find(View view,String label){
        if(view instanceof TextView&&label.equals(((TextView)view).getText().toString()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    @Test public void toolsReturnToCurrentConversationAndFavoritesToMedia(){
        MainActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            field("home").set(a,false);
            String key=(String)field("activeKey").get(a);
            for(String destination:new String[]{"search","analytics","relationships","media"}){
                field("screen").set(a,destination);field("returnScreen").set(a,"");
                field("analyticsSection").set(a,"overview");field("historyEventsExpanded").set(a,false);
                assertEquals(Boolean.TRUE,call(a,"backWithinHistory"));
                assertEquals("chat",field("screen").get(a));assertEquals(false,field("home").get(a));
                assertEquals(key,field("activeKey").get(a));
            }
            field("screen").set(a,"favorites");field("mediaOffset").setInt(a,19);
            assertEquals(Boolean.TRUE,call(a,"backWithinHistory"));assertEquals("media",field("screen").get(a));
            assertEquals(19,field("mediaOffset").getInt(a));assertEquals("chat",field("returnScreen").get(a));
            field("screen").set(a,"search");call(a,"openChatMediaFromHeader");assertEquals("search",field("screen").get(a));
        }catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void mediaControlsDoNotOfferImpossibleGridOrMisleadingImport(){
        MainActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            field("home").set(a,false);field("screen").set(a,"media");field("mediaKind").set(a,"file");
            call(a,"configureBars");LinearLayout filter=(LinearLayout)field("filterBar").get(a);
            assertNull(find(filter,"Сетка"));assertNull(find(filter,"Список"));
            LinearLayout list=(LinearLayout)field("list").get(a);list.removeAllViews();call(a,"renderEmptyMediaState");
            assertNotNull(find(list,"Нет файлов этой категории"));
            assertNull(find(list,"Добавить медиа из экспорта"));assertNotNull(find(list,"Показать все файлы"));
            field("mediaKind").set(a,"all");list.removeAllViews();call(a,"renderEmptyMediaState");
            assertNotNull(find(list,"В переписке нет медиа"));assertNull(find(list,"Показать все файлы"));
        }catch(Exception e){throw new AssertionError(e);}});
    }
    @Test public void archiveTransitionImmediatelyRemovesOldViewsAndRestoresHeader(){
        MainActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            LinearLayout list=(LinearLayout)field("list").get(a);
            TextView previous=new TextView(a);previous.setText("OLD_ARCHIVE_SENTINEL");list.addView(previous);list.setTag("home-list");
            field("home").set(a,false);field("screen").set(a,"chat");field("activeName").set(a,"New synthetic chat");
            call(a,"clearArchiveTransition");
            assertEquals(0,list.getChildCount());assertNull(list.getTag());
            assertEquals("New synthetic chat",((TextView)field("title").get(a)).getText().toString());
            assertEquals(View.VISIBLE,((View)field("status").get(a)).getVisibility());
            assertEquals(View.GONE,((View)field("mediaRecycler").get(a)).getVisibility());
        }catch(Exception e){throw new AssertionError(e);}});
    }
}
