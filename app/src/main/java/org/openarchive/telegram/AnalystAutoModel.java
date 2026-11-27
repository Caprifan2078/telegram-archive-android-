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
    /** Full passes may use an explicitly permitted weak model regardless of total
     * archive length: the scanner subdivides the input to its real context budget. */
    static int chooseForFull(long chars,boolean experimental,AnalystDeviceFit fit,File dir){
        int candidate=choose(chars,experimental,false,fit,dir);
        if(candidate!=NONE||!experimental)return candidate;
        for(boolean installed:new boolean[]{true,false})for(int i:new int[]{MEDIUM,SMALL}){
            File model=AnalystModelCatalog.file(dir,i);
            if(fit.fits(i,model)&&(!installed||AnalystModelCatalog.installed(model,i)))return i;
        }
        // Explicit experimental full-scan consent also permits installed
        // catalog alternatives, never an automatic unknown custom file.
        for(int i:AnalystModelCatalog.pickerOrder()){
            if(i==AnalystModelCatalog.CUSTOM)continue;
            File file=AnalystModelCatalog.file(dir,i);
            if(AnalystModelCatalog.installed(file,i)&&fit.fits(i,file))return i;
        }
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
