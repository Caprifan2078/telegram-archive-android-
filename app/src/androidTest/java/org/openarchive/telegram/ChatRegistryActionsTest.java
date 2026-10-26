package org.openarchive.telegram;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ChatRegistryActionsTest {
    @Test public void hideOnlyChangesVisibleRegistryAndRetainsOtherChat()throws Exception{
        JSONArray input=new JSONArray();
        JSONObject one=new JSONObject().put("key","abc").put("name","Личная переписка")
            .put("bio","Проверка").put("tree","content://my_private_folder");
        JSONObject two=new JSONObject().put("key","xyz").put("name","Другая")
            .put("tree","local:/data/user/0/telegram_imports/xyz");
        input.put(one);input.put(two);
        JSONArray result=ChatRegistryActions.without(input,"abc");
        assertEquals(1,result.length());
        assertEquals("xyz",result.getJSONObject(0).getString("key"));
        assertEquals("local:/data/user/0/telegram_imports/xyz",result.getJSONObject(0).getString("tree"));
        assertEquals(2,input.length());
        assertEquals("content://my_private_folder",one.getString("tree"));
    }
    @Test public void hideLastChatIsEmptyAndNeverRestoresGhostEntry()throws Exception{
        JSONArray input=new JSONArray().put(new JSONObject().put("key","single"));
        assertEquals(0,ChatRegistryActions.without(input,"single").length());
        assertEquals(1,ChatRegistryActions.without(input,"unknown").length());
    }
}
