package org.openarchive.telegram;
import java.util.Locale;
/** Strict allowlist: never interpolate a user-supplied string except fixed version. */
final class DiagnosticReport {
    static String render(String build,int sdk,int memoryClassMb,int performanceMode,
                         boolean reduceMotion,boolean glass){
        return "Telegram Archive · обезличенная диагностика\n"
            +"Версия: "+build+"\n"
            +"Android API: "+Math.max(0,sdk)+"\n"
            +"Класс памяти приложения: "+Math.max(0,memoryClassMb)+" МБ\n"
            +"Режим производительности: "+Math.max(0,Math.min(2,performanceMode))+"\n"
            +"Уменьшение движения: "+(reduceMotion?"да":"нет")+"\n"
            +"Стеклянные карточки: "+(glass?"да":"нет")+"\n"
            +"Не содержит переписки, пути, имена участников, идентификаторы или содержимое медиа.\n";
    }
}
