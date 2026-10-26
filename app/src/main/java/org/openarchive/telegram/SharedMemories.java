package org.openarchive.telegram;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Two independent chat IDs on a shared, read-only timeline. Never merges source records. */
final class SharedMemories {
    static final int MAX_PER_CHAT=120;
    static final int MAX_VISIBLE=100;
    static final class Entry {
        final String archiveKey,archiveName,author,date,preview;
        final long messageId;
        final LocalDate day;
        final LocalDateTime timestamp;
        Entry(String key,String name,long id,String who,String rawDate,String body){
            if(key==null||key.isEmpty()||id<=0)throw new IllegalArgumentException("Invalid source");
            ChatTrends.Moment m=ChatTrends.parse(rawDate);
            if(m==null)throw new IllegalArgumentException("Date invalid");
            archiveKey=key;archiveName=name==null?"":name;messageId=id;
            author=who==null?"":who;date=rawDate==null?"":rawDate;
            day=m.day;timestamp=m.clock==null?m.day.atStartOfDay():m.clock;
            String s=body==null?"":body.replace('\r',' ').replace('\n',' ').trim();
            int length=s.codePointCount(0,s.length());
            preview=length>180?s.substring(0,s.offsetByCodePoints(0,180))+"…":s;
        }
    }
    static List<Entry> merge(List<Entry> a,List<Entry> b,int visible){
        if(a==null||b==null)throw new IllegalArgumentException("Missing timeline");
        int cap=Math.max(0,Math.min(visible,MAX_VISIBLE));
        ArrayList<Entry> combined=new ArrayList<>(a.size()+b.size());
        combined.addAll(a);combined.addAll(b);
        combined.sort(Comparator.comparing((Entry x)->x.timestamp).reversed()
            .thenComparing(x->x.archiveKey).thenComparingLong(x->x.messageId));
        if(combined.size()>cap)combined.subList(cap,combined.size()).clear();
        return combined;
    }
    /** Take equal quotas from both archives before interleaving, so busy chats cannot
     * erase the other chat from the shared memories overview. */
    static List<Entry> mergeBalanced(List<Entry> a,List<Entry> b,int perChat){
        if(a==null||b==null)throw new IllegalArgumentException("Missing timeline");
        int quota=Math.max(0,Math.min(perChat,MAX_PER_CHAT));
        Comparator<Entry> byDate=Comparator.comparing((Entry x)->x.timestamp).reversed()
            .thenComparing(x->x.archiveKey).thenComparingLong(x->x.messageId);
        ArrayList<Entry> sortedA=new ArrayList<>(a),sortedB=new ArrayList<>(b);
        sortedA.sort(byDate);sortedB.sort(byDate);
        ArrayList<Entry> result=new ArrayList<>();
        result.addAll(sortedA.subList(0,Math.min(sortedA.size(),quota)));
        result.addAll(sortedB.subList(0,Math.min(sortedB.size(),quota)));
        result.sort(byDate);
        return result;
    }
    static int month(Entry item){return item.day.getMonthValue();}
    static boolean belongs(Entry item,String first,String second){
        return item!=null&&(item.archiveKey.equals(first)||item.archiveKey.equals(second));
    }
    private SharedMemories(){}
}
