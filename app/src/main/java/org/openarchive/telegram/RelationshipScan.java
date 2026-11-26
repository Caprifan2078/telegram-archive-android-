package org.openarchive.telegram;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

/** Full archive statistics. Sessions are a six-hour heuristic, not proof of intent. */
final class RelationshipScan {
    static final long SESSION_SECONDS=6*3600;
    static final int MAX_PEOPLE=2000,MAX_PAIRS=10000;
    static final class Person {
        long messages,starts,questions,explicitReplies,receivedReplies,answeredStarts;
        long timedReplies,replySeconds;
        final long[] replyBuckets=new long[5];
    }
    static final class Pair {
        final String from,to;long count,timed,seconds;
        final long[] buckets=new long[5];
        Pair(String from,String to){this.from=from;this.to=to;}
    }
    static final class Report {
        final Map<String,Person> people=new LinkedHashMap<>();
        final Map<List<String>,Pair> replies=new LinkedHashMap<>();
        final BehaviorScan.Report behavior=new BehaviorScan.Report();
        long total,unresolvedReplies,missingTime,unorderedTime,sessions;
        boolean capped,complete;
        LocalDateTime last;
        String sessionStarter;boolean sessionAnswered;
        long invalidReplyTimes,selfReplies;
        static int bucket(long seconds){return seconds<=60?0:seconds<=300?1:seconds<=3600?2:seconds<=86400?3:4;}
        Person person(String name){
            Person p=people.get(name);
            if(p==null){if(people.size()>=MAX_PEOPLE){capped=true;return null;}p=new Person();people.put(name,p);}
            return p;
        }
        static String name(String s){return s==null||s.trim().isEmpty()?"Неизвестный отправитель":s.trim();}
        void add(long id,String author,String date,String body,boolean hasReply,String target){
            add(id,author,date,body,hasReply,target,null);
        }
        void add(long id,String author,String date,String body,boolean hasReply,String target,String targetDate){
            total++;String from=name(author);Person p=person(from);
            if(p!=null){p.messages++;if(body!=null&&(body.contains("?")||body.contains("？")))p.questions++;}
            behavior.add(id,author,date,body);
            ChatTrends.Moment moment=ChatTrends.parse(date);
            if(moment==null||moment.clock==null){missingTime++;last=null;sessionStarter=null;}
            else{
                if(last!=null){
                    long seconds=Duration.between(last,moment.clock).getSeconds();
                    if(seconds<0){unorderedTime++;sessionStarter=null;}
                    else if(seconds>=SESSION_SECONDS){sessions++;if(p!=null)p.starts++;sessionStarter=from;sessionAnswered=false;}
                    else if(sessionStarter!=null&&!sessionAnswered&&!sessionStarter.equals(from)){
                        Person starter=people.get(sessionStarter);if(starter!=null)starter.answeredStarts++;sessionAnswered=true;
                    }
                }else if(total==1){sessions++;if(p!=null)p.starts++;sessionStarter=from;sessionAnswered=false;}
                last=moment.clock;
            }
            if(hasReply){
                if(target==null){unresolvedReplies++;return;}
                String to=name(target);
                if(from.equals(to)){selfReplies++;return;}
                if(p!=null)p.explicitReplies++;
                Person recipient=person(to);if(recipient!=null)recipient.receivedReplies++;
                List<String> key=Arrays.asList(from,to);
                Pair pair=replies.get(key);
                if(pair==null){if(replies.size()>=MAX_PAIRS){capped=true;return;}pair=new Pair(from,to);replies.put(key,pair);}
                pair.count++;
                ChatTrends.Moment original=ChatTrends.parse(targetDate);
                if(moment==null||moment.clock==null||original==null||original.clock==null||moment.clock.isBefore(original.clock)){invalidReplyTimes++;return;}
                long seconds=Duration.between(original.clock,moment.clock).getSeconds();
                pair.timed++;pair.seconds+=seconds;pair.buckets[bucket(seconds)]++;
                if(p!=null){p.timedReplies++;p.replySeconds+=seconds;p.replyBuckets[bucket(seconds)]++;}
            }
        }
    }
    static Report read(File file,AtomicBoolean cancel,LongConsumer progress){
        Report r=new Report();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
            Cursor rows=db.rawQuery("SELECT m.id,m.author,m.date_text,m.body,r.message_id,t.id,t.author,t.date_text FROM messages m LEFT JOIN message_replies r ON r.message_id=m.id LEFT JOIN messages t ON t.id=r.target_message_id ORDER BY m.id",null)){
            while(!cancel.get()&&rows.moveToNext()){
                r.add(rows.getLong(0),rows.getString(1),rows.getString(2),rows.getString(3),!rows.isNull(4),rows.isNull(5)?null:Report.name(rows.getString(6)),rows.getString(7));
                if(r.total%1000==0)progress.accept(r.total);
            }
        }
        r.complete=!cancel.get();r.behavior.complete=r.complete;return r;
    }
    private RelationshipScan(){}
}
