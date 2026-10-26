package org.openarchive.telegram;
import java.util.Locale;
/** Safe and bounded offline search presentation helpers; no server APIs. */
final class DiscoveryRules {
    static final int PER_CHAT_LIMIT=36,GLOBAL_LIMIT=80,MAX_CHATS=25;
    static String likePattern(String raw){
        if(raw==null)throw new IllegalArgumentException("null");
        String s=raw.trim().toLowerCase(Locale.ROOT);
        if(s.length()>120)throw new IllegalArgumentException("query too long");
        return "%"+s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
    }
    static final class Hit {
        final String key,name,author,date,body;
        final long id;
        Hit(String key,String name,long id,String author,String date,String body){
            if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}")||id<=0)
                throw new IllegalArgumentException("invalid source");
            this.key=key;this.name=name==null?"":name;this.id=id;
            this.author=author==null?"":author;this.date=date==null?"":date;
            this.body=body==null?"":body;
        }
        String label(){
            String oneLine=body.replace('\n',' ').replace('\r',' ').trim();
            if(oneLine.length()>85)oneLine=oneLine.substring(0,85)+"…";
            return date+" · "+author+" · "+(oneLine.isEmpty()?"[без текста]":oneLine);
        }
    }
    private DiscoveryRules(){}
}
