package org.openarchive.telegram;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separate process: a native inference crash does not bring down the main Viewer. */
public final class DeepAnalystActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopped=new AtomicBoolean();
    private static final AtomicBoolean MODEL_BUSY=new AtomicBoolean();
    private String archive,compareArchive;
    private boolean relationships;
    private File model;
    private int selectedModel;
    private Button modelSelector;
    private LinearLayout modelCard;
    private ProgressBar progress;
    private boolean downloading;
    private static final String MODEL_PREFS="analyst_model_selection";
    private LinearLayout panel,results;
    private ScrollView pageScroll;
    private TextView status;
    private EditText question;
    private Button install,ask,remove,download,scan,stop,full,modelCheck;
    private final AtomicBoolean taskCancel=new AtomicBoolean();
    private ViewerPalette palette;
    private boolean busy;
    private boolean modelOptionsOpen, advancedOpen;
    private Button modelOptions,advanced,evidenceOnly;
    private TextView modelState;
    private LinearLayout modelMaintenance,advancedPanel;
    private int dp(int n){return ViewerUi.dp(this,n);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(palette.text);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    private Button button(String value){Button b=new Button(this);b.setText(value);ViewerUi.styleButton(b,palette.raised,palette.text,palette.light);return b;}
    private boolean runtimeAvailable(){try{Class.forName("org.openarchive.telegram.LocalLlmRuntime",false,getClassLoader());return true;}catch(ClassNotFoundException|LinkageError e){return false;}}
    @Override public void onCreate(Bundle state){
        ViewerUi.applyActivityTheme(this);super.onCreate(state);palette=ViewerUi.palette(this);ViewerUi.systemBars(this,palette);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        archive=getIntent().getStringExtra("archive");
        try{if(!getDatabasePath(DeepChatContext.dbName(archive)).isFile()){finish();return;}}catch(IllegalArgumentException bad){finish();return;}
        compareArchive=getIntent().getStringExtra("compare_archive");
        relationships=getIntent().getBooleanExtra("relationships",false)&&compareArchive==null;
        if(compareArchive!=null){
            try{if(archive.equals(compareArchive)||!getDatabasePath(DeepChatContext.dbName(compareArchive)).isFile()){finish();return;}}
            catch(IllegalArgumentException bad){finish();return;}
        }
        android.content.SharedPreferences prefs=getSharedPreferences(MODEL_PREFS,MODE_PRIVATE);
        selectedModel=AnalystModelCatalog.initialSelection(prefs.getString("selected_key",null),
            prefs.getInt("selected",-1),AnalystModelCatalog.file(getNoBackupFilesDir(),AnalystModelCatalog.CUSTOM).isFile());
        prefs.edit().putString("selected_key",AnalystModelCatalog.key(selectedModel)).apply();
        model=AnalystModelCatalog.file(getNoBackupFilesDir(),selectedModel);
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(16),dp(16),dp(24));panel.setBackgroundColor(palette.background);pageScroll.addView(panel);setContentView(pageScroll);
        TextView title=text(relationships?"Взаимоотношения":compareArchive==null?"Глубокий анализ":"Сравнение двух переписок",22);title.setTypeface(null,android.graphics.Typeface.BOLD);panel.addView(title);
        panel.addView(text(relationships?"Поступки, взаимность, забота и конфликты. Выводы только по сообщениям, не оценка личности.":compareArchive==null?"Локальный ИИ · быстрый вопрос или полный проход. Ответы могут ошибаться.":"Локальный ИИ · отдельные выборки А и Б. Сравнение по цитатам, не полный проход.",14));
        status=text("",13);panel.addView(status);
        modelCard=new LinearLayout(this);modelCard.setOrientation(LinearLayout.VERTICAL);
        modelCard.setPadding(dp(12),dp(10),dp(12),dp(12));
        modelCard.setBackground(ViewerUi.surface(this,palette.surface,ViewerUi.CARD,palette.light,false));
        modelCard.addView(text("Локальная модель",15));
        modelSelector=button("");modelSelector.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
        modelCard.addView(modelSelector);
        modelState=text("",12);modelState.setTextColor(palette.muted);modelCard.addView(modelState);
        download=button("Скачать модель");modelCard.addView(download);
        install=button("Импортировать модель");modelCard.addView(install);
        modelOptions=button("Управление моделью  ›");modelCard.addView(modelOptions);
        modelMaintenance=new LinearLayout(this);modelMaintenance.setOrientation(LinearLayout.VERTICAL);
        modelCard.addView(modelMaintenance);modelMaintenance.setVisibility(android.view.View.GONE);
        remove=button("Удалить выбранную модель");modelMaintenance.addView(remove);
        modelCheck=button("Проверить модель");modelMaintenance.addView(modelCheck);
        modelOptions.setOnClickListener(v->{modelOptionsOpen=!modelOptionsOpen;
            modelMaintenance.setVisibility(modelOptionsOpen?android.view.View.VISIBLE:android.view.View.GONE);
            modelOptions.setText(modelOptionsOpen?"Управление моделью  ▾":"Управление моделью  ›");});
        modelCheck.setOnClickListener(v->checkModel());
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(palette.accent));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.raised));
        progress.setVisibility(android.view.View.GONE);modelCard.addView(progress);
        question=new EditText(this);ViewerUi.styleInput(question,palette);question.setHint("Например, о чём договаривались насчёт встречи?");
        question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(360)});
        question.setText(getIntent().getStringExtra("question"));LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,-2);qlp.topMargin=dp(12);qlp.bottomMargin=dp(8);panel.addView(question,qlp);
        ask=button("Спросить");ViewerUi.styleButton(ask,palette.chip,palette.accent,palette.light);panel.addView(ask);
        Button presets=button(relationships?"80 вопросов об отношениях  ›":"Идеи вопросов  ›");panel.addView(presets);
        presets.setOnClickListener(v->showQuestionLibrary());
        evidenceOnly=button("Найти источники без ИИ");panel.addView(evidenceOnly);
        evidenceOnly.setOnClickListener(v->{String q=question.getText().toString().trim();if(!busy&&!q.isEmpty())findEvidence(q);});
        if(relationships){Button report=button("Сводка отношений · весь архив");panel.addView(report);report.setOnClickListener(v->{if(!busy)scanRelationships();});}
        question.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){updateAsk();}
            public void afterTextChanged(android.text.Editable s){}
        });
        stop=button("Отменить операцию");panel.addView(stop);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(dp(10),dp(8),dp(10),dp(8));panel.addView(results);
        panel.addView(modelCard);
        advanced=button("Дополнительный анализ  ›");panel.addView(advanced);
        advancedPanel=new LinearLayout(this);advancedPanel.setOrientation(LinearLayout.VERTICAL);panel.addView(advancedPanel);
        advancedPanel.setVisibility(android.view.View.GONE);
        scan=button("Полный разбор поведения · без ИИ");advancedPanel.addView(scan);
        full=button("Весь чат нейросетью · медленно");advancedPanel.addView(full);
        advanced.setOnClickListener(v->{advancedOpen=!advancedOpen;
            advancedPanel.setVisibility(advancedOpen?android.view.View.VISIBLE:android.view.View.GONE);
            advanced.setText(advancedOpen?"Дополнительный анализ  ▾":"Дополнительный анализ  ›");});
        if(compareArchive!=null)advanced.setVisibility(android.view.View.GONE);
        FontStyler.bind(this);
        modelSelector.setOnClickListener(v->showModelPicker());
        install.setOnClickListener(v->{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(picker,1);});
        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить только файл модели?").setMessage("Переписки останутся без изменений.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,n)->{if(!busy){if(model.exists()&&!model.delete())status.setText("Не удалось удалить модель");else refresh();}}).show());
        ask.setOnClickListener(v->analyze());
        scan.setOnClickListener(v->scanBehavior());
        full.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Полный проход нейросети?")
            .setMessage("Модель прочитает текст по блокам. Большой архив может занять часы, нагреть телефон и разрядить аккумулятор. Конспект сжимается, поэтому отдельные детали могут потеряться. Можно запросить отмену генерации; загрузка модели может завершаться не сразу. Для новых и пользовательских моделей каждый блок запускается в чистом движке: медленнее, но без переноса скрытого состояния.")
            .setNegativeButton("Отмена",null).setPositiveButton("Начать",(d,n)->scanWithModel()).show());
        stop.setOnClickListener(v->{taskCancel.set(true);stop.setEnabled(false);status.setText("Отмена запрошена…");});
        download.setOnClickListener(v->{
            if(selectedModel>=AnalystModelCatalog.CUSTOM)return;
            AnalystModelCatalog.Model spec=AnalystModelCatalog.MODELS[selectedModel];
            new FontDialog.Builder(this).setTitle("Скачать "+spec.label+"?")
                .setMessage(spec.display()+"\nЛучше по Wi-Fi. Публичный файл Hugging Face, проверка SHA-256. Переписка и вопросы не передаются. Совместимость с телефоном и отсутствие отказов модели не гарантируются.")
                .setNegativeButton("Отмена",null).setPositiveButton("Скачать",(d,n)->downloadModel(spec)).show();
        });
        refresh();
    }
    private void showQuestionLibrary(){
        if(busy)return;
        final String[][] semantic=relationships?RelationshipQuestions.GROUPS:compareArchive==null?AnalystQuestionLibrary.SINGLE:AnalystQuestionLibrary.DUAL;
        final String[][] exact=compareArchive==null&&!relationships?ChatQuestionAnalyst.PRESETS:new String[0][];
        final java.util.ArrayList<String[]> groups=new java.util.ArrayList<>();
        final java.util.ArrayList<String> labels=new java.util.ArrayList<>();
        for(String[] group:exact){groups.add(group);labels.add("Факты · "+group[0]+" ("+(group.length-1)+")");}
        for(String[] group:semantic){groups.add(group);labels.add("ИИ · "+group[0]+" ("+(group.length-1)+")");}
        new FontDialog.Builder(this).setTitle("Библиотека вопросов")
            .setItems(labels.toArray(new String[0]),(d,which)->{
                if(busy)return;
                String[] group=groups.get(which);
                String[] items=java.util.Arrays.copyOfRange(group,1,group.length);
                new FontDialog.Builder(this).setTitle(group[0]).setItems(items,(dialog,index)->{
                    if(busy)return;
                    String value=items[index];question.setText(value);
                    int term=value.indexOf("«встреча»");
                    if(term>=0){question.requestFocus();question.setSelection(term+1,term+8);}
                    else question.setSelection(question.length());
                }).setNegativeButton("Назад",(dialog,index)->showQuestionLibrary()).show();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void showModelPicker(){
        if(busy)return;
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(14),dp(6),dp(14),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(choices);
        android.app.AlertDialog picker=new FontDialog.Builder(this).setTitle("Выбор модели").setView(scroll).setNegativeButton("Закрыть",null).create();
        String group="";
        for(int i:AnalystModelCatalog.focusedPickerOrder(getNoBackupFilesDir(),selectedModel)){
            String nextGroup=AnalystModelCatalog.focusedGroup(i);
            if(!group.equals(nextGroup)){group=nextGroup;TextView heading=text(group,14);heading.setTypeface(null,android.graphics.Typeface.BOLD);choices.addView(heading);}
            final int index=i;boolean custom=i==AnalystModelCatalog.CUSTOM,selected=i==selectedModel;
            File file=AnalystModelCatalog.file(getNoBackupFilesDir(),i);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12),dp(10),dp(12),dp(10));
            row.setBackground(ViewerUi.control(this,selected?palette.chip:palette.surface,ViewerUi.CARD,palette.light));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);choices.addView(row,lp);
            TextView name=text((selected?"✓  ":"")+(custom?"Своя модель":AnalystModelCatalog.MODELS[i].label),16);
            name.setTypeface(null,android.graphics.Typeface.BOLD);row.addView(name);
            TextView info=text((custom?"Файл LiteRT-LM":AnalystModelCatalog.MODELS[i].description+" · "+String.format(java.util.Locale.ROOT,"%.2f ГБ",AnalystModelCatalog.MODELS[i].size/1e9))+
                (AnalystModelCatalog.installed(file,i)?" · Установлена":file.exists()?" · Файл неполный":" · Не установлена")+
                (custom?"":"\nЖелательно свободно ОЗУ от "+String.format(java.util.Locale.ROOT,"%.1f ГБ",AnalystModelCatalog.MODELS[i].suggestedFreeBytes()/1e9)+" (оценка)"),12);info.setTextColor(palette.muted);row.addView(info);
            row.setFocusable(true);row.setContentDescription(name.getText()+". "+info.getText());
            row.setOnClickListener(v->{if(busy)return;selectedModel=index;model=file;
                getSharedPreferences(MODEL_PREFS,MODE_PRIVATE).edit().putString("selected_key",AnalystModelCatalog.key(index)).apply();picker.dismiss();refresh();});
        }
        picker.show();
    }
    private void refresh(){
        boolean runtime=runtimeAvailable(),present=AnalystModelCatalog.installed(model,selectedModel);
        modelCard.setVisibility(runtime?android.view.View.VISIBLE:android.view.View.GONE);
        question.setEnabled(!busy);
        status.setText(!runtime?"В этой лёгкой сборке нет движка нейросети. Нужна экспериментальная ИИ-сборка.":present?"Выбранная модель установлена локально · "+(model.length()/1000000)+" МБ.":selectedModel==AnalystModelCatalog.CUSTOM?"Выбери совместимую модель LiteRT-LM из файлов телефона.":"Скачай выбранную модель. Для слабого телефона начни с Qwen3-0.6B.");
        modelState.setText(!runtime?"Движок недоступен в этой сборке":present?"Установлена · "+(model.length()/1000000)+" МБ":selectedModel==AnalystModelCatalog.CUSTOM?"Выбери локальный файл":"Не скачана");
        modelSelector.setText((selectedModel==AnalystModelCatalog.CUSTOM?"Своя модель":AnalystModelCatalog.MODELS[selectedModel].label)+"  ›");
        modelSelector.setEnabled(!busy);install.setEnabled(runtime&&!busy&&selectedModel==AnalystModelCatalog.CUSTOM);remove.setEnabled(model.exists()&&!busy);updateAsk();
        install.setVisibility(selectedModel==AnalystModelCatalog.CUSTOM?android.view.View.VISIBLE:android.view.View.GONE);
        download.setVisibility(selectedModel<AnalystModelCatalog.CUSTOM&&!present?android.view.View.VISIBLE:android.view.View.GONE);
        remove.setVisibility(model.exists()?android.view.View.VISIBLE:android.view.View.GONE);
        stop.setVisibility(busy?android.view.View.VISIBLE:android.view.View.GONE);
        progress.setVisibility(downloading?android.view.View.VISIBLE:android.view.View.GONE);
        download.setEnabled(runtime&&!busy&&selectedModel<AnalystModelCatalog.CUSTOM&&checkSelfPermission(android.Manifest.permission.INTERNET)==android.content.pm.PackageManager.PERMISSION_GRANTED);
        scan.setEnabled(!busy);full.setEnabled(runtime&&present&&!busy);stop.setEnabled(busy);
        modelCheck.setVisibility(runtime&&present?android.view.View.VISIBLE:android.view.View.GONE);modelCheck.setEnabled(runtime&&present&&!busy);
        modelOptions.setVisibility(model.exists()?android.view.View.VISIBLE:android.view.View.GONE);
        modelMaintenance.setVisibility(model.exists()&&modelOptionsOpen?android.view.View.VISIBLE:android.view.View.GONE);
    }
    private boolean factualQuestion(){
        return compareArchive==null&&question!=null&&ChatQuestionAnalyst.supports(question.getText().toString());
    }
    private void updateAsk(){
        if(ask==null||question==null)return;
        boolean exact=factualQuestion();
        ask.setText(exact?"Ответить по фактам":runtimeAvailable()&&AnalystModelCatalog.installed(model,selectedModel)?"Спросить ИИ":"Найти источники");
        ask.setEnabled(!busy&&!question.getText().toString().trim().isEmpty());
        if(evidenceOnly!=null){boolean show=!exact&&runtimeAvailable()&&AnalystModelCatalog.installed(model,selectedModel);
            evidenceOnly.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
            evidenceOnly.setEnabled(show&&!busy&&!question.getText().toString().trim().isEmpty());}
    }
    private void analyzeFacts(String q){
        busy("Проверяю факты по всей переписке…");results.removeAllViews();
        worker.execute(()->{
            try{
                ChatQuestionAnalyst.Answer answer=ChatQuestionAnalyst.readOnly(getDatabasePath(DeepChatContext.dbName(archive)),
                    ChatQuestionAnalyst.parse(q),taskCancel);
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    if(answer==null||taskCancel.get()){status.setText("Операция отменена.");return;}
                    results.addView(text("Точный расчёт по архиву · без нейросети",18));revealResults();
                    results.addView(text(answer.text,15));
                    for(ChatQuestionAnalyst.Evidence src:answer.sources){
                        Button link=button(src.author+" · "+src.date+"\n"+src.excerpt);
                        link.setAllCaps(false);results.addView(link);
                        link.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",src.id));finish();});
                    }
                });
            }catch(Exception failure){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();
                status.setText(taskCancel.get()?"Операция отменена.":"Не удалось прочитать переписку.");});}
        });
    }
    private void busy(String message){taskCancel.set(false);busy=true;question.setEnabled(false);modelSelector.setEnabled(false);install.setEnabled(false);remove.setEnabled(false);ask.setEnabled(false);if(evidenceOnly!=null)evidenceOnly.setEnabled(false);download.setEnabled(false);scan.setEnabled(false);full.setEnabled(false);modelCheck.setEnabled(false);stop.setEnabled(true);stop.setVisibility(android.view.View.VISIBLE);status.setText(message);}
    private void downloadModel(AnalystModelCatalog.Model spec){
        if(busy||selectedModel>=AnalystModelCatalog.CUSTOM||spec!=AnalystModelCatalog.MODELS[selectedModel])return;
        final File destination=spec.file(getNoBackupFilesDir());busy("Скачиваю модель…");downloading=true;progress.setProgress(0);progress.setVisibility(android.view.View.VISIBLE);
        worker.execute(()->{
            String error=null;
            try{AnalystModelDownload.fetch(spec,destination,taskCancel,n->runOnUiThread(()->{if(!stopped.get()&&!taskCancel.get()){int percent=(int)(n*100/spec.size);progress.setProgress(percent);status.setText("Загружено "+percent+"%");}}));}
            catch(Exception e){error=taskCancel.get()?"Загрузка отменена.":"Загрузка не завершена: проверь сеть, свободное место и целостность файла.";}
            final String failure=error;
            runOnUiThread(()->{if(stopped.get())return;downloading=false;busy=false;refresh();if(failure!=null)status.setText(failure);});
        });
    }
    private void scanBehavior(){
        if(busy)return;busy("Сканирую всю переписку…");results.removeAllViews();
        worker.execute(()->{
            try{
                BehaviorScan.Report report=BehaviorScan.read(getDatabasePath(DeepChatContext.dbName(archive)),taskCancel,
                    n->runOnUiThread(()->{if(!stopped.get())status.setText("Проверено сообщений: "+n);}));
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    results.addView(text("Разбор формулировок · "+(report.complete?"полный проход":"неполный проход"),18));
                    results.addView(text(report.summary(),14));
                    results.addView(text("Цитаты могут быть шуткой, отрицанием или пересказом. Оценивать нужно поступки в контексте, а не объявлять человека плохим по счётчику.",14));
                    for(BehaviorScan.Example e:report.evidence){
                        DeepChatContext.Source src=e.source;
                        Button source=button(BehaviorScan.LABELS[e.category]+" · "+src.author+" · "+src.date+"\n"+src.body);
                        results.addView(source);source.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",src.id));finish();});
                    }
                });
            }catch(Exception error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText("Не удалось прочитать архив.");});}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=1||result!=RESULT_OK||data==null||data.getData()==null||busy||selectedModel!=AnalystModelCatalog.CUSTOM)return;
        Uri uri=data.getData();busy("Копирую модель в приватное хранилище…");
        worker.execute(()->{
            File tmp=new File(getNoBackupFilesDir(),"analyst-install-"+java.util.UUID.randomUUID()+".tmp");String error=null;
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp,false)){
                if(in==null)throw new IOException("Unreadable model");
                byte[] buf=new byte[256*1024];long size=0;int n;
                while(!stopped.get()&&!taskCancel.get()&&(n=in.read(buf))!=-1){size+=n;if(size>3L*1024*1024*1024||tmp.getUsableSpace()<128L*1024*1024)throw new IOException("Limit");out.write(buf,0,n);}
                if(stopped.get()||taskCancel.get()||size<1024*1024)throw new IOException("Incomplete");
                out.flush();
            }catch(Exception e){error="Модель не скопирована: файл недоступен, слишком большой или мало места.";}
            if(error==null&&!stopped.get()&&!taskCancel.get())try{java.nio.file.Files.move(tmp.toPath(),model.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){error="Не удалось сохранить модель.";}
            if(tmp.exists())tmp.delete();final String message=error;
            runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();if(message!=null)status.setText(message);});
        });
    }
    private boolean canStartModel(){
        if(busy||!AnalystModelCatalog.installed(model,selectedModel))return false;
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        long required=AnalystModelCatalog.requiredFreeBytes(model.length(),AnalystModelCatalog.canReuseEngine(model.getAbsolutePath()));
        if(memory.lowMemory||memory.availMem<required){
            status.setText(String.format(java.util.Locale.ROOT,"Свободно ОЗУ %.1f ГБ; оценка для запуска %.1f ГБ. Закрой другие приложения или выбери модель меньше.",memory.availMem/1e9,required/1e9));return false;
        }
        if(android.os.Build.VERSION.SDK_INT>=29){
            android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
            if(power!=null&&power.getCurrentThermalStatus()>=android.os.PowerManager.THERMAL_STATUS_SEVERE){
                status.setText("Телефон перегрет. Дай ему остыть перед запуском модели.");return false;
            }
        }
        return true;
    }
    private static final class InvalidModelFile extends IOException {}
    private void verifyModelForRun()throws IOException{
        if(taskCancel.get()||stopped.get())throw new IOException("Cancelled");
        if(selectedModel==AnalystModelCatalog.CUSTOM)return; // User-selected file has no pinned hash.
        AnalystModelCatalog.Model spec=AnalystModelCatalog.MODELS[selectedModel];
        runOnUiThread(()->{if(!stopped.get()&&!taskCancel.get())status.setText("Проверяю целостность модели…");});
        boolean valid;
        try{valid=AnalystModelDownload.matches(model,spec.size,spec.sha,taskCancel);}
        catch(IOException failure){if(taskCancel.get()||stopped.get())throw failure;throw new InvalidModelFile();}
        if(taskCancel.get()||stopped.get())throw new IOException("Cancelled");
        if(!valid)throw new InvalidModelFile();
    }
    private String modelFailure(Throwable error,String fallback){
        if(taskCancel.get())return "Операция отменена.";
        if(error instanceof InvalidModelFile)return "Файл модели повреждён или недоступен. Удали выбранную модель и скачай её заново.";
        Throwable root=error;
        for(int i=0;i<8&&root.getCause()!=null&&root.getCause()!=root;i++)root=root.getCause();
        if(root instanceof LinkageError||String.valueOf(root.getMessage()).contains("JNI ABI mismatch"))
            return "Движок этой сборки несовместим с моделью. Можно открыть найденные источники без ИИ.";
        if(root instanceof OutOfMemoryError)return "Не хватило памяти для модели. Выбери более лёгкую или просмотри источники.";
        return fallback;
    }
    private void checkModel(){
        if(!canStartModel()||!MODEL_BUSY.compareAndSet(false,true))return;
        final String path=model.getAbsolutePath();busy("Проверяю модель на вымышленном диалоге…");results.removeAllViews();
        worker.execute(()->{
            try{
                verifyModelForRun();
                long start=android.os.SystemClock.elapsedRealtime();
                String answer=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generate",String.class,String.class,AtomicBoolean.class).invoke(null,path,AnalystSelfCheck.PROMPT,taskCancel);
                long duration=android.os.SystemClock.elapsedRealtime()-start;
                if(taskCancel.get()||stopped.get())throw new IOException("Cancelled");
                final String value=answer==null?"":answer.trim();
                final String assessment=AnalystSelfCheck.assess(value);
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();
                    results.addView(text("Проверка на вымышленном диалоге",18));revealResults();
                    results.addView(text(String.format(java.util.Locale.ROOT,"Запуск и ответ: %.1f с. Это не скорость токенов и не полноценный тест качества.",duration/1000.0),13));
                    results.addView(text(assessment,14));results.addView(text(DeepChatContext.clip(value,4000),14));
                    results.addView(text("Личные сообщения не использовались. Этот тест не доказывает отсутствие отказов или галлюцинаций.",13));
                });
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText(modelFailure(error,"Модель не прошла пробный запуск. Проверь совместимость и память."));});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    private void scanWithModel(){
        if(!canStartModel())return;
        if(!MODEL_BUSY.compareAndSet(false,true)){status.setText("Предыдущий запрос ещё завершается.");return;}
        String input=question.getText().toString().trim();
        final String q=input.isEmpty()?"Разбери поведение обоих участников, конфликты, поддержку и изменения общения.":input;
        busy("Запускаю полный проход модели…");results.removeAllViews();
        worker.execute(()->{
            try{
                verifyModelForRun();
                FullChatModelScan.Progress progress=(rows,total,blocks)->runOnUiThread(()->{
                    if(!stopped.get()&&!taskCancel.get()){
                        if(android.os.Build.VERSION.SDK_INT>=29){
                            android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
                            if(power!=null&&power.getCurrentThermalStatus()>=android.os.PowerManager.THERMAL_STATUS_SEVERE){
                                taskCancel.set(true);status.setText("Проход остановлен из-за перегрева.");return;
                            }
                        }
                        status.setText("Прочитано строк "+rows+" из "+total+" · обработано блоков "+blocks);
                    }
                });
                FullChatModelScan.Report report=(FullChatModelScan.Report)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generateFull",String.class,String.class,String.class,AtomicBoolean.class,FullChatModelScan.Progress.class)
                    .invoke(null,model.getAbsolutePath(),getDatabasePath(DeepChatContext.dbName(archive)).getAbsolutePath(),q,taskCancel,progress);
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    results.addView(text(report.complete?"Полный проход по текстовым строкам":"Незавершённый проход",18));
                    results.addView(text("Строк прочитано: "+report.rows+" из "+report.total+" · завершено блоков: "+report.blocks,13));
                    results.addView(text("Сжатый конспект модели. Может пропускать детали; не является доказательством всех выводов.",13));
                    results.addView(text(report.notes.isEmpty()?"Конспект не сформирован.":report.notes,15));
                    if(report.unverified)results.addView(text("Часть выводов не имеет надёжной ссылки. Проверь цитаты.",14));
                    for(DeepChatContext.Source src:report.sources){
                        Button source=button("[#"+src.id+"] "+src.author+" · "+src.date+"\n"+src.body);results.addView(source);
                        source.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",src.id));finish();});
                    }
                });
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText(modelFailure(error,"Модель не завершила проход. Проверь память и совместимость."));});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    /** Useful evidence retrieval even on lightweight builds or phones without downloaded weights. */
    private String replyTiming(long count,long seconds,long[] buckets){
        if(count==0)return "Нет явных ответов с двумя известными отметками времени.";
        return String.format(java.util.Locale.ROOT,"Средний интервал: %.1f мин · пар: %d\nДо 1 мин: %d · до 5 мин: %d · до часа: %d · до суток: %d · дольше: %d",
            (double)seconds/count/60,count,buckets[0],buckets[1],buckets[2],buckets[3],buckets[4]);
    }
    private LinearLayout reportSection(String label){
        Button toggle=button(label+"  ▸");toggle.setAllCaps(false);results.addView(toggle);
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setVisibility(android.view.View.GONE);results.addView(body);
        toggle.setOnClickListener(v->{boolean open=body.getVisibility()!=android.view.View.VISIBLE;body.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);toggle.setText(label+(open?"  ▾":"  ▸"));});
        return body;
    }
    private void revealResults(){
        // The result lives directly under the question, above setup controls.
        pageScroll.post(()->{if(!stopped.get()&&!busy&&results.getChildCount()>0){
            pageScroll.smoothScrollTo(0,Math.max(0,results.getTop()-dp(8)));
        }});
    }
    private void scanRelationships(){
        busy("Сканирую весь архив…");results.removeAllViews();
        worker.execute(()->{
            try{
                RelationshipScan.Report report=RelationshipScan.read(getDatabasePath(DeepChatContext.dbName(archive)),taskCancel,
                    n->runOnUiThread(()->{if(!stopped.get()&&!taskCancel.get())status.setText("Проверено сообщений: "+n);}));
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    if(!report.complete||taskCancel.get()){status.setText("Сканирование отменено.");return;}
                    results.addView(text("Взаимоотношения · "+report.total+" сообщений",20));revealResults();
                    results.addView(text("Начало разговора: первое сообщение архива или после паузы от 6 часов. Неизвестные даты и обратный порядок ограничивают точность. Ответы — только явные связи Telegram, не все сообщения.",13));
                    results.addView(text("Начал разговоров: "+report.sessions+" · без времени: "+report.missingTime+" · обратный порядок: "+report.unorderedTime,13));
                    java.util.List<java.util.Map.Entry<String,RelationshipScan.Person>> people=new java.util.ArrayList<>(report.people.entrySet());
                    people.sort((a,b)->Long.compare(b.getValue().messages,a.getValue().messages));
                    LinearLayout peopleSection=reportSection("Участники · "+people.size());
                    for(int i=0;i<Math.min(20,people.size());i++){
                        java.util.Map.Entry<String,RelationshipScan.Person> item=people.get(i);RelationshipScan.Person p=item.getValue();
                        peopleSection.addView(text(item.getKey()+" · "+p.messages+" сообщений",17));
                        peopleSection.addView(text("Начал разговор: "+p.starts+" · продолжены другим отправителем: "+p.answeredStarts+
                            "\nСо знаком вопроса: "+p.questions+" · отправлено явных ответов: "+p.explicitReplies+" · получено: "+p.receivedReplies,14));
                        peopleSection.addView(text(replyTiming(p.timedReplies,p.replySeconds,p.replyBuckets),13));
                    }
                    if(people.size()>20||report.capped)results.addView(text("Разбивка по участникам ограничена.",13));
                    LinearLayout replySection=reportSection("Кто кому отвечал · "+report.replies.size());
                    replySection.addView(text("Интервал между сообщением и явным ответом Telegram, а не время прочтения. Сессии — приблизительные; в группе продолжение может относиться к другому человеку.",13));
                    java.util.List<RelationshipScan.Pair> pairs=new java.util.ArrayList<>(report.replies.values());
                    pairs.sort((a,b)->Long.compare(b.count,a.count));
                    for(int i=0;i<Math.min(20,pairs.size());i++){
                        RelationshipScan.Pair p=pairs.get(i);replySection.addView(text(p.from+" → "+p.to+": "+p.count,15));
                        replySection.addView(text(replyTiming(p.timed,p.seconds,p.buckets),13));
                    }
                    if(pairs.isEmpty())results.addView(text("Нет разрешённых связей явных ответов.",13));
                    replySection.addView(text("Неразрешённых ответов: "+report.unresolvedReplies+" · без корректного интервала: "+report.invalidReplyTimes+" · ответы себе: "+report.selfReplies,13));
                    if(pairs.size()>20)replySection.addView(text("Показаны 20 самых частых направлений.",13));
                    LinearLayout conduct=reportSection("Поступки и формулировки");
                    conduct.addView(text(report.behavior.summary(),14));
                    for(BehaviorScan.Example example:report.behavior.evidence){
                        DeepChatContext.Source src=example.source;
                        Button link=button(BehaviorScan.LABELS[example.category]+" · "+src.author+"\n"+src.body);
                        link.setAllCaps(false);conduct.addView(link);
                        link.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",src.id));finish();});
                    }
                });
            }catch(Exception failure){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText("Не удалось прочитать архив.");});}
        });
    }
    private void findEvidence(String q){
        busy("Подбираю источники из локального архива…");results.removeAllViews();
        worker.execute(()->{
            try{
                DeepChatContext first=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(archive)),q,taskCancel);
                final DualAnalystContext dual;
                if(compareArchive!=null&&!taskCancel.get()){
                    DeepChatContext second=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(compareArchive)),q,taskCancel);
                    dual=DualAnalystContext.combine(archive,first,compareArchive,second);
                }else dual=null;
                DeepChatContext context=dual==null?first:dual.context;
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    if(taskCancel.get()){status.setText("Операция отменена.");return;}
                    results.addView(text("Подборка источников · без нейросети",18));revealResults();
                    results.addView(text("Это найденные сообщения, а не готовый смысловой вывод. Проверь контекст каждой цитаты.",14));
                    results.addView(text(context.method+" · "+context.sources.size()+" выдержек из "+context.total+" сообщений",13));
                    if(context.sources.isEmpty())results.addView(text("Подходящих сообщений нет.",14));
                    for(DeepChatContext.Source src:context.sources){
                        Button source=button(src.author+" · "+src.date+"\n"+src.body);
                        source.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                        source.setAllCaps(false);results.addView(source);
                        source.setOnClickListener(v->{
                            DualAnalystContext.Link link=dual==null?null:dual.links.get(src.id);
                            setResult(RESULT_OK,new Intent().putExtra("archive",link==null?archive:link.archive)
                                .putExtra("message",link==null?src.id:link.messageId));finish();
                        });
                    }
                });
            }catch(Exception failure){runOnUiThread(()->{
                if(stopped.get())return;busy=false;refresh();
                status.setText(taskCancel.get()?"Операция отменена.":"Не удалось подобрать сообщения.");
            });}
        });
    }
    private void analyze(){
        String q=question.getText().toString().trim();if(q.isEmpty()||busy)return;
        if(factualQuestion()){analyzeFacts(q);return;}
        if(!runtimeAvailable()||!AnalystModelCatalog.installed(model,selectedModel)){findEvidence(q);return;}
        if(!canStartModel()){
            new FontDialog.Builder(this).setTitle("ИИ сейчас недоступен").setMessage(status.getText()+"\nМожно найти исходные сообщения без модели.")
                .setNegativeButton("Закрыть",null).setPositiveButton("Найти источники",(d,n)->{if(!busy)findEvidence(q);}).show();return;
        }
        if(!MODEL_BUSY.compareAndSet(false,true)){status.setText("Предыдущий запрос ещё завершает работу.");return;}
        busy("Подбираю сообщения, затем запускаю модель…");results.removeAllViews();
        worker.execute(()->{
            try{
                verifyModelForRun();
                DeepChatContext first=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(archive)),q,taskCancel);
                final DualAnalystContext dual;
                if(compareArchive!=null){
                    if(taskCancel.get()||stopped.get())throw new IOException("Cancelled");
                    DeepChatContext second=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(compareArchive)),q,taskCancel);
                    if(first.sources.isEmpty()||second.sources.isEmpty())throw new IOException("Missing comparison evidence");
                    dual=DualAnalystContext.combine(archive,first,compareArchive,second);
                }else dual=null;
                DeepChatContext context=dual==null?first:dual.context;
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                if(context.sources.isEmpty())throw new IOException("No sources");
                String prompt=dual==null?context.prompt(q):dual.prompt(q);
                String response=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generate",String.class,String.class,AtomicBoolean.class).invoke(null,model.getAbsolutePath(),prompt,taskCancel);
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                final String answer=response==null?"":response.trim();
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();results.removeAllViews();
                    results.addView(text("Ответ модели · не проверенный факт",18));revealResults();
                    results.addView(text(answer.isEmpty()?"Модель не вернула текст.":DeepChatContext.clip(answer,16000),15));
                    if(dual!=null&&!dual.citesBoth(answer,prompt))results.addView(text("Сравнение не подтверждено цитатами из обеих переписок. Проверь выводы.",14));
                    if(!DeepChatContext.unknownCitations(answer,prompt).isEmpty())results.addView(text("Внимание: ответ содержит неизвестные номера источников. Перепроверь выводы.",14));
                    if(!answer.matches("(?s).*\\[#\\d+\\].*"))results.addView(text("Модель не сослалась на конкретные сообщения. Выводы не подтверждены.",14));
                    long sent=context.sources.stream().filter(src->DeepChatContext.sentSource(prompt,src.id)).count();
                    results.addView(text(context.method+" · "+sent+" выдержек из "+context.total+" сообщений",13));
                    for(DeepChatContext.Source src:context.sources){
                        // Only offer sources that were actually sent to the model.
                        if(!DeepChatContext.sentSource(prompt,src.id))continue;
                        Button source=button("[#"+src.id+"] "+src.author+" · "+src.date+"\n"+src.body);
                        source.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                        source.setAllCaps(false);results.addView(source);
                        source.setOnClickListener(v->{
                            DualAnalystContext.Link link=dual==null?null:dual.links.get(src.id);
                            setResult(RESULT_OK,new Intent().putExtra("archive",link==null?archive:link.archive)
                                .putExtra("message",link==null?src.id:link.messageId));finish();
                        });
                    }
                });
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText(modelFailure(error,"Не удалось выполнить анализ: проверь совместимость модели и доступную память."));
                if(!taskCancel.get()){
                    Button retry=button("Посмотреть источники без ИИ");results.addView(retry);
                    retry.setOnClickListener(v->{if(!busy)findEvidence(q);});
                }});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    @Override protected void onDestroy(){stopped.set(true);taskCancel.set(true);worker.shutdownNow();super.onDestroy();}
}
