package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Case-insensitive, bounded search of locally authored event titles/notes, not message text. */
final class HistoryEventSearch {
    static final int MAX_SCANNED=3000,MAX_RESULTS=60,PAGE=50;
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
        int total=db.relationshipEventCount(),scanned=0;
        ArrayList<MessageDb.RelationshipEvent> matches=new ArrayList<>();
        while(scanned<total&&scanned<MAX_SCANNED&&matches.size()<MAX_RESULTS){
            int limit=Math.min(PAGE,Math.min(MAX_SCANNED-scanned,total-scanned));
            List<MessageDb.RelationshipEvent> page=db.relationshipEventsPage(scanned,limit);
            if(page.isEmpty())break;
            for(MessageDb.RelationshipEvent event:page){
                String title=event.title==null?"":event.title;
                String note=event.note==null?"":event.note;
                String date=db.messageDate(event.anchorId);
                if(title.toLowerCase(Locale.ROOT).contains(needle)||
                    note.toLowerCase(Locale.ROOT).contains(needle)||
                    (date!=null&&date.toLowerCase(Locale.ROOT).contains(needle)))
                    matches.add(event);
                if(matches.size()>=MAX_RESULTS)break;
            }
            scanned+=page.size();
        }
        return new Result(matches,scanned,scanned<total);
    }
    private HistoryEventSearch(){}
}
