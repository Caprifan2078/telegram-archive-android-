package org.openarchive.telegram;

import java.util.Arrays;

/** Opt-in offline comparison: monthly COUNTS only, never copies messages between chats.
 * A scan limit is explicit: partial data must never be presented as a full-year count. */
final class ParallelCalendars {
    static final int MAX_DATE_ROWS=300000;
    static final class YearData {
        final int year;
        final long[] months=new long[12];
        final boolean[] activeDates=new boolean[366];
        long scanned,accepted,rejected;
        boolean partial;
        YearData(int year){
            if(year<1||year>9999)throw new IllegalArgumentException("Год должен быть от 1 до 9999");
            this.year=year;
        }
        void add(String timestamp){
            if(scanned>=MAX_DATE_ROWS){partial=true;return;}
            scanned++;
            ChatTrends.Moment parsed=ChatTrends.parse(timestamp);
            if(parsed==null||parsed.day.getYear()!=year)rejected++;
            else{accepted++;months[parsed.day.getMonthValue()-1]++;
                activeDates[parsed.day.getDayOfYear()-1]=true;}
        }
        long total(){long n=0;for(long v:months)n+=v;return n;}
        int activeDays(){int n=0;for(boolean active:activeDates)if(active)n++;return n;}
        long[] counts(){return Arrays.copyOf(months,months.length);}
    }
    static final class Comparison {
        final YearData first,second;
        Comparison(YearData first,YearData second){
            if(first==null||second==null||first.year!=second.year)
                throw new IllegalArgumentException("Different years");
            this.first=first;this.second=second;
        }
        boolean complete(){return !first.partial&&!second.partial;}
        int sharedActiveDays(){int n=0;for(int i=0;i<first.activeDates.length;i++)
            if(first.activeDates[i]&&second.activeDates[i])n++;return n;}
        String summary(){
            return complete()?"Обработаны все строки указанного года":"Данные неполные: достигнут лимит сканирования";
        }
    }
    private ParallelCalendars(){}
}
