package org.openarchive.telegram;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;

/** Deterministic question answering over a single imported chat. No network or model. */
final class ChatQuestionAnalyst {
    enum Kind { FIRST, LAST, TOTAL, MOST, TOPIC, FIRST_TOPIC, LAST_TOPIC, DAY, ACTIVE_DAYS, BUSIEST_DAY, LONGEST_GAP, QUESTIONS, AUTHOR_COUNT, PARTICIPANTS, LEAST, TEXT_COUNT, LONGEST_TEXT, AVG_LENGTH, AVG_DAY, PEAK_HOUR, WEEKEND, INSULT_PHRASES, APOLOGY_PHRASES, UNKNOWN }
    static final String HELP="Примеры: Кто первый написал? · Кто написал последним? · Сколько сообщений? · Кто больше всего писал? · Найди «встреча» · Когда впервые упоминали «встреча»? · Что писали 01.02.2026?";
    static final class Query {
        final Kind kind; final String phrase; final LocalDate day;
        Query(Kind k,String p,LocalDate d){kind=k;phrase=p;day=d;}
    }
    static final String[][] PRESETS={
        {"Начало и история","Кто первый написал?","Кто написал последним?"},
        {"Активность","Сколько сообщений?","Кто больше всего писал?","Сколько дней общались?"},
        {"Ритм общения","Самый активный день?","Самый долгий перерыв?"},
        {"Содержание","Сколько сообщений с вопросами?","Найди «встреча»","Когда впервые упоминали «встреча»?","Когда последний раз упоминали «встреча»?"},
        {"Участники и привычки","Сколько участников?","Кто меньше всего писал?","Сколько сообщений с текстом?","Самое длинное сообщение?","Средняя длина сообщения?"},
        {"Распорядок общения","Сколько сообщений в активный день?","Самый активный час?","Сколько сообщений по выходным?"}
    };
    private static String fold(String s){
        return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е');
    }
    static Query parse(String raw){
        String q=fold(raw).trim().replaceAll("[?!.]+$","").replaceAll("\\s+"," ");
        if(q.matches("кто (извинялся|извинился|извинилась|просил прощения|просила прощения)|кто писал извинения"))return new Query(Kind.APOLOGY_PHRASES,"",null);
        if(q.matches("кто (оскорблял|оскорбляла|оскорбил|оскорбила)( собеседника)?|кто писал оскорбления"))return new Query(Kind.INSULT_PHRASES,"",null);
        if(q.matches("кто (первым|первый) написал( сообщение| в чате| в переписке| кому)?|кто кому первый написал|кто начал переписку|первое сообщение|когда началась переписка"))return new Query(Kind.FIRST,"",null);
        if(q.matches("кто (написал последним|последним написал)|последнее сообщение|когда закончилось общение"))return new Query(Kind.LAST,"",null);
        if(q.matches("сколько (всего )?сообщений( в чате| в переписке)?|количество сообщений"))return new Query(Kind.TOTAL,"",null);
        if(q.matches("кто (больше всего писал|больше писал|написал больше всего сообщений|написал больше сообщений|самый активный)"))return new Query(Kind.MOST,"",null);
        if(q.matches("сколько дней общались|сколько дней переписывались|сколько активных дней"))return new Query(Kind.ACTIVE_DAYS,"",null);
        if(q.matches("самый активный день|когда было больше всего сообщений|в какой день больше всего писали"))return new Query(Kind.BUSIEST_DAY,"",null);
        if(q.matches("самый долгий перерыв|самый длинный перерыв|когда дольше всего не общались"))return new Query(Kind.LONGEST_GAP,"",null);
        if(q.matches("сколько сообщений с вопросами|сколько сообщений содержат вопросы"))return new Query(Kind.QUESTIONS,"",null);
        if(q.matches("сколько участников|сколько отправителей"))return new Query(Kind.PARTICIPANTS,"",null);
        if(q.matches("кто меньше всего писал|кто написал меньше всего сообщений"))return new Query(Kind.LEAST,"",null);
        if(q.matches("сколько сообщений с текстом|сколько текстовых сообщений"))return new Query(Kind.TEXT_COUNT,"",null);
        if(q.matches("самое длинное сообщение|кто написал самое длинное сообщение"))return new Query(Kind.LONGEST_TEXT,"",null);
        if(q.matches("средняя длина сообщения|средняя длина текста"))return new Query(Kind.AVG_LENGTH,"",null);
        if(q.matches("сколько сообщений в активный день|среднее число сообщений в активный день"))return new Query(Kind.AVG_DAY,"",null);
        if(q.matches("самый активный час|в какой час больше всего сообщений"))return new Query(Kind.PEAK_HOUR,"",null);
        if(q.matches("сколько сообщений по выходным|сколько сообщений в выходные"))return new Query(Kind.WEEKEND,"",null);
        Matcher m=Pattern.compile("^сколько сообщений (написал|написала) [«\"]([^»\"]{1,160})[»\"]$").matcher(q);
        if(m.matches())return new Query(Kind.AUTHOR_COUNT,m.group(2).trim(),null);
        m=Pattern.compile("^(найди|найти|сколько раз упоминали|когда впервые упоминали|кто первым упомянул|когда последний раз упоминали) [«\"]([^»\"]{1,160})[»\"]$").matcher(q);
        if(m.matches()&&!m.group(2).trim().isEmpty()){
            String action=m.group(1);
            Kind k=action.contains("впервые")||action.contains("первым")?Kind.FIRST_TOPIC:action.contains("последний")?Kind.LAST_TOPIC:Kind.TOPIC;
            return new Query(k,m.group(2).trim(),null);
        }
        m=Pattern.compile("^что писали (\\d{2}\\.\\d{2}\\.\\d{4}|\\d{4}-\\d{2}-\\d{2})$").matcher(q);
        if(m.matches()){ChatTrends.Moment t=ChatTrends.parse(m.group(1));if(t!=null)return new Query(Kind.DAY,"",t.day);}
        return new Query(Kind.UNKNOWN,"",null);
    }
    static boolean supports(String question){return parse(question).kind!=Kind.UNKNOWN;}
    static Answer readOnly(java.io.File file,Query query,java.util.concurrent.atomic.AtomicBoolean cancelled){
        Accumulator result=new Accumulator(query);
        if(cancelled.get())return null;
        if(query.kind==Kind.UNKNOWN)return result.finish();
        try(android.database.sqlite.SQLiteDatabase database=android.database.sqlite.SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            android.database.Cursor rows=database.rawQuery("SELECT id,author,date_text,body FROM messages ORDER BY id",null)){
            while(!cancelled.get()&&rows.moveToNext())result.add(rows.getLong(0),rows.getString(1),rows.getString(2),rows.getString(3));
        }
        return cancelled.get()?null:result.finish();
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
        final TreeMap<LocalDate,Long> days=new TreeMap<>();
        final List<Evidence> examples=new ArrayList<>();
        final long[] hours=new long[24];
        final Map<String,Evidence> authorExamples=new LinkedHashMap<>();
        final Matcher phraseMatcher;
        long textChars,longestChars;Evidence longest;
        boolean capped,firstTie,lastTie,firstIncomplete,lastIncomplete;
        Evidence first,last;
        Accumulator(Query q){query=q;phraseMatcher=q.kind==Kind.APOLOGY_PHRASES?BehaviorScan.RULES[3].matcher(""):q.kind==Kind.INSULT_PHRASES?BehaviorScan.RULES[0].matcher(""):null;}
        private static int compare(Evidence a,Evidence b){
            int d=a.day.compareTo(b.day);
            if(d!=0)return d;
            if(a.time==null||b.time==null)return 0;
            return a.time.compareTo(b.time);
        }
        void add(long id,String author,String date,String body){
            total++;
            if(query.kind==Kind.TOTAL||query.kind==Kind.UNKNOWN)return;
            if(phraseMatcher!=null){
                if(body==null||body.isEmpty())return;
                phraseMatcher.reset(body);
                if(phraseMatcher.find()){
                    matched++;
                    String name=author==null||author.trim().isEmpty()?"Неизвестный отправитель":author.trim();
                    if(authors.containsKey(name)||authors.size()<10000){
                        authors.put(name,authors.getOrDefault(name,0L)+1);
                        if(!authorExamples.containsKey(name)&&authorExamples.size()<8)
                            authorExamples.put(name,new Evidence(id,name,date,body,ChatTrends.parse(date),fold(phraseMatcher.group())));
                    }else capped=true;
                    if(examples.size()<8)examples.add(new Evidence(id,name,date,body,ChatTrends.parse(date),fold(phraseMatcher.group())));
                }
                phraseMatcher.reset("");return;
            }
            if(query.kind==Kind.TEXT_COUNT||query.kind==Kind.LONGEST_TEXT||query.kind==Kind.AVG_LENGTH){
                if(body!=null&&!body.trim().isEmpty()){
                    matched++;int chars=body.codePointCount(0,body.length());textChars+=chars;
                    if(query.kind==Kind.LONGEST_TEXT&&chars>longestChars){
                        longestChars=chars;longest=new Evidence(id,author,date,body,ChatTrends.parse(date),"");
                    }
                }
                return;
            }
            if(query.kind==Kind.PEAK_HOUR||query.kind==Kind.WEEKEND){
                ChatTrends.Moment stamp=ChatTrends.parse(date);
                if(stamp==null){unknownDate++;return;}
                if(query.kind==Kind.PEAK_HOUR){
                    if(stamp.clock==null){unknownTime++;return;}
                    hours[stamp.clock.getHour()]++;matched++;
                }else if(stamp.day.getDayOfWeek().getValue()>=6)matched++;
                return;
            }
            if(query.kind==Kind.QUESTIONS){
                if(body!=null&&(body.indexOf('?')>=0||body.indexOf('？')>=0)){
                    matched++;if(examples.size()<8)examples.add(new Evidence(id,author,date,body,ChatTrends.parse(date),""));
                }
                return;
            }
            if(query.kind==Kind.AUTHOR_COUNT){
                if(fold(author).trim().equals(query.phrase)){
                    matched++;if(examples.size()<8)examples.add(new Evidence(id,author,date,body,ChatTrends.parse(date),""));
                }
                return;
            }
            if(query.kind==Kind.ACTIVE_DAYS||query.kind==Kind.BUSIEST_DAY||query.kind==Kind.LONGEST_GAP||query.kind==Kind.AVG_DAY){
                ChatTrends.Moment stamp=ChatTrends.parse(date);
                if(stamp==null){unknownDate++;return;}
                if(days.containsKey(stamp.day)||days.size()<100000)
                    days.put(stamp.day,days.getOrDefault(stamp.day,0L)+1);
                else capped=true;
                return;
            }
            if(query.kind==Kind.MOST||query.kind==Kind.LEAST||query.kind==Kind.PARTICIPANTS){
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
            if(phraseMatcher!=null){
                String label=query.kind==Kind.APOLOGY_PHRASES?"Формулировки извинений":"Формулировки возможных оскорблений";
                StringBuilder out=new StringBuilder(label+": "+matched+" сообщений из "+total+".");
                List<Map.Entry<String,Long>> ranked=new ArrayList<>(authors.entrySet());
                ranked.sort(Comparator.<Map.Entry<String,Long>>comparingLong(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey));
                for(int i=0;i<Math.min(8,ranked.size());i++)out.append("\n").append(ranked.get(i).getKey()).append(": ").append(ranked.get(i).getValue());
                if(ranked.size()>8||capped)out.append("\nПоказана ограниченная разбивка по отправителям.");
                out.append("\nЭто буквальные признаки в сообщениях, а не оценка намерений: возможны цитаты, шутки, отрицания и совпадающие имена. Отсутствие совпадений не доказывает отсутствие поступка.");
                List<Evidence> sources=new ArrayList<>(authorExamples.values());
                for(Evidence e:examples)if(sources.size()<8&&sources.stream().noneMatch(x->x.id==e.id))sources.add(e);
                return new Answer(out.toString()+scope,sources);
            }
            if(query.kind==Kind.UNKNOWN)return new Answer("Этот вопрос пока не поддерживается. "+HELP,Collections.emptyList());
            if(query.kind==Kind.TOTAL)return new Answer("Всего сообщений: "+total+"."+scope,Collections.emptyList());
            if(query.kind==Kind.TEXT_COUNT)return new Answer("Сообщений с непустым текстом: "+matched+" из "+total+"."+scope,Collections.emptyList());
            if(query.kind==Kind.AVG_LENGTH)return new Answer(matched==0?"Нет непустых текстов."+scope:
                "Средняя длина непустого текста: "+String.format(Locale.ROOT,"%.1f",(double)textChars/matched)+" символов Unicode."+scope,Collections.emptyList());
            if(query.kind==Kind.LONGEST_TEXT)return new Answer(longest==null?"Нет непустых текстов."+scope:
                "Самое длинное найденное сообщение: "+longest.author+" · "+longestChars+" символов Unicode. При равенстве показан первый вариант в порядке импорта."+scope,
                longest==null?Collections.emptyList():Collections.singletonList(longest));
            if(query.kind==Kind.WEEKEND)return new Answer("Сообщений по субботам и воскресеньям: "+matched+". Без даты: "+unknownDate+"."+scope,Collections.emptyList());
            if(query.kind==Kind.PEAK_HOUR){
                long max=0;for(long n:hours)max=Math.max(max,n);
                if(max==0)return new Answer("Нет сообщений с точным временем."+scope,Collections.emptyList());
                List<String> best=new ArrayList<>();
                for(int h=0;h<24;h++)if(hours[h]==max)best.add(String.format(Locale.ROOT,"%02d:00",h));
                return new Answer("Максимум за час суток: "+max+". Часы: "+String.join(", ",best)+". Время как в экспорте. Без даты или времени: "+(unknownDate+unknownTime)+"."+scope,Collections.emptyList());
            }
            if(query.kind==Kind.QUESTIONS||query.kind==Kind.AUTHOR_COUNT){
                String label=query.kind==Kind.QUESTIONS?"Сообщений со знаком вопроса":"Сообщений от имени «"+query.phrase+"»";
                return new Answer(label+": "+matched+" из "+total+". "+(query.kind==Kind.QUESTIONS?"Это признак по пунктуации, а не распознавание смысла.":"Совпадение имени без учёта регистра; одинаковые имена не различаются.")+scope,examples);
            }
            if(query.kind==Kind.ACTIVE_DAYS||query.kind==Kind.BUSIEST_DAY||query.kind==Kind.LONGEST_GAP||query.kind==Kind.AVG_DAY){
                if(capped)return new Answer("Слишком много разных дат для точного расчёта.",Collections.emptyList());
                if(days.isEmpty())return new Answer("Нет сообщений с распознанной датой."+scope,Collections.emptyList());
                String missing=unknownDate>0?" Сообщений с неизвестной датой: "+unknownDate+"; они не учтены.":"";
                if(query.kind==Kind.ACTIVE_DAYS)return new Answer("Дней с сообщениями: "+days.size()+". Период: "+days.firstKey()+" — "+days.lastKey()+"."+missing+scope,Collections.emptyList());
                if(query.kind==Kind.AVG_DAY)return new Answer("Среднее в активный день: "+String.format(Locale.ROOT,"%.1f",(double)(total-unknownDate)/days.size())+" сообщений."+missing+scope,Collections.emptyList());
                if(query.kind==Kind.BUSIEST_DAY){
                    long peak=Collections.max(days.values());List<String> dates=new ArrayList<>();int ties=0;
                    for(Map.Entry<LocalDate,Long> e:days.entrySet())if(e.getValue()==peak){ties++;if(dates.size()<8)dates.add(e.getKey().toString());}
                    return new Answer("Максимум за день: "+peak+". Даты: "+String.join(", ",dates)+(ties>8?" (всего дат: "+ties+")":"")+"."+missing+scope,Collections.emptyList());
                }
                LocalDate prev=null,from=null,to=null;long gap=0;
                for(LocalDate day:days.keySet()){
                    if(prev!=null){long n=java.time.temporal.ChronoUnit.DAYS.between(prev,day)-1;if(n>gap){gap=n;from=prev;to=day;}}
                    prev=day;
                }
                String result=gap==0?"Между известными активными днями нет полных пустых дней.":"Самый длинный промежуток без сообщений: "+gap+" полных календарных дней между "+from+" и "+to+".";
                return new Answer(result+" Это не доказательство отсутствия общения вне архива."+missing+scope,Collections.emptyList());
            }
            if(query.kind==Kind.MOST||query.kind==Kind.LEAST||query.kind==Kind.PARTICIPANTS){
                if(authors.isEmpty())return new Answer("Сообщений нет.",Collections.emptyList());
                if(capped)return new Answer("Слишком много различных имён для точного рейтинга. Результат не определён.",Collections.emptyList());
                if(query.kind==Kind.PARTICIPANTS)return new Answer("Различных имён отправителей: "+authors.size()+". Одинаковые имена не различаются; неизвестный отправитель учитывается отдельно."+scope,Collections.emptyList());
                long max=query.kind==Kind.LEAST?Collections.min(authors.values()):Collections.max(authors.values());List<String> leaders=new ArrayList<>();
                for(Map.Entry<String,Long> e:authors.entrySet())if(e.getValue()==max)leaders.add(e.getKey());
                Collections.sort(leaders);int n=leaders.size();
                String names=String.join(", ",leaders.subList(0,Math.min(8,n)));
                return new Answer((query.kind==Kind.LEAST?(n>1?"Одинаковый минимум: ":"Меньше всего сообщений: "):(n>1?"Одинаковый максимум: ":"Больше всего сообщений: "))+names+" — по "+max+" из "+total+(n>8?" (всего лидеров: "+n+")":"")+". Учитываются имена из экспорта, не идентификаторы людей."+scope,Collections.emptyList());
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
