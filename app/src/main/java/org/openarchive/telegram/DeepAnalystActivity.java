package org.openarchive.telegram;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
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
    private static final AtomicBoolean MODEL_BUSY=AnalystBackgroundService.MODEL_BUSY;
    private final android.os.Handler backgroundUi=new android.os.Handler(android.os.Looper.getMainLooper());
    private AnalystBackgroundService.Job displayedJob;
    private TextView backgroundStatus;
    private Uri visionUri;
    private boolean visionVideo;
    private long visionMessage=-1;
    private Button visionAction;
    private Button backgroundAction;
    private final Runnable backgroundPoll=new Runnable(){public void run(){if(!stopped.get()){renderBackground();backgroundUi.postDelayed(this,800);}}};
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
    private TextView modelState,archiveScope;
    private volatile long archiveChars=-1;
    private boolean autoModel,autoExperimental;
    private int automaticChoice=AnalystAutoModel.NONE;
    private TextView autoState;
    private LinearLayout modelMaintenance,advancedPanel;
    private int dp(int n){return ViewerUi.dp(this,n);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(palette.text);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    private Button button(String value){Button b=new Button(this);b.setText(value);ViewerUi.styleButton(b,palette.raised,palette.text,palette.light);return b;}
    private boolean runtimeAvailable(){try{Class.forName("org.openarchive.telegram.LocalLlmRuntime",false,getClassLoader());return true;}catch(ClassNotFoundException|LinkageError e){return false;}}
    @Override public void onCreate(Bundle state){
        ViewerUi.applyActivityTheme(this);super.onCreate(state);palette=ViewerUi.palette(this);ViewerUi.systemBars(this,palette);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        archive=getIntent().getStringExtra("archive");
        try{if(!getDatabasePath(DeepChatContext.dbName(archive)).isFile()){finish();return;}}catch(IllegalArgumentException bad){finish();return;}
        compareArchive=getIntent().getStringExtra("compare_archive");
        String selectedMedia=getIntent().getStringExtra("vision_uri");
        if(selectedMedia!=null){visionUri=Uri.parse(selectedMedia);visionVideo=getIntent().getBooleanExtra("vision_video",false);visionMessage=getIntent().getLongExtra("vision_message",-1);}
        relationships=getIntent().getBooleanExtra("relationships",false)&&compareArchive==null;
        if(compareArchive!=null){
            try{if(archive.equals(compareArchive)||!getDatabasePath(DeepChatContext.dbName(compareArchive)).isFile()){finish();return;}}
            catch(IllegalArgumentException bad){finish();return;}
        }
        android.content.SharedPreferences prefs=getSharedPreferences(MODEL_PREFS,MODE_PRIVATE);
        selectedModel=AnalystModelCatalog.initialSelection(prefs.getString("selected_key",null),
            prefs.getInt("selected",-1),AnalystModelCatalog.file(getNoBackupFilesDir(),AnalystModelCatalog.CUSTOM).isFile());
        autoModel=prefs.getBoolean("auto_model",!prefs.contains("selected_key")||"gemmae2baltprobe".equals(prefs.getString("selected_key","")));
        autoExperimental=prefs.getBoolean("auto_experimental",false);
        prefs.edit().putString("selected_key",AnalystModelCatalog.key(selectedModel)).apply();
        model=AnalystModelCatalog.file(getNoBackupFilesDir(),selectedModel);
        pageScroll=new ScrollView(this);pageScroll.setFillViewport(true);pageScroll.setFocusableInTouchMode(true);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(16),dp(16),dp(24));panel.setBackgroundColor(palette.background);pageScroll.addView(panel);setContentView(pageScroll);
        TextView title=text(relationships?"Взаимоотношения":compareArchive==null?"Глубокий анализ":"Сравнение двух переписок",22);title.setTypeface(null,android.graphics.Typeface.BOLD);if(android.os.Build.VERSION.SDK_INT>=28)title.setAccessibilityHeading(true);panel.addView(title);
        panel.addView(text(relationships?"Поступки, взаимность, забота и конфликты. Выводы только по сообщениям, не оценка личности.":compareArchive==null?"Локальный ИИ · быстрый вопрос или полный проход. Ответы могут ошибаться.":"Локальный ИИ · отдельные выборки А и Б. Сравнение по цитатам, не полный проход.",14));
        status=text("",13);panel.addView(status);
        archiveScope=text("Считаю объём текста…",12);archiveScope.setTextColor(palette.muted);panel.addView(archiveScope);
        modelCard=new LinearLayout(this);modelCard.setOrientation(LinearLayout.VERTICAL);
        modelCard.setPadding(dp(12),dp(10),dp(12),dp(12));
        modelCard.setBackground(ViewerUi.surface(this,palette.surface,ViewerUi.CARD,palette.light,false));
        modelCard.addView(text("Локальная модель",15));
        modelSelector=button("");modelSelector.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
        modelCard.addView(modelSelector);
        autoState=text("",12);autoState.setTextColor(palette.accent);modelCard.addView(autoState);
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
        visionAction=button("Разобрать фото или видео · экспериментально");
        visionAction.setOnClickListener(v->chooseVision());
        if(visionUri!=null)panel.addView(visionAction);
        if(relationships){Button report=button("Сводка отношений · весь архив");panel.addView(report);report.setOnClickListener(v->{if(!busy)scanRelationships();});}
        question.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){updateAsk();}
            public void afterTextChanged(android.text.Editable s){}
        });
        backgroundAction=button("Работать в фоне  ›");panel.addView(backgroundAction);
        backgroundStatus=text("",12);backgroundStatus.setVisibility(android.view.View.GONE);panel.addView(backgroundStatus);
        backgroundAction.setOnClickListener(v->backgroundMenu());
        stop=button("Отменить операцию");panel.addView(stop);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);results.setPadding(dp(10),dp(8),dp(10),dp(8));panel.addView(results);
        panel.addView(modelCard);
        advanced=button("Дополнительный анализ  ›");panel.addView(advanced);
        advancedPanel=new LinearLayout(this);advancedPanel.setOrientation(LinearLayout.VERTICAL);panel.addView(advancedPanel);
        advancedPanel.setVisibility(android.view.View.GONE);
        scan=button("Полный разбор поведения · без ИИ");advancedPanel.addView(scan);
        full=button("Весь чат нейросетью · по блокам");
        panel.addView(full, panel.indexOfChild(backgroundAction));
        full.setVisibility(compareArchive==null?android.view.View.VISIBLE:android.view.View.GONE);
        if(visionUri==null)advancedPanel.addView(visionAction);
        advanced.setOnClickListener(v->{advancedOpen=!advancedOpen;
            advancedPanel.setVisibility(advancedOpen?android.view.View.VISIBLE:android.view.View.GONE);
            advanced.setText(advancedOpen?"Дополнительный анализ  ▾":"Дополнительный анализ  ›");});
        if(compareArchive!=null)advanced.setVisibility(android.view.View.GONE);
        FontStyler.bind(this);
        modelSelector.setOnClickListener(v->showModelPicker());
        install.setOnClickListener(v->{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(picker,1);});
        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить только файл модели?").setMessage("Переписки останутся без изменений.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,n)->{if(!busy&&!AnalystBackgroundService.running()){if(model.exists()&&!model.delete())status.setText("Не удалось удалить модель");else refresh();}}).show());
        ask.setOnClickListener(v->analyze());
        scan.setOnClickListener(v->scanBehavior());
        full.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Полный проход нейросети?")
            .setMessage("Весь текст будет прочитан блоками по 10 000 знаков. Каждый блок получит свой конспект, затем модель объединит выводы. На слабой модели блок делится на мелкие порции. Это может занять часы. Можно уйти с экрана или отменить задачу. Проверяй выводы по цитатам.")
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
        readArchiveScope();
        backgroundUi.post(backgroundPoll);
        pageScroll.requestFocus();pageScroll.post(()->pageScroll.scrollTo(0,0));
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
    private AnalystDeviceFit deviceFit(){
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        return new AnalystDeviceFit(memory.availMem,memory.totalMem,memory.lowMemory);
    }
    private void selectModel(int index){
        if(busy||AnalystBackgroundService.running())return;autoModel=false;
        setSelectedModel(index);getSharedPreferences(MODEL_PREFS,MODE_PRIVATE).edit().putBoolean("auto_model",false).apply();refresh();
    }
    private void setSelectedModel(int index){
        selectedModel=index;model=AnalystModelCatalog.file(getNoBackupFilesDir(),index);
        getSharedPreferences(MODEL_PREFS,MODE_PRIVATE).edit().putString("selected_key",AnalystModelCatalog.key(index)).apply();
    }
    private void setAutomatic(boolean experimental){
        if(busy||AnalystBackgroundService.running())return;
        autoModel=true;autoExperimental=experimental;
        getSharedPreferences(MODEL_PREFS,MODE_PRIVATE).edit().putBoolean("auto_model",true).putBoolean("auto_experimental",experimental).apply();
        applyAutomatic(false);refresh();
    }
    private void applyAutomatic(boolean deep){
        if(!autoModel||busy||AnalystBackgroundService.running()||archiveChars<0)return;
        automaticChoice=AnalystAutoModel.choose(archiveChars,autoExperimental,deep||relationships||compareArchive!=null||visionUri!=null,deviceFit(),getNoBackupFilesDir());
        if(automaticChoice!=AnalystAutoModel.NONE)setSelectedModel(automaticChoice);
    }
    private void applyWholeArchiveModel(){
        if(!autoModel||busy||AnalystBackgroundService.running()||archiveChars<0)return;
        automaticChoice=AnalystAutoModel.chooseForFull(archiveChars,autoExperimental,deviceFit(),getNoBackupFilesDir());
        if(automaticChoice!=AnalystAutoModel.NONE)setSelectedModel(automaticChoice);
    }
    private boolean autoUnavailable(){return autoModel&&archiveChars>=0&&automaticChoice==AnalystAutoModel.NONE;}
    private void showModelPicker(){showModelPicker(3);}
    private void showModelPicker(int mode){
        if(busy)return;
        AnalystDeviceFit fit=deviceFit();
        LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
        choices.setPadding(dp(14),dp(6),dp(14),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(choices);
        android.app.AlertDialog picker=new FontDialog.Builder(this).setTitle("Выбор модели").setView(scroll).setNegativeButton("Закрыть",null).create();
        choices.addView(text((archiveChars<0?"Объём архива рассчитывается":"В переписке "+AnalystTextScope.number(archiveChars)+" знаков")+"\n"+fit.summary(),13));
        choices.addView(text("Подбор зависит от размера чата и свободной памяти. Большие чаты читаются по частям.",12));
        if(archiveChars>=0)choices.addView(text("Для этого объёма: "+AnalystAutoModel.label(AnalystAutoModel.sizeCandidate(archiveChars)),14));
        Button safeAuto=button((autoModel&&!autoExperimental?"✓ ":"")+"Автоподбор · точность");choices.addView(safeAuto);
        safeAuto.setOnClickListener(v->{picker.dismiss();setAutomatic(false);});
        Button sizeAuto=button((autoModel&&autoExperimental?"✓ ":"")+"Автоподбор · по размеру чата");choices.addView(sizeAuto);
        sizeAuto.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Лёгкие модели для небольших чатов?")
            .setMessage("До 10 000 знаков — компактная Qwen, до 100 000 — обычная Qwen, крупнее — Gemma. Qwen может ошибаться в смысле и авторстве. Сложный анализ и сравнение используют Gemma. Если памяти недостаточно, останется поиск без ИИ.")
            .setNegativeButton("Отмена",null).setPositiveButton("Включить",(d,n)->{picker.dismiss();setAutomatic(true);}).show());
        String[] filters={"Рекомендуемые","Подходят по памяти","С ослабленными ограничениями","Все модели","Резервные"};
        Button filter=button("Показать: "+filters[mode]+"  ▾");choices.addView(filter);
        filter.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Фильтр моделей").setSingleChoiceItems(filters,mode,(d,n)->{d.dismiss();picker.dismiss();showModelPicker(n);}).show());
        if(mode==2)choices.addView(text("*Сторонние модификации с ослабленными ограничениями; отсутствие отказов и качество не гарантированы.",12));
        String group="";
        int shown=0;
        for(int i:AnalystModelCatalog.filteredPickerOrder(getNoBackupFilesDir(),selectedModel,mode,fit)){
            shown++;
            String nextGroup=customGroup(i);
            if(!group.equals(nextGroup)){group=nextGroup;TextView heading=text(group,14);heading.setTypeface(null,android.graphics.Typeface.BOLD);choices.addView(heading);}
            final int index=i;boolean custom=i==AnalystModelCatalog.CUSTOM,selected=i==selectedModel;
            File file=AnalystModelCatalog.file(getNoBackupFilesDir(),i);
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(10),dp(7),dp(10),dp(7));
            row.setBackground(ViewerUi.control(this,selected?palette.chip:palette.surface,ViewerUi.CARD,palette.light));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);choices.addView(row,lp);
            TextView name=text((selected?"✓  ":"")+(custom?"Своя модель":AnalystModelCatalog.MODELS[i].label),16);
            name.setTypeface(null,android.graphics.Typeface.BOLD);row.addView(name);
            TextView info=text((custom?"Выбрать файл с телефона":AnalystTextScope.size(AnalystModelCatalog.MODELS[i].size))+
                (AnalystModelCatalog.installed(file,i)?" · Скачана":file.exists()?" · Файл неполный":" · Не скачана")+
                "\n"+AnalystAutoModel.range(i)+"\n"+AnalystTextScope.label(i,archiveChars)+"\n"+fit.label(i,file)+"\n"+AnalystTextScope.quality(i),12);info.setTextColor(palette.muted);row.addView(info);
            row.setFocusable(true);row.setContentDescription(name.getText()+". "+info.getText());
            row.setOnClickListener(v->{if(busy)return;picker.dismiss();selectModel(index);});
        }
        if(shown==0)choices.addView(text("Нет моделей, подходящих по текущей оценке. Можно изменить фильтр или использовать поиск без ИИ.",14));
        picker.show();
    }
    private String customGroup(int i){
        if(i==10)return "Рекомендуемая";
        if(i==AnalystModelCatalog.CUSTOM)return "Своя модель";
        return "Другие модели";
    }
    private void readArchiveScope(){
        worker.execute(()->{
            try{
                long chars=archiveCharacters(archive);
                if(compareArchive!=null)chars+=archiveCharacters(compareArchive);
                final long count=chars;
                runOnUiThread(()->{if(!stopped.get()){archiveChars=count;archiveScope.setText("Текст переписки: "+AnalystTextScope.number(count)+" знаков · "+AnalystTextScope.number(count==0?0:1+(count-1)/FullChatModelScan.LOGICAL_BLOCK)+" блоков по 10 000 · медиа отдельно");if(!busy){applyAutomatic(false);refresh();}}});
            }catch(Exception ignored){runOnUiThread(()->{if(!stopped.get())archiveScope.setText("Объём текста не определён");});}
        });
    }
    private long archiveCharacters(String key){
        try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(DeepChatContext.dbName(key)).getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
            return FullChatModelScan.countScope(db)[1];
        }
    }
    private void refresh(){
        boolean runtime=runtimeAvailable(),present=AnalystModelCatalog.installed(model,selectedModel);
        modelCard.setVisibility(runtime?android.view.View.VISIBLE:android.view.View.GONE);
        question.setEnabled(!busy);
        status.setText(!runtime?"Доступен поиск без ИИ":present?"Модель готова к проверке":"Можно искать без ИИ или скачать модель");
        modelState.setText(!runtime?"Поиск без ИИ":selectedModel==AnalystModelCatalog.CUSTOM?"Выбери файл":AnalystTextScope.size(AnalystModelCatalog.MODELS[selectedModel].size)+(present?" · Скачана":" · Не скачана")+"\n"+AnalystTextScope.label(selectedModel,archiveChars));
        modelSelector.setText((autoModel?"Авто · ":"")+(autoUnavailable()?"Поиск без ИИ":selectedModel==AnalystModelCatalog.CUSTOM?"Своя модель":AnalystModelCatalog.MODELS[selectedModel].label)+"  ›");
        if(autoState!=null)autoState.setText(!autoModel?"Ручной выбор":archiveChars<0?"Подбираю модель…":autoUnavailable()?"Нет подходящей модели по памяти. Поиск остаётся доступным.":AnalystAutoModel.range(selectedModel)+(autoExperimental?" · автоподбор по размеру":" · приоритет точности"));
        modelSelector.setEnabled(!busy);install.setEnabled(runtime&&!busy&&selectedModel==AnalystModelCatalog.CUSTOM);remove.setEnabled(model.exists()&&!busy);updateAsk();
        install.setVisibility(selectedModel==AnalystModelCatalog.CUSTOM?android.view.View.VISIBLE:android.view.View.GONE);
        download.setVisibility(!autoUnavailable()&&selectedModel<AnalystModelCatalog.CUSTOM&&!present?android.view.View.VISIBLE:android.view.View.GONE);
        remove.setVisibility(model.exists()?android.view.View.VISIBLE:android.view.View.GONE);
        stop.setVisibility(busy?android.view.View.VISIBLE:android.view.View.GONE);
        progress.setVisibility(downloading?android.view.View.VISIBLE:android.view.View.GONE);
        download.setEnabled(runtime&&!busy&&selectedModel<AnalystModelCatalog.CUSTOM&&checkSelfPermission(android.Manifest.permission.INTERNET)==android.content.pm.PackageManager.PERMISSION_GRANTED);
        scan.setEnabled(!busy);full.setEnabled(runtime&&!busy&&compareArchive==null&&(autoModel||present));stop.setEnabled(busy);
        if(visionAction!=null){visionAction.setVisibility(runtime?android.view.View.VISIBLE:android.view.View.GONE);visionAction.setEnabled(runtime&&!busy&&!AnalystBackgroundService.running());}
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
        ask.setText(exact?"Ответить по фактам":!autoUnavailable()&&runtimeAvailable()&&AnalystModelCatalog.installed(model,selectedModel)?"Спросить ИИ":"Найти источники");
        ask.setEnabled(!busy&&!question.getText().toString().trim().isEmpty());
        if(evidenceOnly!=null){boolean show=!exact&&runtimeAvailable()&&AnalystModelCatalog.installed(model,selectedModel);
            evidenceOnly.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);
            evidenceOnly.setEnabled(show&&!busy&&!question.getText().toString().trim().isEmpty());}
    }
    private void analyzeFacts(String q){
        busy("Проверяю факты по всей переписке…");results.removeAllViews();
        worker.execute(()->{
            try{
                ChatQuestionAnalyst.Query parsed=ChatQuestionAnalyst.parse(q);
                ChatQuestionAnalyst.Answer answer=ChatQuestionAnalyst.readOnly(getDatabasePath(DeepChatContext.dbName(archive)),parsed,taskCancel);
                runOnUiThread(()->{
                    if(stopped.get())return;busy=false;refresh();
                    if(answer==null||taskCancel.get()){status.setText("Операция отменена.");return;}
                    results.addView(text(parsed.kind==ChatQuestionAnalyst.Kind.INSULT_PHRASES||parsed.kind==ChatQuestionAnalyst.Kind.APOLOGY_PHRASES||parsed.kind==ChatQuestionAnalyst.Kind.CONFLICT_PHRASES?"Совпадения формулировок · без нейросети":"Точный расчёт по архиву · без нейросети",18));revealResults();
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
        startBackground(AnalystBackgroundService.DOWNLOAD,"");
    }
    private void startBackground(String kind,String q){
        if(busy||AnalystBackgroundService.running()){status.setText("Сначала завершите текущую задачу");return;}
        if(AnalystBackgroundService.ANSWER.equals(kind)||AnalystBackgroundService.FULL.equals(kind)){
            if(AnalystBackgroundService.FULL.equals(kind))applyWholeArchiveModel();else applyAutomatic(false);refresh();
            if(!canStartModel()){status.setText("Нужна подходящая установленная модель");return;}
        }
        if(!AnalystBackgroundService.start(this,kind,archive,compareArchive,q,selectedModel)){
            status.setText("Не удалось начать фоновую задачу");return;
        }
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},74);
        renderBackground();
    }
    private void backgroundMenu(){
        if(AnalystBackgroundService.running()){
            new FontDialog.Builder(this).setTitle("Фоновая задача").setMessage("Можно закрыть этот экран. Операция продолжится, пока Android разрешает работу.")
                .setNegativeButton("Закрыть",null).setPositiveButton("Отменить задачу",(d,n)->AnalystBackgroundService.cancel()).show();
            return;
        }
        if(busy)return;
        String q=question.getText().toString().trim();
        if(q.isEmpty()){question.setError("Введи вопрос");return;}
        java.util.ArrayList<String> labels=new java.util.ArrayList<>(),kinds=new java.util.ArrayList<>();
        labels.add("Найти сообщения");kinds.add(AnalystBackgroundService.SEARCH);
        if(runtimeAvailable()&&AnalystModelCatalog.installed(model,selectedModel)){
            labels.add("Ответить нейросетью");kinds.add(AnalystBackgroundService.ANSWER);
            if(compareArchive==null){labels.add("Прочитать весь чат по частям");kinds.add(AnalystBackgroundService.FULL);}
        }
        new FontDialog.Builder(this).setTitle("Работать в фоне").setItems(labels.toArray(new String[0]),(d,i)->startBackground(kinds.get(i),q)).setNegativeButton("Отмена",null).show();
    }
    private void renderBackground(){
        AnalystBackgroundService.Job j=AnalystBackgroundService.current();
        if(j==null)return;
        boolean running=!j.done;
        backgroundStatus.setVisibility(android.view.View.VISIBLE);
        backgroundStatus.setText(j.status+(j.percent>=0&&!j.done?" · "+j.percent+"%":""));
        backgroundAction.setText(running?"Фоновая задача  ›":"Работать в фоне  ›");
        if(running){
            if(!busy){modelSelector.setEnabled(false);download.setEnabled(false);install.setEnabled(false);remove.setEnabled(false);ask.setEnabled(false);evidenceOnly.setEnabled(false);scan.setEnabled(false);full.setEnabled(false);modelCheck.setEnabled(false);}
            return;
        }
        if(displayedJob==j||busy)return;
        displayedJob=j;applyAutomatic(false);refresh();
        if(AnalystBackgroundService.DOWNLOAD.equals(j.kind))return;
        if(!j.matches(archive,compareArchive))return;
        results.removeAllViews();results.addView(text("Фоновая задача · "+j.status,18));
        results.addView(text(j.answer,15));
        if(!j.blockNotes.isEmpty()){
            Button blocks=button("Выводы по блокам · "+j.blockNotes.size());results.addView(blocks);
            blocks.setOnClickListener(v->showBlockNotes(j,0));
        }
        for(DeepChatContext.Source src:j.sources){
            Button source=button(src.author+" · "+src.date+"\n"+src.body);source.setAllCaps(false);results.addView(source);
            source.setOnClickListener(v->{DualAnalystContext.Link link=j.links.get(src.id);setResult(RESULT_OK,new Intent().putExtra("archive",link==null?archive:link.archive).putExtra("message",link==null?src.id:link.messageId));finish();});
        }
        revealResults();
    }
    private void showBlockNotes(AnalystBackgroundService.Job j,int start){
        ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(8),dp(12),dp(8));scroll.addView(body);
        int end=Math.min(start+20,j.blockNotes.size());
        for(int i=start;i<end;i++){TextView note=text(j.blockNotes.get(i),14);note.setTextIsSelectable(true);body.addView(note);}
        if(j.completedBlocks>j.blockNotes.size())body.addView(text("Показаны первые "+j.blockNotes.size()+" конспектов; остальные учтены в итоговом объединении.",12));
        FontDialog.Builder dialog=new FontDialog.Builder(this);dialog.setTitle("Конспекты "+(start+1)+"–"+end+" из "+j.completedBlocks).setView(scroll).setPositiveButton("Закрыть",null);
        if(end<j.blockNotes.size())dialog.setNeutralButton("Далее",(d,n)->showBlockNotes(j,end));
        if(start>0)dialog.setNegativeButton("Назад",(d,n)->showBlockNotes(j,Math.max(0,start-20)));
        dialog.show();
    }
    private void scanBehavior(){
        if(busy||AnalystBackgroundService.running())return;busy("Сканирую всю переписку…");results.removeAllViews();
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
    private void chooseVision(){
        if(busy||AnalystBackgroundService.running())return;
        applyAutomatic(true);refresh();
        if(selectedModel!=10||!AnalystModelCatalog.installed(model,selectedModel)){
            new FontDialog.Builder(this).setTitle("Для медиа нужна Gemma").setMessage("Выбери и скачай Gemma 4 E2B. Анализ изображений экспериментальный, требует дополнительной памяти.")
                .setNegativeButton("Отмена",null).setPositiveButton("Выбрать модель",(d,n)->selectModel(10)).show();return;
        }
        if(visionUri!=null){runVision();return;}
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});startActivityForResult(picker,2);
    }
    private void runVision(){
        if(visionUri==null||selectedModel!=10||!canStartModel()||!MODEL_BUSY.compareAndSet(false,true))return;
        final Uri local=visionUri;final boolean video=visionVideo;final long message=visionMessage;
        final String request=question.getText().toString().trim();
        busy("Читаю выбранное медиа…");results.removeAllViews();
        worker.execute(()->{
            try{
                verifyModelForRun();MediaVisionInput.Sample sample=MediaVisionInput.read(this,local,video,taskCancel);
                String prompt=(video?"На изображении "+sample.frames+" отдельных кадров видео слева направо. Не делай выводов о том, что между кадрами или в звуке. ":"Это одно фото. ")+
                    "Опиши только видимые детали по-русски. Если не видно, скажи об этом. Не угадывай личности. "+(request.isEmpty()?"Что видно?":request);
                String answer=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime").getMethod("generateVision",String.class,byte[].class,String.class,AtomicBoolean.class)
                    .invoke(null,model.getAbsolutePath(),sample.jpeg,prompt,taskCancel);
                if(taskCancel.get())throw new IOException("Cancelled");
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();results.addView(text("Описание медиа · экспериментально",18));
                    results.addView(text(video?"Проанализировано кадров: "+sample.frames+". Звук и остальная часть видео не анализировались.":"Проанализировано одно фото.",13));
                    results.addView(text(answer==null||answer.trim().isEmpty()?"Модель не вернула описание":answer,15));
                    if(message>0){Button link=button("Открыть исходное сообщение");results.addView(link);link.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",message));finish();});}revealResults();});
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(!stopped.get()){busy=false;refresh();status.setText(modelFailure(error,"Не удалось разобрать медиа. Возможно, модель или формат не поддерживается либо не хватило памяти."));}});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==2){if(result==RESULT_OK&&data!=null&&data.getData()!=null&&!busy&&!AnalystBackgroundService.running()){
            visionUri=data.getData();String mime=getContentResolver().getType(visionUri);visionVideo=mime!=null&&mime.startsWith("video/");visionMessage=-1;runVision();}return;}
        if(request!=1||result!=RESULT_OK||data==null||data.getData()==null||busy||AnalystBackgroundService.running()||selectedModel!=AnalystModelCatalog.CUSTOM)return;
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
        if(busy||AnalystBackgroundService.running()||autoUnavailable()||!AnalystModelCatalog.installed(model,selectedModel))return false;
        AnalystDeviceFit fit=deviceFit();
        if(!fit.fits(selectedModel,model)){
            status.setText(fit.summary()+". "+fit.label(selectedModel,model)+". Выбери лёгкую модель или найди источники.");return false;
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
                final String rawAssessment=AnalystSelfCheck.assess(value);
                final String assisted;
                if(AnalystQuestionParts.lightweight(AnalystModelCatalog.key(selectedModel))&&!AnalystSelfCheck.conflictDefects(value).isEmpty()){
                    String[] answers=(String[])Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                        .getMethod("generateParts",String.class,String[].class,AtomicBoolean.class)
                        .invoke(null,path,AnalystSelfCheck.independentPrompts(),taskCancel);
                    assisted=String.join("\n",answers);
                }else assisted=null;
                final String assessment=rawAssessment;
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();
                    results.addView(text("Проверка на вымышленном диалоге",18));revealResults();
                    results.addView(text(String.format(java.util.Locale.ROOT,"Запуск и ответ: %.1f с. Это не скорость токенов и не полноценный тест качества.",duration/1000.0),13));
                    results.addView(text(assessment,14));results.addView(text(DeepChatContext.clip(value,4000),14));
                    if(assisted!=null){results.addView(text("Отдельные вопросы (не заменяют исходный тест)",16));results.addView(text(AnalystSelfCheck.assess(assisted),14));results.addView(text(DeepChatContext.clip(assisted,4000),14));}
                    results.addView(text("Личные сообщения не использовались. Этот тест не доказывает отсутствие отказов или галлюцинаций.",13));
                });
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText(modelFailure(error,"Модель не прошла пробный запуск. Проверь совместимость и память."));});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    private void scanWithModel(){
        if(compareArchive!=null)return;
        String q=question.getText().toString().trim();
        if(q.isEmpty())q="Кратко разбери события, взаимную поддержку, конфликты и изменения общения.";
        startBackground(AnalystBackgroundService.FULL,q);
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
        if(busy||AnalystBackgroundService.running())return;
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
        String q=question.getText().toString().trim();if(q.isEmpty()||busy||AnalystBackgroundService.running())return;
        if(factualQuestion()){analyzeFacts(q);return;}
        applyAutomatic(false);refresh();
        if(autoUnavailable()){findEvidence(q);return;}
        if(!runtimeAvailable()||!AnalystModelCatalog.installed(model,selectedModel)){findEvidence(q);return;}
        if(!canStartModel()){
            android.app.AlertDialog.Builder fallback=new FontDialog.Builder(this).setTitle("ИИ сейчас недоступен").setMessage(status.getText()+"\nМожно найти исходные сообщения без модели.")
                .setNegativeButton("Закрыть",null).setPositiveButton("Найти источники",(d,n)->{if(!busy)findEvidence(q);});
            int alternative=deviceFit().lightweightAlternative(getNoBackupFilesDir(),selectedModel);
            if(alternative>=0)fallback.setNeutralButton("Экспериментальная Qwen",(d,n)->selectModel(alternative));
            fallback.show();return;
        }
        if(!MODEL_BUSY.compareAndSet(false,true)){status.setText("Предыдущий запрос ещё завершает работу.");return;}
        busy("Подбираю сообщения, затем запускаю модель…");results.removeAllViews();
        worker.execute(()->{
            try{
                DeepChatContext first=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(archive)),q,taskCancel);
                final DualAnalystContext dual;
                if(compareArchive!=null){
                    if(taskCancel.get()||stopped.get())throw new IOException("Cancelled");
                    DeepChatContext second=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(compareArchive)),q,taskCancel);
                    dual=DualAnalystContext.combine(archive,first,compareArchive,second);
                }else dual=null;
                DeepChatContext context=dual==null?first:dual.context;
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                if(!context.hasTopicEvidence||context.sources.isEmpty()){
                    runOnUiThread(()->{
                        if(stopped.get())return;
                        busy=false;refresh();
                        if(taskCancel.get()){status.setText("Операция отменена.");return;}
                        results.removeAllViews();
                        results.addView(text("Недостаточно тематических источников",18));revealResults();
                        results.addView(text("ИИ не запускался: поиск не нашёл достаточных совпадений"+(dual!=null?" в обеих переписках":"")+". Измените вопрос или откройте ограниченную выборку без ИИ.",14));
                        Button evidence=button("Посмотреть выборку без ИИ");results.addView(evidence);
                        evidence.setOnClickListener(v->{if(!busy)findEvidence(q);});
                    });return;
                }
                verifyModelForRun();
                String prompt=AnalystModelCatalog.shortContext(selectedModel)?context.compactPrompt(q,dual!=null):dual==null?context.prompt(q):dual.prompt(q);
                Class<?> runtime=Class.forName("org.openarchive.telegram.LocalLlmRuntime");
                java.util.List<String> parts=AnalystQuestionParts.lightweight(AnalystModelCatalog.key(selectedModel))&&dual==null?
                    AnalystQuestionParts.split(q):java.util.Collections.singletonList(q);
                String response;boolean missingPart=false;
                if(parts.size()>1){
                    String[] prompts=new String[parts.size()];
                    for(int n=0;n<prompts.length;n++)prompts[n]=AnalystModelCatalog.shortContext(selectedModel)?
                        context.compactPrompt(parts.get(n),false):context.prompt(parts.get(n));
                    String[] answers=(String[])runtime.getMethod("generateParts",String.class,String[].class,AtomicBoolean.class)
                        .invoke(null,model.getAbsolutePath(),prompts,taskCancel);
                    StringBuilder joined=new StringBuilder();
                    for(int n=0;n<answers.length;n++){
                        boolean grounded=AnalystQuestionParts.hasEvidence(answers[n],prompts[n]);missingPart|=!grounded;
                        if(n>0)joined.append("\n\n");
                        joined.append("Вопрос ").append(n+1).append(": ").append(parts.get(n)).append("\n")
                            .append(grounded?answers[n]:"Не подтверждено источниками. "+(answers[n]==null?"":answers[n]));
                    }
                    response=joined.toString();
                }else response=(String)runtime.getMethod("generate",String.class,String.class,AtomicBoolean.class)
                    .invoke(null,model.getAbsolutePath(),prompt,taskCancel);
                final boolean incomplete=missingPart;
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                final String answer=response==null?"":response.trim();
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();results.removeAllViews();
                    java.util.Set<Long> cited=FullChatModelScan.ids(answer);
                    boolean unknown=!DeepChatContext.unknownCitations(answer,prompt).isEmpty();
                    boolean missingSide=dual!=null&&!dual.citesBoth(answer,prompt);
                    boolean unsupported=answer.isEmpty()||cited.isEmpty()||unknown||missingSide||incomplete;
                    results.addView(text(unsupported?"Ответ требует проверки":"Ответ модели · сверены номера цитат",18));revealResults();
                    if(answer.isEmpty())results.addView(text("Модель не вернула итоговый текст. Ниже доступны найденные источники.",14));
                    if(incomplete)results.addView(text("Не все части запроса получили ссылки на источники.",14));
                    if(missingSide)results.addView(text("Нет ссылок на обе переписки. Сравнение не подтверждено.",14));
                    if(unknown)results.addView(text("Ответ содержит неизвестные номера источников.",14));
                    if(!answer.isEmpty()&&cited.isEmpty())results.addView(text("Ответ не ссылается на конкретные сообщения.",14));
                    if(!unsupported)results.addView(text("Номера существуют в выборке; это не проверка смысла или истинности вывода.",13));
                    if(AnalystQuestionParts.lightweight(AnalystModelCatalog.key(selectedModel)))results.addView(text("Экспериментальная модель: может ошибаться в авторстве, даже если номер цитаты существует.",14));
                    if(AnalystModelCatalog.shortContext(selectedModel))results.addView(text("Быстрый режим: до 4 цитат; для подробного анализа выберите Gemma, если хватает памяти. Ответы Qwen могут ошибаться в определении автора.",13));
                    if(!answer.isEmpty())results.addView(text(DeepChatContext.clip(answer,16000),15));
                    long sent=context.sources.stream().filter(src->DeepChatContext.sentSource(prompt,src.id)).count();
                    results.addView(text(context.method+" · "+sent+" выдержек из "+context.total+" сообщений",13));
                    LinearLayout other=null;
                    for(DeepChatContext.Source src:context.sources){
                        // Only offer sources that were actually sent to the model.
                        if(!DeepChatContext.sentSource(prompt,src.id))continue;
                        Button source=button("[#"+src.id+"] "+src.author+" · "+src.date+"\n"+src.body);
                        source.setGravity(android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
                        source.setAllCaps(false);
                        if(cited.contains(src.id)||unsupported)results.addView(source);
                        else{if(other==null)other=reportSection("Остальные выдержки");other.addView(source);}
                        source.setOnClickListener(v->{
                            DualAnalystContext.Link link=dual==null?null:dual.links.get(src.id);
                            setResult(RESULT_OK,new Intent().putExtra("archive",link==null?archive:link.archive)
                                .putExtra("message",link==null?src.id:link.messageId));finish();
                        });
                    }
                    if(unsupported){
                        Button retry=button("Все найденные источники без ИИ");results.addView(retry);
                        retry.setOnClickListener(v->{if(!busy)findEvidence(q);});
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
    @Override protected void onDestroy(){stopped.set(true);backgroundUi.removeCallbacks(backgroundPoll);taskCancel.set(true);worker.shutdownNow();super.onDestroy();}
}
