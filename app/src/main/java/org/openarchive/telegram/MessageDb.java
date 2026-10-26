package org.openarchive.telegram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.text.Normalizer;

/** Private offline index: messages + media references; never stores copies of media bytes. */
class MessageDb extends SQLiteOpenHelper {
    static final class Item {
        long id; String author,date,body,media,mediaType; boolean favorite;
    }
    static final class ReplyInfo {
        final long targetId,telegramId;
        final String author,preview;
        ReplyInfo(long targetId,long telegramId,String author,String preview){
            this.targetId=targetId;this.telegramId=telegramId;
            this.author=author==null?"":author;
            this.preview=preview==null?"":preview;
        }
    }
    static final class Attachment {
        long id,messageId; String path,kind,author,date,body;
    }
    MessageDb(Context ctx,String key){super(ctx,"default".equals(key)?"telegram_archive.db":"telegram_"+key+".db",null,14);}
    @Override public void onConfigure(SQLiteDatabase d){super.onConfigure(d);d.enableWriteAheadLogging();}
    @Override public void onCreate(SQLiteDatabase d){
        d.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT,body_fold TEXT,author_fold TEXT)");
        d.execSQL("CREATE TABLE staged_messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT,body_fold TEXT,author_fold TEXT)");
        d.execSQL("CREATE VIRTUAL TABLE messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61)");
        mediaTables(d);
        d.execSQL("CREATE TABLE favorites (message_id INTEGER PRIMARY KEY)");
        d.execSQL("CREATE TABLE pins (message_id INTEGER PRIMARY KEY)");
        collectionTables(d);
        timelineTables(d);
        relationshipTables(d);
        replyTables(d);
        localProfileTables(d);
    }
    /** Reply links use real Telegram IDs. Staging and publishing are transactional.
     * A missing target remains explicitly unresolved; never guess from row position. */
    private void replyTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS staged_telegram_ids (message_id INTEGER PRIMARY KEY,telegram_id INTEGER NOT NULL UNIQUE)");
        d.execSQL("CREATE TABLE IF NOT EXISTS staged_reply_ids (message_id INTEGER PRIMARY KEY,target_telegram_id INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS telegram_ids (message_id INTEGER PRIMARY KEY,telegram_id INTEGER NOT NULL UNIQUE)");
        d.execSQL("CREATE TABLE IF NOT EXISTS message_replies (message_id INTEGER PRIMARY KEY,target_telegram_id INTEGER NOT NULL,target_message_id INTEGER)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_reply_target ON message_replies(target_message_id)");
    }
    void stageTelegramId(SQLiteDatabase d,long messageId,long telegramId){
        ContentValues v=new ContentValues();v.put("message_id",messageId);v.put("telegram_id",telegramId);
        // Telegram IDs are unique within one export. A duplicate must NOT resolve a
        // reply to whichever copy happened to be imported first. Keep a negative
        // staged message_id as an ambiguity marker (never publish it); this
        // preserves every staged message while avoiding a schema migration.
        // The UNIQUE telegram_id key also makes 3+ duplicates stay ambiguous.
        long inserted=d.insertWithOnConflict("staged_telegram_ids",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(inserted==-1L){
            d.execSQL("UPDATE staged_telegram_ids SET message_id=-ABS(message_id) WHERE telegram_id=?",
                new Object[]{telegramId});
        }
    }
    void stageReply(SQLiteDatabase d,long messageId,long targetTelegramId){
        ContentValues v=new ContentValues();v.put("message_id",messageId);v.put("target_telegram_id",targetTelegramId);
        d.insertWithOnConflict("staged_reply_ids",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    /** A single bounded query for a visible page; never an N+1 query per message. */
    Map<Long,ReplyInfo> replyPreviews(List<Item> page){
        HashMap<Long,ReplyInfo> out=new HashMap<>();
        if(page==null||page.isEmpty())return out;
        for(int start=0;start<page.size();start+=200){
            int end=Math.min(start+200,page.size());
            StringBuilder sql=new StringBuilder("SELECT r.message_id,r.target_telegram_id,r.target_message_id,t.author,t.body "+
                "FROM message_replies r LEFT JOIN messages t ON t.id=r.target_message_id WHERE r.message_id IN (");
            String[] args=new String[end-start];
            for(int i=start;i<end;i++){if(i>start)sql.append(',');sql.append('?');args[i-start]=String.valueOf(page.get(i).id);}
            sql.append(')');
            try(Cursor c=getReadableDatabase().rawQuery(sql.toString(),args)){
                while(c.moveToNext()){
                    String body=c.getString(4);if(body!=null){body=body.replace('\n',' ').trim();if(body.length()>110)body=body.substring(0,110)+"…";}
                    out.put(c.getLong(0),new ReplyInfo(c.isNull(2)?-1:c.getLong(2),c.getLong(1),c.getString(3),body));
                }
            }
        }
        return out;
    }
    private void localProfileTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS calendar_periods (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,note TEXT NOT NULL,start_date TEXT NOT NULL,end_date TEXT NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS local_profile (id INTEGER PRIMARY KEY CHECK(id=1),display_name TEXT NOT NULL,username TEXT NOT NULL,note TEXT NOT NULL)");
    }
    java.util.List<CalendarPeriod> calendarPeriods(){
        java.util.ArrayList<CalendarPeriod> out=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,note,start_date,end_date FROM calendar_periods ORDER BY start_date,id",null)){
            while(c.moveToNext())out.add(new CalendarPeriod(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)));
        }return out;
    }
    long saveCalendarPeriod(CalendarPeriod period){
        ContentValues v=new ContentValues();v.put("name",period.name);v.put("note",period.note);v.put("start_date",period.start);v.put("end_date",period.end);
        if(period.id>0){if(getWritableDatabase().update("calendar_periods",v,"id=?",new String[]{String.valueOf(period.id)})!=1)throw new IllegalArgumentException("Период уже удалён");return period.id;}
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM calendar_periods",null)){if(c.moveToFirst()&&c.getInt(0)>=100)throw new IllegalArgumentException("Не больше 100 периодов");}
        return getWritableDatabase().insertOrThrow("calendar_periods",null,v);
    }
    void deleteCalendarPeriod(long id){getWritableDatabase().delete("calendar_periods","id=?",new String[]{String.valueOf(id)});}
    String[] localProfile(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT display_name,username,note FROM local_profile WHERE id=1",null)){
            if(c.moveToFirst())return new String[]{c.getString(0),c.getString(1),c.getString(2)};
        }return new String[]{"","",""};
    }
    void saveLocalProfile(String name,String username,String note){
        if(name.length()>80||username.length()>80||note.length()>1500)throw new IllegalArgumentException("Имя / username: до 80 символов; заметка: до 1500");
        ContentValues v=new ContentValues();v.put("id",1);v.put("display_name",name.trim());v.put("username",username.trim());v.put("note",note.trim());
        getWritableDatabase().insertWithOnConflict("local_profile",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    private void mediaTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS attachments (id INTEGER PRIMARY KEY,message_id INTEGER NOT NULL,path TEXT NOT NULL,kind TEXT NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS staged_attachments (id INTEGER PRIMARY KEY,message_id INTEGER NOT NULL,path TEXT NOT NULL,kind TEXT NOT NULL)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_attachment_kind ON attachments(kind,message_id)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_attachment_message ON attachments(message_id)");
    }
    private void collectionTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS media_collections (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT UNIQUE NOT NULL)");
        d.execSQL("CREATE TABLE IF NOT EXISTS collection_entries (collection_id INTEGER NOT NULL,attachment_id INTEGER NOT NULL,path TEXT NOT NULL,PRIMARY KEY(collection_id,attachment_id))");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_collection_entry_id ON collection_entries(attachment_id)");
    }
    private void timelineTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS conversation_periods (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "name TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',"+
            "start_message INTEGER NOT NULL,start_offset INTEGER NOT NULL,"+
            "end_message INTEGER NOT NULL,end_offset INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_period_boundaries ON conversation_periods(start_message,end_message)");
    }
    private void relationshipTables(SQLiteDatabase d){
        d.execSQL("CREATE TABLE IF NOT EXISTS relationship_events (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "title TEXT NOT NULL,note TEXT NOT NULL DEFAULT '',anchor_id INTEGER NOT NULL,"+
            "anchor_offset INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL)");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_relationship_anchor ON relationship_events(anchor_id)");
        // Existing Telegram media stay in their original place. Store only stable references.
        d.execSQL("CREATE TABLE IF NOT EXISTS relationship_links (event_id INTEGER NOT NULL,"+
            "attachment_id INTEGER NOT NULL,path TEXT NOT NULL,PRIMARY KEY(event_id,attachment_id))");
        d.execSQL("CREATE INDEX IF NOT EXISTS ix_relation_links ON relationship_links(attachment_id)");
    }
    static final class RelationshipEvent {
        long id,anchorId;int anchorOffset,linkedCount;
        String title,note;
    }
    long addRelationshipEvent(String title,String note,long anchorId,int offset){
        if(title==null||title.trim().isEmpty()||title.trim().length()>90)
            throw new IllegalArgumentException("Название события: 1–90 символов");
        if(note!=null&&note.length()>2500)throw new IllegalArgumentException("Описание: до 2500 символов");
        if(anchorId<1||offset<0)throw new IllegalArgumentException("Неверная точка в переписке");
        SQLiteDatabase d=getWritableDatabase();
        try(Cursor exists=d.rawQuery("SELECT 1 FROM messages WHERE id=?",new String[]{String.valueOf(anchorId)})){
            if(!exists.moveToFirst())throw new IllegalArgumentException("Исходное сообщение не найдено");
        }
        ContentValues v=new ContentValues();v.put("title",title.trim());v.put("note",note==null?"":note.trim());
        v.put("anchor_id",anchorId);v.put("anchor_offset",offset);v.put("created_at",System.currentTimeMillis());
        return d.insertOrThrow("relationship_events",null,v);
    }
    List<RelationshipEvent> relationshipEvents(){
        ArrayList<RelationshipEvent> out=new ArrayList<>();
        String sql="SELECT e.id,e.title,e.note,e.anchor_id,e.anchor_offset,"+
            "(SELECT COUNT(*) FROM relationship_links l WHERE l.event_id=e.id) "+
            "FROM relationship_events e ORDER BY e.anchor_id,e.anchor_offset,e.id LIMIT 250";
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){
            while(c.moveToNext()){
                RelationshipEvent e=new RelationshipEvent();e.id=c.getLong(0);e.title=c.getString(1);
                e.note=c.getString(2);e.anchorId=c.getLong(3);e.anchorOffset=c.getInt(4);
                e.linkedCount=c.getInt(5);out.add(e);
            }
        }
        return out;
    }
    void removeRelationshipEvent(long id){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{d.delete("relationship_links","event_id=?",new String[]{String.valueOf(id)});
            d.delete("relationship_events","id=?",new String[]{String.valueOf(id)});
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    void linkRelationshipMedia(long eventId,long attachmentId){
        SQLiteDatabase d=getWritableDatabase();
        if(eventId==0)throw new IllegalArgumentException("Неверная отметка");
        String table=eventId>0?"relationship_events":"conversation_periods";
        try(Cursor check=d.rawQuery("SELECT 1 FROM "+table+" WHERE id=?",
                new String[]{String.valueOf(Math.abs(eventId))})){
            if(!check.moveToFirst())throw new IllegalArgumentException("Этап или событие не найдены");
        }
        String path;
        try(Cursor c=d.rawQuery("SELECT path FROM attachments WHERE id=?",new String[]{String.valueOf(attachmentId)})){
            if(!c.moveToFirst())throw new IllegalArgumentException("Вложение отсутствует");
            path=c.getString(0);
        }
        ContentValues v=new ContentValues();v.put("event_id",eventId);v.put("attachment_id",attachmentId);v.put("path",path);
        d.insertWithOnConflict("relationship_links",null,v,SQLiteDatabase.CONFLICT_IGNORE);
    }
    List<Attachment> relationshipMedia(long eventId){
        ArrayList<Attachment> out=new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind FROM relationship_links l "+
            "INNER JOIN attachments a ON a.id=l.attachment_id AND a.path=l.path "+
            "WHERE l.event_id=? ORDER BY a.id LIMIT 100";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(eventId)})){
            while(c.moveToNext()){Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);
                a.path=c.getString(2);a.kind=c.getString(3);out.add(a);}
        }
        return out;
    }
    /** Unicode-aware phrase matching using a streaming cursor, opt-in, bounded by
     * max scanned rows and preview entries. Counts are explicitly labelled as partial. */
    PhraseEcho.Report echoPhrase(String phrase){
        String query=PhraseEcho.requirePhrase(phrase);
        ArrayList<PhraseEcho.Hit> hits=new ArrayList<>();
        int scanned=0,matches=0,occurrences=0;boolean partial=false;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,author,date_text,body FROM messages ORDER BY id",null)){
            while(c.moveToNext()){
                if(scanned>=PhraseEcho.MAX_SCANNED){partial=true;break;}
                scanned++;
                String content=c.getString(3);if(content==null||content.isEmpty())continue;
                String normalized=PhraseEcho.canonical(content);
                int n=PhraseEcho.count(normalized,query);
                if(n==0)continue;
                matches++;occurrences+=n;
                if(hits.size()<PhraseEcho.MAX_RESULTS)hits.add(new PhraseEcho.Hit(
                    c.getLong(0),c.getString(1),c.getString(2),PhraseEcho.preview(content,query)));
            }
        }
        return new PhraseEcho.Report(hits,scanned,matches,occurrences,partial);
    }
    @Override public void onUpgrade(SQLiteDatabase d,int old,int now){
        if(old<2){
            d.execSQL("CREATE TABLE IF NOT EXISTS staged_messages (id INTEGER PRIMARY KEY,author TEXT,date_text TEXT,body TEXT,media TEXT,media_type TEXT)");
            d.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS messages_fts USING fts4(author,body,date_text,content='messages',tokenize=unicode61)");
            d.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");
        }
        if(old<3){
            mediaTables(d);
            d.execSQL("INSERT INTO attachments(message_id,path,kind) SELECT id,media,CASE WHEN lower(media) LIKE '%round_video%' OR lower(media) LIKE '%video_message%' THEN 'round' WHEN media_type='video' THEN 'video' WHEN media_type='audio' THEN 'audio' ELSE 'image' END FROM messages WHERE media IS NOT NULL AND media<>''");
        }
        if(old<4){d.execSQL("CREATE TABLE IF NOT EXISTS favorites (message_id INTEGER PRIMARY KEY)");}
        if(old<6)d.execSQL("CREATE TABLE IF NOT EXISTS pins (message_id INTEGER PRIMARY KEY)");
        if(old<7){
            d.execSQL("UPDATE attachments SET kind='gif' WHERE kind='video' AND (lower(path) LIKE '%/animations/%' OR lower(path) LIKE '%/gifs/%' OR lower(path) LIKE '%/animated/%')");
            d.execSQL("UPDATE attachments SET kind='gif' WHERE kind='image' AND lower(path) LIKE '%.gif'");
        }
        if(old<8){
            // Correct legacy tags without touching original files or message IDs.
            d.execSQL("UPDATE attachments SET kind='voice' WHERE kind='audio' AND " +
                "(lower(path) LIKE '%voice_messages%' OR lower(path) LIKE '%voice_message%' OR " +
                "lower(path) LIKE '%voice_note%' OR lower(path) LIKE '%audio_message%')");
            d.execSQL("UPDATE attachments SET kind='gif' WHERE kind='video' AND " +
                "(lower(path) LIKE '%/animations/%' OR lower(path) LIKE '%/gifs/%' OR " +
                "lower(path) LIKE '%/animated/%' OR lower(path) LIKE 'animations/%' OR " +
                "lower(path) LIKE 'gifs/%' OR lower(path) LIKE '%animation%.mp4')");
            d.execSQL("UPDATE attachments SET kind='sticker' WHERE kind IN ('image','file') AND " +
                "(lower(path) LIKE '%/stickers/%' OR lower(path) LIKE 'stickers/%' OR lower(path) LIKE '%.tgs')");
        }
        if(old<9)collectionTables(d);
        if(old<11)timelineTables(d);
        if(old<12)relationshipTables(d);
        if(old<14){replyTables(d);localProfileTables(d);}
        if(old<10){
            // Older synthetic/minimal databases may lack optional media tables.
            mediaTables(d);
            // Fix already imported v9 archives; preserve IDs, text, favorites, pins and collections.
            d.execSQL("UPDATE attachments SET kind='sticker' WHERE kind IN ('image','file','video','gif') " +
                "AND (lower(path) LIKE 'stickers/%' OR lower(path) LIKE '%/stickers/%' OR lower(path) LIKE '%.tgs')");
            d.execSQL("UPDATE attachments SET kind='gif' WHERE kind IN ('image','video','file') " +
                "AND (lower(path) LIKE '%.gif' OR lower(path) LIKE '%.gif?%' OR lower(path) LIKE 'gifs/%' " +
                "OR lower(path) LIKE '%/gifs/%' OR lower(path) LIKE 'animations/%' OR lower(path) LIKE '%/animations/%')");
            d.execSQL("UPDATE attachments SET kind='voice' WHERE kind='audio' AND " +
                "(lower(path) LIKE '%voice%' OR lower(path) LIKE '%/audio_message%' " +
                "OR (lower(path) LIKE '%.ogg' AND lower(path) NOT LIKE '%music%') " +
                "OR (lower(path) LIKE '%.opus' AND lower(path) NOT LIKE '%music%'))");
        }

        if(old<5){
            d.execSQL("ALTER TABLE messages ADD COLUMN body_fold TEXT");
            d.execSQL("ALTER TABLE messages ADD COLUMN author_fold TEXT");
            d.execSQL("ALTER TABLE staged_messages ADD COLUMN body_fold TEXT");
            d.execSQL("ALTER TABLE staged_messages ADD COLUMN author_fold TEXT");
            // Existing data stays readable; a successful re-import populates Unicode casefold columns.

        }
    }
    /** Saved in the chat SQLite: metadata and full backups include user periods automatically. */
    long addPeriod(String name,String note,ConversationPeriods.Boundary start,ConversationPeriods.Boundary end){
        ConversationPeriods.validate(name,note,start,end);
        SQLiteDatabase database=getWritableDatabase();
        try(Cursor check=database.rawQuery("SELECT COUNT(*) FROM conversation_periods",null)){
            if(check.moveToFirst()&&check.getInt(0)>=ConversationPeriods.MAX_PHASES)
                throw new IllegalStateException("Максимум 100 этапов в чате");
        }
        ContentValues values=new ContentValues();values.put("name",name.trim());values.put("note",note==null?"":note.trim());
        values.put("start_message",start.messageId);values.put("start_offset",start.offset);
        values.put("end_message",end.messageId);values.put("end_offset",end.offset);
        values.put("created_at",System.currentTimeMillis());
        return database.insertOrThrow("conversation_periods",null,values);
    }
    boolean removePeriod(long id){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
        try{d.delete("relationship_links","event_id=?",new String[]{String.valueOf(-id)});
            boolean removed=d.delete("conversation_periods","id=?",new String[]{String.valueOf(id)})>0;
            d.setTransactionSuccessful();return removed;
        }finally{d.endTransaction();}
    }
    List<ConversationPeriods.Chapter> periods(){
        ArrayList<ConversationPeriods.Chapter> output=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,name,note,start_message,start_offset,end_message,end_offset "+
                "FROM conversation_periods ORDER BY start_message,start_offset,id",null)){
            while(c.moveToNext())output.add(new ConversationPeriods.Chapter(c.getLong(0),c.getString(1),c.getString(2),
                    new ConversationPeriods.Boundary(c.getLong(3),c.getInt(4)),
                    new ConversationPeriods.Boundary(c.getLong(5),c.getInt(6))));
        }
        return output;
    }
    /** One read-only bounded scan, aggregating activity from actual message timestamps.
     * Bin axis is normalized from first to last valid calendar day in each period. */
    ConversationPeriods.Stats periodStats(ConversationPeriods.Chapter p){
        long messages=0,media=0,dated=0;java.time.LocalDate first=null,last=null;
        java.util.TreeMap<java.time.LocalDate,Long> days=new java.util.TreeMap<>();
        String[] args={String.valueOf(p.start.messageId),String.valueOf(p.end.messageId)};
        try(Cursor count=getReadableDatabase().rawQuery("SELECT COUNT(*),"+
                "(SELECT COUNT(*) FROM attachments a WHERE a.message_id>=? AND a.message_id<=?) "+
                "FROM messages WHERE id>=? AND id<=?",new String[]{args[0],args[1],args[0],args[1]})){
            if(count.moveToFirst()){messages=count.getLong(0);media=count.getLong(1);}
        }
        // No unbounded temporary rows: only an aggregate by date_text from SQLite.
        String daySql="CASE WHEN date_text GLOB '????-??-??*' THEN substr(date_text,1,10) "+
            "WHEN date_text GLOB '??.??.????*' THEN substr(date_text,7,4)||'-'||substr(date_text,4,2)||'-'||substr(date_text,1,2) ELSE NULL END";
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+daySql+" AS message_day,COUNT(*) FROM messages "+
                "WHERE id>=? AND id<=? GROUP BY message_day",args)){
            while(c.moveToNext()){
                ChatTrends.Moment m=ChatTrends.parse(c.getString(0));
                if(m==null)continue;
                long n=c.getLong(1);dated+=n;
                days.merge(m.day,n,Long::sum);
                if(first==null||m.day.isBefore(first))first=m.day;
                if(last==null||m.day.isAfter(last))last=m.day;
            }
        }
        long[] bins=new long[12];
        if(first!=null){long span=java.time.temporal.ChronoUnit.DAYS.between(first,last)+1;
            for(java.util.Map.Entry<java.time.LocalDate,Long> e:days.entrySet()){
                long elapsed=java.time.temporal.ChronoUnit.DAYS.between(first,e.getKey());
                int b=(int)Math.min(11,elapsed*12/span);bins[b]+=e.getValue();
            }
        }
        return new ConversationPeriods.Stats(messages,days.size(),dated,media,
                first==null?"?":first.toString(),last==null?"?":last.toString(),bins);
    }
    boolean isPinned(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM pins WHERE message_id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    boolean togglePinned(long id){SQLiteDatabase d=getWritableDatabase();if(isPinned(id)){d.delete("pins","message_id=?",new String[]{String.valueOf(id)});return false;}ContentValues v=new ContentValues();v.put("message_id",id);d.insertWithOnConflict("pins",null,v,SQLiteDatabase.CONFLICT_IGNORE);return true;}
    List<Item> pinned(){ArrayList<Item> result=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT m.id,m.author,m.date_text,m.body FROM messages m JOIN pins p ON p.message_id=m.id ORDER BY m.id DESC LIMIT 100",null)){while(c.moveToNext()){Item i=new Item();i.id=c.getLong(0);i.author=c.getString(1);i.date=c.getString(2);i.body=c.getString(3);result.add(i);}}return result;}
    int totalMessages(){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages",null)){return c.moveToFirst()?c.getInt(0):0;}}

    /** Stream the current chat without loading message bodies or attachments into a list.
     * Read-only: no schema migration, no files, no network access. Run on worker thread. */
    ChatAnalytics.Snapshot analytics(){
        SQLiteDatabase database=getReadableDatabase();
        ChatAnalytics.Accumulator stats=new ChatAnalytics.Accumulator();
        try(Cursor rows=database.rawQuery("SELECT author,date_text,body FROM messages ORDER BY id",null)){
            while(rows.moveToNext())stats.message(rows.getString(0),rows.getString(1),rows.getString(2));
        }
        try(Cursor media=database.rawQuery("SELECT m.author,a.kind,COUNT(*) FROM attachments a " +
                "INNER JOIN messages m ON m.id=a.message_id GROUP BY m.author,a.kind",null)){
            while(media.moveToNext())stats.media(media.getString(0),media.getString(1),media.getLong(2));
        }
        return stats.snapshot();
    }

    /** Count messages by calendar month for a single selected year, without reading bodies.
     * The limit protects large archives; the UI must visibly mark incomplete reports. */
    ParallelCalendars.YearData calendarYear(int year){
        return calendarYear(getReadableDatabase(),year);
    }
    static ParallelCalendars.YearData calendarYear(SQLiteDatabase database,int year){
        ParallelCalendars.YearData data=new ParallelCalendars.YearData(year);
        String y=String.format(Locale.ROOT,"%04d",year);
        // Indexed message IDs preserve a deterministic partial prefix if the cap is hit.
        // Both date formats are validated again by ChatTrends.parse before counting.
        String sql="SELECT date_text FROM messages WHERE substr(date_text,1,4)=? "+
            "OR substr(date_text,7,4)=? ORDER BY id LIMIT "+(ParallelCalendars.MAX_DATE_ROWS+1);
        try(Cursor c=database.rawQuery(sql,new String[]{y,y})){
            while(c.moveToNext()){
                if(data.scanned>=ParallelCalendars.MAX_DATE_ROWS){data.partial=true;break;}
                data.add(c.getString(0));
            }
        }
        return data;
    }

    /** Recent snippets from the same calendar date in earlier years. Read-only and bounded. */
    List<OnThisDay.Entry> onThisDay(int month,int day,int maxResults){
        if(month<1||month>12||day<1||day>31||maxResults<1)return new ArrayList<>();
        int limit=Math.min(maxResults,60);
        String iso=String.format(Locale.ROOT,"%02d-%02d",month,day);
        String rus=String.format(Locale.ROOT,"%02d.%02d",day,month);
        ArrayList<OnThisDay.Entry> found=new ArrayList<>();
        // Index-independent WHERE bounds reduce Java decoding without loading all messages.
        String sql="SELECT id,author,date_text,body FROM messages WHERE " +
            "substr(date_text,6,5)=? OR substr(date_text,1,5)=? ORDER BY id DESC LIMIT 600";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{iso,rus})){
            while(c.moveToNext()&&found.size()<limit){
                if(OnThisDay.matches(c.getString(2),month,day))
                    found.add(new OnThisDay.Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
            }
        }
        return found;
    }

    /** Checks indexed media paths only; never opens or modifies original media bytes. */
    ChatMediaHealth.Report mediaHealth(DocumentFolder root,int cap){
        int scanLimit=Math.max(1,Math.min(5000,cap));
        int distinct=0;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(DISTINCT path) FROM attachments",null)){
            if(c.moveToFirst())distinct=c.getInt(0);
        }
        ChatMediaHealth.Builder stats=new ChatMediaHealth.Builder();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT path FROM attachments ORDER BY path LIMIT "+scanLimit,null)){
            while(c.moveToNext()){
                String path=c.getString(0);
                boolean found=false;
                if(root!=null){try{found=root.resolve(path)!=null;}catch(Exception ignored){}}
                stats.record(path,found);
            }
        }
        return stats.finish(distinct);
    }

    static String normalizedAuthor(String author){
        if(author==null)return "";
        return Normalizer.normalize(author,Normalizer.Form.NFKC).replace('\u00a0',' ').trim().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
    }
    List<String> participants(){
        LinkedHashMap<String,String> unique=new LinkedHashMap<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT DISTINCT author FROM messages WHERE author IS NOT NULL AND trim(author)<>'' ORDER BY author",null)){
            while(c.moveToNext()){
                String display=c.getString(0).trim();String key=normalizedAuthor(display);
                if(!key.isEmpty()&&!unique.containsKey(key))unique.put(key,display);
            }
        }
        return new ArrayList<>(unique.values());
    }
    private static String fold(String s){return (s==null?"":s).toLowerCase(Locale.ROOT);}
    void clearStage(SQLiteDatabase d){d.delete("staged_reply_ids",null,null);d.delete("staged_telegram_ids",null,null);d.delete("staged_attachments",null,null);d.delete("staged_messages",null,null);}
    long stage(SQLiteDatabase d,String author,String date,String body,String media,String type){
        ContentValues v=new ContentValues();v.put("author",author);v.put("date_text",date);v.put("body",body);
        v.put("media",media);v.put("media_type",type);v.put("body_fold",fold(body));v.put("author_fold",fold(author));
        return d.insertOrThrow("staged_messages",null,v);
    }
    void stageAttachment(SQLiteDatabase d,long messageId,String path,String kind){
        ContentValues v=new ContentValues();v.put("message_id",messageId);v.put("path",path);v.put("kind",kind);
        d.insertOrThrow("staged_attachments",null,v);
    }
    /** Fail closed before modifying published rows; the caller can import into a new archive. */
    private void assertSafeReimport(SQLiteDatabase d){
        String[] checks={
            "SELECT 1 FROM messages m LEFT JOIN staged_messages s ON s.id=m.id WHERE s.id IS NULL OR m.author IS NOT s.author OR m.date_text IS NOT s.date_text OR m.body IS NOT s.body OR m.media IS NOT s.media OR m.media_type IS NOT s.media_type LIMIT 1",
            "SELECT 1 FROM attachments a LEFT JOIN staged_attachments s ON s.id=a.id WHERE s.id IS NULL OR a.message_id IS NOT s.message_id OR a.path IS NOT s.path OR a.kind IS NOT s.kind LIMIT 1",
            "SELECT 1 FROM telegram_ids t LEFT JOIN staged_telegram_ids s ON s.telegram_id=t.telegram_id WHERE s.message_id IS NOT t.message_id LIMIT 1"
        };
        for(String query:checks){
            try(Cursor c=d.rawQuery(query,null)){
                if(c.moveToFirst())throw new IllegalStateException("Повторный импорт изменяет существующие сообщения или вложения. Прежний архив и ваши отметки сохранены. Импортируйте экспорт как новый архив; заранее создайте резервную копию.");
            }
        }
    }
    void publishStaged(SQLiteDatabase d){
        d.beginTransaction();try{
            // IDs are positional, not intrinsically stable across exports. Refuse any
            // re-import that could silently reassign local annotations to another row.
            assertSafeReimport(d);
            d.delete("attachments",null,null);d.delete("messages",null,null);
            d.execSQL("INSERT INTO messages(id,author,date_text,body,media,media_type,body_fold,author_fold) SELECT id,author,date_text,body,media,media_type,body_fold,author_fold FROM staged_messages ORDER BY id");
            d.execSQL("INSERT INTO attachments(id,message_id,path,kind) SELECT id,message_id,path,kind FROM staged_attachments ORDER BY id");
            d.execSQL("DELETE FROM favorites WHERE message_id NOT IN (SELECT id FROM messages)");
            d.execSQL("DELETE FROM pins WHERE message_id NOT IN (SELECT id FROM messages)");
            // Never leave collection entries linked to re-used attachment IDs.
            d.execSQL("DELETE FROM collection_entries WHERE NOT EXISTS (SELECT 1 FROM attachments a WHERE a.id=collection_entries.attachment_id AND a.path=collection_entries.path)");
            // Resolve replies only after all numbered HTML parts were staged.
            d.delete("message_replies",null,null);d.delete("telegram_ids",null,null);
            // Do not resolve links to ambiguous source IDs. Their reply cards remain
            // explicitly unresolved rather than pointing at an arbitrary message.
            d.execSQL("INSERT INTO telegram_ids(message_id,telegram_id) SELECT message_id,telegram_id FROM staged_telegram_ids WHERE message_id>0");
            d.execSQL("INSERT INTO message_replies(message_id,target_telegram_id,target_message_id) "+
                "SELECT r.message_id,r.target_telegram_id,t.message_id FROM staged_reply_ids r "+
                "LEFT JOIN telegram_ids t ON t.telegram_id=r.target_telegram_id");
            d.execSQL("INSERT INTO messages_fts(messages_fts) VALUES('rebuild')");
            clearStage(d);d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    private static String fts(String raw){
        if(raw==null)return null;StringBuilder b=new StringBuilder();
        for(String t:fold(raw).split("[^\\p{L}\\p{N}]+"))if(!t.isEmpty()){
            if(b.length()>0)b.append(" AND ");b.append(t).append('*');
        }
        return b.length()==0?null:b.toString();
    }
    private static final class Filter{String clause;String[] args;Filter(String c,String[] a){clause=c;args=a;}}
    private Filter condition(String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        ArrayList<String> cond=new ArrayList<>();ArrayList<String> args=new ArrayList<>();
        if(q!=null&&!q.trim().isEmpty()){
            if(deep||fts(q)==null){
                cond.add("(COALESCE(body_fold,body) LIKE ? ESCAPE '\\' OR COALESCE(author_fold,author) LIKE ? ESCAPE '\\')");
                String w="%"+escapeLike(fold(q.trim()))+"%";args.add(w);args.add(w);
            }else{cond.add("messages.id IN (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?)");args.add(fts(q));}
        }
        if(author!=null&&!author.trim().isEmpty()){cond.add("COALESCE(author_fold,author) LIKE ? ESCAPE '\\'");args.add("%"+escapeLike(fold(author.trim()))+"%");}
        if(onlyMedia)cond.add("EXISTS(SELECT 1 FROM attachments a WHERE a.message_id=messages.id)");
        if(favorites)cond.add("EXISTS(SELECT 1 FROM favorites f WHERE f.message_id=messages.id)");
        return new Filter(cond.isEmpty()?"":" WHERE "+android.text.TextUtils.join(" AND ",cond),args.toArray(new String[0]));
    }
    private static String escapeLike(String s){return s.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    /** Count matching messages AND literal (non-overlapping) occurrences inside message text.
     * For quick FTS prefix searches these are different metrics, intentionally labelled. */
    static final class SearchStats{
        final int messages;
        final long literalOccurrences;
        SearchStats(int count,long occurrences){messages=count;literalOccurrences=occurrences;}
    }
    SearchStats searchStats(String q,boolean deep,String author,boolean onlyMedia){
        Filter f=condition(q,deep,author,onlyMedia,false);
        String needle=fold(q==null?"":q.trim());
        if(needle.isEmpty())return new SearchStats(count(q,deep,author,onlyMedia,false),0);
        String folded="COALESCE(body_fold,LOWER(body),'')";
        String sql="SELECT COUNT(*), COALESCE(SUM((LENGTH("+folded+") - LENGTH(REPLACE("+folded+", ?, ''))) / NULLIF(LENGTH(?),0)),0) FROM messages"+f.clause;
        String[] arguments=new String[f.args.length+2];
        arguments[0]=needle;arguments[1]=needle;
        System.arraycopy(f.args,0,arguments,2,f.args.length);
        try(Cursor c=getReadableDatabase().rawQuery(sql,arguments)){
            return c.moveToFirst()?new SearchStats(c.getInt(0),c.getLong(1)):new SearchStats(0,0);
        }
    }
    int count(String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        Filter f=condition(q,deep,author,onlyMedia,favorites);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages"+f.clause,f.args)){return c.moveToFirst()?c.getInt(0):0;}
    }
    List<Item> page(int offset,int limit,String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        Filter f=condition(q,deep,author,onlyMedia,favorites);ArrayList<Item> list=new ArrayList<>();
        String sql="SELECT id,author,date_text,body,media,media_type,"+
                "EXISTS(SELECT 1 FROM favorites WHERE message_id=messages.id) FROM messages"+f.clause+
                " ORDER BY id LIMIT "+limit+" OFFSET "+Math.max(offset,0);
        try(Cursor c=getReadableDatabase().rawQuery(sql,f.args)){while(c.moveToNext()){
            Item m=new Item();m.id=c.getLong(0);m.author=c.getString(1);m.date=c.getString(2);m.body=c.getString(3);
            m.media=c.getString(4);m.mediaType=c.getString(5);m.favorite=c.getInt(6)!=0;list.add(m);
        }}
        return list;
    }
    /** Seek-based continuation instead of OFFSET for deep scrolling. */
    List<Item> pageAfter(long afterId,int limit,String q,boolean deep,String author,boolean onlyMedia,boolean favorites){
        if(limit<=0)return new ArrayList<>();
        Filter f=condition(q,deep,author,onlyMedia,favorites);
        String where=" WHERE messages.id > ?"+(f.clause.isEmpty()?"":" AND "+f.clause.substring(7));
        String[] args=new String[f.args.length+1];args[0]=String.valueOf(afterId);
        System.arraycopy(f.args,0,args,1,f.args.length);
        ArrayList<Item> items=new ArrayList<>();
        String sql="SELECT id,author,date_text,body,media,media_type,"+
                "EXISTS(SELECT 1 FROM favorites WHERE message_id=messages.id) FROM messages"+
                where+" ORDER BY id LIMIT "+limit;
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext()){
                Item m=new Item();m.id=c.getLong(0);m.author=c.getString(1);
                m.date=c.getString(2);m.body=c.getString(3);m.media=c.getString(4);
                m.mediaType=c.getString(5);m.favorite=c.getInt(6)!=0;items.add(m);
            }
        }
        return items;
    }
    boolean toggleFavorite(long id){
        SQLiteDatabase d=getWritableDatabase();
        if(isFavorite(id)){d.delete("favorites","message_id=?",new String[]{String.valueOf(id)});return false;}
        ContentValues v=new ContentValues();v.put("message_id",id);d.insertWithOnConflict("favorites",null,v,SQLiteDatabase.CONFLICT_IGNORE);return true;
    }
    boolean isFavorite(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE message_id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    int positionFor(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM messages WHERE id < ?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):-1;}}
    /** Exact quote anchor plus at most two true neighboring records on each side.
     * This does not infer a reply-to relationship or rewrite quoted content. */
    QuoteSourceMap.Context quoteContext(long sourceId){
        return quoteContext(getReadableDatabase(),sourceId);
    }
    static QuoteSourceMap.Context quoteContext(SQLiteDatabase database,long sourceId){
        if(sourceId<=0)throw new IllegalArgumentException("Invalid quote source ID");
        QuoteSourceMap.Entry source=null;
        try(Cursor c=database.rawQuery(
                "SELECT id,author,date_text,body FROM messages WHERE id=? LIMIT 1",
                new String[]{String.valueOf(sourceId)})){
            if(c.moveToFirst())source=new QuoteSourceMap.Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3));
        }
        ArrayList<QuoteSourceMap.Entry> before=new ArrayList<>(),after=new ArrayList<>();
        if(source!=null){
            try(Cursor c=database.rawQuery(
                    "SELECT id,author,date_text,body FROM messages WHERE id<? ORDER BY id DESC LIMIT "+QuoteSourceMap.RADIUS,
                    new String[]{String.valueOf(sourceId)})){
                while(c.moveToNext())before.add(new QuoteSourceMap.Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
            }
            java.util.Collections.reverse(before);
            try(Cursor c=database.rawQuery(
                    "SELECT id,author,date_text,body FROM messages WHERE id>? ORDER BY id ASC LIMIT "+QuoteSourceMap.RADIUS,
                    new String[]{String.valueOf(sourceId)})){
                while(c.moveToNext())after.add(new QuoteSourceMap.Entry(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
            }
        }
        return new QuoteSourceMap.Context(sourceId,source,before,after);
    }
    boolean hasMessage(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM messages WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst();}}
    /** A date lookup is not limited to days with messages: opens the first message on/after
     * the requested day, or the last existing message if the date is beyond the archive. */
    static final class DateMatch {
        final int position; final String day;
        DateMatch(int pos,String date){position=pos;day=date;}
    }
    DateMatch nearestDate(int y,int m,int d){
        String wanted=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m,d);
        // Telegram Desktop can export dd.MM.yyyy HH:mm:ss or ISO yyyy-MM-dd.
        String normalized="CASE WHEN date_text GLOB '??.??.????*' THEN "+
            "substr(date_text,7,4)||'-'||substr(date_text,4,2)||'-'||substr(date_text,1,2) "+
            "WHEN date_text GLOB '????-??-??*' THEN substr(date_text,1,10) ELSE NULL END";
        String base="SELECT id, "+normalized+" AS archive_day FROM messages WHERE "+normalized+" IS NOT NULL";
        long id=-1;String found="";
        String sql=base+" AND "+normalized+" >= ? ORDER BY archive_day ASC,id ASC LIMIT 1";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{wanted})){
            if(c.moveToFirst()){id=c.getLong(0);found=c.getString(1);}
        }
        if(id<0){
            sql=base+" ORDER BY archive_day DESC,id DESC LIMIT 1";
            try(Cursor c=getReadableDatabase().rawQuery(sql,null)){
                if(c.moveToFirst()){id=c.getLong(0);found=c.getString(1);}
            }
        }
        return id<0?new DateMatch(-1,""):new DateMatch(positionFor(id),found);
    }
    int positionForDate(int y,int m,int d){return nearestDate(y,m,d).position;}
    static final class MediaMonth {
        final String month;final int count;
        MediaMonth(String month,int count){this.month=month;this.count=count;}
    }
    /** Grouped by valid ISO / Telegram Desktop dd.MM.yyyy timestamp, no media decoding. */
    private static String mediaMonthExpr(){
        return "CASE WHEN m.date_text GLOB '????-??-??*' THEN substr(m.date_text,1,7) "+
            "WHEN m.date_text GLOB '??.??.????*' THEN substr(m.date_text,7,4)||'-'||substr(m.date_text,4,2) "+
            "ELSE NULL END";
    }
    List<MediaMonth> mediaMonths(){
        ArrayList<MediaMonth> months=new ArrayList<>();String month=mediaMonthExpr();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT "+month+" AS ym,COUNT(*) FROM attachments a JOIN messages m ON m.id=a.message_id "+
                "WHERE a.kind IN ('image','video','round','gif','sticker') AND "+month+" IS NOT NULL "+
                "GROUP BY ym ORDER BY ym DESC LIMIT 240",null)){
            while(c.moveToNext())months.add(new MediaMonth(c.getString(0),c.getInt(1)));
        }
        return months;
    }
    List<Attachment> mediaForMonth(String month,int limit){
        if(month==null||!month.matches("[0-9]{4}-(0[1-9]|1[0-2])"))return new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind,m.author,m.date_text,m.body FROM attachments a "+
                "JOIN messages m ON m.id=a.message_id WHERE "+mediaMonthExpr()+"=? AND "+
                "a.kind IN ('image','video','round','gif','sticker') ORDER BY m.id DESC,a.id DESC LIMIT "+
                Math.min(48,Math.max(1,limit));
        ArrayList<Attachment> found=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{month})){
            while(c.moveToNext()){
                Attachment item=new Attachment();item.id=c.getLong(0);item.messageId=c.getLong(1);
                item.path=c.getString(2);item.kind=c.getString(3);item.author=c.getString(4);
                item.date=c.getString(5);item.body=c.getString(6);found.add(item);
            }
        }
        return found;
    }
    private String mediaWhere(String kind){
        switch(kind){
            case "all":return "";
            case "photos":return " WHERE a.kind='image'";
            case "videos":return " WHERE a.kind='video'";
            case "audio":return " WHERE a.kind='audio'";
            default:return " WHERE a.kind='"+kind.replace("'","")+"'";
        }
    }
    static final class MediaCollection {
        final long id;final String name;final int count;
        MediaCollection(long id,String name,int count){this.id=id;this.name=name;this.count=count;}
    }
    long createCollection(String userName){
        String name=userName==null?"":userName.trim();
        if(name.isEmpty()||name.length()>60||name.contains("\n"))throw new IllegalArgumentException("Название: от 1 до 60 символов без переноса строки");
        ContentValues value=new ContentValues();value.put("name",name);
        long id=getWritableDatabase().insertWithOnConflict("media_collections",null,value,SQLiteDatabase.CONFLICT_IGNORE);
        if(id<0)throw new IllegalArgumentException("Коллекция с таким названием уже существует");
        return id;
    }
    /** Deletes collection references only; no files, messages or attachment records are removed. */
    int deleteCollection(long id){
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try{
            database.delete("collection_entries","collection_id=?",new String[]{String.valueOf(id)});
            int removed=database.delete("media_collections","id=?",new String[]{String.valueOf(id)});
            database.setTransactionSuccessful();
            return removed;
        }finally{database.endTransaction();}
    }
    List<MediaCollection> collections(){
        ArrayList<MediaCollection> out=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT c.id,c.name,COUNT(a.id) FROM media_collections c LEFT JOIN collection_entries e ON c.id=e.collection_id " +
                "LEFT JOIN attachments a ON a.id=e.attachment_id AND a.path=e.path GROUP BY c.id ORDER BY c.name COLLATE NOCASE",null)){
            while(c.moveToNext())out.add(new MediaCollection(c.getLong(0),c.getString(1),c.getInt(2)));
        }
        return out;
    }
    void addToCollection(long collectionId,Attachment item){
        if(item==null||item.path==null)throw new IllegalArgumentException("Неподдерживаемое вложение");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM attachments WHERE id=? AND path=?",new String[]{String.valueOf(item.id),item.path})){
            if(!c.moveToFirst())throw new IllegalArgumentException("Файл отсутствует в текущем чате");
        }
        ContentValues value=new ContentValues();value.put("collection_id",collectionId);
        value.put("attachment_id",item.id);value.put("path",item.path);
        getWritableDatabase().insertWithOnConflict("collection_entries",null,value,SQLiteDatabase.CONFLICT_IGNORE);
    }
    void removeFromCollection(long collectionId,long attachmentId){
        getWritableDatabase().delete("collection_entries","collection_id=? AND attachment_id=?",
                new String[]{String.valueOf(collectionId),String.valueOf(attachmentId)});
    }
    List<Attachment> collectionMedia(long collectionId,int limit){
        ArrayList<Attachment> list=new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind,m.author,m.date_text,m.body FROM collection_entries e " +
                "JOIN attachments a ON a.id=e.attachment_id AND a.path=e.path " +
                "JOIN messages m ON m.id=a.message_id WHERE e.collection_id=? " +
                "ORDER BY a.message_id DESC,a.id DESC LIMIT "+Math.max(1,Math.min(limit,500));
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(collectionId)})){
            while(c.moveToNext()){
                Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);
                a.path=c.getString(2);a.kind=c.getString(3);a.author=c.getString(4);
                a.date=c.getString(5);a.body=c.getString(6);list.add(a);
            }
        }
        return list;
    }
    List<Attachment> distinctMediaForInspection(int max){
        ArrayList<Attachment> list=new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind FROM attachments a " +
                "JOIN (SELECT MIN(id) id FROM attachments GROUP BY path) unique_paths ON unique_paths.id=a.id ORDER BY a.id LIMIT "+Math.max(1,Math.min(300,max));
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){
            while(c.moveToNext()){
                Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);
                a.path=c.getString(2);a.kind=c.getString(3);list.add(a);
            }
        }
        return list;
    }
    int mediaCount(String kind){try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM attachments a"+mediaWhere(kind),null)){return c.moveToFirst()?c.getInt(0):0;}}
    List<Attachment> mediaPage(String kind,int offset,int limit){
        List<Attachment> list=new ArrayList<>();
        String sql="SELECT a.id,a.message_id,a.path,a.kind,m.author,m.date_text,m.body FROM attachments a JOIN messages m ON a.message_id=m.id"+
                mediaWhere(kind)+" ORDER BY a.message_id,a.id LIMIT "+limit+" OFFSET "+Math.max(0,offset);
        try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext()){
            Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);a.path=c.getString(2);a.kind=c.getString(3);
            a.author=c.getString(4);a.date=c.getString(5);a.body=c.getString(6);list.add(a);
        }}
        return list;
    }
    /** Fetch all media for a page in batches instead of one SQLite query per message. */
    Map<Long,List<Attachment>> attachmentsForMessages(List<Item> items){
        HashMap<Long,List<Attachment>> out=new HashMap<>();
        if(items==null||items.isEmpty())return out;
        SQLiteDatabase database=getReadableDatabase();
        for(int start=0;start<items.size();start+=200){
            int size=Math.min(200,items.size()-start);
            StringBuilder clause=new StringBuilder();
            String[] args=new String[size];
            for(int i=0;i<size;i++){
                if(i>0)clause.append(',');
                clause.append('?');
                args[i]=String.valueOf(items.get(start+i).id);
            }
            String sql="SELECT id,message_id,path,kind FROM attachments WHERE message_id IN ("+clause+") ORDER BY message_id,id";
            try(Cursor c=database.rawQuery(sql,args)){
                while(c.moveToNext()){
                    Attachment a=new Attachment();
                    a.id=c.getLong(0);a.messageId=c.getLong(1);
                    a.path=c.getString(2);a.kind=c.getString(3);
                    List<Attachment> group=out.get(a.messageId);
                    if(group==null){group=new ArrayList<>();out.put(a.messageId,group);}
                    group.add(a);
                }
            }
        }
        return out;
    }
    Attachment adjacentMedia(String kind,long messageId,long attachmentId,boolean forward){
        String op=forward?">":"<";String order=forward?"ASC":"DESC";
        String sql="SELECT a.id,a.message_id,a.path,a.kind,m.author,m.date_text,m.body "+
            "FROM attachments a JOIN messages m ON m.id=a.message_id WHERE a.kind=? AND "+
            "(a.message_id "+op+" ? OR (a.message_id=? AND a.id "+op+" ?)) "+
            "ORDER BY a.message_id "+order+",a.id "+order+" LIMIT 1";
        try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{kind,String.valueOf(messageId),String.valueOf(messageId),String.valueOf(attachmentId)})){
            if(!c.moveToFirst())return null;
            Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);a.path=c.getString(2);
            a.kind=c.getString(3);a.author=c.getString(4);a.date=c.getString(5);a.body=c.getString(6);return a;
        }
    }
    List<Attachment> attachmentsForMessage(long id){
        List<Attachment> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,message_id,path,kind FROM attachments WHERE message_id=? ORDER BY id",new String[]{String.valueOf(id)})){while(c.moveToNext()){
            Attachment a=new Attachment();a.id=c.getLong(0);a.messageId=c.getLong(1);a.path=c.getString(2);a.kind=c.getString(3);list.add(a);
        }}
        return list;
    }
}
