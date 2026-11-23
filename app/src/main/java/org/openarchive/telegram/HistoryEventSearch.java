package org.openarchive.telegram;

import android.database.Cursor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Case-insensitive local search of the user's own chronology events.
 * One streaming SQLite cursor searches the entire event table, rather than
 * silently stopping after 3000 records or issuing N date queries.
 */
final class HistoryEventSearch {
    static final int MAX_RESULTS=60;
    static final class Result {
        final List<MessageDb.RelationshipEvent> matches;
        final int scanned;
        final boolean incomplete;
        Result(List<MessageDb.RelationshipEvent> matches,int scanned,boolean incomplete){
            this.matches=Collections.unmodifiableList(matches);
            this.scanned=scanned;this.incomplete=incomplete;
        }
    }
    static Result find(MessageDb db,String query){
        if(db==null)throw new IllegalArgumentException("Архив не открыт");
        String needle=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(needle.isEmpty()||needle.length()>100)
            throw new IllegalArgumentException("Укажи слово или часть названия события (до 100 символов)");
        ArrayList<MessageDb.RelationshipEvent> matches=new ArrayList<>();
        int scanned=0;
        boolean incomplete=false;
        // SQLite's built-in LOWER is ASCII-only on many Android builds.
        // Compare using Java/Unicode instead, so Cyrillic queries work offline.
        String sql="SELECT e.id,e.title,e.note,e.anchor_id,e.anchor_offset,"+
            "(SELECT COUNT(*) FROM relationship_links l WHERE l.event_id=e.id),"+
            "m.date_text FROM relationship_events e "+
            "LEFT JOIN messages m ON m.id=e.anchor_id "+
            "ORDER BY e.anchor_id,e.anchor_offset,e.id";
        try(Cursor rows=db.getReadableDatabase().rawQuery(sql,null)){
            while(rows.moveToNext()){
                scanned++;
                String title=rows.getString(1),note=rows.getString(2),date=rows.getString(6);
                if((title!=null&&title.toLowerCase(Locale.ROOT).contains(needle))||
                   (note!=null&&note.toLowerCase(Locale.ROOT).contains(needle))||
                   (date!=null&&date.toLowerCase(Locale.ROOT).contains(needle))){
                    MessageDb.RelationshipEvent event=new MessageDb.RelationshipEvent();
                    event.id=rows.getLong(0);
                    event.title=title;event.note=note;
                    event.anchorId=rows.getLong(3);
                    event.anchorOffset=rows.getInt(4);
                    event.linkedCount=rows.getInt(5);
                    matches.add(event);
                    if(matches.size()==MAX_RESULTS){
                        // A truncated result must be labelled as such in the UI.
                        incomplete=rows.moveToNext();
                        break;
                    }
                }
            }
        }
        return new Result(matches,scanned,incomplete);
    }
    private HistoryEventSearch(){}
}
