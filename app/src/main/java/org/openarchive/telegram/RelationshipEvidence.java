package org.openarchive.telegram;

import java.util.*;
import java.util.regex.Pattern;

/** Topic cues for finding messages, never a claim that an event occurred. */
final class RelationshipEvidence {
    private static final String[][] TOPICS={
        {"любов|романтик|нежност|признани|влюбл|сблиз", "люблю","скучаю","обнимаю","целую"},
        {"дружб|дружеск|общие интерес", "дружба","друг","подруга","вместе"},
        {"забот|поддерж|помощ|помог|трудн|пережив", "помоги","помогу","спасибо","держись"},
        {"ссор|конфликт|примир|извин|ошиб", "прости","извини","поругались","помиримся"},
        {"границ|отказ|давлен|ультимат|уваж", "отказ","нехочу","запрещаю","давление"},
        {"обещан|обязатель|договор|довер|выполн", "обещаю","договорились","сделаю","выполнил"},
        {"ревност|ревну", "ревную","ревность","ревновал","ревновала"},
        {"встреч|созвон|инициатив|внимани", "встреча","созвон","привет","позвони"},
        {"благодар|достижен|ободря", "спасибо","благодарю","горжусь","молодец"}
    };
    static List<String> cues(String question){
        String q=question==null?"":question.toLowerCase(Locale.ROOT).replace('ё','е');
        LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String[] topic:TOPICS)if(Pattern.compile(topic[0]).matcher(q).find())
            for(int i=1;i<topic.length&&out.size()<8;i++)out.add(topic[i]);
        return new ArrayList<>(out);
    }
    private RelationshipEvidence(){}
}
