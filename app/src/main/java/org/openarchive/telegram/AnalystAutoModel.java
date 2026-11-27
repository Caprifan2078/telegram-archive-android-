package org.openarchive.telegram;

import java.io.File;

/** Product routing by archive size, not a claim about model token capacity.
 * Small models failed semantic attribution QA; never silently substitute one
 * for a verified model on a large archive, or select a RAM-incompatible model. */
final class AnalystAutoModel {
    static final int SMALL=8,MEDIUM=0,LARGE=10,NONE=-1;
    static final long SMALL_MAX=10_000,MEDIUM_MAX=100_000;
    static int sizeCandidate(long chars){
        if(chars<0)return LARGE;
        if(chars<=SMALL_MAX)return SMALL;
        return chars<=MEDIUM_MAX?MEDIUM:LARGE;
    }
    static int choose(long chars,boolean allowExperimental,boolean deep,AnalystDeviceFit fit,File dir){
        int preferred=deep||!allowExperimental?LARGE:sizeCandidate(chars);
        if(fit.fits(preferred,AnalystModelCatalog.file(dir,preferred)))return preferred;
        // Do not downgrade large/complex chats to an unreliable model.
        if(preferred!=LARGE&&fit.fits(LARGE,AnalystModelCatalog.file(dir,LARGE)))return LARGE;
        return NONE;
    }
    static String range(int i){
        if(i==SMALL)return "Небольшие чаты · до 10 000 знаков";
        if(i==MEDIUM)return "Средние чаты · до 100 000 знаков";
        if(i==LARGE)return "Большие чаты · от 100 000 знаков и сложный анализ";
        if(i==AnalystModelCatalog.CUSTOM)return "Объём неизвестен";
        return "Ручной выбор · подходящий объём ещё не проверен";
    }
    static String label(int index){
        return index==NONE?"Поиск без ИИ":AnalystModelCatalog.MODELS[index].label;
    }
    private AnalystAutoModel(){}
}
