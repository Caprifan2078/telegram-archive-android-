package org.openarchive.telegram;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic home-screen grouping check, with no user exports or databases. */
@RunWith(AndroidJUnit4.class)
public final class MultiConversationHomeRegressionTest {
    @Rule public ActivityTestRule<MainActivity> rule=new ActivityTestRule<>(MainActivity.class);
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private Object field(MainActivity a,String name)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);
    }
    private void put(MainActivity a,String name,Object value)throws Exception{
        Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);
    }
    private void call(MainActivity a,String name)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);
    }
    private View find(View root,String label){
        if(root instanceof TextView&&label.equals(((TextView)root).getText().toString()))return root;
        if(root instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)root;
            for(int i=0;i<parent.getChildCount();i++){
                View child=find(parent.getChildAt(i),label);if(child!=null)return child;
            }
        }
        return null;
    }
    @Test public void personCardShowsInsteadOfDuplicateStandaloneSourceCards()throws Exception{
        MainActivity a=rule.getActivity();
        SharedPreferences prefs=a.getPreferences(android.content.Context.MODE_PRIVATE);
        String previousGroups=prefs.getString(MultiConversationRegistry.PREF_KEY,null);
        boolean showGrouped=prefs.getBoolean("show_grouped_individual_chats",false);
        JSONArray oldChats=(JSONArray)field(a,"archives");
        Object oldHome=field(a,"home"),oldScreen=field(a,"screen");
        String first=UUID.randomUUID().toString().replace("-","");
        String second=UUID.randomUUID().toString().replace("-","");
        JSONArray synthetic=new JSONArray();
        synthetic.put(new JSONObject().put("key",first).put("name","TG Вася").put("tree",""));
        synthetic.put(new JSONObject().put("key",second).put("name","Другая учётка").put("tree",""));
        try{
            prefs.edit().putString(MultiConversationRegistry.PREF_KEY,"[]")
                .putBoolean("show_grouped_individual_chats",false).commit();
            String id=MultiConversationRegistry.create(prefs,"Вася");
            MultiConversationRegistry.add(prefs,id,first,"Telegram",new HashSet<>(Arrays.asList(first,second)));
            MultiConversationRegistry.add(prefs,id,second,"Второй профиль",new HashSet<>(Arrays.asList(first,second)));
            inst.runOnMainSync(()->{
                try{
                    put(a,"archives",synthetic);put(a,"home",true);put(a,"screen","chat");
                    call(a,"renderHome");
                    View root=a.getWindow().getDecorView();
                    assertNotNull("Grouped person is visible",find(root,"Вася"));
                    assertNotNull(find(root,"2 переписки · нажми, чтобы выбрать источник"));
                    assertNull("Avoid a second duplicate standalone TG card",find(root,"TG Вася"));
                    assertNull("Avoid duplicate standalone second chat",find(root,"Другая учётка"));
                    assertNotNull("Compact reveal action",find(root,"Показать чаты"));
                    assertNotNull("Create action shares the same row",find(root,"＋ Группа"));
                    assertNull("No duplicate legacy comparison tools when a group is visible",
                        find(root,"Сравнить две переписки"));
                }catch(Exception e){throw new AssertionError(e);}
            });
        }finally{
            SharedPreferences.Editor restore=prefs.edit()
                .putBoolean("show_grouped_individual_chats",showGrouped);
            if(previousGroups==null)restore.remove(MultiConversationRegistry.PREF_KEY);
            else restore.putString(MultiConversationRegistry.PREF_KEY,previousGroups);
            restore.commit();
            inst.runOnMainSync(()->{
                try{
                    put(a,"archives",oldChats);put(a,"home",oldHome);put(a,"screen",oldScreen);
                    call(a,"showPage");
                }catch(Exception e){throw new AssertionError(e);}
            });
        }
    }
}
