package org.openarchive.telegram;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Read-only queries against a single archive database. No schema changes or exports. */
final class DualChatRepository {
    static final class Snapshot {
        final ParallelCalendars.YearData calendar;
        final long messages,withText,attachments,favorites,participants;
        final Map<String,Long> mediaTypes;
        Snapshot(ParallelCalendars.YearData calendar,long messages,long withText,
                 long attachments,long favorites,long participants,Map<String,Long> mediaTypes){
            this.calendar=calendar;this.messages=messages;this.withText=withText;
            this.attachments=attachments;this.favorites=favorites;this.participants=participants;
            this.mediaTypes=Collections.unmodifiableMap(new LinkedHashMap<>(mediaTypes));
        }
    }
    private static long scalar(SQLiteDatabase db,String sql){
        try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}
    }
    static Snapshot snapshot(SQLiteDatabase db,int year){
        if(db==null||db.isReadOnly()==false)throw new IllegalArgumentException("Read-only database required");
        // Source record counts are exact regardless of the year scan cap.
        long total=scalar(db,"SELECT COUNT(*) FROM messages");
        long text=scalar(db,"SELECT COUNT(*) FROM messages WHERE body IS NOT NULL AND TRIM(body)<>''");
        long people=scalar(db,"SELECT COUNT(DISTINCT author) FROM messages WHERE author IS NOT NULL AND TRIM(author)<>''");
        long favorites=scalar(db,"SELECT COUNT(*) FROM favorites");
        long attachments=scalar(db,"SELECT COUNT(*) FROM attachments");
        LinkedHashMap<String,Long> types=new LinkedHashMap<>();
        try(Cursor c=db.rawQuery("SELECT kind,COUNT(*) FROM attachments GROUP BY kind ORDER BY kind",null)){
            while(c.moveToNext())types.put(c.getString(0)==null?"file":c.getString(0),c.getLong(1));
        }
        return new Snapshot(MessageDb.calendarYear(db,year),total,text,attachments,favorites,people,types);
    }
    static List<SharedMemories.Entry> timeline(SQLiteDatabase db,String key,String name,int year,int month){
        if(!db.isReadOnly())throw new IllegalArgumentException("Read-only database required");
        if(year<1||year>9999||month<0||month>12)throw new IllegalArgumentException("Invalid date selection");
        String yyyy=String.format(Locale.ROOT,"%04d",year);
        String mm=String.format(Locale.ROOT,"%02d",month);
        // Two Telegram date encodings; SQL narrows the query and Java validates real dates.
        String where=month==0 ?
            "(substr(date_text,1,4)=? OR substr(date_text,7,4)=?)" :
            "((substr(date_text,1,7)=? OR substr(date_text,4,7)=?))";
        String[] args=month==0?new String[]{yyyy,yyyy}:
            new String[]{yyyy+"-"+mm,mm+"."+yyyy};
        String sql="SELECT id,author,date_text,body,media FROM messages WHERE "+where+
            " ORDER BY id DESC LIMIT "+(SharedMemories.MAX_PER_CHAT+1);
        ArrayList<SharedMemories.Entry> items=new ArrayList<>();
        try(Cursor c=db.rawQuery(sql,args)){
            while(c.moveToNext()&&items.size()<SharedMemories.MAX_PER_CHAT){
                String raw=c.getString(2);
                ChatTrends.Moment parsed=ChatTrends.parse(raw);
                if(parsed==null||parsed.day.getYear()!=year||
                    (month!=0&&parsed.day.getMonthValue()!=month))continue;
                String body=c.getString(3);
                if((body==null||body.trim().isEmpty())&&c.getString(4)!=null)body="📎 Медиа без текста";
                items.add(new SharedMemories.Entry(key,name,c.getLong(0),c.getString(1),raw,body));
            }
        }
        return items;
    }
    private DualChatRepository(){}
}
