package org.openarchive.telegram;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;

/** Deterministic question answering over a single imported chat. No network or model. */
final class ChatQuestionAnalyst {
    enum Kind { FIRST, LAST, TOTAL, MOST, TOPIC, FIRST_TOPIC, LAST_TOPIC, DAY, UNKNOWN }
    static final String HELP="Примеры: Кто первый написал? · Кто написал последним? · Сколько сообщений? · Кто больше всего писал? · Найди «встреча» · Когда впервые упоминали «встреча»? · Что писали 01.02.2026?";
    static final class Query {
        final Kind kind; final String phrase; final LocalDate day;
        Query(Kind k,String p,LocalDate d){kind=k;phrase=p;day=d;}
    }
    private static String fold(String s){
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е');
    }
    static Query parse(String raw){
        String q=fold(raw).trim().replaceAll("[?!.]+$","").replaceAll("\\s+"," ");
        if(q.matches("кто (первым|первый) написал( сообщение| в чате| в переписке)?|кто начал переписку|первое сообщение|когда началась переписка"))return new Query(Kind.FIRST,"",null);
        if(q.matches("кто (написал последним|последним написал)|последнее сообщение|когда закончилось общение"))return new Query(Kind.LAST,"",null);
        if(q.matches("сколько (всего )?сообщений( в чате| в переписке)?|количество сообщений"))return new Query(Kind.TOTAL,"",null);
        if(q.matches("кто (больше всего писал|больше писал|написал больше всего сообщений|написал больше сообщений|самый активный)"))return new Query(Kind.MOST,"",null);
        Matcher m=Pattern.compile("^(найди|найти|сколько раз упоминали|когда впервые упоминали|кто первым упомянул|когда последний раз упоминали) [«\"]([^»\"]{1,160})[»\"]$").matcher(q);
        if(m.matches()&&!m.group(2).trim().isEmpty()){
            String action=m.group(1);
            Kind k=action.contains("впервые")||action.contains("первым")?Kind.FIRST_TOPIC:action.contains("последний")?Kind.LAST_TOPIC:Kind.TOPIC;
            return new Query(k,m.group(2).trim(),null);
        }
        m=Pattern.compile("^что писали (\\d{2}\\.\\d{2}\\.\\d{4}|\\d{4}-\\d{2}-\\d{2})$").matcher(q);
        if(m.matches()){ChatTrends.Moment t=ChatTrends.parse(m.group(1));if(t!=null)return new Query(Kind.DAY,"",t.day);}
        return new Query(Kind.UNKNOWN,"",null);
    }
    static final class Evidence {
        final long id;final String author,date,excerpt;
        final LocalDate day;final LocalDateTime time;
        Evidence(long id,String author,String date,String body,ChatTrends.Moment t,String phrase){
            this.id=id;this.author=author==null||author.trim().isEmpty()?"Неизвестный отправитель":author.trim();
            this.date=date==null?"":date;day=t==null?null:t.day;time=t==null?null:t.clock;
            String s=body==null?"":body;
            int at=phrase.isEmpty()?-1:s.toLowerCase(Locale.ROOT).replace('ё','е').indexOf(phrase);
            int start=at<0?0:Math.min(s.length(),Math.max(0,at-80)),end=Math.min(s.length(),start+280);
            if(start>0&&start<s.length()&&Character.isLowSurrogate(s.charAt(start)))start--;
            if(end<s.length()&&end>0&&Character.isHighSurrogate(s.charAt(end-1)))end++;
            excerpt=s.isEmpty()?"[Нет текста; возможно вложение]":(start>0?"…":"")+s.substring(start,end)+(end<s.length()?"…":"");
        }
    }
    static final class Answer {
        final String text;final List<Evidence> sources;
        Answer(String t,List<Evidence> s){text=t;sources=Collections.unmodifiableList(new ArrayList<>(s));}
    }
    static final class Accumulator {
        final Query query;long total,matched,unknownDate,unknownTime;
        final Map<String,Long> authors=new HashMap<>();
        final List<Evidence> examples=new ArrayList<>();
        boolean capped,firstTie,lastTie,firstIncomplete,lastIncomplete;
        Evidence first,last;
        Accumulator(Query q){query=q;}
        private static int compare(Evidence a,Evidence b){
            int d=a.day.compareTo(b.day);
            if(d!=0)return d;
            if(a.time==null||b.time==null)return 0;
            return a.time.compareTo(b.time);
        }
        void add(long id,String author,String date,String body){
            total++;
            if(query.kind==Kind.TOTAL||query.kind==Kind.UNKNOWN)return;
            if(query.kind==Kind.MOST){
                String key=author==null||author.trim().isEmpty()?"Неизвестный отправитель":author.trim();
                if(authors.containsKey(key)||authors.size()<10000)authors.put(key,authors.getOrDefault(key,0L)+1);
                else capped=true;
                return;
            }
            ChatTrends.Moment stamp=ChatTrends.parse(date);
            if(query.kind==Kind.DAY){if(stamp==null){unknownDate++;return;}if(!query.day.equals(stamp.day))return;}
            if(query.kind==Kind.TOPIC||query.kind==Kind.FIRST_TOPIC||query.kind==Kind.LAST_TOPIC)
                if(!fold(body).contains(query.phrase))return;
            matched++;
            if(stamp==null)unknownDate++;
            else if(stamp.clock==null)unknownTime++;
            Evidence row=null;
            if(examples.size()<8&&(query.kind==Kind.DAY||query.kind==Kind.TOPIC)){
                row=new Evidence(id,author,date,body,stamp,query.phrase);examples.add(row);
            }
            if(stamp==null||query.kind==Kind.DAY||query.kind==Kind.TOPIC)return;
            boolean earliest=query.kind==Kind.FIRST||query.kind==Kind.FIRST_TOPIC;
            Evidence current=earliest?first:last;
            int dayOrder=current==null?0:stamp.day.compareTo(current.day);
            boolean newDay=current==null||(earliest?dayOrder<0:dayOrder>0);
            boolean sameDay=current!=null&&dayOrder==0;
            if(newDay){
                Evidence next=new Evidence(id,author,date,body,stamp,query.phrase);
                if(earliest){first=next;firstTie=false;firstIncomplete=stamp.clock==null;}
                else{last=next;lastTie=false;lastIncomplete=stamp.clock==null;}
            }else if(sameDay){
                boolean incomplete=stamp.clock==null||current.time==null;
                if(earliest)firstIncomplete|=incomplete;else lastIncomplete|=incomplete;
                int order=incomplete?0:stamp.clock.compareTo(current.time);
                if((earliest?order<0:order>0)||(current.time==null&&stamp.clock!=null)){
                    Evidence next=new Evidence(id,author,date,body,stamp,query.phrase);
                    if(earliest){first=next;firstTie=false;}
                    else{last=next;lastTie=false;}
                }else if(order==0&&id!=current.id){
                    if(earliest)firstTie=true;else lastTie=true;
                }
            }
        }
        Answer finish(){
            String scope="\nТолько импортированная переписка; отсутствующие или удалённые сообщения не учитываются.";
            if(query.kind==Kind.UNKNOWN)return new Answer("Этот вопрос пока не поддерживается. "+HELP,Collections.emptyList());
            if(query.kind==Kind.TOTAL)return new Answer("Всего сообщений: "+total+"."+scope,Collections.emptyList());
            if(query.kind==Kind.MOST){
                if(authors.isEmpty())return new Answer("Сообщений нет.",Collections.emptyList());
                if(capped)return new Answer("Слишком много различных имён для точного рейтинга. Результат не определён.",Collections.emptyList());
                long max=Collections.max(authors.values());List<String> leaders=new ArrayList<>();
                for(Map.Entry<String,Long> e:authors.entrySet())if(e.getValue()==max)leaders.add(e.getKey());
                Collections.sort(leaders);int n=leaders.size();
                String names=String.join(", ",leaders.subList(0,Math.min(8,n)));
                return new Answer((n>1?"Одинаковый максимум: ":"Больше всего сообщений: ")+names+" — по "+max+" из "+total+(n>8?" (всего лидеров: "+n+")":"")+". Учитываются имена из экспорта, не идентификаторы людей."+scope,Collections.emptyList());
            }
            if(matched==0)return new Answer("Подходящих сообщений не найдено. Проверено: "+total+"."+(query.kind==Kind.DAY&&unknownDate>0?" Сообщений с неизвестной датой: "+unknownDate+".":"")+scope,Collections.emptyList());
            if(query.kind==Kind.DAY||query.kind==Kind.TOPIC){
                String what=query.kind==Kind.DAY?"Сообщений за "+query.day:"Сообщений с буквальным упоминанием «"+query.phrase+"»";
                return new Answer(what+": "+matched+". Показано до 8 примеров в порядке импорта; это не смысловой пересказ."+(query.kind==Kind.DAY&&unknownDate>0?" Сообщений с неизвестной датой: "+unknownDate+".":"")+scope,examples);
            }
            boolean earliest=query.kind==Kind.FIRST||query.kind==Kind.FIRST_TOPIC;
            Evidence e=earliest?first:last;boolean tie=earliest?(firstTie||firstIncomplete):(lastTie||lastIncomplete);
            if(e==null)return new Answer("Найдено сообщений: "+matched+", но ни у одного нет распознанной даты. Порядок неизвестен."+scope,Collections.emptyList());
            String prefix=tie?"Возможное "+(earliest?"самое раннее":"самое позднее"):(earliest?"Самое раннее":"Самое позднее");
            String caveat=(tie?" Есть сообщения с одинаковой или неполной отметкой времени; однозначный порядок не установлен.":"")+
                (unknownDate>0?" Без распознанной даты: "+unknownDate+"; абсолютный порядок не гарантирован.":"")+
                (unknownTime>0?" Без точного времени: "+unknownTime+".":"");
            return new Answer(prefix+" найденное сообщение: "+e.author+" · "+e.date+"."+caveat+
                " Получатель отдельного сообщения не определяется. Время как в экспорте."+scope,Collections.singletonList(e));
        }
    }
}
