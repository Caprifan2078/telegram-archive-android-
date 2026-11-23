package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded read-only timeline across separately imported conversations.
 *
 * Never rewrites, copies, merges, or deduplicates source messages: equal text can
 * be different evidence and every row keeps its original (archiveKey,messageId).
 * Date ordering is local export time, without inventing timezone conversions.
 */
final class GroupTimeline {
    static final int PAGE_SIZE=60;
    static final int MAX_PER_SOURCE=600;
    static final int MAX_SOURCES=30;

    static final class Source {
        final String key,label;
        Source(String key,String label){
            if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}"))
                throw new IllegalArgumentException("Неверный ID источника");
            if(label==null||label.trim().isEmpty()||label.trim().length()>80)
                throw new IllegalArgumentException("Неверное название источника");
            this.key=key;this.label=label.trim();
        }
    }
    static final class Result {
        final List<SharedMemories.Entry> items;
        final int invalidDates,missingSources;
        final boolean hasMore;
        Result(List<SharedMemories.Entry> items,int invalidDates,int missingSources,boolean hasMore){
            this.items=Collections.unmodifiableList(items);
            this.invalidDates=invalidDates;this.missingSources=missingSources;this.hasMore=hasMore;
        }
    }

    static Result read(Context ctx,List<Source> sources,int perSource) {
        if(ctx==null||sources==null||sources.isEmpty()||sources.size()>MAX_SOURCES)
            throw new IllegalArgumentException("Нет источников или их слишком много");
        if(perSource<1||perSource>MAX_PER_SOURCE)
            throw new IllegalArgumentException("Недопустимый размер страницы");
        Set<String> unique=new HashSet<>();
        ArrayList<SharedMemories.Entry> entries=new ArrayList<>();
        int invalid=0,missing=0;
        boolean hasMore=false;
        for(Source source:sources){
            if(source==null||!unique.add(source.key))
                throw new IllegalArgumentException("Один архив указан несколько раз");
            File dbFile=ctx.getDatabasePath("default".equals(source.key)?
                    "telegram_archive.db":"telegram_"+source.key+".db");
            // Important: SQLiteOpenHelper would create a missing DB, which makes
            // an incorrectly moved archive look like an empty chat.
            if(!dbFile.isFile()){missing++;continue;}
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(
                    dbFile.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
                Cursor cursor=db.rawQuery(
                    "SELECT id,author,date_text,body,media FROM messages "+
                        "ORDER BY id DESC LIMIT ?",new String[]{Integer.toString(perSource+1)})){
                int read=0;
                while(cursor.moveToNext()){
                    if(++read>perSource){hasMore=true;break;}
                    String date=cursor.getString(2);
                    if(ChatTrends.parse(date)==null){invalid++;continue;}
                    String body=cursor.getString(3);
                    if((body==null||body.trim().isEmpty())&&cursor.getString(4)!=null)
                        body="📎 Вложение";
                    try{
                        entries.add(new SharedMemories.Entry(source.key,source.label,
                            cursor.getLong(0),cursor.getString(1),date,body));
                    }catch(IllegalArgumentException invalidRow){invalid++;}
                }
            }catch(Exception ex){
                // Do not silently present a partial, allegedly complete timeline.
                throw new IllegalStateException("Не удалось прочитать локальный источник «"+
                    source.label+"»: "+ex.getClass().getSimpleName(),ex);
            }
        }
        entries.sort(Comparator.comparing((SharedMemories.Entry e)->e.timestamp).reversed()
            .thenComparing(e->e.archiveKey).thenComparingLong(e->e.messageId));
        return new Result(entries,invalid,missing,hasMore);
    }
    private GroupTimeline(){}
}
