package org.openarchive.telegram;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only, offline chat statistics. No Android dependencies, telemetry or persisted text. */
final class ChatAnalytics {
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*(?:['’\\-][\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*)*");
    private static final Pattern ISO=Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern RU_DATE=Pattern.compile("^(\\d{2})\\.(\\d{2})\\.(\\d{4})(?:\\D|$)");
    private static final int MAX_DICTIONARY=120_000;
    private static final Set<String> COMMON=new HashSet<>(Arrays.asList(
        "и","в","во","не","на","а","я","ты","он","она","оно","они","мы","вы","это","что","как","с","со","к","у","по","от","из","за","для","о","об","про","да","нет","же","ли","ну","то","но","бы","был","была","были","есть","мне","тебе","его","ее","её","их","нас","вас","там","тут","вот","уже","ещё","еще","так","только","очень","тоже","если","или","до","после","the","a","an","and","or","to","of","is","are","in","on","i","you","he","she","it","we","they","for","at","be","that","this","was","were","with"));
    static final class Person {
        final String key;String display;
        long messages,words,characters,attachments;
        final Map<String,Long> kinds=new HashMap<>();
        Person(String k,String name){key=k;display=name;}
        long kind(String kind){Long n=kinds.get(kind);return n==null?0:n;}
    }
    static final class WordCount {
        final String word;final long count;
        WordCount(String word,long count){this.word=word;this.count=count;}
    }
    static final class Snapshot {
        final List<Person> people;
        final List<WordCount> topWords,topMeaningfulWords;
        final Map<String,Long> days,types;
        final ChatTrends.Snapshot trends;
        final long totalMessages,totalWords,totalCharacters,totalMedia;
        final long switchCount;final double meanSwitchMinutes;
        final String firstDay,lastDay;
        final boolean limitedVocabulary;
        Snapshot(List<Person> people,List<WordCount> topWords,List<WordCount> meaningful,
                 Map<String,Long> days,Map<String,Long> types,long messages,long words,long chars,long media,boolean capped,ChatTrends.Snapshot trends,
                 long switchCount,double meanSwitchMinutes){
            this.switchCount=switchCount;this.meanSwitchMinutes=meanSwitchMinutes;
            this.trends=trends;
            this.people=Collections.unmodifiableList(people);
            this.topWords=Collections.unmodifiableList(topWords);
            this.topMeaningfulWords=Collections.unmodifiableList(meaningful);
            this.days=Collections.unmodifiableMap(days);
            this.types=Collections.unmodifiableMap(types);
            this.totalMessages=messages;this.totalWords=words;this.totalCharacters=chars;this.totalMedia=media;
            this.firstDay=days.isEmpty()?"":days.keySet().iterator().next();
            String last="";for(String day:days.keySet())last=day;this.lastDay=last;
            this.limitedVocabulary=capped;
        }
        long type(String kind){Long n=types.get(kind);return n==null?0:n;}
    }
    static final class Accumulator {
        private final Map<String,Person> byAuthor=new LinkedHashMap<>();
        private final Map<String,Long> vocabulary=new HashMap<>();
        private final TreeMap<String,Long> days=new TreeMap<>();
        private final Map<String,Long> types=new HashMap<>();
        private final ChatTrends.Builder trends=new ChatTrends.Builder();
        private long messageCount,wordCount,charCount,mediaCount;
        private String previousAuthor;
        private java.time.LocalDateTime previousTime;
        private long switchCount,switchSeconds;
        private boolean capped;
        private Person person(String name){
            String display=name==null?"":Normalizer.normalize(name,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ");
            if(display.isEmpty())display="Неизвестный отправитель";
            String key=display.toLowerCase(Locale.ROOT);
            Person p=byAuthor.get(key);
            if(p==null){p=new Person(key,display);byAuthor.put(key,p);}
            return p;
        }
        void message(String author,String date,String body){
            Person person=person(author);
            // Approximate response latency: a different sender follows the previous
            // sender within 24 hours. This does NOT establish an actual reply link.
            ChatTrends.Moment stamp=ChatTrends.parse(date);
            java.time.LocalDateTime time=stamp==null?null:stamp.clock;
            if(previousAuthor!=null&&previousTime!=null&&time!=null&&
                !previousAuthor.equals(person.key)){
                long seconds=java.time.Duration.between(previousTime,time).getSeconds();
                if(seconds>=0&&seconds<=86400){switchCount++;switchSeconds+=seconds;}
            }
            previousAuthor=person.key;previousTime=time;
            person.messages++;messageCount++;
            trends.message(person.key,date,body);
            String text=body==null?"":body;
            // Count Unicode code points rather than UTF-16 units (emoji count as one).
            long characters=text.codePointCount(0,text.length());
            charCount+=characters;person.characters+=characters;
            Matcher matcher=WORD.matcher(Normalizer.normalize(text,Normalizer.Form.NFKC));
            while(matcher.find()){
                String token=Normalizer.normalize(matcher.group(),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
                if(token.isEmpty())continue;
                wordCount++;person.words++;
                // Do not retain arbitrarily long attacker-controlled tokens in the word map.
                // This does not change the exact total-word counts.
                if(token.length()>80){capped=true;continue;}
                Long old=vocabulary.get(token);
                if(old!=null)vocabulary.put(token,old+1);
                else if(vocabulary.size()<MAX_DICTIONARY)vocabulary.put(token,1L);
                else capped=true;
            }
            String day=parseDay(date);
            if(day!=null)days.put(day,days.getOrDefault(day,0L)+1L);
        }
        void media(String author,String kind,long count){
            if(count<=0)return;
            Person p=person(author);
            String k=kind==null||kind.trim().isEmpty()?"file":kind.toLowerCase(Locale.ROOT);
            p.attachments+=count;mediaCount+=count;
            p.kinds.put(k,p.kinds.getOrDefault(k,0L)+count);
            types.put(k,types.getOrDefault(k,0L)+count);
        }
        Snapshot snapshot(){
            ArrayList<Person> persons=new ArrayList<>(byAuthor.values());
            persons.sort((a,b)->{int n=Long.compare(b.messages,a.messages);return n!=0?n:a.display.compareToIgnoreCase(b.display);});
            return new Snapshot(persons,rank(false),rank(true),new TreeMap<>(days),new HashMap<>(types),
                messageCount,wordCount,charCount,mediaCount,capped,trends.snapshot(),
                switchCount,switchCount==0?0.0:switchSeconds/(60.0*switchCount));
        }
        private List<WordCount> rank(boolean excludeCommon){
            ArrayList<WordCount> ordered=new ArrayList<>();
            for(Map.Entry<String,Long> e:vocabulary.entrySet()){
                if(excludeCommon&&COMMON.contains(e.getKey()))continue;
                ordered.add(new WordCount(e.getKey(),e.getValue()));
            }
            ordered.sort((a,b)->{int n=Long.compare(b.count,a.count);return n!=0?n:a.word.compareTo(b.word);});
            if(ordered.size()>20)ordered.subList(20,ordered.size()).clear();
            return ordered;
        }
    }
    static String parseDay(String date){
        ChatTrends.Moment m=ChatTrends.parse(date);
        return m==null?null:m.day.toString();
    }
    static double percent(long count,long whole){return whole==0?0:100.0*count/whole;}
}
