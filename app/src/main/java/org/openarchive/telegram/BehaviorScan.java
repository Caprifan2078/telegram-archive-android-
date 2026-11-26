package org.openarchive.telegram;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full row scan for explainable linguistic leads, NOT a personality score or diagnosis. */
final class BehaviorScan {
    static final String[] LABELS={"Прямые оскорбления","Давление и ультиматумы","Обобщения и обвинения","Извинения","Поддержка и благодарность","Уважение границ"};
    static final Pattern[] RULES={
        Pattern.compile("(?<![\\p{L}])(?:идиот(?:ка|ом)?|дурак|дура|тупица|ничтожество|дебил(?:ка)?)(?![\\p{L}])",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:ты обязан[а]?|ты долж[е]?н[а]?|если не .* то|иначе я|или .* или я|я запрещаю|не смей)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:ты всегда|ты никогда|все из-за тебя|всё из-за тебя|ты во вс[её]м виноват[а]?)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:прости|простите|извини|извиняюсь|моя вина|я был[а]? неправ[а]?)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:спасибо|благодарю|я рядом|горжусь тобой|держись|я тебя поддерживаю)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE),
        Pattern.compile("\\b(?:как тебе удобно|если хочешь|не буду давить|уважаю твой выбор|я понимаю твой отказ|можешь отказаться)\\b",Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE)
    };
    static final int MAX_AUTHORS=2000, MAX_EXAMPLES=24;
    static final class Example {
        final int category;final DeepChatContext.Source source;
        Example(int k,DeepChatContext.Source s){category=k;source=s;}
    }
    static final class Person {
        long messages;final long[] counts=new long[LABELS.length];
    }
    static final class Report {
        final Map<String,Person> people=new LinkedHashMap<>();
        final List<Example> evidence=new ArrayList<>();
        final long[] totals=new long[LABELS.length];
        long scanned,textRows;boolean limited,complete;
        private final int[] samples=new int[LABELS.length];
        private final java.util.regex.Matcher[] matchers=new java.util.regex.Matcher[RULES.length];
        Report(){for(int i=0;i<RULES.length;i++)matchers[i]=RULES[i].matcher("");}
        void add(long id,String name,String date,String body){
            scanned++;String author=name==null||name.trim().isEmpty()?"Неизвестный отправитель":name.trim();
            Person p=people.get(author);
            if(p==null&&people.size()<MAX_AUTHORS){p=new Person();people.put(author,p);}
            if(p==null)limited=true;else p.messages++;
            if(body==null||body.isEmpty())return;
            textRows++;
            for(int i=0;i<RULES.length;i++){
                java.util.regex.Matcher hit=matchers[i].reset(body);
                if(!hit.find()){hit.reset("");continue;}
                totals[i]++;if(p!=null)p.counts[i]++;
                if(samples[i]<4){
                    int start=Math.max(0,hit.start()-90),end=Math.min(body.length(),hit.end()+140);
                    if(start>0&&Character.isLowSurrogate(body.charAt(start)))start--;
                    if(end<body.length()&&end>0&&Character.isHighSurrogate(body.charAt(end-1)))end++;
                    String excerpt=(start>0?"…":"")+body.substring(start,end)+(end<body.length()?"…":"");
                    evidence.add(new Example(i,new DeepChatContext.Source(id,author,date,excerpt)));samples[i]++;
                }
                hit.reset("");
            }
        }
        String summary(){
            StringBuilder s=new StringBuilder("Просмотрено строк: "+scanned+"; с текстом: "+textRows+". "+
                (complete?"Полный проход завершён.":"Неполный проход.")+" Совпадения формулировок, не доказательство намерений. ");
            for(int i=0;i<LABELS.length;i++)s.append(LABELS[i]).append(": ").append(totals[i]).append("; ");
            if(limited)s.append("Разбивка по именам ограничена. ");
            List<Map.Entry<String,Person>> top=new ArrayList<>(people.entrySet());
            top.sort((x,y)->Long.compare(y.getValue().messages,x.getValue().messages));
            for(int k=0;k<Math.min(8,top.size());k++){
                Map.Entry<String,Person> e=top.get(k);s.append(DeepChatContext.clip(e.getKey(),70)).append(" (").append(e.getValue().messages).append(" сообщений): ");
                for(int i=0;i<LABELS.length;i++)s.append(i+1).append("=").append(e.getValue().counts[i]).append(" ");
                s.append("; ");
            }return s.toString();
        }
    }
    static Report read(File file,AtomicBoolean cancel,java.util.function.LongConsumer progress){
        Report report=new Report();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            Cursor rows=db.rawQuery("SELECT id,author,date_text,body FROM messages ORDER BY id",null)){
            while(!cancel.get()&&rows.moveToNext()){
                report.add(rows.getLong(0),rows.getString(1),rows.getString(2),rows.getString(3));
                if(report.scanned%1000==0)progress.accept(report.scanned);
            }
            report.complete=!cancel.get();
        }
        return report;
    }
}
