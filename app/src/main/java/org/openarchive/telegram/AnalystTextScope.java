package org.openarchive.telegram;
import java.util.Locale;
/** These are application evidence limits, not the model vendor's maximum context.
 * Search works across archives; a single generation only sees the selected text. */
final class AnalystTextScope {
    static int evidenceCharacters(int index){
        if(index==AnalystModelCatalog.CUSTOM)return 0;
        return AnalystModelCatalog.shortContext(index)?560:5120;
    }
    static String size(long bytes){
        return bytes>=1000000000L?String.format(Locale.forLanguageTag("ru-RU"),"%.2f ГБ",bytes/1e9):
            String.format(Locale.forLanguageTag("ru-RU"),"%.0f МБ",bytes/1e6);
    }
    static String number(long n){return java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")).format(n);}
    static String label(int index,long chars){
        int limit=evidenceCharacters(index);
        if(limit==0)return "Объём текста неизвестен";
        String capacity="До "+number(limit)+" знаков из сообщений за ответ";
        if(chars<0)return capacity;
        if(chars<=limit)return capacity+" · небольшой архив";
        return capacity+" · большой архив: поиск по частям";
    }
    static String quality(int index){
        if(index==10)return "Рекомендуется · проверена на текстовых вопросах";
        if(index==0||index==8)return "Быстрая, экспериментальная · может путать авторов";
        if(index==AnalystModelCatalog.CUSTOM)return "Совместимость неизвестна";
        return "Экспериментальная · качество не подтверждено";
    }
    private AnalystTextScope(){}
}
