package org.openarchive.telegram;

import java.util.*;
/** Balanced read-only evidence for comparing two distinct archives. IDs are prompt-local. */
final class DualAnalystContext {
    static final class Link {
        final long promptId, messageId;
        final String archive, side;
        Link(long p,long m,String a,String s){promptId=p;messageId=m;archive=a;side=s;}
    }
    final DeepChatContext context;
    final Map<Long,Link> links;
    private DualAnalystContext(DeepChatContext context,Map<Long,Link> links){
        this.context=context;this.links=Collections.unmodifiableMap(new LinkedHashMap<>(links));
    }
    static DualAnalystContext combine(String aKey,DeepChatContext a,String bKey,DeepChatContext b){
        if(aKey==null||bKey==null||aKey.equals(bKey))throw new IllegalArgumentException("Two distinct archives required");
        List<DeepChatContext.Source> rows=new ArrayList<>();
        Map<Long,Link> links=new LinkedHashMap<>();
        for(int i=0;i<8;i++){
            append(rows,links,aKey,"А",a,i);
            append(rows,links,bKey,"Б",b,i);
        }
        String facts="Переписка А: "+DeepChatContext.clip(a.facts,380)+"; Переписка Б: "+DeepChatContext.clip(b.facts,380);
        return new DualAnalystContext(new DeepChatContext(rows,a.total+b.total,facts,
            "Отдельные выборки А и Б; максимум восемь цитат из каждой, не полный проход"),links);
    }
    private static void append(List<DeepChatContext.Source> rows,Map<Long,Link> links,String key,String side,DeepChatContext c,int i){
        int size=c.sources.size(), count=Math.min(size,8);
        if(i>=count)return;
        // Sample across the chronological evidence list, not only its earliest rows.
        int at=count<=1?0:i*(size-1)/(count-1);
        DeepChatContext.Source src=c.sources.get(at);
        long id=rows.size()+1;
        rows.add(new DeepChatContext.Source(id,side+": "+src.author,src.date,src.body));
        links.put(id,new Link(id,src.id,key,side));
    }
    String prompt(String question){
        return context.prompt("Сравни А и Б отдельно, приводи цитаты обеих сторон, не считай одинаковые имена одним человеком. "+DeepChatContext.clip(question,250));
    }
}
