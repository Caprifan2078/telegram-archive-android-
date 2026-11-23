package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;

/** Synthetic, offline group + backup regressions. Never reads a real conversation. */
@RunWith(AndroidJUnit4.class)
public final class MultiConversationRegistryRegressionTest {
    private String key(){return UUID.randomUUID().toString().replace("-","");}
    private SharedPreferences prefs(Context ctx){
        return ctx.getSharedPreferences("qa-multichat-"+key(),Context.MODE_PRIVATE);
    }
    @Test public void twoSourcesStaySeparateAndAnnotationsSurviveGroupChanges() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);
        String one=key(),two=key();
        MessageDb first=new MessageDb(ctx,one);
        try{
            SQLiteDatabase d=first.getWritableDatabase();
            long message=first.stage(d,"Вася","2026-01-01 12:00","Привет",null,"");
            first.publishStaged(d);
            assertTrue(first.togglePinned(message));
            long event=first.addRelationshipEvent("Событие","Важная заметка",message,0);
            String group=MultiConversationRegistry.create(p,"Вася");
            MultiConversationRegistry.add(p,group,one,"Telegram",new HashSet<>(Arrays.asList(one,two)));
            MultiConversationRegistry.add(p,group,two,"WhatsApp (моя подпись)",new HashSet<>(Arrays.asList(one,two)));
            JSONArray saved=MultiConversationRegistry.read(p);
            assertEquals(1,saved.length());
            JSONObject g=saved.getJSONObject(0);
            assertEquals("Вася",g.getString("name"));
            assertEquals(2,g.getJSONArray("members").length());
            assertEquals("WhatsApp (моя подпись)",g.getJSONArray("members").getJSONObject(1).getString("label"));
            assertTrue(MultiConversationRegistry.memberOfAny(saved,one));
            assertTrue(first.isPinned(message));
            assertEquals(event,first.relationshipEvents().get(0).id);
            MultiConversationRegistry.remove(p,group,one);
            assertTrue("Unlinking a chat must never delete message pins",first.isPinned(message));
            assertEquals(event,first.relationshipEvents().get(0).id);
            assertFalse(MultiConversationRegistry.memberOfAny(MultiConversationRegistry.read(p),one));
            MultiConversationRegistry.delete(p,group);
            assertEquals(0,MultiConversationRegistry.read(p).length());
        }finally{first.close();ctx.deleteDatabase("telegram_"+one+".db");p.edit().clear().commit();}
    }
    @Test public void duplicateMembershipRejectedAcrossGroups() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);String one=key();
        try{
            String g1=MultiConversationRegistry.create(p,"Первый");
            String g2=MultiConversationRegistry.create(p,"Второй");
            MultiConversationRegistry.add(p,g1,one,"Telegram",new HashSet<>(Arrays.asList(one)));
            try{MultiConversationRegistry.add(p,g2,one,"Второй профиль",new HashSet<>(Arrays.asList(one)));fail("Chat must not be ambiguously grouped");}
            catch(IllegalArgumentException expected){assertEquals(2,MultiConversationRegistry.read(p).length());}
            try{MultiConversationRegistry.add(p,g1,key(),"Неизвестный",new HashSet<>(Arrays.asList(one)));fail("Unknown chat must not be accepted");}
            catch(IllegalArgumentException expected){}
        }finally{p.edit().clear().commit();}
    }
    @Test public void remappedBackupKeepsGroupsAndDropsUnregisteredReferences() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);
        String old=key(),other=key(),fresh=key();
        try{
            String group=MultiConversationRegistry.create(p,"Вася");
            MultiConversationRegistry.add(p,group,old,"Telegram",new HashSet<>(Arrays.asList(old,other)));
            MultiConversationRegistry.add(p,group,other,"Другая учётка",new HashSet<>(Arrays.asList(old,other)));
            Map<String,String> mapping=new HashMap<>();mapping.put(old,fresh);
            JSONArray restored=MultiConversationRegistry.remapGroups(p.getString(MultiConversationRegistry.PREF_KEY,""),mapping);
            assertEquals(1,restored.length());
            JSONObject g=restored.getJSONObject(0);
            assertNotEquals(group,g.getString("id"));
            assertEquals(1,g.getJSONArray("members").length());
            assertEquals(fresh,g.getJSONArray("members").getJSONObject(0).getString("key"));
        }finally{p.edit().clear().commit();}
    }
    @Test public void corruptGroupMetadataMustNeverBeOverwritten() throws Exception {
        Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences p=prefs(ctx);
        try{
            p.edit().putString(MultiConversationRegistry.PREF_KEY,"not-json").commit();
            try{MultiConversationRegistry.create(p,"Вася");fail("Corruption must be visible");}
            catch(IllegalStateException expected){}
            assertEquals("not-json",p.getString(MultiConversationRegistry.PREF_KEY,""));
        }finally{p.edit().clear().commit();}
    }
}
