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
    private String archive;
    private File model;
    private LinearLayout panel,results;
    private TextView status;
    private EditText question;
    private Button install,ask,remove,download,scan,stop;
    private final AtomicBoolean taskCancel=new AtomicBoolean();
    private ViewerPalette palette;
    private boolean busy;
    private int dp(int n){return ViewerUi.dp(this,n);}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(palette.text);t.setPadding(dp(4),dp(5),dp(4),dp(5));return t;}
    private Button button(String value){Button b=new Button(this);b.setText(value);ViewerUi.styleButton(b,palette.raised,palette.text,palette.light);return b;}
    private boolean runtimeAvailable(){try{Class.forName("org.openarchive.telegram.LocalLlmRuntime",false,getClassLoader());return true;}catch(ClassNotFoundException|LinkageError e){return false;}}
    @Override public void onCreate(Bundle state){
        ViewerUi.applyActivityTheme(this);super.onCreate(state);palette=ViewerUi.palette(this);ViewerUi.systemBars(this,palette);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        archive=getIntent().getStringExtra("archive");
        try{if(!getDatabasePath(DeepChatContext.dbName(archive)).isFile()){finish();return;}}catch(IllegalArgumentException bad){finish();return;}
        model=new File(getNoBackupFilesDir(),"analyst.litertlm");
        ScrollView scroll=new ScrollView(this);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16),dp(16),dp(16),dp(24));panel.setBackgroundColor(palette.background);scroll.addView(panel);setContentView(scroll);
        panel.addView(text("Глубокий анализ · локальная ИИ",22));
        panel.addView(text("Экспериментальный режим. Модель получает только выбранные выдержки и статистику этого чата, не всю переписку. Ответы могут ошибаться.",14));
        status=text("",13);panel.addView(status);
        download=button("Скачать лёгкую модель · 614 МБ");panel.addView(download);
        install=button("Выбрать свою модель .litertlm");panel.addView(install);
        remove=button("Удалить локальную модель");panel.addView(remove);
        question=new EditText(this);ViewerUi.styleInput(question,palette);question.setHint("Например, о чём договаривались насчёт встречи?");
        question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(360)});
        question.setText(getIntent().getStringExtra("question"));panel.addView(question);
        ask=button("Спросить локальную нейросеть");panel.addView(ask);
        scan=button("Полный разбор поведения · без ИИ");panel.addView(scan);
        stop=button("Отменить операцию");panel.addView(stop);
        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);panel.addView(results);
        install.setOnClickListener(v->{Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(picker,1);});
        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить только файл модели?").setMessage("Переписки останутся без изменений.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,n)->{if(!busy){if(model.exists()&&!model.delete())status.setText("Не удалось удалить модель");else refresh();}}).show());
        ask.setOnClickListener(v->analyze());
        scan.setOnClickListener(v->scanBehavior());
        stop.setOnClickListener(v->{taskCancel.set(true);status.setText("Отмена запрошена…");});
        download.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Скачать Qwen3-0.6B?").setMessage("614 МБ, лучше по Wi-Fi. Скачивается только публичный файл модели с Hugging Face; переписка и вопросы не передаются.").setNegativeButton("Отмена",null).setPositiveButton("Скачать",(d,n)->downloadModel()).show());
        refresh();
    }
    private void refresh(){
        boolean runtime=runtimeAvailable(),present=model.isFile();
        status.setText(!runtime?"В этой лёгкой сборке нет движка нейросети. Нужна экспериментальная ИИ-сборка.":present?"Модель установлена локально.":"Выбери совместимую модель LiteRT-LM из файлов телефона.");
        install.setEnabled(runtime&&!busy);remove.setEnabled(present&&!busy);ask.setEnabled(runtime&&present&&!busy);
        download.setEnabled(runtime&&!busy&&checkSelfPermission(android.Manifest.permission.INTERNET)==android.content.pm.PackageManager.PERMISSION_GRANTED);
        scan.setEnabled(!busy);stop.setEnabled(busy);
    }
    private void busy(String message){taskCancel.set(false);busy=true;install.setEnabled(false);remove.setEnabled(false);ask.setEnabled(false);download.setEnabled(false);scan.setEnabled(false);stop.setEnabled(true);status.setText(message);}
    private void downloadModel(){
        if(busy)return;busy("Скачиваю модель…");
        worker.execute(()->{
            String error=null;
            try{AnalystModelDownload.fetch(model,taskCancel,n->runOnUiThread(()->{if(!stopped.get()&&!taskCancel.get())status.setText("Загружено "+(n*100/AnalystModelDownload.SIZE)+"%");}));}
            catch(Exception e){error=taskCancel.get()?"Загрузка отменена.":"Загрузка не завершена: проверь сеть, свободное место и целостность файла.";}
            final String failure=error;
            runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();if(failure!=null)status.setText(failure);});
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
        if(request!=1||result!=RESULT_OK||data==null||data.getData()==null||busy)return;
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
    private void analyze(){
        String q=question.getText().toString().trim();if(q.isEmpty()||busy||!model.isFile())return;
        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        if(memory.lowMemory||memory.availMem<model.length()+512L*1024*1024){status.setText("Недостаточно свободной памяти для этой модели. Выбери модель меньше.");return;}
        if(!MODEL_BUSY.compareAndSet(false,true)){status.setText("Предыдущий запрос ещё завершает работу.");return;}
        busy("Подбираю сообщения, затем запускаю модель…");results.removeAllViews();
        worker.execute(()->{
            try{
                DeepChatContext context=DeepChatContext.read(getDatabasePath(DeepChatContext.dbName(archive)),q,taskCancel);
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                if(context.sources.isEmpty())throw new IOException("No sources");
                String prompt=context.prompt(q);
                String response=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime")
                    .getMethod("generate",String.class,String.class).invoke(null,model.getAbsolutePath(),prompt);
                if(taskCancel.get())throw new IOException("Cancelled");
                if(stopped.get())return;
                final String answer=response==null?"":response.trim();
                runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();results.removeAllViews();
                    results.addView(text("Ответ модели · не проверенный факт",18));
                    results.addView(text(answer.isEmpty()?"Модель не вернула текст.":DeepChatContext.clip(answer,16000),15));
                    if(!DeepChatContext.unknownCitations(answer,prompt).isEmpty())results.addView(text("Внимание: ответ содержит неизвестные номера источников. Перепроверь выводы.",14));
                    if(!answer.matches("(?s).*\\[#\\d+\\].*"))results.addView(text("Модель не сослалась на конкретные сообщения. Выводы не подтверждены.",14));
                    results.addView(text(context.method+" · до "+context.sources.size()+" выдержек из "+context.total+" сообщений",13));
                    for(DeepChatContext.Source src:context.sources){
                        // Only offer sources that were actually sent to the model.
                        if(!DeepChatContext.sentSource(prompt,src.id))continue;
                        Button source=button("[#"+src.id+"] "+src.author+" · "+src.date+"\n"+src.body);
                        source.setAllCaps(false);results.addView(source);
                        source.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra("archive",archive).putExtra("message",src.id));finish();});
                    }
                });
            }catch(Exception|LinkageError error){runOnUiThread(()->{if(stopped.get())return;busy=false;refresh();status.setText("Не удалось выполнить анализ: проверь совместимость модели и доступную память.");});}
            finally{MODEL_BUSY.set(false);}
        });
    }
    @Override protected void onDestroy(){stopped.set(true);taskCancel.set(true);worker.shutdownNow();super.onDestroy();}
}
