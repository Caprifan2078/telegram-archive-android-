package org.openarchive.telegram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Privacy-preserving offline search plan for SQLite FTS4/unicode61.
 * The engine finds *actual* messages, never generates an answer or infers an event.
 * Close lexical variants are enabled by default. Related topics are opt-in and
 * explicitly labeled as broader leads, NOT as proofs of a user's actions.
 */
final class OfflineSynonymSearch {
    static final int MAX_CHARS=140, MAX_TERMS=7, MAX_ALTERNATIVES=22;
    private static final Set<String> QUESTIONS=new HashSet<>(Arrays.asList(
        "найди","найти","покажи","показать","где","когда","что","какие","какой","какая",
        "какое","кому","кто","зачем","почему","мне","мы","вы","они","я","он","она",
        "было","были","был","была","есть","про","об","о","это","этот","этом",
        "сообщения","сообщение","сообщений","сообщении","упоминали","говорили","обсуждали",
        "все","среди","переписке","переписках","разговоре","напиши","написал","писал",
        "мой","мою","мои","наши","с","со","по","и","в","на","из","за","для","как","ли"));

    private static final class Variant {
        final String text;
        final boolean prefix;
        Variant(String token){prefix=token.endsWith("*");text=prefix?token.substring(0,token.length()-1):token;}
        String fts(){return "body:"+text+(prefix?"*":"");}
    }
    private static final class Concept {
        final String label;
        final List<Variant> variants;
                Concept(String label,String... words){this.label=label;
            List<Variant> a=new ArrayList<>();for(String w:words)a.add(new Variant(w));
            variants=Collections.unmodifiableList(a);
        }
        boolean contains(String word){for(Variant v:variants)if(v.prefix?word.startsWith(v.text):word.equals(v.text))return true;return false;}
    }
    private static final ArrayList<Concept> CONCEPTS=new ArrayList<>();
    private static final Map<String,Concept> DICTIONARY=new HashMap<>();
    private static final Map<String,Concept> RELATED=new LinkedHashMap<>();
    static {
        // Curated lexical equivalence / clear word-family variations. No universal
        // stemming of short words; комп* and куп* would produce many false hits.
        concept("компьютер", "компьютер*","комп","компа","компе","компу","компом","пк","pc");
        concept("ноутбук", "ноутбук*","ноут","ноута","ноуте","ноутом","laptop");
        concept("телефон", "телефон*","смартфон*","мобильник*","мобила","мобилу","мобилой");
        concept("автомобиль", "автомобил*","машина","машины","машину","машиной","машине","авто","тачка","тачку","тачке","тачки","тачкой");
        concept("велосипед", "велосипед*","велик","велика","велике","велосипедик*");
        concept("мотоцикл", "мотоцикл*","мотик","мотика","байк","байка");
        concept("фотография", "фотограф*","фото","фотка","фотки","фотку","фоток","снимок","снимки","снимка");
        concept("видео", "видео","видеоролик*","видос*","ролик","ролика","ролики","видеозапис*");
        concept("музыка", "музык*","песня","песни","песню","песен","трек","треки","трека","композиция","композиции");
        concept("путешествие", "путешеств*","поездка","поездки","поездку","отпуск","отпуска","отпуске");
        concept("деньги", "деньги","денег","деньгах","деньгами","бабки","бабок","бабками","наличка","наличные");
        concept("покупка", "покупа*","покупк*","купить","купил*","куплю","купим","купят","куплено","приобрет*","приобрел*","приобрёл*","приобрести");
        concept("продажа", "прода*","продал*","продаж*","сбыть","реализовал*");
        concept("благодарность", "спасибо","спасибочки","спасиб","спс","благодар*","признател*","мерси","thanks","thankyou","спасибки","сенкс","thx","thnx","ty","благодарствую");
        concept("извинение", "извини*","извиня*","прости","простите","прощения","прощени*","сорри","sorry");
        concept("поздравление", "поздрав*","поздравля*","счастья","сднемрождения");
        concept("подарок", "подар*","презент","презента","подарочек","сюрприз","сюрприза");
        concept("встреча", "встреч*","увиделись","увидимся","увидеться","пересеклись");
        concept("звонок", "звон*","созвон*","позвони*","позвонил*","набрал*","вызов","вызовы");
        concept("разговор", "разговор*","бесед*","общал*","пообщал*","общени*");
        concept("сообщение", "сообщени*","смс","sms","месседж","месседжи");
        concept("работа", "работ*","труд","труда","трудоустро*","подработк*");
        concept("учеба", "учеб*","учёб*","учился","училась","учебник*","обучени*");
        concept("экзамен", "экзамен*","зачет*","зачёт*","сессия","сессию");
        concept("квартира", "квартир*","жилье","жильё","жилья","апартаменты");
        concept("дом", "дом","дома","дому","домой","жилище");
        concept("болезнь", "болезн*","болею","болел*","заболел*","недомогани*");
        concept("врач", "врач*","доктор*","медик*","терапевт*");
        concept("еда", "еда","еду","поесть","кушать","куша*","покуша*","питани*");
        concept("ресторан", "ресторан*","кафе","кофейня","кофейне","столовая","столовой");
        concept("магазин", "магазин*","супермаркет*","лавка","лавке","торговыйцентр");
        concept("праздник", "праздник*","празднов*","торжеств*","вечеринк*");
        concept("игра", "игра","игры","игру","игрой","игр","гейм","game");
        concept("ремонт", "ремонт*","чинить","почин*","отремонтир*","поломк*");
        concept("погода", "погод*","дожд*","снег*","ливень");
        concept("еда-доставка", "доставк*","курьер*","привезл*","привезут");
        concept("зарплата", "зарплат*","зп","оклад*","жаловань*");
        concept("интернет", "интернет*","сеть","wifi","вайфай","вай-фай");
        concept("книга", "книга","книги","книгу","книг","книжка","книжку","роман","романа");
        concept("страх", "страшно","боюсь","боял*","опасаюсь","страх*","испуг*","напуг*");
        concept("радость", "рад*","счастлив*","радост*","доволен","довольна","ура","круто");
        concept("грусть", "груст*","печаль*","тоска","тоску*","расстроен*","плакал*","плакала");
        concept("помощь", "помоги","помочь","помощ*","выруч*","поддержк*","подсказ*");
        concept("перевод", "перевел*","перевёл*","перевод*","отправилденьги");
        concept("кот", "кот","кота","коту","котик*","кошка","кошку","кошк*","котен*","котён*");
        concept("собака", "собак*","пес","пёс","пса","псу","щен*","питомец","питомца");
        // v0.49: Additional curated lexical groups across ordinary, scientific and literary domains.
        // They remain deterministic; a broader SUBJECT relationship must stay opt-in below.
        concept("проблема", "проблем*","трудност*","неполадк*","загвоздк*","сложност*","препятстви*");
        concept("ошибка", "ошиб*","опечатк*","баг","баги","сбой","сбоев","неисправност*");
        concept("решение", "решени*","решил*","разрешени*","разобрался","исправил*");
        concept("вопрос", "вопрос*","спросил*","спросить","вопроша*","question");
        concept("план", "план","планы","плана","планир*","намерева*","собираюсь");
        concept("цель", "цель","цели","целью","задач*","намерени*");
        concept("срок", "срок","сроки","дедлайн*","deadline","крайнийсрок");
        concept("начало", "начал*","начин*","старт","старта","приступил*");
        concept("завершение", "заверш*","закончил*","окончил*","финиш","финишир*");
        concept("перенос", "перенес*","перенёс*","перенос*","переезд","миграци*");
        concept("сохранение", "сохран*","записал*","записать","сбереж*","сейв","save");
        concept("удаление", "удал*","стёр*","стер*","вычеркнул*","убрал*");
        concept("поиск", "поиск*","искал*","ищу","разыскива*","разыскал*");
        concept("переписка", "переписк*","чат","чата","чате","диалог*","conversation");
        concept("ответ", "ответ*","ответил*","ответишь","реплай","reply");
        concept("согласие", "согласен","согласна","согласил*","одобря*","подтвержда*");
        concept("отказ", "отказ*","отклонил*","нехочу","возража*");
        concept("предложение", "предлага*","предложил*","предложени*","инициатив*");
        concept("выбор", "выбор*","выбира*","выбрал*","предпочёл*","предпочел*");
        concept("заказ", "заказ*","заказал*","оформилзаказ","заказыва*");
        concept("доставка-посылка", "посылк*","бандерол*","отправлени*","трекномер","трек-номер");
        concept("оплата", "оплат*","заплатил*","заплатить","платёж*","платеж*","payment");
        concept("скидка", "скидк*","уценк*","акци*","промокод*","discount");
        concept("цена", "цен*","стоимост*","прайс","прайса","расценк*");
        concept("бесплатно", "бесплатн*","даром","безвозмездн*","free");
        concept("приветствие", "привет*","здравствуй*","добрыйдень","салют","hello","hi");
        concept("прощание", "пока","досвидания","прощай*","увидимся","бай","bye");
        concept("обещание", "обеща*","клянусь","обязуюсь","гарантирую");
        concept("договор", "договор*","контракт*","соглашени*","сделк*");
        concept("суд", "суд","суда","судебн*","разбирательств*","тяжб*");
        concept("адвокат", "адвокат*","юрист*","защитник*","правовед*");
        concept("закон", "закон*","правил*","норматив*","законодательств*");
        concept("транспорт-билет", "билет*","проездн*","электронныйбилет","ticket");
        concept("самолёт", "самолет*","самолёт*","авиарейс*","рейс*","аэроплан*");
        concept("поезд", "поезд*","электричк*","состав","состава","вагон*");
        concept("автобус", "автобус*","маршрутк*","микроавтобус*");
        concept("дорога", "дорог*","трасс*","магистрал*","шоссе");
        concept("адрес", "адрес*","местоположени*","геопозици*","локаци*");
        concept("время", "время","времени","часов","времечко","time");
        concept("вчера", "вчера","вчерашн*","накануне");
        concept("завтра", "завтра","завтрашн*","следующийдень");
        concept("литература", "литератур*","словесност*","художественнаяпроза");
        concept("поэзия", "поэзи*","стих*","стихотворени*","лирик*");
        concept("писатель", "писател*","автор","автора","автором","литератор*");
        concept("сюжет", "сюжет*","фабул*","повествовани*","истори*");
        concept("персонаж", "персонаж*","герой","героя","героин*","действующеелицо");
        concept("театр", "театр*","спектакл*","постановк*","сценическ*");
        concept("фильм", "фильм*","кино","кинолент*","кинофильм*");
        concept("живопись", "живопис*","картин*","полотно","полотна","холст","холста");
        concept("рисунок", "рисунк*","рисовал*","нарисова*","эскиз*","наброс*");
        concept("язык", "язык","языка","языки","лингвистик*","language");
        concept("перевести", "перевести","переводить","перевел*","перевёл*","переведён*","translated");
        concept("программирование", "программировани*","кодинг","coding","разработк*","программист*");
        concept("приложение", "приложени*","приложуха","апп","app","программа","программу");
        concept("сервер", "сервер*","хостинг*","хост","backend","бэкенд*");
        concept("база-данных", "базаданных","бд","database","sqlite","субд","хранилищ*");
        concept("искусственный-интеллект", "нейросет*","нейронк*","ии","ai","искусственныйинтеллект","llm");
        concept("робот", "робот*","бот","бота","боту","ботик*");
        concept("алгоритм", "алгоритм*","алгоритмическ*");
        concept("тестирование", "тестировани*","тестир*","проверя*","тесты","qa");
        concept("безопасность", "безопасност*","защит*","секьюрит*","security");
        concept("шифрование", "шифров*","зашифров*","encryption");
        concept("аккаунт", "аккаунт*","учётказапись","учетказапись","профиль","профиля","account");
        concept("пароль", "парол*","пассворд*","password","пинкод*","пин-код*");
        concept("восстановление", "восстанов*","рестор*","restore","реанимир*","вернутьданные");
        concept("копия", "копия","копию","копии","бэкап*","беккап*","backup","резервнаякопия");
        concept("экран", "экран*","диспле*","монитор*","screen");
        concept("память", "память","памяти","оперативк*","озу","ram");
        concept("зарядка", "заряд*","батаре*","аккумулятор*","акб");
        concept("здоровье", "здоров*","самочувстви*","благополуч*");
        concept("лекарство", "лекарств*","медикамент*","таблетк*","препарат*");
        concept("больница", "больниц*","госпитал*","клиник*","стационар*");
        concept("исследование", "исследовани*","изуча*","анализир*","эксперимент*");
        concept("наука", "наук*","научн*","исследовательск*");
        concept("энергия", "энерги*","энергетическ*");
        concept("скорость", "скорост*","быстрот*","быстрее","ускор*","speed");
        concept("качество", "качеств*","добротн*","надёжност*","надежност*");
        concept("спрос", "спрос","спроса","потребност*","востребованност*");
        concept("экономия", "эконом*","сбережени*","бережлив*");
        concept("инвестиция", "инвестиц*","вложени*","инвестировани*");
        concept("прибыль", "прибыл*","профит","прибылью");
        concept("расходы", "расход*","затрат*","издержк*","трата","траты");
        concept("спорт", "спорт*","тренировк*","фитнес","упражнени*");
        concept("бег", "бег","бега","бегал*","бегу","пробежк*");
        concept("сон", "сон","сна","сном","спал*","сплю","заснул*");
        concept("усталость", "устал*","утомил*","изнеможен*","вымотал*");
        concept("ссора", "ссор*","поругал*","поссорил*","конфликт*");
        concept("мириться", "помирил*","мириться","примирени*","попросилпрощения");
        concept("сомнение", "сомнева*","неуверен*","сомнени*","колеба*");
        concept("удивление", "удивил*","удивлен*","удивлён*","поразил*","неожиданн*");
        // Opt-in only: category relations are not synonyms and never prove intent.
        // Wide topic associations are explicitly opt-in: never equate revenue with profit,
        // a clinical visit with illness, or a tool with the intent to purchase it.
        related("финансы", "прибыл*","выручк*","доход*","расход*","затрат*","инвестиц*","эконом*","оплат*");
        related("литературные-жанры", "литератур*","поэзи*","проз*","рассказ*","стих*","роман*","фантастик*","детектив*");
        related("технологии", "программ*","сервер*","компьютер*","смартфон*","алгоритм*","бд","нейросет*","ии");
        related("здоровье-тема", "здоров*","болезн*","лекарств*","врач*","больниц*","самочувстви*");
        related("путешествия-тема", "путешеств*","отпуск*","поезд*","самолёт*","самолет*","гостиниц*","билет*");
        related("экзамены-тема", "экзамен*","учеб*","студент*","курс*","сессия","зачёт*","зачет*");
        related("техника-тема", "устройств*","телефон*","компьютер*","ноутбук*","машин*","робот*","гаджет*");
        related("меч", "меч","меча","мечом","мечу","мечи","мечей","катана","катану","катаны","катаной","сабля","саблю","клинок","клинка");
        related("оружие", "оружи*","меч","меча","катана","катану","лук","арбалет","клинок");
        related("транспорт", "транспорт*","автомобил*","машина","машину","тачка","тачку","мотоцикл*","велосипед*");
    }
    // Separate thematic categories: a katana is a type of sword, not a synonym
    // for the act of purchasing. Their hits need explicit human verification.
    private static void concept(String label,String... words){
        Concept c=new Concept(label,words);CONCEPTS.add(c);
        for(Variant v:c.variants)if(!v.prefix)DICTIONARY.putIfAbsent(v.text,c);
    }
    private static void related(String label,String... words){
        Concept c=new Concept(label,words);RELATED.put(label,c);
    }
    private static Concept lookup(String token){
        Concept exact=DICTIONARY.get(token);if(exact!=null)return exact;
        for(Concept c:CONCEPTS)for(Variant v:c.variants)if(v.prefix&&token.startsWith(v.text))return c;
        return null;
    }
    private static Concept relatedLookup(String token){
        for(Concept c:RELATED.values())if(c.contains(token))return c;
        return null;
    }
    static final class Plan {
        final String fts,original,description;
        final List<String> terms;
        final List<String> searchTerms;
        final boolean expanded,broader,fuzzy;
        private final List<List<Variant>> alternatives;
        private final Map<String,List<String>> typoSuggestions;
        Plan(String expression,String original,String description,List<String> terms,List<String> variants,
            List<List<Variant>> alternatives,boolean expanded,boolean broader,Map<String,List<String>> typos){
            fts=expression;this.original=original;this.description=description;
            this.terms=Collections.unmodifiableList(new ArrayList<>(terms));
            this.searchTerms=Collections.unmodifiableList(new ArrayList<>(variants));
            this.alternatives=alternatives;this.expanded=expanded;this.broader=broader;
            LinkedHashMap<String,List<String>> safe=new LinkedHashMap<>();
            for(Map.Entry<String,List<String>> e:typos.entrySet())
                safe.put(e.getKey(),Collections.unmodifiableList(new ArrayList<>(e.getValue())));
            this.typoSuggestions=Collections.unmodifiableMap(safe);this.fuzzy=!safe.isEmpty();
        }
        /** Explains EACH required term. This is evidence, not an AI assertion. */
        String sourceMatch(String body){
            if(body==null||body.isEmpty())return "Совпадение в индексе (текст пуст)";
            String normalized=normalize(body);
            List<String> words=Arrays.asList(normalized.split("[^\\p{L}\\p{N}]+"));
            ArrayList<String> reports=new ArrayList<>();
            for(int i=0;i<terms.size();i++){
                String exact=terms.get(i),found=null;
                boolean exactMatch=words.contains(exact);
                for(Variant variant:alternatives.get(i)){
                    for(String word:words){
                        if(variant.prefix?word.startsWith(normalize(variant.text)):word.equals(normalize(variant.text))){
                            found=word;break;
                        }
                    }
                    if(found!=null)break;
                }
                if(found==null)reports.add(exact+" → индекс (проверь)");
                else reports.add(exact+" → "+found+(exactMatch?" (точно)":
                    typoSuggestions.containsKey(exact)&&typoSuggestions.get(exact).contains(found)?" (исправление опечатки)":" (вариант)"));
            }
            String negation=(normalized.contains("без покуп")||normalized.contains("не купил")||
                normalized.contains("не покуп")||normalized.contains("не приобр")||normalized.contains("не благодар"))
                ?"Внимание: возможно отрицание действия · ":"";
            return (broader?"Широкая тема, проверь контекст · ":"")+negation+String.join("; ",reports);
        }
    }
    static Plan make(String input,boolean synonyms){return make(input,synonyms,false);}
    static Plan make(String input,boolean synonyms,boolean allowRelated){
        return make(input,synonyms,allowRelated,Collections.<String,List<String>>emptyMap());
    }
    /** Typos must be obtained from indexed corpus words, never from generated guesses. */
    static Plan make(String input,boolean synonyms,boolean allowRelated,Map<String,List<String>> typos){
        if(input==null||input.trim().isEmpty())throw new IllegalArgumentException("Введите запрос");
        if(input.codePointCount(0,input.length())>MAX_CHARS)throw new IllegalArgumentException("Запрос длиннее 140 символов");
        String[] raw=normalize(input).split("[^\\p{L}\\p{N}]+");
        String[] initial=input.split("[^\\p{L}\\p{N}]+");
        boolean firstPerson=Arrays.asList(raw).contains("я");
        ArrayList<String> tokens=new ArrayList<>();
        Map<String,Boolean> capitalized=new HashMap<>();
        for(int i=0;i<raw.length;i++){
            String t=raw[i];
            if(t.length()<2||tokens.contains(t))continue;
            tokens.add(t);
            String orig=i<initial.length?initial[i]:t;
            capitalized.put(t,orig.length()>1&&Character.isUpperCase(orig.charAt(0))&&
                !orig.equals(orig.toUpperCase(Locale.ROOT)));
        }
        if(tokens.size()>1){
            ArrayList<String> cleaned=new ArrayList<>();
            for(String t:tokens)if(!QUESTIONS.contains(t))cleaned.add(t);
            if(!cleaned.isEmpty())tokens=cleaned;
        }
        if(tokens.isEmpty())throw new IllegalArgumentException("Не найдено слов длиннее одного символа");
        if(tokens.size()>MAX_TERMS)throw new IllegalArgumentException("Слишком много слов: максимум 7");
        ArrayList<String> blocks=new ArrayList<>(),variants=new ArrayList<>();
        ArrayList<List<Variant>> explanations=new ArrayList<>();
        boolean expanded=false,broader=false;
        LinkedHashMap<String,List<String>> addedTypos=new LinkedHashMap<>();
        for(String token:tokens){
            LinkedHashSet<String> forms=new LinkedHashSet<>();forms.add(token);
            Concept concept=synonyms?lookup(token):null;
            if(concept!=null){for(Variant v:concept.variants)forms.add(v.text+(v.prefix?"*":""));}
            // Unknown capitalized names may be written in declined form, e.g.
            // Никиту ↔ Никита. Conservative endings, exact words only.
            if(synonyms&&concept==null&&Boolean.TRUE.equals(capitalized.get(token))){
                if(token.endsWith("иту")&&token.length()>=5){
                    String stem=token.substring(0,token.length()-1);forms.add(stem+"а");forms.add(stem+"е");
                }else if(token.endsWith("ита")&&token.length()>=5){
                    String stem=token.substring(0,token.length()-1);forms.add(stem+"у");forms.add(stem+"е");
                }else if(token.endsWith("шу")&&token.length()>=4){forms.add(token.substring(0,token.length()-1)+"а");}
                else if(token.endsWith("ша")&&token.length()>=4){forms.add(token.substring(0,token.length()-1)+"у");}
            }
            if(synonyms&&allowRelated){
                Concept related=relatedLookup(token);
                if(related!=null){broader=true;for(Variant v:related.variants)forms.add(v.text+(v.prefix?"*":""));}
            }
            if(typos!=null&&typos.containsKey(token)){
                ArrayList<String> actual=new ArrayList<>();
                for(String typo:typos.get(token)){
                    String w=FuzzyWordPolicy.normalized(typo);
                    if(!FuzzyWordPolicy.near(token,w)||actual.contains(w))continue;
                    if(forms.size()>=MAX_ALTERNATIVES)break;
                    actual.add(w);forms.add(w);
                }
                if(!actual.isEmpty())addedTypos.put(token,actual);
            }
            // Single unfamiliar words retain the existing FTS prefix behavior.
            // Known short abbreviations (e.g. комп) are handled by exact lexicon forms.
            if(forms.size()==1&&token.length()>=4){forms.clear();forms.add(token+"*");}
            ArrayList<Variant> options=new ArrayList<>();
            for(String candidate:forms){
                if(options.size()>=MAX_ALTERNATIVES)break;
                Variant v=new Variant(candidate);
                // Reject short unsafe wildcard roots and all FTS operators.
                if(v.text.length()<2||!v.text.matches("[\\p{L}\\p{N}]+")||v.prefix&&v.text.length()<4)
                    continue;
                options.add(v);
                if(!variants.contains(v.text))variants.add(v.text);
            }
            if(options.isEmpty())throw new IllegalArgumentException("Пустой или недопустимый запрос");
            if(options.size()>1)expanded=true;
            ArrayList<String> expr=new ArrayList<>();
            for(Variant v:options)expr.add(v.fts());
            blocks.add(expr.size()==1?expr.get(0):"("+String.join(" OR ",expr)+")");
            explanations.add(Collections.unmodifiableList(options));
        }
        String description=broader?"Синонимы + тематически близкие слова (не доказательство события)":
            expanded?"Локальные варианты слов":"Точные слова";
        if(!addedTypos.isEmpty())description+=". Исправление опечаток: по словам в индексе, проверь точность";
        if(firstPerson)description+=". «Я» не определяет автора: выбери его в отдельном фильтре";
        return new Plan(String.join(" AND ",blocks),normalize(input),description,tokens,variants,
            Collections.unmodifiableList(explanations),expanded,broader,addedTypos);
    }
    private static String normalize(String s){return s.toLowerCase(Locale.ROOT).replace('ё','е').trim();}
    private OfflineSynonymSearch(){}
}
