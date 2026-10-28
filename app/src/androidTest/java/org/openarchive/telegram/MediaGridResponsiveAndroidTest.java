package org.openarchive.telegram;

import android.app.Instrumentation;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MediaGridResponsiveAndroidTest {
    @Rule public ActivityTestRule<MainActivity> activity=new ActivityTestRule<>(MainActivity.class);

    @Test public void galleryUsesActualNarrowListWidthInsteadOfFullScreen()throws Exception{
        Instrumentation inst=InstrumentationRegistry.getInstrumentation();
        inst.runOnMainSync(()->{
            try{
                MainActivity a=activity.getActivity();
                Field listField=MainActivity.class.getDeclaredField("list");
                listField.setAccessible(true);
                LinearLayout list=(LinearLayout)listField.get(a);
                Field kindField=MainActivity.class.getDeclaredField("mediaKind");
                kindField.setAccessible(true);kindField.set(a,"image");
                // Simulate a narrow side-by-side content pane without modifying
                // display size or requiring private files on the emulator.
                int density=a.getResources().getDisplayMetrics().densityDpi;
                int contentWidth=Math.round(280*density/160f);
                list.setPadding(0,0,0,0);
                list.measure(View.MeasureSpec.makeMeasureSpec(contentWidth,View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));
                list.layout(0,0,contentWidth,Math.max(100,list.getMeasuredHeight()));
                MessageDb.Attachment first=new MessageDb.Attachment();
                first.kind="image";first.path="synthetic/a.jpg";first.date="2026-01-01";first.id=1;first.messageId=1;
                MessageDb.Attachment second=new MessageDb.Attachment();
                second.kind="image";second.path="synthetic/b.jpg";second.date="2026-01-01";second.id=2;second.messageId=2;
                Method render=MainActivity.class.getDeclaredMethod("renderMedia",java.util.List.class);
                render.setAccessible(true);render.invoke(a,Arrays.asList(first,second));
                GridLayout grid=null;
                for(int i=0;i<list.getChildCount();i++)
                    if(list.getChildAt(i) instanceof GridLayout)grid=(GridLayout)list.getChildAt(i);
                assertNotNull("Media gallery was not rendered",grid);
                assertEquals("Narrow gallery should fit two columns",2,grid.getColumnCount());
                assertEquals(2,grid.getChildCount());
                int occupied=0;
                for(int i=0;i<grid.getChildCount();i++){
                    GridLayout.LayoutParams lp=(GridLayout.LayoutParams)grid.getChildAt(i).getLayoutParams();
                    int span=lp.width+lp.leftMargin+lp.rightMargin;
                    occupied+=span;
                    assertEquals("Square media tile",lp.width,lp.height);
                }
                assertTrue("Grid tiles extend beyond content: "+occupied+"/"+contentWidth,occupied<=contentWidth);
            }catch(Exception e){throw new AssertionError(e);}
        });
    }
}
