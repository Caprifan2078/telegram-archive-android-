package org.openarchive.telegram;
import java.io.File;
import java.util.Locale;

/** A fixed, digest-pinned catalog; no arbitrary network URL or remote metadata. */
final class AnalystModelCatalog {
    static final class Model {
        final String key,label,description,url,sha;
        final long size;
        Model(String key,String label,String description,String url,long size,String sha){
            this.key=key;this.label=label;this.description=description;this.url=url;this.size=size;this.sha=sha;
        }
        File file(File dir){return new File(dir,"analyst-"+key+".litertlm");}
        String display(){return label+" · "+String.format(Locale.ROOT,"%.2f ГБ",size/1e9)+" · "+description;}
    }
    static final Model[] MODELS={
        new Model("qwen06","Qwen3-0.6B","Лёгкая",
            AnalystModelDownload.URL,AnalystModelDownload.SIZE,AnalystModelDownload.SHA),
        new Model("qwen17","Qwen3-1.7B","Более глубокий анализ",
            "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/4b46d76eccd6033af5032ebe2f186e3377dd1d10/Qwen3_1.7B.litertlm",
            2056729520L,"66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c"),
        new Model("gemma4community","Gemma 4 E2B Uncensored","Сторонняя экспериментальная версия; совместимость не подтверждена",
            "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm/resolve/5e28da253d149f19bc78dad2dec5d91f8c799756/gemma4_uncensored_INT4_8192.litertlm",
            2570459088L,"7c1dd5e4723e8a6d20fbb5f11f34141eb1f8e413d2049aa52211f533966d785e")
    };
    static final int CUSTOM=MODELS.length;
    static File file(File dir,int index){return index>=0&&index<MODELS.length?MODELS[index].file(dir):new File(dir,"analyst.litertlm");}
    static int safeIndex(int index){return index>=0&&index<=CUSTOM?index:0;}
    private AnalystModelCatalog(){}
}
