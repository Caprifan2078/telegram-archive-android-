package org.openarchive.telegram;

import java.util.*;

/** Pure decision logic, intentionally independent of Android for repeatable JVM tests. */
final class RestoreRecoveryPolicy {
    enum State { UNPUBLISHED, COMMITTED, AMBIGUOUS }
    static State state(Collection<String> expected,Set<String> published){
        if(expected==null||published==null)throw new IllegalArgumentException("Null journal state");
        Set<String> distinct=new HashSet<>();
        int present=0;
        for(String key:expected){
            if(!safeKey(key)||!distinct.add(key))throw new IllegalArgumentException("Malformed key list");
            if(published.contains(key))present++;
        }
        if(present==0)return State.UNPUBLISHED;
        if(present==expected.size())return State.COMMITTED;
        return State.AMBIGUOUS;
    }
    static boolean safeKey(String key){return key!=null&&key.matches("[a-f0-9]{32}");}
    static boolean safeStage(String name){return name!=null&&name.matches("restore-[a-f0-9]{32}");}
    static boolean safeAsset(String name){return name!=null&&name.matches("[a-zA-Z0-9_-]{1,96}\\.jpg");}
}
