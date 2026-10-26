package org.openarchive.telegram;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java, streaming, privacy-preserving time statistics. No Android or network APIs. */
final class ChatTrends {
    private static final Pattern ISO=Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})(?:[T ](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?(?:\\D|$)");
    private static final Pattern RU=Pattern.compile("^(\\d{2})\\.(\\d{2})\\.(\\d{4})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?(?:\\D|$)");
    private static final Pattern WEB_LINK=Pattern.compile("(?i)\\b(?:https?://|www\\.)[^\\s]+");
    static final String[] WEEKDAYS={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
    static final class Moment {
        final LocalDate day;
        final int hour;
        final LocalDateTime clock;
        Moment(LocalDate d,int h,int minute,int second){day=d;hour=h;clock=h<0?null:d.atTime(h,minute,second);}
    }
    static Moment parse(String raw){
        if(raw==null)return null;
        String text=raw.trim();Matcher m=ISO.matcher(text);boolean russian=false;
        if(!m.find()){m=RU.matcher(text);russian=true;if(!m.find())return null;}
        try{
            int y=Integer.parseInt(m.group(russian?3:1));
            if(y<1)return null; // reject ISO year 0000: not a valid Telegram export year
            int mo=Integer.parseInt(m.group(2));
            int d=Integer.parseInt(m.group(russian?1:3));
            LocalDate day=LocalDate.of(y,mo,d); // rejects imaginary days, leap-year errors
            int hour=-1,minute=0,sec=0;
            if(m.group(4)!=null){
                hour=Integer.parseInt(m.group(4));minute=Integer.parseInt(m.group(5));
                sec=m.group(6)==null?0:Integer.parseInt(m.group(6));
                if(hour>23||minute>59||sec>59)return new Moment(day,-1,0,0);
            }
            return new Moment(day,hour,minute,sec);
        }catch(IllegalArgumentException|DateTimeException bad){return null;}
    }
    static final class Emoji {
        final String value;final long count;
        Emoji(String v,long c){value=v;count=c;}
    }
    static final class Activity {
        final java.util.NavigableMap<LocalDate,Long> days=new TreeMap<>();
        final long[] hours=new long[24];
        final long[] weekdays=new long[7];
        final long[][] weekdayHours=new long[7][24];
        final Map<String,Long> emojis=new HashMap<>();
        long validDate,validHour,unknownDate,unknownHour,questions,linkMessages,sessionsStarted;
        boolean emojiVocabularyLimited;
        Activity copy(){
            Activity o=new Activity();o.days.putAll(days);System.arraycopy(hours,0,o.hours,0,24);
            System.arraycopy(weekdays,0,o.weekdays,0,7);
            for(int d=0;d<7;d++)System.arraycopy(weekdayHours[d],0,o.weekdayHours[d],0,24);
            o.emojis.putAll(emojis);o.validDate=validDate;o.validHour=validHour;
            o.unknownDate=unknownDate;o.unknownHour=unknownHour;
            o.questions=questions;o.linkMessages=linkMessages;o.sessionsStarted=sessionsStarted;
            o.emojiVocabularyLimited=emojiVocabularyLimited;
            return o;
        }
        void add(Moment time,String body){
            if(time==null){unknownDate++;unknownHour++;}
            else {
                validDate++;days.put(time.day,days.getOrDefault(time.day,0L)+1);
                int d=time.day.getDayOfWeek().getValue()-1;weekdays[d]++;
                if(time.hour>=0){validHour++;hours[time.hour]++;weekdayHours[d][time.hour]++;}
                else unknownHour++;
            }
            String text=body==null?"":body;
            if(text.indexOf('?')>=0||text.indexOf('？')>=0)questions++;
            if(WEB_LINK.matcher(text).find())linkMessages++;
            if(countEmoji(text,emojis))emojiVocabularyLimited=true;
        }
        int busiestHour(){int best=-1;long count=0;for(int i=0;i<24;i++)if(hours[i]>count){best=i;count=hours[i];}return best;}
        int busiestWeekday(){int best=-1;long count=0;for(int i=0;i<7;i++)if(weekdays[i]>count){best=i;count=weekdays[i];}return best;}
        long nightCount(){long n=0;for(int h=0;h<6;h++)n+=hours[h];return n;}
        int longestStreak(){int max=0,cur=0;LocalDate last=null;for(LocalDate day:days.keySet()){
            cur=last!=null&&last.plusDays(1).equals(day)?cur+1:1;max=Math.max(cur,max);last=day;
        }return max;}
        List<Emoji> topEmoji(int limit){
            ArrayList<Emoji> ranked=new ArrayList<>();
            for(Map.Entry<String,Long> e:emojis.entrySet())ranked.add(new Emoji(e.getKey(),e.getValue()));
            ranked.sort((a,b)->{int c=Long.compare(b.count,a.count);return c!=0?c:a.value.compareTo(b.value);});
            if(ranked.size()>limit)ranked.subList(limit,ranked.size()).clear();
            return ranked;
        }
    }
    static final class Series {
        final List<String> labels,details;
        final long[] values;final boolean clipped;
        final long visibleCount;
        Series(List<String> labels,List<String> details,long[] values,boolean clipped){
            this.labels=Collections.unmodifiableList(labels);this.details=Collections.unmodifiableList(details);
            this.values=values;this.clipped=clipped;
            long sum=0;for(long n:values)sum+=n;this.visibleCount=sum;
        }
    }
    /** One self-contained calendar year. -1 denotes a day outside the selected year. */
    static final class YearCalendar {
        final int year, weekCount, activeDays, longestStreak;
        final long total, max;
        final long[][] weeks;
        YearCalendar(int y, long[][] bins, long total, long max, int active, int streak){
            year=y;weeks=bins;weekCount=bins.length;this.total=total;
            this.max=max;activeDays=active;longestStreak=streak;
        }
    }
    /** Rendering is bounded to <=54 week-columns even for a 30-year chat. */
    static YearCalendar calendar(Activity a,int year){
        if(year<1||year>9999)throw new IllegalArgumentException("Invalid calendar year");
        LocalDate start=LocalDate.of(year,1,1);
        int firstOffset=start.getDayOfWeek().getValue()-1;
        int count=start.lengthOfYear();
        long[][] values=new long[(firstOffset+count+6)/7][7];
        for(long[] week:values)Arrays.fill(week,-1);
        long sum=0,max=0;int active=0,streak=0,longest=0;
        for(int i=0;i<count;i++){
            LocalDate day=start.plusDays(i);
            long n=a.days.getOrDefault(day,0L);
            values[(firstOffset+i)/7][(firstOffset+i)%7]=n;
            sum+=n;max=Math.max(max,n);
            if(n>0){active++;streak++;longest=Math.max(longest,streak);}else streak=0;
        }
        return new YearCalendar(year,values,sum,max,active,longest);
    }
    /** Two participants, the same twelve calendar months, including empty bins. */
    static final class MonthlyComparison {
        final List<String> labels;
        final long[] first,second;
        final long firstTotal,secondTotal;
        MonthlyComparison(List<String> names,long[] a,long[] b){
            labels=Collections.unmodifiableList(names);first=a;second=b;
            long x=0,y=0;for(int i=0;i<a.length;i++){x+=a[i];y+=b[i];}
            firstTotal=x;secondTotal=y;
        }
    }
    static MonthlyComparison compareMonths(Activity a,Activity b,int endingYear,int endingMonth){
        if(endingYear<1||endingYear>9999||endingMonth<1||endingMonth>12)
            throw new IllegalArgumentException("Invalid comparison month");
        LocalDate last=LocalDate.of(endingYear,endingMonth,1);
        ArrayList<String> labels=new ArrayList<>();
        long[] x=new long[12],y=new long[12];
        for(int i=0;i<12;i++){
            LocalDate begin=last.minusMonths(11-i);
            labels.add(String.format(Locale.ROOT,"%02d.%02d",begin.getMonthValue(),begin.getYear()%100));
            LocalDate end=begin.plusMonths(1);
            for(long amount:a.days.subMap(begin,true,end,false).values())x[i]+=amount;
            for(long amount:b.days.subMap(begin,true,end,false).values())y[i]+=amount;
        }
        return new MonthlyComparison(labels,x,y);
    }
    /** All calendar years, never a truncated rolling window. Group sparse far-apart years safely. */
    static Series allTimeYears(Activity a){
        TreeMap<Integer,Long> years=new TreeMap<>();
        for(Map.Entry<LocalDate,Long> entry:a.days.entrySet())
            years.merge(entry.getKey().getYear(),entry.getValue(),Long::sum);
        ArrayList<String> labels=new ArrayList<>(),details=new ArrayList<>();
        long[] counts=new long[years.size()];int i=0;
        for(Map.Entry<Integer,Long> year:years.entrySet()){
            labels.add(String.valueOf(year.getKey()));details.add("Год "+year.getKey());counts[i++]=year.getValue();
        }
        return new Series(labels,details,counts,false);
    }
    /** Exactly twelve calendar months, including empty ones; no horizontal scroll at default zoom. */
    static Series twelveMonths(Activity a,int year){
        if(year<1||year>9999)throw new IllegalArgumentException("Invalid year");
        String[] names={"Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек"};
        ArrayList<String> labels=new ArrayList<>(),details=new ArrayList<>();
        long[] values=new long[12];
        for(int i=0;i<12;i++){
            labels.add(names[i]);details.add(names[i]+" "+year);
        }
        for(Map.Entry<LocalDate,Long> entry:a.days.entrySet())
            if(entry.getKey().getYear()==year)values[entry.getKey().getMonthValue()-1]+=entry.getValue();
        return new Series(labels,details,values,false);
    }
    /** A numeric 0–10 scale independent of monitor color reproduction. */
    static int heatLevel(long value,long maximum){
        if(value<=0||maximum<=0)return 0;
        if(value>=maximum)return 10;
        return Math.max(1,Math.min(9,(int)Math.round(10.0*value/maximum)));
    }
    static int latestYear(Activity a){return a.days.isEmpty()?0:a.days.lastKey().getYear();}
    static int earliestYear(Activity a){return a.days.isEmpty()?0:a.days.firstKey().getYear();}
    static final class Snapshot {
        final Activity all;final Map<String,Activity> people;
        Snapshot(Activity a,Map<String,Activity> p){all=a;people=Collections.unmodifiableMap(p);}
        Activity forPerson(String key){Activity a=key==null||key.isEmpty()?all:people.get(key);return a==null?new Activity():a;}
    }
    static final class Builder {
        final Activity all=new Activity();
        final Map<String,Activity> people=new LinkedHashMap<>();
        private LocalDateTime previous;
        void message(String senderKey,String date,String body){
            Moment time=parse(date);
            Activity person=people.computeIfAbsent(senderKey,k->new Activity());
            all.add(time,body);person.add(time,body);
            // A 'session' is an approximate activity burst separated by >=6h of silence;
            // not a directed reply or a claim about who initiated the relationship.
            if(time!=null&&time.clock!=null&& !"системное сообщение".equals(senderKey)){
                if(previous==null||Duration.between(previous,time.clock).toMinutes()>=360){
                    all.sessionsStarted++;person.sessionsStarted++;
                }
                if(previous==null||!time.clock.isBefore(previous))previous=time.clock;
            }
        }
        Snapshot snapshot(){
            Map<String,Activity> data=new LinkedHashMap<>();
            for(Map.Entry<String,Activity> p:people.entrySet())data.put(p.getKey(),p.getValue().copy());
            return new Snapshot(all.copy(),data);
        }
    }
    /** Inclusive time bins ending on the latest valid date. Bounded to keep charts smooth even for decades of history. */
    static Series series(Activity a,String unit){
        if(a.days.isEmpty())return new Series(new ArrayList<>(),new ArrayList<>(),new long[0],false);
        int cap;switch(unit){case "week":cap=104;break;case "month":cap=60;break;case "year":cap=30;break;default:unit="day";cap=90;}
        LocalDate first=a.days.keySet().iterator().next();
        LocalDate last=null;for(LocalDate d:a.days.keySet())last=d;
        LocalDate start=period(last,unit);LocalDate earliest=period(first,unit);
        List<LocalDate> reversed=new ArrayList<>();LocalDate p=start;
        while(!p.isBefore(earliest)&&reversed.size()<cap){reversed.add(p);p=previous(p,unit);}
        boolean clipped=!p.isBefore(earliest);
        Collections.reverse(reversed);
        Map<LocalDate,Integer> positions=new HashMap<>();
        for(int i=0;i<reversed.size();i++)positions.put(reversed.get(i),i);
        long[] vals=new long[reversed.size()];
        for(Map.Entry<LocalDate,Long> d:a.days.entrySet()){
            Integer i=positions.get(period(d.getKey(),unit));if(i!=null)vals[i]+=d.getValue();
        }
        ArrayList<String> labels=new ArrayList<>(),details=new ArrayList<>();
        for(LocalDate d:reversed){
            switch(unit){
                case "week":labels.add(String.format(Locale.ROOT,"%02d.%02d",d.getDayOfMonth(),d.getMonthValue()));
                    details.add("Неделя с "+d);break;
                case "month":labels.add(String.format(Locale.ROOT,"%02d.%02d",d.getMonthValue(),d.getYear()%100));
                    details.add(String.format(Locale.ROOT,"%04d-%02d",d.getYear(),d.getMonthValue()));break;
                case "year":labels.add(String.valueOf(d.getYear()));details.add(labels.get(labels.size()-1));break;
                default: labels.add(String.format(Locale.ROOT,"%02d.%02d",d.getDayOfMonth(),d.getMonthValue()));
                    details.add(d.toString());
            }
        }
        return new Series(labels,details,vals,clipped);
    }
    private static LocalDate period(LocalDate d,String unit){
        switch(unit){
            case "week":return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "month":return d.withDayOfMonth(1);
            case "year":return d.withDayOfYear(1);
            default:return d;
        }
    }
    private static LocalDate previous(LocalDate d,String unit){
        switch(unit){case "week":return d.minusWeeks(1);case "month":return d.minusMonths(1);
            case "year":return d.minusYears(1);default:return d.minusDays(1);}
    }
    private static boolean emojiBase(int cp){return cp>=0x1F000&&cp<=0x1FAFF||cp>=0x2600&&cp<=0x27BF||
        cp==0x00A9||cp==0x00AE||cp==0x203C||cp==0x2049||cp==0x2122||cp==0x2139;}
    private static boolean countEmoji(String s,Map<String,Long> counts){
        boolean limited=false;
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);if(!emojiBase(cp)){i+=Character.charCount(cp);continue;}
            int start=i;i+=Character.charCount(cp);
            // Regional indicator pairs form a flag; modifiers and ZWJ sequences form one displayed emoji.
            if(cp>=0x1F1E6&&cp<=0x1F1FF&&i<s.length()){
                int next=s.codePointAt(i);if(next>=0x1F1E6&&next<=0x1F1FF)i+=Character.charCount(next);
            }
            boolean more=true;while(more&&i<s.length()){
                int next=s.codePointAt(i);
                if(next==0xFE0F||next==0xFE0E||next>=0x1F3FB&&next<=0x1F3FF){i+=Character.charCount(next);}
                else if(next==0x200D){int j=i+1;if(j<s.length()&&emojiBase(s.codePointAt(j))){
                    i=j+Character.charCount(s.codePointAt(j));
                }else more=false;}
                else more=false;
            }
            String emoji=s.substring(start,i);
            if(counts.containsKey(emoji))counts.put(emoji,counts.get(emoji)+1);
            else if(counts.size()<512)counts.put(emoji,1L);
            else limited=true;
        }
        return limited;
    }
}
