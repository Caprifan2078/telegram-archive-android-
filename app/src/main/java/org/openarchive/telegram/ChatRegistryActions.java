package org.openarchive.telegram;

import org.json.JSONArray;
import org.json.JSONObject;

/** Changes only visible registry metadata. Has no filesystem/database operations. */
final class ChatRegistryActions {
    private ChatRegistryActions(){}
    static JSONArray without(JSONArray existing,String key){
        JSONArray result=new JSONArray();
        if(existing==null||key==null)return result;
        for(int i=0;i<existing.length();i++){
            JSONObject chat=existing.optJSONObject(i);
            if(chat!=null&&!key.equals(chat.optString("key")))result.put(chat);
        }
        return result;
    }
}
