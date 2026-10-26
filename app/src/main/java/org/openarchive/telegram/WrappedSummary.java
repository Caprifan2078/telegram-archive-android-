package org.openarchive.telegram;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Read-only annual recap derived from the same immutable aggregates as the analytics screen. */
final class WrappedSummary {
    static final class Contributor {
        final String key; final long messages;
        Contributor(String key,long messages){this.key=key;this.messages=messages;}
    }
    final int year, activeDays, streak, peakMonth;
    final long messages, peakMonthMessages, busiestDayMessages;
    final LocalDate busiestDay;
    final long[] months;
    final List<Contributor> top;
    private WrappedSummary(int y, int active, int streak, long count, int peak, long peakCount,
                           LocalDate bestDay,long bestCount,long[] months,List<Contributor> top){
        year=y;activeDays=active;this.streak=streak;messages=count;peakMonth=peak;
        peakMonthMessages=peakCount;busiestDay=bestDay;busiestDayMessages=bestCount;
        this.months=months.clone();this.top=Collections.unmodifiableList(new ArrayList<>(top));
    }
    static WrappedSummary create(ChatTrends.Snapshot snapshot,int year){
        if(year<1||year>9999)throw new IllegalArgumentException("year");
        ChatTrends.YearCalendar cal=ChatTrends.calendar(snapshot.all,year);
        long[] months=new long[12];LocalDate day=null;long bestCount=0;
        for(Map.Entry<LocalDate,Long> entry:snapshot.all.days.entrySet()){
            LocalDate d=entry.getKey();if(d.getYear()!=year)continue;
            long n=entry.getValue();months[d.getMonthValue()-1]+=n;
            if(n>bestCount){bestCount=n;day=d;}
        }
        int bestMonth=0;long bestMonthCount=0;
        for(int i=0;i<months.length;i++)if(months[i]>bestMonthCount){bestMonth=i+1;bestMonthCount=months[i];}
        List<Contributor> contributors=new ArrayList<>();
        for(Map.Entry<String,ChatTrends.Activity> participant:snapshot.people.entrySet()){
            long amount=0;
            for(Map.Entry<LocalDate,Long> e:participant.getValue().days.entrySet())
                if(e.getKey().getYear()==year)amount+=e.getValue();
            if(amount>0)contributors.add(new Contributor(participant.getKey(),amount));
        }
        contributors.sort(Comparator.comparingLong((Contributor p)->p.messages).reversed().thenComparing(p->p.key));
        if(contributors.size()>12)contributors=new ArrayList<>(contributors.subList(0,12));
        return new WrappedSummary(year,cal.activeDays,cal.longestStreak,cal.total,
                bestMonth,bestMonthCount,day,bestCount,months,contributors);
    }
}
