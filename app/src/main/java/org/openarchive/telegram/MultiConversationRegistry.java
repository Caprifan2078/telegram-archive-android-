package org.openarchive.telegram;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** User-declared conversation collections: metadata only, never merges media or message IDs. */
final class MultiConversationRegistry {
    static final String PREF_KEY="multi_chat_groups";
    private static final int MAX_GROUPS=150, MAX_MEMBERS=30;

    private static String title(String value,String kind){
        if(value==null||value.trim().isEmpty()||value.trim().length()>80)
            throw new IllegalArgumentException(kind+": от 1 до 80 символов");
        return value.trim();
    }
    private static String chatKey(String key){
        if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Неверный идентификатор переписки");
        return key;
    }
    private static JSONArray parse(String raw){
        try{
            JSONArray groups=new JSONArray(raw==null?"[]":raw);
            if(groups.length()>MAX_GROUPS)throw new IllegalArgumentException("Слишком много групп");
            Set<String> ids=new HashSet<>(),members=new HashSet<>();
            for(int i=0;i<groups.length();i++){
                JSONObject g=groups.getJSONObject(i);
                String id=g.getString("id");
                if(!id.matches("[a-f0-9]{32}")||!ids.add(id))
                    throw new IllegalArgumentException("Неверный или повторный ID группы");
                title(g.getString("name"),"Название группы");
                JSONArray chats=g.getJSONArray("members");
                if(chats.length()>MAX_MEMBERS)throw new IllegalArgumentException("Слишком много источников");
                for(int j=0;j<chats.length();j++){
                    JSONObject entry=chats.getJSONObject(j);
                    if(!members.add(chatKey(entry.getString("key"))))
                        throw new IllegalArgumentException("Переписка числится в нескольких группах");
                    title(entry.getString("label"),"Название источника");
                }
            }
            return groups;
        }catch(Exception problem){
            throw new IllegalStateException("Список мультипереписок повреждён: данные оставлены без изменений",problem);
        }
    }
    static JSONArray read(SharedPreferences prefs){return parse(prefs.getString(PREF_KEY,"[]"));}
    private static void persist(SharedPreferences prefs,JSONArray groups){
        parse(groups.toString());
        if(!prefs.edit().putString(PREF_KEY,groups.toString()).commit())
            throw new IllegalStateException("Не удалось сохранить мультипереписки");
    }
    static JSONObject find(JSONArray groups,String id){
        if(id==null)return null;
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.optJSONObject(i);
            if(g!=null&&id.equals(g.optString("id")))return g;
        }
        return null;
    }
    static boolean memberOfAny(JSONArray groups,String key){
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.optJSONObject(i);if(g==null)continue;
            JSONArray arr=g.optJSONArray("members");if(arr==null)continue;
            for(int j=0;j<arr.length();j++){
                JSONObject member=arr.optJSONObject(j);
                if(member!=null&&key.equals(member.optString("key")))return true;
            }
        }
        return false;
    }
    static String create(SharedPreferences prefs,String name)throws Exception{
        name=title(name,"Название человека");
        JSONArray groups=read(prefs);
        if(groups.length()>=MAX_GROUPS)throw new IllegalArgumentException("Достигнут предел мультипереписок");
        String id=UUID.randomUUID().toString().replace("-","");
        JSONObject group=new JSONObject();
        group.put("id",id);group.put("name",name);group.put("members",new JSONArray());
        groups.put(group);persist(prefs,groups);
        return id;
    }
    static void add(SharedPreferences prefs,String groupId,String key,String label,Set<String> registered)throws Exception{
        key=chatKey(key);label=title(label,"Подпись источника");
        if(registered==null||!registered.contains(key))
            throw new IllegalArgumentException("Переписка отсутствует в приложении");
        JSONArray groups=read(prefs);
        JSONObject g=find(groups,groupId);
        if(g==null)throw new IllegalArgumentException("Группа не найдена");
        if(memberOfAny(groups,key))throw new IllegalArgumentException("Эта переписка уже включена в группу");
        JSONArray members=g.getJSONArray("members");
        if(members.length()>=MAX_MEMBERS)throw new IllegalArgumentException("Слишком много переписок в группе");
        JSONObject entry=new JSONObject();entry.put("key",key);entry.put("label",label);
        members.put(entry);persist(prefs,groups);
    }
    static void remove(SharedPreferences prefs,String id,String key)throws Exception{
        JSONArray groups=read(prefs);JSONObject group=find(groups,id);
        if(group==null)throw new IllegalArgumentException("Группа не найдена");
        JSONArray existing=group.getJSONArray("members"),remaining=new JSONArray();
        boolean found=false;
        for(int i=0;i<existing.length();i++){
            JSONObject entry=existing.getJSONObject(i);
            if(key.equals(entry.getString("key")))found=true;
            else remaining.put(entry);
        }
        if(!found)throw new IllegalArgumentException("Чат не входит в группу");
        group.put("members",remaining);persist(prefs,groups);
    }
    static void rename(SharedPreferences prefs,String id,String name)throws Exception{
        JSONArray groups=read(prefs);JSONObject group=find(groups,id);
        if(group==null)throw new IllegalArgumentException("Группа не найдена");
        group.put("name",title(name,"Название человека"));persist(prefs,groups);
    }
    static void delete(SharedPreferences prefs,String id)throws Exception{
        JSONArray groups=read(prefs),remaining=new JSONArray();boolean found=false;
        for(int i=0;i<groups.length();i++){
            JSONObject g=groups.getJSONObject(i);
            if(id.equals(g.getString("id")))found=true;else remaining.put(g);
        }
        if(!found)throw new IllegalArgumentException("Группа не найдена");
        // Only the grouping metadata is removed; individual chats and annotations are untouched.
        persist(prefs,remaining);
    }
    /** Each restored chat gets a fresh key; copy and translate declared group members safely. */
    static JSONArray remapGroups(String raw,Map<String,String> keyMap)throws Exception{
        JSONArray source=parse(raw),result=new JSONArray();
        Set<String> used=new HashSet<>();
        for(int i=0;i<source.length();i++){
            JSONObject old=source.getJSONObject(i);
            JSONArray nextMembers=new JSONArray(),members=old.getJSONArray("members");
            for(int j=0;j<members.length();j++){
                JSONObject member=members.getJSONObject(j);
                String mapped=keyMap.get(member.getString("key"));
                if(mapped==null)continue;
                chatKey(mapped);
                if(!used.add(mapped))throw new IllegalStateException("Повтор переписки при восстановлении");
                JSONObject item=new JSONObject();
                item.put("key",mapped);item.put("label",member.getString("label"));
                nextMembers.put(item);
            }
            if(nextMembers.length()==0)continue;
            JSONObject next=new JSONObject();
            next.put("id",UUID.randomUUID().toString().replace("-",""));
            next.put("name",old.getString("name"));next.put("members",nextMembers);
            result.put(next);
        }
        return parse(result.toString());
    }
    static JSONArray mergeForRestore(SharedPreferences prefs,String incoming,Map<String,String> keyMap)throws Exception{
        JSONArray kept=read(prefs),translated=remapGroups(incoming,keyMap);
        if(kept.length()+translated.length()>MAX_GROUPS)
            throw new IllegalStateException("Слишком много групп после восстановления");
        for(int i=0;i<translated.length();i++)kept.put(translated.getJSONObject(i));
        return parse(kept.toString());
    }
}
