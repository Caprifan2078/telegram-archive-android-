package org.openarchive.telegram;

import android.app.Activity;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieCompositionFactory;
import com.airbnb.lottie.LottieResult;
import com.airbnb.lottie.LottieDrawable;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import android.animation.ValueAnimator;
import android.os.SystemClock;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;
import android.text.TextUtils;
import android.view.animation.DecelerateInterpolator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Selection;
import android.text.style.BackgroundColorSpan;
import android.util.LruCache;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.PopupMenu;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import android.widget.MediaController;
import android.webkit.MimeTypeMap;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline Telegram-style archive. No INTERNET permission, no uploads or telemetry. */
public class MainActivity extends Activity {
    private int relationshipPageIndex=0;
    // Read-only side-by-side workspaces. The first archive is always the active chat.
    private String dualSecondKey="",dualSecondName="";
    private int dualYear=LocalDate.now().getYear(),dualMonth=0;

    private ConversationPeriods.Boundary pendingPeriodStart;
    private String pendingPeriodArchive="";
    private static final int PICK_FONT=117,PICK_FOLDER=27,PAGE=40,MEDIA_PAGE=30,SAVE_MEDIA_REQUEST=91,SAVE_TEXT_REQUEST=92,BATCH_TREE_REQUEST=93;
    private int BG=0xff0b1424,PANEL=0xff18283e,INCOMING=0xff29405b,OUTGOING=0xff1a6159,
            FG=0xfff5f9ff,SUB=0xffafc1d4,ACCENT=0xff7ad2ff,CHIP=0xff29465f,HIGHLIGHT=0xff326e8f;
    private int themeIndex=0, densityIndex=1,wallpaperIndex=0,wallpaperOpacity=28,bubbleRadius=17;private boolean reduceMotion=false,glassStyle=true;
    private int performanceMode=0; // 0 classic, 1 smooth, 2 low-memory, 3 adaptive
    private static final int WRAPPED_PNG_REQUEST=73,CAPSULE_EXPORT_REQUEST=84;
    private WrappedSummary wrappedForExport;
    private char[] pendingCapsulePassword;
    private DocumentFolder pendingMediaSaveRoot;
    private String pendingMediaSavePath;
    private MediaSavePolicy.Spec pendingMediaSaveSpec;
    private String pendingMessageText;
    private final LinkedHashMap<Long,MessageDb.Attachment> mediaSelection=new LinkedHashMap<>();
    private boolean multiSelectMedia=false;
    private String selectedMediaArchive="";
    private final AtomicBoolean batchCancelled=new AtomicBoolean(false);
    private boolean batchRunning=false;
    private List<MediaBatchExporter.Entry> pendingBatchFiles;
    private DocumentFolder pendingBatchSource;
    private boolean pendingBatchDownloads=false;


    private static final String[] MEDIA_KEYS={"all","image","video","favorite","round","voice","audio","file","gif","sticker"};
    private static final String[] MEDIA_LABELS={"Всё","Фото","Видео","Избранное","Кружки","Голосовые","Музыка","Файлы","GIF","Стикеры"};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ExecutorService mediaWorker=Executors.newFixedThreadPool(2);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable deferredReadingSave=this::saveReadingProgress;
    // Scroll events can arrive 60-120 times/sec. Coalesce geometry lookups and
    // preference writes instead of doing them during every animation frame.
    private final Runnable deferredFloatingCheck=this::checkAutoFloat;
    private Runnable scheduledPrefetch;
    private void scheduleReadingSave(){handler.removeCallbacks(deferredReadingSave);handler.postDelayed(deferredReadingSave,350);}
    private void scheduleFloatingCheck(){handler.removeCallbacks(deferredFloatingCheck);handler.postDelayed(deferredFloatingCheck,70);}
    private long lastLoadedMessageId=-1;
    private final LruCache<String,Bitmap> thumbs=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    private final LruCache<String,String> durations=new LruCache<>(250);
    private MessageDb db;private DocumentFolder folder;private SavedStore savedStore;private boolean loadingMore=false;private int autoLoaded=PAGE;private int searchLoaded=PAGE;private boolean home=true;private boolean autoFloating=false;private LinearLayout bottomPager;
    private JSONArray archives=new JSONArray();private String activeKey="default",activeName="Архив";private long pinnedFocus=-1;
    private LinearLayout pinnedBar;
    private FrameLayout root, floatingPanel;
    private VerticalTimeline verticalTimeline;
    private final int[] participantColors={0xff315c79,0xff665184,0xff326c65,0xff75513f,0xff5d607f,0xff724d68,0xff52683a,0xff426884};
    private LinearLayout base,list,filterBar,bottomNav,searchArea,toolArea,savedComposer;
    private ScrollView scroll;private TextView title,subtitle,status,pageLabel;private Button navBack,menuButton;private String lastRenderedDay="";
    private EditText searchInput,authorInput;private CheckBox mediaOnly;
    private Button backPage,nextPage,quickButton,deepButton,folderButton,backButton,headerActions;
    private String screen="chat",mediaKind="all",q="",byAuthor="",mine="",returnScreen="";
    private boolean deep=false,onlyMedia=false,importing=false;
    private int chatOffset=0,searchOffset=0,mediaOffset=0,favOffset=0,total=0,loadToken=0;
    private long highlightId=-1;private long lastVisibleMessage=-1;private int lastVisibleTop=0;private final Map<Long,View> renderedMessageViews=new HashMap<>();private long pendingRestoreId=-1;private int pendingRestoreTop=0;private boolean restoringBackup=false;private boolean fullBackupMode=true;private char[] backupPassword=null;private String selectedBackupName="";
    private String info="";
    private boolean analyticsCommonWords=false;
    private String analyticsUnit="month",analyticsSelectedAuthor="";
    private int analyticsCalendarYear=0;
    private String analyticsCompareFirst="",analyticsCompareSecond="";
    private ChatAnalytics.Snapshot lastAnalytics;
    private MediaPlayer player;private String playingPath="";private TextureView activeTexture;
    private int playbackGeneration=0;
    private Runnable progressUpdater;private MessageDb.Attachment playingAttachment;private LinearLayout playerStrip;private TextView playerText;private Button playerPause;private FrameLayout inlineHolder;private int floatingStart=0;
    private RoundControls activeRound;
    private static final class RoundControls {
        SeekBar seek;
        TextView timer;
        ImageView preview;
        TextView overlay;
        boolean dragging=false,wasPlaying=false;
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        FontStyler.bind(this);
        try{
            RestoreJournal.recoverAll(this,getPreferences(MODE_PRIVATE));
        }catch(Exception recovery){
            new FontDialog.Builder(this).setTitle("Не завершено восстановление")
                .setMessage("Автоматическая безопасная очистка не удалась. Данные не удалены. " +
                    "Перед следующим запуском сохраните резервную копию и обратитесь к журналу ошибок: " + recovery.getMessage())
                .setPositiveButton("Закрыть",(dialog,which)->finish())
                .setCancelable(false).show();
            return;
        }
        loadAppearance();
        if(state!=null){
            home=state.getBoolean("ta_home",true);screen=state.getString("ta_screen","chat");
            analyticsUnit=state.getString("ta_analytics_unit","month");
            analyticsSelectedAuthor=state.getString("ta_analytics_author","");
            analyticsCalendarYear=state.getInt("ta_analytics_year",0);
            analyticsCompareFirst=state.getString("ta_analytics_compare_first","");
            analyticsCompareSecond=state.getString("ta_analytics_compare_second","");
            dualSecondKey=state.getString("ta_dual_second_key","");
            dualSecondName=state.getString("ta_dual_second_name","");
            dualYear=Math.max(1,Math.min(9999,state.getInt("ta_dual_year",LocalDate.now().getYear())));
            dualMonth=Math.max(0,Math.min(12,state.getInt("ta_dual_month",0)));
        }
        loadArchiveRegistry();
        savedStore=new SavedStore(this);
        db=new MessageDb(this,activeKey);
        mine=getPreferences(MODE_PRIVATE).getString("mine","");
        String old=activeTree();
        if(old!=null)try{folder=old.startsWith("local:")?new DocumentFolder(getContentResolver(),new java.io.File(old.substring(6))):new DocumentFolder(getContentResolver(),Uri.parse(old));}catch(Exception ignored){}
        createUi();showPage();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putBoolean("ta_home",home);state.putString("ta_screen",screen);
        state.putString("ta_analytics_unit",analyticsUnit);
        state.putString("ta_analytics_author",analyticsSelectedAuthor);
        state.putInt("ta_analytics_year",analyticsCalendarYear);
        state.putString("ta_analytics_compare_first",analyticsCompareFirst);
        state.putString("ta_analytics_compare_second",analyticsCompareSecond);
        state.putString("ta_dual_second_key",dualSecondKey);
        state.putString("ta_dual_second_name",dualSecondName);
        state.putInt("ta_dual_year",dualYear);
        state.putInt("ta_dual_month",dualMonth);
        super.onSaveInstanceState(state);
    }
    private void loadAppearance(){
        SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        themeIndex=prefs.getInt("appearance_theme",0);
        densityIndex=prefs.getInt("appearance_density",1);
        bubbleRadius=prefs.getInt("appearance_bubble_radius",17);
        wallpaperIndex=prefs.getInt("appearance_wallpaper",0);
        wallpaperOpacity=prefs.getInt("appearance_wallpaper_alpha",28);
        reduceMotion=prefs.getBoolean("appearance_less_motion",false);
        performanceMode=Math.max(0,Math.min(3,prefs.getInt("appearance_performance",0)));
        glassStyle=prefs.getBoolean("appearance_glass",true);
        thumbs.resize(PerformanceAdvisor.thumbnailCacheBytes(memoryClassMb(),performanceMode));
        if(themeIndex==1){ // Dark graphite
            BG=0xff101216;PANEL=0xff23262d;INCOMING=0xff30343d;OUTGOING=0xff33544e;
            FG=0xfff5f5f7;SUB=0xffb8bdc7;ACCENT=0xffb5b9ff;CHIP=0xff393e4b;HIGHLIGHT=0xff474b80;
        }else if(themeIndex==2){ // Light pearl
            BG=0xfff3f6fa;PANEL=0xffffffff;INCOMING=0xffe2e9f2;OUTGOING=0xffc5eade;
            FG=0xff172b43;SUB=0xff506780;ACCENT=0xff1468a4;CHIP=0xffdce8f4;HIGHLIGHT=0xffb3d9ed;
        }else{
            BG=0xff0b1424;PANEL=0xff18283e;INCOMING=0xff29405b;OUTGOING=0xff1a6159;
            FG=0xfff5f9ff;SUB=0xffafc1d4;ACCENT=0xff7ad2ff;CHIP=0xff29465f;HIGHLIGHT=0xff326e8f;
        }
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        int flags=themeIndex==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private int memoryClassMb(){
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        return manager==null?128:manager.getMemoryClass();
    }
    private int effectivePerformanceMode(){
        if(performanceMode!=3)return performanceMode;
        android.app.ActivityManager manager=(android.app.ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        boolean lowRam=manager!=null&&manager.isLowRamDevice();
        return PerformanceAdvisor.mode(memoryClassMb(),lowRam);
    }
    private int motionDuration(int normal){
        // Respect both the in-app setting AND Android's system animator scale.
        boolean systemAnimations=android.animation.ValueAnimator.areAnimatorsEnabled();
        return PremiumMotion.duration(normal,reduceMotion,systemAnimations,effectivePerformanceMode());
    }
    private String lastAnimatedScreen="",lastAnimatedArchive="";

    private int bubbleInset(){return densityIndex==0?5:densityIndex==2?14:9;}
    private void appearanceDialog(){
        String[] themes={"Океан · тёмно-синяя","Графит · строгая тёмная","Жемчуг · светлая"};
        new FontDialog.Builder(this).setTitle("Тема оформления")
            .setSingleChoiceItems(themes,themeIndex,(dialog,index)->{
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_theme",index).apply();
                dialog.dismiss();loadAppearance();recreate();
            })
            .setNeutralButton("Плотность сообщений",(dialog,button)->densityDialog())
            .setPositiveButton("Готово",null).show();
    }
    private void wallpaperDialog(){
        if(home){
            ArrayList<String> chatNames=new ArrayList<>(),chatKeys=new ArrayList<>();
            for(int n=0;n<archives.length();n++){
                JSONObject chat=archives.optJSONObject(n);
                if(chat!=null){chatNames.add(chat.optString("name","Чат"));chatKeys.add(chat.optString("key"));}
            }
            if(chatKeys.isEmpty()){toast("Сначала добавь переписку");return;}
            new FontDialog.Builder(this).setTitle("Для какого чата установить фон?")
                .setItems(chatNames.toArray(new String[0]),(dialog,index)->{
                    pendingImageChatKey=chatKeys.get(index);pickPersonalImage(60);
                }).setNegativeButton("Отмена",null).show();
            return;
        }
        String[] choices={"Океанские линии","Тёплое сияние","Нефритовые волны","Геометрия","Своя фотография"};
        new FontDialog.Builder(this).setTitle("Фон переписки")
            .setSingleChoiceItems(choices,wallpaperIndex,(dialog,idx)->{
                if(idx==4){dialog.dismiss();pickPersonalImage(60);return;}
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_wallpaper",idx).remove(PersonalImages.wallpaperKey(activeKey)).apply();
                wallpaperIndex=idx;dialog.dismiss();opacityDialog();
            }).setNeutralButton("Прозрачность",(d,w)->opacityDialog()).setPositiveButton("Готово",null).show();
    }
    private void opacityDialog(){
        android.widget.SeekBar slider=new android.widget.SeekBar(this);slider.setMax(100);
        slider.setProgress(wallpaperOpacity);slider.setPadding(px(26),px(12),px(26),px(12));
        new FontDialog.Builder(this).setTitle("Интенсивность узора")
            .setView(slider).setPositiveButton("Применить",(d,w)->{
                getPreferences(MODE_PRIVATE).edit()
                    .putInt("appearance_wallpaper_alpha",slider.getProgress()).apply();
                wallpaperOpacity=slider.getProgress();showPage();
            }).setNegativeButton("Отмена",null).show();
    }
    private void bubbleStyleDialog(){
        SeekBar radius=new SeekBar(this);radius.setMax(27);radius.setProgress(bubbleRadius);
        radius.setPadding(px(25),px(15),px(25),px(15));
        new FontDialog.Builder(this).setTitle("Скругление сообщений")
            .setMessage("Слева — строгие прямоугольники, справа — плавные пузырьки.")
            .setView(radius).setNegativeButton("Отмена",null)
            .setPositiveButton("Применить",(d,w)->{
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_bubble_radius",radius.getProgress()).apply();
                bubbleRadius=radius.getProgress();showPage();
            }).show();
    }
    private void pickPersonalImage(int request){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,request);
    }
    private String pendingImageChatKey="";
    private void avatarSelection(){
        if(home){
            ArrayList<String> names=new ArrayList<>();ArrayList<String> keys=new ArrayList<>();
            for(int n=0;n<archives.length();n++){
                JSONObject chat=archives.optJSONObject(n);
                if(chat!=null){names.add(chat.optString("name","Чат"));keys.add(chat.optString("key"));}
            }
            if(keys.isEmpty()){toast("Сначала добавь переписку");return;}
            new FontDialog.Builder(this).setTitle("Аватар чата / группы")
                .setItems(names.toArray(new String[0]),(dialog,index)->{
                    pendingImageChatKey=keys.get(index);pickPersonalImage(62);
                }).show();
            return;
        }
        worker.execute(()->{
            List<String> authors=db.participants();
            runOnUiThread(()->{
                String[] names=authors.toArray(new String[0]);boolean[] selected=new boolean[names.length];
                new FontDialog.Builder(this).setTitle("Аватары участников")
                    .setMultiChoiceItems(names,selected,(d,i,enabled)->selected[i]=enabled)
                    .setNegativeButton("Отмена",null).setNeutralButton("Аватар чата",(d,w)->pickPersonalImage(62))
                    .setPositiveButton("Выбрать фото",(d,w)->{
                        ArrayList<String> chosen=new ArrayList<>();
                        for(int i=0;i<selected.length;i++)if(selected[i])chosen.add(names[i]);
                        if(chosen.isEmpty()){toast("Выбери хотя бы одного участника");return;}
                        pendingAvatarUsers=chosen;pickPersonalImage(61);
                    }).show();
            });
        });
    }
    private ArrayList<String> pendingAvatarUsers=new ArrayList<>();
    private void applyPersonalImage(Uri uri,int request){
        ArrayList<String> chosen=new ArrayList<>(pendingAvatarUsers);
        String targetChat=pendingImageChatKey.isEmpty()?activeKey:pendingImageChatKey;
        worker.execute(()->{
            try{
                String filename=PersonalImages.save(this,uri,UUID.randomUUID().toString().replace("-",""));
                SharedPreferences.Editor editor=getPreferences(MODE_PRIVATE).edit();
                if(request==60)editor.putString(PersonalImages.wallpaperKey(targetChat),filename);
                else if(request==62)editor.putString(PersonalImages.chatAvatarKey(targetChat),filename);
                else for(String name:chosen)editor.putString(PersonalImages.avatarKey(activeKey,name),filename);
                if(!editor.commit())throw new IOException("Не удалось сохранить настройки");
                runOnUiThread(()->{pendingAvatarUsers.clear();pendingImageChatKey="";showPage();toast("Изображение сохранено локально");});
            }catch(Exception error){runOnUiThread(()->toast("Не удалось сохранить фото: "+error.getMessage()));}
        });
    }
    private void densityDialog(){
        String[] options={"Компактно","Обычно","Просторно"};
        new FontDialog.Builder(this).setTitle("Плотность переписки")
            .setSingleChoiceItems(options,densityIndex,(dialog,index)->{
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_density",index).apply();
                dialog.dismiss();recreate();
            }).setNeutralButton("Скорость и плавность",(dialog,index)->performanceDialog())
            .setPositiveButton("Готово",null).show();
    }
    private void performanceDialog(){
        String[] options={"Классический · все сообщения остаются на экране",
            "Плавность · окно до 200 сообщений, мягкие анимации",
            "Экономия · окно до 120 сообщений, без анимаций",
            "Автоматически · подстраивать окно под память устройства"};
        new FontDialog.Builder(this).setTitle("Производительность переписки")
            .setMessage("Оконный режим удаляет с экрана только старые карточки. Сообщения и медиа в архиве не изменяются. Кнопка сверху возвращает к более ранним сообщениям.")
            .setSingleChoiceItems(options,performanceMode,(dialog,selection)->{
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_performance",selection).apply();
                performanceMode=selection;thumbs.resize(PerformanceAdvisor.thumbnailCacheBytes(memoryClassMb(),performanceMode));
                dialog.dismiss();autoLoaded=PAGE;searchLoaded=PAGE;showPage();
            }).setNeutralButton(reduceMotion?"Включить анимации":"Уменьшить анимации",(dialog,which)->{
                getPreferences(MODE_PRIVATE).edit().putBoolean("appearance_less_motion",!reduceMotion).apply();
                reduceMotion=!reduceMotion;
            }).setNegativeButton("Закрыть",null).show();
    }
    private int px(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}
    private GradientDrawable glass(int color,int radius){
        if(!glassStyle||effectivePerformanceMode()==2)return shape(color,radius);
        // Single drawable pass: premium sheen without expensive real-time GPU blur.
        int bright=themeIndex==2?0x22ffffff:0x137cdefa;
        int top=mixColors(color,bright,themeIndex==2?.20f:.24f);
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{(top&0x00ffffff)|0xf1000000,(color&0x00ffffff)|0xe9000000});
        d.setCornerRadius(px(radius));d.setStroke(px(1),themeIndex==2?0x337695ae:0x3379b9de);
        return d;
    }
    private GradientDrawable outlined(int color,int radius,boolean active){
        GradientDrawable d=glass(color,radius);
        d.setStroke(px(1),active?ACCENT:(themeIndex==2?0x2f748da6:0x306f9bb3));
        return d;
    }
    private GradientDrawable gradient(int first,int second,int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{first,second});d.setCornerRadius(px(radius));return d;}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setIncludeFontPadding(false);t.setFontFeatureSettings("kern");return t;
    }
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private LinearLayout.LayoutParams p(int w,int h){return new LinearLayout.LayoutParams(w<0?w:px(w),h<0?h:px(h));}
    private Button btn(String s){
        Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(13);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(FG);
        b.setPadding(px(10),0,px(10),0);b.setMinWidth(0);b.setMinHeight(0);
        b.setElevation(0);b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(themeIndex==2?0x22336c9c:0x397ad2ff),
            outlined(PANEL,18,false),shape(0xffffffff,18)));
        if(motionDuration(120)>0){
            android.animation.StateListAnimator states=new android.animation.StateListAnimator();
            android.animation.AnimatorSet pressed=new android.animation.AnimatorSet();
            pressed.playTogether(android.animation.ObjectAnimator.ofFloat(b,View.SCALE_X,.975f),
                android.animation.ObjectAnimator.ofFloat(b,View.SCALE_Y,.975f));
            pressed.setDuration(motionDuration(105));
            android.animation.AnimatorSet released=new android.animation.AnimatorSet();
            released.playTogether(android.animation.ObjectAnimator.ofFloat(b,View.SCALE_X,1f),
                android.animation.ObjectAnimator.ofFloat(b,View.SCALE_Y,1f));
            released.setDuration(motionDuration(175));
            states.addState(new int[]{android.R.attr.state_pressed},pressed);
            states.addState(new int[]{},released);
            b.setStateListAnimator(states);
        }
        return b;
    }
    private void space(ViewGroup g,int height){g.addView(new View(this),new ViewGroup.LayoutParams(1,px(height)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private void askCapsulePassword(){
        EditText pass=new EditText(this);pass.setSingleLine(true);pass.setInputType(129);
        pass.setHint("Пароль от 8 символов");
        new FontDialog.Builder(this).setTitle("Зашифрованная капсула избранного")
            .setMessage("Сохраняются только тексты избранных сообщений и даты. Фото, видео, голосовые, заметки и настройки НЕ включаются. Это НЕ резервная копия приложения. Для полного сохранения используй полную копию Telegram Archive.")
            .setView(pass).setNegativeButton("Отмена",null)
            .setPositiveButton("Создать файл",(d,which)->{
                char[] password=pass.getText().toString().toCharArray();
                if(password.length<8){java.util.Arrays.fill(password,(char)0);
                    toast("Минимум 8 символов — повтори создание");return;}
                if(pendingCapsulePassword!=null)java.util.Arrays.fill(pendingCapsulePassword,(char)0);
                pendingCapsulePassword=password;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE,"TelegramArchive_Favorites_Capsule.tcap");
                startActivityForResult(intent,CAPSULE_EXPORT_REQUEST);
            }).show();
    }
    private void exportCapsule(Uri file,char[] password){
        final MessageDb source=db;final String chatKey=activeKey;
        if(source==null||password==null)return;
        status.setText("Создаю зашифрованную текстовую капсулу…");
        worker.execute(()->{
            try{
                int count=FavoritesCapsule.save(this,file,source.getReadableDatabase(),password);
                runOnUiThread(()->{
                    if(chatKey.equals(activeKey))status.setText("Капсула сохранена: "+count+" сообщений. Медиа не включены.");
                });
            }catch(Exception e){runOnUiThread(()->status.setText("Ошибка капсулы: "+e.getMessage()));}
            finally{java.util.Arrays.fill(password,(char)0);}
        });
    }

    /** Read-only memories; matches month/day across different years, without exporting data. */
    private void showOnThisDay(int month,int day){
        final String chatKey=activeKey;
        final MessageDb source=db;
        if(source==null){toast("Переписка не открыта");return;}
        status.setText("Ищу воспоминания…");
        worker.execute(()->{
            try{
                List<OnThisDay.Entry> found=source.onThisDay(month,day,60);
                runOnUiThread(()->{
                    if(home||!chatKey.equals(activeKey))return;
                    status.setText("Найдено за этот день: "+found.size());
                    String[] labels=new String[found.size()];
                    for(int i=0;i<found.size();i++){
                        OnThisDay.Entry message=found.get(i);
                        labels[i]=message.date+" · "+message.author+"\n"+
                            (message.preview.isEmpty()?"(сообщение без текста)":message.preview);
                    }
                    AlertDialog.Builder dialog=new FontDialog.Builder(this)
                        .setTitle(String.format(Locale.ROOT,"В этот день · %02d.%02d",day,month))
                        .setMessage(found.isEmpty()?"В этой переписке на выбранную дату ничего не найдено.":null)
                        .setItems(labels,(d,which)->jump(found.get(which).id,""))
                        .setNegativeButton("Закрыть",(d,which)->{});
                    dialog.setNeutralButton("Другая дата",(d,which)->{
                        LocalDate today=LocalDate.now();
                        new DatePickerDialog(this,(picker,year,mo,dt)->showOnThisDay(mo+1,dt),
                            today.getYear(),month-1,day).show();
                    });
                    dialog.show();
                });
            }catch(Exception e){runOnUiThread(()->{
                if(chatKey.equals(activeKey))status.setText("Не удалось открыть воспоминания: "+e.getMessage());
            });}
        });
    }

    /** No media bytes are modified or read. A SAF resolution failure isn't necessarily a lost file. */
    private void showMediaHealth(){
        final String chatKey=activeKey;
        final MessageDb source=db;
        final DocumentFolder rootFolder=folder;
        if(source==null){toast("Переписка не открыта");return;}
        status.setText("Проверяю пути медиатеки (до 2000 файлов)…");
        worker.execute(()->{
            try{
                ChatMediaHealth.Report r=source.mediaHealth(rootFolder,ChatMediaHealth.DEFAULT_SCAN_LIMIT);
                runOnUiThread(()->{
                    if(home||!chatKey.equals(activeKey))return;
                    StringBuilder text=new StringBuilder();
                    text.append("Проверено путей: ").append(r.scanned).append(" из ").append(r.totalDistinct)
                        .append("\nДоступно: ").append(r.accessible)
                        .append("\nНе удалось найти или открыть: ").append(r.unresolved);
                    if(r.truncated)text.append("\nОстальные пути пока не проверялись.");
                    if(!r.unresolvedExamples.isEmpty()){
                        text.append("\n\nПримеры:\n");
                        for(String path:r.unresolvedExamples)text.append("• ").append(path).append('\n');
                    }
                    text.append("\nЭто только проверка доступности: файлы не читаются и не удаляются. ")
                        .append("Проблема доступа SAF может выглядеть как отсутствие файла.");
                    new FontDialog.Builder(this).setTitle("Здоровье медиатеки")
                        .setMessage(text.toString()).setPositiveButton("Готово",null).show();
                    status.setText("Проверка медиатеки завершена");
                });
            }catch(Exception e){runOnUiThread(()->{
                if(chatKey.equals(activeKey))status.setText("Ошибка проверки медиатеки: "+e.getMessage());
            });}
        });
    }

    /** Local-only scan of calendar pauses. A quiet period does not imply data loss. */
    private void showConversationPauses(){
        final String chatKey=activeKey;
        final MessageDb source=db;
        if(source==null){toast("Сначала открой переписку");return;}
        status.setText("Ищу паузы в истории переписки…");
        worker.execute(()->{
            final ConversationGapNavigator scan=new ConversationGapNavigator();
            String error=null;
            try(android.database.Cursor c=source.getReadableDatabase().rawQuery(
                    "SELECT id,date_text FROM messages ORDER BY id ASC LIMIT "+
                        (ConversationGapNavigator.MAX_SCAN+1),null)){
                while(c.moveToNext()&&scan.scanned()<ConversationGapNavigator.MAX_SCAN){
                    ChatTrends.Moment moment=ChatTrends.parse(c.getString(1));
                    scan.accept(c.getLong(0),moment==null?null:moment.day);
                }
            }catch(Exception e){error=e.getClass().getSimpleName();}
            final String failure=error;
            runOnUiThread(()->{
                if(isFinishing()||home||!chatKey.equals(activeKey))return;
                if(failure!=null){status.setText("Ошибка просмотра пауз: "+failure);return;}
                final java.util.List<ConversationGapNavigator.Gap> gaps=scan.gaps();
                final String[] items=new String[gaps.size()];
                for(int i=0;i<gaps.size();i++){
                    ConversationGapNavigator.Gap g=gaps.get(i);
                    items[i]=g.days+" дней · "+g.from+" → "+g.to;
                }
                String intro="Паузы между сообщениями, не доказательство пропусков экспорта. " +
                    "Проверено записей: "+scan.scanned()+"; дат без распознавания: "+scan.invalid()+
                    "; с обратным порядком: "+scan.outOfOrder()+"."+
                    (scan.scanned()>=ConversationGapNavigator.MAX_SCAN?
                        "\nПоказан лишь первый участок большого архива.":"");
                new FontDialog.Builder(this).setTitle("Навигатор пауз")
                    .setMessage(intro+(items.length==0?"\nПаузы от 7 дней не найдены.":""))
                    .setItems(items,(dialog,index)->{
                        ConversationGapNavigator.Gap g=gaps.get(index);
                        new FontDialog.Builder(this).setTitle("Границы паузы")
                            .setMessage(g.from+" → "+g.to+"\nПерейти к сообщению до или после паузы?")
                            .setPositiveButton("После",(d,w)->jump(g.toId,""))
                            .setNeutralButton("До",(d,w)->jump(g.fromId,""))
                            .setNegativeButton("Отмена",null).show();
                    }).setPositiveButton("Закрыть",null).show();
                status.setText("Навигатор пауз: просмотр завершён");
            });
        });
    }

    // The five discovery tools are strictly opt-in, local and bounded.
    private void discoveryStudio(){
        LinearLayout preview=column();preview.setPadding(px(18),px(18),px(18),px(10));
        preview.setBackground(gradient(PANEL,CHIP,22));
        TextView h=text("СТУДИЯ ОФОРМЛЕНИЯ 2.0",15,ACCENT);h.setTypeface(null,Typeface.BOLD);preview.addView(h);
        TextView a=text("  Привет! Это пример входящего сообщения.  ",14,FG);
        a.setPadding(px(12),px(13),px(12),px(13));a.setBackground(shape(INCOMING,bubbleRadius));
        LinearLayout.LayoutParams al=p(-2,-2);al.setMargins(0,px(17),px(38),px(8));preview.addView(a,al);
        TextView b=text("  А это твой ответ ✨  ",14,FG);
        b.setPadding(px(12),px(13),px(12),px(13));b.setBackground(shape(OUTGOING,bubbleRadius));
        LinearLayout.LayoutParams bl=p(-2,-2);bl.gravity=Gravity.END;preview.addView(b,bl);
        String[] choices={"Цветовая тема", "Фон переписки", "Аватары чата и людей", "Форма пузырьков", "Плотность сообщений", "Плавность и производительность"};
        new FontDialog.Builder(this).setTitle("Студия чата 2.0").setView(preview)
            .setItems(choices,(d,which)->{
                switch(which){case 0:appearanceDialog();break;case 1:wallpaperDialog();break;
                    case 2:avatarSelection();break;case 3:bubbleStyleDialog();break;
                    case 4:densityDialog();break;default:performanceDialog();}
            }).setNegativeButton("Закрыть",null).show();
    }
    private void discoveryMediaPro(){
        if(home){toast("Выбери переписку для её медиатеки");return;}
        String[] entries={"Галерея медиа · по типам", "Кинолента · по годам и месяцам", "Мои коллекции", "Проверка недостающих оригиналов"};
        new FontDialog.Builder(this).setTitle("Медиатека PRO")
            .setMessage("Ничего не скачивается. Фото, видео и голосовые читаются из выбранного локального экспорта.")
            .setItems(entries,(d,i)->{
                if(i==0){screen="media";showPage();}
                else if(i==1){screen="cinema";showPage();}
                else if(i==2)showCollectionPicker();else showMediaHealth();
            }).setNegativeButton("Закрыть",null).show();
    }
    private void discoveryYearPicker(String title,java.util.function.IntConsumer action){
        EditText year=new EditText(this);year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        year.setSingleLine(true);year.setText(String.valueOf(LocalDate.now().getYear()));
        year.setPadding(px(25),px(8),px(22),px(8));
        new FontDialog.Builder(this).setTitle(title).setMessage("Все данные остаются на устройстве")
            .setView(year).setNegativeButton("Отмена",null).setPositiveButton("Открыть",(d,w)->{
                try{int y=Integer.parseInt(year.getText().toString().trim());
                    if(y<1||y>9999)throw new NumberFormatException();action.accept(y);
                }catch(NumberFormatException e){toast("Нужен год от 1 до 9999");}
            }).show();
    }
    private void discoveryHistoryMap(){
        if(home){toast("Открой переписку");return;}
        discoveryYearPicker("Карта истории · год",year->{
            final String key=activeKey;final MessageDb source=db;
            status.setText("Строю локальную карту месяцев…");
            worker.execute(()->{
                try{
                    ParallelCalendars.YearData stats=source.calendarYear(year);
                    String[] months={"Январь","Февраль","Март","Апрель","Май","Июнь","Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
                    String[] labels=new String[12];for(int i=0;i<12;i++)labels[i]=months[i]+" · "+stats.months[i]+" сообщений";
                    runOnUiThread(()->{
                        if(isFinishing()||!key.equals(activeKey))return;
                        status.setText("Карта истории · "+year);
                        new FontDialog.Builder(this).setTitle("Карта истории · "+year)
                            .setMessage("Сообщений: "+stats.total()+" · активных дней: "+stats.activeDays()+
                                (stats.partial?"\nЧАСТИЧНО: достигнут лимит 300 000 сообщений.":""))
                            .setItems(labels,(d,month)->discoveryHistoryMonth(key,year,month+1))
                            .setNegativeButton("Закрыть",null).show();
                    });
                }catch(Exception e){runOnUiThread(()->toast("Карта истории: ошибка чтения"));}
            });
        });
    }
    private void discoveryHistoryMonth(String key,int year,int month){
        if(!canOpenQuoteArchive(key)||!key.equals(activeKey))return;
        final MessageDb source=db;
        worker.execute(()->{
            ArrayList<DiscoveryRules.Hit> hits=new ArrayList<>();
            String yyyy=String.format(Locale.ROOT,"%04d",year),mm=String.format(Locale.ROOT,"%02d",month);
            try(android.database.Cursor c=source.getReadableDatabase().rawQuery(
                "SELECT id,author,date_text,body FROM messages WHERE " +
                "substr(date_text,1,7)=? OR substr(date_text,4,7)=? ORDER BY id DESC LIMIT 45",
                new String[]{yyyy+"-"+mm,mm+"."+yyyy})){
                while(c.moveToNext()&&hits.size()<30){
                    ChatTrends.Moment dt=ChatTrends.parse(c.getString(2));
                    if(dt!=null&&dt.day.getYear()==year&&dt.day.getMonthValue()==month)
                        hits.add(new DiscoveryRules.Hit(key,activeName,c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
                }
            }catch(Exception ignored){}
            runOnUiThread(()->{
                if(isFinishing()||!key.equals(activeKey))return;
                if(hits.isEmpty()){toast("Сообщений за месяц не найдено");return;}
                String[] labels=new String[hits.size()];for(int i=0;i<labels.length;i++)labels[i]=hits.get(i).label();
                new FontDialog.Builder(this).setTitle("Сообщения · "+month+"/"+year)
                    .setItems(labels,(d,i)->jump(hits.get(i).id,""))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void discoveryTimeCapsule(){
        if(home){toast("Открой переписку");return;}
        discoveryYearPicker("Капсула времени · избранное за год",year->{
            final String key=activeKey;final MessageDb source=db;
            worker.execute(()->{
                ArrayList<DiscoveryRules.Hit> hits=new ArrayList<>();
                String yyyy=String.format(Locale.ROOT,"%04d",year);
                try(android.database.Cursor c=source.getReadableDatabase().rawQuery(
                    "SELECT m.id,m.author,m.date_text,m.body FROM messages m JOIN favorites f ON f.message_id=m.id " +
                    "WHERE substr(m.date_text,1,4)=? OR substr(m.date_text,7,4)=? ORDER BY m.id DESC LIMIT 110",
                    new String[]{yyyy,yyyy})){
                    while(c.moveToNext()&&hits.size()<80){
                        ChatTrends.Moment m=ChatTrends.parse(c.getString(2));
                        if(m!=null&&m.day.getYear()==year)
                            hits.add(new DiscoveryRules.Hit(key,activeName,c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
                    }
                }catch(Exception ignored){}
                runOnUiThread(()->{
                    if(isFinishing()||!key.equals(activeKey))return;
                    if(hits.isEmpty()){toast("Избранных сообщений за этот год не найдено");return;}
                    String[] labels=new String[hits.size()];for(int i=0;i<labels.length;i++)labels[i]=hits.get(i).label();
                    new FontDialog.Builder(this).setTitle("Капсула времени · "+year)
                        .setMessage("Это подборка уже отмеченных тобой сообщений. Оригиналы и медиа не копируются и не изменяются.")
                        .setItems(labels,(d,i)->jump(hits.get(i).id,""))
                        .setNegativeButton("Закрыть",null).show();
                });
            });
        });
    }
    private void discoverySmartSearch(){
        if(importing){toast("Дождись завершения импорта");return;}
        LinearLayout controls=column();controls.setPadding(px(22),px(8),px(22),px(5));
        EditText query=new EditText(this);query.setSingleLine(true);query.setHint("Слово или точная фраза");controls.addView(query);
        EditText author=new EditText(this);author.setSingleLine(true);author.setHint("Автор · необязательно");controls.addView(author);
        EditText year=new EditText(this);year.setSingleLine(true);year.setHint("Год · необязательно");year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);controls.addView(year);
        CheckBox onlyMedia=new CheckBox(this);onlyMedia.setText("Только сообщения с медиа");controls.addView(onlyMedia);
        CheckBox synonyms=new CheckBox(this);synonyms.setText("Индекс + локальные синонимы (рекомендуется)");
        synonyms.setChecked(true);controls.addView(synonyms);
        CheckBox broader=new CheckBox(this);
        broader.setText("Показывать и близкие темы (больше результатов, возможны ошибки)");
        broader.setChecked(false);controls.addView(broader);
        CheckBox fuzzyWords=new CheckBox(this);
        fuzzyWords.setText("Исправлять опечатки (медленнее; только по отдельной галочке)");
        fuzzyWords.setChecked(false);controls.addView(fuzzyWords);
        synonyms.setOnCheckedChangeListener((buttonView,isChecked)->{
            broader.setEnabled(isChecked);fuzzyWords.setEnabled(isChecked);
            if(!isChecked){broader.setChecked(false);fuzzyWords.setChecked(false);}
        });
        TextView explanation=text("Например: благодарю → спасибо; покупаю машину → купил тачку. " +
            "Катана и меч — не точные синонимы: включай широкий поиск отдельно. " +
            "По отдельной галочке «Опечатки» попробует плахо → плохо через локальный словарь слов чата. " +
            "Не исправляет все ошибки в любом слове. Все результаты — настоящие сообщения с переходом к оригиналу.",12,SUB);
        controls.addView(explanation);
        new FontDialog.Builder(this).setTitle("Умный поиск по всем чатам")
            .setMessage("Текст, автор, год и медиа. Работает офлайн и не объединяет переписки.")
            .setView(controls).setNegativeButton("Отмена",null)
            .setPositiveButton("Найти",(d,w)->{
                String q=query.getText().toString().trim(),a=author.getText().toString().trim();
                Integer y=null;
                try{if(!year.getText().toString().trim().isEmpty()){
                    y=Integer.parseInt(year.getText().toString().trim());
                    if(y<1||y>9999)throw new NumberFormatException();}
                }catch(NumberFormatException e){toast("Неверный год");return;}
                if(q.length()>120||a.length()>120){toast("Запрос слишком длинный (максимум 120 символов)");return;}
                if(q.codePointCount(0,q.length())<2&&a.codePointCount(0,a.length())<2){
                    toast("Введи минимум 2 символа текста или имени автора");return;}
                discoverySearchRun(q,a,y,onlyMedia.isChecked(),synonyms.isChecked(),broader.isChecked(),fuzzyWords.isChecked());
            }).show();
    }
    private void discoverySearchRun(String query,String author,Integer year,boolean media,boolean indexed,boolean broaden,boolean typoMode){
        final OfflineSynonymSearch.Plan plan;
        if(indexed&&!query.isEmpty()){
            try{plan=OfflineSynonymSearch.make(query,true,broaden);}
            catch(IllegalArgumentException bad){toast(bad.getMessage());return;}
        }else plan=null;
        final ArrayList<String> keys=new ArrayList<>(),names=new ArrayList<>();
        for(int i=0;i<archives.length()&&keys.size()<25;i++){
            JSONObject item=archives.optJSONObject(i);
            if(item!=null&&canOpenQuoteArchive(item.optString("key"))){
                keys.add(item.optString("key"));names.add(item.optString("name","Чат"));
            }
        }
        status.setText("Ищу в "+keys.size()+" локальных чатах…");
        worker.execute(()->{
            ArrayList<DiscoveryRules.Hit> result=new ArrayList<>();
            ArrayList<DiscoveryRules.Hit> earliestCandidates=new ArrayList<>();
            int unavailable=0;
            final java.util.Map<String,OfflineSynonymSearch.Plan> perChatPlans=new java.util.HashMap<>();
            boolean unsupportedFuzzy=false;
            // Indexed mode searches the ENTIRE SQLite FTS4 index, not only the newest 100k IDs.
            // Legacy substring mode keeps its old bounded scan and is always labelled partial.
            StringBuilder where=new StringBuilder(plan!=null?" WHERE messages.id IN (SELECT docid FROM messages_fts WHERE messages_fts MATCH ?)":
                " WHERE messages.id > (SELECT COALESCE(MAX(id),0)-100000 FROM messages)");
            ArrayList<String> params=new ArrayList<>();
            if(plan!=null)params.add(plan.fts);
            else if(!query.isEmpty()){
                where.append(" AND (body_fold LIKE ? ESCAPE '\\' OR author_fold LIKE ? ESCAPE '\\')");
                String pat=DiscoveryRules.likePattern(query);params.add(pat);params.add(pat);
            }
            if(!author.isEmpty()){where.append(" AND author_fold LIKE ? ESCAPE '\\'");params.add(DiscoveryRules.likePattern(author));}
            if(year!=null){
                where.append(" AND (substr(date_text,1,4)=? OR substr(date_text,7,4)=?)");
                params.add(String.format(Locale.ROOT,"%04d",year));params.add(String.format(Locale.ROOT,"%04d",year));
            }
            if(media)where.append(" AND EXISTS (SELECT 1 FROM attachments a WHERE a.message_id=messages.id)");
            String sql="SELECT id,author,date_text,body FROM messages"+where+" ORDER BY id DESC LIMIT 36";
            for(int i=0;i<keys.size();i++){
                String key=keys.get(i);
                try{
                    java.io.File file=dualChatDbFile(key);
                    if(!file.isFile()){unavailable++;continue;}
                    try(android.database.sqlite.SQLiteDatabase d=android.database.sqlite.SQLiteDatabase.openDatabase(
                        file.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                        OfflineSynonymSearch.Plan chatPlan=plan;
                        java.util.ArrayList<String> localParams=new java.util.ArrayList<>(params);
                        if(typoMode&&indexed&&plan!=null){
                            IndexedTypoLookup.Result fuzzy=IndexedTypoLookup.collect(d,plan.terms);
                            chatPlan=OfflineSynonymSearch.make(query,true,broaden,fuzzy.suggestions);
                            if(fuzzy.notice.contains("недоступен"))unsupportedFuzzy=true;
                            localParams.set(0,chatPlan.fts);
                        }
                        if(chatPlan!=null)perChatPlans.put(key,chatPlan);
                        try(android.database.Cursor c=d.rawQuery(sql,localParams.toArray(new String[0]))){
                        while(c.moveToNext()){
                            result.add(new DiscoveryRules.Hit(key,names.get(i),c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
                        }
                        }
                        // Separate earliest lookup is necessary: the main UI shows recent hits.
                        // FTS4 matches older messages without scanning the text on the UI thread.
                        if(plan!=null){
                            String firstSql="SELECT id,author,date_text,body FROM messages"+where+" ORDER BY id ASC LIMIT 1";
                            try(android.database.Cursor first=d.rawQuery(firstSql,localParams.toArray(new String[0]))){
                                if(first.moveToNext())earliestCandidates.add(new DiscoveryRules.Hit(
                                    key,names.get(i),first.getLong(0),first.getString(1),first.getString(2),first.getString(3)));
                            }
                        }
                    }
                }catch(Exception e){unavailable++;}
            }
            final int misses=unavailable;
            final boolean fuzzyUnavailable=unsupportedFuzzy;
            DiscoveryRules.Hit candidate=null;
            // Earliest dates from distinct chats are compared only if parsable.
            for(DiscoveryRules.Hit hit:earliestCandidates){
                ChatTrends.Moment t=ChatTrends.parse(hit.date);
                if(t==null)continue;
                if(candidate==null||t.day.isBefore(ChatTrends.parse(candidate.date).day))candidate=hit;
            }
            final DiscoveryRules.Hit earliest=candidate;
            result.sort((a,b)->{
                ChatTrends.Moment da=ChatTrends.parse(a.date),dbDate=ChatTrends.parse(b.date);
                if(da==null&&dbDate==null)return Long.compare(b.id,a.id);
                if(da==null)return 1;if(dbDate==null)return -1;
                int compare=dbDate.day.compareTo(da.day);
                return compare!=0?compare:Long.compare(b.id,a.id);
            });
            if(result.size()>80)result.subList(80,result.size()).clear();
            runOnUiThread(()->{
                if(isFinishing())return;
                status.setText("Поиск завершён · показано "+result.size()+" результатов (список ограничен)");
                if(result.isEmpty()){toast("Совпадений не найдено"+(misses>0?" · недоступно чатов: "+misses:"")+
                    (fuzzyUnavailable?" · поиск опечаток не поддержан":""));return;}
                String[] labels=new String[result.size()];
                for(int i=0;i<labels.length;i++){
                    DiscoveryRules.Hit hit=result.get(i);
                    OfflineSynonymSearch.Plan explained=perChatPlans.get(hit.key);
                    labels[i]=hit.name+" · "+(explained==null?"":explained.sourceMatch(hit.body)+" · ")+hit.label();
                }
                AlertDialog.Builder foundDialog=new FontDialog.Builder(this).setTitle("Поиск · показано "+result.size())
                    .setMessage((plan==null?"Обычный поиск: проверены последние 100 000 ID на чат. ":
                        "Полный FTS4 индекс каждого доступного чата; "+plan.description+". ")+
                        "Показаны не более 36 последних совпадений из каждого чата и 80 всего. "+
                        (keys.size()>=25?"Не более 25 чатов за запрос. ":"")+
                        (misses>0?"Недоступно чатов: "+misses+". ":"")+
                        (fuzzyUnavailable?"На некоторых устройствах расширение опечаток недоступно. ":"")+
                        (typoMode?"Опечатки ищутся только среди индексированных слов с похожим началом; не гарантируется полнота. ":"")+
                        (earliest!=null?"\nСамое раннее подтверждённое совпадение по дате: "+earliest.date+" · "+earliest.name+".":"")+
                        " Список ограничен, поэтому не все совпадения показаны.")
                    .setItems(labels,(d,index)->{
                        DiscoveryRules.Hit item=result.get(index);
                        if(!canOpenQuoteArchive(item.key)){toast("Чат отсутствует");return;}
                        openSavedQuote(item.id,item.key);
                    });
                if(earliest!=null)foundDialog.setNeutralButton("Перейти к раннему",(d,w)->
                    openSavedQuote(earliest.id,earliest.key));
                foundDialog.setNegativeButton("Закрыть",null).show();
            });
        });
    }

    /** Drawer sections expose one clear destination per action and avoid repeating settings. */
    private void showSideMenu(){
        final Dialog dialog=new FontDialog(this,android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(0x99040912);
        LinearLayout pane=column();pane.setPadding(px(18),px(27),px(17),px(14));
        pane.setBackground(gradient(themeIndex==2?0xfaffffff:0xfa1a3045,
            themeIndex==2?0xffe8edf4:0xff101c2d,24));pane.setElevation(px(12));
        int width=Math.min(px(348),getResources().getDisplayMetrics().widthPixels-px(38));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,-1,Gravity.START);
        overlay.addView(pane,lp);
        TextView app=text("TELEGRAM ARCHIVE",18,FG);app.setTypeface(null,Typeface.BOLD);
        app.setPadding(px(5),px(7),0,px(4));pane.addView(app);
        TextView desc=text("Приватная коллекция · офлайн",12,SUB);
        desc.setPadding(px(5),0,0,px(16));pane.addView(desc);
        ScrollView menuScroll=new ScrollView(this);menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.setFillViewport(false);pane.addView(menuScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout items=column();menuScroll.addView(items);
        if(!home)sideMenuItem(items,dialog,pane,"⌂  Все чаты","К списку переписок",()->{
            home=true;screen="chat";showPage();});
        sideMenuItem(items,dialog,pane,"📝  Личные заметки","Заметки из разных переписок",()->{
            home=false;screen="saved";renderSaved();});
        sideMenuSection(items,"Архивы и резервные копии",false,group->{
            sideMenuItem(group,dialog,pane,"＋  Импортировать чат","Папка Telegram, ZIP или RAR",this::chooseFolder);
            sideMenuItem(group,dialog,pane,"⇧  Создать резервную копию","Чаты, настройки и при выборе — медиа",this::backupPicker);
            sideMenuItem(group,dialog,pane,"✓  Проверить копию","Контроль целостности без изменений",this::verifyBackupPicker);
            sideMenuItem(group,dialog,pane,"◈  Пробное восстановление","Тест без изменения переписок",this::dryRunPicker);
            sideMenuItem(group,dialog,pane,"▤  Квитанция переноса","Сверка последнего восстановления",this::showLastRestoreReceipt);
            sideMenuItem(group,dialog,pane,"⇩  Восстановить архив","Импорт резервной копии",this::restorePicker);
            sideMenuItem(group,dialog,pane,"✧  Капсула избранного","Только зашифрованные тексты, не полный бэкап",()->{
                if(home)toast("Сначала открой переписку");else askCapsulePassword();});
        });
        sideMenuSection(items,"Инструменты переписки",false,group->{
            sideMenuItem(group,dialog,pane,"▤  Кинолента медиа","По годам и месяцам, без сетевых запросов",()->{
                if(home){toast("Сначала открой переписку");return;}screen="cinema";showPage();});
            sideMenuItem(group,dialog,pane,"◷  Хронология отношений","Твои этапы и сравнение активности · вручную",()->{
                if(home){toast("Сначала открой переписку");return;}screen="chronology";showPage();});
            sideMenuItem(group,dialog,pane,"⇄  Сравнение диалогов","Два чата · месяцы, медиа, участники",()->{
                if(home){toast("Сначала открой переписку");return;}chooseDualChat("dual_compare");});
            sideMenuItem(group,dialog,pane,"◷  Воспоминания двух чатов","Общая временная линия двух переписок",()->{
                if(home){toast("Сначала открой переписку");return;}chooseDualChat("dual_memories");});
            sideMenuItem(group,dialog,pane,"▥  Анализ диалога","Графики, активность и сравнения",()->{
                if(home){toast("Сначала открой переписку");return;}screen="analytics";showPage();});
            sideMenuItem(group,dialog,pane,"⌁  В этот день","Сообщения в эту дату разных лет",()->{
                if(home){toast("Сначала открой переписку");return;}
                LocalDate today=LocalDate.now();showOnThisDay(today.getMonthValue(),today.getDayOfMonth());});
            sideMenuItem(group,dialog,pane,"◎  Здоровье медиатеки","Проверка отсутствующих файлов",()->{
                if(home)toast("Сначала открой переписку");else showMediaHealth();});
            sideMenuItem(group,dialog,pane,"◌  Навигатор пауз","Промежутки в истории · без изменения файлов",()->{
                if(home)toast("Сначала открой переписку");else showConversationPauses();});
        });
        sideMenuSection(items,"Открытия · новые возможности",false,group->{
            sideMenuItem(group,dialog,pane,"⌕  Умный поиск по всем чатам","Текст, автор, год, медиа · только локально",this::discoverySmartSearch);
            sideMenuItem(group,dialog,pane,"✦  Студия оформления 2.0","Предпросмотр, темы, обои и аватары",this::discoveryStudio);
            sideMenuItem(group,dialog,pane,"▦  Медиатека PRO","Галерея, кинолента, коллекции, проверка файлов",this::discoveryMediaPro);
            sideMenuItem(group,dialog,pane,"◷  Карта истории","Месяцы, активность и оригиналы сообщений",this::discoveryHistoryMap);
            sideMenuItem(group,dialog,pane,"✧  Капсула времени","Избранное за выбранный год",this::discoveryTimeCapsule);
        });
        sideMenuSection(items,"Оформление и персонализация",false,group->{
            sideMenuItem(group,dialog,pane,"◐  Темы и оформление","Цвет, плотность и плавность",this::appearanceDialog);
            sideMenuItem(group,dialog,pane,"✧  Стеклянные карточки","Лёгкая прозрачность без тяжёлого размытия",this::glassDialog);
            sideMenuItem(group,dialog,pane,"⚡  Умная производительность","Автоматическая экономия памяти",this::performanceDialog);
            sideMenuItem(group,dialog,pane,"▧  Обои переписки","Свой фон, узоры и прозрачность",this::wallpaperDialog);
            sideMenuItem(group,dialog,pane,"◉  Аватары","Один аватар нескольким участникам",this::avatarSelection);
            sideMenuItem(group,dialog,pane,"⚙  Настройки переписки","Имя, цвета и пузырьки",this::settingsMenu);
        });
        sideMenuItem(items,dialog,pane,"Aa  Шрифты · изменить везде","Системный, классика, свой TTF/OTF",this::showFontStudio);
        sideMenuItem(items,dialog,pane,"ⓘ  Помощь и инструкция","Пошаговая инструкция внутри приложения",this::showHelp);
        sideMenuItem(items,dialog,pane,"▶  Учебная переписка","Практика без изменений настоящих данных",
            ()->startActivity(new Intent(this,DemoChatActivity.class)));
        sideMenuItem(items,dialog,pane,"⌁  Диагностика","Создать обезличенный технический отчёт",this::shareDiagnostic);
        TextView label=text("Telegram Archive · 0.42 QA\nЛокально · Без аккаунта · Без серверов",11,SUB);
        label.setPadding(px(5),px(12),0,px(5));pane.addView(label);
        overlay.setOnClickListener(v->closeDrawer(dialog,pane,null));pane.setOnClickListener(v->{});
        dialog.setContentView(overlay);dialog.show();Window w=dialog.getWindow();if(w!=null)w.setLayout(-1,-1);
        pane.setTranslationX(-width);pane.animate().translationX(0).setDuration(motionDuration(260))
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void closeDrawer(Dialog dialog,View pane,Runnable next){
        if(!dialog.isShowing())return;
        pane.animate().cancel();
        pane.animate().translationX(-pane.getWidth()).alpha(.85f).setDuration(motionDuration(210))
            .withEndAction(()->{dialog.dismiss();if(next!=null)next.run();}).start();
    }
    private void sideMenuSection(LinearLayout parent,String name,boolean opened,
                                 java.util.function.Consumer<LinearLayout> populate){
        LinearLayout group=column();parent.addView(group,p(-1,-2));
        TextView title=text((opened?"▾  ":"▸  ")+name,13,ACCENT);
        title.setTypeface(null,Typeface.BOLD);title.setPadding(px(10),px(12),px(6),px(12));
        title.setBackground(shape(themeIndex==2?0x55c0dbe9:0x55385069,12));
        group.addView(title,p(-1,-2));
        LinearLayout contents=column();contents.setPadding(0,px(6),0,px(7));group.addView(contents);
        populate.accept(contents);contents.setVisibility(opened?View.VISIBLE:View.GONE);
        title.setOnClickListener(v->{boolean expand=contents.getVisibility()!=View.VISIBLE;
            title.setText((expand?"▾  ":"▸  ")+name);
            contents.setVisibility(expand?View.VISIBLE:View.GONE);
            if(expand){contents.setAlpha(0f);contents.animate().alpha(1f)
                .setDuration(motionDuration(170)).start();}
        });
        space(group,5);
    }
    private void sideMenuItem(LinearLayout parent,Dialog dialog,View pane,
                              String heading,String detail,Runnable action){
        LinearLayout item=column();item.setPadding(px(13),px(11),px(9),px(11));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2e5aaad8),outlined(PANEL,15,false),null));
        LinearLayout.LayoutParams params=p(-1,-2);params.bottomMargin=px(5);parent.addView(item,params);
        TextView name=text(heading,15,FG);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));item.addView(name);
        TextView summary=text(detail,12,SUB);summary.setPadding(0,px(4),0,0);item.addView(summary);
        item.setOnClickListener(v->closeDrawer(dialog,pane,action));
    }
    private void glassDialog(){
        new FontDialog.Builder(this).setTitle("Полупрозрачные карточки")
            .setMessage("Лёгкий стеклянный градиент без тяжёлого размытия. На слабом устройстве автоматический режим упростит оформление. Исходные чаты не изменяются.")
            .setSingleChoiceItems(new String[]{"Стеклянный стиль", "Обычные карточки"},glassStyle?0:1,
                (d,selection)->{glassStyle=selection==0;getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("appearance_glass",glassStyle).apply();d.dismiss();showPage();})
            .setNegativeButton("Закрыть",null).show();
    }
    private void shareDiagnostic(){
        // Privacy boundary: only app/build, SDK and coarse memory/performance
        // fields. Never include chat names, paths, IDs, messages or stack traces.
        String report=DiagnosticReport.render("0.36-premium-rc",Build.VERSION.SDK_INT,
            memoryClassMb(),effectivePerformanceMode(),reduceMotion,glassStyle);
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");send.putExtra(Intent.EXTRA_SUBJECT,"Telegram Archive — диагностика");
        send.putExtra(Intent.EXTRA_TEXT,report);
        startActivity(Intent.createChooser(send,"Сохранить обезличенный отчёт"));
    }
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)thumbs.evictAll();
        else if(level>=android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)thumbs.trimToSize(thumbs.maxSize()/2);
    }
    // Explicit navigation stack: category -> shelf -> one chapter. No giant accordion.
    private boolean helpReturnHome=true;
    private String helpReturnScreen="chat";
    private int helpCategory=-1,helpShelf=-1,helpChapter=-1;
    /** Global typography: one setting for chats, menus, videos and the demo. */
    private void showFontStudio(){
        int selected=FontManager.choiceIndex(this);
        String[] options=FontManager.LABELS;
        new FontDialog.Builder(this).setTitle("Шрифты · оформление всего приложения")
            .setMessage("Системный шрифт выбран по умолчанию. Можно выбрать готовый стиль или добавить свой TTF/OTF. Настройки действуют только внутри Telegram Archive. Для необычных шрифтов убедись, что читается русский текст.")
            .setSingleChoiceItems(options,selected,(dialog,index)->{
                dialog.dismiss();
                if(index==8&&!FontManager.customFile(this).isFile()){
                    new FontDialog.Builder(this).setTitle("Свой шрифт")
                        .setMessage("Сначала выбери локальный .ttf или .otf файл. Он не передаётся в интернет и проверяется перед установкой.")
                        .setPositiveButton("Выбрать файл",(d,w)->pickFontFile())
                        .setNegativeButton("Отмена",null).show();return;
                }
                showFontPreview(index);
            })
            .setNeutralButton("Добавить свой TTF/OTF",(d,w)->pickFontFile())
            .setNegativeButton("Закрыть",null).show();
    }
    private void showFontPreview(int index){
        String key=FontPolicy.KEYS[index];
        TextView sample=new TextView(this);
        sample.setText("Telegram Archive\nСъешь ещё этих мягких французских булок\n1234567890 · Жизнь — это мгновения");
        sample.setTextColor(0xff122639);sample.setTextSize(18);
        sample.setPadding(px(16),px(16),px(16),px(16));
        android.graphics.Typeface chosen;
        switch(key){
            case "serif":chosen=Typeface.SERIF;break;
            case "mono":chosen=Typeface.MONOSPACE;break;
            case "sans":chosen=Typeface.SANS_SERIF;break;
            case "condensed":chosen=Typeface.create("sans-serif-condensed",0);break;
            case "casual":chosen=Typeface.create("casual",0);break;
            case "light":chosen=Typeface.create("sans-serif-light",0);break;
            case "medium":chosen=Typeface.create("sans-serif-medium",0);break;
            case "custom":chosen=Typeface.createFromFile(FontManager.customFile(this));break;
            default:chosen=Typeface.DEFAULT;
        }
        sample.setTag("ta-font-preview");
        sample.setTypeface(chosen);
        new FontDialog.Builder(this).setTitle(FontManager.LABELS[index])
            .setMessage("Предпросмотр. При применении обновятся сообщения, меню, кнопки и справка.")
            .setView(sample).setNegativeButton("Другой шрифт",(d,w)->showFontStudio())
            .setPositiveButton("Применить",(d,w)->{
                FontManager.select(this,key);
                recreate();
            }).show();
    }
    private void pickFontFile(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,PICK_FONT);
    }
    private void handleFontImport(Uri uri){
        toast("Проверяю шрифт…");
        worker.execute(()->{
            String error=null;
            try{FontManager.importFile(this,uri);}catch(Exception e){error=e.getMessage();}
            final String failure=error;
            runOnUiThread(()->{
                if(isFinishing())return;
                if(failure!=null)new FontDialog.Builder(this).setTitle("Шрифт не установлен")
                    .setMessage(failure+". Предыдущий шрифт сохранён.")
                    .setPositiveButton("Понятно",null).show();
                else{toast("Шрифт установлен · Telegram Archive");recreate();}
            });
        });
    }

    private void showHelp(){
        if(!"help".equals(screen)){
            helpReturnHome=home;
            helpReturnScreen=screen;
        }
        helpCategory=-1;helpShelf=-1;helpChapter=-1;
        screen="help";renderHelpPage();
    }
    private boolean helpStepBack(){
        if(helpChapter!=-1){helpChapter=-1;renderHelpPage();return true;}
        if(helpShelf!=-1){helpShelf=-1;renderHelpPage();return true;}
        if(helpCategory!=-1){helpCategory=-1;renderHelpPage();return true;}
        return false;
    }
    private void leaveHelp(){
        if(helpStepBack())return;
        ++loadToken;
        home=helpReturnHome;
        screen=helpReturnScreen;
        showPage();
    }
    private void helpNavCard(String header,String subtitle,Runnable action){
        LinearLayout box=column();box.setPadding(px(17),px(14),px(17),px(15));
        box.setBackground(new RippleDrawable(ColorStateList.valueOf(0x447ad2ff),
            outlined(PANEL,19,false),shape(0xffffffff,19)));
        LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(7),px(4),px(7),px(7));list.addView(box,lp);
        TextView label=text(header.replaceFirst("^[0-9]+[.] *", "")+"   ›",16,FG);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));box.addView(label);
        if(subtitle!=null&&!subtitle.isEmpty()){
            TextView explanation=text(subtitle,12,SUB);explanation.setPadding(0,px(6),0,0);box.addView(explanation);
        }
        box.setContentDescription(header+". "+subtitle);
        box.setOnClickListener(v->{action.run();});
    }
    private void addHelpIllustration(int chapter){
        try(java.io.InputStream input=getAssets().open("help/"+chapter+".png")){
            final Bitmap bitmap=BitmapFactory.decodeStream(input);if(bitmap==null)return;
            TextView caption=text("Схема, не скриншот · нажми, чтобы увеличить",12,SUB);
            caption.setPadding(px(14),px(8),px(14),px(8));list.addView(caption);
            ImageView thumbnail=new ImageView(this);thumbnail.setImageBitmap(bitmap);
            thumbnail.setAdjustViewBounds(true);thumbnail.setContentDescription("Схема действий. Открыть крупно");
            list.addView(thumbnail,p(-1,-2));thumbnail.setOnClickListener(v->{
                LinearLayout body=column();body.setBackgroundColor(BG);
                TextView hint=text("Схема · увеличивай двумя пальцами",14,FG);body.addView(hint);
                ImageView large=new ImageView(this);large.setImageBitmap(bitmap);large.setScaleType(ImageView.ScaleType.FIT_CENTER);
                body.addView(large,new LinearLayout.LayoutParams(-1,0,1));
                android.view.ScaleGestureDetector zoom=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
                    @Override public boolean onScale(android.view.ScaleGestureDetector detector){float scale=Math.max(1f,Math.min(3f,large.getScaleX()*detector.getScaleFactor()));large.setScaleX(scale);large.setScaleY(scale);return true;}
                });
                large.setOnTouchListener(new View.OnTouchListener(){float x,y;public boolean onTouch(View view,MotionEvent event){
                    zoom.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_DOWN){x=event.getRawX();y=event.getRawY();}
                    if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&!zoom.isInProgress()){
                        large.setTranslationX(PhotoZoomMath.clamp(large.getTranslationX()+event.getRawX()-x,(large.getScaleX()-1)*large.getWidth()/2));
                        large.setTranslationY(PhotoZoomMath.clamp(large.getTranslationY()+event.getRawY()-y,(large.getScaleY()-1)*large.getHeight()/2));
                        x=event.getRawX();y=event.getRawY();
                    }return true;
                }});
                Button reset=btn("Сбросить масштаб");body.addView(reset);reset.setOnClickListener(w->{large.setScaleX(1);large.setScaleY(1);large.setTranslationX(0);large.setTranslationY(0);});
                android.app.Dialog dialog=new FontDialog(this);Button back=btn("Назад к инструкции");body.addView(back);back.setOnClickListener(w->dialog.dismiss());
                dialog.setContentView(body);dialog.show();if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,-1);
            });
        }catch(java.io.IOException ignored){}
    }
    private void renderHelpPage(){
        ++loadToken;
        list.removeAllViews();list.setBackgroundColor(BG);
        title.setText("Помощь · Telegram Archive");
        subtitle.setText("Пошаговая навигация · офлайн");
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        savedComposer.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        playerStrip.setVisibility(View.GONE);pinnedBar.removeAllViews();
        filterBar.removeAllViews();searchArea.removeAllViews();
        status.setText("Справочник не изменяет твои данные");
        TextView crumb=text(helpChapter!=-1?"ИНСТРУКЦИЯ":helpShelf!=-1?"ПОДРАЗДЕЛ":helpCategory!=-1?"РАЗДЕЛ":"СПРАВОЧНИК",11,ACCENT);
        crumb.setTypeface(null,Typeface.BOLD);crumb.setPadding(px(12),px(16),0,px(5));list.addView(crumb);
        if(helpCategory!=-1){
            Button back=btn("‹ Назад к "+(helpChapter!=-1?"темам":helpShelf!=-1?"разделу":"всем темам"));
            LinearLayout.LayoutParams backLp=p(-1,45);backLp.setMargins(px(7),px(4),px(7),px(10));list.addView(back,backLp);
            back.setOnClickListener(v->helpStepBack());
        }
        if(helpChapter!=-1){
            HelpContent.Chapter chapter=HelpContent.all().get(helpChapter-1);
            TextView heading=text(chapter.title.replaceFirst("^[0-9]+[.] *", ""),21,FG);heading.setTypeface(null,Typeface.BOLD);
            heading.setPadding(px(14),px(7),px(12),px(12));list.addView(heading);
            TextView note=text("Инструкция для офлайн-версии. Выполняй шаги по порядку; если пункт отсутствует, проверь версию приложения.",12,SUB);
            note.setPadding(px(14),0,px(13),px(13));list.addView(note);
            addHelpIllustration(helpChapter);
            String[] paragraphs=chapter.description.split("\n");
            for(String paragraph:paragraphs){
                TextView line=text(paragraph,15,FG);line.setLineSpacing(px(4),1.06f);
                line.setPadding(px(16),px(13),px(16),px(13));line.setBackground(glass(PANEL,17));
                LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(7),px(4),px(7),px(8));list.addView(line,lp);
            }
        }else if(helpShelf!=-1){
            HelpCatalog.Shelf shelf=HelpCatalog.GROUPS[helpCategory].shelves[helpShelf];
            TextView heading=text(shelf.title,20,FG);heading.setPadding(px(12),px(7),px(12),px(12));list.addView(heading);
            for(int id:shelf.chapterIds){
                HelpContent.Chapter chapter=HelpContent.all().get(id-1);
                helpNavCard(chapter.title,"Открыть подробное объяснение",()->{helpChapter=id;renderHelpPage();});
            }
        }else if(helpCategory!=-1){
            HelpCatalog.Group group=HelpCatalog.GROUPS[helpCategory];
            TextView heading=text(group.title,20,FG);heading.setPadding(px(12),px(7),px(12),px(12));list.addView(heading);
            for(HelpCatalog.Shelf shelf:group.shelves){
                TextView section=text(shelf.title,14,ACCENT);section.setPadding(px(14),px(12),px(14),px(4));list.addView(section);
                for(int id:shelf.chapterIds){
                    HelpContent.Chapter chapter=HelpContent.all().get(id-1);
                    helpNavCard(chapter.title,"Шаги, результат и ограничения",()->{helpShelf=-1;helpChapter=id;renderHelpPage();});
                }
            }
        }else{
            TextView heading=text("Один вопрос — один понятный маршрут",21,FG);
            heading.setTypeface(null,Typeface.BOLD);heading.setPadding(px(12),px(6),px(12),px(6));list.addView(heading);
            TextView intro=text("Выбери одну из тем. Внутри — список тем и пошаговые инструкции. Всего два перехода до ответа.",13,SUB);
            intro.setPadding(px(12),0,px(12),px(14));list.addView(intro);
            helpNavCard("▶ Учебная переписка · попробуй сам",
                "Демо без личных данных: тексты, фото, видео, кружок, музыка, голосовое, GIF, стикер. Закрепляй, отвечай, цитируй.",
                ()->startActivity(new Intent(this,DemoChatActivity.class)));
            for(int i=0;i<HelpCatalog.GROUPS.length;i++){
                final int index=i;HelpCatalog.Group group=HelpCatalog.GROUPS[i];
                helpNavCard(group.title,group.subtitle,()->{helpCategory=index;renderHelpPage();});
            }
            EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);
            search.setHint("Найти инструкцию…");search.setTextColor(FG);search.setHintTextColor(SUB);
            search.setBackground(glass(PANEL,16));search.setPadding(px(14),px(10),px(14),px(10));
            LinearLayout.LayoutParams searchLp=p(-1,-2);searchLp.setMargins(px(7),px(13),px(7),px(9));list.addView(search,searchLp);
            LinearLayout results=column();list.addView(results,p(-1,-2));
            search.addTextChangedListener(new android.text.TextWatcher(){
                public void beforeTextChanged(CharSequence t,int a,int b,int c){}
                public void onTextChanged(CharSequence t,int a,int b,int c){
                    results.removeAllViews();String q=t.toString().trim();if(q.isEmpty())return;
                    List<HelpContent.Chapter> matches=HelpContent.find(q);
                    for(HelpContent.Chapter chapter:matches){
                        int id=HelpContent.all().indexOf(chapter)+1;
                        LinearLayout card=column();card.setPadding(px(12),px(8),px(12),px(8));
                        TextView name=text("⌕ "+chapter.title,14,ACCENT);card.addView(name);
                        results.addView(card,p(-1,-2));
                        card.setOnClickListener(v->{int[] path=HelpCatalog.findChapter(id);
                            if(path==null)return;helpCategory=path[0];helpShelf=-1;helpChapter=id;renderHelpPage();});
                    }
                    if(matches.isEmpty()){TextView empty=text("Совпадений нет",13,SUB);results.addView(empty);}
                }
                public void afterTextChanged(android.text.Editable e){}
            });
        }
        TextView privacy=text("Без интернета и отправки данных. Не удаляй исходный экспорт или старое приложение до проверки полной резервной копии.",12,SUB);
        privacy.setPadding(px(14),px(17),px(14),px(28));list.addView(privacy);
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private void animateEntrance(){
        if(list==null)return;
        list.animate().cancel();
        // Avoid replaying entrance animations during pagination and background refresh.
        String state=home?"home":screen;
        String archive=home?"":activeKey;
        boolean changed=!state.equals(lastAnimatedScreen)||!archive.equals(lastAnimatedArchive);
        lastAnimatedScreen=state;lastAnimatedArchive=archive;
        if(!changed||motionDuration(210)==0){list.setAlpha(1f);list.setTranslationY(0);return;}
        list.setAlpha(.95f);list.setTranslationY(px(5));
        list.animate().alpha(1f).translationY(0).setDuration(motionDuration(180))
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    private void renderHome(){
        list.setBackgroundColor(BG);
        savedComposer.setVisibility(View.GONE);
        ++loadToken;list.removeAllViews();pinnedBar.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();
        verticalTimeline.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);bottomNav.setVisibility(View.GONE);
        menuButton.setVisibility(View.VISIBLE);navBack.setVisibility(View.GONE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE); // Only the large bottom Add Chat button.
        title.setText("Мои переписки");subtitle.setText("Твои сообщения · в одном месте");
        status.setText("Личный архив  ·  Данные на устройстве");
        LinearLayout hero=column();hero.setPadding(px(18),px(19),px(17),px(19));
        hero.setBackground(gradient(themeIndex==2?0xffd5e8f7:0xff244b68,themeIndex==2?0xffe9f2fa:0xff163750,22));
        LinearLayout.LayoutParams heroParams=p(-1,-2);heroParams.setMargins(px(2),px(10),px(2),px(14));list.addView(hero,heroParams);
        TextView heading=text("Всего чатов: "+archives.length(),19,FG);heading.setTypeface(null,Typeface.BOLD);hero.addView(heading);
        TextView description=text("Локально · все данные только на устройстве",12,themeIndex==2?SUB:0xffc8e2f1);
        description.setPadding(0,px(9),0,0);hero.addView(description);
        if(archives.length()>=2){
            Button compareFromHome=btn("⇄ Сравнение диалогов");list.addView(compareFromHome,p(-1,49));
            compareFromHome.setOnClickListener(v->chooseFirstDualChat("dual_compare"));
            Button memoriesFromHome=btn("◷ Воспоминания двух чатов");list.addView(memoriesFromHome,p(-1,49));
            memoriesFromHome.setOnClickListener(v->chooseFirstDualChat("dual_memories"));
        }
        // User guide lives in the side drawer; the home screen stays focused on chats.
        TextView section=text("ПЕРЕПИСКИ",11,SUB);section.setTypeface(null,Typeface.BOLD);
        section.setPadding(px(9),px(5),0,px(8));list.addView(section);
        // Global saved notes live in the drawer; chat favorites live under Media.
        ArrayList<JSONObject> orderedChats=new ArrayList<>();
        for(int i=0;i<archives.length();i++){JSONObject c=archives.optJSONObject(i);if(c!=null)orderedChats.add(c);}
        orderedChats.sort((a,b)->Boolean.compare(isChatPinned(b.optString("key")),isChatPinned(a.optString("key"))));
        for(int i=0;i<orderedChats.size();i++){
            JSONObject chat=orderedChats.get(i);
            String key=chat.optString("key"),name=chat.optString("name","Переписка");
            LinearLayout item=row();item.setPadding(px(14),px(12),px(12),px(12));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2e6fc6f4),outlined(PANEL,19,false),null));
            LinearLayout.LayoutParams margins=p(-1,78);margins.setMargins(px(2),px(3),px(2),px(8));list.addView(item,margins);
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);
            TextView avatar=text(letter,23,FG);avatar.setTypeface(null,Typeface.BOLD);avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(gradient(participantColors[i%participantColors.length],0xff294968,100));
            Bitmap picture=PersonalImages.load(this,getPreferences(MODE_PRIVATE).getString(PersonalImages.chatAvatarKey(key),""));
            if(picture!=null){avatar.setText("");avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),PersonalImages.circular(picture,px(51))));}
            item.addView(avatar,p(51,51));
            LinearLayout info=column();info.setPadding(px(14),0,0,0);item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView nameLabel=text((isChatPinned(key)?"📌  ":"")+name,16,FG);nameLabel.setTypeface(null,Typeface.BOLD);nameLabel.setSingleLine(true);
            nameLabel.setEllipsize(TextUtils.TruncateAt.END);info.addView(nameLabel);
            info.addView(text("Переписка на устройстве · офлайн",12,SUB));
            TextView arrow=text("›",25,ACCENT);item.addView(arrow);
            item.setOnClickListener(v->{item.animate().alpha(.75f).setDuration(90).withEndAction(()->{item.setAlpha(1);activateArchive(key);}).start();});
            item.setOnLongClickListener(v->{new FontDialog.Builder(this).setTitle(name)
                .setItems(new String[]{isChatPinned(key)?"Открепить чат":"📌 Закрепить чат", "Открыть переписку"},(d,which)->{
                    if(which==0)toggleChatPin(key);else activateArchive(key);
                }).show();return true;});
        }
        TextView add=text("＋   Добавить переписку",15,ACCENT);add.setTypeface(null,Typeface.BOLD);
        add.setGravity(Gravity.CENTER);add.setPadding(px(12),px(17),px(12),px(17));
        add.setTextColor(themeIndex==2?0xff193852:0xffe7f8ff);
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(0x66ffffff),
            gradient(themeIndex==2?0xffc2e8f7:0xff12628a,themeIndex==2?0xff81d0f0:0xff17425d,20),
            shape(0xffffffff,20)));
        add.setContentDescription("Добавить переписку из экспорта Telegram");
        LinearLayout.LayoutParams ap=p(-1,60);ap.setMargins(px(2),px(9),px(2),px(8));list.addView(add,ap);
        add.setOnClickListener(v->chooseFolder());
        animateEntrance();scroll.post(()->scroll.scrollTo(0,0));
    }
    private boolean isChatPinned(String key){return getPreferences(MODE_PRIVATE).getBoolean("pinned_chat_"+key,false);}
    private void toggleChatPin(String key){
        boolean next=!isChatPinned(key);
        getPreferences(MODE_PRIVATE).edit().putBoolean("pinned_chat_"+key,next).apply();
        renderHome();toast(next?"Чат закреплён сверху":"Закрепление снято");
    }
    /** Find a quote in a registered archive only; never open an arbitrary database name from a restored note. */
    private boolean canOpenQuoteArchive(String key){
        if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}"))return false;
        for(int i=0;i<archives.length();i++){
            JSONObject entry=archives.optJSONObject(i);
            if(entry!=null&&key.equals(entry.optString("key")))return true;
        }
        return false;
    }
    private void openSavedQuote(long messageId,String archiveKey){
        if(!canOpenQuoteArchive(archiveKey)){
            toast("Исходный чат не зарегистрирован. Цитата сохранена.");return;
        }
        if(importing){toast("Дождись окончания импорта");return;}
        if(!archiveKey.equals(activeKey))activateArchive(archiveKey);
        if(archiveKey.equals(activeKey))jump(messageId,"");
        else toast("Не удалось открыть исходный чат");
    }
    /** User-triggered, read-only context lookup. Missing anchors are displayed, not guessed. */
    private void showQuoteSourceMap(String archiveKey,long sourceId){
        if(sourceId<=0||!canOpenQuoteArchive(archiveKey)){
            new FontDialog.Builder(this).setTitle("Карта источника")
                .setMessage("Исходный чат отсутствует в списке архивов. Сохранённая цитата не удалена.")
                .setPositiveButton("Закрыть",null).show();return;
        }
        final int token=loadToken;
        status.setText("Проверяю источник цитаты…");
        worker.execute(()->{
            QuoteSourceMap.Context result=null;String failure=null;
            try{
                String filename="default".equals(archiveKey)?"telegram_archive.db":"telegram_"+archiveKey+".db";
                if(!getDatabasePath(filename).isFile()){
                    failure="База исходного чата не найдена на этом устройстве.";
                }else{
                    try(android.database.sqlite.SQLiteDatabase lookup=android.database.sqlite.SQLiteDatabase.openDatabase(
                            getDatabasePath(filename).getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                        result=MessageDb.quoteContext(lookup,sourceId);
                    }
                }
            }catch(Exception ex){failure="Не удалось проверить источник: "+ex.getClass().getSimpleName();}
            final QuoteSourceMap.Context context=result;
            final String error=failure;
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||!"saved".equals(screen))return;
                if(error!=null||context==null||!context.found){
                    String detail=error!=null?error:"Сообщение с сохранённым ID больше не найдено в исходном чате.";
                    new FontDialog.Builder(this).setTitle("Источник цитаты не найден")
                        .setMessage(detail+"\n\nЦитата и её привязка остаются сохранёнными. Новые связи не создаются.")
                        .setPositiveButton("Закрыть",null).show();
                    status.setText("Карта источника: ссылка не подтверждена");return;
                }
                String[] options=new String[context.messages.size()];
                for(int i=0;i<options.length;i++){
                    QuoteSourceMap.Entry entry=context.messages.get(i);
                    options[i]=context.positionLabel(i)+" · "+entry.date+" · "+entry.author+"\n"+
                        QuoteSourceMap.preview(entry.body);
                }
                new FontDialog.Builder(this).setTitle("Карта источника цитаты")
                    .setItems(options,(dialog,index)->openSavedQuote(context.messages.get(index).id,archiveKey))
                    .setNeutralButton("О связях",(dialog,index)->
                        new FontDialog.Builder(this).setTitle("Как построена карта")
                            .setMessage("Исходник подтверждён по ID архива и сообщения. Рядом показаны записи до и после него " +
                                        "в порядке сохранённого экспорта. Это не доказанные ответы на цитату; связи между людьми не вычисляются.")
                            .setPositiveButton("Понятно",null).show())
                    .setNegativeButton("Закрыть",null).show();
                status.setText("Контекст цитаты: "+context.messages.size()+" сообщений");
            });
        });
    }
    private void renderSaved(){
        list.setBackgroundColor(BG);
        ++loadToken;home=false;screen="saved";list.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();pinnedBar.removeAllViews();bottomNav.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);title.setText("★ Избранное");subtitle.setText("Локальные заметки и сохранённые сообщения");
        savedComposer.removeAllViews();savedComposer.setVisibility(View.VISIBLE);
        LinearLayout compose=row();savedComposer.addView(compose,p(-1,-2));
        EditText input=new EditText(this);input.setHint("Написать заметку…");input.setTextColor(FG);input.setHintTextColor(SUB);compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button send=btn("➤");compose.addView(send,p(50,52));send.setOnClickListener(v->{String value=input.getText().toString().trim();if(value.isEmpty())return;worker.execute(()->{savedStore.add(value,null,null,null);runOnUiThread(this::renderSaved);});});
        Button attach=btn("📎 Прикрепить файл");savedComposer.addView(attach,p(-1,43));attach.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,28);});
        final int savedToken=loadToken;
        worker.execute(()->{List<SavedStore.Note> notes=savedStore.recent(150);runOnUiThread(()->{if(savedToken!=loadToken||!screen.equals("saved"))return;
            for(SavedStore.Note n:notes){
                TextView text=text("★ "+(n.body==null?"":n.body)+(n.fileName!=null?"\n📎 "+n.fileName:""),15,FG);
                text.setPadding(px(14),px(12),px(14),px(12));text.setBackground(shape(PANEL,12));
                LinearLayout.LayoutParams mp=p(-1,-2);mp.setMargins(0,px(5),0,px(5));list.addView(text,mp);
                if(n.originArchive!=null&&n.originMessageId>0){
                    TextView source=text("↗ К исходному сообщению",12,ACCENT);
                    source.setPadding(px(15),px(6),px(12),px(12));list.addView(source);
                    source.setOnClickListener(v->openSavedQuote(n.originMessageId,n.originArchive));
                    TextView context=text("◌ Карта источника · ±2 сообщения",12,ACCENT);
                    context.setPadding(px(15),px(5),px(12),px(12));list.addView(context);
                    context.setOnClickListener(v->showQuoteSourceMap(n.originArchive,n.originMessageId));
                }
                text.setOnLongClickListener(v->{new FontDialog.Builder(this).setTitle("Заметка").setItems(new String[]{"Удалить заметку"},(d,w)->worker.execute(()->{savedStore.delete(n.id);runOnUiThread(this::renderSaved);})).show();return true;});
                if(n.fileId!=null)text.setOnClickListener(v->{try{
                    Uri file=n.fileId.startsWith(PortableNoteFiles.PREFIX)
                        ?PortableNoteFiles.openUri(this,n.fileId):Uri.parse(n.fileId);
                    Intent action=new Intent(Intent.ACTION_VIEW);
                    action.setDataAndType(file,n.mime==null?"*/*":n.mime);
                    action.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(action);
                }catch(Exception ignored){toast("Не удалось открыть файл. Проверь полную резервную копию.");}});
                else if(n.fileName!=null)text.setOnClickListener(v->toast("Вложение отсутствует: для переноса нужна полная резервная копия"));
            }
        });});
        // A global list of starred items across all registered archives.
        for(int i=0;i<archives.length();i++){
            JSONObject item=archives.optJSONObject(i);if(item==null)continue;
            String key=item.optString("key"),label=item.optString("name","Чат");
            worker.execute(()->{
                MessageDb other=new MessageDb(this,key);
                List<MessageDb.Item> favorites;
                try{favorites=other.page(0,50,"",false,"",false,true);}finally{other.close();}
                runOnUiThread(()->{if(!screen.equals("saved"))return;
                    for(MessageDb.Item m:favorites){
                        TextView t=text("★ "+label+" · "+day(m.date)+"\n"+clip(m.body,180),14,FG);
                        t.setPadding(px(12),px(12),px(12),px(12));t.setBackground(shape(CHIP,12));
                        LinearLayout.LayoutParams pad=p(-1,-2);pad.topMargin=px(5);list.addView(t,pad);
                        t.setOnClickListener(v->{activateArchive(key);jump(m.id,"");});
                    }
                });
            });
        }
    }
    private void appendMessages(){
        if(loadingMore||home||(!screen.equals("chat")&&!screen.equals("search")))return;
        boolean searching=screen.equals("search");
        int loaded=searching?searchLoaded:autoLoaded;
        if(loaded>=total)return;
        loadingMore=true;
        final int token=loadToken,offset=getOffset()+loaded;
        final long afterId=lastLoadedMessageId;
        final MessageDb sourceDb=db;
        final String term=q,who=byAuthor;final boolean mode=deep,withMedia=onlyMedia;
        worker.execute(()->{
            try{
                List<MessageDb.Item> part=afterId>0?sourceDb.pageAfter(afterId,PAGE,searching?term:"",searching&&mode,searching&&mode?who:"",searching&&mode&&withMedia,false):sourceDb.page(offset,PAGE,searching?term:"",searching&&mode,searching&&mode?who:"",searching&&mode&&withMedia,false);
                Map<Long,List<MessageDb.Attachment>> files=sourceDb.attachmentsForMessages(part);
                Map<Long,MessageDb.ReplyInfo> replies=sourceDb.replyPreviews(part);
                runOnUiThread(()->{
                    if(token!=loadToken)return;
                    loadingMore=false;
                    if(home||part.isEmpty())return;
                    if(searching)searchLoaded+=part.size();else autoLoaded+=part.size();
                    lastLoadedMessageId=part.get(part.size()-1).id;
                    int y=scroll.getScrollY();
                    renderMessages(part,files,replies,searching,true);
                    scroll.post(()->{if(!searching&&token==loadToken)pruneChatWindow();else scroll.scrollTo(0,y);});
                    pageLabel.setText((getOffset()+1)+"–"+Math.min(total,getOffset()+(searching?searchLoaded:autoLoaded))+" / "+total);
                });
            }catch(Exception error){runOnUiThread(()->{
                if(token!=loadToken)return;
                loadingMore=false;
                status.setText("Ошибка подгрузки: "+error.getMessage());
            });}
        });
    }
    /** Opt-in bounded chat window: evict old Views only; SQLite remains untouched. */
    private void pruneChatWindow(){
        int mode=effectivePerformanceMode();
        if(home||!"chat".equals(screen)||mode==0||list==null)return;
        int toRemove=ChatWindowPolicy.trim(mode,autoLoaded,PAGE);
        if(toRemove==0)return;
        int removed=0,removedHeight=0;
        final int priorY=scroll.getScrollY();
        while(list.getChildCount()>0&&removed<toRemove){
            View child=list.getChildAt(0);Object tag=child.getTag();
            if(tag instanceof Long){renderedMessageViews.remove((Long)tag);removed++;}
            removedHeight+=child.getHeight();list.removeViewAt(0);
        }
        if(removed==0)return;
        chatOffset=ChatWindowPolicy.afterEvictionOffset(chatOffset,removed);autoLoaded-=removed;
        TextView previous=text("↑ Загрузить предыдущие сообщения",13,ACCENT);
        previous.setGravity(Gravity.CENTER);previous.setPadding(px(8),px(13),px(8),px(13));
        previous.setBackground(shape(PANEL,12));
        previous.setOnClickListener(v->{chatOffset=Math.max(0,chatOffset-PAGE);autoLoaded=PAGE;
            lastLoadedMessageId=-1;showPage();});
        list.addView(previous,0,p(-1,-2));
        int change=removedHeight-Math.max(px(44),previous.getHeight());
        scroll.scrollTo(0,Math.max(0,priorY-change));
        verticalTimeline.setPosition(chatOffset,total);
    }
    private void saveReadingProgress(){
        if(home||!screen.equals("chat")||scroll==null||list==null||list.getChildCount()==0)return;
        int y=scroll.getScrollY();
        int probe=y+px(115);
        // Child order is stable; HashMap iteration order is not. Record the first
        // visible message's ID and pixel offset, never a random mapped message.
        long visibleId=-1;int visibleTop=0;
        for(int i=0;i<list.getChildCount();i++){
            View v=list.getChildAt(i);Object tag=v.getTag();
            if(!(tag instanceof Long))continue;
            if(v.getBottom()>probe){visibleId=(Long)tag;visibleTop=v.getTop()-y;break;}
        }
        if(visibleId>0){lastVisibleMessage=visibleId;lastVisibleTop=visibleTop;}
        int child=0;
        for(int i=0;i<list.getChildCount();i++){
            if(list.getChildAt(i).getBottom()>y){child=i;break;}
        }
        int approx=chatOffset+Math.min(autoLoaded-1,Math.max(0,child/2));
        SharedPreferences.Editor editor=getPreferences(MODE_PRIVATE).edit();
        editor.putInt("read_offset_"+activeKey,approx);
        if(lastVisibleMessage>0){
            editor.putLong("read_id_"+activeKey,lastVisibleMessage);
            editor.putInt("read_top_"+activeKey,lastVisibleTop);
        }
        editor.apply();
    }
    private void checkAutoFloat(){
        if(player==null||inlineHolder==null||floatingPanel!=null||!screen.equals("chat"))return;
        try{if(!player.isPlaying())return;}catch(Exception badState){return;}
        int[] p=new int[2],viewport=new int[2];inlineHolder.getLocationOnScreen(p);scroll.getLocationOnScreen(viewport);
        if(p[1]+inlineHolder.getHeight()<viewport[1]||p[1]>viewport[1]+scroll.getHeight()){
            if(playingAttachment!=null&&playingAttachment.kind.equals("round")){
                int ms=0;try{ms=player.getCurrentPosition();}catch(Exception ignored){}
                floatingStart=ms;showFloatingRound(playingAttachment);autoFloating=true;
            }
        }
    }
    private void loadArchiveRegistry(){
        try{archives=new JSONArray(getPreferences(MODE_PRIVATE).getString("archive_registry","[]"));}catch(Exception e){archives=new JSONArray();}
        activeKey=getPreferences(MODE_PRIVATE).getString("active_archive","default");
        if(archives.length()==0){
            String old=getPreferences(MODE_PRIVATE).getString("tree",null);
            if(old!=null)try{JSONObject o=new JSONObject();o.put("key","default");o.put("name","Первая переписка");o.put("tree",old);archives.put(o);saveRegistry();}catch(Exception ignored){}
        }
        for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&activeKey.equals(o.optString("key"))){activeName=o.optString("name","Архив");return;}}
        if(archives.length()>0){JSONObject o=archives.optJSONObject(0);activeKey=o.optString("key","default");activeName=o.optString("name","Архив");}
    }
    private void saveRegistry(){getPreferences(MODE_PRIVATE).edit().putString("archive_registry",archives.toString()).putString("active_archive",activeKey).apply();}
    private String activeTree(){for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&activeKey.equals(o.optString("key")))return o.optString("tree",null);}return null;}
    private void registerArchive(String name,String tree){
        try{JSONObject o=new JSONObject();String key=UUID.randomUUID().toString().replace("-","");o.put("key",key);o.put("name",name);o.put("tree",tree);archives.put(o);activateArchive(key);}catch(Exception e){toast("Ошибка добавления чата");}
    }
    private void activateArchive(String key){
        saveReadingProgress();
        if(importing){toast("Импорт ещё идёт");return;}
        JSONObject choice=null;for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&key.equals(o.optString("key")))choice=o;}
        if(choice==null)return;closeFloatingRound();stopPlayback();++loadToken;clearMediaSelection();activeKey=key;activeName=choice.optString("name","Архив");analyticsSelectedAuthor="";analyticsCalendarYear=0;analyticsCompareFirst="";analyticsCompareSecond="";lastAnalytics=null;saveRegistry();
        if(db!=null)db.close();db=new MessageDb(this,activeKey);
        String path=choice.optString("tree","");folder=path.isEmpty()?null:path.startsWith("local:")?new DocumentFolder(getContentResolver(),new java.io.File(path.substring(6))):new DocumentFolder(getContentResolver(),Uri.parse(path));
        chatOffset=searchOffset=mediaOffset=favOffset=0;autoLoaded=PAGE;home=false;screen="chat";returnScreen="";chatOffset=getPreferences(MODE_PRIVATE).getInt("read_offset_"+activeKey,0);
        pendingRestoreId=getPreferences(MODE_PRIVATE).getLong("read_id_"+activeKey,-1);
        pendingRestoreTop=getPreferences(MODE_PRIVATE).getInt("read_top_"+activeKey,0);
        if(pendingRestoreId>0){
            final String openedKey=activeKey;
            final long messageId=pendingRestoreId;
            worker.execute(()->{int pos=db.positionFor(messageId);runOnUiThread(()->{
                if(isFinishing()||!openedKey.equals(activeKey))return;
                if(pos<0){pendingRestoreId=-1;showPage();return;}
                chatOffset=Math.max(0,pos-3);showPage();
            });});
        }
        else showPage();
    }
    private void chatPicker(){
        String[] labels=new String[archives.length()+1];for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);labels[i]=o==null?"Чат":o.optString("name","Чат");}
        labels[archives.length()]="＋ Добавить чат";
        new FontDialog.Builder(this).setTitle("Мои архивы").setItems(labels,(d,i)->{
            if(i==archives.length())chooseFolder();else{JSONObject o=archives.optJSONObject(i);if(o!=null)activateArchive(o.optString("key"));}
        }).show();
    }
    private void moreMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);menu.getMenu().add("⇈ В начало");menu.getMenu().add("⇊ В конец");menu.getMenu().add("📌 Закреплённые");menu.getMenu().add("Мои архивы");menu.getMenu().add("▥ Анализ переписки");menu.getMenu().add("Цвета участников");
        menu.setOnMenuItemClickListener(item->{String x=item.getTitle().toString();
            if(x.contains("начало")){chatOffset=0;showPage();}
            else if(x.contains("конец")){worker.execute(()->{int n=db.totalMessages();runOnUiThread(()->{chatOffset=Math.max(0,n-PAGE);showPage();});});}
            else if(x.contains("Закрепл"))showPinList();
            else if(x.contains("Анализ")){screen="analytics";showPage();}
            else if(x.contains("Цвета"))participantSettings();else chatPicker();return true;
        });menu.show();
    }
    private void showConversationInfo(){
        new FontDialog.Builder(this).setTitle(activeName)
            .setItems(new String[]{"▦ Медиатека","⌕ Поиск сообщений","▥ Анализ переписки","⚙ Оформление переписки","Локальный профиль и заметка"},(d,i)->{
                if(i==0)screen="media";
                else if(i==1)screen="search";
                else if(i==2)screen="analytics";
                else if(i==3){settingsMenu();return;}else{editLocalProfile();return;}
                showPage();
            }).show();
    }
    private void showConversationMenu(View anchor){
        PopupMenu popup=new PopupMenu(this,anchor);
        popup.getMenu().add("⌕ Поиск");popup.getMenu().add("▥ Анализ переписки");popup.getMenu().add("♡ Хронология");popup.getMenu().add("▤ Кинолента медиа");
        popup.getMenu().add("⚙ Настроить чат");popup.getMenu().add("▦ Медиатека");
        popup.getMenu().add("📌 Закреплённые сообщения");
        popup.setOnMenuItemClickListener(item->{
            String action=item.getTitle().toString();
            if(action.contains("Поиск")){screen="search";showPage();}
            else if(action.contains("Анализ")){screen="analytics";showPage();}
            else if(action.contains("Хронология")){screen="relationships";showPage();}
            else if(action.contains("Кинолента")){screen="cinema";showPage();}
            else if(action.contains("Настроить"))settingsMenu();
            else if(action.contains("Медиатека")){screen="media";showPage();}
            else if(action.contains("Закреплённые"))showPinList();
            return true;
        });popup.show();
    }
    /** Compact Telegram-like pinned preview: tap the message to jump, arrow to switch, cross to unpin. */
    private void renderPinnedBar(){
        pinnedBar.removeAllViews();
        if(!screen.equals("chat")||home)return;
        final String key=activeKey;
        worker.execute(()->{
            List<MessageDb.Item> pins=db.pinned();
            runOnUiThread(()->{
                if(!screen.equals("chat")||home||!key.equals(activeKey))return;
                pinnedBar.animate().cancel();pinnedBar.setAlpha(1f);pinnedBar.removeAllViews();if(pins.isEmpty()){pinnedFocus=-1;return;}
                int selected=0;
                for(int i=0;i<pins.size();i++)if(pins.get(i).id==pinnedFocus){selected=i;break;}
                MessageDb.Item current=pins.get(selected);pinnedFocus=current.id;
                LinearLayout bar=row();bar.setPadding(px(8),px(5),px(6),px(5));
                bar.setBackground(shape(0xff1b3548,12));
                LinearLayout.LayoutParams outer=p(-1,62);outer.setMargins(px(2),px(3),px(2),px(5));pinnedBar.addView(bar,outer);
                TextView stripe=text("┃",28,ACCENT);stripe.setGravity(Gravity.CENTER);
                bar.addView(stripe,p(16,49));
                LinearLayout info=column();info.setGravity(Gravity.CENTER_VERTICAL);
                info.setPadding(px(4),0,px(4),0);
                bar.addView(info,new LinearLayout.LayoutParams(0,-1,1));
                TextView caption=text("📌 Закреплённое сообщение  ·  "+(selected+1)+"/"+pins.size(),11,ACCENT);
                caption.setTypeface(null,Typeface.BOLD);info.addView(caption);
                String summary=current.body==null||current.body.trim().isEmpty()?"Медиафайл":current.body;
                TextView snippet=text(clip(summary,78),13,FG);snippet.setSingleLine(true);info.addView(snippet);
                info.setOnClickListener(v->jump(current.id,""));
                final int nextIndex=(selected+1)%pins.size();
                TextView next=text("⌄",27,FG);next.setGravity(Gravity.CENTER);
                bar.addView(next,p(34,45));next.setOnClickListener(v->{
                    pinnedFocus=pins.get(nextIndex).id;renderPinnedBar();
                });
                TextView unpin=text("✕",20,SUB);unpin.setGravity(Gravity.CENTER);bar.addView(unpin,p(32,45));
                unpin.setOnClickListener(v->unpinMessage(current.id));
                bar.setOnLongClickListener(v->{showPinList();return true;});
                bar.setAlpha(0f);bar.setTranslationY(-px(8));bar.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
            });
        });
    }
    private void unpinMessage(long id){
        new FontDialog.Builder(this).setTitle("Открепить сообщение?").setMessage("Сообщение останется в переписке, но исчезнет из закреплённых.").setNegativeButton("Отмена",null).setPositiveButton("Открепить",(d,w)->unpinConfirmed(id)).show();
    }
    private void unpinConfirmed(long id){
        worker.execute(()->{
            if(db.isPinned(id))db.togglePinned(id);
            runOnUiThread(()->{if(pinnedFocus==id)pinnedFocus=-1;toast("Сообщение откреплено");renderPinnedBar();});
        });
    }
    private void pinnedItemMenu(MessageDb.Item message){
        new FontDialog.Builder(this).setTitle("📌 Закреплённое сообщение")
            .setItems(new String[]{"↗ Перейти к сообщению","Открепить сообщение"},(d,choice)->{
                if(choice==0){pinnedFocus=message.id;jump(message.id,"");}
                else unpinMessage(message.id);
            }).show();
    }
    private void showPinList(){
        worker.execute(()->{
            List<MessageDb.Item> pins=db.pinned();
            runOnUiThread(()->{
                if(pins.isEmpty()){toast("Нет закреплённых сообщений");return;}
                String[] labels=new String[pins.size()];
                for(int i=0;i<pins.size();i++)
                    labels[i]=(i+1)+". "+day(pins.get(i).date)+" · "+clip(pins.get(i).body==null||pins.get(i).body.isEmpty()?"Медиа":pins.get(i).body,65);
                new FontDialog.Builder(this).setTitle("Закреплённые сообщения")
                    .setItems(labels,(d,i)->{pinnedFocus=pins.get(i).id;jump(pinnedFocus,"");})
                    .setNeutralButton("Открепить…",(d,i)->{
                        new FontDialog.Builder(this).setTitle("Что открепить?")
                            .setItems(labels,(dialog,at)->unpinMessage(pins.get(at).id)).show();
                    }).show();
            });
        });
    }
    private void createUi(){
        root=new FrameLayout(this);
        base=column();base.setBackgroundColor(BG);base.setPadding(px(10),px(5),px(10),px(6));
        root.addView(base,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        // Android 15+ uses edge-to-edge: protect the entire header from status bar and cutout.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){
                Insets safe=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars()|WindowInsets.Type.displayCutout());
                base.setPadding(px(10),safe.top+px(6),px(10),safe.bottom+px(6));
            }else{
                base.setPadding(px(10),insets.getSystemWindowInsetTop()+px(6),px(10),insets.getSystemWindowInsetBottom()+px(6));
            }
            return insets;
        });root.requestApplyInsets();
        LinearLayout head=row();head.setPadding(px(7),px(4),px(7),px(4));
        head.setBackground(glass(PANEL,19));
        LinearLayout.LayoutParams headBounds=p(-1,58);headBounds.bottomMargin=px(8);
        base.addView(head,headBounds);
        menuButton=btn("☰");menuButton.setTextSize(22);menuButton.setGravity(Gravity.CENTER);
        head.addView(menuButton,p(42,42));menuButton.setOnClickListener(v->showSideMenu());
        navBack=btn("‹");navBack.setTextSize(25);navBack.setGravity(Gravity.CENTER);
        head.addView(navBack,p(42,42));
        navBack.setOnClickListener(v->{if("help".equals(screen)){leaveHelp();return;}
            if(!home){saveReadingProgress();home=true;screen="chat";returnScreen="";showPage();}});
        TextView logo=text("◇",29,ACCENT);logo.setGravity(Gravity.CENTER);logo.setContentDescription("Локальный архив");head.addView(logo,p(34,42));
        LinearLayout headings=column();headings.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(headings,new LinearLayout.LayoutParams(0,px(50),1));
        title=text("Telegram Archive",17,FG);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);headings.addView(title,p(-1,26));
        subtitle=text("Личный архив · офлайн",11,SUB);
        subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);headings.addView(subtitle,p(-1,19));
        headerActions=btn("≡");headerActions.setContentDescription("Меню переписки");head.addView(headerActions,p(42,42));
        headerActions.setOnClickListener(v->showConversationMenu(headerActions));
        title.setOnClickListener(v->{if(!home&& !"saved".equals(screen)&&!"help".equals(screen))showConversationInfo();});
        folderButton=btn("＋");folderButton.setTextSize(22);head.addView(folderButton,p(41,42));
        folderButton.setOnClickListener(v->chooseFolder());
        playerStrip=row();playerStrip.setVisibility(View.GONE);
        playerStrip.setPadding(px(10),0,px(5),0);playerStrip.setBackground(outlined(PANEL,16,true));
        LinearLayout.LayoutParams playerBounds=p(-1,49);playerBounds.bottomMargin=px(6);base.addView(playerStrip,playerBounds);
        playerText=text("Воспроизведение",13,FG);playerText.setSingleLine(true);
        playerText.setEllipsize(TextUtils.TruncateAt.END);
        playerStrip.addView(playerText,new LinearLayout.LayoutParams(0,-2,1));
        Button prev=btn("⏮");playerStrip.addView(prev,p(38,40));prev.setOnClickListener(v->playAdjacent(false));
        playerPause=btn("⏸");playerStrip.addView(playerPause,p(38,40));playerPause.setOnClickListener(v->toggleActivePlayback());
        Button next=btn("⏭");playerStrip.addView(next,p(38,40));next.setOnClickListener(v->playAdjacent(true));
        Button close=btn("✕");playerStrip.addView(close,p(38,40));close.setOnClickListener(v->{closeFloatingRound();stopPlayback();});
        bottomNav=row();bottomNav.setPadding(px(4),px(3),px(4),px(3));
        bottomNav.setBackground(glass(PANEL,17));
        LinearLayout.LayoutParams navBounds=p(-1,53);navBounds.bottomMargin=px(7);base.addView(bottomNav,navBounds);
        addTab("💬 Переписка","chat");addTab("▦ Медиа","media");
        toolArea=column();base.addView(toolArea,p(-1,-2));
        pinnedBar=row();base.addView(pinnedBar,p(-1,-2));
        filterBar=column();toolArea.addView(filterBar,p(-1,-2));
        searchArea=column();toolArea.addView(searchArea,p(-1,-2));
        status=text("",11,SUB);status.setPadding(px(8),px(4),px(8),px(6));status.setMaxLines(3);base.addView(status,p(-1,-2));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,ox,oy)->{if(!home&&(screen.equals("chat")||screen.equals("search"))&&!loadingMore&&list.getHeight()>0&&y>oy&&y+scroll.getHeight()>list.getHeight()-px(300)&&((screen.equals("chat")?autoLoaded:searchLoaded)<total)){appendMessages();}if(!home&&"chat".equals(screen)){scheduleFloatingCheck();scheduleReadingSave();}});
        FrameLayout scrollHost=new FrameLayout(this);
        base.addView(scrollHost,new LinearLayout.LayoutParams(-1,0,1));
        scrollHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        verticalTimeline=new VerticalTimeline();
        scrollHost.addView(verticalTimeline,new FrameLayout.LayoutParams(px(25),-1,Gravity.END));
        list=column();list.setPadding(px(3),px(6),px(3),px(14));scroll.addView(list);
        LinearLayout bottom=row();bottomPager=bottom;base.addView(bottom,p(-1,52));
        backPage=btn("‹ Назад");nextPage=btn("Далее ›");pageLabel=text("",12,SUB);pageLabel.setGravity(Gravity.CENTER);
        savedComposer=column();savedComposer.setVisibility(View.GONE);
        savedComposer.setBackground(glass(PANEL,16));base.addView(savedComposer,p(-1,-2));
        bottom.addView(backPage,p(95,44));bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));bottom.addView(nextPage,p(95,44));
        backPage.setOnClickListener(v->{setOffset(Math.max(0,getOffset()-getLimit()));showPage();});
        nextPage.setOnClickListener(v->{setOffset(getOffset()+getLimit());showPage();});
        configureBars();
    }
    private void addTab(String label,String key){
        TextView t=text(label,13,SUB);t.setGravity(Gravity.CENTER);t.setTag(key);
        t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setContentDescription(label);t.setBackground(shape(Color.TRANSPARENT,14));
        LinearLayout.LayoutParams tabBounds=new LinearLayout.LayoutParams(0,px(44),1);
        tabBounds.leftMargin=px(2);tabBounds.rightMargin=px(2);bottomNav.addView(t,tabBounds);
        t.setOnClickListener(v->{if(floatingPanel==null)stopPlayback();screen=key;returnScreen="";highlightId=-1;showPage();});
    }
    private void refreshTabs(){
        for(int i=0;i<bottomNav.getChildCount();i++){
            TextView t=(TextView)bottomNav.getChildAt(i);boolean selected=screen.equals(t.getTag())||("favorites".equals(screen)&&"media".equals(t.getTag()));
            boolean previously=t.isSelected();t.setSelected(selected);
            t.setBackground(selected?outlined(CHIP,13,true):shape(Color.TRANSPARENT,13));
            t.setTextColor(selected?FG:SUB);
            if(previously!=selected&&motionDuration(180)>0){
                t.animate().cancel();t.setAlpha(.80f);t.animate().alpha(1f)
                    .setDuration(motionDuration(160)).setInterpolator(new DecelerateInterpolator()).start();
            }else{t.animate().cancel();t.setAlpha(1f);}
        }
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);
        if(headerActions!=null)headerActions.setVisibility(View.VISIBLE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE);
        title.setText(activeName);
        subtitle.setText(screen.equals("chat")?"История сообщений · локально":screen.equals("media")?"Фото, видео, голосовые и файлы":screen.equals("favorites")?"Твои сохранённые сообщения":screen.equals("analytics")?"Статистика · полностью офлайн":screen.equals("relationships")?"Личные события и этапы":screen.equals("echo")?"Повторяющиеся фразы":screen.equals("chronology")?"Периоды истории · вручную":screen.equals("cinema")?"Медиа по годам · офлайн":"Найди любое сообщение");
    }
    private void configureBars(){
        filterBar.removeAllViews();searchArea.removeAllViews();
        if("media".equals(screen)||"favorites".equals(screen)){
            HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chips=row();hsv.addView(chips);
            for(int i=0;i<MEDIA_KEYS.length;i++){
                final String key=MEDIA_KEYS[i];TextView c=text(MEDIA_LABELS[i],12,FG);c.setGravity(Gravity.CENTER);
                c.setPadding(px(12),px(8),px(12),px(8));c.setBackground(shape((key.equals(mediaKind)&&"media".equals(screen)||"favorite".equals(key)&&"favorites".equals(screen))?HIGHLIGHT:PANEL,14));
                LinearLayout.LayoutParams cp=p(-2,37);cp.setMargins(px(2),px(2),px(4),px(2));chips.addView(c,cp);
                c.setOnClickListener(v->{if("favorite".equals(key)){screen="favorites";favOffset=0;}else{screen="media";mediaKind=key;mediaOffset=0;}showPage();});
            }
            filterBar.addView(hsv,p(-1,44));
            if("favorites".equals(screen))return;
            LinearLayout mediaTools=row();
            Button selection=btn(multiSelectMedia?"☑ Выбрано "+mediaSelection.size():"☐ Выбрать несколько");
            mediaTools.addView(selection,new LinearLayout.LayoutParams(0,px(42),1));
            selection.setOnClickListener(v->{
                if(multiSelectMedia){clearMediaSelection();}else{multiSelectMedia=true;selectedMediaArchive=activeKey;}
                showPage();
            });
            Button collections=btn("Коллекции");mediaTools.addView(collections,new LinearLayout.LayoutParams(0,px(42),1));
            collections.setOnClickListener(v->showCollectionPicker());
            Button duplicates=btn("≡");duplicates.setContentDescription("Поиск одинаковых медиа");
            mediaTools.addView(duplicates,p(47,42));duplicates.setOnClickListener(v->inspectDuplicateMedia());
            filterBar.addView(mediaTools,p(-1,46));
            if(multiSelectMedia){
                LinearLayout actions=row();
                Button save=btn("↓ Сохранить "+mediaSelection.size());
                actions.addView(save,new LinearLayout.LayoutParams(0,px(43),1));
                save.setOnClickListener(v->confirmBatchExport(false));
                Button album=btn("+ В коллекцию");actions.addView(album,new LinearLayout.LayoutParams(0,px(43),1));
                album.setOnClickListener(v->addSelectionToCollection());
                filterBar.addView(actions,p(-1,46));
            }
        }
        if("chat".equals(screen)){
            LinearLayout actions=row();
            Button date=btn("📅 Перейти к дате");actions.addView(date,new LinearLayout.LayoutParams(0,px(42),1));date.setOnClickListener(v->chooseDate());
            if(!returnScreen.isEmpty()){
                backButton=btn("↩ Назад к "+("media".equals(returnScreen)?"медиа":"relationships".equals(returnScreen)?"Хронологиям":"echo".equals(returnScreen)?"Эхо фраз":"поиску"));
                actions.addView(backButton,p(-2,42));backButton.setOnClickListener(v->restoreReturn());
            }
            filterBar.addView(actions,p(-1,46));
        }
        if("search".equals(screen)){
            searchInput=new EditText(this);searchInput.setText(q);searchInput.setTextSize(16);searchInput.setSingleLine(true);
            searchInput.setTextColor(FG);searchInput.setHintTextColor(SUB);searchInput.setHint("Слово, фраза или часть слова");
            searchInput.setPadding(px(14),px(3),px(14),px(3));searchInput.setBackground(shape(PANEL,12));
            searchArea.addView(searchInput,p(-1,48));
            LinearLayout controls=row();quickButton=btn("⚡ Быстрый поиск");deepButton=btn("⌕ Глубокий поиск");
            controls.addView(quickButton,new LinearLayout.LayoutParams(0,px(43),1));controls.addView(deepButton,new LinearLayout.LayoutParams(0,px(43),1));
            searchArea.addView(controls,p(-1,49));
            quickButton.setBackground(shape(!deep?HIGHLIGHT:PANEL,10));deepButton.setBackground(shape(deep?HIGHLIGHT:PANEL,10));
            quickButton.setOnClickListener(v->{deep=false;applySearch();});
            deepButton.setOnClickListener(v->{deep=true;applySearch();});
            if(deep){
                authorInput=new EditText(this);authorInput.setText(byAuthor);authorInput.setSingleLine(true);
                authorInput.setTextColor(FG);authorInput.setHintTextColor(SUB);authorInput.setTextSize(13);
                authorInput.setHint("Отправитель (необязательно)");authorInput.setBackground(shape(PANEL,10));
                authorInput.setPadding(px(12),px(4),px(12),px(4));searchArea.addView(authorInput,p(-1,39));
                mediaOnly=new CheckBox(this);mediaOnly.setText("Только сообщения с медиа");mediaOnly.setTextColor(FG);mediaOnly.setTextSize(12);
                mediaOnly.setChecked(onlyMedia);searchArea.addView(mediaOnly,p(-1,39));
                TextView hint=text("Глубокий поиск ищет точное сочетание букв, даже внутри слова; может работать медленнее.",11,SUB);
                hint.setPadding(px(7),0,0,px(6));searchArea.addView(hint,p(-1,-2));
            }else{
                TextView hint=text("Быстрый поиск по словам и их началам. Нажми на результат, чтобы перейти в чат.",11,SUB);
                hint.setPadding(px(7),0,0,px(4));searchArea.addView(hint,p(-1,-2));
            }
            searchInput.setOnEditorActionListener((v,action,event)->{applySearch();return true;});
        }
    }
    private void applySearch(){
        if(searchInput!=null)q=searchInput.getText().toString().trim();
        if(authorInput!=null&&deep)byAuthor=authorInput.getText().toString().trim();
        if(mediaOnly!=null&&deep)onlyMedia=mediaOnly.isChecked();
        searchOffset=0;searchLoaded=PAGE;configureBars();hideKeyboard();showPage();
    }
    private void hideKeyboard(){try{((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(base.getWindowToken(),0);}catch(Exception ignored){}}
    private void identityDialog(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(mine);input.setHint("Имя в переписке Telegram");
        LinearLayout wrap=column();wrap.setPadding(px(20),px(5),px(20),0);wrap.addView(input);
        new FontDialog.Builder(this).setTitle("Как тебя зовут в экспорте?")
                .setMessage("Укажи имя отправителя точно как в HTML, чтобы твои сообщения были справа и выделялись цветом.")
                .setView(wrap).setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить",(d,w)->{
                    mine=input.getText().toString().trim();getPreferences(MODE_PRIVATE).edit().putString("mine",mine).apply();showPage();
                }).show();
    }
    private void chooseDate(){
        Calendar c=Calendar.getInstance();
        new DatePickerDialog(this,(view,y,m,d)->{
            final String requested=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);
            status.setText("Ищу переписку по дате "+requested+"…");
            worker.execute(()->{
                MessageDb.DateMatch found=db.nearestDate(y,m+1,d);
                runOnUiThread(()->{
                    if(found.position<0){toast("В переписке нет сообщений с распознанной датой");return;}
                    screen="chat";chatOffset=Math.max(0,found.position-5);highlightId=-1;returnScreen="";
                    showPage();
                    if(!requested.equals(found.day))toast("Ближайшая дата переписки: "+found.day);
                });
            });
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void chooseFolder(){
        new FontDialog.Builder(this).setTitle("Добавить переписку").setItems(
            new String[]{"Выбрать папку Telegram","Выбрать ZIP или RAR"},
            (d,selected)->{if(selected==0)chooseExistingFolder();else chooseCompressedArchive();}).show();
    }
    private void chooseCompressedArchive(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,57);
    }
    private void chooseExistingFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_FOLDER);
    }
    @Override public void onBackPressed(){
        if("help".equals(screen)){leaveHelp();return;}
        if(floatingPanel!=null){closeFloatingRound();return;}
        if(screen.equals("saved")||(!home&&returnScreen.isEmpty())){saveReadingProgress();home=true;screen="chat";bottomNav.setVisibility(View.VISIBLE);showPage();return;}if(!returnScreen.isEmpty()){restoreReturn();return;}super.onBackPressed();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_FONT){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)handleFontImport(data.getData());
            return;
        }
        if(request==BATCH_TREE_REQUEST){
            List<MediaBatchExporter.Entry> prepared=pendingBatchFiles;
            DocumentFolder source=pendingBatchSource;
            boolean downloads=pendingBatchDownloads;
            pendingBatchFiles=null;pendingBatchSource=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&prepared!=null&&source!=null){
                startBatchExport(prepared,source,downloads,data.getData());
            }
            return;
        }
        if(request==SAVE_MEDIA_REQUEST){
            DocumentFolder sourceFolder=pendingMediaSaveRoot;
            String sourcePath=pendingMediaSavePath;
            pendingMediaSaveRoot=null;pendingMediaSavePath=null;pendingMediaSaveSpec=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&sourceFolder!=null&&sourcePath!=null){
                Uri destination=data.getData();
                status.setText("Сохраняю файл в выбранную папку…");
                worker.execute(()->{
                    try{
                        Uri source=sourceFolder.resolve(sourcePath);
                        if(source==null)throw new IOException("Исходное медиа не найдено");
                        long bytes=MediaSaver.copy(this,source,destination);
                        if(bytes==0)throw new IOException("Исходный файл пуст");
                        runOnUiThread(()->toast("Сохранено · "+(bytes/1024)+" КБ"));
                    }catch(Exception error){
                        MediaSaver.discardIncompleteDocument(this,destination);
                        runOnUiThread(()->toast("Не удалось сохранить: "+error.getMessage()));
                    }
                });
            }
            return;
        }
        if(request==SAVE_TEXT_REQUEST){
            String textToWrite=pendingMessageText;pendingMessageText=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&textToWrite!=null){
                Uri destination=data.getData();
                worker.execute(()->{
                    try(OutputStream output=getContentResolver().openOutputStream(destination,"w")){
                        if(output==null)throw new IOException("Нет доступа к документу");
                        output.write(textToWrite.getBytes(StandardCharsets.UTF_8));
                        output.flush();runOnUiThread(()->toast("Сообщение сохранено в TXT"));
                    }catch(Exception error){
                        MediaSaver.discardIncompleteDocument(this,destination);
                        runOnUiThread(()->toast("Не удалось сохранить: "+error.getMessage()));
                    }
                });
            }
            return;
        }
        if(request==CAPSULE_EXPORT_REQUEST){
            char[] password=pendingCapsulePassword;pendingCapsulePassword=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)exportCapsule(data.getData(),password);
            else if(password!=null)java.util.Arrays.fill(password,(char)0);
            return;
        }
        if(request==WRAPPED_PNG_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null){exportWrappedTo(data.getData());return;}
        if((request==60||request==61||request==62)&&result==RESULT_OK&&data!=null&&data.getData()!=null){applyPersonalImage(data.getData(),request);return;}
        if(request==44&&result==RESULT_OK&&data!=null&&data.getData()!=null){dryRunSelected(data.getData());return;}
        if(request==43&&result==RESULT_OK&&data!=null&&data.getData()!=null){verifyBackup(data.getData());return;}
        if((request==41||request==42)&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            if(request==42)askRestorePassword(data.getData());else{
                selectedBackupName=displayDocumentName(data.getData());
                status.setText("Сохраняю «"+selectedBackupName+"» в выбранную через Android папку…");
                backupAction(data.getData(),false);
            }return;
        }
        if(request==28&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            String label=uri.getLastPathSegment();String mime=getContentResolver().getType(uri);
            worker.execute(()->{savedStore.add("",label,uri.toString(),mime);runOnUiThread(this::renderSaved);});return;
        }
        if(request==57&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri source=data.getData();String name="Telegram.zip";
            try(android.database.Cursor c=getContentResolver().query(source,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null&&c.moveToFirst())name=c.getString(0);
            }catch(Exception ignored){}
            String low=name==null?"":name.toLowerCase(Locale.ROOT);
            String ext=low.endsWith(".rar")?"rar":low.endsWith(".zip")?"zip":"";
            if(ext.isEmpty()){toast("Нужен ZIP или RAR");return;}
            final String displayName=name;
            new FontDialog.Builder(this).setTitle("Импорт "+displayName)
                .setMessage("Архив автоматически распакуется во внутреннюю папку приложения. Для медиа потребуется свободное место.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Импортировать",(d,w)->importCompressed(source,ext,displayName)).show();
            return;
        }
        if(request!=PICK_FOLDER||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception e){status.setText("Нет доступа к папке: "+e.getMessage());return;}
        EditText chatName=new EditText(this);chatName.setSingleLine(true);chatName.setHint("Например, переписка с Ваней");
        new FontDialog.Builder(this).setTitle("Название чата").setView(chatName).setPositiveButton("Добавить",(dd,ww)->{
        String name=chatName.getText().toString().trim();if(name.isEmpty())name="Чат "+(archives.length()+1);
        registerArchive(name,uri.toString());
        new FontDialog.Builder(this).setTitle("Импортировать экспорт Telegram?")
                .setMessage("Будут проиндексированы все messages*.html. Фото, видео и голосовые останутся в этой папке. Текущие сообщения заменятся только после успешного импорта. Избранное по возможности сохранится.")
                .setPositiveButton("Импортировать",(d,w)->importArchive()).setNegativeButton("Отмена",null).show();
        }).setNegativeButton("Отмена",null).show();
    }
    private void importCompressed(Uri source,String extension,String label){
        if(importing)return;
        importing=true;folderButton.setEnabled(false);status.setText("Распаковка "+label+"…");
        String key=UUID.randomUUID().toString().replace("-","");
        worker.execute(()->{
            try{
                java.io.File root=ArchiveFileImport.extract(this,source,extension,key,msg->runOnUiThread(()->status.setText(msg)));
                MessageDb fresh=new MessageDb(this,key);
                try{TelegramImporter.importFiles(getContentResolver(),new DocumentFolder(getContentResolver(),root),fresh,msg->runOnUiThread(()->status.setText(msg)));}finally{fresh.close();}
                runOnUiThread(()->{
                    importing=false;folderButton.setEnabled(true);
                    try{
                        JSONObject o=new JSONObject();o.put("key",key);o.put("name",label);
                        o.put("tree","local:"+root.getAbsolutePath());archives.put(o);
                        activateArchive(key);status.setText("Импорт завершён");
                    }catch(Exception error){toast("Ошибка регистрации чата: "+error.getMessage());}
                });
            }catch(Exception error){
                ArchiveFileImport.deleteTree(new java.io.File(new java.io.File(getFilesDir(),"telegram_imports"),key));
                runOnUiThread(()->{importing=false;folderButton.setEnabled(true);status.setText("Ошибка архива: "+error.getMessage());});
            }
        });
    }
    private void importArchive(){
        if(folder==null||importing)return;closeFloatingRound();stopPlayback();importing=true;folderButton.setEnabled(false);status.setText("Создаю локальную базу, не копируя медиа…");
        list.removeAllViews();DocumentFolder current=folder;
        worker.execute(()->{
            try{TelegramImporter.importFiles(getContentResolver(),current,db,s->runOnUiThread(()->status.setText(s)));info="Импорт завершён";}
            catch(Exception e){info="Ошибка импорта: "+e.getMessage();}
            runOnUiThread(()->{importing=false;folderButton.setEnabled(true);status.setText(info);chatOffset=0;mediaOffset=0;searchOffset=0;favOffset=0;showPage();});
        });
    }
    private int getOffset(){switch(screen){case "media":return mediaOffset;case "search":return searchOffset;case "favorites":return favOffset;default:return chatOffset;}}
    private int getLimit(){return screen.equals("media")?MEDIA_PAGE:PAGE;}
    private void setOffset(int x){switch(screen){case "media":mediaOffset=x;break;case "search":searchOffset=x;break;case "favorites":favOffset=x;break;default:chatOffset=x;}}
    private void showPage(){
        if("help".equals(screen)){renderHelpPage();return;}
        if("dual_compare".equals(screen)||"dual_memories".equals(screen)){
            renderDualChatScreen();return;
        }
        if("relationships".equals(screen)){renderRelationshipsPage();return;}
        if("echo".equals(screen)){renderEchoPage();return;}
        if("chronology".equals(screen)){renderChronologyPage();return;}
        if("cinema".equals(screen)){renderCinemaPage();return;}
        if(savedComposer!=null&&!screen.equals("saved"))savedComposer.setVisibility(View.GONE);
        if(scheduledPrefetch!=null){handler.removeCallbacks(scheduledPrefetch);scheduledPrefetch=null;}
        if(screen.equals("saved")){renderSaved();return;}
        if(!home)bottomNav.setVisibility(View.VISIBLE);
        if(importing)return;if(floatingPanel==null)stopPlayback();refreshTabs();configureBars();renderPinnedBar();int token=++loadToken;
        loadingMore=false; // A stale asynchronous page must not block the new tab.
        if(home){renderHome();return;}
        final String tab=screen,kind=mediaKind,search=q,who=byAuthor;
        final int requested=getOffset();final boolean deepMode=deep,hasMedia=onlyMedia;
        backPage.setEnabled(false);nextPage.setEnabled(false);status.setText("Загружаю…");
        worker.execute(()->{
            try{
                if("analytics".equals(tab)){
                    // Run SQLite scan off the UI thread. The load token drops stale results
                    // after switching chats/tabs, without ever writing to the database.
                    ChatAnalytics.Snapshot report=db.analytics();
                    runOnUiThread(()->{
                        if(isFinishing()||token!=loadToken)return;
                        lastAnalytics=report;list.removeAllViews();list.setBackgroundColor(BG);
                        renderAnalytics(report);
                        scroll.post(()->scroll.scrollTo(0,0));
                        bottomPager.setVisibility(View.GONE);
                        verticalTimeline.setVisibility(View.GONE);
                        status.setText("Статистика рассчитана локально · ничего не отправлено");
                    });
                    return;
                }
                boolean gallery="media".equals(tab),find="search".equals(tab),favs="favorites".equals(tab);
                int count=gallery?db.mediaCount(kind):db.count(find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                int pos=count==0?0:Math.min(requested,Math.max(0,count-1));
                List<MessageDb.Attachment> attachments=gallery?db.mediaPage(kind,pos,MEDIA_PAGE):null;
                List<MessageDb.Item> messages=gallery?null:db.page(pos,tab.equals("chat")?Math.max(PAGE,autoLoaded):tab.equals("search")?Math.max(PAGE,searchLoaded):PAGE,find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                Map<Long,List<MessageDb.Attachment>> links=messages==null?new HashMap<>():db.attachmentsForMessages(messages);
                Map<Long,MessageDb.ReplyInfo> replies=messages==null?new HashMap<>():db.replyPreviews(messages);
                MessageDb.SearchStats stats=find?db.searchStats(search,deepMode,deepMode?who:"",deepMode&&hasMedia):null;
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken)return;
                    total=count;setOffset(pos);list.removeAllViews();
                    if("chat".equals(tab)){
                        android.graphics.drawable.Drawable custom=PersonalImages.wallpaper(this,getPreferences(MODE_PRIVATE),activeKey,BG);
                        list.setBackground(custom!=null?custom:new ChatWallpaper(wallpaperIndex,wallpaperOpacity/100f,themeIndex==2));
                    }
                    else list.setBackgroundColor(BG);
                    if(gallery){renderMedia(attachments);scheduleMediaPrefetch(token,kind,pos+attachments.size(),count);}
                    else {lastLoadedMessageId=messages.isEmpty()?-1:messages.get(messages.size()-1).id;renderMessages(messages,links,replies,find,false);}
                    animateEntrance();
                    bottomPager.setVisibility(tab.equals("chat")||tab.equals("search")?View.GONE:View.VISIBLE);
                    int shown=gallery?attachments.size():messages.size();
                    verticalTimeline.setVisibility("chat".equals(tab)?View.VISIBLE:View.GONE);
                    if("chat".equals(tab))verticalTimeline.setPosition(pos,count);
                    pageLabel.setText(count==0?"Нет данных":(pos+1)+"–"+(pos+shown)+" / "+count);
                    backPage.setEnabled(pos>0);nextPage.setEnabled(pos+shown<count);
                    if(count==0)status.setText(gallery?"Таких медиа пока нет. Попробуй повторный импорт исходных HTML.":
                            "Ничего не найдено — попробуй другой запрос или выбери папку.");
                    else status.setText("media".equals(tab)?"Нажми — воспроизвести · удерживай — действия":
                            "search".equals(tab)?(search.isEmpty()?"Введите слово или фразу":("Сообщений: "+count+"  ·  Точных вхождений «"+clip(search,28)+"»: "+stats.literalOccurrences+(deepMode?"":"  ·  Быстрый поиск учитывает начала слов"))):
                            "favorites".equals(tab)?"Твои избранные сообщения": "История · удерживай сообщение для действий");
                });
            }catch(Exception e){runOnUiThread(()->{if(token==loadToken)status.setText("Не удалось прочитать базу: "+e.getMessage());});}
        });
    }
    /** Analytics is a read-only, anonymous-by-default view of the current chat DB. */
    private void renderAnalytics(ChatAnalytics.Snapshot r){
        TextView heading=text("Анализ переписки",22,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(px(10),px(8),px(8),px(7));list.addView(heading);
        TextView note=text("Все данные остаются на устройстве. Проценты показывают долю отправленных сообщений, а не того, кому они адресованы.",12,SUB);
        note.setPadding(px(11),0,px(11),px(12));list.addView(note);
        LinearLayout overview=analyticsCard("Общая картина");
        analyticsLine(overview,"Сообщений",r.totalMessages);
        analyticsLine(overview,"Участников / отправителей",r.people.size());
        analyticsLine(overview,"Слов",r.totalWords);
        analyticsLine(overview,"Символов (Unicode)",r.totalCharacters);
        analyticsLine(overview,"Медиа и файлов",r.totalMedia);
        if(!r.firstDay.isEmpty())analyticsText(overview,"Период: "+r.firstDay+" — "+r.lastDay+" · активных дней: "+r.days.size(),SUB,12);
        if(r.totalMessages==0){analyticsText(overview,"В этой переписке пока нет сообщений.",SUB,13);return;}
        renderWrappedCard(r);
        renderTimeAnalytics(r);
        LinearLayout people=analyticsCard("Кто сколько написал");
        int shown=0;
        for(ChatAnalytics.Person person:r.people){
            if(shown++>=35)break;
            TextView label=text(person.display+"  ·  "+person.messages+" ("+fmtPct(ChatAnalytics.percent(person.messages,r.totalMessages))+"%)",14,FG);
            label.setPadding(px(5),px(8),px(3),px(3));people.addView(label);
            int weight=(int)Math.round(ChatAnalytics.percent(person.messages,r.totalMessages)*10);
            weight=Math.min(1000,Math.max(person.messages>0?1:0,weight));
            LinearLayout bar=row();bar.setBackground(shape(CHIP,8));
            LinearLayout.LayoutParams bp=p(-1,10);bp.setMargins(px(5),0,px(5),px(4));people.addView(bar,bp);
            if(weight>0){View filled=new View(this);filled.setBackground(shape(authorColor(person.display),8));
                bar.addView(filled,new LinearLayout.LayoutParams(0,-1,weight));}
            if(weight<1000)bar.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1000-weight));
            analyticsText(people,"Слов: "+person.words+" ("+fmtPct(ChatAnalytics.percent(person.words,r.totalWords))+"%)"+
                " · символов: "+person.characters+" · вложений: "+person.attachments+
                " ("+fmtPct(ChatAnalytics.percent(person.attachments,r.totalMedia))+"%)",SUB,12);
            analyticsText(people,"Фото: "+person.kind("image")+" · кружков: "+person.kind("round")+
                " · голосовых: "+person.kind("voice")+" · видео: "+person.kind("video")+
                " · GIF: "+person.kind("gif")+" · файлов: "+person.kind("file")+
                " · музыки: "+person.kind("audio")+" · стикеров: "+person.kind("sticker"),SUB,11);
        }
        if(r.people.size()>35)analyticsText(people,"Показаны первые 35 участников из "+r.people.size()+". В общих итогах учтены все.",SUB,12);
        LinearLayout response=analyticsCard("Интервал между сообщениями участников");
        if(r.switchCount>0){
            analyticsText(response,"Средний интервал при смене отправителя: "+
                fmtPct(r.meanSwitchMinutes)+" минут · пар сообщений: "+r.switchCount,FG,14);
            analyticsText(response,"Учитываются соседние сообщения разных отправителей с датой и перерывом до 24 часов. Это приблизительная оценка темпа общения, а не подтверждённое время ответа на конкретное сообщение. В группах точность особенно ограничена.",SUB,11);
        }else analyticsText(response,"Не удалось собрать подходящие пары сообщений.",SUB,12);
        LinearLayout media=analyticsCard("Медиатека по типам");
        String[] kinds={"image","video","round","voice","audio","gif","sticker","file"};
        String[] titles={"Фото","Обычные видео","Видеосообщения","Голосовые","Музыка/аудио","GIF","Стикеры","Файлы"};
        for(int i=0;i<kinds.length;i++)analyticsLine(media,titles[i],r.type(kinds[i]));
        LinearLayout terms=analyticsCard("Самые частые слова");
        TextView toggle=text(analyticsCommonWords?"◉ Все слова · скрыть служебные":"◉ Без служебных · показать все",12,ACCENT);
        toggle.setPadding(px(4),px(8),px(4),px(8));terms.addView(toggle);
        toggle.setOnClickListener(v->{analyticsCommonWords=!analyticsCommonWords;if(lastAnalytics==r){list.removeAllViews();renderAnalytics(r);}});
        if(r.limitedVocabulary)analyticsText(terms,"Очень большой словарь: редкие новые слова могли не учитываться в рейтинге. Итоги сообщений, слов и медиа полные.",SUB,12);
        List<ChatAnalytics.WordCount> words=analyticsCommonWords?r.topWords:r.topMeaningfulWords;
        for(ChatAnalytics.WordCount word:words){
            TextView entry=text("«"+word.word+"»  ·  "+word.count+" раз",14,FG);
            entry.setPadding(px(6),px(6),px(4),px(6));terms.addView(entry);
            entry.setOnClickListener(v->{q=word.word;deep=true;searchOffset=0;screen="search";showPage();});
        }
        if(words.isEmpty())analyticsText(terms,"Пока нет текстовых сообщений.",SUB,13);
        if(!r.days.isEmpty()){
            LinearLayout days=analyticsCard("Самые активные дни");
            ArrayList<Map.Entry<String,Long>> ranked=new ArrayList<>(r.days.entrySet());
            ranked.sort((a,b)->{int diff=Long.compare(b.getValue(),a.getValue());return diff!=0?diff:a.getKey().compareTo(b.getKey());});
            for(int i=0;i<Math.min(8,ranked.size());i++)analyticsLine(days,ranked.get(i).getKey(),ranked.get(i).getValue());
        }
        LinearLayout about=analyticsCard("Как считаются показатели");
        analyticsText(about,"Отправители объединяются без учёта регистра и лишних пробелов. Каждое вложение считается отдельно; одно сообщение может содержать несколько файлов. Слова считаются по Unicode-буквам и цифрам. Системные сообщения из старых импортов могут иметь ошибочно назначенного автора — для уточнения нужен повторный импорт HTML.",SUB,12);
        analyticsText(about,"Число медиа основано на индексированных ссылках HTML: отсутствующий на диске файл тоже учитывается. Получатель в группе не определяется только по имени отправителя. Нажатие на слово открывает поиск.",SUB,12);
    }
    private void renderWrappedCard(ChatAnalytics.Snapshot report){
        if(report.trends==null||report.trends.all.days.isEmpty())return;
        int latest=ChatTrends.latestYear(report.trends.all);
        int first=ChatTrends.earliestYear(report.trends.all);
        if(analyticsCalendarYear<first||analyticsCalendarYear>latest)analyticsCalendarYear=latest;
        WrappedSummary wrap=WrappedSummary.create(report.trends,analyticsCalendarYear);
        LinearLayout card=analyticsCard("✦ Итоги года · Telegram Archive Wrapped");
        TextView choose=text("◉ Выбран "+wrap.year+" год  ·  изменить год",15,ACCENT);
        choose.setPadding(px(7),px(12),px(7),px(12));card.addView(choose);
        choose.setOnClickListener(v->{
            int from=Math.max(first,latest-79),length=latest-from+1;
            String[] years=new String[length];for(int j=0;j<length;j++)years[j]=String.valueOf(latest-j);
            new FontDialog.Builder(this).setTitle("Год итогов")
                .setItems(years,(d,index)->{analyticsCalendarYear=latest-index;refreshAnalyticsView();}).show();
        });
        analyticsLine(card,"Сообщений",wrap.messages);
        analyticsLine(card,"Дней с сообщениями",wrap.activeDays);
        analyticsLine(card,"Самая длинная серия дней",wrap.streak);
        if(wrap.peakMonth!=0)analyticsText(card,"Самый активный месяц: "+String.format(Locale.ROOT,"%02d.%04d",wrap.peakMonth,wrap.year)+
            " · "+wrap.peakMonthMessages+" сообщений",FG,13);
        if(wrap.busiestDay!=null)analyticsText(card,"Рекордный день: "+wrap.busiestDay+" · "+wrap.busiestDayMessages,FG,13);
        if(!wrap.top.isEmpty()){
            Map<String,String> authorNames=new HashMap<>();
            for(ChatAnalytics.Person p:report.people)authorNames.put(p.key,p.display);
            for(int i=0;i<Math.min(3,wrap.top.size());i++){
                WrappedSummary.Contributor top=wrap.top.get(i);
                analyticsText(card,(i+1)+". "+authorNames.getOrDefault(top.key,top.key)+": "+top.messages+" ("+
                    fmtPct(ChatAnalytics.percent(top.messages,wrap.messages))+"%)",FG,13);
            }
        }
        String[] months={"Янв","Фев","Мар","Апр","Май","Июн","Июл","Авг","Сен","Окт","Ноя","Дек"};
        List<String> labels=new ArrayList<>(),details=new ArrayList<>();
        for(int i=0;i<12;i++){labels.add(months[i]);details.add(months[i]+" "+wrap.year);}
        analyticsBarChart(card,labels,details,wrap.months,42,95);
        analyticsText(card,"Годовые итоги включают сообщения с распознанными датами. Рейтинг слов и медиатипов ниже относится ко всей переписке.",SUB,11);
        Button export=btn("↓ Сохранить итоги года в PNG");card.addView(export,p(-1,48));
        export.setOnClickListener(v->{wrappedForExport=wrap;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_TITLE,"TelegramArchive_Wrapped_"+wrap.year+".png");
            startActivityForResult(intent,WRAPPED_PNG_REQUEST);
        });
    }
    private void exportWrappedTo(Uri destination){
        final WrappedSummary snapshot=wrappedForExport;
        if(snapshot==null)return;
        worker.execute(()->{
            try(java.io.OutputStream stream=getContentResolver().openOutputStream(destination,"w")){
                if(stream==null)throw new IOException("Не удалось открыть файл");
                WrappedPoster.write(snapshot,stream);
                runOnUiThread(()->toast("Итоги года сохранены в PNG"));
            }catch(Exception error){runOnUiThread(()->toast("Ошибка сохранения PNG: "+error.getMessage()));}
        });
    }
    /** Changes only the presentation of an already-computed, immutable SQLite snapshot. */
    private void refreshAnalyticsView(){
        if(!"analytics".equals(screen)||lastAnalytics==null)return;
        final int scrollPosition=scroll.getScrollY();
        list.removeAllViews();renderAnalytics(lastAnalytics);
        scroll.post(()->{
            if(!"analytics".equals(screen)||scroll.getChildCount()==0)return;
            int maxY=Math.max(0,scroll.getChildAt(0).getHeight()-scroll.getHeight());
            scroll.scrollTo(0,Math.min(scrollPosition,maxY));
        });
    }
    private void renderTimeAnalytics(ChatAnalytics.Snapshot report){
        LinearLayout controls=analyticsCard("Графики активности");
        analyticsText(controls,"Считаются по времени из экспорта Telegram. Часовой пояс берётся из исходного экспорта без пересчёта.",SUB,11);
        HorizontalScrollView switchScroll=new HorizontalScrollView(this);switchScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout switches=row();
        switchScroll.addView(switches);controls.addView(switchScroll,p(-1,-2));
        String[] periods={"day","week","month","year"};
        String[] labels={"Дни","Недели","Месяцы","Годы"};
        for(int i=0;i<periods.length;i++){
            final String period=periods[i];
            TextView chip=text(labels[i],13,period.equals(analyticsUnit)?BG:FG);
            chip.setGravity(Gravity.CENTER);chip.setPadding(px(14),px(10),px(14),px(10));
            chip.setBackground(shape(period.equals(analyticsUnit)?ACCENT:CHIP,13));
            LinearLayout.LayoutParams pp=p(-2,-2);pp.rightMargin=px(5);switches.addView(chip,pp);
            chip.setOnClickListener(v->{analyticsUnit=period;refreshAnalyticsView();});
        }
        String chosenName="Все участники";
        for(ChatAnalytics.Person person:report.people)if(person.key.equals(analyticsSelectedAuthor))chosenName=person.display;
        if(!analyticsSelectedAuthor.isEmpty()&&chosenName.equals("Все участники"))analyticsSelectedAuthor="";
        TextView choose=text("Участник: "+chosenName+"  ▾",14,ACCENT);
        choose.setPadding(px(5),px(13),px(5),px(10));controls.addView(choose);
        choose.setOnClickListener(v->{
            String[] names=new String[report.people.size()+1];
            names[0]="Все участники";
            for(int i=0;i<report.people.size();i++)names[i+1]=report.people.get(i).display;
            new FontDialog.Builder(this).setTitle("Чья активность?").setItems(names,(dialog,which)->{
                analyticsSelectedAuthor=which==0?"":report.people.get(which-1).key;
                refreshAnalyticsView();
            }).setNegativeButton("Отмена",null).show();
        });
        ChatTrends.Activity activity=report.trends.forPerson(analyticsSelectedAuthor);
        ChatTrends.Series values=ChatTrends.series(activity,analyticsUnit);
        if(values.values.length==0){analyticsText(controls,"Нет сообщений с распознанной датой.",SUB,13);}
        else {
            analyticsBarChart(controls,values.labels,values.details,values.values,
                analyticsUnit.equals("day")?43:analyticsUnit.equals("week")?50:analyticsUnit.equals("month")?56:68,108);
            if(values.clipped)analyticsText(controls,"Показаны последние "+values.values.length+" периодов; более ранняя история скрыта только на графике.",SUB,11);
            analyticsText(controls,"Сообщений на показанном участке: "+values.visibleCount+" · нажми на столбец для точного значения.",SUB,11);
        }
        LinearLayout entire=analyticsCard("Вся история переписки · по годам");
        analyticsText(entire,"От самого первого до последнего года экспорта. Это все годы, а не только последние 30. Нажми на столбец, чтобы узнать точное количество.",SUB,12);
        ChatTrends.Series fullHistory=ChatTrends.allTimeYears(activity);
        if(fullHistory.values.length>0){
            analyticsBarChart(entire,fullHistory.labels,fullHistory.details,fullHistory.values,38,120);
            analyticsText(entire,"Всего сообщений с распознанной датой: "+fullHistory.visibleCount,SUB,12);
        }
        LinearLayout singleYear=analyticsCard("Все 12 месяцев одного года");
        int earliestYear=ChatTrends.earliestYear(activity),latestYear=ChatTrends.latestYear(activity);
        if(latestYear>0){
            if(analyticsCalendarYear<earliestYear||analyticsCalendarYear>latestYear)analyticsCalendarYear=latestYear;
            final int year=analyticsCalendarYear;
            TextView changeYear=text("Год: "+year+"  ▾",15,ACCENT);changeYear.setPadding(px(5),px(10),px(5),px(10));singleYear.addView(changeYear);
            changeYear.setOnClickListener(v->{String[] years=new String[Math.min(100,latestYear-earliestYear+1)];
                for(int k=0;k<years.length;k++)years[k]=String.valueOf(latestYear-k);
                new FontDialog.Builder(this).setTitle("Год для графика").setItems(years,(dialog,pos)->{
                    analyticsCalendarYear=latestYear-pos;refreshAnalyticsView();}).show();});
            ChatTrends.Series yearMonths=ChatTrends.twelveMonths(activity,year);
            analyticsBarChart(singleYear,yearMonths.labels,yearMonths.details,yearMonths.values,29,114);
            analyticsText(singleYear,"Все месяцы с января по декабрь видны одновременно при масштабе 1×. Столбцы пропорциональны количеству сообщений.",SUB,11);
        }
        LinearLayout hours=analyticsCard("Когда пишут · 00:00–24:00");
        analyticsText(hours,"Час отправки сообщения по исходной отметке времени, 00:00–23:59.",SUB,11);
        ArrayList<String> hLabels=new ArrayList<>(),hFull=new ArrayList<>();
        for(int i=0;i<24;i++){hLabels.add(i%3==0?String.format(Locale.ROOT,"%02d",i):"·");
            hFull.add(String.format(Locale.ROOT,"%02d:00–%02d:59",i,i));}
        analyticsBarChart(hours,hLabels,hFull,activity.hours,21,110);
        analyticsText(hours,"00 — начало суток; 24:00 — граница следующих суток, не отдельный 25-й час. Показываются интервалы от 00:00 до 23:59.",SUB,11);
        int busiest=activity.busiestHour();
        if(busiest>=0)analyticsText(hours,"Пик: "+String.format(Locale.ROOT,"%02d:00–%02d:59",busiest,busiest)+
            " · "+activity.hours[busiest]+" сообщений",ACCENT,13);
        if(activity.validHour==0)analyticsText(hours,"Нет сообщений с распознанным часом.",SUB,12);
        LinearLayout heat=analyticsCard("Тепловая карта · день недели × час");
        analyticsText(heat,"Каждая клетка подписана уровнем 0–10: 0 — нет сообщений, 10 — максимум среди 168 ячеек. Нажми для точного количества. Сетка пролистывается по горизонтали и увеличивается.",SUB,11);
        analyticsHeatmap(heat,activity);
        renderAnnualCalendar(activity);
        renderParticipantComparison(report);
        LinearLayout facts=analyticsCard("Интересные факты об активности");
        analyticsLine(facts,"Дней с сообщениями",activity.days.size());
        analyticsLine(facts,"Самая длинная серия активных дней",activity.longestStreak());
        analyticsLine(facts,"Сообщений ночью (00–05)",activity.nightCount());
        analyticsLine(facts,"Сообщений с вопросами",activity.questions);
        analyticsLine(facts,"Сообщений со ссылками",activity.linkMessages);
        analyticsLine(facts,"Начало сессий после ≥6 ч тишины",activity.sessionsStarted);
        if(!activity.days.isEmpty())analyticsText(facts,"В среднем "+fmtPct((double)activity.validDate/activity.days.size())+
            " сообщений в активный день",SUB,12);
        int weekday=activity.busiestWeekday();
        if(weekday>=0)analyticsText(facts,"Самый активный день недели: "+ChatTrends.WEEKDAYS[weekday],ACCENT,13);
        if(activity.unknownDate>0||activity.unknownHour>0){
            analyticsText(facts,"Не удалось определить дату: "+activity.unknownDate+
                " · час: "+activity.unknownHour+". Эти сообщения есть в общем счётчике, но не на соответствующих графиках.",SUB,11);
        }
        LinearLayout emojis=analyticsCard("Любимые эмодзи");
        List<ChatTrends.Emoji> favourites=activity.topEmoji(12);
        if(favourites.isEmpty())analyticsText(emojis,"Эмодзи в тексте пока нет.",SUB,12);
        if(activity.emojiVocabularyLimited)analyticsText(emojis,"Необычно большой набор: показаны наиболее частые из первых 512 различных эмодзи.",SUB,11);
        for(ChatTrends.Emoji emoji:favourites){
            TextView e=text("«"+emoji.value+"»  × "+emoji.count,19,FG);e.setPadding(px(6),px(6),px(6),px(6));
            emojis.addView(e);e.setOnClickListener(v->{q=emoji.value;deep=true;searchOffset=0;screen="search";showPage();});
        }
        analyticsText(facts,"Сессия — условный отрезок общения после паузы ≥6 часов, а не доказательство ответа конкретному человеку. Ночные часы и дни недели не переводятся между часовыми поясами.",SUB,11);
    }
    /** Exact values remain visible at narrow widths and with large system fonts. */
    private void analyticsBarChart(LinearLayout parent,List<String> labels,List<String> details,long[] values,int widthDp,int heightDp){
        if(values.length==0){analyticsText(parent,"Нет данных за этот период",SUB,14);return;}
        long max=0;for(long value:values)max=Math.max(max,value);
        analyticsText(parent,"Количество сообщений · общая шкала 0–"+max+". Значения указаны точно, без округления.",SUB,12);
        for(int i=0;i<values.length;i++){
            final long count=values[i];final String detail=details.get(i);
            LinearLayout block=column();block.setPadding(px(4),px(8),px(4),px(8));parent.addView(block,p(-1,-2));
            TextView label=text(detail+"  ·  "+count,14,FG);label.setTextIsSelectable(true);
            label.setContentDescription(detail+": "+count+" сообщений");block.addView(label,p(-1,-2));
            FrameLayout track=new FrameLayout(this);track.setBackground(shape(CHIP,4));
            block.addView(track,p(-1,px(8)));
            View bar=new View(this);bar.setBackground(shape(ACCENT,4));
            track.addView(bar,new FrameLayout.LayoutParams(-1,-1));bar.setPivotX(0f);
            bar.setScaleX(max==0?0f:count/(float)max);
        }
    }
    /** Render large amounts compactly; tapping still reveals the exact amount. */
    private static String analyticsShortCount(long value){
        if(value<10000)return String.valueOf(value);
        if(value<1000000)return String.format(Locale.forLanguageTag("ru-RU"),"%.1fк",value/1000.0);
        return String.format(Locale.forLanguageTag("ru-RU"),"%.1fм",value/1000000.0);
    }

    /** Six four-hour columns, all seven weekdays: every cell shows message counts, not only colours. */
    private void analyticsHeatmap(LinearLayout parent,ChatTrends.Activity activity){
        analyticsText(parent,"Столбцы обозначают ВРЕМЯ: 00–03 означает от 00:00 до 03:59. " +
            "В каждой клетке указано ЧИСЛО сообщений, а не цветовой балл. " +
            "Для отдельных часов используй график «Когда пишут» выше.",SUB,12);
        long[][] counts=new long[7][6];long max=0;
        for(int day=0;day<7;day++)for(int block=0;block<6;block++){
            for(int hour=block*4;hour<block*4+4;hour++)counts[day][block]+=activity.weekdayHours[day][hour];
            max=Math.max(max,counts[day][block]);
        }
        LinearLayout header=row();parent.addView(header,p(-1,px(29)));
        TextView corner=text("День",10,SUB);header.addView(corner,new LinearLayout.LayoutParams(0,-1,0.9f));
        for(int block=0;block<6;block++){
            int begin=block*4;
            TextView hour=text(String.format(Locale.ROOT,"%02d–%02d",begin,begin+3),10,FG);
            hour.setGravity(Gravity.CENTER);header.addView(hour,new LinearLayout.LayoutParams(0,-1,1f));
        }
        for(int day=0;day<7;day++){
            LinearLayout line=row();parent.addView(line,p(-1,px(47)));
            TextView weekday=text(ChatTrends.WEEKDAYS[day],12,SUB);
            weekday.setGravity(Gravity.CENTER_VERTICAL);line.addView(weekday,new LinearLayout.LayoutParams(0,-1,0.9f));
            for(int block=0;block<6;block++){
                final int d=day,b=block;final long amount=counts[day][block];
                int heatBackground=analyticsHeatColor(max==0?0:(float)amount/max);
                TextView square=text(analyticsShortCount(amount),11,
                    AnalyticsContrastPolicy.readableTextColor(heatBackground,Color.BLACK,FG));
                square.setGravity(Gravity.CENTER);square.setSingleLine(true);
                square.setBackground(shape(heatBackground,5));
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,px(42),1f);
                cell.setMargins(px(1),px(2),px(1),px(2));line.addView(square,cell);
                square.setContentDescription(ChatTrends.WEEKDAYS[d]+", "+(b*4)+":00–"+(b*4+3)+":59 — "+amount+" сообщений");
                square.setOnClickListener(v->toast(ChatTrends.WEEKDAYS[d]+", "+
                    String.format(Locale.ROOT,"%02d:00–%02d:59",b*4,b*4+3)+": "+amount+" сообщений"));
            }
        }
        analyticsText(parent,"Серый = 0 сообщений; зелёный = относительно мало, жёлтый = больше, оранжевый = больше всего. " +
            "Ориентируйся прежде всего на ЧИСЛО в клетке.",SUB,11);
    }

    /** Calendar is a view of the cached snapshot; no SQLite query or media access. */
    /** Readable annual calendar: one vertical month at a time, seven day columns, no cut-off. */
    private void renderAnnualCalendar(ChatTrends.Activity activity){
        LinearLayout container=analyticsCard("Календарь активности · по месяцам");
        int earliest=ChatTrends.earliestYear(activity),latest=ChatTrends.latestYear(activity);
        if(latest==0){analyticsText(container,"Нет сообщений с распознанной датой.",SUB,12);return;}
        if(analyticsCalendarYear<earliest||analyticsCalendarYear>latest)analyticsCalendarYear=latest;
        final int year=analyticsCalendarYear;
        LinearLayout navigation=row();navigation.setGravity(Gravity.CENTER_VERTICAL);container.addView(navigation,p(-1,-2));
        Button prev=btn("‹ "+(year-1));navigation.addView(prev,new LinearLayout.LayoutParams(0,px(45),1));
        prev.setEnabled(year>earliest);prev.setOnClickListener(v->{analyticsCalendarYear=year-1;refreshAnalyticsView();});
        TextView current=text(String.valueOf(year),19,FG);current.setGravity(Gravity.CENTER);navigation.addView(current,p(px(75),px(45)));
        Button next=btn((year+1)+" ›");navigation.addView(next,new LinearLayout.LayoutParams(0,px(45),1));
        next.setEnabled(year<latest);next.setOnClickListener(v->{analyticsCalendarYear=year+1;refreshAnalyticsView();});
        analyticsText(container,"Каждый день: верхнее число — дата, нижнее — сообщений. «—» означает 0. " +
            "Цвет дополнительный, данные читаются и без него. Нажми на день для точного числа.",SUB,12);
        String[] weekdays={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
        for(int month=1;month<=12;month++){
            LocalDate first=LocalDate.of(year,month,1);
            long monthSum=0;
            for(int day=1;day<=first.lengthOfMonth();day++)monthSum+=activity.days.getOrDefault(first.withDayOfMonth(day),0L);
            TextView heading=text(first.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE,
                Locale.forLanguageTag("ru-RU"))+" · "+monthSum+" сообщений",14,FG);
            heading.setTypeface(null,Typeface.BOLD);heading.setPadding(px(5),px(16),0,px(6));container.addView(heading);
            LinearLayout header=row();container.addView(header,p(-1,px(25)));
            for(String dayName:weekdays){TextView cell=text(dayName,11,SUB);cell.setGravity(Gravity.CENTER);
                header.addView(cell,new LinearLayout.LayoutParams(0,-1,1f));}
            int offset=first.getDayOfWeek().getValue()-1;
            int rows=(offset+first.lengthOfMonth()+6)/7;
            for(int r=0;r<rows;r++){
                LinearLayout week=row();container.addView(week,p(-1,px(45)));
                for(int col=0;col<7;col++){
                    int day=r*7+col-offset+1;
                    boolean valid=day>=1&&day<=first.lengthOfMonth();
                    LocalDate date=valid?first.withDayOfMonth(day):null;
                    long amount=date==null?0:activity.days.getOrDefault(date,0L);
                    String label=valid?(day+"\n"+(amount==0?"—":analyticsShortCount(amount))):"";
                    int dayBackground=amount==0?0xff293449:analyticsHeatColor(
                        Math.min(1f,(float)Math.sqrt(amount/Math.max(1.0,300.0))));
                    TextView cell=text(label,10,
                        AnalyticsContrastPolicy.readableTextColor(dayBackground,Color.BLACK,FG));
                    cell.setGravity(Gravity.CENTER);
                    if(valid)cell.setBackground(shape(dayBackground,5));
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,px(42),1f);
                    cp.setMargins(px(1),px(1),px(1),px(1));week.addView(cell,cp);
                    if(valid){cell.setContentDescription(date+": "+amount+" сообщений");
                        cell.setOnClickListener(v->toast(date+": "+amount+" сообщений"));}
                }
            }
        }
    }

    /** The two sender profiles are compared over identical calendar months. */
    private void renderParticipantComparison(ChatAnalytics.Snapshot report){
        if(report.people.size()<2||report.trends.all.days.isEmpty())return;
        LinearLayout container=analyticsCard("Сравнение двух участников · 12 месяцев");
        if(analyticsCompareFirst.isEmpty()||!report.trends.people.containsKey(analyticsCompareFirst))
            analyticsCompareFirst=report.people.get(0).key;
        if(analyticsCompareSecond.isEmpty()||!report.trends.people.containsKey(analyticsCompareSecond))
            analyticsCompareSecond=report.people.get(1).key;
        LinearLayout choose=row();container.addView(choose,p(-1,-2));
        String[] selected={analyticsCompareFirst,analyticsCompareSecond};
        for(int side=0;side<2;side++){
            final int index=side;
            String name=selected[side];for(ChatAnalytics.Person p:report.people)if(p.key.equals(selected[side]))name=p.display;
            TextView picker=text((side==0?"А: ":"Б: ")+name+" ▾",12,side==0?ACCENT:FG);
            picker.setGravity(Gravity.CENTER);picker.setPadding(px(6),px(12),px(6),px(12));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);choose.addView(picker,cp);
            picker.setOnClickListener(v->{
                String[] names=new String[report.people.size()];
                for(int i=0;i<report.people.size();i++)names[i]=report.people.get(i).display;
                new FontDialog.Builder(this).setTitle(index==0?"Участник А":"Участник Б")
                    .setItems(names,(dialog,position)->{
                        if(index==0)analyticsCompareFirst=report.people.get(position).key;
                        else analyticsCompareSecond=report.people.get(position).key;
                        refreshAnalyticsView();
                    }).setNegativeButton("Отмена",null).show();
            });
        }
        if(analyticsCompareFirst.equals(analyticsCompareSecond)){
            analyticsText(container,"Выбери двух разных участников.",SUB,12);return;
        }
        LocalDate latest=report.trends.all.days.lastKey();
        ChatTrends.MonthlyComparison comparison=ChatTrends.compareMonths(
            report.trends.forPerson(analyticsCompareFirst),report.trends.forPerson(analyticsCompareSecond),
            latest.getYear(),latest.getMonthValue());
        long combined=comparison.firstTotal+comparison.secondTotal;
        analyticsText(container,"Последние 12 месяцев до "+latest.getYear()+"-"+
            String.format(Locale.ROOT,"%02d",latest.getMonthValue())+
            " · А: "+comparison.firstTotal+" ("+fmtPct(ChatAnalytics.percent(comparison.firstTotal,combined))+"%)"+
            " · Б: "+comparison.secondTotal+" ("+fmtPct(ChatAnalytics.percent(comparison.secondTotal,combined))+"%)",SUB,11);
        analyticsText(container,"А — ГОЛУБОЙ ●; Б — ОРАНЖЕВЫЙ ■. В каждом месяце показаны ТОЧНЫЕ значения обоих участников.",SUB,12);
        long peak=1;for(long n:comparison.first)peak=Math.max(peak,n);
        for(long n:comparison.second)peak=Math.max(peak,n);
        for(int i=0;i<12;i++){
            final long a=comparison.first[i],b=comparison.second[i];
            final String period=comparison.labels.get(i);
            LinearLayout block=column();block.setPadding(px(5),px(7),px(5),px(7));container.addView(block,p(-1,-2));
            TextView month=text(period+"  ·  ● А: "+a+"   ■ Б: "+b,12,FG);
            month.setContentDescription(period+": участник А "+a+", участник Б "+b);
            block.addView(month,p(-1,-2));
            LinearLayout tracks=row();block.addView(tracks,p(-1,px(19)));
            long[] values={a,b};int[] colors={0xff7ddaff,0xffffa46b};
            for(int side=0;side<2;side++){
                FrameLayout track=new FrameLayout(this);
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,px(16),1f);cp.setMargins(px(2),0,px(2),0);
                tracks.addView(track,cp);track.setBackground(shape(CHIP,4));
                View fill=new View(this);fill.setBackground(shape(colors[side],4));
                track.addView(fill,new FrameLayout.LayoutParams(-1,-1));
                final float fraction=values[side]/(float)peak;
                fill.setScaleX(fraction);fill.setPivotX(0f);
            }
        }
        analyticsText(container,"А (голубой) и Б (оранжевый) сравниваются по точным числам. Сравниваются сообщения, отправленные каждым участником, без предположений об адресате.",SUB,11);
    }
    private static int analyticsHeatColor(float fraction){
        if(fraction<=0)return 0xff293449;
        if(fraction<0.30f)return 0xff3bbd96;
        if(fraction<0.65f)return 0xffffc857;
        return 0xffed744b;
    }
    private static int analyticsMix(int from,int to,float fraction){
        float ratio=Math.max(0f,Math.min(1f,fraction));
        int r=(int)(Color.red(from)*(1-ratio)+Color.red(to)*ratio);
        int g=(int)(Color.green(from)*(1-ratio)+Color.green(to)*ratio);
        int b=(int)(Color.blue(from)*(1-ratio)+Color.blue(to)*ratio);
        return Color.rgb(r,g,b);
    }
    private LinearLayout analyticsCard(String caption){
        LinearLayout card=column();card.setPadding(px(13),px(12),px(13),px(14));card.setBackground(shape(PANEL,18));
        LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(5),px(5),px(5),px(11));list.addView(card,lp);
        TextView title=text(caption,16,FG);title.setTypeface(null,Typeface.BOLD);title.setPadding(0,0,0,px(8));card.addView(title);
        return card;
    }
    private void analyticsLine(LinearLayout parent,String name,long value){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);parent.addView(line,p(-1,-2));
        TextView key=text(name,12,SUB);key.setPadding(0,px(3),0,px(3));line.addView(key,new LinearLayout.LayoutParams(0,-2,1));
        TextView number=text(String.format(Locale.ROOT,"%,d",value).replace(',', ' '),13,FG);
        number.setTypeface(null,Typeface.BOLD);line.addView(number);
    }
    private void analyticsText(LinearLayout parent,String value,int color,int size){
        TextView t=text(value,size,color);t.setPadding(px(4),px(4),px(4),px(4));parent.addView(t);
    }
    private static String fmtPct(double n){return String.format(Locale.forLanguageTag("ru-RU"),"%.1f",n);}

    private String clip(String s,int max){if(s==null)return "";return s.length()>max?s.substring(0,max)+"…":s;}
    private String day(String s){if(s==null)return "";if(s.length()<10)return s;return s.substring(0,10);}
    private String clock(String s){if(s==null)return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\b([0-2]?\\d:[0-5]\\d)(?::[0-5]\\d)?\\b").matcher(s);
        return m.find()?m.group(1):clip(s,22);
    }
    private void dateSeparator(String date){
        TextView d=text(date.isEmpty()?"Дата неизвестна":date,11,SUB);d.setGravity(Gravity.CENTER);
        d.setPadding(px(12),px(7),px(12),px(7));d.setBackground(shape(0xff23384b,14));
        LinearLayout r=row();r.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=p(-1,38);rp.topMargin=px(10);list.addView(r,rp);r.addView(d);
    }
    private CharSequence highlighted(String body,String find){
        if(body==null)return "";if(find==null||find.trim().isEmpty())return body;
        SpannableString span=new SpannableString(body);String hay=body.toLowerCase(Locale.ROOT);
        ArrayList<String> needles=new ArrayList<>();
        if(deep)needles.add(find.toLowerCase(Locale.ROOT));
        else for(String token:find.split("[^\\p{L}\\p{N}]+"))if(!token.isEmpty())needles.add(token.toLowerCase(Locale.ROOT));
        for(String needle:needles){int from=0,n=0,at;
            while(!needle.isEmpty()&&(at=hay.indexOf(needle,from))>=0&&n++<30){
                span.setSpan(new BackgroundColorSpan(0xffa37528),at,at+needle.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from=at+needle.length();
            }
        }return span;
    }
    private void renderMessages(List<MessageDb.Item> messages,Map<Long,List<MessageDb.Attachment>> files,Map<Long,MessageDb.ReplyInfo> replies,boolean results,boolean append){
        if(messages.isEmpty())return;
        if(!append)renderedMessageViews.clear();
        String last=append?lastRenderedDay:"";View focus=null;View glowBubble=null;int glowBase=0;long glowId=-1;
        for(MessageDb.Item m:messages){
            String date=day(m.date);if(!date.equals(last)){dateSeparator(date);last=date;}
            boolean own=!mine.isEmpty()&&mine.equalsIgnoreCase(m.author==null?"":m.author.trim());
            LinearLayout line=row();line.setTag(Long.valueOf(m.id));line.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
            LinearLayout.LayoutParams linep=p(-1,-2);linep.setMargins(px(2),px(1),px(2),px(densityIndex==0?2:densityIndex==2?8:4));list.addView(line,linep);
            if(!own){TextView avatar=text(initial(m.author),14,FG);avatar.setTypeface(null,Typeface.BOLD);
                avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(authorColor(m.author),100));
                String avatarFile=getPreferences(MODE_PRIVATE).getString(PersonalImages.avatarKey(activeKey,m.author),"");
                Bitmap portrait=PersonalImages.circularCached(this,avatarFile,px(32));
                if(portrait!=null){avatar.setText("");avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),portrait));}
                LinearLayout.LayoutParams ap=p(32,32);ap.rightMargin=px(7);line.addView(avatar,ap);}
            List<MessageDb.Attachment> attachments=files.get(m.id);
            boolean roundOnly=attachments!=null&&attachments.size()==1&&"round".equals(attachments.get(0).kind)
                &&(m.body==null||m.body.trim().matches("[.·•]*"));
            LinearLayout bubble=column();
            bubble.setPadding(px(roundOnly?0:bubbleInset()),px(roundOnly?0:densityIndex==0?3:densityIndex==2?10:6),
                px(roundOnly?0:bubbleInset()),px(roundOnly?0:densityIndex==0?3:densityIndex==2?11:7));
            int chosenColor=authorColor(m.author);
            if(roundOnly)bubble.setBackgroundColor(Color.TRANSPARENT);
            else bubble.setBackground(gradient(m.id==highlightId?HIGHLIGHT:chosenColor,
                m.id==highlightId?HIGHLIGHT:darken(chosenColor),bubbleRadius));
            if(m.id==highlightId){glowBubble=bubble;glowBase=chosenColor;glowId=m.id;line.setBackground(shape(0x443ad0fc,12));}
            LinearLayout.LayoutParams bp=p(-2,-2);line.addView(bubble,bp);
            if(own){
                String ownFile=getPreferences(MODE_PRIVATE).getString(PersonalImages.avatarKey(activeKey,m.author),"");
                Bitmap ownPhoto=PersonalImages.circularCached(this,ownFile,px(30));
                if(ownPhoto!=null){
                    ImageView ownAvatar=new ImageView(this);
                    ownAvatar.setImageBitmap(ownPhoto);
                    LinearLayout.LayoutParams avatarParams=p(30,30);avatarParams.leftMargin=px(7);
                    line.addView(ownAvatar,avatarParams);
                }
            }
            int maxWidth=getResources().getDisplayMetrics().widthPixels-px(93);
            if(maxWidth<px(190))maxWidth=px(240);final int mw=maxWidth;
            bubble.setOnLongClickListener(v->{messageActions(m);return true;});
            String author=m.author==null||m.author.isEmpty()?"Неизвестный отправитель":m.author;
            if(!roundOnly){
                TextView from=text(author,12,0xffe8f6ff);from.setMaxWidth(mw);from.setTypeface(null,Typeface.BOLD);bubble.addView(from);
                from.setOnLongClickListener(v->{pickAuthorColor(author);return true;});
            }
            MessageDb.ReplyInfo reply=replies.get(m.id);
            if(reply!=null&&!roundOnly){
                final String reference=reply.targetId>0?
                    "↪ "+(reply.author.isEmpty()?"Ответ на сообщение":reply.author)+" · "+
                    (reply.preview.isEmpty()?"Сообщение с вложением":reply.preview):
                    "↪ Ответ на сообщение Telegram #"+reply.telegramId+" (оригинал отсутствует)";
                TextView link=text(reference,12,reply.targetId>0?0xffd9faff:SUB);
                link.setMaxWidth(mw);link.setMaxLines(2);link.setEllipsize(TextUtils.TruncateAt.END);
                link.setPadding(px(10),px(8),px(10),px(8));
                link.setBackground(shape(0x44345b76,8));
                bubble.addView(link,p(-1,-2));
                if(reply.targetId>0)link.setOnClickListener(v->jump(reply.targetId,""));
            }
            if(m.body!=null&&!m.body.isEmpty()&&!roundOnly){
                TextView body=text("",15,FG);body.setMaxWidth(mw);body.setText(highlighted(m.body,results?q:""));
                body.setPadding(0,px(5),0,px(4));body.setLineSpacing(px(2),1.0f);bubble.addView(body);
            }
            if(attachments!=null)for(MessageDb.Attachment a:attachments)bubble.addView(mediaTile(a,false),p(-2,-2));
            LinearLayout footer=row();footer.setGravity(Gravity.RIGHT);
            TextView time=text(clock(m.date),11,SUB);
            footer.addView(time);
            if(roundOnly){time.setTextSize(10);time.setTextColor(SUB);}
            bubble.addView(footer);
            // Favorites are changed only in the long-press actions menu: no accidental tap targets.
            if(results){
                TextView open=text("↗ К сообщению в чате",12,ACCENT);open.setPadding(0,px(8),0,px(2));bubble.addView(open);
                open.setOnClickListener(v->jump(m.id,"search"));
                bubble.setOnClickListener(v->jump(m.id,"search"));
            }
            renderedMessageViews.put(m.id,line);
            if(m.id==highlightId)focus=line;
        }
        lastRenderedDay=last;
        if(append)return; // Never jump to the top when more history is appended.
        if(pendingRestoreId>0){
            final View restored=renderedMessageViews.get(pendingRestoreId);
            final int top=pendingRestoreTop;
            pendingRestoreId=-1;
            if(restored!=null){scroll.post(()->scroll.scrollTo(0,Math.max(0,restored.getTop()-top)));return;}
        }
        final View target=focus;
        final View targetBubble=glowBubble;
        final int color=glowBase;
        final long id=glowId;
        scroll.post(()->{
            if(target!=null){
                scroll.smoothScrollTo(0,Math.max(0,target.getTop()-px(120)));
                if(targetBubble!=null){
                    targetBubble.postDelayed(()->pulseMessage(targetBubble,color,id),190);
                }
            }else scroll.scrollTo(0,0);
        });
    }
    private int mixColors(int a,int b,float t){
        return Color.rgb(
            Math.round(Color.red(a)*(1-t)+Color.red(b)*t),
            Math.round(Color.green(a)*(1-t)+Color.green(b)*t),
            Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));
    }
    private void pulseMessage(View bubble,int background,long id){
        if(highlightId!=id||!screen.equals("chat")||home)return;
        android.graphics.drawable.Drawable drawable=bubble.getBackground();
        if(!(drawable instanceof GradientDrawable))return;
        GradientDrawable bg=(GradientDrawable)drawable;
        ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(1750);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation->{
            if(bubble.getParent()==null)return;
            float t=(float)animation.getAnimatedValue();
            bg.setColors(new int[]{mixColors(0xff78cae4,background,t),mixColors(0xff397c9d,darken(background),t)});
        });
        animator.start();
        handler.postDelayed(()->{if(highlightId==id)highlightId=-1;},1850);
    }
    private String initial(String s){if(s==null||s.isEmpty())return "?";return s.substring(0,1).toUpperCase(Locale.ROOT);}
    private void messageActions(MessageDb.Item m){
        boolean outOfChat=!"chat".equals(screen);
        ArrayList<String> actions=new ArrayList<>();
        actions.add(m.favorite?"Убрать из избранного":"★ Добавить в избранное");
        actions.add(db.isPinned(m.id)?"Открепить":"📌 Закрепить сообщение");
        actions.add("◷ Отметить начало периода");
        actions.add("◷ Отметить конец периода");
        actions.add("♡ Добавить событие истории");
        actions.add("❞ Цитировать фрагмент");
        actions.add("Скопировать текст");
        actions.add("↓ Сохранить сообщение в TXT");
        actions.add("↓ Сохранить вложение…");
        if(outOfChat)actions.add("↗ Перейти к сообщению");
        new FontDialog.Builder(this).setTitle("Сообщение · "+clock(m.date))
            .setItems(actions.toArray(new String[0]),(dialog,which)->{
                if(which==0)toggleStar(m.id);
                else if(which==1){worker.execute(()->{boolean pin=db.togglePinned(m.id);runOnUiThread(()->{toast(pin?"Закреплено":"Откреплено");renderPinnedBar();});});}
                else if(which==2)selectPeriodBoundary(m,true);
                else if(which==3)selectPeriodBoundary(m,false);
                else if(which==4)createRelationshipEventDialog(m);
                else if(which==5)quoteMessage(m);
                else if(which==6){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Telegram message",m.body==null?"":m.body));toast("Текст скопирован");}
                else if(which==7)saveMessageText(m);
                else if(which==8)chooseMessageAttachment(m.id);
                else jump(m.id,screen.equals("search")?"search":screen.equals("favorites")?"favorites":"");
            }).show();
    }
    /** Opt-in personal timeline. A user chooses every label and both message boundaries. */
    private void selectPeriodBoundary(MessageDb.Item message,boolean start){
        LinearLayout content=column();content.setPadding(px(18),px(8),px(18),px(6));
        String description=start?"Это НАЧАЛО этапа. Зажми текст, выдели слово или поставь курсор. Если ничего не выделено, начало — первый символ сообщения.":
                "Это КОНЕЦ этапа. Зажми текст, выдели слово или поставь курсор. Если ничего не выделено, конец — последний символ сообщения.";
        TextView instructions=text(description,13,SUB);content.addView(instructions);
        TextView original=text(message.body==null||message.body.isEmpty()?"(Сообщение без текста)":message.body,16,FG);
        original.setTextIsSelectable(true);original.setPadding(px(12),px(10),px(12),px(10));
        original.setBackground(glass(PANEL,12));content.addView(original,p(-1,-2));
        new FontDialog.Builder(this).setTitle(start?"◷ Начало периода":"◷ Конец периода")
            .setView(content).setNegativeButton("Отмена",null).setPositiveButton("Отметить",(dialog,which)->{
                String source=message.body==null?"":message.body;
                int a=Selection.getSelectionStart(original.getText()),b=Selection.getSelectionEnd(original.getText());
                int offset=start?(a>=0?Math.min(a,b):0):(b>=0?Math.max(a,b):source.length());
                offset=Math.max(0,Math.min(source.length(),offset));
                ConversationPeriods.Boundary selected=new ConversationPeriods.Boundary(message.id,offset);
                if(start){pendingPeriodStart=selected;pendingPeriodArchive=activeKey;
                    toast("Начало отмечено. Найди последнее сообщение этапа и выбери «Конец периода».");
                }else{
                    if(pendingPeriodStart==null||!activeKey.equals(pendingPeriodArchive)){
                        toast("Сначала отметь начало этапа в этой переписке");return;}
                    if(pendingPeriodStart.compareTo(selected)>0){toast("Конец этапа раньше начала. Отметь более позднее сообщение.");return;}
                    createPeriodDialog(pendingPeriodStart,selected);
                }
            }).show();
    }
    private void createPeriodDialog(ConversationPeriods.Boundary start,ConversationPeriods.Boundary end){
        LinearLayout form=column();form.setPadding(px(18),px(5),px(18),px(6));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: Знакомство");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ConversationPeriods.MAX_NAME)});
        form.addView(name,p(-1,-2));
        EditText note=new EditText(this);note.setHint("Что происходило в этот период? (необязательно)");
        note.setMinLines(2);note.setMaxLines(4);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(ConversationPeriods.MAX_NOTE)});
        form.addView(note,p(-1,-2));
        AlertDialog dialog=new FontDialog.Builder(this).setTitle("Новый этап истории")
            .setMessage("Этап хранится только на устройстве. Название выбираешь ты, приложение не оценивает отношения.")
            .setNeutralButton("Как это работает",(d,w)->openHelpChapter(21)).setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            final String label=name.getText().toString().trim(),comment=note.getText().toString().trim();
            try{ConversationPeriods.validate(label,comment,start,end);}catch(IllegalArgumentException e){name.setError(e.getMessage());return;}
            final MessageDb source=db;final String archive=activeKey;
            worker.execute(()->{try{source.addPeriod(label,comment,start,end);
                runOnUiThread(()->{if(!archive.equals(activeKey)||isFinishing())return;
                    pendingPeriodStart=null;pendingPeriodArchive="";dialog.dismiss();screen="chronology";showPage();});
            }catch(Exception e){runOnUiThread(()->toast("Не сохранилось: "+e.getMessage()));}});
        }));dialog.show();
    }
    /** Opt-in relationship workspace: no automatic categorization or upload. */
    private void prepareRelationshipScreen(String heading,String description){
        if(scheduledPrefetch!=null){handler.removeCallbacks(scheduledPrefetch);scheduledPrefetch=null;}
        bottomNav.setVisibility(View.GONE);pinnedBar.removeAllViews();
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        savedComposer.setVisibility(View.GONE);list.removeAllViews();list.setBackgroundColor(BG);
        TextView title=text(heading,22,FG);title.setTypeface(null,Typeface.BOLD);
        title.setPadding(px(12),px(13),px(12),px(8));list.addView(title);
        analyticsText(list,description,SUB,12);
    }
    private void createRelationshipEventDialog(MessageDb.Item m){
        if(home||db==null)return;
        LinearLayout form=column();form.setPadding(px(16),px(4),px(16),px(8));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: Первая встреча");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(90)});
        form.addView(name,p(-1,-2));
        EditText description=new EditText(this);description.setHint("Что случилось и почему это важно? (необязательно)");
        description.setMinLines(3);description.setMaxLines(6);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2500)});
        form.addView(description,p(-1,-2));
        TextView explain=text("Событие привяжется к выбранному сообщению. Переписка не меняется. После сохранения можно прикрепить фото, видео, кружки, голосовые и файлы ИЗ ЭТОГО АРХИВА.",12,SUB);
        form.addView(explain);
        new FontDialog.Builder(this).setTitle("♡ Событие в истории")
            .setNeutralButton("Как это работает",(d,w)->openHelpChapter(24)).setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(dialog,which)->{
                String label=name.getText().toString(),note=description.getText().toString();
                if(label.trim().isEmpty()){toast("Название не может быть пустым");return;}
                final MessageDb source=db;final String key=activeKey;
                worker.execute(()->{
                    try{source.addRelationshipEvent(label,note,m.id,0);
                        runOnUiThread(()->{if(key.equals(activeKey))toast("Событие сохранено → меню ⋮ → Хронология");});
                    }catch(Exception e){runOnUiThread(()->toast("Событие не сохранено: "+e.getMessage()));}
                });
            }).show();
    }
    private LinearLayout relationshipCard(LinearLayout parent,String title){
        LinearLayout card=column();card.setPadding(px(13),px(12),px(13),px(13));
        card.setBackground(glass(PANEL,17));
        LinearLayout.LayoutParams params=p(-1,-2);params.setMargins(px(6),px(5),px(6),px(9));
        parent.addView(card,params);
        TextView heading=text(title,16,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(0,0,0,px(8));card.addView(heading);
        return card;
    }
    private void renderRelationshipsPage(){
        int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        prepareRelationshipScreen("♡ Хронология","Личный архив истории: этапы, события, медиа и повторяющиеся выражения. Всё включается вручную и остаётся на телефоне.");
        Button stages=btn("◷ Этапы истории и сравнение графиков");list.addView(stages,p(-1,52));
        stages.setOnClickListener(v->{screen="chronology";showPage();});
        Button echoes=btn("❞ Эхо фраз — найти повторения");list.addView(echoes,p(-1,52));
        echoes.setOnClickListener(v->{screen="echo";showPage();});
        Button fullCompare=btn("⇄ Сравнение диалогов · два чата");list.addView(fullCompare,p(-1,52));
        fullCompare.setOnClickListener(v->chooseDualChat("dual_compare"));
        Button sharedMemories=btn("◷ Воспоминания · общая временная линия");list.addView(sharedMemories,p(-1,52));
        sharedMemories.setOnClickListener(v->chooseDualChat("dual_memories"));
        Button comparison=btn("▦ Параллельные календари двух чатов");list.addView(comparison,p(-1,52));
        comparison.setOnClickListener(v->chooseParallelCalendar());
        Button cinema=btn("▤ Кинолента: медиа по годам");list.addView(cinema,p(-1,52));
        cinema.setOnClickListener(v->{screen="cinema";showPage();});
        Button health=btn("◇ Проверить сохранность медиа");list.addView(health,p(-1,52));
        health.setOnClickListener(v->showMediaHealth());
        analyticsText(list,"Как создать событие: вернись в переписку, удерживай нужное сообщение → «♡ Добавить событие истории». Затем открой этот раздел и прикрепи медиа. Удаление события не удаляет переписку или файлы.",SUB,12);
        LinearLayout eventsHolder=column();list.addView(eventsHolder,p(-1,-2));
        final MessageDb source=db;final String key=activeKey;
        worker.execute(()->{
            try{
                List<MessageDb.RelationshipEvent> all=source.relationshipEvents();
                int start=Math.min(relationshipPageIndex*20,Math.max(0,all.size()-1));
                if(start!=relationshipPageIndex*20){relationshipPageIndex=start/20;start=relationshipPageIndex*20;}
                int end=Math.min(all.size(),start+20);
                List<MessageDb.RelationshipEvent> visible=new ArrayList<>(all.subList(start,end));
                LinkedHashMap<Long,List<MessageDb.Attachment>> links=new LinkedHashMap<>();
                for(MessageDb.RelationshipEvent e:visible)links.put(e.id,source.relationshipMedia(e.id));
                final int first=start;
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken||!key.equals(activeKey)||db!=source)return;
                    eventsHolder.removeAllViews();
                    TextView heading=text("Мои события · "+all.size(),18,FG);heading.setTypeface(null,Typeface.BOLD);
                    eventsHolder.addView(heading,p(-1,50));
                    if(all.isEmpty())analyticsText(eventsHolder,"Событий пока нет. Они создаются через долгое нажатие на сообщение.",SUB,13);
                    for(MessageDb.RelationshipEvent event:visible){
                        LinearLayout card=relationshipCard(eventsHolder,"♡ "+event.title);
                        analyticsText(card,"Сообщение #"+event.anchorId+" · исходная точка в переписке",SUB,12);
                        if(!event.note.isEmpty())analyticsText(card,event.note,FG,13);
                        List<MessageDb.Attachment> attachments=links.get(event.id);
                        if(attachments!=null)for(MessageDb.Attachment a:attachments){
                            Button attachment=btn("▣ "+mediaLabel(a.kind)+" · открыть вложение");
                            card.addView(attachment,p(-1,43));attachment.setOnClickListener(v->openMedia(a));
                        }
                        if(event.linkedCount>0&&(attachments==null||attachments.size()<event.linkedCount))
                            analyticsText(card,"Некоторые ссылки не разрешились: исходные файлы могли измениться после импорта.",SUB,11);
                        Button anchor=btn("↗ К исходному сообщению");card.addView(anchor,p(-1,43));
                        anchor.setOnClickListener(v->jump(event.anchorId,"relationships"));
                        Button attach=btn("+ Прикрепить медиа из архива");card.addView(attach,p(-1,43));
                        attach.setOnClickListener(v->chooseRelationshipAttachment(event.id,0));
                        Button remove=btn("Удалить отметку (не файл)");card.addView(remove,p(-1,42));
                        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить событие?")
                            .setMessage("Удалится только описание и список ссылок. Все исходные файлы и сообщения останутся.")
                            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,which)->worker.execute(()->{
                                source.removeRelationshipEvent(event.id);
                                runOnUiThread(()->{if(key.equals(activeKey)&&screen.equals("relationships"))renderRelationshipsPage();});
                            })).show());
                    }
                    if(all.size()>20){
                        LinearLayout pager=row();eventsHolder.addView(pager,p(-1,52));
                        Button prev=btn("‹ Раньше");pager.addView(prev,new LinearLayout.LayoutParams(0,px(46),1));
                        prev.setEnabled(first>0);prev.setOnClickListener(v->{relationshipPageIndex=Math.max(0,relationshipPageIndex-1);renderRelationshipsPage();});
                        Button next=btn("Позже ›");pager.addView(next,new LinearLayout.LayoutParams(0,px(46),1));
                        next.setEnabled(first+20<all.size());next.setOnClickListener(v->{relationshipPageIndex++;renderRelationshipsPage();});
                    }
                    status.setText("Хронология · "+all.size()+" событий · без облака");
                });
            }catch(Exception e){runOnUiThread(()->{if(token==loadToken)status.setText("Ошибка событий: "+e.getMessage());});}
        });
    }
    private void chooseRelationshipAttachment(long eventId,int offset){
        final MessageDb source=db;final String key=activeKey;
        worker.execute(()->{
            try{
                List<MessageDb.Attachment> files=source.mediaPage("all",offset,25);
                runOnUiThread(()->{
                    if(isFinishing()||!key.equals(activeKey)||db!=source)return;
                    String[] titles=new String[files.size()];
                    for(int i=0;i<files.size();i++){
                        MessageDb.Attachment a=files.get(i);
                        titles[i]=mediaLabel(a.kind)+" · #"+a.messageId+" · "+clip(a.path,36);
                    }
                    if(files.isEmpty()){toast("В этом архиве больше нет вложений для выбора");return;}
                    AlertDialog.Builder picker=new FontDialog.Builder(this)
                        .setTitle("Прикрепить ссылку из архива · "+(offset+1)+"–"+(offset+files.size()))
                        .setItems(titles,(dialog,index)->{
                            MessageDb.Attachment selected=files.get(index);
                            worker.execute(()->{
                                try{source.linkRelationshipMedia(eventId,selected.id);
                                    runOnUiThread(()->{if(!key.equals(activeKey))return;
                                        if(screen.equals("relationships"))renderRelationshipsPage();
                                        else if(screen.equals("chronology"))renderChronologyPage();});
                                }catch(Exception e){runOnUiThread(()->toast("Не удалось прикрепить файл: "+e.getMessage()));}
                            });
                        }).setNegativeButton("Закрыть",null);
                    if(files.size()==25)picker.setPositiveButton("Следующие 25 ›",(dialog,which)->chooseRelationshipAttachment(eventId,offset+25));
                    if(offset>0)picker.setNeutralButton("‹ Назад",(dialog,which)->chooseRelationshipAttachment(eventId,Math.max(0,offset-25)));
                    picker.show();
                });
            }catch(Exception e){runOnUiThread(()->toast("Ошибка медиатеки: "+e.getMessage()));}
        });
    }
    /** Two separate per-chat SQLite databases; opt-in counts only, no participant mixing. */
    private void chooseParallelCalendar(){
        final String firstKey=activeKey;
        if(!canOpenQuoteArchive(firstKey)){toast("Сначала открой переписку");return;}
        ArrayList<String> keys=new ArrayList<>(),labels=new ArrayList<>();
        for(int i=0;i<archives.length();i++){
            JSONObject chat=archives.optJSONObject(i);
            if(chat==null)continue;
            String key=chat.optString("key");
            if(!firstKey.equals(key)&&canOpenQuoteArchive(key)){
                keys.add(key);labels.add(chat.optString("name","Переписка"));
            }
        }
        if(keys.isEmpty()){
            new FontDialog.Builder(this).setTitle("Параллельные календари")
                .setMessage("Добавь вторую переписку. Сравнение работает только между двумя явно выбранными локальными архивами.")
                .setPositiveButton("Понятно",null).show();return;
        }
        new FontDialog.Builder(this).setTitle("Второй чат для сравнения")
            .setItems(labels.toArray(new String[0]),(dialog,index)->{
                String secondKey=keys.get(index),secondName=labels.get(index);
                EditText yearInput=new EditText(this);
                yearInput.setSingleLine(true);yearInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
                yearInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
                yearInput.setText(String.valueOf(java.time.LocalDate.now().getYear()));
                new FontDialog.Builder(this).setTitle("Год для двух календарей")
                    .setMessage("Отобразятся только числа сообщений за каждый месяц. Переписки и участники не объединяются.")
                    .setView(yearInput).setNegativeButton("Отмена",null)
                    .setPositiveButton("Сравнить",(d,i)->{
                        try{
                            int year=Integer.parseInt(yearInput.getText().toString().trim());
                            if(year<1||year>9999)throw new NumberFormatException();
                            showParallelCalendar(firstKey,secondKey,secondName,year);
                        }catch(NumberFormatException ignored){toast("Укажи год от 1 до 9999");}
                    }).show();
            }).setNegativeButton("Отмена",null).show();
    }
    private void showParallelCalendar(String firstKey,String secondKey,String secondName,int year){
        if(!canOpenQuoteArchive(firstKey)||!canOpenQuoteArchive(secondKey)){
            toast("Архив больше не зарегистрирован");return;
        }
        final int token=loadToken;
        final String firstName=activeName;
        status.setText("Строю календари — только локальные счётчики…");
        worker.execute(()->{
            ParallelCalendars.Comparison report=null;
            String failure=null;
            try{
                String filenameA="default".equals(firstKey)?"telegram_archive.db":"telegram_"+firstKey+".db";
                String filenameB="default".equals(secondKey)?"telegram_archive.db":"telegram_"+secondKey+".db";
                if(!getDatabasePath(filenameA).isFile()||!getDatabasePath(filenameB).isFile()){
                    failure="Одна из баз данных ещё не импортирована";
                }else{
                    ParallelCalendars.YearData a,b;
                    try(android.database.sqlite.SQLiteDatabase dbA=android.database.sqlite.SQLiteDatabase.openDatabase(
                            getDatabasePath(filenameA).getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                        a=MessageDb.calendarYear(dbA,year);
                    }
                    try(android.database.sqlite.SQLiteDatabase dbB=android.database.sqlite.SQLiteDatabase.openDatabase(
                            getDatabasePath(filenameB).getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                        b=MessageDb.calendarYear(dbB,year);
                    }
                    report=new ParallelCalendars.Comparison(a,b);
                }
            }catch(Exception e){failure="Ошибка чтения календаря: "+e.getClass().getSimpleName();}
            final ParallelCalendars.Comparison view=report;
            final String error=failure;
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||!firstKey.equals(activeKey)||!"relationships".equals(screen))return;
                if(error!=null){toast(error);status.setText("Календари: ошибка чтения");return;}
                StringBuilder result=new StringBuilder("Год: ").append(year).append("\n")
                    .append(firstName).append(" / ").append(secondName).append("\n\n");
                for(int month=0;month<12;month++){
                    result.append(String.format(Locale.ROOT,"%02d",month+1)).append(" · ")
                        .append(view.first.months[month]).append(" / ")
                        .append(view.second.months[month]).append('\n');
                }
                result.append("\nСуммы: ").append(view.first.total()).append(" / ")
                    .append(view.second.total()).append("\n").append(view.summary());
                if(view.first.rejected>0||view.second.rejected>0)
                    result.append("\nПропущены некорректные даты: ").append(view.first.rejected)
                        .append(" / ").append(view.second.rejected);
                result.append("\n\nТолько подсчёт по датам из экспорта. Никакие сообщения или участники не объединяются.");
                new FontDialog.Builder(this).setTitle("Параллельные календари")
                    .setMessage(result.toString()).setPositiveButton("Закрыть",null).show();
                status.setText("Сравнение календарей завершено"+(view.complete()?"":" · частично"));
            });
        });
    }
    private void chooseFirstDualChat(String mode){
        if(archives.length()<2){toast("Для сравнения нужны две переписки");return;}
        ArrayList<String> keys=new ArrayList<>(),names=new ArrayList<>();
        for(int i=0;i<archives.length();i++){
            JSONObject c=archives.optJSONObject(i);
            if(c!=null&&canOpenQuoteArchive(c.optString("key"))){
                keys.add(c.optString("key"));names.add(c.optString("name","Переписка"));
            }
        }
        if(keys.size()<2){toast("Добавь вторую переписку");return;}
        new FontDialog.Builder(this).setTitle("Первая переписка для сравнения")
            .setItems(names.toArray(new String[0]),(d,index)->{
                activateArchive(keys.get(index));
                if(!home)chooseDualChat(mode);
            }).setNegativeButton("Отмена",null).show();
    }
    /** Compare two registered, separately stored archives. Nothing is imported or merged. */
    private void chooseDualChat(String mode){
        if(!"dual_compare".equals(mode)&&!"dual_memories".equals(mode))return;
        if(home||!canOpenQuoteArchive(activeKey)){toast("Сначала открой первую переписку");return;}
        ArrayList<String> keys=new ArrayList<>(),labels=new ArrayList<>();
        for(int n=0;n<archives.length();n++){
            JSONObject a=archives.optJSONObject(n);
            if(a==null)continue;
            String key=a.optString("key");
            if(!key.equals(activeKey)&&canOpenQuoteArchive(key)){
                keys.add(key);labels.add(a.optString("name","Чат"));
            }
        }
        if(keys.isEmpty()){
            new FontDialog.Builder(this).setTitle("Нужны две переписки")
                .setMessage("Импортируй ещё один чат. Сравнение и воспоминания работают с двумя локальными архивами и ничего между ними не переносят.")
                .setPositiveButton("Понятно",null).show();return;
        }
        new FontDialog.Builder(this).setTitle("Выбери вторую переписку")
            .setItems(labels.toArray(new String[0]),(d,index)->{
                dualSecondKey=keys.get(index);dualSecondName=labels.get(index);
                if(dualYear<1||dualYear>9999)dualYear=LocalDate.now().getYear();
                dualMonth=0;screen=mode;showPage();
            }).setNegativeButton("Отмена",null).show();
    }
    private void chooseDualYear(){
        EditText year=new EditText(this);year.setSingleLine(true);
        year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        year.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        year.setText(String.valueOf(dualYear));
        new FontDialog.Builder(this).setTitle("Год сравнения двух переписок")
            .setView(year).setNegativeButton("Отмена",null)
            .setPositiveButton("Применить",(d,which)->{
                try{int y=Integer.parseInt(year.getText().toString().trim());
                    if(y<1||y>9999)throw new NumberFormatException();
                    dualYear=y;dualMonth=0;showPage();
                }catch(NumberFormatException bad){toast("Год от 1 до 9999");}
            }).show();
    }
    private void chooseDualMonth(){
        String[] labels={"Все месяцы","Январь","Февраль","Март","Апрель","Май","Июнь",
            "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь"};
        new FontDialog.Builder(this).setTitle("Месяц общей линии · "+dualYear)
            .setSingleChoiceItems(labels,dualMonth,(dialog,index)->{
                dualMonth=index;dialog.dismiss();showPage();
            }).setNegativeButton("Закрыть",null).show();
    }
    private java.io.File dualChatDbFile(String key){
        if(!canOpenQuoteArchive(key))throw new IllegalArgumentException("Чат не зарегистрирован");
        return getDatabasePath("default".equals(key)?"telegram_archive.db":"telegram_"+key+".db");
    }
    private void openDualMemory(SharedMemories.Entry row){
        if(!SharedMemories.belongs(row,activeKey,dualSecondKey)||!canOpenQuoteArchive(row.archiveKey)){
            toast("Источник больше не доступен");return;
        }
        String sourceKey=row.archiveKey;
        if(!sourceKey.equals(activeKey))activateArchive(sourceKey);
        if(sourceKey.equals(activeKey))jump(row.messageId,"");
    }
    private void renderDualChatScreen(){
        if(home){screen="chat";showPage();return;}
        final boolean isTimeline="dual_memories".equals(screen);
        final String mode=screen,first=activeKey,second=dualSecondKey;
        final int year=dualYear,month=dualMonth,token=++loadToken;
        if(first.equals(second)||!canOpenQuoteArchive(first)||!canOpenQuoteArchive(second)){
            toast("Выбери две разные зарегистрированные переписки");screen="relationships";showPage();return;
        }
        prepareRelationshipScreen(isTimeline?"◷ Воспоминания двух переписок":"⇄ Сравнение диалогов",
            "Две локальные базы. Сообщения, личности и медиа не объединяются, в интернет ничего не отправляется.");
        Button back=btn("‹ К взаимоотношениям");list.addView(back,p(-1,45));
        back.setOnClickListener(v->{screen="relationships";showPage();});
        Button other=btn("⇄ Сменить вторую переписку");list.addView(other,p(-1,45));
        other.setOnClickListener(v->chooseDualChat(mode));
        Button yearButton=btn("Год: "+year+"  ▾");list.addView(yearButton,p(-1,45));
        yearButton.setOnClickListener(v->chooseDualYear());
        if(isTimeline){
            Button monthButton=btn("Период: "+(month==0?"весь год":String.format(Locale.ROOT,"%02d",month))+"  ▾");
            list.addView(monthButton,p(-1,45));monthButton.setOnClickListener(v->chooseDualMonth());
        }
        TextView progress=text("Сверяю локальные базы в режиме чтения…",13,SUB);list.addView(progress,p(-1,-2));
        final String firstName=activeName,secondName=dualSecondName;
        worker.execute(()->{
            DualChatRepository.Snapshot a=null,b=null;
            List<SharedMemories.Entry> memoriesA=null,memoriesB=null;
            String failure=null;
            try{
                java.io.File fa=dualChatDbFile(first),fb=dualChatDbFile(second);
                if(!fa.isFile()||!fb.isFile())throw new IllegalStateException("Сначала импортируй обе переписки");
                try(android.database.sqlite.SQLiteDatabase d=android.database.sqlite.SQLiteDatabase.openDatabase(
                        fa.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                    a=DualChatRepository.snapshot(d,year);
                    if(isTimeline)memoriesA=DualChatRepository.timeline(d,first,firstName,year,month);
                }
                try(android.database.sqlite.SQLiteDatabase d=android.database.sqlite.SQLiteDatabase.openDatabase(
                        fb.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY)){
                    b=DualChatRepository.snapshot(d,year);
                    if(isTimeline)memoriesB=DualChatRepository.timeline(d,second,secondName,year,month);
                }
            }catch(Exception e){failure=e.getClass().getSimpleName()+": "+e.getMessage();}
            final DualChatRepository.Snapshot rA=a,rB=b;
            final List<SharedMemories.Entry> listA=memoriesA,listB=memoriesB;
            final String error=failure;
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||!first.equals(activeKey)||!mode.equals(screen))return;
                if(error!=null){progress.setText("Не получилось прочитать базы: "+error);status.setText("Ошибка сравнения");return;}
                progress.setText(firstName+"   /   "+secondName+" · "+year);
                if(isTimeline){
                    List<SharedMemories.Entry> rows=SharedMemories.mergeBalanced(listA,listB,30);
                    renderDualMemoriesRows(rows,rA,rB,firstName,secondName);
                }else renderDualComparison(rA,rB,firstName,secondName);
            });
        });
    }
    private void renderDualComparison(DualChatRepository.Snapshot a,DualChatRepository.Snapshot b,
                                      String firstName,String secondName){
        LinearLayout summary=relationshipCard(list,"Два диалога · общие показатели");
        analyticsText(summary,"A: "+firstName+"    Б: "+secondName,FG,13);
        analyticsText(summary,"Сообщения: "+a.messages+" / "+b.messages,FG,14);
        analyticsText(summary,"С текстом: "+a.withText+" / "+b.withText,FG,13);
        analyticsText(summary,"Участники (в каждом чате отдельно): "+a.participants+" / "+b.participants,FG,13);
        analyticsText(summary,"Вложения: "+a.attachments+" / "+b.attachments,FG,13);
        analyticsText(summary,"Избранное: "+a.favorites+" / "+b.favorites,FG,13);
        LinearLayout yearCard=relationshipCard(list,"Активность за "+dualYear);
        analyticsText(yearCard,"Сообщений за год: "+a.calendar.total()+" / "+b.calendar.total(),FG,13);
        analyticsText(yearCard,"Дней с сообщениями: "+a.calendar.activeDays()+" / "+b.calendar.activeDays(),FG,13);
        analyticsText(yearCard,"Дней, когда были активны оба чата: "+
            new ParallelCalendars.Comparison(a.calendar,b.calendar).sharedActiveDays(),FG,13);
        if(a.calendar.partial||b.calendar.partial)
            analyticsText(yearCard,"⚠ Годовые данные частичные (лимит "+ParallelCalendars.MAX_DATE_ROWS+
                " записей на чат). Полные счётчики выше не ограничены.",0xffffc377,12);
        analyticsText(yearCard,"График по месяцам · A синий, Б оранжевый. Шкала общая.",SUB,12);
        drawDualMonthlyBars(yearCard,a.calendar.counts(),b.calendar.counts());
        LinearLayout media=relationshipCard(list,"Медиа по категориям");
        java.util.TreeSet<String> kinds=new java.util.TreeSet<>();
        kinds.addAll(a.mediaTypes.keySet());kinds.addAll(b.mediaTypes.keySet());
        if(kinds.isEmpty())analyticsText(media,"В этих чатах вложения пока не индексированы.",SUB,12);
        for(String kind:kinds)analyticsText(media,kind+": "+a.mediaTypes.getOrDefault(kind,0L)+
            " / "+b.mediaTypes.getOrDefault(kind,0L),FG,12);
        Button time=btn("◷ Открыть общие воспоминания за "+dualYear);
        list.addView(time,p(-1,49));time.setOnClickListener(v->{dualMonth=0;screen="dual_memories";showPage();});
        status.setText("Сравнение двух диалогов · без изменения данных");
    }
    private void drawDualMonthlyBars(LinearLayout target,long[] a,long[] b){
        long top=1;for(int i=0;i<12;i++)top=Math.max(top,Math.max(a[i],b[i]));
        final long peak=top;
        final int blue=0xff64c9ff,orange=0xffffb36b;
        View chart=new View(this){
            final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                super.onDraw(canvas);
                float w=getWidth(),h=getHeight();float left=px(33),right=w-px(52);
                float step=(h-px(8))/12f;
                for(int i=0;i<12;i++){
                    float y=i*step+px(7);
                    pen.setColor(SUB);pen.setTextSize(px(11));
                    canvas.drawText(String.format(Locale.ROOT,"%02d",i+1),px(4),y+px(11),pen);
                    float aw=(right-left)*(float)a[i]/peak,bw=(right-left)*(float)b[i]/peak;
                    pen.setColor(blue);canvas.drawRoundRect(left,y,left+aw,y+px(7),px(3),px(3),pen);
                    pen.setColor(orange);canvas.drawRoundRect(left,y+px(9),left+bw,y+px(16),px(3),px(3),pen);
                    pen.setColor(FG);pen.setTextSize(px(10));
                    canvas.drawText(a[i]+"/"+b[i],right+px(3),y+px(11),pen);
                }
            }
        };
        chart.setContentDescription("Сравнение сообщений за 12 месяцев: первая и вторая переписка");
        target.addView(chart,p(-1,12*27));
        for(int i=0;i<12;i++)analyticsText(target,String.format(Locale.ROOT,"%02d",i+1)+
            " · "+a[i]+" / "+b[i],SUB,11);
    }
    private void renderDualMemoriesRows(List<SharedMemories.Entry> rows,
                 DualChatRepository.Snapshot a,DualChatRepository.Snapshot b,String firstName,String secondName){
        analyticsText(list,"Сообщения в "+dualYear+": "+a.calendar.total()+" / "+b.calendar.total()+
            ". Показаны последние сообщения выбранного периода: до "+SharedMemories.MAX_PER_CHAT+
            " на каждый чат, затем до 30 из каждого чата по общей шкале. Времена из экспорта, без догадок о часовом поясе.",SUB,12);
        if(a.calendar.partial||b.calendar.partial)
            analyticsText(list,"⚠ Годовой подсчёт неполный: достигнут лимит чтения.",0xffffc377,12);
        if(rows.isEmpty())analyticsText(list,"Для этого периода сообщения не найдены. Попробуй другой месяц или год.",SUB,14);
        int currentMonth=-1;
        for(SharedMemories.Entry entry:rows){
            int month=SharedMemories.month(entry);
            if(month!=currentMonth){
                currentMonth=month;
                TextView monthHeading=text(String.format(Locale.ROOT,"%02d.%04d",month,dualYear),16,ACCENT);
                monthHeading.setPadding(px(12),px(16),0,px(7));list.addView(monthHeading);
            }
            boolean fromFirst=entry.archiveKey.equals(activeKey);
            LinearLayout card=relationshipCard(list,(fromFirst?"🔵 ":"🟠 ")+entry.archiveName);
            analyticsText(card,entry.date+" · "+clip(entry.author,42)+" · #"+entry.messageId,SUB,11);
            analyticsText(card,entry.preview.isEmpty()?"(сообщение без текста)":entry.preview,FG,13);
            Button open=btn("↗ Перейти к оригиналу в «"+clip(entry.archiveName,26)+"»");
            card.addView(open,p(-1,40));open.setOnClickListener(v->openDualMemory(entry));
        }
        Button comparison=btn("⇄ Сравнить активность и медиа двух чатов");
        list.addView(comparison,p(-1,49));
        comparison.setOnClickListener(v->{screen="dual_compare";showPage();});
        status.setText("Общая временная линия · "+rows.size()+" записей в обзоре");
    }
    private void renderEchoPage(){
        ++loadToken;
        if(home){screen="chat";showPage();return;}
        prepareRelationshipScreen("❞ Эхо фраз","Введи выражение из переписки. Поиск учитывает регистр Unicode и повторы в разных сообщениях. Никаких выводов о чувствах людей не делается.");
        Button back=btn("‹ К взаимоотношениям");list.addView(back,p(-1,44));
        back.setOnClickListener(v->{screen="relationships";showPage();});
        EditText phrase=new EditText(this);phrase.setSingleLine(true);phrase.setHint("Например: доброе утро");
        phrase.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(PhraseEcho.MAX_PHRASE)});
        list.addView(phrase,p(-1,55));
        Button search=btn("Найти повторы");list.addView(search,p(-1,48));
        LinearLayout output=column();list.addView(output,p(-1,-2));
        search.setOnClickListener(v->{
            String query=phrase.getText().toString();
            try{PhraseEcho.requirePhrase(query);}catch(IllegalArgumentException e){phrase.setError(e.getMessage());return;}
            final int token=++loadToken;final MessageDb source=db;final String key=activeKey;
            search.setEnabled(false);output.removeAllViews();analyticsText(output,"Поиск выполняется локально…",SUB,13);
            worker.execute(()->{
                try{PhraseEcho.Report report=source.echoPhrase(query);
                    runOnUiThread(()->{
                        if(isFinishing()||token!=loadToken||!key.equals(activeKey)||db!=source)return;
                        search.setEnabled(true);output.removeAllViews();
                        LinearLayout info=relationshipCard(output,"Совпадения «"+query.trim()+"»");
                        analyticsText(info,"Сообщений: "+report.matchingMessages+" · повторений: "+report.occurrences+
                            " · просмотрено: "+report.scanned,FG,14);
                        if(report.partial)analyticsText(info,"Проверены только первые 300 000 сообщений: результат частичный. Это не число по всему архиву.",SUB,12);
                        analyticsText(info,"Считаются буквальные совпадения фразы. Показаны первые "+report.examples.size()+" из найденных; названия этапов не присваиваются автоматически.",SUB,11);
                        for(PhraseEcho.Hit hit:report.examples){
                            LinearLayout item=relationshipCard(output,"#"+hit.messageId+" · "+clip(hit.author,32));
                            analyticsText(item,clip(hit.date,35)+" · «"+clip(hit.preview,160)+"»",FG,13);
                            Button jump=btn("↗ Открыть в переписке");item.addView(jump,p(-1,40));
                            jump.setOnClickListener(w->jump(hit.messageId,"echo"));
                        }
                    });
                }catch(Exception e){runOnUiThread(()->{search.setEnabled(true);toast("Ошибка поиска: "+e.getMessage());});}
            });
        });
    }
    private void openHelpChapter(int chapter){showHelp();int[] path=HelpCatalog.findChapter(chapter);if(path!=null){helpCategory=path[0];helpShelf=-1;helpChapter=chapter;renderHelpPage();}}
    private void editLocalProfile(){
        final MessageDb source=db;final String key=activeKey;
        worker.execute(()->{String[] profile=source.localProfile();runOnUiThread(()->{
            if(!key.equals(activeKey)||isFinishing())return;
            LinearLayout form=column();form.setPadding(px(18),px(8),px(18),px(8));
            String[] labels={"Отображаемое имя","@username (локальная подпись)","Личная заметка"};EditText[] fields=new EditText[3];
            for(int i=0;i<3;i++){fields[i]=new EditText(this);fields[i].setHint(labels[i]);fields[i].setText(profile[i]);fields[i].setTextColor(FG);fields[i].setHintTextColor(SUB);fields[i].setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(i==2?1500:80)});form.addView(fields[i]);}
            new FontDialog.Builder(this).setTitle("Локальный профиль").setMessage("Только для этого архива. Не меняет Telegram или сообщения. Входит в полную резервную копию.")
                .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(dialog,which)->{
                    String name=fields[0].getText().toString(),username=fields[1].getText().toString(),note=fields[2].getText().toString();
                    worker.execute(()->{try{source.saveLocalProfile(name,username,note);runOnUiThread(()->toast("Профиль сохранён локально"));}catch(Exception error){runOnUiThread(()->toast("Не сохранено: "+error.getMessage()));}});
                }).show();
        });});
    }
    private void editCalendarPeriod(CalendarPeriod old){
        final MessageDb source=db;final String key=activeKey;
        LinearLayout form=column();form.setPadding(px(18),px(8),px(18),px(8));
        EditText name=new EditText(this);name.setHint("Название периода");name.setTextColor(FG);name.setHintTextColor(SUB);name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});form.addView(name);
        EditText note=new EditText(this);note.setHint("Описание (необязательно)");note.setTextColor(FG);note.setHintTextColor(SUB);note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1500)});form.addView(note);
        String today=LocalDate.now().toString();final String[] dates={old==null?today:old.start,old==null?today:old.end};
        if(old!=null){name.setText(old.name);note.setText(old.note);}
        for(int i=0;i<2;i++){final int index=i;Button date=btn((i==0?"Начало: ":"Конец: ")+dates[i]);form.addView(date);
            date.setOnClickListener(v->{LocalDate chosen=LocalDate.parse(dates[index]);new android.app.DatePickerDialog(this,(picker,y,m,d)->{dates[index]=LocalDate.of(y,m+1,d).toString();date.setText((index==0?"Начало: ":"Конец: ")+dates[index]);},chosen.getYear(),chosen.getMonthValue()-1,chosen.getDayOfMonth()).show();});}
        android.app.AlertDialog dialog=new FontDialog.Builder(this).setTitle(old==null?"Новый календарный период":"Изменить период")
            .setMessage("Любые календарные даты, даже до начала переписки. Это личная отметка, она не создаёт сообщений.")
            .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            try{CalendarPeriod period=new CalendarPeriod(old==null?0:old.id,name.getText().toString(),note.getText().toString(),dates[0],dates[1]);
                dialog.getButton(-1).setEnabled(false);
                worker.execute(()->{try{source.saveCalendarPeriod(period);runOnUiThread(()->{dialog.dismiss();if(key.equals(activeKey)&&"chronology".equals(screen))renderChronologyPage();});}
                    catch(Exception error){runOnUiThread(()->{dialog.getButton(-1).setEnabled(true);toast("Не сохранено: "+error.getMessage());});}});
            }catch(Exception invalid){toast(invalid.getMessage());}
        }));dialog.show();
    }
    private void renderCalendarPeriods(LinearLayout container,MessageDb source,String key,int token){
        worker.execute(()->{try{List<CalendarPeriod> periods=source.calendarPeriods();runOnUiThread(()->{
            if(token!=loadToken||!key.equals(activeKey)||isFinishing())return;
            if(periods.isEmpty())analyticsText(container,"Календарных периодов нет. Можно добавить даты вне переписки.",SUB,13);
            for(CalendarPeriod period:periods){
                LinearLayout card=column();card.setPadding(px(12),px(12),px(12),px(12));card.setBackground(shape(PANEL,16));container.addView(card,p(-1,-2));
                analyticsText(card,period.name,FG,17);analyticsText(card,period.start+" — "+period.end,ACCENT,14);
                if(!period.note.isEmpty())analyticsText(card,period.note,FG,14);
                Button edit=btn("Изменить даты / описание");card.addView(edit);edit.setOnClickListener(v->editCalendarPeriod(period));
                Button remove=btn("Удалить период");card.addView(remove);remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить период?")
                    .setMessage("Удалится только личная отметка. Переписка останется.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->worker.execute(()->{source.deleteCalendarPeriod(period.id);runOnUiThread(()->{if(key.equals(activeKey))renderChronologyPage();});})).show());
            }
        });}catch(Exception error){runOnUiThread(()->{if(token==loadToken)analyticsText(container,"Не удалось прочитать периоды",SUB,14);});}});
    }
    private void renderChronologyPage(){
        int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        bottomNav.setVisibility(View.VISIBLE);refreshTabs();
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        list.removeAllViews();list.setBackgroundColor(BG);status.setText("Загружаю твою хронологию…");
        TextView heading=text("◷  Хронология истории",21,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(px(12),px(12),px(12),px(10));list.addView(heading);
        analyticsText(list,"Этот раздел включается только вручную. Ты сам задаёшь названия и границы, даже посередине сообщения. Импортированный текст не меняется.",SUB,13);
        Button back=btn("‹ К переписке");list.addView(back,p(-1,46));back.setOnClickListener(v->{screen="chat";showPage();});
        Button twoChats=btn("◷ Показать две переписки на общей временной линии");
        list.addView(twoChats,p(-1,48));twoChats.setOnClickListener(v->chooseDualChat("dual_memories"));
        TextView how=text("Как создать этап: открой «Переписка» → удерживай первое сообщение → «Отметить начало периода». Затем отметь конец в другом сообщении, придумай название и сохрани.",14,FG);
        how.setPadding(px(12),px(15),px(12),px(15));how.setBackground(glass(PANEL,14));list.addView(how,p(-1,-2));
        Button addCalendar=btn("+ Календарный период · любые даты");list.addView(addCalendar,p(-1,-2));addCalendar.setOnClickListener(v->editCalendarPeriod(null));
        LinearLayout calendar=column();list.addView(calendar,p(-1,-2));renderCalendarPeriods(calendar,db,activeKey,token);
        final MessageDb source=db, initial=db;final String archive=activeKey;
        worker.execute(()->{
            try{
                List<ConversationPeriods.Chapter> stages=source.periods();
                LinkedHashMap<Long,ConversationPeriods.Stats> stats=new LinkedHashMap<>();
                for(ConversationPeriods.Chapter stage:stages)stats.put(stage.id,source.periodStats(stage));
                LinkedHashMap<Long,List<MessageDb.Attachment>> phaseMedia=new LinkedHashMap<>();
                for(ConversationPeriods.Chapter stage:stages)phaseMedia.put(stage.id,source.relationshipMedia(-stage.id));
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken||!archive.equals(activeKey)||db!=initial)return;
                    if(stages.isEmpty())analyticsText(list,"Периодов пока нет. Отметь начало и конец через долгое нажатие на сообщения.",ACCENT,14);
                    long peak=1;for(ConversationPeriods.Stats st:stats.values())for(long n:st.bins)peak=Math.max(peak,n);
                    final long maximum=peak;
                    renderPeriodOverlay(stages,stats);
                    int index=0;
                    for(ConversationPeriods.Chapter stage:stages){
                        ConversationPeriods.Stats st=stats.get(stage.id);
                        LinearLayout card=analyticsCard((++index)+". "+stage.name);
                        analyticsText(card,"Сообщения "+stage.start.messageId+" : "+stage.start.offset+
                            " — "+stage.end.messageId+" : "+stage.end.offset,SUB,11);
                        if(!stage.note.isEmpty())analyticsText(card,"Заметка: "+stage.note,FG,13);
                        analyticsText(card,"Сообщений: "+st.messages+" · медиа: "+st.media+
                            " · активных дней: "+st.activeDays+" · сообщений/активный день: "+
                            String.format(Locale.ROOT,"%.1f",st.dailyAverage()),FG,13);
                        analyticsText(card,"Даты: "+st.firstDate+" — "+st.lastDate+
                            " · график: 12 равных частей этого периода, общая шкала для всех этапов",SUB,11);
                        renderPeriodBars(card,st,maximum);
                        List<MessageDb.Attachment> phaseAttachments=phaseMedia.get(stage.id);
                        if(phaseAttachments!=null)for(MessageDb.Attachment a:phaseAttachments){
                            Button media=btn("▣ "+mediaLabel(a.kind)+" · открыть вложение");
                            card.addView(media,p(-1,42));media.setOnClickListener(v->openMedia(a));
                        }
                        Button link=btn("+ Прикрепить фото, кружок или файл к этапу");card.addView(link,p(-1,46));
                        link.setOnClickListener(v->chooseRelationshipAttachment(-stage.id,0));
                        LinearLayout buttons=row();card.addView(buttons,p(-1,42));
                        Button jump=btn("↗ Начало");buttons.addView(jump,new LinearLayout.LayoutParams(0,-1,1));
                        jump.setOnClickListener(v->jump(stage.start.messageId,"chronology"));
                        Button remove=btn("Удалить этап");buttons.addView(remove,new LinearLayout.LayoutParams(0,-1,1));
                        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить «"+stage.name+"»?")
                            .setMessage("Удалится только твоя отметка периода. Переписка и медиа не изменятся.")
                            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->worker.execute(()->{
                                source.removePeriod(stage.id);runOnUiThread(()->{if(archive.equals(activeKey))renderChronologyPage();});
                            })).show());
                    }
                    status.setText("Этапов: "+stages.size()+" · сравнение по общей числовой шкале");
                });
            }catch(Exception e){runOnUiThread(()->{if(token==loadToken)status.setText("Ошибка хронологии: "+e.getMessage());});}
        });
    }
    private void renderCinemaPage(){
        int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        bottomNav.setVisibility(View.VISIBLE);refreshTabs();
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        list.removeAllViews();list.setBackgroundColor(BG);
        TextView heading=text("▤ Кинолента воспоминаний",22,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(px(10),px(10),px(10),px(8));list.addView(heading);
        analyticsText(list,"Выбери год и месяц. Медиа открываются локально; загружается не более 18 миниатюр за один раз. Это не автоматическая презентация личных сообщений.",SUB,12);
        Button back=btn("‹ К переписке");list.addView(back,p(-1,46));back.setOnClickListener(v->{screen="chat";showPage();});
        final MessageDb source=db;final String archive=activeKey;
        worker.execute(()->{try{List<MessageDb.MediaMonth> months=source.mediaMonths();
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||!archive.equals(activeKey))return;
                if(months.isEmpty())analyticsText(list,"Медиа с распознаваемыми датами пока нет.",SUB,14);
                String currentYear="";
                for(MessageDb.MediaMonth month:months){
                    if(month.month==null||month.month.length()!=7)continue;
                    String year=month.month.substring(0,4);
                    if(!year.equals(currentYear)){
                        currentYear=year;TextView yearLabel=text(year,21,ACCENT);yearLabel.setTypeface(null,Typeface.BOLD);
                        yearLabel.setPadding(px(11),px(21),px(10),px(5));list.addView(yearLabel,p(-1,-2));
                    }
                    LinearLayout block=column();block.setBackground(glass(PANEL,12));
                    LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(3),px(5),px(3),px(4));list.addView(block,lp);
                    TextView title=text(month.month+"  ·  "+month.count+" медиа  ▾",15,FG);
                    title.setPadding(px(13),px(12),px(8),px(12));block.addView(title,p(-1,-2));
                    LinearLayout previews=column();block.addView(previews,p(-1,-2));
                    title.setOnClickListener(v->{
                        if(previews.getChildCount()>0){previews.removeAllViews();return;}
                        title.setText(month.month+"  ·  Загружаю…");
                        worker.execute(()->{try{List<MessageDb.Attachment> attachments=source.mediaForMonth(month.month,18);
                            runOnUiThread(()->{
                                if(token!=loadToken||!archive.equals(activeKey))return;
                                title.setText(month.month+"  ·  "+month.count+" медиа  ▴");
                                for(MessageDb.Attachment item:attachments){
                                    LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
                                    line.setPadding(px(10),px(5),px(10),px(5));previews.addView(line,p(-1,94));
                                    View tile=mediaTile(item,true);line.addView(tile,p(93,90));
                                    TextView detail=text(mediaLabel(item.kind)+" · "+day(item.date)+"\nНажми — открыть · удерживай — действия",13,FG);
                                    detail.setPadding(px(10),0,0,0);line.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                                    line.setOnClickListener(vv->openMedia(item));
                                    line.setOnLongClickListener(vv->{mediaActions(item);return true;});
                                }
                                if(month.count>18)analyticsText(previews,"Показаны первые 18 вложений. Для остальных используй «Медиа».",SUB,12);
                            });
                        }catch(Exception error){runOnUiThread(()->toast("Не удалось открыть месяц"));}});
                    });
                }
                status.setText("Месяцев: "+months.size()+" · медиа не копируются");
            });
        }catch(Exception failure){runOnUiThread(()->{if(token==loadToken)status.setText("Ошибка киноленты: "+failure.getMessage());});}});
    }
    /** Opt-in overlay: each series uses the same messages-per-calendar-day Y axis.
     * Labels and numeric tap details remain usable without interpreting colors. */
    private void renderPeriodOverlay(List<ConversationPeriods.Chapter> periods,
                                     LinkedHashMap<Long,ConversationPeriods.Stats> reports){
        if(periods.size()<2)return;
        int visible=Math.min(3,periods.size());
        List<ConversationPeriods.Chapter> shown=new ArrayList<>(periods.subList(periods.size()-visible,periods.size()));
        final int[] colors={0xff64E3D5,0xffFFB25B,0xffC59EFF};
        LinearLayout card=analyticsCard("Наложение этапов · "+visible+" линии");
        analyticsText(card,"Каждая линия — 12 равных отрезков времени. Сравнивается сообщений на календарный день. Нажми на точку, чтобы увидеть число.",SUB,11);
        double max=0;
        final double[][] series=new double[visible][12];
        for(int k=0;k<visible;k++){
            ConversationPeriods.Chapter chapter=shown.get(k);
            ConversationPeriods.Stats report=reports.get(chapter.id);
            double days=Math.max(1.0,report.calendarDays/12.0);
            for(int i=0;i<12;i++){
                series[k][i]=report.bins.get(i)/days;
                max=Math.max(max,series[k][i]);
            }
            TextView legend=text("● "+chapter.name+" — "+report.messages+" сообщений за "+report.calendarDays+
                " календарных дней",12,colors[k]);
            legend.setPadding(px(8),px(4),px(6),px(4));card.addView(legend,p(-1,-2));
        }
        final double ceiling=Math.max(0.1,max);
        View chart=new View(this){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                float left=px(29),top=px(12),right=getWidth()-px(10),bottom=getHeight()-px(28);
                paint.setStrokeWidth(px(1));paint.setColor(0xff6A7E91);
                for(int n=0;n<=4;n++){
                    float y=top+(bottom-top)*n/4f;
                    canvas.drawLine(left,y,right,y,paint);
                    paint.setTextSize(px(9));
                    String t=String.format(Locale.ROOT,"%.1f",ceiling*(4-n)/4);
                    canvas.drawText(t,px(1),y+px(3),paint);
                }
                for(int n=0;n<12;n++){
                    float x=left+(right-left)*n/11f;
                    if(n==0||n==5||n==11){paint.setColor(SUB);paint.setTextSize(px(11));
                        canvas.drawText(String.valueOf(n+1),x-px(3),getHeight()-px(7),paint);}
                }
                for(int k=0;k<series.length;k++){
                    paint.setColor(colors[k]);paint.setStrokeWidth(px(3));
                    float previousX=-1,previousY=-1;
                    for(int n=0;n<12;n++){
                        float x=left+(right-left)*n/11f;
                        float y=bottom-(bottom-top)*(float)(series[k][n]/ceiling);
                        if(n>0)canvas.drawLine(previousX,previousY,x,y,paint);
                        canvas.drawCircle(x,y,px(3),paint);
                        previousX=x;previousY=y;
                    }
                }
            }
            @Override public boolean onTouchEvent(MotionEvent event){
                if(event.getAction()==MotionEvent.ACTION_DOWN)return true;
                if(event.getAction()==MotionEvent.ACTION_UP){
                    float l=px(29),r=getWidth()-px(10);
                    int bin=Math.max(0,Math.min(11,Math.round((event.getX()-l)/Math.max(1,r-l)*11)));
                    StringBuilder detail=new StringBuilder("Часть "+(bin+1)+"/12");
                    for(int k=0;k<shown.size();k++)detail.append(" · ").append(clip(shown.get(k).name,12))
                        .append(": ").append(String.format(Locale.ROOT,"%.1f",series[k][bin])).append("/день");
                    toast(detail.toString());performClick();return true;
                }
                return super.onTouchEvent(event);
            }
            @Override public boolean performClick(){super.performClick();return true;}
        };
        chart.setContentDescription("Сравнение этапов: числовая шкала сообщений за календарный день");
        card.addView(chart,p(-1,190));
    }
    private void renderPeriodBars(LinearLayout card,ConversationPeriods.Stats stats,long max){
        LinearLayout chart=row();chart.setGravity(Gravity.BOTTOM);card.addView(chart,p(-1,125));
        for(int i=0;i<stats.bins.size();i++){
            long value=stats.bins.get(i);int height=(int)Math.round(74.0*value/Math.max(1,max));
            LinearLayout slot=column();slot.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
            chart.addView(slot,new LinearLayout.LayoutParams(0,-1,1));
            TextView n=text(value==0?"·":String.valueOf(value),8,FG);n.setGravity(Gravity.CENTER);slot.addView(n,p(-1,17));
            View bar=new View(this);bar.setBackground(shape(value==0?CHIP:ACCENT,4));slot.addView(bar,p(-1,Math.max(2,height)));
            TextView x=text(String.valueOf(i+1),8,SUB);x.setGravity(Gravity.CENTER);slot.addView(x,p(-1,15));
            final int bin=i+1;slot.setOnClickListener(v->toast("Отрезок "+bin+"/12 · "+value+" сообщений"));
        }
    }
    /** Local, read-only excerpt from a Telegram export. The user chooses the fragment;
     * we never write back to a Telegram account or edit imported messages. */
    private void quoteMessage(MessageDb.Item m){
        if(m.body==null||m.body.trim().isEmpty()){
            toast("У сообщения нет текста, который можно процитировать");return;
        }
        LinearLayout content=column();content.setPadding(px(18),px(5),px(18),px(8));
        TextView hint=text("Зажми текст и выдели нужные слова маркерами. Без выделения цитируется всё сообщение. Копирование помещает текст в системный буфер обмена.",13,SUB);
        hint.setPadding(0,0,0,px(12));content.addView(hint);
        TextView excerpt=text(m.body,16,FG);
        excerpt.setTextIsSelectable(true);
        excerpt.setPadding(px(12),px(12),px(12),px(12));
        excerpt.setBackground(glass(PANEL,12));
        content.addView(excerpt,p(-1,-2));
        TextView origin=text("Источник: "+clip(m.author,70)+" · "+clock(m.date)+". Архив останется без изменений.",12,SUB);
        origin.setPadding(0,px(11),0,0);content.addView(origin);
        AlertDialog dialog=new FontDialog.Builder(this)
            .setTitle("❞ Цитировать сообщение")
            .setView(content)
            .setPositiveButton("Скопировать",null)
            .setNeutralButton("В заметки",null)
            .setNegativeButton("Отмена",null)
            .create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String selected=selectedQuoteText(excerpt);
                if(selected.isEmpty()){toast("Выдели текст для цитаты");return;}
                String formatted=QuoteFormatter.format(selected,m.author,m.date,activeName);
                android.content.ClipboardManager manager=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if(manager==null){toast("Буфер обмена недоступен");return;}
                manager.setPrimaryClip(android.content.ClipData.newPlainText("Цитата из Telegram Archive",formatted));
                dialog.dismiss();toast("Цитата скопирована");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
                String selected=selectedQuoteText(excerpt);
                if(selected.isEmpty()){toast("Выдели текст для цитаты");return;}
                final String archive=activeKey;
                final String formatted=QuoteFormatter.format(selected,m.author,m.date,activeName);
                worker.execute(()->{
                    try{
                        savedStore.addQuote(formatted,archive,m.id);
                        runOnUiThread(()->toast("Цитата сохранена в заметках · исходник доступен по ссылке"));
                    }catch(Exception failure){runOnUiThread(()->toast("Не удалось сохранить цитату"));}
                });
                dialog.dismiss();
            });
        });
        dialog.show();
    }
    private static String selectedQuoteText(TextView view){
        CharSequence sequence=view.getText();
        if(sequence==null)return "";
        int start=Selection.getSelectionStart(sequence),end=Selection.getSelectionEnd(sequence);
        return QuoteFormatter.excerpt(sequence.toString(),start,end);
    }
    private void chooseMessageAttachment(long messageId){
        worker.execute(()->{
            MessageDb.Item selected=new MessageDb.Item();selected.id=messageId;
            Map<Long,List<MessageDb.Attachment>> result=db.attachmentsForMessages(java.util.Collections.singletonList(selected));
            List<MessageDb.Attachment> media=result.get(messageId);
            runOnUiThread(()->{
                if(media==null||media.isEmpty()){toast("У этого сообщения нет вложений");return;}
                if(media.size()==1){mediaActions(media.get(0));return;}
                String[] items=new String[media.size()];
                for(int i=0;i<media.size();i++)items[i]=mediaLabel(media.get(i).kind)+" · "+(i+1);
                new FontDialog.Builder(this).setTitle("Выбери вложение")
                    .setItems(items,(dlg,index)->mediaActions(media.get(index))).show();
            });
        });
    }
    private void saveMessageText(MessageDb.Item m){
        if(m.body==null||m.body.isEmpty()){toast("У сообщения нет текста. Для медиа выбери «Сохранить вложение»");return;}
        pendingMessageText=m.body;
        Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        picker.setType("text/plain");picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.putExtra(Intent.EXTRA_TITLE,"TelegramArchive_message_"+m.id+".txt");
        try{startActivityForResult(picker,SAVE_TEXT_REQUEST);}catch(Exception e){pendingMessageText=null;toast("Не удалось открыть выбор папки");}
    }
    private void toggleStar(long id){
        final String archive=activeKey;
        final MessageDb source=db;
        worker.execute(()->{
            boolean on=source.toggleFavorite(id);
            runOnUiThread(()->{
                if(!archive.equals(activeKey)||isFinishing())return;
                // Never call showPage(): it rebuilds ScrollView and teleports the reader.
                toast(on?"★ Добавлено в избранное":"Убрано из избранного");
            });
        });
    }
    private void renderMedia(List<MessageDb.Attachment> media){
        if("audio".equals(mediaKind)&&!media.isEmpty()){
            Button playAll=btn("♫  Открыть музыкальный плеер");
            playAll.setContentDescription("Музыкальный плеер: открыть выбранную переписку");
            LinearLayout.LayoutParams dims=p(-1,55);dims.setMargins(px(9),px(10),px(9),px(12));
            list.addView(playAll,dims);
            playAll.setOnClickListener(v->openMusicPlayer(media.get(0)));
        }
        if(media.isEmpty())return;
        boolean grid=mediaKind.equals("image")||mediaKind.equals("video")||mediaKind.equals("gif")||mediaKind.equals("sticker");
        String currentMonth=null;GridLayout gallery=null;
        for(MessageDb.Attachment a:media){
            String month=MediaTimeline.month(a.date);
            if(!month.equals(currentMonth)){
                currentMonth=month;
                TextView group=text("▦  "+month,15,FG);group.setTypeface(null,Typeface.BOLD);
                group.setPadding(px(12),px(15),px(12),px(7));list.addView(group,p(-1,-2));
                if(grid){gallery=new GridLayout(this);gallery.setColumnCount(3);list.addView(gallery,p(-1,-2));}
            }
            if(grid){
                FrameLayout tile=new FrameLayout(this);tile.setBackground(outlined(PANEL,13,false));tile.setClipToOutline(true);
                int screenWidth=getResources().getDisplayMetrics().widthPixels;
                int cell=(screenWidth-px(40))/3;
                GridLayout.LayoutParams t=new GridLayout.LayoutParams();t.width=cell-px(4);t.height=cell-px(4);
                t.setMargins(px(2),px(2),px(2),px(2));gallery.addView(tile,t);
                ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tile.addView(image,new FrameLayout.LayoutParams(-1,-1));
                if("sticker".equals(a.kind)&&isTgsPath(a.path)){
                    loadTgsPreview(a.path,tile);
                }else loadThumbnail(a.path,isVideoStickerPath(a.path)?"video":a.kind,image);
                if(a.kind.equals("video")||isVideoStickerPath(a.path)||a.kind.equals("gif")&&a.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$")){
                    TextView play=text("▶",25,FG);play.setGravity(Gravity.CENTER);
                    tile.addView(play,new FrameLayout.LayoutParams(-1,-1));
                }
                TextView go=text("↗",17,FG);go.setGravity(Gravity.CENTER);go.setContentDescription("К исходному сообщению");
                go.setBackground(shape(0xcc1b3349,11));
                tile.addView(go,new FrameLayout.LayoutParams(px(44),px(44),Gravity.END|Gravity.BOTTOM));
                go.setOnClickListener(v->jump(a.messageId,"media"));
                tile.setContentDescription(mediaLabel(a.kind)+" · "+month);
                TextView chosen=text("✓",20,FG);chosen.setGravity(Gravity.CENTER);
                chosen.setBackground(shape(0xcc197986,10));chosen.setVisibility(mediaSelection.containsKey(a.id)?View.VISIBLE:View.GONE);
                FrameLayout.LayoutParams checkBounds=new FrameLayout.LayoutParams(px(42),px(42),Gravity.TOP|Gravity.START);
                tile.addView(chosen,checkBounds);
                tile.setOnClickListener(v->{if(multiSelectMedia){toggleMediaSelection(a);chosen.setVisibility(mediaSelection.containsKey(a.id)?View.VISIBLE:View.GONE);}else openMedia(a);});
                tile.setOnLongClickListener(v->{if(multiSelectMedia)toggleMediaSelection(a);else mediaActions(a);return true;});
            }else{
                LinearLayout item=row();item.setPadding(px(8),px(7),px(8),px(7));item.setBackground(glass(PANEL,16));
                LinearLayout.LayoutParams ip=p(-1,-2);ip.setMargins(px(1),px(4),px(1),px(5));list.addView(item,ip);
                View tile=mediaTile(a,true);item.addView(tile,p(-2,-2));
                LinearLayout desc=column();desc.setPadding(px(10),0,0,0);item.addView(desc,new LinearLayout.LayoutParams(0,-2,1));
                TextView name=text(mediaLabel(a.kind),14,FG);name.setTypeface(null,Typeface.BOLD);desc.addView(name);
                TextView date=text(day(a.date)+" · "+clock(a.date),12,SUB);desc.addView(date);
                if(a.author!=null&&!a.author.isEmpty()){TextView author=text(clip(a.author,25),11,SUB);desc.addView(author);}
                TextView go=text("↗ Перейти в чат",12,ACCENT);go.setPadding(0,px(11),0,0);desc.addView(go);
                go.setOnClickListener(v->jump(a.messageId,"media"));
                item.setOnClickListener(v->{if(multiSelectMedia){toggleMediaSelection(a);item.setBackground(shape(mediaSelection.containsKey(a.id)?HIGHLIGHT:PANEL,17));}else openMedia(a);});
                item.setBackground(outlined(mediaSelection.containsKey(a.id)?HIGHLIGHT:PANEL,17,mediaSelection.containsKey(a.id)));
                item.setOnLongClickListener(v->{if(multiSelectMedia)toggleMediaSelection(a);else mediaActions(a);return true;});
            }
        }
        scroll.post(()->scroll.scrollTo(0,0));
    }
    private String mediaLabel(String k){switch(k){case "round":return "Видеосообщение";case "voice":return "Голосовое";case "video":return "Видео";case "audio":return "Аудио / музыка";case "image":return "Фото";case "gif":return "GIF";case "sticker":return "Стикер";default:return "Файл";}}
    private View mediaTile(MessageDb.Attachment a,boolean gallery){
        if(a.kind.equals("round")){
            int dpWidth=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
            return circle(a,gallery?124:Math.min(224,Math.max(164,dpWidth-118)),!gallery);
        }
        if(a.kind.equals("voice")||a.kind.equals("audio"))return voice(a,gallery);
        if(a.kind.equals("image")||a.kind.equals("gif")||a.kind.equals("sticker")||a.kind.equals("video")){
            FrameLayout box=new FrameLayout(this);int size=gallery?84:Math.min(260,Math.max(190,
                Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-116));
            box.setBackground(shape(0x142e536b,8));box.setClipToOutline(true);
            ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(image,new FrameLayout.LayoutParams(-1,-1));
            if("sticker".equals(a.kind)&&isTgsPath(a.path)){
                loadTgsPreview(a.path,box);
            }else loadThumbnail(a.path,isVideoStickerPath(a.path)?"video":a.kind,image);
            if(a.kind.equals("video")||isVideoStickerPath(a.path)||a.kind.equals("gif")&&a.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$")){TextView play=text("▶",27,FG);play.setGravity(Gravity.CENTER);box.addView(play,new FrameLayout.LayoutParams(-1,-1));}
            box.setOnClickListener(v->openMedia(a));box.setOnLongClickListener(v->{mediaActions(a);return true;});
            FrameLayout outer=new FrameLayout(this);outer.addView(box,new FrameLayout.LayoutParams(px(size),px(gallery?84:Math.min(280,size))));return outer;
        }
        TextView file=text("📎 "+clip(filename(a.path),gallery?18:33),13,ACCENT);file.setPadding(px(8),px(10),px(8),px(10));
        file.setBackground(shape(CHIP,12));file.setOnClickListener(v->openMedia(a));
        file.setOnLongClickListener(v->{mediaActions(a);return true;});return file;
    }
    private String filename(String path){int p=path.lastIndexOf('/');return p<0?path:path.substring(p+1);}
    /** Tap: expand and play in-chat; tap again: pause/resume. Drag the bar to seek. */
    private View circle(MessageDb.Attachment a,int size,boolean chatCircle){
        FrameLayout holder=new FrameLayout(this);holder.setBackground(shape(0xff31526c,100));
        holder.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View v,Outline o){o.setOval(0,0,v.getWidth(),v.getHeight());}
        });holder.setClipToOutline(true);
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        holder.addView(preview,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,"round",preview);
        TextView overlay=text("▶",size>130?38:28,FG);overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0x38000000);holder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout wrap=column();wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(holder,p(size,size));
        TextView duration=text("00:00",12,SUB);duration.setGravity(Gravity.CENTER);wrap.addView(duration,p(size,24));
        loadDuration(a.path,duration);
        RoundControls controls=new RoundControls();
        controls.timer=duration;controls.preview=preview;controls.overlay=overlay;
        if(chatCircle){
            SeekBar seek=new SeekBar(this);seek.setMax(1000);seek.setProgress(0);
            seek.setVisibility(View.GONE);wrap.addView(seek,p(size,34));controls.seek=seek;
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){
                    controls.dragging=true;controls.wasPlaying=false;
                    try{if(player!=null&&a.path.equals(playingPath)){
                        controls.wasPlaying=player.isPlaying();if(controls.wasPlaying)player.pause();
                    }}catch(Exception ignored){}
                }
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                    if(!user)return;
                    try{if(player!=null&&a.path.equals(playingPath)){
                        int length=Math.max(0,player.getDuration());
                        duration.setText(formatTime(Math.round(length*value/1000f)/1000)+" / "+formatTime(length/1000));
                    }}catch(Exception ignored){}
                }
                @Override public void onStopTrackingTouch(SeekBar bar){
                    try{if(player!=null&&a.path.equals(playingPath)){
                        int length=Math.max(0,player.getDuration());
                        player.seekTo(Math.round(length*bar.getProgress()/1000f));
                        if(controls.wasPlaying){player.start();overlay.setVisibility(View.GONE);}
                    }}catch(Exception e){toast("Не удалось перемотать видео");}
                    controls.dragging=false;
                }
            });
        }
        android.view.ScaleGestureDetector videoZoom=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            float zoom=1f;
            @Override public boolean onScale(android.view.ScaleGestureDetector detector){
                zoom=Math.max(1f,Math.min(3f,zoom*detector.getScaleFactor()));
                holder.setScaleX(zoom);holder.setScaleY(zoom);return true;
            }
        });
        holder.setOnTouchListener((v,e)->{videoZoom.onTouchEvent(e);return e.getPointerCount()>1;});
        holder.setTag(controls);
        holder.setOnClickListener(v->{
            if(chatCircle){
                int max=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density)-105;
                int enlarged=Math.max(size,Math.min(260,max));
                if(holder.getLayoutParams().width<px(enlarged)){
                    holder.setLayoutParams(p(enlarged,enlarged));duration.setLayoutParams(p(enlarged,25));
                }
                if(controls.seek!=null){
                    controls.seek.setVisibility(View.VISIBLE);
                    controls.seek.setLayoutParams(p(enlarged,35));
                }
                wrap.requestLayout();
            }
            startRoundPlayback(a,holder,preview,overlay);
        });
        holder.setOnLongClickListener(v->{mediaActions(a);return true;});
        return wrap;
    }
    private View voice(MessageDb.Attachment a,boolean gallery){
        LinearLayout outer=column();outer.setBackground(shape(CHIP,14));outer.setPadding(px(8),px(5),px(8),px(5));
        LinearLayout inner=row();outer.addView(inner,p(gallery?98:218,47));
        TextView play=text("▶",23,FG);play.setGravity(Gravity.CENTER);inner.addView(play,p(32,42));
        LinearLayout details=column();details.setPadding(px(5),0,0,0);inner.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView bars=text("▂▆▃▇▄▅▂▆▃▄▇▂▅",gallery?11:15,ACCENT);bars.setSingleLine(true);details.addView(bars);
        TextView duration=text("00:00",11,SUB);details.addView(duration);loadDuration(a.path,duration);
        play.setOnClickListener(v->{
            if("audio".equals(a.kind)){openMusicPlayer(a);return;}
            startAudio(a.path,play,duration);
            playingAttachment=a;
            playerStrip.setVisibility(View.VISIBLE);
            playerText.setText((a.kind.equals("voice")?"Голосовое · ":"Музыка · ")+(a.author==null?"":a.author));
        });
        outer.setOnLongClickListener(v->{mediaActions(a);return true;});return outer;
    }
    private void clearMediaSelection(){
        multiSelectMedia=false;mediaSelection.clear();selectedMediaArchive="";
    }
    private void toggleMediaSelection(MessageDb.Attachment a){
        if(!multiSelectMedia)return;
        if(!activeKey.equals(selectedMediaArchive)){clearMediaSelection();toast("Чат изменился. Выбор очищен");return;}
        if(mediaSelection.containsKey(a.id))mediaSelection.remove(a.id);
        else if(mediaSelection.size()<100)mediaSelection.put(a.id,a);
        else toast("За один раз можно выбрать не более 100 файлов");
        configureBars();
    }
    private void confirmBatchExport(boolean downloads){
        if(batchRunning){toast("Сохранение уже выполняется");return;}
        if(mediaSelection.isEmpty()){toast("Сначала выбери файлы");return;}
        if(!activeKey.equals(selectedMediaArchive)){clearMediaSelection();toast("Выбран другой чат");return;}
        if(folder==null){toast("Папка с медиа не выбрана");return;}
        String[] destinations={"Галерея / Музыка", "Все в Загрузки"};
        new FontDialog.Builder(this).setTitle("Сохранить "+mediaSelection.size()+" файлов")
          .setMessage("Оригиналы не изменятся. Файлы сохраняются по очереди; операцию можно отменить после текущего файла. " +
              "OGG и Opus остаются в исходном формате, это не MP3-конвертация.")
          .setItems(destinations,(dlg,index)->{
              List<MediaBatchExporter.Entry> inputs=new ArrayList<>();
              for(MessageDb.Attachment a:mediaSelection.values())inputs.add(new MediaBatchExporter.Entry(a.path,a.kind));
              DocumentFolder source=folder;
              clearMediaSelection();showPage();
              if(Build.VERSION.SDK_INT<29){
                  pendingBatchFiles=inputs;pendingBatchSource=source;pendingBatchDownloads=index==1;
                  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                  intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
                  try{startActivityForResult(intent,BATCH_TREE_REQUEST);}catch(Exception e){
                      pendingBatchFiles=null;pendingBatchSource=null;toast("Не удалось выбрать папку");
                  }
              }else startBatchExport(inputs,source,index==1,null);
          }).setNegativeButton("Отмена",null).show();
    }
    private void startBatchExport(List<MediaBatchExporter.Entry> inputs,DocumentFolder rootFolder,boolean downloads,Uri tree){
        if(batchRunning)return;
        batchCancelled.set(false);batchRunning=true;
        TextView progress=text("Готовлю файлы…",14,FG);
        progress.setPadding(px(15),px(22),px(15),px(20));
        AlertDialog dialog=new FontDialog.Builder(this).setTitle("Пакетное сохранение")
           .setView(progress).setNegativeButton("Отменить после текущего файла",(d,w)->batchCancelled.set(true)).create();
        dialog.setOnCancelListener(d->batchCancelled.set(true));dialog.show();
        worker.execute(()->{
            MediaBatchExporter.Report report=MediaBatchExporter.export(this,rootFolder,inputs,downloads,tree,batchCancelled,
                (done,total,failed)->runOnUiThread(()->{if(!isFinishing())progress.setText("Обработано "+done+" из "+total+" · ошибок "+failed);}));
            runOnUiThread(()->{
                batchRunning=false;
                if(!isFinishing()){
                    dialog.dismiss();
                    new FontDialog.Builder(this).setTitle("Сохранение завершено")
                     .setMessage("Сохранено: "+report.saved+" · Ошибок: "+report.failed+" · " +
                         (batchCancelled.get()?"Остановлено пользователем.":"Готово.") +
                         "\nОригинальные файлы не изменены.")
                     .setPositiveButton("Понятно",null).show();
                }
            });
        });
    }
    private void addSelectionToCollection(){
        if(mediaSelection.isEmpty()){toast("Сначала выбери файлы");return;}
        addMediaToCollection(new ArrayList<>(mediaSelection.values()));
    }
    private void addMediaToCollection(List<MessageDb.Attachment> items){
        final String archive=activeKey;
        final MessageDb sourceDb=db;
        worker.execute(()->{
            List<MessageDb.MediaCollection> names=sourceDb.collections();
            runOnUiThread(()->{
                if(!archive.equals(activeKey)||isFinishing())return;
                String[] choices=new String[names.size()+1];choices[0]="+ Новая коллекция";
                for(int i=0;i<names.size();i++)choices[i+1]=names.get(i).name+" ("+names.get(i).count+")";
                new FontDialog.Builder(this).setTitle("Добавить "+items.size()+" вложений")
                .setItems(choices,(dialog,which)->{
                    if(which==0){
                        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Например: любимые кружки");
                        new FontDialog.Builder(this).setTitle("Название коллекции").setView(input)
                        .setPositiveButton("Создать",(d,w)->{
                            final String chosenName=input.getText().toString();
                            worker.execute(()->{
                            try{
                                if(!archive.equals(activeKey))return;
                                long id=sourceDb.createCollection(chosenName);
                                for(MessageDb.Attachment a:items)sourceDb.addToCollection(id,a);
                                runOnUiThread(()->toast("Коллекция создана"));
                            }catch(Exception e){runOnUiThread(()->toast("Не удалось создать: "+e.getMessage()));}
                            });
                        }).setNegativeButton("Отмена",null).show();
                    }else{
                        long id=names.get(which-1).id;
                        worker.execute(()->{
                            int ok=0;
                            if(!archive.equals(activeKey))return;
                            for(MessageDb.Attachment a:items)try{sourceDb.addToCollection(id,a);ok++;}catch(Exception ignored){}
                            final int saved=ok;
                            runOnUiThread(()->toast("Добавлено в коллекцию: "+saved));
                        });
                    }
                }).show();
            });
        });
    }
    private void showCollectionPicker(){
        final String archive=activeKey;
        worker.execute(()->{
            List<MessageDb.MediaCollection> names=db.collections();
            runOnUiThread(()->{
                if(!archive.equals(activeKey)||isFinishing())return;
                if(names.isEmpty()){
                    toast("Коллекций пока нет. Долго нажми на медиа и выбери «Добавить в коллекцию».");return;
                }
                String[] choices=new String[names.size()];
                for(int i=0;i<names.size();i++)choices[i]=names.get(i).name+" · "+names.get(i).count;
                new FontDialog.Builder(this).setTitle("Мои коллекции")
                    .setItems(choices,(d,idx)->showCollectionMedia(names.get(idx)))
                    .setNeutralButton("Управлять",(d,unused)->manageCollections(names))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void manageCollections(List<MessageDb.MediaCollection> names){
        String[] labels=new String[names.size()];
        for(int i=0;i<names.size();i++)labels[i]=names.get(i).name+" · "+names.get(i).count+" медиа";
        new FontDialog.Builder(this).setTitle("Удалить коллекцию (не файлы)")
            .setMessage("Удалятся только ссылки в выбранной коллекции. Фото, видео и сообщения останутся нетронутыми.")
            .setItems(labels,(d,index)->confirmDeleteCollection(names.get(index)))
            .setNegativeButton("Отмена",null).show();
    }
    private void confirmDeleteCollection(MessageDb.MediaCollection collection){
        final String currentKey=activeKey;
        final MessageDb currentDb=db;
        new FontDialog.Builder(this).setTitle("Удалить «"+collection.name+"»?")
            .setMessage("Это удалит только коллекцию и её список. Исходные файлы и сообщения не удаляются.")
            .setPositiveButton("Удалить коллекцию",(dialog,button)->worker.execute(()->{
                try{if(!currentKey.equals(activeKey))return;
                    int removed=currentDb.deleteCollection(collection.id);
                    runOnUiThread(()->{if(currentKey.equals(activeKey))toast(removed>0?"Коллекция удалена":"Коллекция уже отсутствует");});
                }catch(Exception error){runOnUiThread(()->toast("Не удалось удалить коллекцию"));}
            }))
            .setNegativeButton("Отмена",null).show();
    }
    private void showCollectionMedia(MessageDb.MediaCollection collection){
        final String archive=activeKey;
        worker.execute(()->{
            List<MessageDb.Attachment> media=db.collectionMedia(collection.id,200);
            runOnUiThread(()->{
                if(!archive.equals(activeKey)||isFinishing())return;
                list.removeAllViews();list.setBackgroundColor(BG);
                TextView header=text("← Назад в медиатеку · "+collection.name,17,FG);
                header.setPadding(px(12),px(15),px(12),px(15));list.addView(header);
                header.setOnClickListener(v->showPage());
                renderMedia(media);
                bottomPager.setVisibility(View.GONE);
                status.setText("Коллекция: "+media.size()+" файлов, показано до 200. Оригиналы не дублируются.");
            });
        });
    }
    private void inspectDuplicateMedia(){
        if(folder==null){toast("Папка с медиа не выбрана");return;}
        final DocumentFolder source=folder;
        final String archive=activeKey;
        new FontDialog.Builder(this).setTitle("Найти одинаковые медиа")
          .setMessage("Проверю до 200 уникальных файлов (до 128 МБ каждый, общий лимит 512 МБ). " +
              "Это может занять время. Файлы никогда не удаляются.")
          .setPositiveButton("Проверить",(d,w)->{
              final AtomicBoolean cancelled=new AtomicBoolean(false);
              TextView progress=text("Проверяю содержимое файлов…",14,FG);
              progress.setPadding(px(18),px(20),px(18),px(20));
              AlertDialog running=new FontDialog.Builder(this).setTitle("Поиск совпадений")
                  .setView(progress).setNegativeButton("Остановить",(x,y)->cancelled.set(true)).create();
              running.setOnCancelListener(x->cancelled.set(true));running.show();
              worker.execute(()->{
              List<MessageDb.Attachment> files=db.distinctMediaForInspection(200);
              ContentDuplicateIndex.Index hashes=new ContentDuplicateIndex.Index();
              int missing=0,oversized=0,scanned=0;
              final java.util.concurrent.atomic.AtomicLong bytesRead=new java.util.concurrent.atomic.AtomicLong();
              for(MessageDb.Attachment a:files){
                  if(cancelled.get()||bytesRead.get()>=ContentDuplicateIndex.MAX_TOTAL_BYTES)break;
                  try{
                      Uri uri=source.resolve(a.path);
                      if(uri==null){missing++;continue;}
                      try(InputStream raw=getContentResolver().openInputStream(uri)){
                          if(raw==null){missing++;continue;}
                          try(InputStream in=new ContentDuplicateIndex.BudgetedInput(raw,bytesRead,ContentDuplicateIndex.MAX_TOTAL_BYTES)){
                              ContentDuplicateIndex.Digest fingerprint=ContentDuplicateIndex.fingerprint(in,ContentDuplicateIndex.MAX_BYTES_PER_FILE,cancelled::get);
                              hashes.add(a.path,fingerprint);
                          }
                      }
                  }catch(IOException e){oversized++;}catch(Exception e){missing++;}
                  scanned++;
                  if(scanned%8==0){final int count=scanned;
                      runOnUiThread(()->{if(!isFinishing())progress.setText("Проверено: "+count+" / "+files.size());});}
              }
              List<List<String>> duplicateGroups=hashes.repeated();
              int total=scanned,errors=missing+oversized;
              runOnUiThread(()->{
                  running.dismiss();
                  if(!archive.equals(activeKey)||isFinishing())return;
                  StringBuilder report=new StringBuilder("Проверено: "+total+". Недоступно/пропущено: "+errors+".\n");
                  report.append("Групп одинакового содержимого: ").append(duplicateGroups.size());
                  int count=0;
                  for(List<String> group:duplicateGroups){
                      if(++count>12)break;
                      report.append("\n• ").append(group.size()).append(" экземпляра: ");
                      String raw=group.get(0).replace('\\','/');
                      report.append(raw.substring(raw.lastIndexOf('/')+1));
                  }
                  report.append("\n\nНикакие файлы не удалены. Это только поиск точных совпадений SHA-256.");
                  new FontDialog.Builder(this).setTitle("Результат проверки")
                    .setMessage(report.toString()).setPositiveButton("Готово",null).show();
              });
              });
          }).setNegativeButton("Отмена",null).show();
    }
    private void mediaActions(MessageDb.Attachment a){
        boolean round="round".equals(a.kind);
        MediaSavePolicy.Spec spec=MediaSavePolicy.plan(a.path,a.kind,false);
        String saveLabel=(spec.destination==MediaSavePolicy.Destination.PICTURES||spec.destination==MediaSavePolicy.Destination.MOVIES)
            ?"↓ Сохранить в галерею":(spec.destination==MediaSavePolicy.Destination.MUSIC?"↓ Сохранить в музыку":"↓ Сохранить на устройство");
        String[] names=round?
            new String[]{"▶ Плавающее видеосообщение","⛶ Полный экран / PiP","↗ Перейти к сообщению","☆ В избранное","Открыть внешним плеером",saveLabel,"↓ Сохранить в Загрузки","☑ Выбрать несколько","+ Добавить в коллекцию"}:
            new String[]{"▶ Воспроизвести / открыть","↗ Перейти к сообщению","☆ Отметить сообщение","⛶ Открыть полностью",saveLabel,"↓ Сохранить в Загрузки","☑ Выбрать несколько","+ Добавить в коллекцию"};
        new FontDialog.Builder(this).setTitle(mediaLabel(a.kind)+" · "+day(a.date)).setItems(names,(dlg,which)->{
            if(round){
                if(which==0)showFloatingRound(a);
                else if(which==1)openRoundPlayer(a);
                else if(which==2)jump(a.messageId,screen.equals("media")?"media":"");
                else if(which==3)toggleStar(a.messageId);
                else if(which==4)resolveMedia(a.path,uri->external(uri,a.path));
                else if(which==7){multiSelectMedia=true;selectedMediaArchive=activeKey;toggleMediaSelection(a);screen="media";showPage();}
                else if(which==8)addMediaToCollection(java.util.Collections.singletonList(a));
                else saveAttachment(a,which==6);
            }else{
                if(which==0||which==3)openMedia(a);
                else if(which==1)jump(a.messageId,"media");
                else if(which==2)toggleStar(a.messageId);
                else if(which==6){multiSelectMedia=true;selectedMediaArchive=activeKey;toggleMediaSelection(a);screen="media";showPage();}
                else if(which==7)addMediaToCollection(java.util.Collections.singletonList(a));
                else saveAttachment(a,which==5);
            }
        }).show();
    }
    private void saveAttachment(MessageDb.Attachment a,boolean downloads){
        MediaSavePolicy.Spec spec=MediaSavePolicy.plan(a.path,a.kind,downloads);
        Runnable save=()->{
            DocumentFolder sourceFolder=folder;
            if(sourceFolder==null){toast("Не выбрана папка с медиа");return;}
            if(android.os.Build.VERSION.SDK_INT<29){
                // Android 8–9: the system document picker provides scoped write permission.
                pendingMediaSaveRoot=sourceFolder;pendingMediaSavePath=a.path;pendingMediaSaveSpec=spec;
                Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType(spec.mime);
                picker.putExtra(Intent.EXTRA_TITLE,spec.filename);
                try{startActivityForResult(picker,SAVE_MEDIA_REQUEST);}catch(Exception e){
                    pendingMediaSaveRoot=null;pendingMediaSavePath=null;pendingMediaSaveSpec=null;
                    toast("Не удалось выбрать папку");
                }
                return;
            }
            resolveMedia(a.path,uri->{
                status.setText("Сохраняю медиа на устройство…");
                worker.execute(()->{
                    try{
                        MediaSaver.Result result=MediaSaver.saveToLibrary(this,uri,spec);
                        runOnUiThread(()->toast("Сохранено · "+(result.bytes/1024)+" КБ · "+saveLocation(spec)));
                    }catch(Exception error){runOnUiThread(()->toast("Ошибка сохранения: "+error.getMessage()));}
                });
            });
        };
        if(("voice".equals(a.kind)||"audio".equals(a.kind))&&!MediaSavePolicy.isActualMp3(spec)){
            new FontDialog.Builder(this).setTitle("Сохранить аудио")
                .setMessage("Этот файл имеет формат "+spec.mime+". Telegram часто хранит голосовые в OGG/Opus. " +
                    "Просто переименовать такой файл в MP3 нельзя: он перестанет корректно открываться. " +
                    "Сохранить оригинал без потери качества? Конвертация в MP3 пока не реализована.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Сохранить оригинал",(dlg,which)->save.run()).show();
        }else save.run();
    }
    private String saveLocation(MediaSavePolicy.Spec spec){
        switch(spec.destination){
            case PICTURES:return "Галерея / фото";
            case MOVIES:return "Галерея / видео";
            case MUSIC:return "Музыка";
            default:return "Загрузки";
        }
    }
    private void jump(long id,String source){
        status.setText("Перехожу к сообщению…");
        final String requestedArchive=activeKey;
        final MessageDb requestedDb=db;
        if(requestedDb==null)return;
        worker.execute(()->{
            if(!requestedArchive.equals(activeKey)||db!=requestedDb)return;
            int at=requestedDb.hasMessage(id)?requestedDb.positionFor(id):-1;
            List<MessageDb.Item> candidates=requestedDb.pinned();
            long nextPinned=-1;
            for(MessageDb.Item candidate:candidates)if(candidate.id<id){nextPinned=candidate.id;break;}
            if(nextPinned<0&&!candidates.isEmpty())nextPinned=candidates.get(0).id;
            final long rotation=nextPinned;
            runOnUiThread(()->{
                if(!requestedArchive.equals(activeKey)||db!=requestedDb||isFinishing())return;
                if(at<0){toast("Сообщение не найдено");return;}
                if(rotation>=0)pinnedFocus=rotation;
                home=false;autoLoaded=PAGE;
                returnScreen=source;screen="chat";chatOffset=Math.max(0,at-7);highlightId=id;showPage();
            });
        });
    }
    private void restoreReturn(){
        if(returnScreen.isEmpty())return;
        String target=returnScreen;
        closeFloatingRound();stopPlayback();
        returnScreen="";highlightId=-1;screen=target;home=false;
        autoFloating=false;showPage();
    }

    private void resolveMedia(String path,java.util.function.Consumer<Uri> success){
        if(folder==null){toast("Сначала выбери папку с экспортом");return;}
        DocumentFolder current=folder;
        final String requestedArchive=activeKey;
        final int requestedLoad=loadToken;
        final int requestedPlayback=playbackGeneration;
        mediaWorker.execute(()->{
            Uri result=null;try{result=current.resolve(path);}catch(Exception ignored){}
            Uri uri=result;runOnUiThread(()->{
                // A slow SAF provider must never open media from a previously
                // selected chat after navigation or player closure.
                if(isFinishing()||!MediaRequestGuard.isCurrent(requestedArchive,activeKey,
                        requestedLoad,loadToken,requestedPlayback,playbackGeneration))return;
                if(uri==null){status.setText("Файл не найден: "+path);toast("Медиа не найдено в выбранной папке");}
                else success.accept(uri);
            });
        });
    }
    private void openMedia(MessageDb.Attachment a){
        if(a.kind.equals("gif")&&a.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$")){resolveMedia(a.path,this::fullVideo);return;}
        // Telegram VP9 WebM stickers are short alpha videos, not BitmapFactory images.
        if("sticker".equals(a.kind)&&isVideoStickerPath(a.path)){
            resolveMedia(a.path,this::fullVideo);return;
        }
        if("sticker".equals(a.kind)&&isTgsPath(a.path)){
            resolveMedia(a.path,this::openTgsSticker);return;
        }
        if(a.kind.equals("image")||a.kind.equals("sticker")||a.kind.equals("gif")){openPhotoGallery(a);return;}
        if(a.kind.equals("round")){showFloatingRound(a);return;}
        if(a.kind.equals("video"))resolveMedia(a.path,this::fullVideo);
        else if(a.kind.equals("audio"))openMusicPlayer(a);
        else if(a.kind.equals("voice"))resolveMedia(a.path,uri->external(uri,a.path));
        else resolveMedia(a.path,uri->external(uri,a.path));
    }
    /** Open the dedicated offline player for music, never for voice notes. */
    private void openMusicPlayer(MessageDb.Attachment song){
        String tree=activeTree();
        if(tree==null||tree.trim().isEmpty()){toast("Выбери папку с оригинальной музыкой");return;}
        closeFloatingRound();stopPlayback();
        Intent intent=new Intent(this,MusicPlayerActivity.class);
        intent.putExtra("chat_key",activeKey);
        intent.putExtra("tree",tree);
        intent.putExtra("attachment_id",song.id);
        startActivity(intent);
    }
    /** Static first frame of a local TGS in chat and gallery, with bounded Lottie cache.
     * Deliberately never runs background looping animations in a scrolling list. */
    private final LruCache<String,LottieComposition> tgsCompositions=new LruCache<>(10);
    private boolean isVideoStickerPath(String path){
        return path!=null&&path.toLowerCase(Locale.ROOT).matches(".*\\.webm(\\?.*)?$");
    }
    private boolean isTgsPath(String path){
        return path!=null&&path.toLowerCase(Locale.ROOT).matches(".*\\.tgs(\\?.*)?$");
    }
    private void loadTgsPreview(String path,FrameLayout target){
        if(folder==null)return;
        final DocumentFolder source=folder;
        final String archive=activeKey;
        final int token=loadToken;
        final TextView state=text("◌",24,ACCENT);
        state.setGravity(Gravity.CENTER);
        state.setContentDescription("Загружается анимированный стикер");
        target.addView(state,new FrameLayout.LayoutParams(-1,-1));
        final LottieComposition cached=tgsCompositions.get(archive+":"+path);
        if(cached!=null){setTgsPreview(target,state,cached);return;}
        mediaWorker.execute(()->{
            LottieComposition composition=null;
            try{
                Uri uri=source.resolve(path);
                if(uri==null)throw new IOException("TGS не найден");
                try(InputStream raw=getContentResolver().openInputStream(uri)){
                    String json=TgsPayload.decode(raw);
                    JSONObject payload=new JSONObject(json);
                    if(!payload.has("layers")||!payload.has("fr")||payload.optDouble("fr")<=0)
                        throw new IOException("Неверный формат TGS");
                    LottieResult<LottieComposition> result=LottieCompositionFactory.fromJsonStringSync(json,null);
                    composition=result.getValue();
                    if(composition==null)throw new IOException("Неподдерживаемый TGS");
                }
            }catch(Exception ignored){}
            final LottieComposition ready=composition;
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||!archive.equals(activeKey)||target.getParent()==null)return;
                if(ready!=null){
                    tgsCompositions.put(archive+":"+path,ready);
                    setTgsPreview(target,state,ready);
                }else{
                    state.setText("⚠");
                    state.setTextSize(16);
                    state.setContentDescription("Не удалось показать стикер. Нажмите, чтобы открыть отдельно");
                }
            });
        });
    }
    private void setTgsPreview(FrameLayout target,TextView loading,LottieComposition composition){
        if(loading.getParent()==target)target.removeView(loading);
        LottieAnimationView preview=new LottieAnimationView(this);
        preview.setContentDescription("Превью анимированного стикера");
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setComposition(composition);
        preview.setProgress(0.08f); // a visible frame, not a grey TGS placeholder
        preview.setRepeatCount(0);
        preview.setClickable(false);
        target.addView(preview,new FrameLayout.LayoutParams(-1,-1));
    }
    /** Telegram TGS is gzipped Lottie JSON. No media leaves this device.
     * Both gzip and decompressed JSON have hard limits to resist hostile archives. */
    private void openTgsSticker(Uri localUri){
        final Dialog dialog=new FontDialog(this);
        LinearLayout content=column();content.setPadding(px(12),px(14),px(12),px(18));
        content.setBackground(gradient(PANEL,BG,20));
        TextView header=text("Анимированный стикер · офлайн",16,FG);header.setGravity(Gravity.CENTER);
        content.addView(header,p(-1,50));
        LottieAnimationView animation=new LottieAnimationView(this);
        animation.setContentDescription("Анимированный TGS-стикер Telegram");
        animation.setRepeatCount(LottieDrawable.INFINITE);
        animation.setScaleType(ImageView.ScaleType.FIT_CENTER);
        content.addView(animation,p(-1,Math.min(px(340),getResources().getDisplayMetrics().heightPixels/2)));
        TextView progress=text("Открываю локальный файл…",12,SUB);progress.setGravity(Gravity.CENTER);content.addView(progress,p(-1,38));
        Button close=btn("Закрыть");content.addView(close,p(-1,46));close.setOnClickListener(v->dialog.dismiss());
        dialog.setContentView(content);dialog.setOnDismissListener(d->animation.cancelAnimation());
        dialog.show();dialog.getWindow().setLayout(Math.min(px(400),getResources().getDisplayMetrics().widthPixels-px(20)),-2);
        final int generation=loadToken;final String archive=activeKey;
        mediaWorker.execute(()->{
            try{
                String json;
                try(InputStream raw=getContentResolver().openInputStream(localUri)){
                    json=TgsPayload.decode(raw);
                }
                JSONObject obj=new JSONObject(json);
                if(!obj.has("layers")||!obj.has("fr")||obj.optDouble("op")-obj.optDouble("ip")>360)
                    throw new IOException("Некорректная TGS-анимация");
                LottieResult<LottieComposition> result=LottieCompositionFactory.fromJsonStringSync(json,null);
                if(result.getValue()==null)throw new IOException("Рендерер не поддерживает этот TGS");
                LottieComposition composition=result.getValue();
                runOnUiThread(()->{
                    if(isFinishing()||!dialog.isShowing()||!archive.equals(activeKey)||generation!=loadToken)return;
                    progress.setText("Нажми «Закрыть», чтобы вернуться в чат");
                    animation.setComposition(composition);animation.playAnimation();
                });
            }catch(Exception failure){runOnUiThread(()->{if(dialog.isShowing())progress.setText(
                "Не удалось воспроизвести TGS. Исходный файл не изменён: "+failure.getClass().getSimpleName());});}
        });
    }
    private void external(Uri uri,String path){
        String ext=path.contains(".")?path.substring(path.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
        String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);if(mime==null)mime="application/octet-stream";
        Intent intent=new Intent(Intent.ACTION_VIEW);intent.setDataAndType(uri,mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(Intent.createChooser(intent,"Открыть файл"));}
        catch(Exception e){toast("Не найдено приложение для открытия файла");}
    }
    /** One consistent fullscreen player: no overlapping legacy MediaController and top bar. */
    private void fullVideo(Uri uri){
        stopPlayback();
        final Dialog dialog=new FontDialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(gradient(0xff040910,0xff152338,0));
        VideoView video=new VideoView(this);
        frame.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(px(10),px(4),px(10),px(4));
        header.setBackground(glass(0xe812263b,21));
        Button back=btn("‹ Назад");header.addView(back,p(94,44));back.setOnClickListener(v->dialog.dismiss());
        TextView caption=text("Видео · локальный архив",13,FG);caption.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(caption,new LinearLayout.LayoutParams(0,px(44),1));
        Button options=btn("⚙");options.setContentDescription("Скорость видео и открыть внешним плеером");
        header.addView(options,p(49,44));
        frame.addView(header,new FrameLayout.LayoutParams(-1,px(54),Gravity.TOP));
        LinearLayout bottom=column();bottom.setPadding(px(10),px(8),px(10),px(15));
        bottom.setBackground(glass(0xe812263b,21));
        SeekBar timeline=new SeekBar(this);timeline.setMax(1000);bottom.addView(timeline,p(-1,32));
        LinearLayout actions=row();actions.setGravity(Gravity.CENTER_VERTICAL);bottom.addView(actions,p(-1,44));
        Button rewind=btn("−10 с");actions.addView(rewind,p(75,43));
        Button pause=btn("Ⅱ");actions.addView(pause,new LinearLayout.LayoutParams(0,px(43),1));
        Button forward=btn("+10 с");actions.addView(forward,p(75,43));
        TextView timer=text("0:00 / 0:00",12,FG);timer.setGravity(Gravity.CENTER);bottom.addView(timer,p(-1,23));
        frame.addView(bottom,new FrameLayout.LayoutParams(-1,px(114),Gravity.BOTTOM));
        final android.media.MediaPlayer[] prepared={null};
        final float[] selectedSpeed={1f};
        final boolean[] seeking={false};
        final Runnable[] updater={null};
        final Runnable[] autoHide={null};
        final boolean[] visible={true};
        final Runnable hideControls=()->{
            if(!dialog.isShowing()||!video.isPlaying()||!visible[0])return;
            visible[0]=false;
            header.animate().alpha(0f).setDuration(motionDuration(260)).withEndAction(()->header.setVisibility(View.GONE)).start();
            bottom.animate().alpha(0f).setDuration(motionDuration(260)).withEndAction(()->bottom.setVisibility(View.GONE)).start();
        };
        autoHide[0]=hideControls;
        video.setOnClickListener(v->{
            if(visible[0]){
                handler.removeCallbacks(hideControls);hideControls.run();
            }else{
                visible[0]=true;header.animate().cancel();bottom.animate().cancel();
                header.setVisibility(View.VISIBLE);bottom.setVisibility(View.VISIBLE);
                header.animate().alpha(1f).setDuration(motionDuration(200)).start();
                bottom.animate().alpha(1f).setDuration(motionDuration(200)).start();
                handler.postDelayed(hideControls,4500);
            }
        });
        Runnable refresh=new Runnable(){@Override public void run(){
            if(!dialog.isShowing())return;
            try{
                if(!seeking[0]){
                    int duration=Math.max(0,video.getDuration());int current=Math.max(0,video.getCurrentPosition());
                    timeline.setProgress(duration==0?0:(int)Math.min(1000,1000L*current/duration));
                    timer.setText(formatTime(current/1000)+" / "+formatTime(duration/1000));
                }
                pause.setText(video.isPlaying()?"Ⅱ":"▶");
            }catch(Exception ignored){}
            handler.postDelayed(this,350);
        }};
        updater[0]=refresh;
        rewind.setOnClickListener(v->{try{video.seekTo(Math.max(0,video.getCurrentPosition()-10000));}catch(Exception ignored){}});
        forward.setOnClickListener(v->{try{video.seekTo(Math.min(Math.max(0,video.getDuration()),video.getCurrentPosition()+10000));}catch(Exception ignored){}});
        pause.setOnClickListener(v->{try{if(video.isPlaying())video.pause();else video.start();}catch(Exception e){toast("Видеофайл не поддерживается этим декодером");}});
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar b){seeking[0]=true;}
            public void onProgressChanged(SeekBar b,int progress,boolean user){
                if(user){int duration=Math.max(0,video.getDuration());timer.setText(formatTime((int)(duration*progress/1000L)/1000)+" / "+formatTime(duration/1000));}
            }
            public void onStopTrackingTouch(SeekBar b){try{video.seekTo((int)(Math.max(0,video.getDuration())*b.getProgress()/1000L));}catch(Exception ignored){}seeking[0]=false;}
        });
        options.setOnClickListener(v->{
            String[] speedNames={"0.25×","0.5×","0.75×","1×","1.25×","1.5×","1.75×","2×","Открыть внешним плеером"};
            float[] speedValues={0.25f,0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f};
            new FontDialog.Builder(this).setTitle("Скорость и действия с видео")
                .setItems(speedNames,(d,index)->{
                    if(index==8){dialog.dismiss();external(uri,"media.mp4");return;}
                    try{
                        if(prepared[0]==null){toast("Подожди загрузки видео");return;}
                        // Android MediaPlayer may reject unsupported speeds; do not claim success then.
                        prepared[0].setPlaybackParams(new android.media.PlaybackParams().setSpeed(speedValues[index]));
                        selectedSpeed[0]=speedValues[index];
                        toast("Скорость "+speedNames[index]);
                    }catch(Exception unsupported){toast("Этот декодер не поддерживает "+speedNames[index]+". Попробуй другую скорость.");}
                }).show();
        });
        dialog.setContentView(frame);dialog.show();
        Window window=dialog.getWindow();if(window!=null)window.setLayout(-1,-1);
        video.setOnPreparedListener(mp->{
            if(!dialog.isShowing())return;
            prepared[0]=mp;
            try{video.start();handler.post(updater[0]);handler.postDelayed(autoHide[0],4500);}catch(Exception e){toast("Не удалось запустить видео");}
        });
        video.setOnCompletionListener(mp->{pause.setText("▶");});
        video.setOnErrorListener((mp,what,extra)->{
            pause.setText("▶");
            toast("Видео не удалось декодировать. Открой во внешнем плеере через ⚙.");
            return true;
        });
        dialog.setOnDismissListener(d->{handler.removeCallbacks(updater[0]);handler.removeCallbacks(autoHide[0]);
            header.animate().cancel();bottom.animate().cancel();prepared[0]=null;video.stopPlayback();});
        video.setVideoURI(uri);
        video.requestFocus();
    }
    private void stopPlayback(){
        ++playbackGeneration; // invalidate any unresolved media callbacks from the previous tab
        if(progressUpdater!=null){handler.removeCallbacks(progressUpdater);progressUpdater=null;}
        if(playerStrip!=null)playerStrip.setVisibility(View.GONE);
        inlineHolder=null;playingAttachment=null;
        if(player!=null){
            try{player.stop();}catch(Exception ignored){}
            try{player.release();}catch(Exception ignored){}
            player=null;
        }
        if(activeTexture!=null){
            TextureView previous=activeTexture;
            activeTexture=null;
            ViewGroup parent=previous.getParent() instanceof ViewGroup?(ViewGroup)previous.getParent():null;
            if(parent!=null)parent.removeView(previous);
        }
        if(activeRound!=null){
            activeRound.preview.setVisibility(View.VISIBLE);
            activeRound.overlay.setVisibility(View.VISIBLE);
            if(activeRound.seek!=null)activeRound.seek.setProgress(0);
            activeRound=null;
        }
        playingPath="";
    }
    /** TextureView lets Android crop video to a true circle while the SeekBar remains outside it. */
    private void startInlineRound(String path,FrameLayout holder,ImageView poster,TextView overlay){
        if(player!=null&&path.equals(playingPath)){
            try{
                if(player.isPlaying()){player.pause();overlay.setVisibility(View.VISIBLE);}
                else{player.start();overlay.setVisibility(View.GONE);}
            }catch(Exception ignored){}
            return;
        }
        stopPlayback();
        final int generation=playbackGeneration;
        inlineHolder=holder;
        RoundControls controls=holder.getTag() instanceof RoundControls?(RoundControls)holder.getTag():null;
        if(controls!=null)activeRound=controls;
        resolveMedia(path,uri->{
            if(generation!=playbackGeneration||!holder.isAttachedToWindow()||isFinishing())return;
            playingPath=path;
            inlineHolder=holder;
            TextureView texture=new TextureView(this);activeTexture=texture;
            overlay.bringToFront();
            final boolean[] decodedFrame={false};
            final RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();
            // Clear feedback instead of an endless silent spinner if a device codec freezes.
            final Runnable[] diagnose={null};
            texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
                @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int w,int h){
                    if(generation!=playbackGeneration||!path.equals(playingPath))return;
                    try{
                        MediaPlayer p=new MediaPlayer();player=p;
                        Surface screenSurface=new Surface(surface);
                        p.setSurface(screenSurface);screenSurface.release();
                        p.setDataSource(MainActivity.this,uri);p.setLooping(true);
                        p.setOnPreparedListener(mp->{
                            if(mp!=player)return;
                            Matrix transform=new Matrix();
                            float vw=Math.max(1,mp.getVideoWidth()),vh=Math.max(1,mp.getVideoHeight());
                            float scale=Math.max(w/vw,h/vh);
                            transform.setScale(vw*scale/w,vh*scale/h,w/2f,h/2f);
                            texture.setTransform(transform);
                            // A prepared decoder is not proof a frame has been rendered.
                            // Keep the thumbnail above black/empty video surfaces until
                            // TextureView receives its first updated frame.
                            poster.bringToFront();overlay.bringToFront();
                            mp.start();
                            diagnose[0]=new Runnable(){
                                @Override public void run(){
                                    if(generation!=playbackGeneration||player!=mp||activeTexture!=texture)return;
                                    try{
                                        RoundPlaybackWatchdog.State health=guard.observe(
                                            SystemClock.uptimeMillis(),mp.getCurrentPosition(),mp.isPlaying(),decodedFrame[0]);
                                        if(health!=RoundPlaybackWatchdog.State.OK){
                                            // Do not alter the source MP4; offer the independent VideoView player.
                                            stopPlayback();
                                            overlay.setText("▶ Открыть в плеере");
                                            overlay.setTextSize(16);
                                            overlay.setVisibility(View.VISIBLE);
                                            holder.setOnClickListener(v->openRoundUri(uri));
                                            toast(health==RoundPlaybackWatchdog.State.FIRST_FRAME_TIMEOUT?
                                                "Кружок не показывает кадры. Попробуй полноэкранный плеер.":
                                                "Видео остановилось. Нажми, чтобы открыть в плеере.");
                                            return;
                                        }
                                        handler.postDelayed(this,600);
                                    }catch(Exception e){
                                        stopPlayback();overlay.setText("▶ Открыть в плеере");
                                        overlay.setVisibility(View.VISIBLE);
                                        holder.setOnClickListener(v->openRoundUri(uri));
                                    }
                                }
                            };
                            handler.post(diagnose[0]);
                            if(controls!=null){
                                controls.timer.setText("0:00 / "+formatTime(Math.max(0,mp.getDuration()/1000)));
                                progressUpdater=new Runnable(){
                                    @Override public void run(){
                                        if(player!=mp)return;
                                        try{
                                            int current=Math.max(0,mp.getCurrentPosition());
                                            int length=Math.max(1,mp.getDuration());
                                            if(!controls.dragging){
                                                if(controls.seek!=null)
                                                    controls.seek.setProgress(Math.min(1000,Math.round(1000f*current/length)));
                                                controls.timer.setText(formatTime(current/1000)+" / "+formatTime(length/1000));
                                            }
                                            handler.postDelayed(this,250);
                                        }catch(Exception ignored){}
                                    }
                                };
                                handler.post(progressUpdater);
                            }
                        });
                        p.setOnErrorListener((mp,what,extra)->{
                            if(mp!=player||generation!=playbackGeneration)return true;
                            toast("Не удалось воспроизвести видео-сообщение. Удержи для полного экрана.");
                            stopPlayback();overlay.setText("▶ Открыть в плеере");
                            overlay.setVisibility(View.VISIBLE);
                            holder.setOnClickListener(v->openRoundUri(uri));return true;
                        });
                        p.prepareAsync();
                    }catch(Exception e){
                        stopPlayback();overlay.setText("▶ Открыть в плеере");
                        overlay.setVisibility(View.VISIBLE);
                        holder.setOnClickListener(v->openRoundUri(uri));toast("Ошибка воспроизведения MP4");
                    }
                }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int w,int h){}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){
                    if(activeTexture==texture){
                        // Do not remove a TextureView from its parent from inside
                        // its own destruction callback (can crash during back navigation).
                        activeTexture=null;
                        stopPlayback();
                    }
                    return true;
                }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture surface){
                    if(generation!=playbackGeneration||activeTexture!=texture)return;
                    decodedFrame[0]=true;
                    if(poster.getVisibility()!=View.INVISIBLE)poster.setVisibility(View.INVISIBLE);
                    try{if(player!=null&&player.isPlaying())overlay.setVisibility(View.GONE);
                        else overlay.setVisibility(View.VISIBLE);}
                    catch(Exception ignored){overlay.setVisibility(View.VISIBLE);}
                }
            });
            holder.addView(texture,1,new FrameLayout.LayoutParams(-1,-1));
            overlay.bringToFront();
        });
    }
    private void startAudio(String path,TextView play,TextView timer){
        if(player!=null&&path.equals(playingPath)){
            try{if(player.isPlaying()){player.pause();play.setText("▶");}else{player.start();play.setText("❚❚");}}catch(Exception ignored){}
            return;
        }
        stopPlayback();final int request=playbackGeneration;resolveMedia(path,uri->{
            if(request!=playbackGeneration||isFinishing()||isDestroyed()||!play.isAttachedToWindow())return;
            try{
                MediaPlayer p=new MediaPlayer();player=p;playingPath=path;p.setDataSource(this,uri);
                p.setOnPreparedListener(mp->{if(mp!=player)return;mp.start();play.setText("❚❚");
                    progressUpdater=new Runnable(){@Override public void run(){
                        if(mp!=player)return;
                        try{int seconds=mp.getCurrentPosition()/1000;
                            int len=mp.getDuration()/1000;
                            timer.setText(formatTime(seconds)+" / "+formatTime(len));
                            handler.postDelayed(this,500);
                        }catch(Exception ignored){}
                    }};handler.post(progressUpdater);
                });
                p.setOnCompletionListener(mp->{if(mp!=player)return;play.setText("▶");stopPlayback();});
                p.setOnErrorListener((mp,what,extra)->{if(mp!=player)return true;toast("Не удалось воспроизвести аудио");stopPlayback();play.setText("▶");return true;});
                p.prepareAsync();
            }catch(Exception e){stopPlayback();toast("Аудиофайл недоступен");}
        });
    }
    private String formatTime(int s){return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private void loadDuration(String path,TextView target){
        String cached=durations.get(path);if(cached!=null){target.setText(cached);return;}
        if(folder==null)return;DocumentFolder current=folder;int token=loadToken;
        mediaWorker.execute(()->{
            String time="00:00";
            try{Uri uri=current.resolve(path);if(uri!=null){MediaMetadataRetriever reader=new MediaMetadataRetriever();
                try{reader.setDataSource(this,uri);String raw=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(raw!=null)time=formatTime((int)(Long.parseLong(raw)/1000));}finally{reader.release();}
            }}catch(Exception ignored){}
            String result=time;durations.put(path,result);
            runOnUiThread(()->{if(token==loadToken)target.setText(result);});
        });
    }
    /** Low-priority, two-item prefetch for the *next* media page; never reads video bytes. */
    private void scheduleMediaPrefetch(int token,String kind,int nextOffset,int count){
        if(nextOffset>=count||folder==null)return;
        final MessageDb source=db;
        final DocumentFolder mediaFolder=folder;
        final String prefix=activeKey+"|";
        scheduledPrefetch=()->{
            if(token!=loadToken||!"media".equals(screen))return;
            worker.execute(()->{
                try{
                    List<MessageDb.Attachment> next=source.mediaPage(kind,nextOffset,Math.max(8,MediaPrefetchPolicy.MAX_NEXT_PAGE_PREVIEWS));
                    int submitted=0;
                    for(MessageDb.Attachment a:next){
                        if(submitted>=MediaPrefetchPolicy.MAX_NEXT_PAGE_PREVIEWS||token!=loadToken)break;
                        if(!MediaPrefetchPolicy.eligible(a.kind,a.path))continue;
                        String cacheKey=prefix+a.path;
                        if(thumbs.get(cacheKey)!=null)continue;
                        submitted++;
                        mediaWorker.execute(()->{
                            if(token!=loadToken||thumbs.get(cacheKey)!=null)return;
                            Bitmap preview=decodeThumbnail(mediaFolder,a.path,a.kind);
                            if(preview==null)return;
                            if(token==loadToken)thumbs.put(cacheKey,preview);
                            else preview.recycle();
                        });
                    }
                }catch(Exception ignored){/* media prefetch is best effort, never block navigation */}
            });
        };
        handler.postDelayed(scheduledPrefetch,450);
    }

    private Bitmap decodeThumbnail(DocumentFolder source,String path,String kind){
        Bitmap bitmap=null;
        try{
            Uri uri=source.resolve(path);
            if(uri==null)return null;
            boolean picture=kind.equals("image")||kind.equals("sticker")||kind.equals("gif")&&
                !path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|webm)(\\?.*)?$");
            if(picture){
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    BitmapFactory.decodeStream(input,null,options);
                }
                options.inJustDecodeBounds=false;
                options.inSampleSize=Math.max(1,Math.max(options.outWidth/230,options.outHeight/230));
                try(InputStream input=getContentResolver().openInputStream(uri)){
                    bitmap=BitmapFactory.decodeStream(input,null,options);
                }
            }else{
                MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                try{
                    retriever.setDataSource(this,uri);
                    bitmap=Build.VERSION.SDK_INT>=27?
                        retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,270,270):
                        retriever.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                }finally{retriever.release();}
            }
            if(bitmap!=null&&(bitmap.getWidth()>270||bitmap.getHeight()>270)){
                float scale=Math.min(270f/bitmap.getWidth(),270f/bitmap.getHeight());
                Bitmap small=Bitmap.createScaledBitmap(bitmap,Math.max(1,(int)(bitmap.getWidth()*scale)),
                    Math.max(1,(int)(bitmap.getHeight()*scale)),true);
                if(small!=bitmap)bitmap.recycle();bitmap=small;
            }
            return bitmap;
        }catch(Exception bad){if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();return null;}
    }

    private void loadThumbnail(String path,String kind,ImageView image){
        String cacheKey=activeKey+"|"+path;
        image.setTag(cacheKey);
        Bitmap cached=thumbs.get(cacheKey);
        if(cached!=null&&!cached.isRecycled()){image.setImageBitmap(cached);return;}
        if(folder==null)return;
        DocumentFolder source=folder;int token=loadToken;
        mediaWorker.execute(()->{
            Bitmap bitmap=decodeThumbnail(source,path,kind);
            if(bitmap==null)return;
            Bitmap loaded=bitmap;
            runOnUiThread(()->{
                if(!isFinishing()&&token==loadToken){
                    Bitmap existing=thumbs.get(cacheKey);
                    Bitmap result=existing!=null&&!existing.isRecycled()?existing:loaded;
                    if(result==loaded)thumbs.put(cacheKey,loaded);
                    else loaded.recycle();
                    if(cacheKey.equals(image.getTag()))image.setImageBitmap(result);
                }else loaded.recycle();
            });
        });
    }

    private int darken(int color){return Color.rgb(Math.round(Color.red(color)*.8f),Math.round(Color.green(color)*.8f),Math.round(Color.blue(color)*.8f));}
    private String colorKey(String name){return "sender_"+activeKey+"_"+MessageDb.normalizedAuthor(name);}
    private int authorColor(String name){
       int idx=((name==null?0:name.hashCode())&Integer.MAX_VALUE)%participantColors.length;
       return getPreferences(MODE_PRIVATE).getInt(colorKey(name),name!=null&&name.equalsIgnoreCase(mine)?OUTGOING:participantColors[idx]);
    }

    private void openPhotoGallery(MessageDb.Attachment initial){
        Dialog dialog=new FontDialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(gradient(0xff080f1d,0xff162336,16));
        ImageView picture=new ImageView(this);picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(picture,new FrameLayout.LayoutParams(-1,-1));
        TextView caption=text("Фото",14,0xfff1f8ff);caption.setGravity(Gravity.CENTER);
        caption.setBackground(glass(0xdb13283f,19));
        caption.setLetterSpacing(.035f);
        frame.addView(caption,new FrameLayout.LayoutParams(-1,px(52),Gravity.TOP));
        Button close=btn("✕");frame.addView(close,new FrameLayout.LayoutParams(px(48),px(48),Gravity.TOP|Gravity.END));
        close.setOnClickListener(v->dialog.dismiss());
        HorizontalScrollView strip=new HorizontalScrollView(this);strip.setHorizontalScrollBarEnabled(false);
        LinearLayout thumbsRow=row();thumbsRow.setPadding(px(4),px(4),px(4),px(4));
        thumbsRow.setBackground(glass(0xcb152a41,22));strip.addView(thumbsRow);
        FrameLayout.LayoutParams film=new FrameLayout.LayoutParams(-1,px(83),Gravity.BOTTOM);frame.addView(strip,film);
        final MessageDb.Attachment[] current={initial};
        final int[] generation={0};
        dialog.setOnDismissListener(ignored->{generation[0]++;picture.animate().cancel();});
        final java.util.function.Consumer<MessageDb.Attachment>[] show=new java.util.function.Consumer[1];
        show[0]=item->{
            current[0]=item;int id=++generation[0];picture.setScaleX(1f);picture.setScaleY(1f);
            picture.setPivotX(picture.getWidth()/2f);picture.setPivotY(picture.getHeight()/2f);
            picture.setTranslationX(0f);picture.setTranslationY(0f);
            picture.animate().cancel();picture.setImageDrawable(null);
            caption.setText(day(item.date)+" · "+(item.author==null?"Фото":item.author));
            DocumentFolder source=folder;
            if(source==null)return;
            mediaWorker.execute(()->{
                Bitmap bitmap=null;
                android.graphics.drawable.Drawable animated=null;
                try{
                    Uri uri=source.resolve(item.path);
                    if(uri!=null){
                        if(item.kind.equals("gif")&&item.path.toLowerCase(Locale.ROOT).matches(".*\\.gif(\\?.*)?$")&&Build.VERSION.SDK_INT>=28){
                            try{
                                android.graphics.ImageDecoder.Source gifSource=android.graphics.ImageDecoder.createSource(getContentResolver(),uri);
                                animated=android.graphics.ImageDecoder.decodeDrawable(gifSource,(decoder,info,imageSource)->{
                                    int iw=info.getSize().getWidth(),ih=info.getSize().getHeight();
                                    int max=Math.max(iw,ih);
                                    if(max>1800)decoder.setTargetSize(Math.max(1,iw*1800/max),Math.max(1,ih*1800/max));
                                });
                            }catch(Exception ignored){} 
                        }
                        if(animated==null){
                        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                        try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,bounds);}
                        BitmapFactory.Options opts=new BitmapFactory.Options();int scale=1;
                        while(bounds.outWidth/scale>2400||bounds.outHeight/scale>2400)scale*=2;
                        opts.inSampleSize=scale;
                        try(InputStream in=getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(in,null,opts);}
                        }
                    }
                }catch(Exception ignored){}
                Bitmap loaded=bitmap;
                android.graphics.drawable.Drawable loadedAnimation=animated;
                runOnUiThread(()->{
                    if(id!=generation[0]||!dialog.isShowing()||isFinishing()||isDestroyed()){
                        // Closing a gallery during a slow SAF decode must free its
                        // bitmap instead of retaining several megabytes until GC.
                        if(loaded!=null&&!loaded.isRecycled())loaded.recycle();
                        if(Build.VERSION.SDK_INT>=28&&loadedAnimation instanceof android.graphics.drawable.AnimatedImageDrawable)
                            ((android.graphics.drawable.AnimatedImageDrawable)loadedAnimation).stop();
                        return;
                    }
                    picture.animate().cancel();picture.setAlpha(0f);picture.setTranslationX(px(12));
                    if(loadedAnimation!=null){
                        picture.setImageDrawable(loadedAnimation);
                        if(Build.VERSION.SDK_INT>=28&&loadedAnimation instanceof android.graphics.drawable.AnimatedImageDrawable)
                            ((android.graphics.drawable.AnimatedImageDrawable)loadedAnimation).start();
                    }else if(loaded!=null)picture.setImageBitmap(loaded);
                    picture.animate().alpha(1f).translationX(0).setDuration(motionDuration(260)).setInterpolator(new DecelerateInterpolator()).start();
                });
            });
            worker.execute(()->{
                ArrayList<MessageDb.Attachment> older=new ArrayList<>(),newer=new ArrayList<>();
                MessageDb.Attachment cursor=item;
                for(int i=0;i<5;i++){cursor=db.adjacentMedia("image",cursor.messageId,cursor.id,false);if(cursor==null)break;older.add(0,cursor);}
                cursor=item;
                for(int i=0;i<5;i++){cursor=db.adjacentMedia("image",cursor.messageId,cursor.id,true);if(cursor==null)break;newer.add(cursor);}
                ArrayList<MessageDb.Attachment> window=new ArrayList<>(older);window.add(item);window.addAll(newer);
                runOnUiThread(()->{
                    if(id!=generation[0]||!dialog.isShowing())return;
                    thumbsRow.removeAllViews();
                    for(MessageDb.Attachment photo:window){
                        ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        LinearLayout.LayoutParams p=p(65,65);p.setMargins(px(3),0,px(3),0);thumbsRow.addView(thumb,p);
                        thumb.setBackground(shape(photo.id==item.id?ACCENT:PANEL,7));
                        thumb.setPadding(px(photo.id==item.id?3:1),px(photo.id==item.id?3:1),px(photo.id==item.id?3:1),px(photo.id==item.id?3:1));
                        loadThumbnail(photo.path,"image",thumb);
                        thumb.setOnClickListener(v->show[0].accept(photo));
                    }
                    thumbsRow.setAlpha(0.45f);thumbsRow.animate().alpha(1f).setDuration(motionDuration(240)).start();
                    strip.post(()->strip.smoothScrollTo(Math.max(0,(older.size()-2)*px(71)),0));
                });
            });
        };
        final android.view.ScaleGestureDetector photoZoom=new android.view.ScaleGestureDetector(this,new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(android.view.ScaleGestureDetector d){
                float before=picture.getScaleX();
                float scale=Math.max(1f,Math.min(4f,before*d.getScaleFactor()));
                // Do NOT update the pivot to every moving fingertip: Android then
                // applies a different transform each event, creating visible jumps.
                float centerX=picture.getWidth()/2f,centerY=picture.getHeight()/2f;
                picture.setPivotX(centerX);picture.setPivotY(centerY);
                float tx=PhotoZoomMath.translationAfterScale(d.getFocusX(),centerX,picture.getTranslationX(),before,scale);
                float ty=PhotoZoomMath.translationAfterScale(d.getFocusY(),centerY,picture.getTranslationY(),before,scale);
                picture.setScaleX(scale);picture.setScaleY(scale);
                picture.setTranslationX(PhotoZoomMath.clamp(tx,(scale-1)*picture.getWidth()/2f));
                picture.setTranslationY(PhotoZoomMath.clamp(ty,(scale-1)*picture.getHeight()/2f));
                return true;
            }
        });
        picture.setOnTouchListener(new View.OnTouchListener(){
            float x,y,lastX,lastY;boolean usedPinch;
            public boolean onTouch(View v,MotionEvent e){
                photoZoom.onTouchEvent(e);
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){
                    picture.animate().cancel();x=lastX=e.getX();y=lastY=e.getY();usedPinch=false;return true;
                }
                if(action==MotionEvent.ACTION_POINTER_DOWN){usedPinch=true;return true;}
                if(e.getPointerCount()>1)return true;
                if(action==MotionEvent.ACTION_MOVE){
                    if(picture.getScaleX()>1.01f&&!usedPinch){
                        float maxX=(picture.getScaleX()-1)*picture.getWidth()/2f;
                        float maxY=(picture.getScaleY()-1)*picture.getHeight()/2f;
                        picture.setTranslationX(Math.max(-maxX,Math.min(maxX,picture.getTranslationX()+e.getX()-lastX)));
                        picture.setTranslationY(Math.max(-maxY,Math.min(maxY,picture.getTranslationY()+e.getY()-lastY)));
                    }
                    lastX=e.getX();lastY=e.getY();return true;
                }
                if(action==MotionEvent.ACTION_UP){
                    float dx=e.getX()-x;
                    if(!usedPinch&&picture.getScaleX()<=1.01f&&Math.abs(dx)>px(55)&&Math.abs(dx)>Math.abs(e.getY()-y)){
                        picture.animate().translationX(dx<0?-px(85):px(85)).alpha(0f).setDuration(150).withEndAction(()->{
                            MessageDb.Attachment old=current[0];
                            worker.execute(()->{
                                MessageDb.Attachment next=db.adjacentMedia("image",old.messageId,old.id,dx<0);
                                runOnUiThread(()->{if(next!=null&&dialog.isShowing())show[0].accept(next);else{picture.setAlpha(1);picture.setTranslationX(0);}});
                            });
                        }).start();
                    }return true;
                }return true;
            }
        });
        dialog.setContentView(frame);dialog.show();show[0].accept(initial);
    }
    private void groupedParticipantColors(){
        worker.execute(()->{
            List<String> authors=db.participants();
            runOnUiThread(()->{
                if(authors.isEmpty()){toast("Сначала импортируй сообщения");return;}
                String[] names=authors.toArray(new String[0]);
                boolean[] checked=new boolean[names.length];
                new FontDialog.Builder(this).setTitle("Объединить цвета участников")
                    .setMultiChoiceItems(names,checked,(d,index,value)->checked[index]=value)
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Выбрать цвет",(d,w)->{
                        ArrayList<String> chosen=new ArrayList<>();
                        for(int i=0;i<checked.length;i++)if(checked[i])chosen.add(names[i]);
                        if(chosen.isEmpty()){toast("Не выбраны участники");return;}
                        String[] colors={"Голубой","Сиреневый","Бирюзовый","Коричневый","Синий","Розовый","Оливковый","Стальной"};
                        new FontDialog.Builder(this).setTitle("Одинаковый цвет для "+chosen.size()+" имён")
                            .setItems(colors,(dlg,pos)->{
                                android.content.SharedPreferences.Editor edit=getPreferences(MODE_PRIVATE).edit();
                                for(String name:chosen)edit.putInt(colorKey(name),participantColors[pos]);
                                edit.apply();showPage();
                            }).show();
                    }).show();
            });
        });
    }
    private void participantSettings(){
       worker.execute(()->{List<String> authors=db.participants();runOnUiThread(()->{
          if(authors.isEmpty()){toast("Сначала импортируй сообщения");return;}
          new FontDialog.Builder(this).setTitle("Цвета участников").setItems(authors.toArray(new String[0]),(dlg,which)->pickAuthorColor(authors.get(which))).show();
       });});
    }
    private void pickAuthorColor(String name){
       String[] names={"Голубой","Сиреневый","Бирюзовый","Коричневый","Синий","Розовый","Оливковый","Стальной","Сброс"};
       new FontDialog.Builder(this).setTitle(name).setItems(names,(d,which)->{
          android.content.SharedPreferences.Editor edit=getPreferences(MODE_PRIVATE).edit();
          if(which==participantColors.length)edit.remove(colorKey(name));else edit.putInt(colorKey(name),participantColors[which]);edit.apply();showPage();
       }).show();
    }
    /** Always register the media AFTER startInlineRound, because it resets the previous player. */
    private void startRoundPlayback(MessageDb.Attachment attachment,FrameLayout target,ImageView poster,TextView overlay){
        startInlineRound(attachment.path,target,poster,overlay);
        playingAttachment=attachment;
        inlineHolder=target;
        if(playerStrip!=null){
            playerStrip.setVisibility(View.VISIBLE);
            playerText.setText("◉ "+(attachment.author==null||attachment.author.trim().isEmpty()?"Видеосообщение":attachment.author));
            playerPause.setText("⏸");
        }
    }
    private void toggleActivePlayback(){
        if(player==null)return;
        try{if(player.isPlaying()){player.pause();playerPause.setText("▶");}else{player.start();playerPause.setText("⏸");}}catch(Exception ignored){}
    }
    private void playAdjacent(boolean forward){
        if(playingAttachment==null)return;
        final MessageDb.Attachment current=playingAttachment;
        worker.execute(()->{
            MessageDb.Attachment found=db.adjacentMedia(current.kind,current.messageId,current.id,forward);
            runOnUiThread(()->{if(found==null){toast("Других сообщений нет");return;}
                if(found.kind.equals("round"))showFloatingRound(found);
                else if(found.kind.equals("voice")||found.kind.equals("audio")){
                    startAudio(found.path,playerPause,playerText);
                    playingAttachment=found;playerStrip.setVisibility(View.VISIBLE);
                }
            });
        });
    }
    private void settingsMenu(){new FontDialog.Builder(this).setTitle("Настройки переписки")
        .setItems(new String[]{"Скругление пузырьков","Моё имя (сообщения справа)",
            "Цвета участников","Объединить цвета нескольких имён"},(d,i)->{
            if(i==0)bubbleStyleDialog();else if(i==1)identityDialog();
            else if(i==2)participantSettings();else if(i==3)groupedParticipantColors();
        }).setPositiveButton("Закрыть",null).show();}
    private String displayDocumentName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"Резервная копия":uri.getLastPathSegment();
    }
    private void backupPicker(){
        new FontDialog.Builder(this).setTitle("Создать резервную копию")
            .setMessage("Для переноса на ДРУГОЙ ТЕЛЕФОН выбирай ПОЛНУЮ копию. Она включает медиа чатов, аватары, обои, избранное, цитаты, этапы истории и файлы заметок. Компактная копия сохраняет базы и настройки, но НЕ переносит медиа и файлы заметок. Проверь копию перед удалением оригиналов.")
            .setItems(new String[]{"ПЕРЕЕЗД · Полная копия со всеми медиа и заметками","КОМПАКТНАЯ · Без медиа и вложений заметок"},(dlg,choice)->{
                fullBackupMode=choice==0;askBackupPassword();
            }).show();
    }
    private void askBackupPassword(){
        LinearLayout layout=column();layout.setPadding(px(23),px(5),px(23),px(3));
        EditText pass=new EditText(this);pass.setHint("Пароль, не меньше 8 символов");pass.setSingleLine(true);
        pass.setInputType(129);layout.addView(pass,p(-1,53));
        TextView note=text("Можно оставить пустым, чтобы создать обычный ZIP без шифрования. Забытый пароль восстановить невозможно.",12,SUB);layout.addView(note);
        new FontDialog.Builder(this).setTitle("Защитить копию паролем").setView(layout)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Без пароля",(d,w)->{backupPassword=null;pickBackupFile();})
            .setPositiveButton("С паролем",(d,w)->{
                String value=pass.getText().toString();
                if(value.length()<8){toast("Пароль должен содержать минимум 8 символов");return;}
                backupPassword=value.toCharArray();pickBackupFile();
            }).show();
    }
    private void pickBackupFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"TelegramArchive-"+(fullBackupMode?"FULL":"COMPACT")+(backupPassword==null?".zip":".tgar"));
        startActivityForResult(i,41);
    }
    private void dryRunPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,44);
    }
    private void dryRunSelected(Uri uri){
        try{
            if(SecureArchive.isEncrypted(this,uri)){
                EditText input=new EditText(this);input.setInputType(129);input.setHint("Пароль резервной копии");
                new FontDialog.Builder(this).setTitle("Пробное восстановление").setView(input)
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Проверить",(d,w)->runDryRestore(uri,input.getText().toString().toCharArray())).show();
            }else runDryRestore(uri,null);
        }catch(Exception e){status.setText("Ошибка чтения: "+e.getMessage());}
    }
    private void runDryRestore(Uri uri,char[] pass){
        if(importing||restoringBackup)return;
        restoringBackup=true;status.setText("Проверяю временные базы, чаты не изменяются…");
        worker.execute(()->{
            try{
                BackupVerifier.DryRun report=BackupVerifier.simulate(this,uri,pass,msg->runOnUiThread(()->status.setText(msg)));
                runOnUiThread(()->{
                    restoringBackup=false;
                    new FontDialog.Builder(this).setTitle("Пробное восстановление завершено")
                        .setMessage(report.summary()+"\\n\\nПроверены базы SQLite и чтение архива. Исходные чаты не затронуты. Открытие медиа на другом устройстве требует отдельного теста.")
                        .setPositiveButton("Отлично",null).show();
                });
            }catch(Exception e){runOnUiThread(()->{restoringBackup=false;status.setText("Ошибка пробного восстановления: "+e.getMessage());});}
            finally{if(pass!=null)java.util.Arrays.fill(pass,(char)0);}
        });
    }
    private void verifyBackupPicker(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(intent,43);
    }
    private void verifyBackup(Uri uri){
        try{
            if(SecureArchive.isEncrypted(this,uri)){
                EditText entry=new EditText(this);entry.setInputType(129);entry.setSingleLine(true);
                entry.setHint("Пароль для проверки архива");
                new FontDialog.Builder(this).setTitle("Проверка защищённой копии")
                    .setView(entry).setNegativeButton("Отмена",null)
                    .setPositiveButton("Проверить",(d,w)->runBackupVerify(uri,entry.getText().toString().toCharArray())).show();
            }else runBackupVerify(uri,null);
        }catch(Exception problem){status.setText("Невозможно открыть копию: "+problem.getMessage());}
    }
    private void runBackupVerify(Uri uri,char[] password){
        if(importing||restoringBackup)return;restoringBackup=true;
        status.setText("Проверяю резервную копию, чаты не изменяются…");
        worker.execute(()->{
            try{
                BackupVerifier.Result result=BackupVerifier.verify(this,uri,password,msg->runOnUiThread(()->status.setText(msg)));
                runOnUiThread(()->{restoringBackup=false;
                    new FontDialog.Builder(this).setTitle("Проверка завершена")
                        .setMessage("ZIP прочитан и CRC проверены. Файлов: "+result.files+". Прочитано: "+(result.bytes/1048576)+" МБ."+
                            (result.full?("\nМедиа: "+(result.mediaHashVerified?"SHA-256 проверен":"старый формат — только CRC и размер")+
                                "\nАватары и обои: "+(result.customHashVerified?"SHA-256 проверен":"старый формат — только CRC")):"")+ 
                            "\n\nИсходные чаты не менялись. Перенос на другом устройстве не подтверждён.")
                        .setPositiveButton("Готово",null).show();
                });
            }catch(Exception error){runOnUiThread(()->{restoringBackup=false;status.setText("Проверка не пройдена: "+error.getMessage());});}
            finally{if(password!=null)java.util.Arrays.fill(password,(char)0);}
        });
    }
    private void showLastRestoreReceipt(){
        String report=getPreferences(MODE_PRIVATE).getString("last_restore_receipt","");
        if(report.isEmpty())report="На этом устройстве ещё не было подтверждённого восстановления. "+
            "Сначала сделайте полную копию, проверьте её и затем восстановите в тестовом профиле.";
        new FontDialog.Builder(this).setTitle("Квитанция переноса · офлайн")
            .setMessage(report+"\n\nДо проверки файлов на новом устройстве старый архив не удаляйте.")
            .setPositiveButton("Готово",null).show();
    }
    private void restorePicker(){
        new FontDialog.Builder(this).setTitle("Восстановить переписки")
            .setMessage("Сначала приложение проверит целостность архива. Существующие чаты останутся на месте. Для защищённой копии потребуется пароль.")
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать копию",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
                i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(i,42);
            }).show();
    }
    private void askRestorePassword(Uri uri){
        try{
            if(!SecureArchive.isEncrypted(this,uri)){backupAction(uri,true);return;}
            EditText entry=new EditText(this);entry.setHint("Пароль резервной копии");
            entry.setInputType(129);entry.setSingleLine(true);
            new FontDialog.Builder(this).setTitle("Зашифрованная копия")
                .setMessage("Введите пароль, чтобы проверить и восстановить данные")
                .setView(entry).setNegativeButton("Отмена",null)
                .setPositiveButton("Продолжить",(d,w)->{
                    backupPassword=entry.getText().toString().toCharArray();
                    backupAction(uri,true);
                }).show();
        }catch(Exception error){status.setText("Не удалось прочитать архив: "+error.getMessage());}
    }
    private void backupAction(Uri uri,boolean restore){
        if(restoringBackup||importing)return;
        final char[] password=backupPassword;
        backupPassword=null;restoringBackup=true;
        status.setText(restore?"Проверяю архив перед восстановлением…":"Создаю архив в выбранном расположении…");
        worker.execute(()->{
            try{
                SharedPreferences prefs=getPreferences(MODE_PRIVATE);
                if(restore){
                    BackupVerifier.Result verified=BackupVerifier.verify(this,uri,password,msg->runOnUiThread(()->status.setText(msg)));
                    int restored;
                    try(InputStream source=SecureArchive.openInput(this,uri,password)){
                        // The preceding read-only verifier has already identified the archive type.
                        // Do not guess from the first ZIP entry: entry order is not a format guarantee.
                        if(verified.full)restored=FullBackup.restore(this,prefs,source,msg->runOnUiThread(()->status.setText(msg)));
                        else restored=ArchiveBackup.restore(this,prefs,source,msg->runOnUiThread(()->status.setText(msg)));
                    }
                    final int count=restored;
                    runOnUiThread(()->{restoringBackup=false;loadArchiveRegistry();home=true;screen="chat";showPage();toast("Проверено и восстановлено чатов: "+count);});
                }else{
                    try(OutputStream raw=getContentResolver().openOutputStream(uri,"w")){
                        if(raw==null)throw new IOException("Нет доступа к месту сохранения");
                        OutputStream dest=password==null?raw:SecureArchive.encryptedOutput(raw,password);
                        if(fullBackupMode)FullBackup.export(this,prefs,dest,msg->runOnUiThread(()->status.setText(msg)));
                        else ArchiveBackup.write(this,prefs,dest,msg->runOnUiThread(()->status.setText(msg)));
                    }
                    runOnUiThread(()->{restoringBackup=false;status.setText("Сохранено: «"+selectedBackupName+"». Папка выбрана в системном окне Android. Проверь архив перед удалением исходников.");toast("Экспорт завершён");});
                }
            }catch(Exception error){
                runOnUiThread(()->{restoringBackup=false;status.setText("Ошибка: "+error.getMessage());});
            }finally{if(password!=null)java.util.Arrays.fill(password,(char)0);}
        });
    }
    private final class VerticalTimeline extends View {
       private final Paint brush=new Paint(3);private int position,count;
       VerticalTimeline(){super(MainActivity.this);}
       void setPosition(int p,int c){position=p;count=c;invalidate();}
       @Override protected void onDraw(Canvas c){
         super.onDraw(c);float x=getWidth()-px(10),top=px(13),bottom=getHeight()-px(13);
         brush.setColor(0xff42657f);brush.setStrokeWidth(px(4));c.drawLine(x,top,x,bottom,brush);
         float ratio=count<=1?0:(float)position/(count-1);brush.setColor(ACCENT);c.drawCircle(x,top+(bottom-top)*ratio,px(5),brush);
       }
       @Override public boolean onTouchEvent(MotionEvent e){
         if(!screen.equals("chat"))return false;
         if(e.getActionMasked()==MotionEvent.ACTION_UP){
           float frac=Math.max(0,Math.min(1,(e.getY()-px(13))/Math.max(1,getHeight()-px(26))));
           chatOffset=Math.round(frac*Math.max(0,count-1));showPage();performClick();return true;
         }
         return true;
       }
       @Override public boolean performClick(){super.performClick();return true;}
    }
    private void openRoundPlayer(MessageDb.Attachment a){
       resolveMedia(a.path,this::openRoundUri);
    }
    /** Safe fallback if the inline TextureView cannot display frames on this device. */
    private void openRoundUri(Uri uri){
       if(uri==null)return;
       Intent intent=new Intent(this,RoundPlayerActivity.class);
       intent.setData(uri);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
       try{startActivity(intent);}catch(Exception e){toast("Не удалось открыть видео-сообщение");}
    }
    private void closeFloatingRound(){
       if(floatingPanel!=null){FrameLayout p=floatingPanel;floatingPanel=null;stopPlayback();root.removeView(p);}
    }
    private void showFloatingRound(MessageDb.Attachment a){
        final int resume=floatingStart;floatingStart=0;
        closeFloatingRound();
        final FrameLayout panel=new FrameLayout(this);floatingPanel=panel;
        int widthDp=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        final int diameter=Math.max(174,Math.min(214,widthDp-118));
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(px(diameter),px(diameter),Gravity.END|Gravity.BOTTOM);
        pos.rightMargin=px(12);pos.bottomMargin=px(96);root.addView(panel,pos);
        FrameLayout round=new FrameLayout(this);
        round.setBackground(shape(0xff28475b,100));
        round.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){outline.setOval(0,0,view.getWidth(),view.getHeight());}
        });
        round.setClipToOutline(true);
        panel.addView(round,new FrameLayout.LayoutParams(-1,-1));
        ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        round.addView(preview,new FrameLayout.LayoutParams(-1,-1));loadThumbnail(a.path,"round",preview);
        TextView play=text("▶",38,FG);play.setGravity(Gravity.CENTER);
        round.addView(play,new FrameLayout.LayoutParams(-1,-1));
        // One close button: ✕ on the media-player strip at the top right.
        round.setContentDescription("Плавающее видеосообщение. Проведи пальцем для перемещения; нажми для паузы.");
        round.setOnClickListener(v->startInlineRound(a.path,round,preview,play));
        round.setOnTouchListener(new View.OnTouchListener(){
            float downX,downY,originX,originY;long downAt;boolean dragged;
            @Override public boolean onTouch(View v,MotionEvent event){
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        downX=event.getRawX();downY=event.getRawY();
                        originX=panel.getTranslationX();originY=panel.getTranslationY();
                        dragged=false;downAt=android.os.SystemClock.uptimeMillis();return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                        if(Math.hypot(dx,dy)>px(8))dragged=true;
                        if(dragged){
                            float minX=-panel.getLeft()+px(6),maxX=root.getWidth()-panel.getLeft()-panel.getWidth()-px(6);
                            float minY=-panel.getTop()+px(6),maxY=root.getHeight()-panel.getTop()-panel.getHeight()-px(10);
                            panel.setTranslationX(Math.min(Math.max(minX,maxX),Math.max(minX,originX+dx)));
                            panel.setTranslationY(Math.min(Math.max(minY,maxY),Math.max(minY,originY+dy)));
                        }return true;
                    case MotionEvent.ACTION_UP:
                        if(!dragged){
                            if(android.os.SystemClock.uptimeMillis()-downAt>=550)mediaActions(a);
                            else v.performClick();
                        }return true;
                    case MotionEvent.ACTION_CANCEL:return true;
                }return false;
            }
        });
        // Playback may resolve asynchronously; the media generation check drops stale callbacks.
        startRoundPlayback(a,round,preview,play);
        handler.postDelayed(()->{try{
            if(floatingPanel==panel&&player!=null&&resume>0)player.seekTo(resume);
        }catch(Exception ignored){}},650);
    }

    @Override protected void onPause(){
        handler.removeCallbacks(deferredReadingSave);
        handler.removeCallbacks(deferredFloatingCheck);
        saveReadingProgress();
        stopPlayback();
        super.onPause();
    }
    @Override protected void onDestroy(){
        batchCancelled.set(true);
        if(pendingCapsulePassword!=null){java.util.Arrays.fill(pendingCapsulePassword,(char)0);pendingCapsulePassword=null;}
        if(scheduledPrefetch!=null)handler.removeCallbacks(scheduledPrefetch);
        handler.removeCallbacks(deferredReadingSave);
        handler.removeCallbacks(deferredFloatingCheck);
        stopPlayback();worker.execute(()->db.close());worker.shutdown();mediaWorker.shutdown();super.onDestroy();}
}
