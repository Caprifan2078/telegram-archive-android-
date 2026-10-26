package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** User-authored offline history chapters. Boundaries are UTF-16 offsets in stable message IDs.
 * A relationship label is NEVER inferred from statistics or message text. */
final class ConversationPeriods {
    static final int MAX_NAME = 80, MAX_NOTE = 1500, MAX_PHASES = 100;
    static final class Boundary implements Comparable<Boundary> {
        final long messageId; final int offset;
        Boundary(long id, int offset) {
            if (id < 1 || offset < 0 || offset > 1_000_000)
                throw new IllegalArgumentException("Invalid message boundary");
            this.messageId = id; this.offset = offset;
        }
        @Override public int compareTo(Boundary b) {
            int cmp = Long.compare(messageId, b.messageId);
            return cmp != 0 ? cmp : Integer.compare(offset, b.offset);
        }
    }
    static final class Chapter {
        final long id; final String name, note;
        final Boundary start, end;
        Chapter(long id,String name,String note,Boundary start,Boundary end) {
            validate(name,note,start,end);
            this.id=id; this.name=name.trim();this.note=note==null?"":note.trim();
            this.start=start;this.end=end;
        }
    }
    static void validate(String name,String note,Boundary start,Boundary end) {
        if(name==null || name.trim().isEmpty() || name.trim().length()>MAX_NAME)
            throw new IllegalArgumentException("Название этапа: 1–80 символов");
        if(note!=null && note.length()>MAX_NOTE)
            throw new IllegalArgumentException("Описание не длиннее 1500 символов");
        if(start==null || end==null || start.compareTo(end)>0)
            throw new IllegalArgumentException("Конец этапа должен быть после начала");
    }
    /** A message touching either boundary is included; split-message character range
     * is shown in the UI but message counts remain whole-message counts. */
    static boolean touches(Chapter chapter,long id) {
        return id>=chapter.start.messageId && id<=chapter.end.messageId;
    }
    static final class Stats {
        final long messages, activeDays, dated, media, calendarDays;
        final String firstDate,lastDate;
        final List<Long> bins;
        Stats(long messages,long days,long dated,long media,String first,String last,long[] values) {
            this.messages=messages;this.activeDays=days;this.dated=dated;this.media=media;
            firstDate=first;lastDate=last;
            long span=1;
            try{span=Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(first),java.time.LocalDate.parse(last))+1);}
            catch(Exception ignored){}
            calendarDays=span;
            ArrayList<Long> list=new ArrayList<>();for(long value:values)list.add(value);
            bins=Collections.unmodifiableList(list);
        }
        double dailyAverage(){return activeDays<=0?0:(double)dated/activeDays;}
    }
    private ConversationPeriods(){}
}
