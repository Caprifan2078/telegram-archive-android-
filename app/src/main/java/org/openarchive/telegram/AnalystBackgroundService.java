package org.openarchive.telegram;

import android.app.*;
import android.content.*;
import android.os.*;
import android.content.pm.ServiceInfo;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** User-initiated, private, single-job local work. Lives in the analyst process,
 * not an Activity. No chat text is placed in notifications or persisted reports. */
public final class AnalystBackgroundService extends Service {
    static final AtomicBoolean MODEL_BUSY=new AtomicBoolean();
    static final String DOWNLOAD="download", SEARCH="search", ANSWER="answer", FULL="full";
    private static final String CHANNEL="analyst_tasks", CANCEL="cancel";
    private static final int NOTIFICATION=6127;
    static final class Job {
        final String kind,archive,compare,question;
        final int model;
        final AtomicBoolean cancel=new AtomicBoolean();
        volatile String status="Запуск…",answer="";
        volatile int percent=-1;
        volatile boolean done,success;
        volatile java.util.List<DeepChatContext.Source> sources=Collections.emptyList();
        volatile Map<Long,DualAnalystContext.Link> links=Collections.emptyMap();
        volatile java.util.List<String> blockNotes=Collections.emptyList();
        volatile long completedBlocks;
        Job(String k,String a,String b,String q,int model){kind=k;archive=a;compare=b;question=q;this.model=model;}
        boolean matches(String a,String b){return java.util.Objects.equals(a,archive)&&java.util.Objects.equals(b,compare);}
    }
    private static volatile Job job;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private Thread worker;
    private volatile Job activeJob;
    private volatile boolean destroyed;
    private PowerManager.WakeLock wake;
    private long lastUpdate;
    static Job current(){return job;}
    static boolean running(){Job j=job;return j!=null&&!j.done;}
    static synchronized boolean start(Context ctx,String kind,String archive,String compare,String question,int model){
        if(!DOWNLOAD.equals(kind)&&!SEARCH.equals(kind)&&!ANSWER.equals(kind)&&!FULL.equals(kind))return false;
        if(FULL.equals(kind)&&compare!=null)return false;
        if(model<0||model>AnalystModelCatalog.CUSTOM)return false;
        if(DOWNLOAD.equals(kind)&&model==AnalystModelCatalog.CUSTOM)return false;
        if(!MODEL_BUSY.compareAndSet(false,true))return false;
        Job next=new Job(kind,archive,compare,question,model);job=next;
        android.util.Log.i("AnalystJob","start kind="+kind);
        try{
            ctx.startForegroundService(new Intent(ctx,AnalystBackgroundService.class));
            return true;
        }catch(RuntimeException error){android.util.Log.w("AnalystJob","start failed: "+error.getClass().getSimpleName());next.status="Не удалось запустить фоновую задачу";next.done=true;MODEL_BUSY.set(false);return false;}
    }
    static void cancel(){Job j=job;if(j!=null&&!j.done)j.cancel.set(true);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        Job active=job;
        if(intent!=null&&CANCEL.equals(intent.getAction())){cancel();if(active==null||active.done)stopSelf();return START_NOT_STICKY;}
        if(active==null||active.done){stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_NOT_STICKY;
        activeJob=active;
        try{
            NotificationManager nm=getSystemService(NotificationManager.class);
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Фоновый анализ",NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);nm.createNotificationChannel(channel);
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification(active),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(NOTIFICATION,notification(active));
            PowerManager pm=getSystemService(PowerManager.class);
            if(pm!=null){wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"telegramarchive:analyst");wake.acquire(6*60*60*1000L);}
        }catch(RuntimeException e){android.util.Log.w("AnalystJob","foreground failed: "+e.getClass().getSimpleName());active.status="Android не разрешил фоновую работу";active.done=true;stopSelf();return START_NOT_STICKY;}
        worker=new Thread(()->execute(active),"analyst-background");worker.start();
        return START_NOT_STICKY;
    }
    private Notification notification(Job j){
        Intent open=new Intent(this,DeepAnalystActivity.class).putExtra("archive",j.archive)
            .putExtra("compare_archive",j.compare).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancelIntent=PendingIntent.getService(this,2,new Intent(this,AnalystBackgroundService.class).setAction(CANCEL),
            PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Telegram Archive").setContentText(j.status).setContentIntent(content)
            .setVisibility(Notification.VISIBILITY_SECRET).setOnlyAlertOnce(true).setOngoing(!j.done);
        if(!j.done)n.addAction(new Notification.Action.Builder(null,"Отменить",cancelIntent).build());
        if(!j.done)n.setProgress(100,Math.max(0,j.percent),j.percent<0);
        return n.build();
    }
    private void update(Job j,String text,int percent){
        j.status=text;j.percent=percent;
        long now=SystemClock.elapsedRealtime();if(now-lastUpdate<800)return;lastUpdate=now;
        ui.post(()->{if(!j.done&&job==j)notifyIfAllowed(j);});
    }
    // Android 13+ is explicitly guarded below; the lint detector does not follow this SDK/permission guard.
    @android.annotation.SuppressLint("NotificationPermission")
    private void notifyIfAllowed(Job j){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        try{getSystemService(NotificationManager.class).notify(NOTIFICATION,notification(j));}catch(RuntimeException ignored){}
    }
    private void updateProgress(Job j,long chars,long total,long blocks){
        update(j,"Прочитано "+AnalystTextScope.number(chars)+" из "+AnalystTextScope.number(total)+" знаков · блоков "+blocks,total==0?-1:(int)Math.min(99,100d*chars/total));
    }
    private void check(Job j)throws InterruptedException{
        if(j.cancel.get())throw new InterruptedException();
        if(Build.VERSION.SDK_INT>=29&&(ANSWER.equals(j.kind)||FULL.equals(j.kind))){
            PowerManager p=getSystemService(PowerManager.class);
            if(p!=null&&p.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE){j.cancel.set(true);throw new InterruptedException();}
        }
    }
    private void execute(Job j){
        try{
            check(j);
            File model=AnalystModelCatalog.file(getNoBackupFilesDir(),j.model);
            if(DOWNLOAD.equals(j.kind)){
                AnalystModelCatalog.Model spec=AnalystModelCatalog.MODELS[j.model];
                update(j,"Загрузка модели",0);
                AnalystModelDownload.fetch(spec,model,j.cancel,n->update(j,"Загрузка модели",(int)(100*n/spec.size)));
                j.answer="Модель скачана и проверена.";
            }else{
                File db=getDatabasePath(DeepChatContext.dbName(j.archive));
                if(!db.isFile())throw new IOException("Missing archive");
                if(!SEARCH.equals(j.kind)){
                    update(j,"Проверка модели",-1);
                    if(!AnalystModelCatalog.installed(model,j.model))throw new IOException("Missing model");
                    ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
                    getSystemService(ActivityManager.class).getMemoryInfo(info);
                    if(!new AnalystDeviceFit(info.availMem,info.totalMem,info.lowMemory).fits(j.model,model))
                        throw new IOException("Insufficient memory");
                    if(j.model!=AnalystModelCatalog.CUSTOM){
                        AnalystModelCatalog.Model spec=AnalystModelCatalog.MODELS[j.model];
                        if(!AnalystModelDownload.matches(model,spec.size,spec.sha,j.cancel))throw new IOException("Invalid model");
                    }
                }
                if(FULL.equals(j.kind)){
                    Class<?> runtime=Class.forName("org.openarchive.telegram.LocalLlmRuntime");
                    FullChatModelScan.Report report=(FullChatModelScan.Report)runtime.getMethod("generateFull",String.class,String.class,String.class,AtomicBoolean.class,FullChatModelScan.Progress.class)
                        .invoke(null,model.getAbsolutePath(),db.getAbsolutePath(),j.question,j.cancel,new FullChatModelScan.Progress(){
                            public void update(long rows,long total,long blocks){
                                if(Build.VERSION.SDK_INT>=29){PowerManager p=getSystemService(PowerManager.class);if(p!=null&&p.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE)j.cancel.set(true);}
                            }
                            public void detail(long chars,long total,long blocks,long passes){
                                updateProgress(j,chars,total,blocks);
                            }
                            public void block(long index,String note){
                                j.completedBlocks=index;
                                if(index<=FullChatModelScan.MAX_VISIBLE_NOTES){java.util.ArrayList<String> copy=new java.util.ArrayList<>(j.blockNotes);copy.add(note);j.blockNotes=copy;}
                            }
                        });
                    j.answer=(report.complete?"Вся переписка прочитана":"Неполный проход")+" · "+AnalystTextScope.number(report.chars)+" из "+AnalystTextScope.number(report.totalChars)+" знаков · "+report.rows+" из "+report.total+" сообщений.\nБлоков: "+report.blocks+" · запросов модели: "+report.passes+"\n\nИтоговый ответ (сжатие может пропускать детали):\n"+report.notes;
                    j.blockNotes=new java.util.ArrayList<>(report.blockNotes);j.completedBlocks=report.blocks;
                    if(!report.complete&&!j.cancel.get())throw new IOException("Incomplete full scan");
                    if(report.unverified)j.answer+="\nЧасть выводов требует проверки цитат.";
                    if(report.mergeStepsWithLostRefs>0)j.answer+="\nСжатие теряло ссылки на "+report.mergeStepsWithLostRefs+" этапах; исходные конспекты восстановлены на "+report.mergeFallbacks+" из них. Проверь выводы по оригиналам.";
                    j.sources=report.sources;
                }else{
                    update(j,"Поиск сообщений",-1);
                    DeepChatContext first=DeepChatContext.read(db,j.question,j.cancel);
                    DualAnalystContext dual=null;
                    if(j.compare!=null){
                        File second=getDatabasePath(DeepChatContext.dbName(j.compare));
                        if(!second.isFile())throw new IOException("Missing archive");
                        dual=DualAnalystContext.combine(j.archive,first,j.compare,DeepChatContext.read(second,j.question,j.cancel));
                    }
                    DeepChatContext context=dual==null?first:dual.context;check(j);
                    j.sources=context.sources;
                    if(dual!=null)j.links=dual.links;
                    if(SEARCH.equals(j.kind))j.answer="Найденные сообщения · без нейросети";
                    else if(!context.hasTopicEvidence||context.sources.isEmpty())j.answer="Недостаточно подходящих сообщений. ИИ не запускался.";
                    else{
                        String prompt=AnalystModelCatalog.shortContext(j.model)?context.compactPrompt(j.question,dual!=null):dual==null?context.prompt(j.question):dual.prompt(j.question);
                        update(j,"Локальная нейросеть отвечает",-1);
                        String response=(String)Class.forName("org.openarchive.telegram.LocalLlmRuntime").getMethod("generate",String.class,String.class,AtomicBoolean.class)
                            .invoke(null,model.getAbsolutePath(),prompt,j.cancel);
                        check(j);
                        boolean verified=AnalystQuestionParts.hasEvidence(response,prompt)&&(dual==null||dual.citesBoth(response,prompt));
                        j.answer=(verified?"Номера цитат существуют; смысл проверь по сообщениям.":"Ответ требует проверки источников.")+"\n"+response;
                        if(AnalystQuestionParts.lightweight(AnalystModelCatalog.key(j.model)))j.answer+="\nЭкспериментальная модель: может путать авторов.";
                        java.util.ArrayList<DeepChatContext.Source> sent=new java.util.ArrayList<>();
                        for(DeepChatContext.Source s:context.sources)if(DeepChatContext.sentSource(prompt,s.id))sent.add(s);
                        j.sources=sent;
                    }
                }
            }
            check(j);j.success=true;j.status="Готово";
            android.util.Log.i("AnalystJob","success kind="+j.kind+" sources="+j.sources.size());
        }catch(Exception|LinkageError error){
            android.util.Log.w("AnalystJob","task failed kind="+j.kind+" type="+error.getClass().getSimpleName());
            j.status=j.cancel.get()?"Операция отменена или остановлена":"Не удалось завершить задачу. Проверь память, модель или архив.";
            if(FULL.equals(j.kind)&&!j.blockNotes.isEmpty()&&j.answer.isEmpty()){
                j.answer="Итог не сформирован. Завершено блоков: "+j.completedBlocks+". Доступные конспекты сохранены до закрытия процесса приложения.";
            }
        }
        finally{
            j.done=true;
            if(destroyed)MODEL_BUSY.set(false);
            ui.post(()->{
                if(destroyed)return;
                stopForeground(STOP_FOREGROUND_REMOVE);
                notifyIfAllowed(j);
                stopSelf();
            });
        }
    }
    @Override public void onTimeout(int startId,int fgsType){Job j=activeJob;if(j!=null)j.cancel.set(true);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf(startId);}
    @Override public void onDestroy(){
        destroyed=true;
        Job j=activeJob;if(j!=null&&!j.done)j.cancel.set(true);
        if(j!=null&&j.done)MODEL_BUSY.set(false);
        if(wake!=null&&wake.isHeld())wake.release();
        super.onDestroy();
    }
}
