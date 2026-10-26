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
                if(term==null)continue;
                String word=term.toLowerCase(java.util.Locale.ROOT);
                String folded=FuzzyWordPolicy.normalized(word);
                if(folded.length()<FuzzyWordPolicy.MIN_LENGTH || folded.length()>24 ||
                    !folded.matches("[\\p{L}]+")) continue;
                // The stored FTS vocabulary may start with ел... OR ёл....
                // Compare distance in normalized form, but query both native
                // prefixes; do not convert the indexed candidate itself.
                String prefix=folded.substring(0,2);
                java.util.LinkedHashSet<String> prefixes=new java.util.LinkedHashSet<>();
                prefixes.add(prefix);
                for(int pos=0;pos<2;pos++){
                    if(prefix.charAt(pos)=='е'){
                        prefixes.add(prefix.substring(0,pos)+'ё'+prefix.substring(pos+1));
                    }
                }
                ArrayList<String> words=new ArrayList<>();
                for(String nativePrefix:prefixes){
                    if(words.size()>=FuzzyWordPolicy.MAX_CANDIDATES)break;
                    int remaining=FuzzyWordPolicy.MAX_CANDIDATES-words.size();
                    try(Cursor c=readOnly.rawQuery("SELECT term FROM temp.ta_vocabulary " +
                            "WHERE col='*' AND term>=? AND term<? ORDER BY term LIMIT "+remaining,
                            new String[]{nativePrefix,FuzzyWordPolicy.prefixUpperBound(nativePrefix)})){
                        while(c.moveToNext())words.add(c.getString(0));
                    }
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
