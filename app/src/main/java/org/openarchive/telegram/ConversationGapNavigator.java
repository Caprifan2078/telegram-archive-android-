package org.openarchive.telegram;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Bounded, deterministic conversation pauses; never imply missing export data. */
final class ConversationGapNavigator {
    static final int MAX_SCAN=100000;
    static final int TOP=12;
    static final int MIN_DAYS=7;
    static final class Gap {
        final LocalDate from,to;
        final long fromId,toId;
        final long days;
        Gap(LocalDate from,LocalDate to,long fromId,long toId,long days){
            this.from=from;this.to=to;this.fromId=fromId;this.toId=toId;this.days=days;
        }
    }
    private final ArrayList<Gap> top=new ArrayList<>();
    private LocalDate previous;
    private long previousId;
    private int scanned,invalid,backwards;
    void accept(long id,LocalDate day){
        scanned++;
        if(day==null||id<=0){invalid++;return;}
        if(previous!=null){
            long diff=ChronoUnit.DAYS.between(previous,day);
            if(diff<0){backwards++;return;}
            if(diff>=MIN_DAYS){
                top.add(new Gap(previous,day,previousId,id,diff));
                top.sort(Comparator.comparingLong((Gap g)->g.days).reversed()
                    .thenComparing(g->g.from));
                if(top.size()>TOP)top.remove(top.size()-1);
            }
        }
        previous=day;previousId=id;
    }
    int scanned(){return scanned;}
    int invalid(){return invalid;}
    int outOfOrder(){return backwards;}
    List<Gap> gaps(){return Collections.unmodifiableList(new ArrayList<>(top));}
    private ConversationGapNavigator(boolean ignored){}
    ConversationGapNavigator(){}
}
