package org.openarchive.telegram;
import java.time.LocalDate;
/** User-authored life dates: independent of message IDs and source timezone. */
final class CalendarPeriod {
    final long id;final String name,note,start,end;
    CalendarPeriod(long id,String name,String note,String start,String end){
        if(name==null||name.trim().isEmpty()||name.trim().length()>80)throw new IllegalArgumentException("Название: 1–80 символов");
        if(note!=null&&note.length()>1500)throw new IllegalArgumentException("Описание: до 1500 символов");
        if(LocalDate.parse(start).isAfter(LocalDate.parse(end)))throw new IllegalArgumentException("Конец раньше начала");
        this.id=id;this.name=name.trim();this.note=note==null?"":note.trim();this.start=start;this.end=end;
    }
}
