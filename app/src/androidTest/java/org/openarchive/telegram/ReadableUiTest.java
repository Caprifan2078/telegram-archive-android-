package org.openarchive.telegram;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real views, synthetic counts only; screenshots never contain imported archives. */
public class ReadableUiTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private View text(View node,String value){
        if(node instanceof TextView&&((TextView)node).getText().toString().contains(value))return node;
        if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){
            View found=text(((ViewGroup)node).getChildAt(i),value);if(found!=null)return found;
        }return null;
    }
    private void capture(String name)throws Exception{
        inst.waitForIdleSync();Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);
        File dir=new File(inst.getTargetContext().getExternalFilesDir(null),"qa-screens");dir.mkdirs();
        try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
    }
    @Test public void exactCountsAndHelpAreVisible()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{
                Field field=MainActivity.class.getDeclaredField("list");field.setAccessible(true);LinearLayout list=(LinearLayout)field.get(a);list.removeAllViews();
                Method method=MainActivity.class.getDeclaredMethod("analyticsBarChart",LinearLayout.class,List.class,List.class,long[].class,int.class,int.class);method.setAccessible(true);
                method.invoke(a,list,Arrays.asList("Янв","Фев","Мар","Апр"),Arrays.asList("Январь 2026","Февраль 2026","Март 2026","Апрель 2026"),new long[]{0,1,10,123456},42,95);
                assertNotNull(text(list,"123456"));assertNotNull(text(list,"Январь 2026"));
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
            capture("statistics-exact-counts");
            scenario.onActivity(a->{try{Method m=MainActivity.class.getDeclaredMethod("showHelp");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new AssertionError(e);}});
            capture("help-categories");
            scenario.onActivity(a->{View category=text(a.getWindow().getDecorView(),"Первые шаги");assertNotNull(category);((View)category.getParent()).performClick();});
            capture("help-topics");
        }
        try(ActivityScenario<DemoChatActivity> scenario=ActivityScenario.launch(DemoChatActivity.class)){capture("tutorial");}
    }

    @Test public void unicodeHighlightsKeepValidAndroidSpanOffsets()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{try{
                Field mode=MainActivity.class.getDeclaredField("deep");mode.setAccessible(true);mode.set(a,false);
                Method highlight=MainActivity.class.getDeclaredMethod("highlighted",String.class,String.class);highlight.setAccessible(true);
                android.text.Spanned text=(android.text.Spanned)highlight.invoke(a,"İx 😀x","x");
                android.text.style.BackgroundColorSpan[] spans=text.getSpans(0,text.length(),android.text.style.BackgroundColorSpan.class);
                assertEquals(2,spans.length);
                assertEquals(1,text.getSpanStart(spans[0]));assertEquals(2,text.getSpanEnd(spans[0]));
                assertEquals(5,text.getSpanStart(spans[1]));assertEquals(6,text.getSpanEnd(spans[1]));
            }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
        }
    }
}
