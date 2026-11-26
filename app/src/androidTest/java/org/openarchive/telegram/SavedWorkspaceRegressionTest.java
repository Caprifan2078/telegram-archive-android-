package org.openarchive.telegram;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import androidx.test.rule.ActivityTestRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class SavedWorkspaceRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private static Object get(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private static View find(View v,String label){
        if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=find(g.getChildAt(i),label);if(r!=null)return r;}}
        return null;
    }
    @Test public void notesAndFavoritesAreSeparateAndDraftSurvivesSwitching(){
        MainActivity a=rule.getActivity();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
            Method render=MainActivity.class.getDeclaredMethod("renderSaved");render.setAccessible(true);render.invoke(a);
            View composer=(View)get(a,"savedComposer");View root=a.getWindow().getDecorView();
            assertEquals(View.VISIBLE,composer.getVisibility());
            EditText input=(EditText)get(a,"noteInput");input.setText("QA synthetic unsaved draft");
            assertTrue(find(root,"Избранное из всех переписок").performClick());
            assertEquals(View.GONE,composer.getVisibility());
            assertNull(get(a,"noteInput"));
            assertEquals("Избранное",((TextView)get(a,"title")).getText().toString());
            assertTrue(find(root,"Личные записи и цитаты").performClick());
            assertEquals(View.VISIBLE,composer.getVisibility());
            assertEquals("QA synthetic unsaved draft",((EditText)get(a,"noteInput")).getText().toString());
            ((EditText)get(a,"noteInput")).setText("");
        }catch(Exception e){throw new AssertionError(e);}});
    }
}
