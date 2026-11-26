package org.openarchive.telegram;
import java.io.File;
import java.util.Locale;

/** Advisory preflight based on live Android available RAM, not a promise of inference success. */
final class AnalystDeviceFit {
    final long available,total;
    final boolean low;
    AnalystDeviceFit(long available,long total,boolean low){
        this.available=Math.max(0,available);this.total=Math.max(0,total);this.low=low;
    }
    long required(int index,File file){
        if(index==AnalystModelCatalog.CUSTOM)
            return file.isFile()?AnalystModelCatalog.requiredFreeBytes(file.length(),false):Long.MAX_VALUE;
        return AnalystModelCatalog.MODELS[index].suggestedFreeBytes();
    }
    boolean fits(int index,File file){return !low&&available>=required(index,file)&&required(index,file)!=Long.MAX_VALUE;}
    boolean offerLightweight(File dir,int selected){
        return selected!=0&&fits(0,AnalystModelCatalog.file(dir,0));
    }
    String summary(){return String.format(Locale.ROOT,"Доступно %.1f из %.1f ГБ ОЗУ%s",available/1e9,total/1e9,low?" · давление памяти":"");}
    String label(int index,File file){
        long need=required(index,file);
        if(need==Long.MAX_VALUE)return "Нужен файл для оценки";
        return String.format(Locale.ROOT,"%s · оценка от %.1f ГБ",fits(index,file)?"По памяти подходит":"Сейчас не рекомендуется",need/1e9);
    }
}
