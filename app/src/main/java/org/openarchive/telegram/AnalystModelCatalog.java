package org.openarchive.telegram;
import java.io.File;
import java.util.Locale;

/** A fixed, digest-pinned catalog; no arbitrary network URL or remote metadata. */
final class AnalystModelCatalog {
    static final class Model {
        final String key,label,description,url,sha;
        final long size;
        boolean modified(){return key.equals("gemma4community")||key.equals("huihui352")||key.equals("gemma4skillsafe");}
        boolean legacy(){return key.equals("qwen06")||key.equals("qwen17")||key.equals("qwen06q4")||key.equals("qwen2515");}
        boolean shortContext(){return key.equals("qwen06q4");}
        boolean reserve(){return key.equals("qwen2515");}
        long suggestedFreeBytes(){return requiredFreeBytes(size,legacy());}
        Model(String key,String label,String description,String url,long size,String sha){
            this.key=key;this.label=label;this.description=description;this.url=url;this.size=size;this.sha=sha;
        }
        File file(File dir){return new File(dir,"analyst-"+key+".litertlm");}
        String display(){return label+" · "+String.format(Locale.ROOT,"%.2f ГБ",size/1e9)+" · "+description;}
    }
    static final Model[] MODELS={
        new Model("qwen06","Qwen3-0.6B","Стандартная · 2 теста пройдены · 43/82 с на эмуляторе",
            AnalystModelDownload.URL,AnalystModelDownload.SIZE,AnalystModelDownload.SHA),
        new Model("qwen17","Qwen3-1.7B","Экспериментальная · сбой памяти на эмуляторе",
            "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/4b46d76eccd6033af5032ebe2f186e3377dd1d10/Qwen3_1.7B.litertlm",
            2056729520L,"66064a4e9269cb693e124c4e3040bcb8a446b10bca42663896329495add3861c"),
        new Model("gemma4community","Gemma 4 E2B Uncensored","Сторонняя экспериментальная версия; совместимость не подтверждена",
            "https://huggingface.co/PeppX/gemma-4-e2b-uncensored-litertlm/resolve/5e28da253d149f19bc78dad2dec5d91f8c799756/gemma4_uncensored_INT4_8192.litertlm",
            2570459088L,"7c1dd5e4723e8a6d20fbb5f11f34141eb1f8e413d2049aa52211f533966d785e"),
        new Model("qwen3508","Qwen3.5 0.8B INT8","Компактный файл · сбой памяти на эмуляторе",
            "https://huggingface.co/litert-community/Qwen3.5-0.8B/resolve/ff11c109e8e56797cd1ee5befa856975bb465afc/Qwen3.5-0.8B_int8.litertlm",
            964566992L,"25447e2550082aed6145bbc755ec4da5d535f98f6a0c386865fdae2ccb88aed1"),
        new Model("qwen352","Qwen3.5 2B INT8","Новый глубокий анализ · экспериментальная",
            "https://huggingface.co/litert-community/Qwen3.5-2B/resolve/895dd827bca539c532b359d9fec3b4fe512f8426/Qwen3.5-2B_int8.litertlm",
            2119744416L,"fa4ac3259404567c7f82533746549eed94e2e1c8222095706c63cce17653c68a"),
        new Model("lfm2526","LFM2.5 2.6B INT4","Компактная альтернатива · русский язык требует проверки",
            "https://huggingface.co/litert-community/LFM2.5-2.6B/resolve/74de0c552676734655e3d282070ffc6ea78cda5d/LFM2.5-2.6B_int4.litertlm",
            1668151680L,"f4797d5a16812231deb6d505b4f417dd835b9b3dc47bb74d9ec71facfd0c91a4"),
        new Model("huihui352","Huihui Qwen3.5 2B Abliterated","Ослабленные ограничения · 2 сбоя памяти на эмуляторе",
            "https://huggingface.co/geladons/Huihui-Qwen3.5-2B-abliterated/resolve/22f7b5edefb2251f1d189f02f4fe668b06066d25/Huihui-Qwen3.5-2B-abliterated_int8_v2.litertlm",
            2116592816L,"3d51c41abe591db12a497dbc4fcfd8ec17f474ef1a5fa8e16814e9cce6e9b873"),
        new Model("gemma4skillsafe","Gemma 4 E2B Community","Сторонняя модификация Skillsafe · экспериментальная",
            "https://huggingface.co/skillsafe-ai/Gemma-4-E2B-Uncensored/resolve/97fb035680f1af0c39789bb48b45e0fe380912ce/model.litertlm",
            2008432640L,"a6132d4e449c56de2b33bc0a12d8508b87f5c62f870ab8ec574e6767f92f7c7b"),
        new Model("qwen06q4","Qwen3 0.6B INT4","Быстрая · 2 теста пройдены · 21/16 с на эмуляторе · короткий контекст",
            "https://huggingface.co/litert-community/Qwen3-0.6B-int4/resolve/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm",347251840L,"2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139"),
        new Model("qwen2515","Qwen2.5 1.5B Instruct","Резерв для подробных ответов · не испытана",
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",1597931520L,"faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9")
    };
    static final int CUSTOM=MODELS.length;
    static File file(File dir,int index){return index>=0&&index<MODELS.length?MODELS[index].file(dir):new File(dir,"analyst.litertlm");}
    static int safeIndex(int index){return index>=0&&index<=CUSTOM?index:0;}
    static final String CUSTOM_KEY="custom";
    // Stable indices are storage identities, not presentation order.
    static int[] pickerOrder(){return new int[]{0,8,9,3,1,4,5,2,6,7,CUSTOM};}
    // Keep the old stable catalog IDs for upgrades, but do not advertise eight downloads.
    static boolean recommended(int index){return index==0||index==8;}
    static int[] focusedPickerOrder(File directory,int selected){
        java.util.ArrayList<Integer> items=new java.util.ArrayList<>();
        for(int i:new int[]{8,0})items.add(i);
        for(int i:pickerOrder()){
            if(i==CUSTOM||recommended(i))continue;
            if(i==selected||file(directory,i).exists())items.add(i);
        }
        items.add(CUSTOM);
        return items.stream().mapToInt(Integer::intValue).toArray();
    }
    static int[] filteredPickerOrder(File dir,int selected,int mode,AnalystDeviceFit fit){
        if(mode==0)return focusedPickerOrder(dir,selected);
        if(mode==3)return pickerOrder();
        java.util.ArrayList<Integer> out=new java.util.ArrayList<>();
        for(int i:pickerOrder()){
            if(mode==1&&fit.fits(i,file(dir,i)))out.add(i);
            if(mode==2&&i<CUSTOM&&MODELS[i].modified())out.add(i);
            if(mode==4&&i<CUSTOM&&MODELS[i].reserve())out.add(i);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }
    static String focusedGroup(int index){
        if(index==CUSTOM)return "Дополнительно";
        if(index==8||index==0)return "Проверенные на двух вопросах";
        return "Ранее выбранные или скачанные";
    }
    static String group(int index){
        if(index==CUSTOM)return "Локальный файл";
        if(MODELS[index].reserve())return "Резервные кандидаты";
        if(MODELS[index].modified())return "Сторонние модификации";
        return index==0||index==3?"Компактные":"Основные альтернативы";
    }
    static String key(int index){return index>=0&&index<CUSTOM?MODELS[index].key:CUSTOM_KEY;}
    static int initialSelection(String storedKey,int legacyIndex,boolean customExists){
        if(storedKey!=null){
            if(CUSTOM_KEY.equals(storedKey))return CUSTOM;
            for(int i=0;i<CUSTOM;i++)if(MODELS[i].key.equals(storedKey))return i;
            return 0;
        }
        // The old catalog had exactly 3 entries. Its index 3 meant custom.
        if(legacyIndex==3||legacyIndex==-1&&customExists)return CUSTOM;
        return legacyIndex>=0&&legacyIndex<3?legacyIndex:0;
    }
    static boolean installed(File file,int index){
        return file.isFile()&&file.length()>0&&(index==CUSTOM||file.length()==MODELS[safeIndex(index)].size);
    }
    static boolean shortContext(int index){return index>=0&&index<CUSTOM&&MODELS[index].shortContext();}
    static boolean canReuseEngine(String path){
        String name=new File(path).getName();
        return name.equals("analyst-qwen06.litertlm")||name.equals("analyst-qwen17.litertlm");
    }
    // Conservative start threshold, not a measurement or a guarantee against native OOM.
    static long requiredFreeBytes(long size,boolean legacy){
        // 5 GiB Android emulator killed both hybrid candidates and the larger legacy model.
        // Conservative preflight estimate, not a measured memory requirement.
        long reserve=(legacy?(size>=1500000000L?2048L:768L):3072L)*1024*1024;
        return size<0||size>Long.MAX_VALUE-reserve?Long.MAX_VALUE:size+reserve;
    }
    private AnalystModelCatalog(){}
}
