package org.openarchive.telegram;

import java.util.Locale;
/** Stable offline media group labels. Never guesses a month for an invalid timestamp. */
final class MediaTimeline {
    static String month(String timestamp){
        ChatTrends.Moment parsed=ChatTrends.parse(timestamp);
        if(parsed==null)return "Дата не указана";
        return String.format(Locale.ROOT,"%04d-%02d",parsed.day.getYear(),parsed.day.getMonthValue());
    }
}
