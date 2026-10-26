package org.openarchive.telegram;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Optional, read-only vocabulary lookup; compatible failures fall back to exact FTS. */
final class IndexedTypoLookup {
    static final class Result {
        final Map<String,List<String>> suggestions;
        final String notice;
        Result(Map<String,List<String>> s,String n){suggestions=s;notice=n;}
    }
    private IndexedTypoLookup(){}
    static Result collect(SQLiteDatabase readOnly, List<String> terms) {
        Map<String,List<String>> found=new LinkedHashMap<>();
        try {
            // Only a connection-local TEMP virtual table is created: never alters archives.
            readOnly.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS temp.ta_vocabulary USING fts4aux(main,messages_fts)");
            for(String term:terms){
                String word=FuzzyWordPolicy.normalized(term);
                if(word.length()<FuzzyWordPolicy.MIN_LENGTH || word.length()>24 ||
                    !word.matches("[\\p{L}]+")) continue;
                String prefix=word.substring(0,2);
                ArrayList<String> words=new ArrayList<>();
                try(Cursor c=readOnly.rawQuery("SELECT term FROM temp.ta_vocabulary " +
                        "WHERE col='*' AND term>=? AND term<? ORDER BY term LIMIT "+FuzzyWordPolicy.MAX_CANDIDATES,
                        new String[]{prefix,FuzzyWordPolicy.prefixUpperBound(prefix)})){
                    while(c.moveToNext())words.add(c.getString(0));
                }
                List<String> options=FuzzyWordPolicy.suggest(word,words);
                if(!options.isEmpty())found.put(word,options);
            }
            return new Result(found,"Опечатки: проверены только слова из индекса (общий список ограничен)");
        }catch(Exception ex){
            // FTS4aux might be absent or disabled by OEM SQLite build.
            return new Result(new LinkedHashMap<String,List<String>>(),
                "Поиск опечаток недоступен на этом устройстве: обычный индекс продолжает работать");
        }
    }
}
