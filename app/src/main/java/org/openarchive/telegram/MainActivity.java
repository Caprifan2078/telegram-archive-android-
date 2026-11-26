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
import android.view.WindowInsetsController;
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
    private boolean historyEventsExpanded=false;
    private boolean savedFavoritesOnly=false;private String noteDraft="";private EditText noteInput;
    private String chronologySection="overview";
    private String cinemaReturnScreen="chat";
    // Read-only side-by-side workspaces. The first archive is always the active chat.
    private String dualSecondKey="",dualSecondName="";
    private boolean comparisonFromHome=false;
    private int dualYear=LocalDate.now().getYear(),dualMonth=0;
    // Read-only combined timeline state. Original chats retain their own databases and notes.
    private String multiTimelineGroupId="";
    private int multiTimelinePerSource=GroupTimeline.PAGE_SIZE,multiTimelinePage=0;

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
    private String pendingCapsuleKey;
    private AlertDialog archivePasswordDialog;
    private DocumentFolder pendingMediaSaveRoot;
    private String pendingMediaSavePath;
    private MediaSavePolicy.Spec pendingMediaSaveSpec;
    private String pendingMessageText;
    private static final int SAVE_CONVERSATION_PDF_REQUEST=118;
    private String pendingPdfKey,pendingPdfName;
    private final LinkedHashMap<Long,MessageDb.Attachment> mediaSelection=new LinkedHashMap<>();
    private boolean multiSelectMedia=false;
    private String selectedMediaArchive="";
    private final AtomicBoolean batchCancelled=new AtomicBoolean(false);
    private boolean batchRunning=false;
    private List<MediaBatchExporter.Entry> pendingBatchFiles;
    private DocumentFolder pendingBatchSource;
    private boolean pendingBatchDownloads=false;


    private static final String[] MEDIA_KEYS={"visual","image","video","favorite","round","voice","audio","file","gif","sticker","all"};
    private static final String[] MEDIA_LABELS={"Фото+видео","Фото","Видео","Избранное","Кружки","Голосовые","Музыка","Файлы","GIF","Стикеры","Все файлы"};
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
    private LinearLayout importProgressPanel;private TextView importProgressText;
    private MediaZoomRecyclerView mediaRecycler;
    private MediaFastScroller mediaFastScroller;
    private TextView mediaFastLabel;
    private MediaGalleryAdapter mediaAdapter;
    private androidx.recyclerview.widget.GridLayoutManager mediaLayout;
    private int mediaColumns=3,mediaLoaded=0,mediaTotal=0,mediaWindowStart=0;
    private boolean mediaAppending=false,mediaPrepending=false;
    private TextView mediaZoomLabel;
    private android.widget.SeekBar mediaZoomSeek;
    private ScrollView scroll;private TextView title,subtitle,status,pageLabel,headerLogo;private Button pdfButton;private Button navBack,menuButton;private String lastRenderedDay="";
    private EditText searchInput,authorInput;private CheckBox mediaOnly;
    private String restoredQueryDraft=null,restoredAuthorDraft=null;private Boolean restoredMediaDraft=null;
    private Button backPage,nextPage,quickButton,deepButton,folderButton,backButton,headerActions;
    private String screen="chat",mediaKind="visual",q="",byAuthor="",mine="",returnScreen="";
    private boolean deep=false,onlyMedia=false,importing=false,searchReturnsToAnalytics=false;
    private int chatOffset=0,searchOffset=0,mediaOffset=0,favOffset=0,total=0,loadToken=0;
    private int searchReturnScroll=-1,pendingSearchScroll=-1;
    private int analyticsReturnScroll=-1,pendingAnalyticsScroll=-1;
    private long highlightId=-1;private long lastVisibleMessage=-1;private int lastVisibleTop=0;private final Map<Long,View> renderedMessageViews=new HashMap<>();private long pendingRestoreId=-1;private int pendingRestoreTop=0;private boolean restoringBackup=false;private boolean fullBackupMode=true;private char[] backupPassword=null;private String selectedBackupName="";
    private String info="";
    private boolean analyticsCommonWords=false;
    private String analyticsSection="overview";
    private boolean analyticsYearExpanded=false;
    private String analyticsUnit="month",analyticsSelectedAuthor="";
    private int analyticsCalendarYear=0;
    private String analyticsCompareFirst="",analyticsCompareSecond="";
    private ChatAnalytics.Snapshot lastAnalytics;
    private MediaPlayer player;private String playingPath="";private TextureView activeTexture;
    private volatile int playbackGeneration=0;
    private Runnable progressUpdater;private MessageDb.Attachment playingAttachment;private LinearLayout playerStrip;private TextView playerText;private Button playerPause;private FrameLayout inlineHolder;private int floatingStart=0;
    private RoundControls activeRound;
    // Remember the original message tile when a playing round video floats away.
    // It must be restored when the user scrolls back, not left as a grey placeholder.
    private FrameLayout autoFloatSourceHolder;
    private ImageView autoFloatSourcePoster;
    private TextView autoFloatSourceOverlay;
    private TextView activeAudioButton;
    private SeekBar activeVoiceSeek;private boolean voiceSeekDragging;
    private static final class RoundControls {
        long sourceAttachmentId; // distinguish several rounds referencing one MP4 path
        SeekBar seek;
        TextView timer;
        ImageView preview;
        TextView overlay;
        boolean dragging=false,wasPlaying=false;
    }

    @Override public void onCreate(Bundle state){
        ViewerUi.applyActivityTheme(this);super.onCreate(state);
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
            pendingPdfKey=state.getString("ta_pending_pdf_key");pendingPdfName=state.getString("ta_pending_pdf_name");
            home=state.getBoolean("ta_home",true);screen=state.getString("ta_screen","chat");
            savedFavoritesOnly=state.getBoolean("ta_saved_favorites",false);noteDraft=state.getString("ta_note_draft","");
            if("help".equals(screen)){
                helpReturnHome=state.getBoolean("ta_help_return_home",true);
                helpReturnScreen=state.getString("ta_help_return_screen","chat");
                if(helpReturnScreen==null||"help".equals(helpReturnScreen))helpReturnScreen="chat";
                int category=state.getInt("ta_help_category",-1);
                if(category>=0&&category<HelpCatalog.GROUPS.length){
                    helpCategory=category;
                    int shelf=state.getInt("ta_help_shelf",-1);
                    if(shelf>=0&&shelf<HelpCatalog.GROUPS[category].shelves.length)helpShelf=shelf;
                    int chapter=state.getInt("ta_help_chapter",-1);
                    if(chapter>0&&chapter<=HelpContent.all().size()){
                        int[] path=HelpCatalog.findChapter(chapter);
                        if(path!=null&&path[0]==category&&(helpShelf<0||path[1]==helpShelf))helpChapter=chapter;
                    }
                }
            }
            q=state.getString("ta_search_query","");byAuthor=state.getString("ta_search_author","");
            deep=state.getBoolean("ta_search_deep",false);onlyMedia=state.getBoolean("ta_search_media",false);
            chatOffset=Math.max(0,state.getInt("ta_chat_offset",0));autoLoaded=Math.max(PAGE,state.getInt("ta_chat_loaded",PAGE));
            searchOffset=Math.max(0,state.getInt("ta_search_offset",0));searchLoaded=Math.max(PAGE,state.getInt("ta_search_loaded",PAGE));
            mediaOffset=Math.max(0,state.getInt("ta_media_offset",0));favOffset=Math.max(0,state.getInt("ta_fav_offset",0));
            mediaKind=state.getString("ta_media_kind","all");
            returnScreen=state.getString("ta_return_screen","");searchReturnsToAnalytics=state.getBoolean("ta_search_parent_analytics",false);
            searchReturnScroll=state.getInt("ta_search_return_scroll",-1);analyticsReturnScroll=state.getInt("ta_analytics_return_scroll",-1);
            pendingSearchScroll=state.getInt("ta_search_scroll",-1);pendingAnalyticsScroll=state.getInt("ta_analytics_scroll",-1);
            highlightId=state.getLong("ta_highlight_id",-1);
            if(!home&&"chat".equals(screen)){
                pendingRestoreId=state.getLong("ta_visible_id",-1);pendingRestoreTop=state.getInt("ta_visible_top",0);
            }else if(!home){
                // The user may return to chat after Android recreates another tab.
                pendingRestoreId=state.getLong("ta_pending_chat_id",-1);
                pendingRestoreTop=state.getInt("ta_pending_chat_top",0);
            }
            analyticsSection=state.getString("ta_analytics_section","overview");
            analyticsYearExpanded=state.getBoolean("ta_analytics_year_expanded",false);
            if(!java.util.Arrays.asList("overview","trends","rhythm","people","content").contains(analyticsSection))analyticsSection="overview";
            analyticsUnit=state.getString("ta_analytics_unit","month");
            analyticsSelectedAuthor=state.getString("ta_analytics_author","");
            analyticsCalendarYear=state.getInt("ta_analytics_year",0);
            analyticsCompareFirst=state.getString("ta_analytics_compare_first","");
            analyticsCompareSecond=state.getString("ta_analytics_compare_second","");
            comparisonFromHome=state.getBoolean("ta_compare_from_home",false);
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
        createUi();applySystemBarAppearance();
        // createUi builds bars once; restore unsent edits for the final showPage build.
        if(state!=null){
            restoredQueryDraft=state.getString("ta_search_draft");
            restoredAuthorDraft=state.getString("ta_search_author_draft");
            if(state.containsKey("ta_search_media_draft"))restoredMediaDraft=state.getBoolean("ta_search_media_draft");
        }
        showPage();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putString("ta_pending_pdf_key",pendingPdfKey);state.putString("ta_pending_pdf_name",pendingPdfName);
        if("help".equals(screen)){
            state.putBoolean("ta_help_return_home",helpReturnHome);state.putString("ta_help_return_screen",helpReturnScreen);
            state.putInt("ta_help_category",helpCategory);state.putInt("ta_help_shelf",helpShelf);state.putInt("ta_help_chapter",helpChapter);
        }
        if(!home&&"chat".equals(screen))saveReadingProgress();
        state.putBoolean("ta_home",home);state.putString("ta_screen",screen);
        state.putBoolean("ta_saved_favorites",savedFavoritesOnly);
        state.putString("ta_note_draft",noteInput!=null?noteInput.getText().toString():noteDraft);
        state.putString("ta_search_query",q);state.putString("ta_search_author",byAuthor);
        state.putBoolean("ta_search_deep",deep);state.putBoolean("ta_search_media",onlyMedia);
        if("search".equals(screen)){
            if(searchInput!=null)state.putString("ta_search_draft",searchInput.getText().toString());
            if(deep&&authorInput!=null)state.putString("ta_search_author_draft",authorInput.getText().toString());
            if(deep&&mediaOnly!=null)state.putBoolean("ta_search_media_draft",mediaOnly.isChecked());
        }
        state.putInt("ta_chat_offset",chatOffset);state.putInt("ta_chat_loaded",autoLoaded);
        state.putInt("ta_search_offset",searchOffset);state.putInt("ta_search_loaded",searchLoaded);
        state.putInt("ta_media_offset",mediaOffset);state.putInt("ta_fav_offset",favOffset);state.putString("ta_media_kind",mediaKind);
        state.putString("ta_return_screen",returnScreen);state.putBoolean("ta_search_parent_analytics",searchReturnsToAnalytics);
        state.putInt("ta_search_return_scroll",searchReturnScroll);state.putInt("ta_analytics_return_scroll",analyticsReturnScroll);
        if(!home&&scroll!=null){
            if("search".equals(screen))state.putInt("ta_search_scroll",scroll.getScrollY());
            if("analytics".equals(screen))state.putInt("ta_analytics_scroll",scroll.getScrollY());
        }
        if("help".equals(screen)&&"search".equals(helpReturnScreen)&&pendingSearchScroll>=0)
            state.putInt("ta_search_scroll",pendingSearchScroll);
        state.putLong("ta_highlight_id",highlightId);state.putLong("ta_visible_id",lastVisibleMessage);state.putInt("ta_visible_top",lastVisibleTop);
        state.putLong("ta_pending_chat_id",pendingRestoreId);state.putInt("ta_pending_chat_top",pendingRestoreTop);
        state.putString("ta_analytics_section",analyticsSection);
        state.putBoolean("ta_analytics_year_expanded",analyticsYearExpanded);
        state.putString("ta_analytics_unit",analyticsUnit);
        state.putString("ta_analytics_author",analyticsSelectedAuthor);
        state.putInt("ta_analytics_year",analyticsCalendarYear);
        state.putString("ta_analytics_compare_first",analyticsCompareFirst);
        state.putString("ta_analytics_compare_second",analyticsCompareSecond);
        state.putBoolean("ta_compare_from_home",comparisonFromHome);
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
        ViewerPalette palette=ViewerPalette.of(themeIndex);
        BG=palette.background;PANEL=palette.surface;INCOMING=palette.incoming;OUTGOING=palette.outgoing;
        FG=palette.text;SUB=palette.muted;ACCENT=palette.accent;CHIP=palette.chip;HIGHLIGHT=palette.highlight;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        applySystemBarAppearance();
    }
    private void applySystemBarAppearance(){
        int flags=themeIndex==2?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null){
                int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(themeIndex==2?mask:0,mask);
            }
        }
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
        // Keep actions inside the scrollable content: stacked native dialog
        // buttons can be clipped on short screens with a large system font.
        ScrollView content=new ScrollView(this);
        LinearLayout controls=column();
        controls.setPadding(px(20),0,px(20),0);
        content.addView(controls);
        Button motion=new Button(this);
        motion.setAllCaps(false);
        motion.setText(reduceMotion?"С анимацией":"Без анимации");
        controls.addView(motion,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new FontDialog.Builder(this)
            .setTitle("Скорость и память")
            .setView(content)
            .setNegativeButton("Закрыть",null)
            .create();
        motion.setOnClickListener(v->{
            getPreferences(MODE_PRIVATE).edit().putBoolean("appearance_less_motion",!reduceMotion).apply();
            reduceMotion=!reduceMotion;
            dialog.dismiss();
        });
        for(int i=0;i<options.length;i++){
            final int selection=i;
            android.widget.RadioButton option=new android.widget.RadioButton(this);
            option.setText(options[i]);
            option.setChecked(i==performanceMode);
            option.setPadding(0,px(10),0,px(10));
            controls.addView(option,new LinearLayout.LayoutParams(-1,-2));
            option.setOnClickListener(v->{
                getPreferences(MODE_PRIVATE).edit().putInt("appearance_performance",selection).apply();
                performanceMode=selection;
                thumbs.resize(PerformanceAdvisor.thumbnailCacheBytes(memoryClassMb(),performanceMode));
                dialog.dismiss();autoLoaded=PAGE;searchLoaded=PAGE;showPage();
            });
        }
        dialog.show();
    }
    private int px(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;}
    private GradientDrawable glass(int color,int radius){
        if(!glassStyle||effectivePerformanceMode()==2)return shape(color,radius);
        // Subtle tonal surface; preserve the original alpha and avoid blue haze on every card.
        return ViewerUi.surface(this,color,radius,themeIndex==2,true);
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
            outlined(PANEL,ViewerUi.CONTROL,false),shape(0xffffffff,ViewerUi.CONTROL)));
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
        if(home||db==null)return;
        final String key=activeKey;
        EditText pass=privatePasswordField("Пароль от 8 символов");
        showPasswordDialog(new FontDialog.Builder(this).setTitle("Зашифрованная капсула избранного")
            .setMessage("Сохраняются только тексты избранных сообщений и даты. Фото, видео, голосовые, заметки и настройки НЕ включаются. Это НЕ резервная копия приложения. Для полного сохранения используй полную копию Telegram Archive.")
            .setView(pass).setNegativeButton("Отмена",null)
            .setPositiveButton("Создать файл",(d,which)->{
                char[] password=takePassword(pass);
                if(password.length<8){java.util.Arrays.fill(password,(char)0);
                    toast("Минимум 8 символов — повтори создание");return;}
                if(pendingCapsulePassword!=null)java.util.Arrays.fill(pendingCapsulePassword,(char)0);
                pendingCapsulePassword=password;pendingCapsuleKey=key;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_TITLE,"TelegramArchive_Favorites_Capsule.tcap");
                try{startActivityForResult(intent,CAPSULE_EXPORT_REQUEST);}catch(RuntimeException failure){
                    java.util.Arrays.fill(password,(char)0);pendingCapsulePassword=null;pendingCapsuleKey=null;
                    toast("Не удалось открыть выбор файла");
                }
            }),pass);
    }
    private void exportCapsule(Uri file,char[] password){
        final MessageDb source=db;final String chatKey=activeKey;
        if(source==null||password==null){if(password!=null)java.util.Arrays.fill(password,(char)0);return;}
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
        final MessageDb source=db;final int token=loadToken;
        if(source==null){toast("Переписка не открыта");return;}
        status.setText("Ищу воспоминания…");
        worker.execute(()->{
            try{
                List<OnThisDay.Entry> found=source.onThisDay(month,day,60);
                runOnUiThread(()->{
                    if(!sameScreenRequest(source,chatKey,token))return;
                    status.setText("Найдено за этот день: "+found.size());
                    String[] labels=new String[found.size()];
                    for(int i=0;i<found.size();i++){
                        OnThisDay.Entry message=found.get(i);
                        labels[i]=message.date+" · "+message.author+"\n"+
                            (message.preview.isEmpty()?"(сообщение без текста)":message.preview);
                    }
                    AlertDialog.Builder dialog=new FontDialog.Builder(this)
                        .setTitle(String.format(Locale.ROOT,"В этот день · %02d.%02d",day,month))
                        .setNegativeButton("Закрыть",null);
                    if(found.isEmpty())dialog.setMessage("На выбранную дату сообщений не найдено.");
                    else dialog.setItems(labels,(d,which)->jump(found.get(which).id,"relationships"));
                    dialog.setNeutralButton("Другая дата",(d,which)->{
                        LocalDate today=LocalDate.now();
                        FontDialog.date(this,(picker,year,mo,dt)->showOnThisDay(mo+1,dt),
                            today.getYear(),month-1,day);
                    });
                    dialog.show();
                });
            }catch(Exception e){runOnUiThread(()->{
                if(sameScreenRequest(source,chatKey,token))status.setText("Не удалось открыть воспоминания: "+e.getMessage());
            });}
        });
    }

    /** No media bytes are modified or read. A SAF resolution failure isn't necessarily a lost file. */
    private void showMediaHealth(){
        final String chatKey=activeKey;
        final MessageDb source=db;final int token=loadToken;
        final DocumentFolder rootFolder=folder;
        if(source==null){toast("Переписка не открыта");return;}
        status.setText("Проверяю пути медиатеки (до 2000 файлов)…");
        worker.execute(()->{
            try{
                ChatMediaHealth.Report r=source.mediaHealth(rootFolder,ChatMediaHealth.DEFAULT_SCAN_LIMIT);
                runOnUiThread(()->{
                    if(!sameScreenRequest(source,chatKey,token))return;
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
                    new FontDialog.Builder(this).setTitle("Доступность оригиналов")
                        .setMessage(text.toString()).setPositiveButton("Готово",null).show();
                    status.setText("Проверка медиатеки завершена");
                });
            }catch(Exception e){runOnUiThread(()->{
                if(sameScreenRequest(source,chatKey,token))status.setText("Ошибка проверки медиатеки: "+e.getMessage());
            });}
        });
    }

    /** Local-only scan of calendar pauses. A quiet period does not imply data loss. */
    private void showConversationPauses(){
        final String chatKey=activeKey;
        final MessageDb source=db;final int token=loadToken;
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
                if(!sameScreenRequest(source,chatKey,token))return;
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
                AlertDialog.Builder pauses=new FontDialog.Builder(this).setTitle("Паузы от 7 дней · "+items.length)
                    .setPositiveButton("Закрыть",null);
                if(items.length==0)pauses.setMessage(intro+"\nПаузы от 7 дней не найдены.");
                else pauses.setNeutralButton("Как посчитано",(d,w)->new FontDialog.Builder(this)
                        .setTitle("Как посчитаны паузы").setMessage(intro).setPositiveButton("Закрыть",null).show())
                    .setItems(items,(dialog,index)->{
                        ConversationGapNavigator.Gap g=gaps.get(index);
                        new FontDialog.Builder(this).setTitle("Границы паузы")
                            .setMessage(g.from+" → "+g.to+"\nПерейти к сообщению до или после паузы?")
                            .setPositiveButton("После",(d,w)->jump(g.toId,"relationships"))
                            .setNeutralButton("До",(d,w)->jump(g.fromId,"relationships"))
                            .setNegativeButton("Отмена",null).show();
                    });
                pauses.show();
                status.setText("Навигатор пауз: просмотр завершён");
            });
        });
    }

    // Local, bounded filters and cross-chat search.
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
    private void showFavoritesByYear(){
        if(home){toast("Открой переписку");return;}
        discoveryYearPicker("Избранное за год",year->{
            final String key=activeKey,name=activeName;final MessageDb source=db;final int token=loadToken;
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
                            hits.add(new DiscoveryRules.Hit(key,name,c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
                    }
                }catch(Exception failure){
                    runOnUiThread(()->{if(token==loadToken&&key.equals(activeKey))toast("Не удалось прочитать избранное");});return;
                }
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()||home||db!=source||token!=loadToken||!key.equals(activeKey)||!"favorites".equals(screen))return;
                    if(hits.isEmpty()){toast("Избранных сообщений за этот год не найдено");return;}
                    String[] labels=new String[hits.size()];for(int i=0;i<labels.length;i++)labels[i]=hits.get(i).label();
                    new FontDialog.Builder(this).setTitle("Избранное · "+year+" · до 80 сообщений")
                        .setItems(labels,(d,i)->jump(hits.get(i).id,"favorites"))
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
        // Draw the drawer background through the system bars, but keep its contents
        // inside actual device insets (including cutouts and gesture navigation).
        LinearLayout pane=column();
        pane.setPadding(px(14),px(13),px(14),px(12));
        // This panel touches the display edge: rounded top corners exposed the
        // dark dialog scrim behind Android's status bar as a black notch.
        pane.setBackgroundColor(themeIndex==2?0xfff5f7fb:PANEL);pane.setElevation(px(12));
        int width=Math.min(px(348),getResources().getDisplayMetrics().widthPixels-px(38));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width,-1,Gravity.START);
        overlay.addView(pane,lp);
        TextView app=text("Telegram Archive",20,FG);
        app.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        app.setPadding(px(5),px(3),0,px(2));pane.addView(app);
        TextView desc=text("Чаты, файлы и настройки",12,SUB);
        desc.setPadding(px(5),0,0,px(11));pane.addView(desc);
        ScrollView menuScroll=new ScrollView(this);menuScroll.setVerticalScrollBarEnabled(false);
        menuScroll.setFillViewport(false);pane.addView(menuScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout items=column();menuScroll.addView(items);
        sideMenuItem(items,dialog,pane,"Переписки","К списку всех чатов",()->{
            home=true;screen="chat";showPage();});
        sideMenuItem(items,dialog,pane,"Личные заметки","Заметки, цитаты и избранное",()->{
            savedFavoritesOnly=false;home=false;screen="saved";renderSaved();});
        sideMenuItem(items,dialog,pane,"Корзина","Вернуть убранные переписки",this::showChatTrash);
        View divider=new View(this);divider.setBackgroundColor(ViewerUi.mix(PANEL,SUB,24));
        LinearLayout.LayoutParams dividerMargin=p(-1,1);
        dividerMargin.setMargins(px(8),px(10),px(8),px(7));items.addView(divider,dividerMargin);
        sideMenuSection(items,"Архивы и резервные копии",false,group->{
            sideMenuItem(group,dialog,pane,"▧  Подключить папку экспорта","Расширенный способ для папки Telegram с медиа",this::chooseExistingFolder);
            sideMenuItem(group,dialog,pane,"⇧  Создать резервную копию","Чаты, настройки и при выборе — медиа",this::backupPicker);
            sideMenuItem(group,dialog,pane,"✓  Проверить копию","Контроль целостности без изменений",this::verifyBackupPicker);
            sideMenuItem(group,dialog,pane,"◈  Пробное восстановление","Тест без изменения переписок",this::dryRunPicker);
            sideMenuItem(group,dialog,pane,"▤  Квитанция переноса","Сверка последнего восстановления",this::showLastRestoreReceipt);
            sideMenuItem(group,dialog,pane,"⇩  Восстановить архив","Импорт резервной копии",this::restorePicker);
        });
        sideMenuSection(items,"Поиск и файлы",false,group->{
            sideMenuItem(group,dialog,pane,"Умный поиск по всем чатам","Слова, авторы, год и вложения; офлайн",this::discoverySmartSearch);
            sideMenuItem(group,dialog,pane,"Просмотреть отдельный файл","Текст Office, документы и архивы",this::chooseReadOnlyPreview);
        });
        sideMenuSection(items,"Оформление и персонализация",false,group->{
            sideMenuItem(group,dialog,pane,"Темы и оформление","Светлая или тёмная цветовая схема",this::appearanceDialog);
            sideMenuItem(group,dialog,pane,"Стеклянные карточки","Прозрачный или сплошной фон",this::glassDialog);
            sideMenuItem(group,dialog,pane,"Шрифты","Системный шрифт или свой TTF/OTF",this::showFontStudio);
            sideMenuItem(group,dialog,pane,"Обои переписки","Фото, узор и его интенсивность",this::wallpaperDialog);
            sideMenuItem(group,dialog,pane,"Аватары","Изображения участников и чатов",this::avatarSelection);
            sideMenuItem(group,dialog,pane,"Настройки переписки","Пузырьки, плотность, имя и цвета",this::settingsMenu);
            sideMenuItem(group,dialog,pane,"Умная производительность","Скорость, память и анимации",this::performanceDialog);
        });
        sideMenuSection(items,"Помощь и диагностика",false,group->{
            sideMenuItem(group,dialog,pane,"Помощь и инструкция","Как пользоваться приложением",this::showHelp);
            sideMenuItem(group,dialog,pane,"Учебная переписка","Попробовать функции на примере",
                ()->startActivity(new Intent(this,DemoChatActivity.class)));
            sideMenuItem(group,dialog,pane,"Диагностика","Обезличенный технический отчёт",this::shareDiagnostic);
        });
        TextView label=text("Telegram Archive · "+installedVersionName()+" QA\nЛокально · Без аккаунта · Без серверов",11,SUB);
        label.setPadding(px(5),px(12),0,px(5));pane.addView(label);
        overlay.setOnClickListener(v->closeDrawer(dialog,pane,null));pane.setOnClickListener(v->{});
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(overlay,(view,insets)->{
            androidx.core.graphics.Insets safe=insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()|
                androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            pane.setPadding(px(14)+safe.left,safe.top+px(13),
                px(14)+safe.right,safe.bottom+px(12));
            return insets;
        });
        dialog.setContentView(overlay);
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null)styleDrawerWindow(w,themeIndex==2);
        androidx.core.view.ViewCompat.requestApplyInsets(overlay);
        pane.setTranslationX(-width);pane.animate().translationX(0).setDuration(motionDuration(260))
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    /** A true edge-to-edge drawer, with system-bar icon contrast matching the theme. */
    static void styleDrawerWindow(Window window,boolean lightSystemBars){
        // A transparent window is required so the drawer (rather than the
        // dialog theme's default dark background) paints behind system bars.
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29){
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        int flags=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if(lightSystemBars)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController controller=window.getInsetsController();
            if(controller!=null){
                int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightSystemBars?mask:0,mask);
            }
        }
        window.setLayout(-1,-1);
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
        TextView title=text((opened?"⌄  ":"›  ")+name,13,ACCENT);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setPadding(px(12),px(15),px(8),px(15));title.setMinHeight(px(48));
        title.setContentDescription((opened?"Свернуть ":"Развернуть ")+name);
        title.setBackground(shape(Color.TRANSPARENT,9));
        group.addView(title,p(-1,-2));
        LinearLayout contents=column();contents.setPadding(0,px(3),0,px(4));group.addView(contents);
        populate.accept(contents);contents.setVisibility(opened?View.VISIBLE:View.GONE);
        title.setOnClickListener(v->{boolean expand=contents.getVisibility()!=View.VISIBLE;
            title.setText((expand?"⌄  ":"›  ")+name);
            title.setContentDescription((expand?"Свернуть ":"Развернуть ")+name);
            contents.setVisibility(expand?View.VISIBLE:View.GONE);
            if(expand){
                contents.setAlpha(0f);contents.animate().alpha(1f)
                    .setDuration(motionDuration(170)).start();
                // A section expanded near the bottom must reveal its actions;
                // otherwise users can tap a heading and see no visible change.
                group.post(()->{
                    if(contents.getVisibility()!=View.VISIBLE)return;
                    if(parent.getParent() instanceof ScrollView){
                        ScrollView menu=(ScrollView)parent.getParent();
                        menu.smoothScrollTo(0,Math.max(0,group.getTop()-px(12)));
                    }
                });
            }
        });
        space(group,3);
    }
    private void sideMenuItem(LinearLayout parent,Dialog dialog,View pane,
                              String heading,String detail,Runnable action){
        int split=heading.indexOf("  ");
        String label=split>=0?heading.substring(split+2).trim():heading;
        LinearLayout item=row();item.setPadding(px(11),px(10),px(9),px(10));item.setMinimumHeight(px(56));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x265aaad8),
            shape(Color.TRANSPARENT,10),null));
        LinearLayout.LayoutParams params=p(-1,-2);params.bottomMargin=px(2);parent.addView(item,params);
        ImageView symbol=new ImageView(this);
        symbol.setImageDrawable(new MenuOutlineIcon(label,ACCENT));
        symbol.setContentDescription(null);
        LinearLayout.LayoutParams symbolSize=p(20,20);symbolSize.rightMargin=px(12);
        item.addView(symbol,symbolSize);
        LinearLayout info=column();item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=text(label,14,FG);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));info.addView(name);
        TextView summary=text(detail,12,SUB);summary.setMaxLines(2);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setPadding(0,px(2),0,0);info.addView(summary);
        item.setContentDescription(label+". "+detail);
        item.setOnClickListener(v->closeDrawer(dialog,pane,action));
    }
    private void glassDialog(){
        new FontDialog.Builder(this).setTitle("Полупрозрачные карточки")
            .setSingleChoiceItems(new String[]{"Стеклянный стиль", "Обычные карточки"},glassStyle?0:1,
                (d,selection)->{glassStyle=selection==0;getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("appearance_glass",glassStyle).apply();d.dismiss();showPage();})
            .setNegativeButton("Закрыть",null).show();
    }
    private String installedVersionName(){
        try {
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            return version==null||version.trim().isEmpty()?"unknown":version;
        }catch(android.content.pm.PackageManager.NameNotFoundException ignored){return "unknown";}
    }
    private void shareDiagnostic(){
        // Privacy boundary: only app/build, SDK and coarse memory/performance
        // fields. Never include chat names, paths, IDs, messages or stack traces.
        String report=DiagnosticReport.render(installedVersionName(),Build.VERSION.SDK_INT,
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
        sample.setTextColor(FG);sample.setTextSize(18);sample.setBackground(ViewerUi.surface(this,PANEL,ViewerUi.CARD,themeIndex==2,false));
        sample.setPadding(px(16),px(16),px(16),px(16));
        android.graphics.Typeface chosen;
        switch(key){
            case "serif":chosen=Typeface.SERIF;break;
            case "mono":chosen=Typeface.MONOSPACE;break;
            case "sans":chosen=Typeface.SANS_SERIF;break;
            case "condensed":chosen=Typeface.create("sans-serif-condensed",Typeface.NORMAL);break;
            case "casual":chosen=Typeface.create("casual",Typeface.NORMAL);break;
            case "light":chosen=Typeface.create("sans-serif-light",Typeface.NORMAL);break;
            case "medium":chosen=Typeface.create("sans-serif-medium",Typeface.NORMAL);break;
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
            rememberChatPositionForReturn();
            if(!home&&"search".equals(screen)&&scroll!=null)pendingSearchScroll=scroll.getScrollY();
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
        if(pdfButton!=null)pdfButton.setVisibility(View.GONE);
        savedComposer.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        playerStrip.setVisibility(View.GONE);pinnedBar.removeAllViews();
        filterBar.removeAllViews();searchArea.removeAllViews();
        status.setText("");status.setVisibility(View.GONE);
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
    /** Direct route renderers bypass showPage(), so clear both gallery overlays together. */
    private void hideGalleryOverlay(){
        if(mediaRecycler!=null)mediaRecycler.setVisibility(View.GONE);
        if(mediaFastScroller!=null)mediaFastScroller.setVisibility(View.GONE);
        if(mediaFastLabel!=null)mediaFastLabel.setVisibility(View.GONE);
    }
    private void renderHome(){
        hideGalleryOverlay();
        if(scroll!=null)scroll.setVisibility(View.VISIBLE);
        list.setBackgroundColor(BG);
        list.setPadding(px(3),px(6),px(3),px(14));
        savedComposer.setVisibility(View.GONE);
        final int homeToken=++loadToken;list.removeAllViews();list.setTag("home-list");pinnedBar.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();
        verticalTimeline.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);bottomNav.setVisibility(View.GONE);
        menuButton.setVisibility(View.VISIBLE);navBack.setVisibility(View.GONE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        if(pdfButton!=null)pdfButton.setVisibility(View.GONE);
        if(headerLogo!=null)headerLogo.setVisibility(View.VISIBLE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE); // One primary import action above chat list.
        title.setText("Мои переписки");subtitle.setText("Твои сообщения · в одном месте");
        status.setText("");status.setVisibility(View.GONE);
        // Keep the single primary import action above even a very long archive list.
        TextView add=text("＋   Добавить переписку",15,themeIndex==2?0xffffffff:BG);
        add.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));add.setGravity(Gravity.CENTER);
        add.setPadding(px(12),px(12),px(12),px(12));
        add.setBackground(new RippleDrawable(ColorStateList.valueOf(themeIndex==2?0x33ffffff:0x22000000),
            shape(ACCENT,ViewerUi.CARD),shape(0xffffffff,ViewerUi.CARD)));
        add.setContentDescription("Добавить переписку из экспорта Telegram");
        LinearLayout.LayoutParams ap=p(-1,52);ap.setMargins(px(2),px(2),px(2),px(10));list.addView(add,ap);
        add.setOnClickListener(v->chooseFolder());
        // Flat, compact list heading: the app bar already says "Мои переписки".
        LinearLayout homeHeading=row();homeHeading.setPadding(px(10),px(9),px(10),px(7));
        list.addView(homeHeading,p(-1,-2));
        int hiddenChats=0;
        for(int i=0;i<archives.length();i++){
            JSONObject entry=archives.optJSONObject(i);
            if(entry!=null&&isChatHidden(entry.optString("key")))hiddenChats++;
        }
        TextView heading=text("Чаты",17,FG);
        heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        homeHeading.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView countLabel=text(String.valueOf(archives.length())+(hiddenChats>0?" · скрыто "+hiddenChats:""),12,SUB);
        countLabel.setContentDescription("Всего чатов: "+archives.length());homeHeading.addView(countLabel);
        // One primary import action stays on the home screen. The protected
        // library is a navigation destination, not a second import button.
        int protectedCount=0;
        try{
            protectedCount=new JSONArray(getSharedPreferences("protected_archive_pins",MODE_PRIVATE)
                .getString("items","[]")).length();
        }catch(org.json.JSONException ignored){}
        TextView protectedArchives=text("Защищённые переписки"+
            (protectedCount>0?" · "+protectedCount:"")+"   ›",14,ACCENT);
        protectedArchives.setGravity(Gravity.CENTER_VERTICAL);
        protectedArchives.setPadding(px(12),0,px(12),0);
        protectedArchives.setMinHeight(px(48));
        protectedArchives.setContentDescription("Защищённые архивы ZIP и RAR, открыть библиотеку");
        protectedArchives.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3390d5ff),
            shape(0x00000000,12),shape(0xffffffff,12)));
        list.addView(protectedArchives,p(-1,-2));
        protectedArchives.setOnClickListener(v->startActivity(new Intent(this,ProtectedArchiveActivity.class)));
        JSONArray trash=chatTrash();
        if(trash==null||trash.length()>0){
            Button recycle=btn(trash==null?"Корзина · требуется проверка":"Корзина · "+trash.length());
            LinearLayout.LayoutParams recycleMargin=p(-1,42);recycleMargin.bottomMargin=px(7);
            list.addView(recycle,recycleMargin);
            recycle.setOnClickListener(v->showChatTrash());
        }
        // No duplicate chapter heading between the app header and chat list.
        // Hidden chats stay indexed and backed up, but disappear from ordinary navigation.
        final boolean showHidden=getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false);
        if(hiddenChats>0){
            Button toggleHidden=btn(showHidden?"Свернуть скрытые чаты":"Показать скрытые чаты ("+hiddenChats+")");
            list.addView(toggleHidden,p(-1,45));
            toggleHidden.setOnClickListener(v->{
                getPreferences(MODE_PRIVATE).edit().putBoolean("show_hidden_chats",!showHidden).apply();
                renderHome();
            });
        }
        // User-declared contact groups are a navigation layer only. Message databases,
        // quote anchors, notes and source media remain in their original chats.
        JSONArray multiGroups;
        try{multiGroups=MultiConversationRegistry.read(getPreferences(MODE_PRIVATE));}
        catch(IllegalStateException corrupted){
            multiGroups=new JSONArray();
            TextView error=text("Мультипереписки недоступны: настройки повреждены. Данные не изменены.",12,0xffffbe8f);
            error.setPadding(px(9),px(9),px(9),px(9));list.addView(error,p(-1,-2));
        }
        // The title bar already says "Мои переписки". Do not repeat a second
        // "Чаты" heading above grouped people when all chats belong to a group.
        if(multiGroups.length()>0)homeHeading.setVisibility(View.GONE);
        final JSONArray visibleGroups=multiGroups;
        final boolean showGroupedChats=getPreferences(MODE_PRIVATE)
            .getBoolean("show_grouped_individual_chats",false);
        if(multiGroups.length()>0){
            TextView groupsHeading=text("Мультипереписки",16,FG);
            groupsHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            groupsHeading.setPadding(px(11),px(12),0,px(9));list.addView(groupsHeading,p(-1,-2));
            for(int i=0;i<multiGroups.length();i++){
                JSONObject group=multiGroups.optJSONObject(i);
                if(group==null)continue;
                String id=group.optString("id"),person=group.optString("name","Человек");
                JSONArray members=group.optJSONArray("members");
                int connected=0;
                if(members!=null)for(int j=0;j<members.length();j++){
                    JSONObject member=members.optJSONObject(j);
                    if(member!=null&&canOpenQuoteArchive(member.optString("key"))&&
                        (!isChatHidden(member.optString("key"))||showHidden))connected++;
                }
                // Do not reveal an entirely hidden person's name on the home screen.
                if(connected==0&&members!=null&&members.length()>0)continue;
                LinearLayout groupCard=row();groupCard.setPadding(px(12),px(10),px(13),px(10));
                groupCard.setGravity(Gravity.CENTER_VERTICAL);
                groupCard.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2e6fc6f4),
                    outlined(PANEL,14,false),null));
                LinearLayout.LayoutParams cardMargins=p(-1,68);
                cardMargins.setMargins(px(2),px(1),px(2),px(5));
                list.addView(groupCard,cardMargins);
                TextView avatar=text("◈",25,FG);avatar.setGravity(Gravity.CENTER);
                avatar.setBackground(gradient(0xff6242a1,0xff305d86,100));
                groupCard.addView(avatar,p(45,45));
                LinearLayout info=column();info.setPadding(px(12),0,0,0);
                groupCard.addView(info,new LinearLayout.LayoutParams(0,-2,1));
                TextView titleLabel=text(person,15,FG);
                titleLabel.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
                titleLabel.setSingleLine(true);titleLabel.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(titleLabel);
                TextView secondary=text(connected+" переписки · нажми, чтобы выбрать источник",12,SUB);
                secondary.setSingleLine(true);secondary.setEllipsize(TextUtils.TruncateAt.END);
                info.addView(secondary);
                TextView arrow=text("›",25,SUB);groupCard.addView(arrow,p(-2,-2));
                groupCard.setContentDescription("Мультипереписка "+person+" · "+connected+" источников");
                groupCard.setOnClickListener(v->showMultiConversation(id));
                groupCard.setOnLongClickListener(v->{manageMultiConversation(id);return true;});
            }
            // One secondary action row instead of two giant stacked buttons.
            LinearLayout groupControls=row();
            groupControls.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams controlsLp=p(-1,46);
            controlsLp.setMargins(px(2),px(5),px(2),px(9));
            list.addView(groupControls,controlsLp);
            Button toggleIndividual=btn(showGroupedChats?"Скрыть чаты":"Показать чаты");
            toggleIndividual.setContentDescription(showGroupedChats?
                "Скрыть отдельные чаты мультипереписок":"Показать отдельные чаты из групп");
            LinearLayout.LayoutParams toggleLp=new LinearLayout.LayoutParams(0,px(44),1);
            toggleLp.rightMargin=px(4);groupControls.addView(toggleIndividual,toggleLp);
            toggleIndividual.setOnClickListener(v->{
                getPreferences(MODE_PRIVATE).edit()
                    .putBoolean("show_grouped_individual_chats",!showGroupedChats).apply();
                renderHome();
            });
            Button createGroup=btn("＋ Группа");
            createGroup.setContentDescription("Создать мультипереписку");
            groupControls.addView(createGroup,new LinearLayout.LayoutParams(0,px(44),1));
            createGroup.setOnClickListener(v->createMultiConversationDialog());
        }else if(archives.length()>0){
            Button createGroup=btn("＋ Создать мультипереписку");
            LinearLayout.LayoutParams createSize=p(-1,44);
            createSize.setMargins(px(2),px(7),px(2),px(9));
            list.addView(createGroup,createSize);
            createGroup.setOnClickListener(v->createMultiConversationDialog());
        }
        ArrayList<JSONObject> orderedChats=new ArrayList<>();
        for(int i=0;i<archives.length();i++){
            JSONObject c=archives.optJSONObject(i);
            if(c!=null&&(!isChatHidden(c.optString("key"))||showHidden)
                &&(showGroupedChats||!MultiConversationRegistry.memberOfAny(visibleGroups,c.optString("key"))))
                orderedChats.add(c);
        }
        orderedChats.sort((a,b)->Boolean.compare(isChatPinned(b.optString("key")),isChatPinned(a.optString("key"))));
        if(multiGroups.length()>0&&!orderedChats.isEmpty()){
            TextView ungroupedHeading=text("Отдельные переписки",15,FG);
            ungroupedHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            ungroupedHeading.setPadding(px(11),px(10),px(11),px(7));
            list.addView(ungroupedHeading,p(-1,-2));
        }
        for(int i=0;i<orderedChats.size();i++){
            JSONObject chat=orderedChats.get(i);
            String key=chat.optString("key"),name=chat.optString("name","Переписка");
            LinearLayout item=row();item.setPadding(px(12),px(8),px(10),px(8));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2e6fc6f4),outlined(PANEL,14,false),null));
            LinearLayout.LayoutParams margins=p(-1,70);margins.setMargins(px(2),px(1),px(2),px(5));list.addView(item,margins);
            String letter=name.isEmpty()?"?":name.substring(0,1).toUpperCase(Locale.ROOT);
            int avatarColor=participantColors[(key.hashCode()&Integer.MAX_VALUE)%participantColors.length];
            TextView avatar=text(letter,20,ChatSurfacePolicy.avatarText(avatarColor));avatar.setTypeface(null,Typeface.BOLD);avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(shape(avatarColor,100));
            Bitmap picture=PersonalImages.load(this,getPreferences(MODE_PRIVATE).getString(PersonalImages.chatAvatarKey(key),""));
            if(picture!=null){avatar.setText("");avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),PersonalImages.circular(picture,px(46))));}
            item.addView(avatar,p(46,46));
            avatar.setContentDescription("Установить аватар чата "+name);
            avatar.setOnClickListener(v->{pendingImageChatKey=key;pickPersonalImage(62);});
            LinearLayout info=column();info.setPadding(px(11),0,0,0);item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView nameLabel=text((isChatPinned(key)?"★  ":"")+name,15,FG);
            nameLabel.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            nameLabel.setSingleLine(true);nameLabel.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(nameLabel);
            TextView preview=text(isChatHidden(key)?"Скрытый чат":"Последнее сообщение загружается…",12,SUB);
            preview.setSingleLine(true);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setPadding(0,px(4),0,0);
            info.addView(preview);
            if(!isChatHidden(key))loadHomePreview(key,preview,homeToken);
            Button actions=btn("⋯");actions.setContentDescription("Управление чатом "+name);
            item.addView(actions,p(39,42));
            actions.setOnClickListener(v->showChatActions(key,name));
            item.setOnClickListener(v->{item.animate().cancel();item.setAlpha(1f);activateArchive(key);});
            item.setOnLongClickListener(v->{showChatActions(key,name);return true;});
        }
        if(archives.length()==0){
            emptyWorkspace(list,"Переписок пока нет","Добавь экспорт Telegram, чтобы читать сообщения и медиа без интернета.","Переписки");
        }
        if(archives.length()>=2&&multiGroups.length()==0){
            Button compare=btn("Сравнить две переписки");
            compare.setContentDescription("Сравнить статистику и общую историю двух переписок");
            LinearLayout.LayoutParams outer=p(-1,48);outer.topMargin=px(10);list.addView(compare,outer);
            compare.setOnClickListener(v->chooseFirstDualChat("dual_compare"));
        }
        animateEntrance();scroll.post(()->scroll.scrollTo(0,0));
    }
    /** Read-only local preview; no import, mutation or archive database creation. */
    private void loadHomePreview(String key,TextView target,int requestToken){
        if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}")){target.setText("Локальная переписка");return;}
        worker.execute(()->{
            String textValue="Нет сообщений";
            try{
                String filename="default".equals(key)?"telegram_archive.db":"telegram_"+key+".db";
                java.io.File file=getDatabasePath(filename);
                if(file.isFile()){
                    try(android.database.sqlite.SQLiteDatabase reader=android.database.sqlite.SQLiteDatabase.openDatabase(
                            file.getAbsolutePath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
                        android.database.Cursor cursor=reader.rawQuery(
                            "SELECT date_text,body,media FROM messages ORDER BY id DESC LIMIT 1",null)){
                        if(cursor.moveToFirst()){
                            String date=cursor.getString(0);
                            String body=cursor.getString(1);
                            String media=cursor.getString(2);
                            String excerpt=(body==null?"":body.trim().replaceAll("\\s+"," "));
                            if(excerpt.isEmpty())excerpt=media!=null&&!media.isEmpty()?"Вложение":"Сообщение без текста";
                            textValue=clip(excerpt,66)+(date==null||date.isEmpty()?"":" · "+day(date));
                        }
                    }
                }
            }catch(Exception error){textValue="Нет предпросмотра · открыть чат";}
            final String preview=textValue;
            runOnUiThread(()->{
                if(isFinishing()||!home||loadToken!=requestToken||target.getParent()==null)return;
                target.setText(preview);
            });
        });
    }
    private boolean isChatHidden(String key){
        return getPreferences(MODE_PRIVATE).getBoolean("hidden_chat_"+key,false);
    }
    private java.util.Set<String> registeredChatKeys(){
        java.util.Set<String> keys=new java.util.HashSet<>();
        for(int i=0;i<archives.length();i++){
            JSONObject item=archives.optJSONObject(i);
            if(item!=null)keys.add(item.optString("key"));
        }
        return keys;
    }
    private String archiveLabelForGroup(String key){
        for(int i=0;i<archives.length();i++){
            JSONObject item=archives.optJSONObject(i);
            if(item!=null&&key.equals(item.optString("key")))
                return item.optString("name","Переписка");
        }
        return "Переписка недоступна · проверь корзину";
    }
    private JSONArray multiConversationGroups(){
        try{return MultiConversationRegistry.read(getPreferences(MODE_PRIVATE));}
        catch(IllegalStateException damaged){
            new FontDialog.Builder(this).setTitle("Нельзя прочитать группы")
                .setMessage("Список повреждён. Исходные переписки не изменены. "+
                    "Не создавай новые группы до восстановления настроек из копии.")
                .setPositiveButton("Закрыть",null).show();
            return null;
        }
    }
    private void createMultiConversationDialog(){
        if(importing||restoringBackup){toast("Дождись завершения операции");return;}
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setHint("Например, Вася");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new FontDialog.Builder(this).setTitle("Новая мультипереписка")
            .setMessage("Создай карточку человека, затем подключи его переписки. "+
                "Исходные сообщения и заметки не объединяются и не удаляются.")
            .setView(name).setNegativeButton("Отмена",null)
            .setPositiveButton("Создать",(d,w)->{
                try{
                    String id=MultiConversationRegistry.create(getPreferences(MODE_PRIVATE),
                        name.getText().toString());
                    renderHome();
                    showAddChatToMultiConversation(id);
                }catch(Exception err){toast(err.getMessage());}
            }).show();
    }
    private void showMultiConversation(String id){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        JSONObject group=MultiConversationRegistry.find(groups,id);
        if(group==null){toast("Группа больше не найдена");renderHome();return;}
        JSONArray members=group.optJSONArray("members");
        ArrayList<String> labels=new ArrayList<>();
        ArrayList<String> keys=new ArrayList<>();
        boolean reveal=getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false);
        if(members!=null)for(int i=0;i<members.length();i++){
            JSONObject entry=members.optJSONObject(i);if(entry==null)continue;
            String key=entry.optString("key");
            if(!canOpenQuoteArchive(key)||isChatHidden(key)&&!reveal)continue;
            keys.add(key);
            labels.add(entry.optString("label","Источник")+" · "+archiveLabelForGroup(key));
        }
        labels.add(0,"◷  Общая лента сообщений");
        labels.add("＋  Добавить переписку");
        labels.add("⋮  Настроить группу");
        String[] options=labels.toArray(new String[0]);
        new FontDialog.Builder(this).setTitle(group.optString("name")+" · мультипереписка")
            .setItems(options,(d,which)->{
                if(which==0)openMultiTimeline(id);
                else if(which<=keys.size())activateArchive(keys.get(which-1));
                else if(which==keys.size()+1)showAddChatToMultiConversation(id);
                else manageMultiConversation(id);
            }).setNegativeButton("Закрыть",null).show();
    }
    /** Opens a shared, read-only view, NOT a new conversation or a merged database. */
    private void openMultiTimeline(String id){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        if(MultiConversationRegistry.find(groups,id)==null){toast("Группа не найдена");return;}
        closeFloatingRound();stopPlayback();
        multiTimelineGroupId=id;multiTimelinePerSource=GroupTimeline.PAGE_SIZE;
        multiTimelinePage=0;home=false;screen="multi_timeline";returnScreen="";
        showPage();
    }
    private void openMultiTimelineMessage(SharedMemories.Entry item){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        JSONObject group=MultiConversationRegistry.find(groups,multiTimelineGroupId);
        if(group==null){toast("Мультипереписка уже удалена");return;}
        boolean allowed=false;
        JSONArray members=group.optJSONArray("members");
        if(members!=null)for(int i=0;i<members.length();i++){
            JSONObject m=members.optJSONObject(i);
            if(m!=null&&item.archiveKey.equals(m.optString("key")))allowed=true;
        }
        boolean reveal=getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false);
        if(!allowed||!canOpenQuoteArchive(item.archiveKey)||
            (isChatHidden(item.archiveKey)&&!reveal)){
            toast("Источник недоступен или скрыт");return;
        }
        activateArchive(item.archiveKey,item.messageId);
    }
    private void renderMultiTimelinePage(){
        final int token=++loadToken;
        hideGalleryOverlay();
        if(scroll!=null)scroll.setVisibility(View.VISIBLE);
        if(floatingPanel!=null)closeFloatingRound();
        list.setPadding(px(3),px(6),px(3),px(14));
        if(savedComposer!=null)savedComposer.setVisibility(View.GONE);
        if(playerStrip!=null)playerStrip.setVisibility(View.GONE);
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        if(pdfButton!=null)pdfButton.setVisibility(View.GONE);
        if(headerLogo!=null)headerLogo.setVisibility(View.GONE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE);
        if(bottomNav!=null)bottomNav.setVisibility(View.GONE);
        if(bottomPager!=null)bottomPager.setVisibility(View.GONE);
        if(verticalTimeline!=null)verticalTimeline.setVisibility(View.GONE);
        pinnedBar.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();
        list.removeAllViews();list.setBackgroundColor(BG);list.setTag(null);
        JSONArray groups=multiConversationGroups();
        if(groups==null)return;
        final String groupId=multiTimelineGroupId;
        JSONObject group=MultiConversationRegistry.find(groups,groupId);
        if(group==null){
            title.setText("Общая лента");subtitle.setText("Группа не найдена");
            list.addView(text("Этот человек больше не найден в мультипереписках.",14,SUB));
            return;
        }
        final String groupName=group.optString("name","Мультипереписка");
        title.setText(groupName);subtitle.setText("Общая лента · локальный просмотр");
        final int quota=multiTimelinePerSource,pageIndex=multiTimelinePage;
        TextView loading=text("Собираю сообщения из исходных чатов…",14,SUB);
        loading.setPadding(px(12),px(16),px(12),px(12));list.addView(loading);
        final ArrayList<GroupTimeline.Source> sources=new ArrayList<>();
        final boolean reveal=getPreferences(MODE_PRIVATE)
            .getBoolean("show_hidden_chats",false);
        JSONArray members=group.optJSONArray("members");
        if(members!=null)for(int i=0;i<members.length();i++){
            JSONObject m=members.optJSONObject(i);if(m==null)continue;
            String key=m.optString("key");
            if(!canOpenQuoteArchive(key)||(isChatHidden(key)&&!reveal))continue;
            try{sources.add(new GroupTimeline.Source(key,m.optString("label","Источник")));}
            catch(IllegalArgumentException ignored){}
        }
        if(sources.isEmpty()){
            list.removeAllViews();list.addView(text(
                "Нет доступных переписок. Добавь источник или покажи скрытые чаты.",14,SUB));
            return;
        }
        status.setVisibility(View.VISIBLE);
        status.setText("Читаю только локальные базы · без изменения источников");
        worker.execute(()->{
            GroupTimeline.Result report=null;String failure=null;
            try{report=GroupTimeline.read(this,sources,quota);}
            catch(Exception error){failure=error.getMessage();}
            final GroupTimeline.Result data=report;
            final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||token!=loadToken||
                    !"multi_timeline".equals(screen)||!groupId.equals(multiTimelineGroupId))
                    return;
                list.removeAllViews();status.setVisibility(View.GONE);
                if(problem!=null){
                    TextView error=text("Не удалось прочитать ленту: "+problem,14,0xffffae9e);
                    error.setPadding(px(13),px(15),px(13),px(13));
                    list.addView(error,p(-1,-2));return;
                }
                TextView description=text("Сообщения разных источников показаны по датам экспорта. "+
                    "Подписи источников заданы тобой и не подтверждают личность отправителя. "+
                    "Оригиналы и личные отметки не изменяются.",12,SUB);
                description.setPadding(px(13),px(12),px(13),px(10));
                list.addView(description,p(-1,-2));
                int total=data.items.size();
                int pages=Math.max(1,(total+GroupTimeline.PAGE_SIZE-1)/GroupTimeline.PAGE_SIZE);
                int safePage=Math.min(Math.max(0,pageIndex),pages-1);
                multiTimelinePage=safePage;
                int start=safePage*GroupTimeline.PAGE_SIZE;
                int stop=Math.min(total,start+GroupTimeline.PAGE_SIZE);
                String pageTitle="Сообщения "+(total==0?0:start+1)+"–"+stop+
                    " из "+total+" · "+sources.size()+" источн.";
                TextView count=text(pageTitle,13,ACCENT);
                count.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
                count.setPadding(px(13),px(13),px(13),px(8));
                list.addView(count,p(-1,-2));
                if(total==0)list.addView(text("Нет сообщений с распознаваемыми датами.",14,SUB));
                String previousDay="";
                for(int i=start;i<stop;i++){
                    SharedMemories.Entry row=data.items.get(i);
                    String dayLabel=row.day.toString();
                    if(!dayLabel.equals(previousDay)){
                        TextView dateHeader=text(dayLabel,12,SUB);
                        dateHeader.setPadding(px(14),px(12),px(8),px(5));
                        list.addView(dateHeader,p(-1,-2));previousDay=dayLabel;
                    }
                    LinearLayout card=column();
                    card.setPadding(px(14),px(11),px(13),px(12));
                    card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2a6fc6f4),
                        outlined(PANEL,15,false),null));
                    LinearLayout.LayoutParams cardLp=p(-1,-2);
                    cardLp.setMargins(px(3),px(2),px(3),px(5));
                    list.addView(card,cardLp);
                    String stamp=row.date==null?"":row.date;
                    TextView source=text(row.archiveName+" · "+stamp,12,ACCENT);
                    source.setSingleLine(true);source.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(source,p(-1,-2));
                    TextView author=text(row.author.isEmpty()?"Без имени":row.author,13,FG);
                    author.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
                    author.setPadding(0,px(6),0,px(4));card.addView(author,p(-1,-2));
                    TextView excerpt=text(row.preview.isEmpty()?"Сообщение без текста":row.preview,14,FG);
                    excerpt.setMaxLines(4);excerpt.setEllipsize(TextUtils.TruncateAt.END);
                    card.addView(excerpt,p(-1,-2));
                    card.setContentDescription(row.archiveName+" · "+stamp+" · "+
                        row.author+" · "+row.preview+". Нажми, чтобы открыть исходное сообщение");
                    card.setOnClickListener(v->openMultiTimelineMessage(row));
                }
                if(data.invalidDates>0||data.missingSources>0){
                    TextView caveat=text("Без даты: "+data.invalidDates+
                        " · недоступных баз: "+data.missingSources+
                        ". Такие записи нельзя безопасно расположить по времени.",12,0xffffc18b);
                    caveat.setPadding(px(12),px(12),px(12),px(10));
                    list.addView(caveat,p(-1,-2));
                }
                LinearLayout controls=row();
                controls.setPadding(px(3),px(10),px(3),px(4));
                list.addView(controls,p(-1,-2));
                Button previous=btn("‹ Новее");previous.setEnabled(safePage>0);
                controls.addView(previous,new LinearLayout.LayoutParams(0,px(46),1));
                previous.setOnClickListener(v->{multiTimelinePage--;showPage();});
                Button next=btn("Старее ›");next.setEnabled(safePage<pages-1);
                controls.addView(next,new LinearLayout.LayoutParams(0,px(46),1));
                next.setOnClickListener(v->{multiTimelinePage++;showPage();});
                if(data.hasMore&&quota<GroupTimeline.MAX_PER_SOURCE){
                    Button older=btn("＋  Загрузить более ранние из каждого источника");
                    LinearLayout.LayoutParams olderLp=p(-1,48);
                    olderLp.setMargins(px(3),px(5),px(3),px(10));
                    list.addView(older,olderLp);
                    older.setOnClickListener(v->{
                        multiTimelinePerSource=Math.min(GroupTimeline.MAX_PER_SOURCE,
                            multiTimelinePerSource+GroupTimeline.PAGE_SIZE);
                        // If newly fetched rows extend the preview, land on the next older
                        // page rather than silently redisplaying the current page.
                        multiTimelinePage=pages;
                        showPage();
                    });
                }else if(data.hasMore){
                    TextView limit=text("Достигнут лимит предварительного просмотра: "+
                        GroupTimeline.MAX_PER_SOURCE+" последних сообщений на источник. "+
                        "Для более ранних сообщений открой исходный чат.",12,SUB);
                    limit.setPadding(px(12),px(10),px(12),px(8));
                    list.addView(limit,p(-1,-2));
                }
                TextView note=text("Это общий просмотр, не склейка оригинальных файлов. "+
                    "Одинаковые тексты из разных источников сохраняются отдельно.",12,SUB);
                note.setPadding(px(12),px(11),px(12),px(22));
                list.addView(note,p(-1,-2));
                scroll.post(()->scroll.scrollTo(0,0));
            });
        });
    }
    private void showAddChatToMultiConversation(String groupId){
        if(importing||restoringBackup){toast("Дождись завершения операции");return;}
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        if(MultiConversationRegistry.find(groups,groupId)==null){toast("Группа не найдена");return;}
        ArrayList<JSONObject> candidates=new ArrayList<>();
        boolean reveal=getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false);
        for(int i=0;i<archives.length();i++){
            JSONObject chat=archives.optJSONObject(i);if(chat==null)continue;
            String key=chat.optString("key");
            if(!MultiConversationRegistry.memberOfAny(groups,key)
                    &&(!isChatHidden(key)||reveal))candidates.add(chat);
        }
        if(candidates.isEmpty()){
            new FontDialog.Builder(this).setTitle("Нет свободных переписок")
                .setMessage("Импортируй ещё одну переписку обычной кнопкой добавления. "+
                    "В каждой мультипереписке используются уже существующие чаты.")
                .setPositiveButton("Закрыть",null).show();return;
        }
        String[] options=new String[candidates.size()];
        for(int i=0;i<candidates.size();i++)
            options[i]=candidates.get(i).optString("name","Переписка");
        new FontDialog.Builder(this).setTitle("Добавить источник в группу")
            .setItems(options,(d,which)->{
                askMultiConversationSourceLabel(groupId,candidates.get(which).optString("key"));
            }).setNegativeButton("Отмена",null).show();
    }
    private void askMultiConversationSourceLabel(String groupId,String key){
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setText("Telegram");name.setSelection(name.length());
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new FontDialog.Builder(this).setTitle("Подпись источника")
            .setMessage("Например: Telegram, второй аккаунт или WhatsApp. "+
                "Подпись вводишь ты; она не подтверждает личность автора или формат файла.")
            .setView(name).setNegativeButton("Назад",(d,w)->showAddChatToMultiConversation(groupId))
            .setPositiveButton("Добавить",(d,w)->{
                try{
                    MultiConversationRegistry.add(getPreferences(MODE_PRIVATE),groupId,key,
                        name.getText().toString(),registeredChatKeys());
                    renderHome();showMultiConversation(groupId);
                }catch(Exception error){toast(error.getMessage());}
            }).show();
    }
    private void chooseMultiConversationForChat(String key){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        for(int i=0;i<groups.length();i++){
            JSONObject group=groups.optJSONObject(i);if(group==null)continue;
            JSONArray members=group.optJSONArray("members");
            if(members==null)continue;
            for(int j=0;j<members.length();j++){
                JSONObject member=members.optJSONObject(j);
                if(member!=null&&key.equals(member.optString("key"))){
                    showMultiConversation(group.optString("id"));return;
                }
            }
        }
        if(groups.length()==0){createMultiConversationDialog();return;}
        final JSONArray choices=groups;
        String[] names=new String[choices.length()+1];
        for(int i=0;i<choices.length();i++)
            names[i]=choices.optJSONObject(i).optString("name","Группа");
        names[choices.length()]="＋  Новая мультипереписка";
        new FontDialog.Builder(this).setTitle("К кому относится переписка?")
            .setItems(names,(d,which)->{
                if(which==choices.length())createMultiConversationDialog();
                else askMultiConversationSourceLabel(
                    choices.optJSONObject(which).optString("id"),key);
            }).setNegativeButton("Отмена",null).show();
    }
    private void manageMultiConversation(String id){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        JSONObject g=MultiConversationRegistry.find(groups,id);
        if(g==null)return;
        new FontDialog.Builder(this).setTitle(g.optString("name")+" · управление")
            .setItems(new String[]{"Переименовать","Исключить одну переписку",
                    "Удалить только группу"},(d,which)->{
                if(which==0)renameMultiConversation(id);
                else if(which==1)removeChatFromMultiConversation(id);
                else{
                    new FontDialog.Builder(this).setTitle("Удалить мультипереписку?")
                        .setMessage("Удалится только общая карточка. "+
                            "Все переписки, заметки, закрепления и хронология останутся.")
                        .setNegativeButton("Отмена",null)
                        .setPositiveButton("Удалить группу",(dialog,w)->{
                            try{
                                MultiConversationRegistry.delete(getPreferences(MODE_PRIVATE),id);
                                renderHome();
                            }catch(Exception error){toast(error.getMessage());}
                        }).show();
                }
            }).setNegativeButton("Назад",(d,w)->showMultiConversation(id)).show();
    }
    private void renameMultiConversation(String id){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        JSONObject g=MultiConversationRegistry.find(groups,id);if(g==null)return;
        EditText name=new EditText(this);name.setSingleLine(true);
        name.setText(g.optString("name"));name.setSelection(name.length());
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new FontDialog.Builder(this).setTitle("Название мультипереписки")
            .setView(name).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                try{MultiConversationRegistry.rename(getPreferences(MODE_PRIVATE),id,
                    name.getText().toString());renderHome();}
                catch(Exception error){toast(error.getMessage());}
            }).show();
    }
    private void removeChatFromMultiConversation(String id){
        JSONArray groups=multiConversationGroups();if(groups==null)return;
        JSONObject group=MultiConversationRegistry.find(groups,id);if(group==null)return;
        JSONArray members=group.optJSONArray("members");
        if(members==null||members.length()==0){toast("Группа пока пустая");return;}
        String[] labels=new String[members.length()];
        for(int i=0;i<members.length();i++){
            JSONObject member=members.optJSONObject(i);
            labels[i]=(member==null?"Источник":member.optString("label","Источник"))+
                " · "+(member==null?"":archiveLabelForGroup(member.optString("key")));
        }
        new FontDialog.Builder(this).setTitle("Исключить переписку из группы")
            .setItems(labels,(d,index)->{
                JSONObject member=members.optJSONObject(index);if(member==null)return;
                new FontDialog.Builder(this).setTitle("Убрать из мультипереписки?")
                    .setMessage("Удалится только связь с группой, не сама переписка и не твои заметки.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Исключить",(dd,w)->{
                        try{MultiConversationRegistry.remove(getPreferences(MODE_PRIVATE),id,
                            member.optString("key"));renderHome();showMultiConversation(id);}
                        catch(Exception error){toast(error.getMessage());}
                    }).show();
            }).setNegativeButton("Отмена",null).show();
    }
    /** Compact actions, not a full-screen stack of giant buttons.
     * The card itself opens the chat, so this menu never duplicates "Open chat". */
    private void showChatActions(String key,String name){
        if(importing||restoringBackup){toast("Дождись завершения операции");return;}
        String[] options={
            "Переименовать",
            "Изменить аватар",
            isChatPinned(key)?"Открепить":"Закрепить",
            isChatHidden(key)?"Показать чат":"Скрыть чат",
            "Убрать в корзину",
            "Мультипереписка · добавить или открыть"
        };
        new FontDialog.Builder(this).setTitle(name)
            .setItems(options,(dialog,choice)->{
                if(choice==0)renameChat(key,name);
                else if(choice==1){pendingImageChatKey=key;pickPersonalImage(62);}
                else if(choice==2)toggleChatPin(key);
                else if(choice==3){
                    boolean hidden=!isChatHidden(key);
                    getPreferences(MODE_PRIVATE).edit().putBoolean("hidden_chat_"+key,hidden).apply();
                    if(hidden)getPreferences(MODE_PRIVATE).edit().putBoolean("show_hidden_chats",false).apply();
                    home=true;screen="chat";renderHome();
                    toast(hidden?"Чат скрыт. Вернуть можно через «Показать скрытые»":"Чат снова виден");
                }else if(choice==4)confirmRemoveChatFromList(key,name);
                else if(choice==5)chooseMultiConversationForChat(key);
            })
            .setNegativeButton("Закрыть",null).show();
    }
    private void renameChat(String key,String name){
        EditText input=new EditText(this);input.setSingleLine(true);input.setText(name);
        input.setSelection(input.length());
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(80)});
        new FontDialog.Builder(this).setTitle("Название переписки")
            .setView(input).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                String updated=input.getText().toString().trim();
                if(updated.isEmpty()){toast("Название не может быть пустым");return;}
                for(int i=0;i<archives.length();i++){
                    JSONObject chat=archives.optJSONObject(i);
                    if(chat==null||!key.equals(chat.optString("key")))continue;
                    try{chat.put("name",updated);}catch(org.json.JSONException error){
                        toast("Не удалось сохранить название");return;
                    }
                    if(key.equals(activeKey))activeName=updated;
                    saveRegistry();if(home)renderHome();else showPage();toast("Название изменено");return;
                }
                toast("Чат больше не найден");
            }).show();
    }
    /** A recoverable, metadata-only bin; never delete source archives, media or SQLite. */
    private JSONArray chatTrash(){
        try{return new JSONArray(getPreferences(MODE_PRIVATE).getString("archive_trash","[]"));}
        catch(org.json.JSONException bad){return null;} // preserve corrupt data, fail closed
    }
    private void showChatTrash(){
        JSONArray removed=chatTrash();
        if(removed==null){
            new FontDialog.Builder(this).setTitle("Корзина недоступна")
                .setMessage("Список корзины поврежден. Ничего не удалено; восстанови настройки из резервной копии.")
                .setPositiveButton("Понятно",null).show();return;
        }
        if(removed.length()==0){
            new FontDialog.Builder(this).setTitle("Корзина пуста")
                .setMessage("Убранные из списка переписки появятся здесь. Файлы и базы сообщений при этом сохраняются.")
                .setPositiveButton("Закрыть",null).show();return;
        }
        // Android's AlertDialog setMessage + setItems can hide the list entirely.
        // A single visible themed panel has explicit restore buttons for each chat.
        LinearLayout panel=column();panel.setPadding(px(12),px(5),px(12),px(8));
        TextView tip=text("Выбери переписку для восстановления. Локальные файлы и база не изменятся.",12,SUB);
        tip.setPadding(px(5),0,px(5),px(12));panel.addView(tip,p(-1,-2));
        final AlertDialog dialog=new FontDialog.Builder(this).setTitle("Корзина · "+removed.length())
            .setView(panel).setNegativeButton("Закрыть",null).create();
        for(int i=0;i<removed.length();i++){
            JSONObject chat=removed.optJSONObject(i);
            if(chat==null)continue;
            final String key=chat.optString("key","");
            Button restore=btn(chat.optString("name","Переписка")+" · восстановить");
            LinearLayout.LayoutParams size=p(-1,46);size.bottomMargin=px(6);
            panel.addView(restore,size);
            restore.setOnClickListener(v->{dialog.dismiss();restoreChatFromTrash(key);});
        }
        dialog.show();
    }
    private void confirmRemoveChatFromList(String key,String name){
        new FontDialog.Builder(this).setTitle("Убрать «"+name+"» в корзину?")
            .setMessage("Чат исчезнет из списка, но останется в корзине. Его можно восстановить без нового импорта. Исходные файлы, сообщения и медиа не удаляются.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("В корзину",(d,w)->removeChatFromList(key)).show();
    }
    private void removeChatFromList(String key){
        if(importing||restoringBackup){toast("Дождись завершения операции");return;}
        JSONArray trash=chatTrash();
        if(trash==null){toast("Корзина недоступна: ничего не изменено");return;}
        JSONArray remaining=new JSONArray();JSONObject removed=null;
        for(int i=0;i<archives.length();i++){
            JSONObject chat=archives.optJSONObject(i);
            if(chat==null)continue;
            if(key.equals(chat.optString("key"))){removed=chat;continue;}
            remaining.put(chat);
        }
        if(removed==null){toast("Чат не найден");return;}
        for(int i=0;i<trash.length();i++){
            JSONObject entry=trash.optJSONObject(i);
            if(entry!=null&&key.equals(entry.optString("key"))){
                toast("Чат уже есть в корзине: ничего не изменено");return;
            }
        }
        try{
            JSONObject archived=new JSONObject(removed.toString());
            archived.put("removed_at",System.currentTimeMillis());
            trash.put(archived);
        }catch(org.json.JSONException e){toast("Не удалось сохранить запись в корзине");return;}
        String nextKey=activeKey;
        if(key.equals(activeKey)){
            JSONObject first=remaining.optJSONObject(0);
            nextKey=first==null?"default":first.optString("key","default");
        }
        boolean written=getPreferences(MODE_PRIVATE).edit()
            .putString("archive_registry",remaining.toString())
            .putString("archive_trash",trash.toString())
            .putString("active_archive",nextKey).commit();
        if(!written){toast("Не удалось сохранить корзину; чат оставлен");return;}
        archives=remaining;
        // Keep avatar and pin preferences, so restored chats retain customization.
        if(key.equals(activeKey)){
            closeFloatingRound();stopPlayback();if(db!=null)db.close();
            activeKey=nextKey;JSONObject first=null;
            for(int i=0;i<archives.length();i++){
                JSONObject entry=archives.optJSONObject(i);
                if(entry!=null&&nextKey.equals(entry.optString("key"))){first=entry;break;}
            }
            activeName=first==null?"Архив":first.optString("name","Архив");
            db=new MessageDb(this,activeKey);folder=null;
        }
        home=true;screen="chat";returnScreen="";renderHome();
        toast("Чат в корзине. Вернуть можно из списка чатов.");
    }
    private void restoreChatFromTrash(String key){
        if(importing||restoringBackup){toast("Дождись завершения операции");return;}
        if(key==null||!key.matches("[a-zA-Z0-9_-]{1,80}")){toast("Неверный ключ переписки");return;}
        JSONArray trash=chatTrash();if(trash==null){toast("Корзина недоступна");return;}
        for(int i=0;i<archives.length();i++){
            JSONObject existing=archives.optJSONObject(i);
            if(existing!=null&&key.equals(existing.optString("key"))){
                toast("Этот чат уже в списке");return;
            }
        }
        JSONArray remainingTrash=new JSONArray();JSONObject recovered=null;
        for(int i=0;i<trash.length();i++){
            JSONObject chat=trash.optJSONObject(i);
            if(chat==null)continue;
            if(key.equals(chat.optString("key"))){
                if(recovered!=null){toast("Повторяющиеся записи в корзине");return;}
                recovered=chat;
            }else remainingTrash.put(chat);
        }
        if(recovered==null){toast("Переписка не найдена в корзине");return;}
        JSONArray restored=new JSONArray();
        for(int i=0;i<archives.length();i++)restored.put(archives.optJSONObject(i));
        try{
            JSONObject clean=new JSONObject(recovered.toString());
            clean.remove("removed_at");restored.put(clean);
        }catch(org.json.JSONException e){toast("Не удалось прочитать запись");return;}
        boolean written=getPreferences(MODE_PRIVATE).edit()
            .putString("archive_registry",restored.toString())
            .putString("archive_trash",remainingTrash.toString())
            .putBoolean("hidden_chat_"+key,false).commit();
        if(!written){toast("Не удалось восстановить запись");return;}
        archives=restored;home=true;renderHome();toast("Чат возвращен в список");
    }
    private boolean isChatPinned(String key){return getPreferences(MODE_PRIVATE).getBoolean("pinned_chat_"+key,false);}
    private void toggleChatPin(String key){
        boolean next=!isChatPinned(key);
        getPreferences(MODE_PRIVATE).edit().putBoolean("pinned_chat_"+key,next).apply();
        if(home)renderHome();else showPage();toast(next?"Чат закреплён сверху":"Закрепление снято");
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
        String destination="saved".equals(screen)?"saved":"";
        if(!archiveKey.equals(activeKey))activateArchive(archiveKey,messageId,destination);
        else jump(messageId,destination);
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
        // Drawer can open Notes directly from Media, where the RecyclerView overlays
        // and hides ScrollView. Always restore the correct viewport/chrome explicitly.
        hideGalleryOverlay();
        if(scroll!=null)scroll.setVisibility(View.VISIBLE);
        if(playerStrip!=null)playerStrip.setVisibility(View.GONE);
        if(headerActions!=null)headerActions.setVisibility(View.GONE);
        if(pdfButton!=null)pdfButton.setVisibility(View.GONE);
        if(headerLogo!=null)headerLogo.setVisibility(View.GONE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE);
        status.setText("");status.setVisibility(View.GONE);
        list.setBackgroundColor(BG);
        list.setPadding(px(3),px(6),px(3),px(14));
        ++loadToken;home=false;screen="saved";list.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();pinnedBar.removeAllViews();bottomNav.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        menuButton.setVisibility(View.GONE);navBack.setVisibility(View.VISIBLE);
        title.setText(savedFavoritesOnly?"Избранное":"Личные заметки");subtitle.setText(savedFavoritesOnly?"Из всех переписок":"Личные записи и цитаты");
        if(noteInput!=null){noteDraft=noteInput.getText().toString();noteInput=null;}
        LinearLayout modes=row();LinearLayout.LayoutParams modesLp=p(-1,50);modesLp.setMargins(px(5),px(3),px(5),px(9));list.addView(modes,modesLp);
        for(boolean favorites:new boolean[]{false,true}){
            Button tab=btn(favorites?"Избранное":"Заметки");tab.setSelected(favorites==savedFavoritesOnly);
            if(favorites==savedFavoritesOnly)tab.setBackground(ViewerUi.control(this,CHIP,ViewerUi.CONTROL,themeIndex==2));
            LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,-1,1);bounds.setMargins(px(2),0,px(2),0);modes.addView(tab,bounds);
            tab.setContentDescription(favorites?"Избранное из всех переписок":"Личные записи и цитаты");
            tab.setOnClickListener(v->{if(savedFavoritesOnly!=favorites){savedFavoritesOnly=favorites;renderSaved();}});
        }
        savedComposer.removeAllViews();savedComposer.setVisibility(savedFavoritesOnly?View.GONE:View.VISIBLE);
        savedComposer.setPadding(px(6),px(4),px(6),px(4));
        if(!savedFavoritesOnly){
        LinearLayout compose=row();compose.setGravity(Gravity.CENTER_VERTICAL);
        savedComposer.addView(compose,p(-1,-2));
        EditText input=new EditText(this);input.setHint("Написать заметку…");input.setText(noteDraft);noteInput=input;
        input.setTextColor(FG);input.setHintTextColor(SUB);
        input.setTextSize(14);input.setMinLines(1);input.setMaxLines(3);
        input.setPadding(px(10),px(6),px(6),px(6));
        input.setBackground(shape(PANEL,12));
        input.setContentDescription("Новая личная заметка");
        compose.addView(input,new LinearLayout.LayoutParams(0,-2,1));
        Button attach=btn("📎");attach.setContentDescription("Прикрепить файл к заметкам");
        LinearLayout.LayoutParams attachSize=p(46,50);attachSize.leftMargin=px(4);
        compose.addView(attach,attachSize);
        attach.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,28);});
        Button send=btn("➤");send.setContentDescription("Сохранить заметку");
        LinearLayout.LayoutParams sendSize=p(46,50);sendSize.leftMargin=px(4);
        compose.addView(send,sendSize);
        send.setOnClickListener(v->{String value=input.getText().toString().trim();if(value.isEmpty())return;
            send.setEnabled(false);worker.execute(()->{try{savedStore.add(value,null,null,null);runOnUiThread(()->{
                if(input.getText().toString().trim().equals(value))input.setText("");
                if(noteDraft.trim().equals(value))noteDraft="";send.setEnabled(true);
                if("saved".equals(screen)&&!savedFavoritesOnly&&!isFinishing())renderSaved();
            });}catch(Exception error){runOnUiThread(()->{send.setEnabled(true);toast("Не удалось сохранить заметку");});}});});
        }
        scroll.post(()->{if("saved".equals(screen))scroll.scrollTo(0,0);});
        // An empty list should explain the next action, not leave a blank canvas.
        final LinearLayout emptyState=column();
        emptyState.setPadding(px(17),px(19),px(17),px(20));
        emptyState.setBackground(outlined(PANEL,16,false));
        LinearLayout.LayoutParams emptyLp=p(-1,-2);
        emptyLp.setMargins(px(5),px(17),px(5),px(8));list.addView(emptyState,emptyLp);
        TextView emptyHeading=text(savedFavoritesOnly?"Нет избранных сообщений":"Здесь будут твои заметки",17,FG);
        emptyHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        emptyState.addView(emptyHeading,p(-1,-2));
        TextView emptyHint=text(savedFavoritesOnly?"Удерживай сообщение в чате и добавь его в избранное.":"Напиши заметку или сохрани цитату из переписки.",13,SUB);
        emptyHint.setPadding(0,px(7),0,0);emptyState.addView(emptyHint,p(-1,-2));
        final int savedToken=loadToken;
        if(!savedFavoritesOnly){
        worker.execute(()->{List<SavedStore.Note> notes=savedStore.recent(150);runOnUiThread(()->{if(savedToken!=loadToken||!screen.equals("saved"))return;
            if(!notes.isEmpty())list.removeView(emptyState);
            for(SavedStore.Note n:notes){
                TextView text=text((n.body==null?"":n.body)+(n.fileName!=null?"\n📎 "+n.fileName:""),15,FG);
                text.setPadding(px(14),px(12),px(14),px(12));text.setBackground(shape(PANEL,12));
                LinearLayout.LayoutParams mp=p(-1,-2);mp.setMargins(0,px(5),0,px(5));list.addView(text,mp);
                if(n.originArchive!=null&&n.originMessageId>0){
                    TextView source=text("↗ К исходному сообщению",12,ACCENT);
                    source.setPadding(px(15),px(6),px(12),px(12));list.addView(source);
                    source.setOnClickListener(v->openSavedQuote(n.originMessageId,n.originArchive));
                    TextView context=text("Контекст цитаты",12,ACCENT);
                    context.setPadding(px(15),px(5),px(12),px(12));list.addView(context);
                    context.setOnClickListener(v->showQuoteSourceMap(n.originArchive,n.originMessageId));
                }
                text.setOnLongClickListener(v->{new FontDialog.Builder(this).setTitle("Заметка").setItems(new String[]{"Удалить заметку"},(d,w)->worker.execute(()->{savedStore.delete(n.id);runOnUiThread(()->{if("saved".equals(screen)&&!isFinishing())renderSaved();});})).show();return true;});
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
        return;
        }
        // A separate read-only overview of starred items; notes never appear here.
        analyticsText(list,"До 50 избранных сообщений из каждого чата. Полный список — в медиатеке чата.",SUB,12);
        for(int i=0;i<archives.length();i++){
            JSONObject item=archives.optJSONObject(i);if(item==null)continue;
            String key=item.optString("key"),label=item.optString("name","Чат");
            if(isChatHidden(key)&&!getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false))continue;
            worker.execute(()->{
                MessageDb other=new MessageDb(this,key);
                List<MessageDb.Item> favorites;
                try{favorites=other.page(0,50,"",false,"",false,true);}finally{other.close();}
                runOnUiThread(()->{if(!screen.equals("saved")||savedToken!=loadToken)return;
                    if(!favorites.isEmpty())list.removeView(emptyState);
                    for(MessageDb.Item m:favorites){
                        TextView t=text("★ "+label+" · "+day(m.date)+"\n"+clip(m.body,180),14,FG);
                        t.setPadding(px(12),px(12),px(12),px(12));t.setBackground(shape(CHIP,12));
                        LinearLayout.LayoutParams pad=p(-1,-2);pad.topMargin=px(5);list.addView(t,pad);
                        t.setOnClickListener(v->{if(importing)return;activateArchive(key,m.id,"saved");});
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
        long lastRenderedId=-1;int lastRenderedTop=0;
        for(int i=0;i<list.getChildCount();i++){
            View v=list.getChildAt(i);Object tag=v.getTag();
            if(!(tag instanceof Long))continue;
            lastRenderedId=(Long)tag;lastRenderedTop=v.getTop()-y;
            if(v.getBottom()>probe){visibleId=(Long)tag;visibleTop=v.getTop()-y;break;}
        }
        if(visibleId<=0){visibleId=lastRenderedId;visibleTop=lastRenderedTop;}
        if(visibleId<=0)return;
        lastVisibleMessage=visibleId;lastVisibleTop=visibleTop;
        int child=0;
        for(int i=0;i<list.getChildCount();i++){
            if(list.getChildAt(i).getBottom()>y){child=i;break;}
        }
        int approx=chatOffset+Math.min(autoLoaded-1,Math.max(0,child/2));
        SharedPreferences.Editor editor=getPreferences(MODE_PRIVATE).edit();
        editor.putInt("read_offset_"+activeKey,approx);
        editor.putLong("read_id_"+activeKey,visibleId);
        editor.putInt("read_top_"+activeKey,visibleTop);
        editor.apply();
    }
    private void rememberChatPositionForReturn(){
        if(home||!"chat".equals(screen)||list==null)return;
        // During a fast screen switch the list can still contain the old
        // screen's views. Do not overwrite an existing anchor without rows.
        for(int i=0;i<list.getChildCount();i++){
            if(list.getChildAt(i).getTag() instanceof Long){
                saveReadingProgress();
                if(lastVisibleMessage>0){pendingRestoreId=lastVisibleMessage;pendingRestoreTop=lastVisibleTop;}
                return;
            }
        }
    }
    /** Find the currently rendered round tile (if any), never a different item sharing its MP4. */
    private FrameLayout findRoundHolderForAttachment(View node,long attachmentId){
        if(node==null)return null;
        if(node instanceof FrameLayout){
            Object tag=node.getTag();
            if(tag instanceof RoundControls&&((RoundControls)tag).sourceAttachmentId==attachmentId)
                return (FrameLayout)node;
        }
        if(node instanceof ViewGroup){
            ViewGroup group=(ViewGroup)node;
            for(int i=0;i<group.getChildCount();i++){
                FrameLayout found=findRoundHolderForAttachment(group.getChildAt(i),attachmentId);
                if(found!=null)return found;
            }
        }
        return null;
    }
    /** Chat pagination can detach and rebuild the original source while a video floats. */
    private void rebindAutoFloatingSourceIfNeeded(){
        if(!autoFloating||playingAttachment==null||!"round".equals(playingAttachment.kind)
            ||autoFloatSourceHolder!=null&&autoFloatSourceHolder.isAttachedToWindow())return;
        View message=renderedMessageViews.get(playingAttachment.messageId);
        FrameLayout rebuilt=findRoundHolderForAttachment(message,playingAttachment.id);
        if(rebuilt==null||!rebuilt.isAttachedToWindow())return;
        RoundControls controls=(RoundControls)rebuilt.getTag();
        if(controls.preview==null||controls.overlay==null)return;
        autoFloatSourceHolder=rebuilt;
        autoFloatSourcePoster=controls.preview;
        autoFloatSourceOverlay=controls.overlay;
    }
    private void checkAutoFloat(){
        if(home||!"chat".equals(screen)||scroll==null)return;
        rebindAutoFloatingSourceIfNeeded();
        int[] viewport=new int[2];
        scroll.getLocationOnScreen(viewport);
        int viewportTop=viewport[1],viewportBottom=viewportTop+scroll.getHeight();

        if(floatingPanel!=null){
            // Auto-floating is reversible; a manually opened bubble stays where it is.
            if(!autoFloating||autoFloatSourceHolder==null||!autoFloatSourceHolder.isAttachedToWindow())return;
            int[] tile=new int[2];
            autoFloatSourceHolder.getLocationOnScreen(tile);
            if(!RoundPlaybackTarget.isMostlyVisible(tile[1],
                tile[1]+autoFloatSourceHolder.getHeight(),viewportTop,viewportBottom))return;

            MessageDb.Attachment attachment=playingAttachment;
            FrameLayout sourceHolder=autoFloatSourceHolder;
            ImageView sourcePoster=autoFloatSourcePoster;
            TextView sourceOverlay=autoFloatSourceOverlay;
            if(attachment==null||sourcePoster==null||sourceOverlay==null){closeFloatingRound();return;}
            int resumeMs=0;
            boolean resumePlaying=true;
            try{if(player!=null){resumeMs=Math.max(0,player.getCurrentPosition());resumePlaying=player.isPlaying();}}
            catch(Exception ignored){}
            FrameLayout oldPanel=floatingPanel;
            floatingPanel=null;autoFloating=false;
            autoFloatSourceHolder=null;autoFloatSourcePoster=null;autoFloatSourceOverlay=null;
            stopPlayback();
            root.removeView(oldPanel);
            sourcePoster.setVisibility(View.VISIBLE);
            sourceOverlay.setVisibility(View.VISIBLE);
            startRoundPlayback(attachment,sourceHolder,sourcePoster,sourceOverlay,resumeMs,resumePlaying);
            return;
        }
        if(player==null||inlineHolder==null)return;
        try{if(!player.isPlaying())return;}catch(Exception badState){return;}
        // A chat rebuild can detach the original round view before another
        // scroll callback arrives. Keep the active video visible by floating it.
        boolean attached=inlineHolder.isAttachedToWindow();
        int[] tile=new int[2];
        if(attached)inlineHolder.getLocationOnScreen(tile);
        if(RoundPlaybackTarget.shouldAutoFloat(attached,tile[1],
            tile[1]+inlineHolder.getHeight(),viewportTop,viewportBottom)){
            if(playingAttachment!=null&&"round".equals(playingAttachment.kind)){
                RoundControls controls=inlineHolder.getTag() instanceof RoundControls
                    ?(RoundControls)inlineHolder.getTag():null;
                if(controls==null||controls.preview==null||controls.overlay==null)return;
                FrameLayout origin=inlineHolder;
                MessageDb.Attachment attachment=playingAttachment;
                int resumeMs=0;try{resumeMs=Math.max(0,player.getCurrentPosition());}catch(Exception ignored){}
                floatingStart=resumeMs;
                showFloatingRound(attachment);
                autoFloatSourceHolder=origin;
                autoFloatSourcePoster=controls.preview;
                autoFloatSourceOverlay=controls.overlay;
                autoFloating=true;
            }
        }
    }
    private void loadArchiveRegistry(){
        try{archives=new JSONArray(getPreferences(MODE_PRIVATE).getString("archive_registry","[]"));}catch(Exception e){archives=new JSONArray();}
        activeKey=getPreferences(MODE_PRIVATE).getString("active_archive","default");
        // A deliberately removed chat must not return through legacy migration
        // while the active registry is empty but the recycle bin has entries.
        if(archives.length()==0&&getPreferences(MODE_PRIVATE).getString("archive_trash","[]").equals("[]")){
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
    private void activateArchive(String key){activateArchive(key,-1);}
    /** Direct jump from grouped timeline avoids the old chat's saved-scroll restoration. */
    /** Remove all old archive content before a potentially slow reading-anchor lookup. */
    private void clearArchiveTransition(){
        list.animate().cancel();list.setAlpha(1f);list.setTranslationY(0f);
        list.removeAllViews();list.setTag(null);renderedMessageViews.clear();
        list.setBackgroundColor(BG);scroll.scrollTo(0,0);
        hideGalleryOverlay();scroll.setVisibility(View.VISIBLE);
        pinnedBar.removeAllViews();filterBar.removeAllViews();searchArea.removeAllViews();
        savedComposer.setVisibility(View.GONE);bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        highlightId=-1;refreshTabs();status.setText("Открываю переписку…");status.setVisibility(View.VISIBLE);
    }
    private void activateArchive(String key,long exactMessageId){activateArchive(key,exactMessageId,"multi_timeline");}
    private void activateArchive(String key,long exactMessageId,String returnDestination){
        saveReadingProgress();
        if(importing){toast("Импорт ещё идёт");return;}
        JSONObject choice=null;for(int i=0;i<archives.length();i++){JSONObject o=archives.optJSONObject(i);if(o!=null&&key.equals(o.optString("key")))choice=o;}
        if(choice==null)return;closeFloatingRound();stopPlayback();++loadToken;clearMediaSelection();activeKey=key;activeName=choice.optString("name","Архив");analyticsSelectedAuthor="";analyticsCalendarYear=0;analyticsCompareFirst="";analyticsCompareSecond="";lastAnalytics=null;
        lastVisibleMessage=-1;lastVisibleTop=0;
        saveRegistry();
        final MessageDb previous=db;db=new MessageDb(this,activeKey);
        if(previous!=null)worker.execute(previous::close);
        String path=choice.optString("tree","");folder=path.isEmpty()?null:path.startsWith("local:")?new DocumentFolder(getContentResolver(),new java.io.File(path.substring(6))):new DocumentFolder(getContentResolver(),Uri.parse(path));
        chatOffset=searchOffset=mediaOffset=favOffset=0;autoLoaded=PAGE;home=false;screen="chat";returnScreen="";searchReturnsToAnalytics=false;chatOffset=getPreferences(MODE_PRIVATE).getInt("read_offset_"+activeKey,0);
        pendingRestoreId=getPreferences(MODE_PRIVATE).getLong("read_id_"+activeKey,-1);
        pendingRestoreTop=getPreferences(MODE_PRIVATE).getInt("read_top_"+activeKey,0);
        clearArchiveTransition();
        if(exactMessageId>0){
            pendingRestoreId=-1;
            jump(exactMessageId,returnDestination);
            return;
        }
        if(pendingRestoreId>0){
            final String openedKey=activeKey;
            final MessageDb restoreDb=db;
            final long messageId=pendingRestoreId;final int restoreToken=loadToken;
            worker.execute(()->{
                if(!openedKey.equals(activeKey)||restoreDb!=db)return;
                int restoredPos;
                // A changed or reimported archive may no longer contain the
                // saved ID. positionFor returns an insertion position, so only
                // use it after confirming that the actual message still exists.
                try{restoredPos=restoreDb.hasMessage(messageId)?restoreDb.positionFor(messageId):-1;}
                catch(RuntimeException closedOrInvalid){
                    runOnUiThread(()->{
                        if(!sameScreenRequest(restoreDb,openedKey,restoreToken)||!"chat".equals(screen))return;
                        pendingRestoreId=-1;showPage();
                    });
                    return;
                }
                final int pos=restoredPos;
                runOnUiThread(()->{
                    if(!sameScreenRequest(restoreDb,openedKey,restoreToken)||!"chat".equals(screen))return;
                    if(pos<0){pendingRestoreId=-1;showPage();return;}
                    chatOffset=Math.max(0,pos-3);showPage();
                });
            });
        }
        else showPage();
    }
    private void chatPicker(){
        ArrayList<JSONObject> choices=new ArrayList<>();
        boolean reveal=getPreferences(MODE_PRIVATE).getBoolean("show_hidden_chats",false);
        for(int i=0;i<archives.length();i++){
            JSONObject chat=archives.optJSONObject(i);
            if(chat!=null&&(!isChatHidden(chat.optString("key"))||reveal))choices.add(chat);
        }
        String[] labels=new String[choices.size()+1];
        for(int i=0;i<choices.size();i++)labels[i]=choices.get(i).optString("name","Чат");
        labels[choices.size()]="Добавить переписку";
        new FontDialog.Builder(this).setTitle("Мои архивы").setItems(labels,(d,i)->{
            if(i==choices.size())chooseFolder();
            else activateArchive(choices.get(i).optString("key"));
        }).show();
    }
    private void openChatMediaFromHeader(){
        if(home||!"chat".equals(screen)||importing)return;
        rememberChatPositionForReturn();
        screen="media";returnScreen="chat";searchReturnsToAnalytics=false;showPage();
    }
    private void openChatTool(String destination){
        if(home||!"chat".equals(screen)||importing)return;
        rememberChatPositionForReturn();
        returnScreen="chat";searchReturnsToAnalytics=false;screen=destination;showPage();
    }
    private void showConversationInfo(){showChatActions(activeKey,activeName);}
    private void showConversationMenu(View anchor){
        // A calm, readable sheet: media lives behind the chat title,
        // PDF has its own visible action, and advanced tools remain in the drawer.
        LinearLayout content=column();content.setPadding(px(14),px(8),px(14),px(12));
        // The app bar already explains tapping the chat title for media.
        // Omit the duplicate instruction inside the action sheet.
        String[] labels={"Найти сообщение","Перейти к дате","Анализ диалога","Хронология","Управление чатом","Все инструменты"};
        String[] descriptions={"Текст, автор, вложения","Сообщения выбранного дня","Активность и участники","События и периоды","Название, аватар, корзина","Настройки и общие инструменты"};
        Runnable[] actions={
            ()->openChatTool("search"),
            this::chooseDate,
            ()->{analyticsSection="overview";openChatTool("analytics");},
            ()->openChatTool("relationships"),
            ()->showChatActions(activeKey,activeName),
            this::showSideMenu
        };
        ScrollView actionScroll=new ScrollView(this);actionScroll.addView(content);
        final AlertDialog dialog=new FontDialog.Builder(this).setTitle("Действия с чатом")
            .setView(actionScroll).setNegativeButton("Закрыть",null).create();
        for(int i=0;i<labels.length;i++){
            final int index=i;
            LinearLayout item=row();item.setPadding(px(12),px(6),px(8),px(6));
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2e5aaad8),outlined(PANEL,14,false),null));
            ImageView symbol=new ImageView(this);
            symbol.setImageDrawable(new MenuOutlineIcon(labels[i],ACCENT));
            LinearLayout.LayoutParams iconSize=p(22,22);iconSize.rightMargin=px(13);item.addView(symbol,iconSize);
            LinearLayout info=column();item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView label=text(labels[i],15,FG);info.addView(label);
            TextView detail=text(descriptions[i],12,SUB);detail.setMaxLines(2);info.addView(detail);
            item.setMinimumHeight(px(56));
            LinearLayout.LayoutParams itemSize=p(-1,-2);itemSize.bottomMargin=px(3);content.addView(item,itemSize);
            item.setContentDescription(labels[i]);
            // Handle taps on the text itself, not only on the surrounding row.
            // UIAutomator/accessibility may dispatch a click directly to the label.
            Runnable open=()->{dialog.dismiss();actions[index].run();};
            item.setOnClickListener(v->open.run());
            label.setOnClickListener(v->open.run());
        }
        dialog.show();
    }
        private void chooseConversationPdf(){
        new FontDialog.Builder(this).setTitle("Сохранить переписку в PDF")
            .setMessage("В PDF войдут текст, авторы и даты всех сообщений. Вложения будут обозначены текстом, без самих файлов. Для одного файла — до "+ConversationPdfExporter.MAX_PAGES+" страниц; доступная память телефона может ограничить экспорт.")
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать файл",(dialog,which)->{
                pendingPdfKey=activeKey;pendingPdfName=activeName;
                Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf");
                picker.putExtra(Intent.EXTRA_TITLE,"Telegram Archive.pdf");
                try{startActivityForResult(picker,SAVE_CONVERSATION_PDF_REQUEST);}
                catch(Exception error){pendingPdfKey=null;pendingPdfName=null;toast("Не удалось открыть выбор файла");}
            }).show();
    }
    /** Compact Telegram-like pinned preview: tap the message to jump, arrow to switch, cross to unpin. */
    private void renderPinnedBar(){
        pinnedBar.removeAllViews();
        if(!screen.equals("chat")||home)return;
        final String key=activeKey;final MessageDb source=db;final int token=loadToken;
        if(source==null)return;
        worker.execute(()->{
            List<MessageDb.Item> pins=source.pinned();
            runOnUiThread(()->{
                if(!screen.equals("chat")||!sameScreenRequest(source,key,token))return;
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
                bar.setAlpha(1f);bar.setTranslationY(0f); // Stable pinned preview: no repeated entrance during refresh.
            });
        });
    }
    private void unpinMessage(long id){
        new FontDialog.Builder(this).setTitle("Открепить сообщение?").setMessage("Сообщение останется в переписке, но исчезнет из закреплённых.").setNegativeButton("Отмена",null).setPositiveButton("Открепить",(d,w)->unpinConfirmed(id)).show();
    }
    private void unpinConfirmed(long id){
        final MessageDb source=db;final String key=activeKey;
        if(source==null||home)return;
        worker.execute(()->{
            if(source.isPinned(id))source.togglePinned(id);
            runOnUiThread(()->{if(isDestroyed()||home||source!=db||!key.equals(activeKey))return;if(pinnedFocus==id)pinnedFocus=-1;toast("Сообщение откреплено");renderPinnedBar();});
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
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        if(source==null||home)return;
        worker.execute(()->{
            List<MessageDb.Item> pins=source.pinned();
            runOnUiThread(()->{
                if(!sameScreenRequest(source,key,token))return;
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
        head.setBackground(ViewerUi.surface(this,PANEL,ViewerUi.CARD,themeIndex==2,false));
        LinearLayout.LayoutParams headBounds=p(-1,52);headBounds.bottomMargin=px(9);
        base.addView(head,headBounds);
        menuButton=btn("☰");menuButton.setTextSize(22);menuButton.setGravity(Gravity.CENTER);menuButton.setContentDescription("Открыть главное меню");
        head.addView(menuButton,p(42,42));menuButton.setOnClickListener(v->showSideMenu());
        navBack=btn("‹");navBack.setTextSize(25);navBack.setGravity(Gravity.CENTER);navBack.setContentDescription("Назад");
        head.addView(navBack,p(42,42));
        navBack.setOnClickListener(v->{if("help".equals(screen)){leaveHelp();return;}
            if(floatingPanel!=null){closeFloatingRound();return;}
            if(backWithinHistory())return;
            // Android Back already restores the previous search/media/analytics
            // context when jumping into a message. The header Back button must
            // follow the same path rather than unexpectedly throwing users home.
            if(!returnScreen.isEmpty()){restoreReturn();return;}
            if(!home){saveReadingProgress();home=true;screen="chat";returnScreen="";searchReturnsToAnalytics=false;showPage();}});
        headerLogo=text("◇",29,ACCENT);headerLogo.setGravity(Gravity.CENTER);headerLogo.setContentDescription("Локальный архив");head.addView(headerLogo,p(34,42));
        LinearLayout headings=column();headings.setGravity(Gravity.CENTER_VERTICAL);
        // Keep the heading away from the circular Back button on compact phones.
        headings.setPadding(px(7),0,0,0);
        head.addView(headings,new LinearLayout.LayoutParams(0,px(44),1));
        title=text("Telegram Archive",18,FG);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);headings.addView(title,p(-1,26));
        subtitle=text("Личный архив · офлайн",12,SUB);
        subtitle.setSingleLine(true);subtitle.setEllipsize(TextUtils.TruncateAt.END);headings.addView(subtitle,p(-1,18));
        pdfButton=btn("PDF");pdfButton.setContentDescription("Сохранить переписку в PDF");head.addView(pdfButton,p(44,42));
        pdfButton.setOnClickListener(v->{if(!home&&"chat".equals(screen))chooseConversationPdf();});
        headerActions=btn("⋯");headerActions.setContentDescription("Другие инструменты переписки");head.addView(headerActions,p(42,42));
        headerActions.setOnClickListener(v->showConversationMenu(headerActions));
        title.setOnClickListener(v->openChatMediaFromHeader());
        headings.setOnClickListener(v->openChatMediaFromHeader());
        headings.setContentDescription("Открыть медиатеку переписки");
        folderButton=btn("＋");folderButton.setTextSize(22);head.addView(folderButton,p(41,42));
        folderButton.setOnClickListener(v->chooseFolder());
        importProgressPanel=row();importProgressPanel.setGravity(Gravity.CENTER_VERTICAL);
        importProgressPanel.setPadding(px(12),px(8),px(12),px(8));
        importProgressPanel.setBackground(outlined(PANEL,13,false));
        android.widget.ProgressBar spinner=new android.widget.ProgressBar(this,null,
            android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);
        importProgressPanel.addView(spinner,p(28,28));
        importProgressText=text("Готовлю импорт…",13,FG);
        importProgressText.setPadding(px(12),0,0,0);
        importProgressPanel.addView(importProgressText,new LinearLayout.LayoutParams(0,-2,1));
        importProgressPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressSize=p(-1,46);progressSize.bottomMargin=px(6);
        base.addView(importProgressPanel,progressSize);
        playerStrip=row();playerStrip.setVisibility(View.GONE);
        playerStrip.setPadding(px(10),0,px(5),0);playerStrip.setBackground(outlined(PANEL,16,true));
        LinearLayout.LayoutParams playerBounds=p(-1,49);playerBounds.bottomMargin=px(6);base.addView(playerStrip,playerBounds);
        playerText=text("Воспроизведение",13,FG);playerText.setSingleLine(true);
        playerText.setEllipsize(TextUtils.TruncateAt.END);
        playerStrip.addView(playerText,new LinearLayout.LayoutParams(0,-2,1));
        Button prev=btn("⏮");prev.setContentDescription("Предыдущее медиа");playerStrip.addView(prev,p(38,40));prev.setOnClickListener(v->playAdjacent(false));
        playerPause=btn("⏸");playerPause.setContentDescription("Пауза или продолжить");playerStrip.addView(playerPause,p(38,40));playerPause.setOnClickListener(v->toggleActivePlayback());
        Button next=btn("⏭");next.setContentDescription("Следующее медиа");playerStrip.addView(next,p(38,40));next.setOnClickListener(v->playAdjacent(true));
        Button close=btn("✕");playerStrip.addView(close,p(38,40));close.setOnClickListener(v->{closeFloatingRound();stopPlayback();});
        bottomNav=row();bottomNav.setPadding(px(4),px(3),px(4),px(3));
        bottomNav.setBackground(glass(PANEL,17));
        LinearLayout.LayoutParams navBounds=p(-1,46);navBounds.bottomMargin=px(4);base.addView(bottomNav,navBounds);
        // No redundant "Переписка | Медиа" tab strip. Tap the chat title to see media.
        // Existing addTab route is preserved for compatibility but hidden.
        addTab("Переписка","chat");addTab("Медиа","media");
        bottomNav.setVisibility(View.GONE);
        toolArea=column();base.addView(toolArea,p(-1,-2));
        pinnedBar=row();base.addView(pinnedBar,p(-1,-2));
        filterBar=column();toolArea.addView(filterBar,p(-1,-2));
        searchArea=column();toolArea.addView(searchArea,p(-1,-2));
        status=text("",11,SUB);status.setPadding(px(8),px(3),px(8),px(4));status.setMaxLines(3);base.addView(status,p(-1,-2));
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,ox,oy)->{if(!home&&(screen.equals("chat")||screen.equals("search"))&&!loadingMore&&list.getHeight()>0&&y>oy&&y+scroll.getHeight()>list.getHeight()-px(300)&&((screen.equals("chat")?autoLoaded:searchLoaded)<total)){appendMessages();}if(!home&&"chat".equals(screen)){scheduleFloatingCheck();scheduleReadingSave();}});
        FrameLayout scrollHost=new FrameLayout(this);
        base.addView(scrollHost,new LinearLayout.LayoutParams(-1,0,1));
        scrollHost.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        mediaColumns=MediaGalleryState.clampColumns(
            getPreferences(MODE_PRIVATE).getInt("gallery_columns",3));
        mediaRecycler=new MediaZoomRecyclerView(this);
        mediaRecycler.setVisibility(View.GONE);
        mediaRecycler.setClipToPadding(false);
        // Reserve an actual touch-safe gutter for the right fast scrubber.
        mediaRecycler.setPadding(px(2),px(6),px(37),px(16));
        mediaRecycler.setItemAnimator(null);
        mediaAdapter=new MediaGalleryAdapter();
        mediaRecycler.setAdapter(mediaAdapter);
        scrollHost.addView(mediaRecycler,new FrameLayout.LayoutParams(-1,-1));
        mediaRecycler.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener(){
            @Override public void onScrolled(androidx.recyclerview.widget.RecyclerView rv,int dx,int dy){
                syncMediaFastScroll();
                if(dy>=0)maybeAppendMedia();
                else maybePrependMedia();
            }
        });
        mediaFastScroller=new MediaFastScroller(this);
        mediaFastScroller.setVisibility(View.GONE);
        mediaFastScroller.setOnSeek(this::jumpToMediaFraction);
        scrollHost.addView(mediaFastScroller,new FrameLayout.LayoutParams(px(39),-1,Gravity.END));
        // A tooltip beside the track shows absolute position, not merely
        // the 30 thumbnails currently loaded from SQLite.
        mediaFastLabel=text("",12,FG);
        mediaFastLabel.setPadding(px(10),px(7),px(10),px(7));
        mediaFastLabel.setGravity(Gravity.CENTER);
        mediaFastLabel.setBackground(outlined(PANEL,12,true));
        mediaFastLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams labelLp=new FrameLayout.LayoutParams(-2,-2,
            Gravity.END|Gravity.CENTER_VERTICAL);
        labelLp.rightMargin=px(43);
        scrollHost.addView(mediaFastLabel,labelLp);
        mediaFastScroller.setOnDrag((position,dragging)->{
            if(mediaFastLabel==null)return;
            mediaFastLabel.setText((position+1)+" / "+mediaTotal);
            mediaFastLabel.setVisibility(dragging?View.VISIBLE:View.GONE);
        });
        verticalTimeline=new VerticalTimeline();
        scrollHost.addView(verticalTimeline,new FrameLayout.LayoutParams(px(20),-1,Gravity.END));
        list=column();list.setPadding(px(3),px(6),px(3),px(14));scroll.addView(list);
        LinearLayout bottom=row();bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottomPager=bottom;base.addView(bottom,p(-1,52));
        // Long labels in 13sp + font scaling used to crowd the page counter on small phones.
        // Keep compact arrows with full accessibility labels instead of squeezed text.
        backPage=btn("‹");backPage.setContentDescription("Предыдущая страница");
        nextPage=btn("›");nextPage.setContentDescription("Следующая страница");
        pageLabel=text("",12,SUB);pageLabel.setGravity(Gravity.CENTER);
        pageLabel.setSingleLine(true);pageLabel.setEllipsize(TextUtils.TruncateAt.END);
        savedComposer=column();savedComposer.setVisibility(View.GONE);
        savedComposer.setBackground(glass(PANEL,16));base.addView(savedComposer,p(-1,-2));
        bottom.addView(backPage,p(56,44));
        bottom.addView(pageLabel,new LinearLayout.LayoutParams(0,-2,1));
        bottom.addView(nextPage,p(56,44));
        backPage.setOnClickListener(v->{setOffset(Math.max(0,getOffset()-getLimit()));showPage();});
        nextPage.setOnClickListener(v->{setOffset(getOffset()+getLimit());showPage();});
        configureBars();
    }
    private void addTab(String label,String key){
        TextView t=text(label,13,SUB);t.setGravity(Gravity.CENTER);t.setTag(key);
        t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setContentDescription(label);t.setBackground(shape(Color.TRANSPARENT,14));
        LinearLayout.LayoutParams tabBounds=new LinearLayout.LayoutParams(0,px(39),1);
        tabBounds.leftMargin=px(2);tabBounds.rightMargin=px(2);bottomNav.addView(t,tabBounds);
        t.setOnClickListener(v->{
            if("media".equals(key))rememberChatPositionForReturn();
            if(floatingPanel==null)stopPlayback();screen=key;returnScreen="";searchReturnsToAnalytics=false;highlightId=-1;showPage();
        });
    }
    private void refreshTabs(){
        bottomNav.setVisibility(View.GONE);
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
        // Each tool screen has its own actions. Repeating the chat overflow in
        // Media (which already has a gallery menu) creates two identical dots.
        if(headerActions!=null)headerActions.setVisibility("chat".equals(screen)?View.VISIBLE:View.GONE);
        if(pdfButton!=null)pdfButton.setVisibility("chat".equals(screen)?View.VISIBLE:View.GONE);
        if(headerLogo!=null)headerLogo.setVisibility(View.GONE);
        if(folderButton!=null)folderButton.setVisibility(View.GONE);
        title.setText("media".equals(screen)? "Медиа":activeName);
        subtitle.setText(screen.equals("chat")?"Медиа — по названию чата":screen.equals("media")?activeName:screen.equals("favorites")?"Твои сохранённые сообщения":screen.equals("analytics")?"Статистика · полностью офлайн":screen.equals("relationships")?"Личные события и этапы":screen.equals("echo")?"Повторяющиеся фразы":screen.equals("chronology")?"Периоды истории · вручную":screen.equals("cinema")?"Медиа по годам · офлайн":"Найди любое сообщение");
    }
    private void returnToMediaFromFavorites(){
        screen="media";returnScreen="chat";searchReturnsToAnalytics=false;showPage();
    }
    private void configureBars(){
        filterBar.removeAllViews();searchArea.removeAllViews();
        if("favorites".equals(screen)){
            // Favorites is a distinct destination. Repeating the entire media-filter
            // strip wastes space and can crowd narrow screens.
            TextView backToMedia=text("‹ В медиатеку",13,ACCENT);
            backToMedia.setGravity(Gravity.CENTER_VERTICAL);
            backToMedia.setPadding(px(13),px(7),px(13),px(7));
            backToMedia.setContentDescription("Вернуться к медиатеке");
            backToMedia.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227ad2ff),
                shape(Color.TRANSPARENT,11),null));
            filterBar.addView(backToMedia,p(-1,42));
            backToMedia.setOnClickListener(v->returnToMediaFromFavorites());
            LinearLayout favoriteTools=row();
            Button byYear=btn("За год");byYear.setContentDescription("Избранные сообщения за выбранный год");
            favoriteTools.addView(byYear,new LinearLayout.LayoutParams(0,px(48),1));
            byYear.setOnClickListener(v->showFavoritesByYear());
            Button encrypted=btn("Зашифрованная копия");encrypted.setContentDescription("Сохранить избранные тексты с паролем");
            favoriteTools.addView(encrypted,new LinearLayout.LayoutParams(0,px(48),2));
            encrypted.setOnClickListener(v->askCapsulePassword());
            filterBar.addView(favoriteTools,p(-1,-2));
            return;
        }
        if("media".equals(screen)){
            HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout chips=row();hsv.addView(chips);
            for(int i=0;i<MEDIA_KEYS.length;i++){
                final String key=MEDIA_KEYS[i];TextView c=text(MEDIA_LABELS[i],12,FG);c.setGravity(Gravity.CENTER);
                c.setPadding(px(8),px(8),px(8),px(8));c.setBackground(shape((key.equals(mediaKind)&&"media".equals(screen)||"favorite".equals(key)&&"favorites".equals(screen))?HIGHLIGHT:PANEL,14));
                LinearLayout.LayoutParams cp=p(-2,37);cp.setMargins(px(2),px(2),px(4),px(2));chips.addView(c,cp);
                c.setSelected(key.equals(mediaKind));c.setContentDescription(MEDIA_LABELS[i]);
                c.setOnClickListener(v->{if("favorite".equals(key)){screen="favorites";favOffset=0;}else{screen="media";mediaKind=key;mediaOffset=0;}showPage();});
            }
            filterBar.addView(hsv,p(-1,44));
            // One compact media toolbar; advanced actions live in its overflow menu.
            LinearLayout mediaTools=row();mediaTools.setGravity(Gravity.CENTER_VERTICAL);
            Button layoutMode=btn(mediaGridEnabled()?"Список":"Сетка");
            // Grid slider, counter and overflow must all remain tappable on narrow phones.
            if(MediaGalleryState.supportsGrid(mediaKind))mediaTools.addView(layoutMode,p(76,42));
            layoutMode.setContentDescription(mediaGridEnabled()?
                "Перейти к списку медиа":"Перейти к масштабируемой галерее");
            layoutMode.setOnClickListener(v->toggleMediaLayout());
            mediaZoomSeek=null;mediaZoomLabel=null;
            if(mediaGridEnabled()&&MediaGalleryState.supportsGrid(mediaKind)){
                TextView small=text("2",11,SUB);small.setGravity(Gravity.CENTER);
                mediaTools.addView(small,p(14,42));
                mediaZoomSeek=new android.widget.SeekBar(this);
                mediaZoomSeek.setMax(7);mediaZoomSeek.setProgress(mediaColumns-2);
                mediaZoomSeek.setContentDescription("Размер сетки: от двух до девяти элементов в ряд");
                mediaTools.addView(mediaZoomSeek,new LinearLayout.LayoutParams(0,px(42),1));
                TextView big=text("9",11,SUB);big.setGravity(Gravity.CENTER);
                mediaTools.addView(big,p(14,42));
                mediaZoomLabel=text(mediaColumns+" в ряд",11,ACCENT);
                mediaZoomLabel.setGravity(Gravity.CENTER);
                mediaTools.addView(mediaZoomLabel,p(61,42));
                mediaZoomSeek.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener(){
                    @Override public void onProgressChanged(android.widget.SeekBar seek,int value,boolean fromUser){
                        if(fromUser)changeMediaColumns(value+2-mediaColumns);
                    }
                    @Override public void onStartTrackingTouch(android.widget.SeekBar seek){}
                    @Override public void onStopTrackingTouch(android.widget.SeekBar seek){}
                });
            }else{
                TextView hint=text("Удерживай для действий",12,SUB);
                hint.setSingleLine(true);hint.setEllipsize(TextUtils.TruncateAt.END);
                mediaTools.addView(hint,new LinearLayout.LayoutParams(0,px(42),1));
            }
            Button more=btn("⋯");more.setContentDescription("Инструменты медиатеки");
            mediaTools.addView(more,p(46,42));
            more.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Действия с медиа")
                .setItems(new String[]{multiSelectMedia?"Снять выделение":"Выбрать несколько",
                    "Мои коллекции","Найти одинаковые файлы","Медиа по годам и месяцам","Проверить доступность оригиналов"},(d,index)->{
                    if(index==0){
                        if(multiSelectMedia)clearMediaSelection();
                        else{multiSelectMedia=true;selectedMediaArchive=activeKey;}
                        showPage();
                    }else if(index==1)showCollectionPicker();
                    else if(index==2)inspectDuplicateMedia();
                    else if(index==3){cinemaReturnScreen="media";screen="cinema";showPage();}
                    else showMediaHealth();
                }).setNegativeButton("Закрыть",null).show());
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
        if("chat".equals(screen)&&!returnScreen.isEmpty()){
            // Date navigation remains available via the chat menu; no permanent
            // 46dp toolbar over every message.
            LinearLayout actions=row();
            backButton=btn("‹ Назад к "+("media".equals(returnScreen)?"медиа":"relationships".equals(returnScreen)?"хронологии":"favorites".equals(returnScreen)?"избранному":"echo".equals(returnScreen)?"фразам":"analytics".equals(returnScreen)?"анализу":"поиску"));
            actions.addView(backButton,p(-2,39));backButton.setOnClickListener(v->restoreReturn());
            filterBar.addView(actions,p(-1,41));
        }
        if("search".equals(screen)){
            if(searchReturnsToAnalytics&&"analytics".equals(returnScreen)){
                Button backToAnalytics=btn("↩ Назад к анализу");
                backToAnalytics.setContentDescription("Вернуться к анализу");
                searchArea.addView(backToAnalytics,p(-1,44));backToAnalytics.setOnClickListener(v->restoreReturn());
            }
            // Discard detached controls when the quick mode hides deep filters.
            authorInput=null;mediaOnly=null;
            searchInput=new EditText(this);searchInput.setText(restoredQueryDraft!=null?restoredQueryDraft:q);restoredQueryDraft=null;searchInput.setTextSize(16);searchInput.setSingleLine(true);
            searchInput.setTextColor(FG);searchInput.setHintTextColor(SUB);searchInput.setHint("Слово, фраза или часть слова");
            searchInput.setPadding(px(14),px(3),px(14),px(3));searchInput.setBackground(shape(PANEL,12));
            searchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            searchInput.setContentDescription("Поиск по сообщениям");
            LinearLayout queryRow=row();queryRow.setGravity(Gravity.CENTER_VERTICAL);
            queryRow.addView(searchInput,new LinearLayout.LayoutParams(0,px(48),1));
            Button clear=btn("✕");clear.setContentDescription("Очистить текст поиска");
            LinearLayout.LayoutParams clearSize=p(42,48);clearSize.leftMargin=px(4);queryRow.addView(clear,clearSize);
            Button submit=btn("Найти");submit.setContentDescription("Выполнить поиск");
            LinearLayout.LayoutParams submitSize=p(72,48);submitSize.leftMargin=px(4);queryRow.addView(submit,submitSize);
            searchArea.addView(queryRow,p(-1,52));
            clear.setOnClickListener(v->{searchInput.setText("");applySearch();});
            submit.setOnClickListener(v->applySearch());
            LinearLayout controls=row();quickButton=btn("По словам");deepButton=btn("По фразе и фильтрам");
            controls.addView(quickButton,new LinearLayout.LayoutParams(0,px(43),1));controls.addView(deepButton,new LinearLayout.LayoutParams(0,px(43),1));
            searchArea.addView(controls,p(-1,49));
            quickButton.setBackground(ViewerUi.control(this,!deep?HIGHLIGHT:PANEL,10,themeIndex==2));deepButton.setBackground(ViewerUi.control(this,deep?HIGHLIGHT:PANEL,10,themeIndex==2));
            quickButton.setSelected(!deep);deepButton.setSelected(deep);
            quickButton.setOnClickListener(v->switchSearchMode(false));
            deepButton.setOnClickListener(v->switchSearchMode(true));
            if(deep){
                authorInput=new EditText(this);authorInput.setText(restoredAuthorDraft!=null?restoredAuthorDraft:byAuthor);restoredAuthorDraft=null;authorInput.setSingleLine(true);
                authorInput.setTextColor(FG);authorInput.setHintTextColor(SUB);authorInput.setTextSize(13);
                authorInput.setHint("Отправитель (необязательно)");authorInput.setBackground(shape(PANEL,10));
                authorInput.setPadding(px(12),px(4),px(12),px(4));searchArea.addView(authorInput,p(-1,39));
                mediaOnly=new CheckBox(this);mediaOnly.setText("Только сообщения с медиа");mediaOnly.setTextColor(FG);mediaOnly.setTextSize(12);
                mediaOnly.setChecked(restoredMediaDraft!=null?restoredMediaDraft:onlyMedia);restoredMediaDraft=null;searchArea.addView(mediaOnly,p(-1,39));
                Button resetFilters=btn("Сбросить фильтры");resetFilters.setContentDescription("Сбросить фильтры поиска");
                LinearLayout filterActions=row();filterActions.setGravity(Gravity.CENTER_VERTICAL);
                TextView hint=text("Поиск в том числе внутри слова",12,SUB);hint.setPadding(px(7),0,px(5),0);
                filterActions.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
                filterActions.addView(resetFilters,p(132,44));searchArea.addView(filterActions,p(-1,-2));resetFilters.setOnClickListener(v->resetSearchFilters());
            }else{
                if(!byAuthor.isEmpty()||onlyMedia){
                    String applied="Неактивные фильтры: "+(byAuthor.isEmpty()?"":"автор «"+byAuthor+"»")+
                        (!byAuthor.isEmpty()&&onlyMedia?", ":"")+(onlyMedia?"только с медиа":"");
                    TextView filterNotice=text(applied,11,SUB);filterNotice.setContentDescription(applied);filterNotice.setPadding(px(7),px(4),px(7),px(4));
                    searchArea.addView(filterNotice,p(-1,-2));
                    Button reset=btn("Сбросить фильтры");reset.setContentDescription("Сбросить фильтры поиска");
                    searchArea.addView(reset,p(-1,48));
                    reset.setOnClickListener(v->resetSearchFilters());
                }
                TextView hint=text("Слова и их начала",12,SUB);
                hint.setPadding(px(7),0,0,px(4));searchArea.addView(hint,p(-1,-2));
            }
            searchInput.setOnEditorActionListener((v,action,event)->{applySearch();return true;});
        }
    }
    private void openWordSearchFromAnalytics(String word){
        analyticsReturnScroll=scroll.getScrollY();searchReturnsToAnalytics=true;
        q=word;byAuthor="";onlyMedia=false;deep=true;searchOffset=0;searchLoaded=PAGE;
        returnScreen="analytics";screen="search";showPage();
    }
    private void resetSearchFilters(){
        byAuthor="";onlyMedia=false;
        if(authorInput!=null)authorInput.setText("");
        if(mediaOnly!=null)mediaOnly.setChecked(false);
        applySearch();
    }
    private void switchSearchMode(boolean nextDeep){
        // Persist visible filters before hiding their controls.
        if(deep){
            if(authorInput!=null)byAuthor=authorInput.getText().toString().trim();
            if(mediaOnly!=null)onlyMedia=mediaOnly.isChecked();
        }
        deep=nextDeep;
        applySearch();
    }
    private void applySearch(){
        if(searchInput!=null)q=searchInput.getText().toString().trim();
        if(authorInput!=null&&deep)byAuthor=authorInput.getText().toString().trim();
        if(mediaOnly!=null&&deep)onlyMedia=mediaOnly.isChecked();
        searchOffset=0;searchLoaded=PAGE;searchReturnScroll=-1;pendingSearchScroll=-1;hideKeyboard();showPage();
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
    private boolean sameScreenRequest(MessageDb source,String key,int token){
        return !isFinishing()&&!isDestroyed()&&!home&&source==db&&key.equals(activeKey)&&token==loadToken;
    }
    private void chooseDate(){
        if(home||db==null)return;
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        Calendar c=Calendar.getInstance();
        FontDialog.date(this,(view,y,m,d)->{
            if(!sameScreenRequest(source,key,token))return;
            final String requested=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);
            status.setText("Ищу переписку по дате "+requested+"…");
            worker.execute(()->{
                MessageDb.DateMatch found;
                try{found=source.nearestDate(y,m+1,d);}catch(Exception error){runOnUiThread(()->{if(sameScreenRequest(source,key,token))toast("Не удалось найти дату");});return;}
                runOnUiThread(()->{
                    if(!sameScreenRequest(source,key,token))return;
                    if(found.position<0){toast("В переписке нет сообщений с распознанной датой");return;}
                    screen="chat";chatOffset=Math.max(0,found.position-5);highlightId=-1;returnScreen="";searchReturnsToAnalytics=false;
                    showPage();
                    if(!requested.equals(found.day))toast("Ближайшая дата переписки: "+found.day);
                });
            });
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
    }
    /** One tap on Add Chat opens the Android file picker; no duplicate import modal. */
    private void chooseFolder(){
        chooseCompressedArchive();
    }
    private void chooseReadOnlyPreview(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent,64);
    }
    private void showReadOnlyPreview(Uri uri,String display){
        Intent intent=new Intent(this,AttachmentPreviewActivity.class);
        intent.setData(uri);intent.putExtra("display_name",display);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
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
    /** Android Back and header Back follow the same history hierarchy. */
    private boolean backWithinHistory(){
        if(home)return false;
        if("dual_compare".equals(screen)||"dual_memories".equals(screen)){
            home=comparisonFromHome;screen=home?"chat":"relationships";returnScreen="";
            showPage();return true;
        }
        if("analytics".equals(screen)&&!"overview".equals(analyticsSection)){
            selectAnalyticsSection("overview");return true;
        }
        if("chronology".equals(screen)){
            chronologySection="overview";screen="relationships";showPage();
            return true;
        }
        if("cinema".equals(screen)){
            String target=cinemaReturnScreen;
            if(!("relationships".equals(target)||"media".equals(target)||"analytics".equals(target)
                ||"search".equals(target)||"chat".equals(target)))target="chat";
            cinemaReturnScreen="chat";screen=target;showPage();return true;
        }
        if("relationships".equals(screen)&&historyEventsExpanded){
            historyEventsExpanded=false;renderRelationshipsPage();return true;
        }
        if("favorites".equals(screen)){
            returnToMediaFromFavorites();return true;
        }
        if("echo".equals(screen)){
            returnScreen="chat";screen="relationships";showPage();return true;
        }
        // A tool belongs to the current conversation even after restoring a
        // nested result cleared the explicit return target.
        if(returnScreen.isEmpty()&&("search".equals(screen)||"analytics".equals(screen)||
                "relationships".equals(screen)||"media".equals(screen))){
            screen="chat";searchReturnsToAnalytics=false;showPage();return true;
        }
        return false;
    }
    @Override public void onBackPressed(){
        if("help".equals(screen)){leaveHelp();return;}
        if(floatingPanel!=null){closeFloatingRound();return;}
        if(backWithinHistory())return;
        if(screen.equals("saved")||(!home&&returnScreen.isEmpty())){saveReadingProgress();home=true;screen="chat";searchReturnsToAnalytics=false;bottomNav.setVisibility(View.GONE);showPage();return;}
        if(!returnScreen.isEmpty()){restoreReturn();return;}
        super.onBackPressed();
    }
    private void showPdfStatus(String key,String message){
        if(!home&&"chat".equals(screen)&&key.equals(activeKey)&&status!=null){
            status.setText(message);status.setVisibility(View.VISIBLE);
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==119){if(result==RESULT_OK&&data!=null&&!home&&"analytics".equals(screen)&&activeKey.equals(data.getStringExtra("archive"))){long id=data.getLongExtra("message",-1);if(id>0)jump(id,"analytics");}return;}
        if(request==SAVE_CONVERSATION_PDF_REQUEST){
            final String key=pendingPdfKey,name=pendingPdfName;pendingPdfKey=null;pendingPdfName=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&key!=null){
                final Uri destination=data.getData();
                showPdfStatus(key,"Создаю PDF переписки…");
                worker.execute(()->{
                    MessageDb source=new MessageDb(getApplicationContext(),key);
                    java.io.File temporary=null;
                    try{
                        temporary=java.io.File.createTempFile("conversation_pdf_",".tmp",getCacheDir());
                        int pages;
                        try(OutputStream out=new java.io.FileOutputStream(temporary)){
                            pages=ConversationPdfExporter.write(source,name,out);
                        }
                        try(InputStream in=new java.io.FileInputStream(temporary);
                            OutputStream out=getContentResolver().openOutputStream(destination,"w")){
                            if(out==null)throw new IOException("Нет доступа к файлу");
                            byte[] buffer=new byte[32768];int n;
                            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                            out.flush();
                        }
                        runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){showPdfStatus(key,"PDF сохранён · страниц: "+pages);toast("PDF сохранён");}});
                    }catch(Exception error){
                        // A document provider may return an existing document: never delete it on failure.
                        runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){showPdfStatus(key,"Не удалось сохранить PDF; проверь выбранный файл");toast("Ошибка PDF: "+error.getMessage());}});
                    }finally{source.close();if(temporary!=null)temporary.delete();}
                });
            }
            return;
        }
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
                        if(!(error instanceof MediaSaver.SameSourceException))
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
            String key=pendingCapsuleKey;pendingCapsuleKey=null;
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&key!=null&&key.equals(activeKey)&&!home)exportCapsule(data.getData(),password);
            else if(password!=null){java.util.Arrays.fill(password,(char)0);if(result==RESULT_OK)toast("Переписка изменилась. Повтори экспорт.");}
            return;
        }
        if(request==WRAPPED_PNG_REQUEST&&result==RESULT_OK&&data!=null&&data.getData()!=null){exportWrappedTo(data.getData());return;}
        if((request==60||request==61||request==62)&&result==RESULT_OK&&data!=null&&data.getData()!=null){applyPersonalImage(data.getData(),request);return;}
        if(request==44&&result==RESULT_OK&&data!=null&&data.getData()!=null){dryRunSelected(data.getData());return;}
        if(request==43&&result==RESULT_OK&&data!=null&&data.getData()!=null){verifyBackup(data.getData());return;}
        if(request==41&&(result!=RESULT_OK||data==null||data.getData()==null)){
            if(backupPassword!=null)java.util.Arrays.fill(backupPassword,(char)0);
            backupPassword=null;selectedBackupName="";
            return;
        }
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
            worker.execute(()->{savedStore.add("",label,uri.toString(),mime);runOnUiThread(()->{if("saved".equals(screen)&&!isFinishing())renderSaved();});});return;
        }
        if(request==64&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri selected=data.getData();
            String name=displayDocumentName(selected);
            showReadOnlyPreview(selected,name);return;
        }
        if(request==57&&result==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri source=data.getData();String name="Выбранный файл";
            try(android.database.Cursor c=getContentResolver().query(source,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(c!=null&&c.moveToFirst())name=c.getString(0);
            }catch(Exception ignored){}
            String low=name==null?"":name.toLowerCase(Locale.ROOT);
            final String displayName=name;
            if(low.endsWith(".html")||low.endsWith(".htm")){
                new FontDialog.Builder(this).setTitle("Импорт HTML Telegram")
                    .setMessage("Сообщения откроются как обычная переписка. Для медиа выбери полный экспорт Telegram.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Импортировать",(d,w)->importSingleHtml(source,displayName)).show();
                return;
            }
            // Android file managers may call a 7z archive .zip (or omit extension).
            // Read only the magic bytes off the UI thread, then import without
            // ever showing a password prompt for unencrypted archives.
            beginImportProgress("Проверяю выбранный архив…");
            worker.execute(()->{
                try{
                    String format=ArchiveFileImport.detectFormat(this,source,displayName);
                    runOnUiThread(()->importCompressed(source,format,displayName,null));
                }catch(Exception error){
                    runOnUiThread(()->{
                        endImportProgress();
                        new FontDialog.Builder(this)
                        .setTitle("Этот файл не является поддерживаемым архивом")
                        .setMessage(error.getMessage()==null?"Выбери ZIP, 7z или RAR":error.getMessage())
                        .setPositiveButton("Закрыть",null).show();
                    });
                }
            });
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
    /** Index a standalone Telegram HTML without replacing another chat or inventing media. */
    /** Only actual extraction/import stages; no fabricated percent completed. */
    private void beginImportProgress(String message){
        if(importProgressPanel!=null)importProgressPanel.setVisibility(View.VISIBLE);
        updateImportProgress(message);
    }
    private void updateImportProgress(String message){
        if(importProgressText!=null)importProgressText.setText(message);
        if(status!=null){status.setVisibility(View.VISIBLE);status.setText(message);}
    }
    private void postImportProgress(String message){
        runOnUiThread(()->{if(importing&&!isFinishing()&&!isDestroyed())updateImportProgress(message);});
    }
    private void endImportProgress(){
        if(importProgressPanel!=null)importProgressPanel.setVisibility(View.GONE);
    }
    private void discardUnpublishedImport(String key){
        // Only this operation\u0027s generated private directory/database, never the source URI.
        ArchiveFileImport.deleteTree(new java.io.File(new java.io.File(getFilesDir(),"telegram_imports"),key));
        deleteDatabase("telegram_"+key+".db");
    }
    private void importSingleHtml(Uri source,String label){
        if(importing||restoringBackup)return;
        importing=true;
        if(folderButton!=null)folderButton.setEnabled(false);
        beginImportProgress("Читаю HTML и создаю переписку…");
        final String key=UUID.randomUUID().toString().replace("-","");
        final java.io.File root=new java.io.File(new java.io.File(getFilesDir(),"telegram_imports"),key);
        worker.execute(()->{
            try{
                if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Не удалось создать папку");
                java.io.File html=new java.io.File(root,"messages.html");
                try(InputStream input=getContentResolver().openInputStream(source);
                    OutputStream output=new java.io.FileOutputStream(html)){
                    if(input==null)throw new IOException("Не удалось прочитать HTML");
                    byte[] buffer=new byte[65536];int count;long totalBytes=0;
                    while((count=input.read(buffer))!=-1){
                        totalBytes+=count;
                        if(totalBytes>1024L*1024*1024)throw new IOException("HTML больше 1 ГБ; импортируй исходную папку");
                        output.write(buffer,0,count);
                    }
                    if(totalBytes==0)throw new IOException("HTML-файл пуст");
                }
                MessageDb fresh=new MessageDb(this,key);
                try{
                    TelegramImporter.importFiles(getContentResolver(),
                        new DocumentFolder(getContentResolver(),root),fresh,
                        this::postImportProgress);
                    if(fresh.totalMessages()==0)throw new IOException("Не найдены сообщения Telegram в HTML");
                }finally{fresh.close();}
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){discardUnpublishedImport(key);return;}
                    importing=false;
                    endImportProgress();
                    if(folderButton!=null)folderButton.setEnabled(true);
                    try{
                        JSONObject entry=new JSONObject();
                        entry.put("key",key);entry.put("name",label);entry.put("tree","local:"+root.getAbsolutePath());
                        archives.put(entry);activateArchive(key);
                        status.setText("Переписка открыта · медиа из одиночного HTML недоступны");
                    }catch(Exception error){toast("Не удалось зарегистрировать чат: "+error.getMessage());}
                });
            }catch(Exception|OutOfMemoryError error){
                discardUnpublishedImport(key);
                runOnUiThread(()->{
                    importing=false;
                    endImportProgress();
                    if(folderButton!=null)folderButton.setEnabled(true);
                    status.setText("Импорт HTML не завершён");
                    new FontDialog.Builder(this).setTitle("HTML не импортирован")
                        .setMessage(error instanceof OutOfMemoryError?"Не хватает памяти для импорта. Попробуй меньший экспорт.":error.getMessage()).setPositiveButton("Закрыть",null).show();
                });
            }
        });
    }

    /** Only invoked AFTER the extractor confirms that a password is required. */
    private void confirmArchiveImport(Uri source,String ext,String displayName){
        EditText archivePassword=new EditText(this);
        archivePassword.setSingleLine(true);
        archivePassword.setSaveEnabled(false);
        archivePassword.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        archivePassword.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        archivePassword.setHint("Пароль от архива");
        archivePassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        archivePassword.setTextColor(FG);
        archivePassword.setHintTextColor(SUB);
        ViewerUi.styleInput(archivePassword,ViewerPalette.of(themeIndex));
        LinearLayout passwordForm=column();passwordForm.setPadding(px(20),px(10),px(20),px(6));
        passwordForm.addView(archivePassword,p(-1,-2));
        android.widget.CheckBox reveal=new android.widget.CheckBox(this);reveal.setText("Показать пароль");reveal.setTextColor(FG);
        reveal.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));passwordForm.addView(reveal,p(-1,-2));
        reveal.setOnCheckedChangeListener((box,checked)->{
            int cursor=archivePassword.getSelectionEnd();
            archivePassword.setTransformationMethod(checked?android.text.method.HideReturnsTransformationMethod.getInstance():android.text.method.PasswordTransformationMethod.getInstance());
            if(cursor>=0)archivePassword.setSelection(Math.min(cursor,archivePassword.length()));
        });
        android.app.AlertDialog passwordDialog=new FontDialog.Builder(this).setTitle("Этот архив защищён паролем")
            .setMessage("Введи пароль для открытия "+displayName+". Пароль не сохраняется.")
            .setView(passwordForm).setNegativeButton("Отмена",null)
            .setPositiveButton("Открыть",null).create();
        passwordDialog.setOnShowListener(ignored->{FontDialog.decorate(passwordDialog);passwordDialog.getButton(-1).setOnClickListener(v->{
                if(archivePassword.length()==0){archivePassword.setError("Введи пароль");return;}
                char[] password=new char[archivePassword.length()];
                archivePassword.getText().getChars(0,archivePassword.length(),password,0);
                archivePassword.getText().clear();
                passwordDialog.dismiss();
                importCompressed(source,ext,displayName,password);
            });});
        archivePasswordDialog=passwordDialog;
        passwordDialog.setOnDismissListener(d->{archivePassword.getText().clear();if(archivePasswordDialog==passwordDialog)archivePasswordDialog=null;});
        if(passwordDialog.getWindow()!=null)
            passwordDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        passwordDialog.show();
        if(passwordDialog.getWindow()!=null)
            passwordDialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void importCompressed(Uri source,String extension,String label,char[] password){
        if(importing||restoringBackup){if(password!=null)java.util.Arrays.fill(password,'\0');return;}
        importing=true;folderButton.setEnabled(false);
        beginImportProgress("Распаковываю «"+label+"»…");
        String key=UUID.randomUUID().toString().replace("-","");
        worker.execute(()->{
            boolean extracted=false;
            try{
                java.io.File root=ArchiveFileImport.extract(this,source,extension,key,password,
                    this::postImportProgress);
                extracted=true;
                postImportProgress("Архив распакован. Читаю сообщения Telegram…");
                MessageDb fresh=new MessageDb(this,key);
                try{
                    TelegramImporter.importFiles(getContentResolver(),
                        new DocumentFolder(getContentResolver(),root),fresh,this::postImportProgress);
                    if(fresh.totalMessages()==0)
                        throw new IOException("В архиве не найдены сообщения Telegram.");
                }finally{fresh.close();}
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){discardUnpublishedImport(key);return;}
                    importing=false;endImportProgress();folderButton.setEnabled(true);
                    try{
                        JSONObject o=new JSONObject();o.put("key",key);o.put("name",label);
                        o.put("tree","local:"+root.getAbsolutePath());archives.put(o);
                        activateArchive(key);status.setText("Импорт завершён");
                    }catch(Exception error){toast("Ошибка регистрации чата: "+error.getMessage());}
                });
            }catch(Exception|OutOfMemoryError error){
                // A failed password or corrupt archive must not publish partially indexed
                // messages or leave a failed import hidden in app-private storage.
                discardUnpublishedImport(key);
                String message=error instanceof OutOfMemoryError?"Не хватает памяти для импорта. Попробуй меньший экспорт.":error.getMessage()==null?"Файл не удалось распаковать":error.getMessage();
                boolean missing=password==null||password.length==0;
                // An entered wrong 7z key can surface as a generic CRC/decode
                // failure. Allow another password without guessing corruption.
                String lowerMessage=message.toLowerCase(Locale.ROOT);
                boolean unsupported=lowerMessage.contains("unsupported")||
                    lowerMessage.contains("not supported")||
                    lowerMessage.contains("не поддерж");
                // Unsupported compression and malformed files are not proof of
                // an incorrect password. Still let users enter one manually.
                final boolean contentMissing=error instanceof ArchiveFileImport.ExportNotFound;
                final boolean decoded=extracted||contentMissing;
                boolean auth=!decoded&&!unsupported&&((error instanceof Exception&&ArchiveFileImport.isPasswordFailure((Exception)error))
                    ||(!missing&&"7z".equalsIgnoreCase(extension)&&
                        (lowerMessage.contains("crc")||lowerMessage.contains("checksum"))));
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed())return;
                    importing=false;endImportProgress();folderButton.setEnabled(true);
                    status.setVisibility(View.VISIBLE);
                    status.setText("Импорт не завершён");
                    if(auth){
                        // Show a password entry only after the extractor has seen
                        // an encrypted entry/header, or rejected an entered key.
                        if(missing){confirmArchiveImport(source,extension,label);return;}
                        new FontDialog.Builder(this).setTitle("Пароль не подошёл")
                            .setMessage("Архив не открылся с этим паролем. Проверь раскладку, регистр и символы. Исходный файл не изменён.")
                            .setNegativeButton("Закрыть",null)
                            .setPositiveButton("Другой пароль",(d,w)->confirmArchiveImport(source,extension,label)).show();
                        return;
                    }
                    AlertDialog.Builder failure=new FontDialog.Builder(this)
                        .setTitle(decoded?"Архив распакован, но переписка не прочитана":"Не удалось распаковать архив")
                        .setMessage("Формат: "+extension.toUpperCase(Locale.ROOT)+"\nЭтап: "+(contentMissing?"поиск HTML переписки":decoded?"индексация HTML":"распаковка")+"\n"+message+"\nИсходный файл не изменён.")
                        .setNegativeButton("Закрыть",null)
                        .setNeutralButton("Повторить",(d,w)->importCompressed(source,extension,label,null));
                    if(!decoded)failure.setPositiveButton(missing?"Ввести пароль":"Другой пароль",(d,w)->confirmArchiveImport(source,extension,label));
                    failure.show();
                });
            }finally{if(password!=null)java.util.Arrays.fill(password,'\0');}
        });
    }
    private void importArchive(){
        if(folder==null||importing||restoringBackup||db==null)return;closeFloatingRound();stopPlayback();importing=true;folderButton.setEnabled(false);
        beginImportProgress("Индексирую локальную переписку…");
        list.removeAllViews();DocumentFolder current=folder;final MessageDb source=db;
        worker.execute(()->{
            String failure=null;
            try{TelegramImporter.importFiles(getContentResolver(),current,source,this::postImportProgress);info="Импорт завершён";}
            catch(Exception|OutOfMemoryError e){failure=e instanceof OutOfMemoryError?"Не хватает памяти для индексации":e.getMessage();if(failure==null||failure.trim().isEmpty())failure="Не удалось импортировать архив. Проверь файлы и повтори попытку.";info="Ошибка импорта: "+failure;}
            final String importFailure=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||source!=db)return;
                importing=false;endImportProgress();folderButton.setEnabled(true);status.setText(info);
                chatOffset=0;mediaOffset=0;searchOffset=0;favOffset=0;showPage();
                // showPage replaces the transient status immediately. Keep failures
                // visible so that users know the old archive was retained.
                if(importFailure!=null&&!isFinishing()&&!isDestroyed())
                    new FontDialog.Builder(this).setTitle("Импорт не завершён")
                        .setMessage(importFailure).setPositiveButton("Понятно",null).show();
            });
        });
    }
    private int getOffset(){switch(screen){case "media":return mediaOffset;case "search":return searchOffset;case "favorites":return favOffset;default:return chatOffset;}}
    private int getLimit(){return screen.equals("media")?MEDIA_PAGE:PAGE;}
    private void setOffset(int x){switch(screen){case "media":mediaOffset=x;break;case "search":searchOffset=x;break;case "favorites":favOffset=x;break;default:chatOffset=x;}}
    private void showPage(){
        // Every destination (cinema, chronology and combined timelines included)
        // must reset the notes' fixed bottom editor BEFORE early routing returns.
        // Otherwise switching from Saved can leave two bottom control rows stacked.
        if(savedComposer!=null&&!screen.equals("saved"))savedComposer.setVisibility(View.GONE);
        if(!"chat".equals(screen)&&list!=null)
            list.setPadding(px(3),px(6),px(3),px(14)); // do not inherit long-chat scrubber gutter
        // Floating video is chat-only; never leave a detached grey surface over other pages.
        if((home||!"chat".equals(screen))&&floatingPanel!=null)closeFloatingRound();
        if(mediaRecycler!=null){
            boolean showingGallery="media".equals(screen)&&!home;
            mediaRecycler.setVisibility(showingGallery?View.VISIBLE:View.GONE);
            scroll.setVisibility(showingGallery?View.GONE:View.VISIBLE);
            if(mediaFastScroller!=null)
                mediaFastScroller.setVisibility(showingGallery&&mediaTotal>1?View.VISIBLE:View.GONE);
            if(mediaFastLabel!=null)mediaFastLabel.setVisibility(View.GONE);
        }
        if("help".equals(screen)){renderHelpPage();return;}
        if("multi_timeline".equals(screen)){renderMultiTimelinePage();return;}
        if("dual_compare".equals(screen)||"dual_memories".equals(screen)){
            renderDualChatScreen();return;
        }
        if("relationships".equals(screen)){renderRelationshipsPage();return;}
        if("echo".equals(screen)){renderEchoPage();return;}
        if("chronology".equals(screen)){renderChronologyPage();return;}
        if("cinema".equals(screen)){renderCinemaPage();return;}
        if(scheduledPrefetch!=null){handler.removeCallbacks(scheduledPrefetch);scheduledPrefetch=null;}
        if(screen.equals("saved")){if(pdfButton!=null)pdfButton.setVisibility(View.GONE);renderSaved();return;}
        bottomNav.setVisibility(View.GONE);
        if(importing)return;if(floatingPanel==null)stopPlayback();refreshTabs();configureBars();int token=++loadToken;renderPinnedBar();
        loadingMore=false; // A stale asynchronous page must not block the new tab.
        if(home){renderHome();return;}
        // Do not show the old chat-list contents under the new conversation header
        // while the SQLite worker is loading the first message page.
        if("home-list".equals(list.getTag())){
            list.removeAllViews();
            list.setTag(null);list.setBackgroundColor(BG);
            scroll.scrollTo(0,0);
        }
        final MessageDb source=db;final String archive=activeKey;
        if(source==null)return;
        final String tab=screen,kind=mediaKind,search=q,who=byAuthor;
        final int chatLimit=Math.max(PAGE,autoLoaded),searchLimit=Math.max(PAGE,searchLoaded);
        final int requested=getOffset();final boolean deepMode=deep,hasMedia=onlyMedia;
        if("media".equals(tab)){
            // Never expose the previous category/chat beneath the new filter.
            // Keep the requested source offset, but invalidate all old pages.
            mediaTotal=0;mediaLoaded=0;mediaWindowStart=0;
            mediaAppending=false;mediaPrepending=false;
            mediaFastScroller.setVisibility(View.GONE);
            mediaAdapter.reset();
        }
        backPage.setEnabled(false);nextPage.setEnabled(false);status.setVisibility(View.VISIBLE);status.setText("Загружаю…");
        worker.execute(()->{
            try{
                if("analytics".equals(tab)){
                    // Run SQLite scan off the UI thread. The load token drops stale results
                    // after switching chats/tabs, without ever writing to the database.
                    ChatAnalytics.Snapshot report=source.analytics();
                    runOnUiThread(()->{
                        if(!sameScreenRequest(source,archive,token))return;
                        lastAnalytics=report;list.removeAllViews();list.setBackgroundColor(BG);
                        renderAnalytics(report);
                        if(pendingAnalyticsScroll>=0){
                            final int restoreY=pendingAnalyticsScroll;pendingAnalyticsScroll=-1;
                            list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                                @Override public boolean onPreDraw(){
                                    if(list.getViewTreeObserver().isAlive())list.getViewTreeObserver().removeOnPreDrawListener(this);
                                    if(token==loadToken&&"analytics".equals(screen))scroll.scrollTo(0,restoreY);
                                    return true;
                                }
                            });
                        }else scroll.post(()->{if(token==loadToken&&"analytics".equals(screen))scroll.scrollTo(0,0);});
                        bottomPager.setVisibility(View.GONE);
                        verticalTimeline.setVisibility(View.GONE);
                        // The dashboard itself explains offline processing; no duplicate status banner.
                        status.setText("");status.setVisibility(View.GONE);
                    });
                    return;
                }
                boolean gallery="media".equals(tab),find="search".equals(tab),favs="favorites".equals(tab);
                int count=gallery?source.mediaCount(kind):source.count(find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                // Restore the actual source attachment position (including after
                // rotation), not always attachment zero. Filter changes reset
                // mediaOffset via their click handlers.
                int pos=count==0?0:Math.max(0,Math.min(requested,count-1));
                List<MessageDb.Attachment> attachments=gallery?
                    source.mediaPage(kind,pos,MediaGalleryState.PAGE_SIZE):null;
                List<MessageDb.Item> messages=gallery?null:source.page(pos,tab.equals("chat")?chatLimit:tab.equals("search")?searchLimit:PAGE,find?search:"",find&&deepMode,find&&deepMode?who:"",find&&deepMode&&hasMedia,favs);
                Map<Long,List<MessageDb.Attachment>> links=messages==null?new HashMap<>():source.attachmentsForMessages(messages);
                Map<Long,MessageDb.ReplyInfo> replies=messages==null?new HashMap<>():source.replyPreviews(messages);
                MessageDb.SearchStats stats=find?source.searchStats(search,deepMode,deepMode?who:"",deepMode&&hasMedia):null;
                runOnUiThread(()->{
                    if(!sameScreenRequest(source,archive,token))return;
                    total=count;setOffset(pos);list.removeAllViews();
                    if("chat".equals(tab)){
                        android.graphics.drawable.Drawable custom=PersonalImages.wallpaper(this,getPreferences(MODE_PRIVATE),activeKey,BG);
                        list.setBackground(custom!=null?custom:new ChatWallpaper(wallpaperIndex,wallpaperOpacity/100f,themeIndex==2));
                    }
                    else list.setBackgroundColor(BG);
                    if(gallery){
                        mediaWindowStart=pos;mediaLoaded=pos+attachments.size();mediaTotal=count;
                        mediaAppending=false;mediaPrepending=false;
                        mediaAdapter.reset();mediaAdapter.append(attachments);
                        updateMediaLayout(false);
                        mediaFastScroller.setProgress(pos,count);
                        mediaFastScroller.setVisibility(count>1?View.VISIBLE:View.GONE);
                        if(count==0){
                            mediaRecycler.setVisibility(View.GONE);
                            scroll.setVisibility(View.VISIBLE);
                            renderEmptyMediaState();
                        }else{
                            scroll.setVisibility(View.GONE);
                            mediaRecycler.setVisibility(View.VISIBLE);
                            mediaRecycler.post(this::maybeAppendMedia);
                        }
                        scheduleMediaPrefetch(token,kind,mediaLoaded,count);
                    }else {
                        scroll.setVisibility(View.VISIBLE);
                        mediaRecycler.setVisibility(View.GONE);
                        lastLoadedMessageId=messages.isEmpty()?-1:messages.get(messages.size()-1).id;
                        renderMessages(messages,links,replies,find,false);
                    }
                    animateEntrance();
                    if("search".equals(tab)&&pendingSearchScroll>=0){
                        final int restoreY=pendingSearchScroll;pendingSearchScroll=-1;
                        // Wait until the newly rendered results have their final measured height.
                        list.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
                            @Override public boolean onPreDraw(){
                                if(list.getViewTreeObserver().isAlive())list.getViewTreeObserver().removeOnPreDrawListener(this);
                                if(token==loadToken&&"search".equals(screen))scroll.scrollTo(0,restoreY);
                                return true;
                            }
                        });
                    }
                    // A pager with no items (or just one page) is dead UI.
                    // Keep it for galleries/favorites only when multiple pages exist.
                    bottomPager.setVisibility(favs&&count>getLimit()?View.VISIBLE:View.GONE);
                    int shown=gallery?attachments.size():messages.size();
                    // Keep the chat scrubber only for long histories, not a 14-message sample.
                    boolean hasScrubber="chat".equals(tab)&&count>120;
                    verticalTimeline.setVisibility(hasScrubber?View.VISIBLE:View.GONE);
                    // The scrubber is an overlay at the right edge. Reserve a real
                    // message gutter or timestamps and bubble actions fall underneath it.
                    list.setPadding(px(3),px(6),hasScrubber?px(27):px(3),px(14));
                    if("chat".equals(tab))verticalTimeline.setPosition(pos,count);
                    pageLabel.setText(count==0?"Нет данных":(pos+1)+"–"+(pos+shown)+" / "+count);
                    backPage.setEnabled(pos>0);nextPage.setEnabled(pos+shown<count);
                    if(gallery&&filterBar.getChildCount()>1)
                        filterBar.getChildAt(1).setVisibility(count==0?View.GONE:View.VISIBLE);
                    if(count>0&&"chat".equals(tab)){
                        status.setText("");status.setVisibility(View.GONE);
                    }else status.setVisibility(View.VISIBLE);
                    if(count==0){
                        if("favorites".equals(tab)){
                            emptyWorkspace(list,"Нет избранных сообщений","Удерживай сообщение в переписке и добавь его в избранное.","Заметки");
                        }
                        status.setText(gallery?"": "chat".equals(tab)?
                            "В этой переписке пока нет сообщений.":
                            "favorites".equals(tab)?
                            "":
                            "Ничего не найдено — попробуй изменить запрос или фильтры.");
                    }
                    else if(!"chat".equals(tab))status.setText("media".equals(tab)?
                            (mediaLoaded<count?"Показано "+mediaLoaded+" из "+count+" · листай вниз":
                                "Медиафайлов: "+count):
                            "search".equals(tab)?(search.isEmpty()?"Введите слово или фразу":("Сообщений: "+count+"  ·  Точных вхождений «"+clip(search,28)+"»: "+stats.literalOccurrences+(deepMode?"":"  ·  Быстрый поиск учитывает начала слов"))):
                            "favorites".equals(tab)?"Твои избранные сообщения": "История · удерживай сообщение для действий");
                });
            }catch(Exception e){runOnUiThread(()->{if(sameScreenRequest(source,archive,token))status.setText("Не удалось прочитать базу: "+e.getMessage());});}
        });
    }
    /** Analytics is a read-only, anonymous-by-default view of the current chat DB. */
    private void addAnalyticsMetric(LinearLayout parent,String caption,long value){
        LinearLayout card=column();card.setGravity(Gravity.CENTER);
        card.setPadding(px(6),px(13),px(6),px(11));
        card.setBackground(outlined(PANEL,15,false));
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,px(75),1f);
        size.setMargins(px(3),0,px(3),0);parent.addView(card,size);
        TextView number=text(String.format(Locale.ROOT,"%,d",value).replace(',',' '),21,FG);
        number.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        number.setGravity(Gravity.CENTER);number.setSingleLine(true);
        number.setAutoSizeTextTypeUniformWithConfiguration(13,21,1,
            android.util.TypedValue.COMPLEX_UNIT_SP);
        card.addView(number,p(-1,32));
        TextView label=text(caption,11,SUB);
        label.setGravity(Gravity.CENTER);label.setSingleLine(true);
        card.addView(label,p(-1,18));
        card.setContentDescription(caption+": "+value);
    }
    private String friendlyAnalyticsDate(String date){
        try{
            ChatTrends.Moment parsed=ChatTrends.parse(date);
            if(parsed!=null)return parsed.day.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy",Locale.forLanguageTag("ru-RU")));
        }catch(Exception ignored){}
        return date==null?"":date;
    }
    /** Minimal local navigation: do not render every heavy graph when the screen opens. */
    private void selectAnalyticsSection(String next){
        if(next.equals(analyticsSection))return;
        analyticsSection=next;
        if("analytics".equals(screen)&&lastAnalytics!=null){
            final ChatAnalytics.Snapshot report=lastAnalytics;
            list.removeAllViews();
            renderAnalytics(report);
            scroll.post(()->{if("analytics".equals(screen)&&next.equals(analyticsSection))scroll.scrollTo(0,0);});
        }
    }
    private TextView secondaryDisclosure(String label){
        TextView action=text(label,13,ACCENT);
        action.setGravity(Gravity.CENTER_VERTICAL);
        action.setPadding(px(12),px(8),px(12),px(8));
        action.setBackground(new RippleDrawable(ColorStateList.valueOf(0x297ad2ff),
            outlined(PANEL,12,false),null));
        return action;
    }
    private void analyticsNavChip(LinearLayout row,String label,String section){
        boolean selected=section.equals(analyticsSection);
        // Compact visible labels fit on a 360dp phone; accessibility keeps the full names.
        String shortLabel="Участники".equals(label)?"Люди":"Медиа и слова".equals(label)?"Контент":label;
        TextView chip=text(shortLabel,12,selected?FG:SUB);
        chip.setTypeface(Typeface.create("sans-serif-medium",selected?Typeface.BOLD:Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setBackground(new RippleDrawable(
            ColorStateList.valueOf(themeIndex==2?0x2279a8ce:0x447ad2ff),
            selected?outlined(CHIP,11,true):outlined(PANEL,11,false),
            shape(0xffffffff,11)));
        chip.setContentDescription("Раздел анализа: "+label);
        chip.setSelected(selected);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,px(48),1f);
        params.setMargins(px(2),px(2),px(2),px(2));
        row.addView(chip,params);
        chip.setOnClickListener(v->selectAnalyticsSection(section));
    }
    private void analyticsSectionHeading(String label,String hint){
        TextView heading=text(label,18,FG);
        heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        heading.setPadding(px(11),px(13),px(10),px(4));
        list.addView(heading,p(-1,-2));
        TextView subtitle=text(hint,12,SUB);
        subtitle.setPadding(px(11),0,px(11),px(8));
        list.addView(subtitle,p(-1,-2));
    }
    private void showChatAnalyst(){
        if(home||!"analytics".equals(screen)||db==null||importing)return;
        final MessageDb source=db;final String archive=activeKey;final int token=loadToken;
        final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean(false);
        LinearLayout panel=column();panel.setPadding(px(16),px(8),px(16),px(12));
        analyticsText(panel,"Только этот чат · локальные ответы по фактам",SUB,12);
        EditText question=new EditText(this);ViewerUi.styleInput(question,ViewerPalette.of(themeIndex));
        question.setHint("Кто первый написал?");question.setContentDescription("Вопрос о переписке");
        question.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        question.setMaxLines(3);panel.addView(question,p(-1,-2));
        Button ask=btn("Спросить");panel.addView(ask,p(-1,48));
        LinearLayout result=column();panel.addView(result,p(-1,-2));
        analyticsText(result,"Задай свой вопрос или выбери готовый ниже.",SUB,13);
        Button suggestions=btn("Готовые вопросы  ▾");panel.addView(suggestions,p(-1,44));
        LinearLayout categories=column();panel.addView(categories,p(-1,-2));
        int[] colors=themeIndex==2?new int[]{0xffdce9ff,0xffd9f0e5,0xffffe9cf,0xffebdfff}:
            new int[]{0xff253c60,0xff244c40,0xff51402b,0xff49365d};
        for(int i=0;i<ChatQuestionAnalyst.PRESETS.length;i++){
            String[] group=ChatQuestionAnalyst.PRESETS[i];
            LinearLayout card=column();card.setPadding(px(10),px(6),px(10),px(8));
            card.setBackground(ViewerUi.surface(this,colors[i],ViewerUi.CARD,themeIndex==2,false));
            LinearLayout.LayoutParams gap=p(-1,-2);gap.topMargin=px(7);categories.addView(card,gap);
            TextView heading=text(group[0],15,FG);heading.setTypeface(null,Typeface.BOLD);card.addView(heading,p(-1,-2));
            for(int j=1;j<group.length;j++){
                final String preset=group[j];
                TextView chip=text(preset,14,FG);chip.setPadding(px(8),px(10),px(8),px(10));
                chip.setMinHeight(px(44));chip.setGravity(Gravity.CENTER_VERTICAL);
                chip.setBackground(ViewerUi.control(this,colors[i],ViewerUi.CONTROL,themeIndex==2));
                chip.setContentDescription("Готовый вопрос: "+preset);
                chip.setFocusable(true);card.addView(chip,p(-1,-2));
                chip.setOnClickListener(v->{if(!ask.isEnabled())return;question.setText(preset);question.setSelection(question.length());
                    if(preset.contains("«встреча»")){
                        int start=preset.indexOf("встреча");question.requestFocus();question.setSelection(start,start+"встреча".length());
                    }else{ask.performClick();}
                });
            }
        }
        suggestions.setOnClickListener(v->{boolean open=categories.getVisibility()!=View.VISIBLE;
            categories.setVisibility(open?View.VISIBLE:View.GONE);suggestions.setText(open?"Готовые вопросы  ▾":"Готовые вопросы  ›");});
        ScrollView viewport=new ScrollView(this);viewport.addView(panel);
        AlertDialog dialog=new FontDialog.Builder(this).setTitle("Аналитик переписки")
            .setView(viewport).setNegativeButton("Закрыть",null).create();
        dialog.setOnDismissListener(d->cancelled.set(true));
        Button deepModel=btn("Глубокий анализ · локальная ИИ");panel.addView(deepModel,1,p(-1,48));
        deepModel.setOnClickListener(v->{if(!sameScreenRequest(source,archive,token))return;
            String initial=question.getText().toString();dialog.dismiss();
            startActivityForResult(new Intent(this,DeepAnalystActivity.class).putExtra("archive",archive).putExtra("question",initial),119);
        });
        ask.setOnClickListener(v->{
            if(!dialog.isShowing()||!sameScreenRequest(source,archive,token))return;
            ChatQuestionAnalyst.Query query=ChatQuestionAnalyst.parse(question.getText().toString());
            categories.setVisibility(View.GONE);suggestions.setText("Готовые вопросы  ›");
            android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(ime!=null)ime.hideSoftInputFromWindow(question.getWindowToken(),0);
            ask.setEnabled(false);result.removeAllViews();analyticsText(result,"Читаю локальную переписку…",SUB,13);
            worker.execute(()->{
                try{
                    ChatQuestionAnalyst.Answer answer=source.askChat(query,cancelled);
                    runOnUiThread(()->{
                        if(answer==null||cancelled.get()||!dialog.isShowing()||!sameScreenRequest(source,archive,token))return;
                        ask.setEnabled(true);result.removeAllViews();analyticsText(result,answer.text,FG,15);
                        for(ChatQuestionAnalyst.Evidence evidence:answer.sources){
                            TextView link=text(evidence.author+" · "+evidence.date+"\n"+evidence.excerpt+"\nОткрыть сообщение ›",13,FG);
                            link.setPadding(px(10),px(8),px(10),px(8));
                            link.setBackground(ViewerUi.control(this,PANEL,ViewerUi.CONTROL,themeIndex==2));
                            LinearLayout.LayoutParams lp=p(-1,-2);lp.topMargin=px(6);result.addView(link,lp);
                            link.setFocusable(true);link.setOnClickListener(t->{
                                if(!sameScreenRequest(source,archive,token))return;
                                dialog.dismiss();jump(evidence.id,"analytics");
                            });
                        }
                    });
                }catch(Exception error){
                    runOnUiThread(()->{if(cancelled.get()||!dialog.isShowing()||!sameScreenRequest(source,archive,token))return;
                        ask.setEnabled(true);result.removeAllViews();analyticsText(result,"Не удалось прочитать переписку. Попробуй ещё раз.",SUB,13);});
                }
            });
        });
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        viewport.setFocusableInTouchMode(true);viewport.requestFocus();
    }

    private void renderAnalytics(ChatAnalytics.Snapshot r){
        // The app bar already identifies the chat and says the analysis is offline.
        // No second hero/title: one concise, always-visible tab row, then content.
        LinearLayout navigation=row();
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(px(4),px(2),px(4),px(2));
        list.addView(navigation,p(-1,56));
        analyticsNavChip(navigation,"Обзор","overview");
        analyticsNavChip(navigation,"Графики","trends");
        analyticsNavChip(navigation,"Ритм","rhythm");
        analyticsNavChip(navigation,"Участники","people");
        analyticsNavChip(navigation,"Медиа и слова","content");
        if(r.totalMessages==0){
            LinearLayout empty=analyticsCard("Нет сообщений для анализа");
            analyticsText(empty,"Импортируй переписку — здесь появятся её показатели, графики и медиатека.",SUB,13);
            return;
        }
        if("trends".equals(analyticsSection)){
            analyticsSectionHeading("Динамика сообщений","Дни, недели, месяцы, годы — с точными значениями.");
            renderTimeAnalytics(r);
            return;
        }
        if("rhythm".equals(analyticsSection)){
            analyticsSectionHeading("Когда вы общаетесь","Время, календарь и распределение по дням недели.");
            renderTimeAnalytics(r);
            return;
        }
        if("people".equals(analyticsSection)){
            analyticsSectionHeading("Участники переписки","Вклад каждого отправителя и сравнение активности.");
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
                analyticsText(people,"Слов: "+person.words+"  ·  вложений: "+person.attachments,SUB,12);
                Button morePerson=btn("Подробности  ▾");
                morePerson.setContentDescription("Подробнее об участнике "+person.display);
                LinearLayout.LayoutParams moreParams=p(-1,38);
                moreParams.setMargins(px(4),px(1),px(4),px(11));people.addView(morePerson,moreParams);
                LinearLayout details=column();details.setPadding(px(8),px(2),px(6),px(10));
                details.setVisibility(View.GONE);people.addView(details,p(-1,-2));
                morePerson.setOnClickListener(v->{
                    boolean open=details.getVisibility()!=View.VISIBLE;
                    if(open&&details.getChildCount()==0){
                        analyticsText(details,"Слов: "+person.words+" ("+
                            fmtPct(ChatAnalytics.percent(person.words,r.totalWords))+"%)"+
                            " · символов: "+person.characters,SUB,12);
                        analyticsText(details,"Вложений: "+person.attachments+" ("+
                            fmtPct(ChatAnalytics.percent(person.attachments,r.totalMedia))+"%)",SUB,12);
                        String[] keys={"image","round","voice","video","gif","file","audio","sticker"};
                        String[] names={"Фото","Кружки","Голосовые","Видео","GIF","Файлы","Музыка","Стикеры"};
                        for(int type=0;type<keys.length;type++)
                            if(person.kind(keys[type])>0)analyticsLine(details,names[type],person.kind(keys[type]));
                        if(person.attachments==0)analyticsText(details,"Вложений пока нет",SUB,12);
                    }
                    details.setVisibility(open?View.VISIBLE:View.GONE);
                    morePerson.setText(open?"Свернуть подробности  ▴":"Подробности  ▾");
                });
            }
            if(r.people.size()>35)analyticsText(people,"Показаны первые 35 участников из "+r.people.size()+". В общих итогах учтены все.",SUB,12);
            LinearLayout response=analyticsCard("Интервал между сообщениями участников");
            if(r.switchCount>0){
                analyticsText(response,"Средний интервал при смене отправителя: "+
                    fmtPct(r.meanSwitchMinutes)+" минут · пар сообщений: "+r.switchCount,FG,14);
                analyticsText(response,"Учитываются соседние сообщения разных отправителей с датой и перерывом до 24 часов. Это приблизительная оценка темпа общения, а не подтверждённое время ответа на конкретное сообщение. В группах точность особенно ограничена.",SUB,11);
            }else analyticsText(response,"Не удалось собрать подходящие пары сообщений.",SUB,12);
            renderParticipantComparison(r);
            return;
        }
        if("content".equals(analyticsSection)){
            analyticsSectionHeading("Слова и вложения","Частота слов и количество вложений по типам.");
        LinearLayout media=analyticsCard("Медиатека по типам");
        String[] kinds={"image","video","round","voice","audio","gif","sticker","file"};
        String[] titles={"Фото","Обычные видео","Видеосообщения","Голосовые","Музыка/аудио","GIF","Стикеры","Файлы"};
        if(r.totalMedia==0){
            analyticsText(media,"В этой переписке пока нет медиа и файлов.",SUB,13);
        }else{
            long classified=0;
            for(int i=0;i<kinds.length;i++){
                long count=r.type(kinds[i]);
                if(count>0){analyticsLine(media,titles[i],count);classified+=count;}
            }
            if(classified<r.totalMedia)analyticsLine(media,"Другие вложения",r.totalMedia-classified);
            analyticsLine(media,"Всего вложений",r.totalMedia);
        }
        LinearLayout terms=analyticsCard("Самые частые слова");
        TextView toggle=text(analyticsCommonWords?
            "Все слова · выбрать значимые  ▾":"Значимые слова · показать все  ▾",12,ACCENT);
        toggle.setContentDescription(analyticsCommonWords?
            "Показаны все слова. Нажми, чтобы скрыть служебные и числовые фрагменты":
            "Показаны значимые слова. Нажми, чтобы увидеть все слова");
        toggle.setPadding(px(4),px(6),px(4),px(7));terms.addView(toggle);
        toggle.setOnClickListener(v->{analyticsCommonWords=!analyticsCommonWords;if(lastAnalytics==r)refreshAnalyticsView();});
        if(r.limitedVocabulary)analyticsText(terms,"Очень большой словарь: редкие новые слова могли не учитываться в рейтинге. Итоги сообщений, слов и медиа полные.",SUB,12);
        List<ChatAnalytics.WordCount> words=analyticsCommonWords?r.topWords:r.topMeaningfulWords;
        if(words.isEmpty()){
            analyticsText(terms,"Пока нет подходящих слов. Можно переключить фильтр выше.",SUB,13);
        }else{
            analyticsText(terms,"Нажми на слово, чтобы найти его в переписке. Полоски — относительно самого частого.",SUB,11);
            LinearLayout wordRows=column();terms.addView(wordRows,p(-1,-2));
            final boolean[] expanded={false};
            final long peak=Math.max(1L,words.get(0).count);
            Runnable draw=()->{
                wordRows.removeAllViews();
                int count=expanded[0]?words.size():Math.min(6,words.size());
                for(int i=0;i<count;i++)renderAnalyticsWordRow(wordRows,words.get(i),peak);
            };
            draw.run();
            if(words.size()>6){
                Button showMore=btn("Ещё "+(words.size()-6)+" слов  ▾");
                LinearLayout.LayoutParams more=p(-1,41);more.setMargins(px(4),px(5),px(4),px(2));
                terms.addView(showMore,more);
                showMore.setOnClickListener(v->{
                    expanded[0]=!expanded[0];draw.run();
                    showMore.setText(expanded[0]?"Свернуть слова  ▴":"Ещё "+(words.size()-6)+" слов  ▾");
                });
            }
        }
            return;
        }
        Button ask=btn("Задать вопрос о переписке");
        list.addView(ask,p(-1,48));ask.setOnClickListener(v->showChatAnalyst());
        analyticsSectionHeading("Главное о переписке","Короткая сводка без длинных таблиц и графиков.");
        LinearLayout glance=row();glance.setGravity(Gravity.TOP);
        glance.setPadding(px(5),0,px(5),px(6));list.addView(glance,p(-1,-2));
        addAnalyticsMetric(glance,"Сообщений",r.totalMessages);
        addAnalyticsMetric(glance,"Людей",r.people.size());
        addAnalyticsMetric(glance,"Медиа",r.totalMedia);

        LinearLayout snapshot=analyticsCard("Сводка");
        analyticsLine(snapshot,"Слов",r.totalWords);
        analyticsLine(snapshot,"Активных дней",r.days.size());
        if(!r.firstDay.isEmpty())
            analyticsText(snapshot,"Период: "+friendlyAnalyticsDate(r.firstDay)+" — "+friendlyAnalyticsDate(r.lastDay),SUB,12);
        analyticsLine(snapshot,"Символов (Unicode)",r.totalCharacters);

        if(r.trends!=null&&!r.trends.all.days.isEmpty()){
            TextView openYear=secondaryDisclosure("✦ Итоги выбранного года  ▾");
            LinearLayout.LayoutParams yearButton=p(-1,47);
            yearButton.setMargins(px(5),px(8),px(5),px(5));
            list.addView(openYear,yearButton);
            LinearLayout yearDetails=column();
            yearDetails.setVisibility(analyticsYearExpanded?View.VISIBLE:View.GONE);
            list.addView(yearDetails,p(-1,-2));
            if(analyticsYearExpanded){
                renderWrappedCard(r,yearDetails);
                openYear.setText("✦ Свернуть итоги года  ▴");
            }
            openYear.setOnClickListener(v->{
                boolean opening=yearDetails.getVisibility()!=View.VISIBLE;
                analyticsYearExpanded=opening;
                if(opening&&yearDetails.getChildCount()==0)renderWrappedCard(r,yearDetails);
                yearDetails.setVisibility(opening?View.VISIBLE:View.GONE);
                openYear.setText(opening?"✦ Свернуть итоги года  ▴":"✦ Итоги выбранного года  ▾");
            });
        }
        TextView methodology=secondaryDisclosure("Как рассчитана статистика  ▾");
        LinearLayout.LayoutParams explainButton=p(-1,43);
        explainButton.setMargins(px(6),px(12),px(6),px(4));
        list.addView(methodology,explainButton);
        LinearLayout about=analyticsCard("Методика расчёта");
        about.setVisibility(View.GONE);
        methodology.setOnClickListener(v->{
            boolean show=about.getVisibility()!=View.VISIBLE;
            about.setVisibility(show?View.VISIBLE:View.GONE);
            methodology.setText(show?"Скрыть методику  ▴":"Как рассчитана статистика  ▾");
        });
        analyticsText(about,"Отправители объединяются без учёта регистра и лишних пробелов. Каждое вложение считается отдельно; одно сообщение может содержать несколько файлов. Слова считаются по Unicode-буквам и цифрам. Системные сообщения из старых импортов могут иметь ошибочно назначенного автора — для уточнения нужен повторный импорт HTML.",SUB,12);
        analyticsText(about,"Число медиа основано на индексированных ссылках HTML: отсутствующий на диске файл тоже учитывается. Получатель в группе не определяется только по имени отправителя. Нажатие на слово открывает поиск.",SUB,12);
    }
    private void renderWrappedCard(ChatAnalytics.Snapshot report,LinearLayout destination){
        if(report.trends==null||report.trends.all.days.isEmpty())return;
        int latest=ChatTrends.latestYear(report.trends.all);
        int first=ChatTrends.earliestYear(report.trends.all);
        if(analyticsCalendarYear<first||analyticsCalendarYear>latest)analyticsCalendarYear=latest;
        WrappedSummary wrap=WrappedSummary.create(report.trends,analyticsCalendarYear);
        LinearLayout card=analyticsCard("✦ Итоги года",destination);
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
        boolean graphs="trends".equals(analyticsSection);
        LinearLayout controls=analyticsCard(graphs?"Графики активности":"Настройки времени");
        TextView info=secondaryDisclosure("Как учитывается время  ›");controls.addView(info,p(-1,44));
        info.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Время сообщений").setMessage("Время берётся из экспорта Telegram без пересчёта часового пояса.").setPositiveButton("Понятно",null).show());
        if(graphs){
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
        if(graphs){
        ChatTrends.Series values="year".equals(analyticsUnit)?ChatTrends.allTimeYears(activity):ChatTrends.series(activity,analyticsUnit);
        if(values.values.length==0){analyticsText(controls,"Нет сообщений с распознанной датой.",SUB,13);}
        else {
            analyticsBarChart(controls,values.labels,values.details,values.values,
                analyticsUnit.equals("day")?43:analyticsUnit.equals("week")?50:analyticsUnit.equals("month")?56:68,108);
            if(values.clipped)analyticsText(controls,"Показаны последние "+values.values.length+" периодов; более ранняя история скрыта только на графике.",SUB,11);
            analyticsText(controls,"Сообщений на показанном участке: "+values.visibleCount+" · точные значения указаны рядом с периодами.",SUB,11);
        }
        if(!"year".equals(analyticsUnit)){
        LinearLayout entire=analyticsCard("Вся история переписки · по годам");
        
        ChatTrends.Series fullHistory=ChatTrends.allTimeYears(activity);
        if(fullHistory.values.length>0){
            analyticsBarChart(entire,fullHistory.labels,fullHistory.details,fullHistory.values,38,120);
            analyticsText(entire,"Всего сообщений с распознанной датой: "+fullHistory.visibleCount,SUB,12);
        }
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
            
        }
            return;
        }
        if(!activity.days.isEmpty()){
            LinearLayout days=analyticsCard("Самые активные дни");
            ArrayList<Map.Entry<LocalDate,Long>> ranked=new ArrayList<>(activity.days.entrySet());
            ranked.sort((a,b)->{int diff=Long.compare(b.getValue(),a.getValue());return diff!=0?diff:a.getKey().compareTo(b.getKey());});
            for(int i=0;i<Math.min(8,ranked.size());i++){
                Map.Entry<LocalDate,Long> day=ranked.get(i);
                if(day.getValue()<=0)continue;
                analyticsLine(days,day.getKey().toString(),day.getValue());
            }
        }
        LinearLayout hours=analyticsCard("Когда пишут · 00:00–24:00");
        
        ArrayList<String> hLabels=new ArrayList<>(),hFull=new ArrayList<>();
        for(int i=0;i<24;i++){hLabels.add(i%3==0?String.format(Locale.ROOT,"%02d",i):"·");
            hFull.add(String.format(Locale.ROOT,"%02d:00–%02d:59",i,i));}
        analyticsBarChart(hours,hLabels,hFull,activity.hours,21,110);
        
        int busiest=activity.busiestHour();
        if(busiest>=0)analyticsText(hours,"Пик: "+String.format(Locale.ROOT,"%02d:00–%02d:59",busiest,busiest)+
            " · "+activity.hours[busiest]+" сообщений",ACCENT,13);
        if(activity.validHour==0)analyticsText(hours,"Нет сообщений с распознанным часом.",SUB,12);
        LinearLayout heat=analyticsCard("Тепловая карта · день недели × час");
        analyticsText(heat,AnalyticsDisplayCopy.HEATMAP_DESCRIPTION,SUB,12);
        analyticsHeatmap(heat,activity);
        renderAnnualCalendar(activity);
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
        analyticsText(parent,"Сообщения · шкала 0–"+max,SUB,12);
        for(int i=0;i<values.length;i++){
            final long count=values[i];final String detail=details.get(i);
            LinearLayout block=column();block.setPadding(px(4),px(5),px(4),px(5));parent.addView(block,p(-1,-2));
            TextView label=text(detail+"  ·  "+count,13,FG);label.setTextIsSelectable(true);
            label.setContentDescription(detail+": "+count+" сообщений");block.addView(label,p(-1,-2));
            FrameLayout track=new FrameLayout(this);track.setBackground(shape(CHIP,4));
            block.addView(track,p(-1,8));
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
            "Время — как в экспорте, без пересчёта пояса. " +
            "В каждой клетке указано ЧИСЛО сообщений, а не цветовой балл. " +
            "Для отдельных часов используй график «Когда пишут» выше.",SUB,12);
        long[][] counts=new long[7][6];long max=0;
        for(int day=0;day<7;day++)for(int block=0;block<6;block++){
            for(int hour=block*4;hour<block*4+4;hour++)counts[day][block]+=activity.weekdayHours[day][hour];
            max=Math.max(max,counts[day][block]);
        }
        LinearLayout header=row();parent.addView(header,p(-1,40));
        TextView corner=text("День",10,SUB);header.addView(corner,new LinearLayout.LayoutParams(0,-1,0.9f));
        for(int block=0;block<6;block++){
            int begin=block*4;
            TextView hour=text(String.format(Locale.ROOT,"%02d–\n%02d",begin,begin+3),10,FG);
            hour.setContentDescription(String.format(Locale.ROOT,"%02d:00–%02d:59",begin,begin+3));
            hour.setGravity(Gravity.CENTER);header.addView(hour,new LinearLayout.LayoutParams(0,-1,1f));
        }
        for(int day=0;day<7;day++){
            LinearLayout line=row();parent.addView(line,p(-1,47));
            TextView weekday=text(ChatTrends.WEEKDAYS[day],12,SUB);
            weekday.setGravity(Gravity.CENTER_VERTICAL);line.addView(weekday,new LinearLayout.LayoutParams(0,-1,0.9f));
            for(int block=0;block<6;block++){
                final int d=day,b=block;final long amount=counts[day][block];
                int heatBackground=analyticsHeatColor(max==0?0:(float)amount/max);
                TextView square=text(analyticsShortCount(amount),11,
                    AnalyticsContrastPolicy.readableTextColor(heatBackground,Color.BLACK,Color.WHITE));
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
        TextView current=text(String.valueOf(year),19,FG);current.setGravity(Gravity.CENTER);navigation.addView(current,p(75,45));
        Button next=btn((year+1)+" ›");navigation.addView(next,new LinearLayout.LayoutParams(0,px(45),1));
        next.setEnabled(year<latest);next.setOnClickListener(v->{analyticsCalendarYear=year+1;refreshAnalyticsView();});
        analyticsText(container,"Каждый день: верхнее число — дата, нижнее — сообщений. «—» означает 0. " +
            "Цвет дополнительный, данные читаются и без него. Выбери всех участников и нажми активный день, чтобы открыть первое сообщение.",SUB,12);
        String[] weekdays={"Пн","Вт","Ср","Чт","Пт","Сб","Вс"};
        for(int month=1;month<=12;month++){
            LocalDate first=LocalDate.of(year,month,1);
            long monthSum=0;
            for(int day=1;day<=first.lengthOfMonth();day++)monthSum+=activity.days.getOrDefault(first.withDayOfMonth(day),0L);
            TextView heading=text(first.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE,
                Locale.forLanguageTag("ru-RU"))+" · "+monthSum+" сообщений",14,FG);
            heading.setTypeface(null,Typeface.BOLD);heading.setPadding(px(5),px(16),0,px(6));container.addView(heading);
            LinearLayout header=row();container.addView(header,p(-1,25));
            for(String dayName:weekdays){TextView cell=text(dayName,11,SUB);cell.setGravity(Gravity.CENTER);
                header.addView(cell,new LinearLayout.LayoutParams(0,-1,1f));}
            int offset=first.getDayOfWeek().getValue()-1;
            int rows=(offset+first.lengthOfMonth()+6)/7;
            for(int r=0;r<rows;r++){
                LinearLayout week=row();container.addView(week,p(-1,45));
                for(int col=0;col<7;col++){
                    int day=r*7+col-offset+1;
                    boolean valid=day>=1&&day<=first.lengthOfMonth();
                    LocalDate date=valid?first.withDayOfMonth(day):null;
                    long amount=date==null?0:activity.days.getOrDefault(date,0L);
                    String label=valid?(day+"\n"+(amount==0?"—":analyticsShortCount(amount))):"";
                    int dayBackground=amount==0?0xff293449:analyticsHeatColor(
                        Math.min(1f,(float)Math.sqrt(amount/Math.max(1.0,300.0))));
                    TextView cell=text(label,10,
                        AnalyticsContrastPolicy.readableTextColor(dayBackground,Color.BLACK,Color.WHITE));
                    cell.setGravity(Gravity.CENTER);
                    if(valid)cell.setBackground(shape(dayBackground,5));
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,px(42),1f);
                    cp.setMargins(px(1),px(1),px(1),px(1));week.addView(cell,cp);
                    if(valid){cell.setContentDescription(date+": "+amount+" сообщений");
                        cell.setOnClickListener(v->{
                            if(amount==0){toast(date+": сообщений нет");return;}
                            if(!analyticsSelectedAuthor.isEmpty()){
                                toast(date+": "+amount+" сообщений выбранного участника. Для перехода выбери всех участников.");return;
                            }
                            toast(date+": "+amount+" сообщений");
                            openAnalyticsDay(date);
                        });}
                }
            }
        }
    }

    /** Open an exact calendar day only; a nearest different date must never be presented as a match. */
    private void openAnalyticsDay(LocalDate day){
        if(!"analytics".equals(screen)||db==null)return;
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        status.setText("Ищу первое сообщение за "+day+"…");
        worker.execute(()->{
            try{
                MessageDb.DateMatch match=source.nearestDate(day.getYear(),day.getMonthValue(),day.getDayOfMonth());
                List<MessageDb.Item> items=match.position<0||!day.toString().equals(match.day)
                    ?java.util.Collections.emptyList():source.page(match.position,1,"",false,"",false,false);
                final long id=items.isEmpty()?-1:items.get(0).id;
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken||db!=source||!key.equals(activeKey)||!"analytics".equals(screen))return;
                    if(id<0){toast("Сообщения за выбранный день не найдены");status.setText("Статистика рассчитана локально");return;}
                    jump(id,"analytics");
                });
            }catch(Exception error){runOnUiThread(()->{
                if(token==loadToken&&db==source&&"analytics".equals(screen))status.setText("Не удалось открыть выбранный день");
            });}
        });
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
            LinearLayout tracks=row();block.addView(tracks,p(-1,19));
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
    private LinearLayout analyticsCard(String caption){return analyticsCard(caption,list);}
    private LinearLayout analyticsCard(String caption,LinearLayout destination){
        LinearLayout card=column();card.setPadding(px(14),px(13),px(14),px(15));
        card.setBackground(ViewerUi.surface(this,PANEL,ViewerUi.CARD,themeIndex==2,false));
        LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(5),px(5),px(5),px(11));
        destination.addView(card,lp);
        TextView title=text(caption,16,FG);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setPadding(0,0,0,px(9));card.addView(title);
        return card;
    }
    /** Ranked vocabulary: readable relative bars and a real link back to chat search. */
    private void renderAnalyticsWordRow(LinearLayout destination,ChatAnalytics.WordCount word,long peak){
        LinearLayout item=column();
        item.setPadding(px(7),px(5),px(7),px(7));
        item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x307ad2ff),
            shape(Color.TRANSPARENT,9),shape(0xffffffff,9)));
        item.setFocusable(true);item.setClickable(true);
        item.setContentDescription("Найти слово «"+word.word+"» в переписке. Употреблено "+word.count+" раз");
        LinearLayout.LayoutParams ip=p(-1,-2);ip.setMargins(0,px(2),0,px(2));destination.addView(item,ip);
        LinearLayout labels=row();labels.setGravity(Gravity.CENTER_VERTICAL);item.addView(labels,p(-1,25));
        TextView textValue=text("«"+word.word+"»",13,FG);textValue.setSingleLine(true);
        textValue.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(textValue,new LinearLayout.LayoutParams(0,-2,1f));
        TextView count=text(word.count+" раз",12,SUB);count.setPadding(px(6),0,0,0);
        labels.addView(count,p(-2,-2));
        LinearLayout track=row();track.setBackground(shape(CHIP,4));
        item.addView(track,p(-1,6));
        int weight=(int)Math.min(1000L,Math.max(3L,Math.round(1000.0*word.count/Math.max(1L,peak))));
        View filled=new View(this);filled.setBackground(shape(ACCENT,4));
        track.addView(filled,new LinearLayout.LayoutParams(0,-1,weight));
        if(weight<1000)track.addView(new View(this),new LinearLayout.LayoutParams(0,-1,1000-weight));
        item.setOnClickListener(v->openWordSearchFromAnalytics(word.word));
    }
    private void analyticsLine(LinearLayout parent,String name,long value){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);parent.addView(line,p(-1,-2));
        TextView key=text(name,12,SUB);key.setPadding(0,px(3),0,px(3));line.addView(key,new LinearLayout.LayoutParams(0,-2,1));
        TextView number=text(String.format(Locale.ROOT,"%,d",value).replace(',', ' '),13,FG);
        number.setTypeface(null,Typeface.BOLD);line.addView(number);
    }
    private void emptyWorkspace(LinearLayout parent,String heading,String detail,String iconLabel){
        LinearLayout card=column();card.setGravity(Gravity.CENTER);card.setPadding(px(20),px(20),px(20),px(20));
        card.setBackground(ViewerUi.surface(this,PANEL,ViewerUi.CARD,themeIndex==2,false));
        LinearLayout.LayoutParams lp=p(-1,-2);lp.setMargins(px(3),px(8),px(3),px(8));parent.addView(card,lp);
        ImageView icon=new ImageView(this);icon.setImageDrawable(new MenuOutlineIcon(iconLabel,ACCENT));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,p(34,34));
        TextView title=text(heading,16,FG);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));title.setGravity(Gravity.CENTER);
        title.setPadding(0,px(10),0,px(5));card.addView(title,p(-1,-2));
        TextView description=text(detail,13,SUB);description.setGravity(Gravity.CENTER);description.setLineSpacing(px(2),1f);card.addView(description,p(-1,-2));
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
        String label=date.isEmpty()?"Дата неизвестна":date;
        try{
            ChatTrends.Moment parsed=ChatTrends.parse(date);
            if(parsed!=null)label=parsed.day.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",Locale.forLanguageTag("ru-RU")));
        }catch(Exception ignored){} // Unrecognized export dates remain visible verbatim.
        TextView d=text(label,11,SUB);d.setGravity(Gravity.CENTER);
        d.setPadding(px(12),px(7),px(12),px(7));d.setBackground(ViewerUi.surface(this,PANEL,14,themeIndex==2,false));
        LinearLayout r=row();r.setGravity(Gravity.CENTER);LinearLayout.LayoutParams rp=p(-1,38);rp.topMargin=px(10);list.addView(r,rp);r.addView(d);
    }
    private CharSequence highlighted(String body,String find,int background){
        if(body==null)return "";if(find==null||find.trim().isEmpty())return body;
        SpannableString span=new SpannableString(body);
        for(int[] range:SearchHighlightPolicy.ranges(body,find,deep))
            span.setSpan(new BackgroundColorSpan(ChatSurfacePolicy.searchHighlight(ViewerPalette.of(themeIndex),background)),range[0],range[1],Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }
    private void renderMessages(List<MessageDb.Item> messages,Map<Long,List<MessageDb.Attachment>> files,Map<Long,MessageDb.ReplyInfo> replies,boolean results,boolean append){
        if(messages.isEmpty())return;
        if(!append)renderedMessageViews.clear();
        String last=append?lastRenderedDay:"",previousSender=null;
        ViewerPalette palette=ViewerPalette.of(themeIndex);
        View focus=null;View glowBubble=null;int glowBase=0;long glowId=-1;
        for(MessageDb.Item m:messages){
            String date=day(m.date),sender=m.author==null?"":m.author.trim();
            boolean newDay=!date.equals(last);
            if(newDay){dateSeparator(date);last=date;previousSender=null;}
            boolean continued=!results&&previousSender!=null&&previousSender.equalsIgnoreCase(sender);
            boolean own=!mine.isEmpty()&&mine.equalsIgnoreCase(sender);
            LinearLayout line=row();line.setTag(Long.valueOf(m.id));line.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
            LinearLayout.LayoutParams linep=p(-1,-2);
            linep.setMargins(px(2),continued?0:px(5),px(2),continued?px(2):px(6));
            list.addView(line,linep);
            if(!own){
                if(continued){
                    // Keep message text aligned with the first bubble in the group.
                    line.addView(new View(this),p(36,1));
                }else{
                    TextView avatar=text(initial(m.author),13,ChatSurfacePolicy.avatarText(authorColor(m.author)));avatar.setTypeface(null,Typeface.BOLD);
                    avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(authorColor(m.author),100));
                    String avatarFile=getPreferences(MODE_PRIVATE).getString(PersonalImages.avatarKey(activeKey,m.author),"");
                    Bitmap portrait=PersonalImages.circularCached(this,avatarFile,px(29));
                    if(portrait!=null){avatar.setText("");avatar.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(),portrait));}
                    LinearLayout.LayoutParams ap=p(29,29);ap.rightMargin=px(7);line.addView(avatar,ap);
                }
            }
            List<MessageDb.Attachment> attachments=files.get(m.id);
            boolean roundOnly=attachments!=null&&attachments.size()==1&&"round".equals(attachments.get(0).kind)
                &&(m.body==null||m.body.trim().matches("[.·•]*"));
            LinearLayout bubble=column();
            bubble.setPadding(px(roundOnly?0:bubbleInset()),px(roundOnly?0:densityIndex==0?3:densityIndex==2?8:5),
                px(roundOnly?0:bubbleInset()),px(roundOnly?0:densityIndex==0?3:densityIndex==2?8:5));
            int chosenColor=authorColor(m.author);
            // Colored avatars remain a useful sender cue; huge saturated message
            // backgrounds are tiring to read. Use muted author-tinted slate bubbles.
            int bubbleColor=ChatSurfacePolicy.bubble(palette,chosenColor,own);
            int displayedColor=m.id==highlightId?ChatSurfacePolicy.highlight(palette,bubbleColor):bubbleColor;
            int detailColor=ChatSurfacePolicy.secondary(palette,displayedColor);
            if(roundOnly)bubble.setBackgroundColor(Color.TRANSPARENT);
            else bubble.setBackground(gradient(displayedColor,displayedColor,bubbleRadius));
            if(m.id==highlightId){glowBubble=bubble;glowBase=bubbleColor;glowId=m.id;line.setBackground(shape(0x443ad0fc,12));}
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
            int viewport=list.getWidth()>0?list.getWidth():getResources().getDisplayMetrics().widthPixels;
            int maxWidth=Math.max(px(96),viewport-list.getPaddingLeft()-list.getPaddingRight()-px(56+2*bubbleInset()));final int mw=maxWidth;
            bubble.setOnLongClickListener(v->{messageActions(m);return true;});
            String author=m.author==null||m.author.isEmpty()?"Неизвестный отправитель":m.author;
            if(!roundOnly&&!continued){
                TextView from=text(author,12,detailColor);from.setMaxWidth(mw);
                from.setTypeface(null,Typeface.BOLD);bubble.addView(from);
                from.setOnLongClickListener(v->{pickAuthorColor(author);return true;});
            }
            MessageDb.ReplyInfo reply=replies.get(m.id);
            if(reply!=null&&!roundOnly){
                final String reference=reply.targetId>0?
                    "↪ "+(reply.author.isEmpty()?"Ответ на сообщение":reply.author)+" · "+
                    (reply.preview.isEmpty()?"Сообщение с вложением":reply.preview):
                    "↪ Ответ на сообщение Telegram #"+reply.telegramId+" (оригинал отсутствует)";
                int replySurface=ChatSurfacePolicy.blend(displayedColor,palette.accent,8);
                TextView link=text(reference,12,ChatSurfacePolicy.secondary(palette,replySurface));
                link.setMaxWidth(mw);link.setMaxLines(2);link.setEllipsize(TextUtils.TruncateAt.END);
                link.setPadding(px(10),px(8),px(10),px(8));
                link.setBackground(shape(replySurface,8));
                bubble.addView(link,p(-1,-2));
                if(reply.targetId>0)link.setOnClickListener(v->jump(reply.targetId,""));
            }
            if(m.body!=null&&!m.body.isEmpty()&&!roundOnly){
                TextView body=text("",15,FG);body.setMaxWidth(mw);body.setText(highlighted(m.body,results?q:"",displayedColor));
                body.setPadding(0,px(3),0,px(2));body.setLineSpacing(px(2),1.0f);bubble.addView(body);
            }
            if(attachments!=null)for(MessageDb.Attachment a:attachments)bubble.addView(mediaTile(a,false),p(-2,-2));
            LinearLayout footer=row();footer.setGravity(Gravity.RIGHT);
            TextView time=text(clock(m.date),11,roundOnly?SUB:detailColor);
            footer.addView(time);
            if(roundOnly){time.setTextSize(10);time.setTextColor(SUB);}
            bubble.addView(footer);
            // Favorites are changed only in the long-press actions menu: no accidental tap targets.
            if(results){
                TextView open=text("↗ К сообщению в чате",12,detailColor);open.setPadding(0,px(8),0,px(2));bubble.addView(open);
                open.setOnClickListener(v->jump(m.id,"search"));
                bubble.setOnClickListener(v->jump(m.id,"search"));
            }
            renderedMessageViews.put(m.id,line);
            if(m.id==highlightId)focus=line;
            previousSender=sender;
        }
        lastRenderedDay=last;
        // Layout can recreate the source without a scroll callback; check the return
        // after the new chat tiles have attached rather than holding a stale bubble.
        if((autoFloating||(playingAttachment!=null&&"round".equals(playingAttachment.kind)))
                &&"chat".equals(screen))scroll.post(this::scheduleFloatingCheck);
        if(append)return; // Never jump to the top when more history is appended.
        if(results&&pendingSearchScroll>=0)return; // The return path restores after layout.
        if("chat".equals(screen)&&pendingRestoreId>0){
            final View restored=renderedMessageViews.get(pendingRestoreId);
            final int top=pendingRestoreTop;
            pendingRestoreId=-1;
            if(restored!=null){final int restoreToken=loadToken;scroll.post(()->{if(restoreToken==loadToken&&"chat".equals(screen)&&!home&&restored.getParent()==list)scroll.scrollTo(0,Math.max(0,restored.getTop()-top));});return;}
        }
        final View target=focus;
        final View targetBubble=glowBubble;
        final int color=glowBase;
        final long id=glowId;
        final int renderedToken=loadToken;final String renderedScreen=screen;
        scroll.post(()->{
            if(renderedToken!=loadToken||!renderedScreen.equals(screen))return;
            if(target!=null){
                scroll.scrollTo(0,Math.max(0,target.getTop()-px(120)));
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
        int duration=motionDuration(1750);
        if(duration==0){bg.setColors(new int[]{background,background});highlightId=-1;return;}
        final int start=ChatSurfacePolicy.highlight(ViewerPalette.of(themeIndex),background);final int token=loadToken;
        ValueAnimator animator=ValueAnimator.ofFloat(0f,1f);
        animator.setDuration(duration);animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation->{
            if(bubble.getParent()==null||token!=loadToken)return;
            float t=(float)animation.getAnimatedValue();
            int color=mixColors(start,background,t);bg.setColors(new int[]{color,color});
        });
        animator.start();
        handler.postDelayed(()->{if(token==loadToken&&highlightId==id)highlightId=-1;},duration+100);
    }
    private String initial(String s){if(s==null||s.isEmpty())return "?";return s.substring(0,1).toUpperCase(Locale.ROOT);}
    private void messageActions(MessageDb.Item m){
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        if(source==null||home)return;
        boolean outOfChat=!"chat".equals(screen),hasText=m.body!=null&&!m.body.trim().isEmpty();
        ArrayList<String> actions=new ArrayList<>();ArrayList<Runnable> handlers=new ArrayList<>();
        actions.add(source.isFavorite(m.id)?"Убрать из избранного":"★ Добавить в избранное");handlers.add(()->toggleStar(m.id));
        boolean pinned=source.isPinned(m.id);
        actions.add(pinned?"Открепить":"📌 Закрепить сообщение");
        handlers.add(()->worker.execute(()->{
            boolean pin=source.togglePinned(m.id);
            runOnUiThread(()->{if(sameScreenRequest(source,key,token)){toast(pin?"Закреплено":"Откреплено");renderPinnedBar();}});
        }));
        actions.add("◷ Отметить начало периода");handlers.add(()->selectPeriodBoundary(m,true));
        actions.add("◷ Отметить конец периода");handlers.add(()->selectPeriodBoundary(m,false));
        actions.add("♡ Добавить событие истории");handlers.add(()->createRelationshipEventDialog(m));
        if(hasText){
            actions.add("❞ Цитировать фрагмент");handlers.add(()->quoteMessage(m));
            actions.add("Скопировать текст");handlers.add(()->{
                android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Telegram message",m.body));toast("Текст скопирован");
            });
            actions.add("↓ Сохранить сообщение в TXT");handlers.add(()->saveMessageText(m));
        }
        actions.add("↓ Сохранить вложение…");handlers.add(()->chooseMessageAttachment(m.id));
        if(outOfChat){actions.add("↗ Перейти к сообщению");
            handlers.add(()->jump(m.id,screen.equals("search")?"search":screen.equals("favorites")?"favorites":""));
        }
        new FontDialog.Builder(this).setTitle("Сообщение · "+clock(m.date))
            .setItems(actions.toArray(new String[0]),(dialog,which)->{
                if(sameScreenRequest(source,key,token))handlers.get(which).run();
            }).setNegativeButton("Закрыть",null).show();
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
        if(home||db==null)return;
        final MessageDb source=db;final String archive=activeKey;final int token=loadToken;
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
            if(!sameScreenRequest(source,archive,token)){dialog.dismiss();toast("Переписка изменилась");return;}
            Button save=dialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);
            worker.execute(()->{try{source.addPeriod(label,comment,start,end);
                runOnUiThread(()->{if(isDestroyed()||isFinishing())return;dialog.dismiss();if(!sameScreenRequest(source,archive,token))return;
                    pendingPeriodStart=null;pendingPeriodArchive="";chronologySection="stages";screen="chronology";showPage();});
            }catch(Exception e){runOnUiThread(()->{if(dialog.isShowing()){save.setEnabled(true);name.setError("Не удалось сохранить этап");}});}});
        }));dialog.show();
    }
    /** Opt-in relationship workspace: no automatic categorization or upload. */
    private void prepareRelationshipScreen(String heading,String description){
        // These pages bypass showPage's normal refreshTabs path. Without
        // refreshing the header they still display stale "Мои переписки" and
        // its home subtitle while showing an unrelated workspace.
        refreshTabs();
        title.setText("dual_compare".equals(screen)||"dual_memories".equals(screen)?"Сравнение переписок":"Хронология");
        subtitle.setText("relationships".equals(screen)?"Личные события и инструменты":heading);
        if(scheduledPrefetch!=null){handler.removeCallbacks(scheduledPrefetch);scheduledPrefetch=null;}
        bottomNav.setVisibility(View.GONE);pinnedBar.removeAllViews();
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        savedComposer.setVisibility(View.GONE);list.removeAllViews();list.setBackgroundColor(BG);
        // Do not repeat "Хронология" under the already-labelled app bar.
        if("relationships".equals(screen)){
            TextView compactHeading=text(historyEventsExpanded?"Личные события":"История переписки",18,FG);
            compactHeading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            compactHeading.setPadding(px(12),px(12),px(12),px(5));list.addView(compactHeading);
            return;
        }
        TextView title=text(heading,20,FG);title.setTypeface(null,Typeface.BOLD);
        title.setPadding(px(12),px(11),px(12),px(7));list.addView(title);
        analyticsText(list,description,SUB,12);
    }
    private void createRelationshipEventDialog(MessageDb.Item m){
        if(home||db==null)return;
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        LinearLayout form=column();form.setPadding(px(16),px(4),px(16),px(8));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Например: Первая встреча");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(90)});
        ViewerUi.styleInput(name,ViewerPalette.of(themeIndex));form.addView(name,p(-1,-2));
        EditText description=new EditText(this);description.setHint("Описание (необязательно)");
        description.setMinLines(3);description.setMaxLines(6);
        description.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2500)});
        ViewerUi.styleInput(description,ViewerPalette.of(themeIndex));LinearLayout.LayoutParams descriptionBounds=p(-1,-2);descriptionBounds.topMargin=px(10);form.addView(description,descriptionBounds);
        TextView explain=text("Отметка привязана к сообщению. Медиа можно добавить после сохранения.",12,SUB);explain.setPadding(0,px(10),0,0);
        form.addView(explain);
        AlertDialog eventDialog=new FontDialog.Builder(this).setTitle("Новое событие")
            .setView(form).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        eventDialog.setOnShowListener(ignored->eventDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String label=name.getText().toString().trim(),note=description.getText().toString().trim();
            if(label.isEmpty()){name.setError("Введи название события");return;}
            if(!sameScreenRequest(source,key,token)){eventDialog.dismiss();toast("Переписка изменилась");return;}
            Button save=eventDialog.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);
            worker.execute(()->{
                try{
                    if(!source.hasMessage(m.id))throw new IOException("Исходное сообщение не найдено");
                    source.addRelationshipEvent(label,note,m.id,0);
                    runOnUiThread(()->{
                        if(isDestroyed()||isFinishing())return;
                        eventDialog.dismiss();
                        if(sameScreenRequest(source,key,token))toast("Событие сохранено в хронологии");
                    });
                }catch(Exception e){runOnUiThread(()->{
                    if(!eventDialog.isShowing())return;save.setEnabled(true);name.setError("Не удалось сохранить событие");
                });}
            });
        }));eventDialog.show();
    }

    private void editRelationshipEvent(MessageDb.RelationshipEvent event){
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        if(source==null||home||!"relationships".equals(screen))return;
        LinearLayout form=column();form.setPadding(px(20),px(12),px(20),px(12));
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Название");
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(90)});
        name.setText(event.title);ViewerUi.styleInput(name,ViewerPalette.of(themeIndex));form.addView(name,p(-1,-2));
        EditText note=new EditText(this);note.setHint("Описание");note.setMinLines(3);note.setMaxLines(6);
        note.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2500)});
        note.setText(event.note);ViewerUi.styleInput(note,ViewerPalette.of(themeIndex));
        LinearLayout.LayoutParams gap=p(-1,-2);gap.topMargin=px(10);form.addView(note,gap);
        AlertDialog dialog=new FontDialog.Builder(this).setTitle("Изменить событие").setView(form)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
            String label=name.getText().toString().trim(),body=note.getText().toString().trim();
            if(label.isEmpty()){name.setError("Введи название");return;}
            if(!sameScreenRequest(source,key,token)){dialog.dismiss();return;}
            Button save=dialog.getButton(-1);save.setEnabled(false);
            worker.execute(()->{try{
                source.updateRelationshipEvent(event.id,label,body);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;dialog.dismiss();
                    if(sameScreenRequest(source,key,token)){if("relationships".equals(screen))renderRelationshipsPage();else if("chronology".equals(screen))renderChronologyPage();}});
            }catch(Exception error){runOnUiThread(()->{if(dialog.isShowing()){save.setEnabled(true);name.setError("Не удалось сохранить");}});}});
        }));dialog.show();
    }
    private void unlinkEventAttachment(long eventId,MessageDb.Attachment attachment){
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        new FontDialog.Builder(this).setTitle("Открепить вложение?")
            .setMessage("Удалится только ссылка из отметки. Исходный файл останется.")
            .setNegativeButton("Отмена",null).setPositiveButton("Открепить",(d,w)->{
                if(!sameScreenRequest(source,key,token))return;
                worker.execute(()->{try{source.unlinkRelationshipMedia(eventId,attachment.id);
                    runOnUiThread(()->{if(sameScreenRequest(source,key,token)&&"relationships".equals(screen))renderRelationshipsPage();});
                }catch(Exception error){runOnUiThread(()->{if(sameScreenRequest(source,key,token))toast("Не удалось открепить");});}});
            }).show();
    }
    private LinearLayout relationshipCard(LinearLayout parent,String title){
        LinearLayout card=column();card.setPadding(px(13),px(12),px(13),px(13));
        card.setBackground(ViewerUi.surface(this,PANEL,ViewerUi.CARD,themeIndex==2,false));
        LinearLayout.LayoutParams params=p(-1,-2);params.setMargins(px(6),px(5),px(6),px(9));
        parent.addView(card,params);
        TextView heading=text(title,16,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(0,0,0,px(8));card.addView(heading);
        return card;
    }
    /** Fast jump into personally saved chronology events without opening each event page. */
    private void searchHistoryEvents(String query){
        if(db==null||home||!"relationships".equals(screen))return;
        String needle=query==null?"":query.trim();
        if(needle.isEmpty()){toast("Введи название, описание или дату события");return;}
        final MessageDb source=db;final String archive=activeKey,term=needle;
        final int token=loadToken;
        status.setVisibility(View.VISIBLE);status.setText("Ищу личные события…");
        worker.execute(()->{
            HistoryEventSearch.Result result=null;String failure=null;
            try{result=HistoryEventSearch.find(source,term);}
            catch(Exception ex){failure=ex.getMessage();}
            final HistoryEventSearch.Result hits=result;final String error=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||token!=loadToken||
                    db!=source||!archive.equals(activeKey)||!"relationships".equals(screen))return;
                status.setVisibility(View.GONE);
                if(error!=null){toast("Поиск не удался: "+error);return;}
                if(hits.matches.isEmpty()){
                    new FontDialog.Builder(this).setTitle("События не найдены")
                        .setMessage("Нет совпадений для «"+clip(term,80)+"»"+
                            (hits.incomplete?". Проверены первые "+hits.scanned+" событий.":""))
                        .setPositiveButton("Закрыть",null).show();
                    return;
                }
                String[] labels=new String[hits.matches.size()];
                for(int i=0;i<labels.length;i++){
                    MessageDb.RelationshipEvent e=hits.matches.get(i);
                    labels[i]=clip(e.title,70)+(e.note==null||e.note.isEmpty()?"":
                        " · "+clip(e.note,55));
                }
                new FontDialog.Builder(this)
                    .setTitle("Найдено событий: "+hits.matches.size()+
                        (hits.incomplete?" · частичный поиск":""))
                    .setMessage(hits.incomplete?
                        "Показаны первые совпадения. Для дальнейших событий уточни запрос.":null)
                    .setItems(labels,(d,which)->jump(hits.matches.get(which).anchorId,"relationships"))
                    .setNegativeButton("Закрыть",null).show();
            });
        });
    }
    /** One descriptive destination per chronology feature, not a stack of identical buttons. */
    private LinearLayout historyDestination(LinearLayout parent,String label,String detail,Runnable open){
        LinearLayout item=row();item.setPadding(px(14),px(13),px(14),px(13));item.setMinimumHeight(px(72));
        item.setBackground(ViewerUi.control(this,PANEL,ViewerUi.CARD,themeIndex==2));
        LinearLayout.LayoutParams outer=p(-1,-2);outer.setMargins(px(5),px(4),px(5),px(5));parent.addView(item,outer);
        ImageView icon=new ImageView(this);icon.setImageDrawable(new MenuOutlineIcon(label,ACCENT));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp=p(24,24);iconLp.rightMargin=px(12);item.addView(icon,iconLp);
        LinearLayout details=column();item.addView(details,new LinearLayout.LayoutParams(0,-2,1));
        TextView heading=text(label,16,FG);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));details.addView(heading);
        TextView description=text(detail,12,SUB);description.setPadding(0,px(4),0,0);description.setLineSpacing(px(2),1f);details.addView(description);
        TextView arrow=text("›",22,ACCENT);arrow.setPadding(px(10),0,0,0);item.addView(arrow);
        item.setContentDescription(label+". "+detail);item.setFocusable(true);item.setOnClickListener(v->open.run());
        return item;
    }
    private void renderRelationshipsPage(){
        int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        prepareRelationshipScreen("Хронология","События, периоды и воспоминания");
        // Find old user-authored events directly, regardless of the current 20-item page.
        // Searching never traverses the private message text or modifies annotations.
        LinearLayout eventSearch=row();eventSearch.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams searchBounds=p(-1,50);
        searchBounds.setMargins(px(6),px(5),px(6),px(8));
        list.addView(eventSearch,searchBounds);
        EditText findEvent=new EditText(this);findEvent.setSingleLine(true);
        findEvent.setTextSize(14);findEvent.setTextColor(FG);findEvent.setHintTextColor(SUB);
        findEvent.setHint("Название, описание или дата события");
        findEvent.setContentDescription("Найти событие в хронологии");
        findEvent.setBackground(outlined(PANEL,12,false));
        findEvent.setPadding(px(12),px(5),px(8),px(5));
        findEvent.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        eventSearch.addView(findEvent,new LinearLayout.LayoutParams(0,px(46),1));
        Button search=btn("Найти");search.setContentDescription("Найти событие по всему архиву");
        LinearLayout.LayoutParams searchButton=p(76,46);
        searchButton.leftMargin=px(6);eventSearch.addView(search,searchButton);
        search.setOnClickListener(v->searchHistoryEvents(findEvent.getText().toString()));
        findEvent.setOnEditorActionListener((v,action,event)->{
            searchHistoryEvents(findEvent.getText().toString());return true;
        });
        historyDestination(list,historyEventsExpanded?"События переписки ▴":"События переписки ▾",
            "Личные отметки, привязанные к отдельным сообщениям.",()->{historyEventsExpanded=!historyEventsExpanded;renderRelationshipsPage();});
        if(!historyEventsExpanded){
        historyDestination(list,"Этапы переписки","Промежуток между двумя сообщениями.",
            ()->{historyEventsExpanded=false;chronologySection="stages";screen="chronology";showPage();});
        historyDestination(list,"Периоды по датам","Отметки с произвольными датами.",
            ()->{historyEventsExpanded=false;chronologySection="calendar";screen="chronology";showPage();});
        historyDestination(list,"Сравнение этапов","Активность в отмеченных этапах.",
            ()->{historyEventsExpanded=false;chronologySection="chart";screen="chronology";showPage();});
        historyDestination(list,"В этот день","Сообщения за одну и ту же дату в разные годы.",
            ()->{LocalDate today=LocalDate.now();showOnThisDay(today.getMonthValue(),today.getDayOfMonth());});
        }
        Button backToChat=btn("+ Отметить событие в переписке");LinearLayout.LayoutParams add=p(-1,48);
        add.setMargins(px(2),px(7),px(2),px(8));list.addView(backToChat,add);
        backToChat.setOnClickListener(v->{screen="chat";showPage();toast("Удерживай важное сообщение и выбери «Добавить событие истории»");});
        if(!historyEventsExpanded){
        TextView more=secondaryDisclosure("Другие инструменты истории  ▾");list.addView(more,p(-1,48));
        LinearLayout advanced=column();advanced.setVisibility(View.GONE);list.addView(advanced,p(-1,-2));
        more.setOnClickListener(v->{boolean expand=advanced.getVisibility()!=View.VISIBLE;advanced.setVisibility(expand?View.VISIBLE:View.GONE);
            more.setText(expand?"Скрыть дополнительные инструменты  ▴":"Другие инструменты истории  ▾");});
        historyDestination(advanced,"Повторяющиеся фразы","Найти одинаковые выражения в чате.",()->{screen="echo";showPage();});
        historyDestination(advanced,"Сравнить две переписки","Статистика и общая временная шкала.",
            ()->{comparisonFromHome=false;chooseDualChat("dual_compare");});
        historyDestination(advanced,"Паузы между сообщениями","Длительные промежутки без сообщений.",this::showConversationPauses);
        TextView explain=secondaryDisclosure("Как работают инструменты  ›");advanced.addView(explain,p(-1,48));
        explain.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Инструменты истории")
             .setMessage(RelationshipFeatureGuide.INTRO+"\n\nСобытие — отметка на одном сообщении. Этап — между двумя сообщениями. Период — любые даты.\n\n"+RelationshipFeatureGuide.STAGES+"\n\n"+RelationshipFeatureGuide.ECHO+"\n\n"+RelationshipFeatureGuide.COMPARE+"\n\n"+RelationshipFeatureGuide.MEMORIES+"\n\n"+RelationshipFeatureGuide.CALENDARS+"\n\nПаузы не доказывают потерю сообщений.")
            .setPositiveButton("Понятно",null).show());
        }
        LinearLayout eventsHolder=column();list.addView(eventsHolder,p(-1,-2));
        if(!historyEventsExpanded){
            status.setText("");status.setVisibility(View.GONE);
            return;
        }
        final MessageDb source=db;final String key=activeKey;
        worker.execute(()->{
            try{
                final int totalEvents=source.relationshipEventCount();
                final int lastPage=Math.max(0,(totalEvents-1)/20);
                final int pageIndex=Math.min(Math.max(0,relationshipPageIndex),lastPage);
                final int first=pageIndex*20;
                final List<MessageDb.RelationshipEvent> visible=source.relationshipEventsPage(first,20);
                LinkedHashMap<Long,String> eventDates=new LinkedHashMap<>();
                for(MessageDb.RelationshipEvent e:visible)
                    eventDates.put(e.id,source.messageDate(e.anchorId));
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken||!key.equals(activeKey)||db!=source)return;
                    relationshipPageIndex=pageIndex;
                    eventsHolder.removeAllViews();
                    TextView heading=text("Лента событий  ·  "+totalEvents,17,FG);
                    heading.setTypeface(null,Typeface.BOLD);
                    heading.setPadding(px(10),px(13),px(8),px(9));
                    eventsHolder.addView(heading,p(-1,-2));
                    if(totalEvents>0)
                        analyticsText(eventsHolder,"Показаны "+(first+1)+"–"+(first+visible.size())+
                            " из "+totalEvents+" событий",SUB,12);
                    if(totalEvents==0){
                        LinearLayout empty=column();empty.setGravity(Gravity.CENTER);
                        empty.setPadding(px(12),px(17),px(12),px(20));
                        TextView dot=text("●",26,ACCENT);dot.setGravity(Gravity.CENTER);
                        empty.addView(dot,p(-1,38));
                        TextView label=text("Здесь появятся твои события",15,FG);
                        label.setGravity(Gravity.CENTER);
                        empty.addView(label,p(-1,-2));
                        analyticsText(empty,"Удерживай важное сообщение и добавь событие.",SUB,12);
                        eventsHolder.addView(empty,p(-1,-2));
                    }
                    String currentYear="";
                    for(MessageDb.RelationshipEvent event:visible){
                        String recorded=eventDates.get(event.id);
                        ChatTrends.Moment moment=ChatTrends.parse(recorded);
                        String year=moment==null?"Без даты":Integer.toString(moment.day.getYear());
                        if(!year.equals(currentYear)){
                            currentYear=year;
                            TextView group=text(year,15,ACCENT);
                            group.setTypeface(null,Typeface.BOLD);
                            group.setPadding(px(12),px(17),px(10),px(6));
                            eventsHolder.addView(group,p(-1,-2));
                        }
                        LinearLayout timelineRow=row();timelineRow.setGravity(Gravity.TOP);
                        eventsHolder.addView(timelineRow,p(-1,-2));
                        LinearLayout rail=column();rail.setGravity(Gravity.CENTER_HORIZONTAL);
                        TextView dot=text("●",17,ACCENT);dot.setGravity(Gravity.CENTER);
                        rail.addView(dot,p(-1,29));
                        View stem=new View(this);stem.setBackground(shape(0xff355b70,2));
                        rail.addView(stem,p(2,95));
                        timelineRow.addView(rail,p(25,-2));
                        LinearLayout card=relationshipCard(timelineRow,event.title);
                        LinearLayout.LayoutParams cell=(LinearLayout.LayoutParams)card.getLayoutParams();
                        cell.width=0;cell.weight=1f;card.setLayoutParams(cell);
                        if(recorded!=null&&!recorded.isEmpty())
                            analyticsText(card,day(recorded)+" · "+clock(recorded),ACCENT,12);
                        if(event.note!=null&&!event.note.isEmpty())
                            analyticsText(card,clip(event.note,115),FG,13);
                        if(event.linkedCount>0)
                            analyticsText(card,"Вложений: "+event.linkedCount,SUB,12);
                        boolean hasDetails=(event.note!=null&&event.note.length()>115)||event.linkedCount>0;
                        if(hasDetails){
                            Button expand=btn("Подробнее  ▾");card.addView(expand,p(-1,41));
                            LinearLayout details=column();details.setVisibility(View.GONE);card.addView(details,p(-1,-2));
                            expand.setOnClickListener(v->{
                                boolean opening=details.getVisibility()!=View.VISIBLE;
                                if(opening&&details.getChildCount()==0){
                                    if(event.note!=null&&event.note.length()>115)
                                        analyticsText(details,event.note,FG,13);
                                    if(event.linkedCount>0){
                                        loadHistoryAttachmentPage(details,event.id,event.linkedCount,source,key,token,0);
                                    }
                                }
                                details.setVisibility(opening?View.VISIBLE:View.GONE);
                                expand.setText(opening?"Свернуть подробности  ▴":"Подробнее  ▾");
                            });
                        }
                        LinearLayout actions=row();card.addView(actions,p(-1,43));
                        Button anchor=btn("К сообщению");actions.addView(anchor,new LinearLayout.LayoutParams(0,-1,1f));
                        anchor.setOnClickListener(v->jump(event.anchorId,"relationships"));
                        Button attach=btn("+ Медиа");actions.addView(attach,new LinearLayout.LayoutParams(0,-1,1f));
                        attach.setOnClickListener(v->chooseRelationshipAttachment(event.id,0));
                        Button remove=btn("⋯");remove.setContentDescription("Управление событием "+event.title);
                        actions.addView(remove,p(43,43));
                        remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle(event.title)
                            .setItems(new String[]{"Изменить название и описание","Удалить событие"},(menu,action)->{
                            if(!sameScreenRequest(source,key,token))return;
                            if(action==0){editRelationshipEvent(event);return;}
                            new FontDialog.Builder(this).setTitle("Удалить событие?")
                            .setMessage("Удалится только описание и список ссылок. Все исходные файлы и сообщения останутся.")
                            .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,which)->worker.execute(()->{
                                source.removeRelationshipEvent(event.id);
                                runOnUiThread(()->{if(key.equals(activeKey)&&screen.equals("relationships"))renderRelationshipsPage();});
                            })).show();}).setNegativeButton("Закрыть",null).show());
                    }
                    if(totalEvents>20){
                        LinearLayout pager=row();pager.setGravity(Gravity.CENTER_VERTICAL);eventsHolder.addView(pager,p(-1,52));
                        Button prev=btn("‹ Раньше");pager.addView(prev,new LinearLayout.LayoutParams(0,px(46),1));
                        prev.setEnabled(first>0);prev.setOnClickListener(v->{relationshipPageIndex=Math.max(0,pageIndex-1);renderRelationshipsPage();});
                        TextView count=text((pageIndex+1)+" / "+(lastPage+1),12,SUB);
                        count.setGravity(Gravity.CENTER);pager.addView(count,p(64,46));
                        Button next=btn("Позже ›");pager.addView(next,new LinearLayout.LayoutParams(0,px(46),1));
                        next.setEnabled(first+visible.size()<totalEvents);
                        next.setOnClickListener(v->{relationshipPageIndex=pageIndex+1;renderRelationshipsPage();});
                    }
                    status.setText("");status.setVisibility(View.GONE);
                });
            }catch(Exception e){runOnUiThread(()->{if(token==loadToken)status.setText("Ошибка событий: "+e.getMessage());});}
        });
    }
    private void loadHistoryAttachmentPage(LinearLayout target,long eventId,int linkedCount,MessageDb source,String key,int token,int offset){
        TextView loading=text("Загружаю вложения…",12,SUB);target.addView(loading);
        worker.execute(()->{try{
            List<MessageDb.Attachment> items=source.relationshipMediaPage(eventId,offset,26);
            runOnUiThread(()->{
                if(!sameScreenRequest(source,key,token)||!target.isAttachedToWindow())return;
                target.removeView(loading);int shown=Math.min(25,items.size());
                if(offset==0&&shown==0)analyticsText(target,"Нет прикреплённых вложений.",SUB,12);
                for(int i=0;i<shown;i++){
                    MessageDb.Attachment attachment=items.get(i);
                    LinearLayout fileRow=row();target.addView(fileRow,p(-1,48));
                    Button open=btn(mediaLabel(attachment.kind)+" · открыть");fileRow.addView(open,new LinearLayout.LayoutParams(0,-1,1));
                    open.setOnClickListener(v->openMedia(attachment));
                    Button unlink=btn("✕");unlink.setContentDescription("Открепить "+mediaLabel(attachment.kind));
                    fileRow.addView(unlink,p(44,44));unlink.setOnClickListener(v->unlinkEventAttachment(eventId,attachment));
                }
                if(items.size()>25){
                    Button more=btn("Показать ещё вложения");target.addView(more,p(-1,48));
                    more.setOnClickListener(v->{target.removeView(more);loadHistoryAttachmentPage(target,eventId,linkedCount,source,key,token,offset+shown);});
                }else if(linkedCount>=0&&offset+shown<linkedCount)analyticsText(target,"Некоторые ссылки не соответствуют исходному вложению.",SUB,12);
            });
        }catch(Exception error){runOnUiThread(()->{
            if(!sameScreenRequest(source,key,token)||!target.isAttachedToWindow())return;
            target.removeView(loading);Button retry=btn("Повторить загрузку");target.addView(retry,p(-1,48));
            retry.setOnClickListener(v->{target.removeView(retry);loadHistoryAttachmentPage(target,eventId,linkedCount,source,key,token,offset);});
        });}});
    }
    private void chooseRelationshipAttachment(long eventId,int offset){
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        if(source==null||home)return;
        worker.execute(()->{
            try{
                List<MessageDb.Attachment> files=source.mediaPage("all",offset,25);
                runOnUiThread(()->{
                    if(!sameScreenRequest(source,key,token))return;
                    String[] titles=new String[files.size()];
                    for(int i=0;i<files.size();i++){
                        MessageDb.Attachment a=files.get(i);
                        titles[i]=mediaLabel(a.kind)+" · #"+a.messageId+" · "+clip(a.path,36);
                    }
                    if(files.isEmpty()){toast("В этом архиве больше нет вложений для выбора");return;}
                    AlertDialog.Builder picker=new FontDialog.Builder(this)
                        .setTitle("Прикрепить ссылку из архива · "+(offset+1)+"–"+(offset+files.size()))
                        .setItems(titles,(dialog,index)->{
                            if(!sameScreenRequest(source,key,token))return;
                            MessageDb.Attachment selected=files.get(index);
                            worker.execute(()->{
                                try{source.linkRelationshipMedia(eventId,selected.id);
                                    runOnUiThread(()->{if(!sameScreenRequest(source,key,token))return;
                                        if(screen.equals("relationships"))renderRelationshipsPage();
                                        else if(screen.equals("chronology"))renderChronologyPage();});
                                }catch(Exception e){runOnUiThread(()->toast("Не удалось прикрепить файл: "+e.getMessage()));}
                            });
                        }).setNegativeButton("Закрыть",null);
                    if(files.size()==25)picker.setPositiveButton("Следующие 25 ›",(dialog,which)->{if(sameScreenRequest(source,key,token))chooseRelationshipAttachment(eventId,offset+25);});
                    if(offset>0)picker.setNeutralButton("‹ Назад",(dialog,which)->{if(sameScreenRequest(source,key,token))chooseRelationshipAttachment(eventId,Math.max(0,offset-25));});
                    picker.show();
                });
            }catch(Exception e){runOnUiThread(()->toast("Ошибка медиатеки: "+e.getMessage()));}
        });
    }
    /** Two separate per-chat SQLite databases; opt-in counts only, no participant mixing. */
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
                comparisonFromHome=true;
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
        prepareRelationshipScreen(isTimeline?"Общая история":"Статистика двух чатов",
            "Сравнение локальных архивов без изменения сообщений.");
        TextView pair=text(activeName+"  /  "+dualSecondName,15,FG);
        pair.setPadding(px(8),px(9),px(8),px(9));pair.setMaxLines(3);list.addView(pair,p(-1,-2));
        Button other=btn("Выбрать второй чат");list.addView(other,p(-1,48));
        other.setOnClickListener(v->chooseDualChat(mode));
        LinearLayout modes=row();list.addView(modes,p(-1,48));
        Button metrics=btn("Статистика");Button timeline=btn("Общая история");
        modes.addView(metrics,new LinearLayout.LayoutParams(0,-1,1));
        modes.addView(timeline,new LinearLayout.LayoutParams(0,-1,1));
        metrics.setEnabled(isTimeline);timeline.setEnabled(!isTimeline);
        metrics.setOnClickListener(v->{screen="dual_compare";showPage();});
        timeline.setOnClickListener(v->{screen="dual_memories";showPage();});
        LinearLayout dates=row();list.addView(dates,p(-1,48));
        Button yearButton=btn("Год: "+year+"  ▾");dates.addView(yearButton,new LinearLayout.LayoutParams(0,-1,1));
        yearButton.setOnClickListener(v->chooseDualYear());
        if(isTimeline){
            Button monthButton=btn(month==0?"Весь год  ▾":String.format(Locale.ROOT,"%02d · месяц ▾",month));
            dates.addView(monthButton,new LinearLayout.LayoutParams(0,-1,1));monthButton.setOnClickListener(v->chooseDualMonth());
        }
        if(!isTimeline){
            Button deep=btn("Сравнить содержание локальным ИИ");list.addView(deep,p(-1,-2));
            deep.setMinHeight(px(48));deep.setOnClickListener(v->{
                if(token!=loadToken||!first.equals(activeKey)||!second.equals(dualSecondKey))return;
                startActivityForResult(new Intent(this,DeepAnalystActivity.class)
                    .putExtra("archive",first).putExtra("compare_archive",second)
                    .putExtra("question","Сравни манеру общения, конфликты, поддержку и изменения в двух переписках."),119);
            });
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
                progress.setVisibility(View.GONE);
                if(isTimeline){
                    List<SharedMemories.Entry> rows=SharedMemories.mergeBalanced(listA,listB,30);
                    renderDualMemoriesRows(rows,rA,rB,firstName,secondName);
                }else renderDualComparison(rA,rB,firstName,secondName);
            });
        });
    }
    private void renderDualComparison(DualChatRepository.Snapshot a,DualChatRepository.Snapshot b,
                                      String firstName,String secondName){
        LinearLayout summary=relationshipCard(list,"За всё время");
        compareHeader(summary,firstName,secondName);
        compareMetric(summary,"Сообщения",a.messages,b.messages);
        compareMetric(summary,"С текстом",a.withText,b.withText);
        compareMetric(summary,"Участники",a.participants,b.participants);
        compareMetric(summary,"Вложения",a.attachments,b.attachments);
        compareMetric(summary,"Избранное",a.favorites,b.favorites);
        LinearLayout yearCard=relationshipCard(list,"Активность за "+dualYear);
        compareHeader(yearCard,firstName,secondName);
        compareMetric(yearCard,"Сообщения",a.calendar.total(),b.calendar.total());
        compareMetric(yearCard,"Активные дни",a.calendar.activeDays(),b.calendar.activeDays());
        analyticsText(yearCard,"Дней, когда были активны оба чата: "+
            new ParallelCalendars.Comparison(a.calendar,b.calendar).sharedActiveDays(),FG,13);
        if(a.calendar.partial||b.calendar.partial)
            analyticsText(yearCard,"⚠ Годовые данные частичные (лимит "+ParallelCalendars.MAX_DATE_ROWS+
                " записей на чат). Полные счётчики выше не ограничены.",0xffffc377,12);
        analyticsText(yearCard,"График по месяцам · A синий, Б оранжевый. Шкала общая.",SUB,12);
        drawDualMonthlyBars(yearCard,a.calendar.counts(),b.calendar.counts());
        LinearLayout media=relationshipCard(list,"Вложения за всё время");
        java.util.TreeSet<String> kinds=new java.util.TreeSet<>();
        kinds.addAll(a.mediaTypes.keySet());kinds.addAll(b.mediaTypes.keySet());
        if(kinds.isEmpty())analyticsText(media,"В этих чатах вложения пока не индексированы.",SUB,12);
        if(!kinds.isEmpty())compareHeader(media,firstName,secondName);
        for(String kind:kinds)compareMetric(media,mediaLabel(kind),a.mediaTypes.getOrDefault(kind,0L),b.mediaTypes.getOrDefault(kind,0L));
        status.setText("");status.setVisibility(View.GONE);
    }
    private void compareHeader(LinearLayout parent,String a,String b){
        LinearLayout line=row();line.setPadding(0,px(5),0,px(7));parent.addView(line,p(-1,-2));
        line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1.15f));
        for(String name:new String[]{a,b}){
            TextView label=text(name,12,ACCENT);label.setGravity(Gravity.CENTER);label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        }
    }
    private void compareMetric(LinearLayout parent,String label,long first,long second){
        LinearLayout line=row();line.setPadding(0,px(9),0,px(9));parent.addView(line,p(-1,-2));
        TextView title=text(label,13,SUB);line.addView(title,new LinearLayout.LayoutParams(0,-2,1.15f));
        for(long count:new long[]{first,second}){
            TextView value=text(String.format(Locale.ROOT,"%,d",count).replace(',',' '),16,FG);
            value.setGravity(Gravity.CENTER);value.setTypeface(null,Typeface.BOLD);value.setTextIsSelectable(true);
            line.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        }
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
        TextView exact=secondaryDisclosure("Точные значения по месяцам  ▾");target.addView(exact,p(-1,44));
        LinearLayout numbers=column();numbers.setVisibility(View.GONE);target.addView(numbers,p(-1,-2));
        for(int i=0;i<12;i++)compareMetric(numbers,String.format(Locale.ROOT,"%02d",i+1),a[i],b[i]);
        exact.setOnClickListener(v->{boolean open=numbers.getVisibility()!=View.VISIBLE;numbers.setVisibility(open?View.VISIBLE:View.GONE);exact.setText(open?"Скрыть значения  ▴":"Точные значения по месяцам  ▾");});
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
        status.setText("Общая временная линия · "+rows.size()+" записей в обзоре");
    }
    private void renderEchoPage(){
        ++loadToken;
        if(home){screen="chat";showPage();return;}
        prepareRelationshipScreen("❞ Эхо фраз",RelationshipFeatureGuide.ECHO_DETAIL);
        Button back=btn("‹ К разделу «Хронология»");list.addView(back,p(-1,44));
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
            if(periods.isEmpty())emptyWorkspace(container,"Календарных периодов нет","Отметь важные даты, даже если они находятся за пределами переписки.","Календарь");
            for(CalendarPeriod period:periods){
                LinearLayout card=column();card.setPadding(px(12),px(12),px(12),px(12));
                card.setBackground(shape(PANEL,16));
                LinearLayout.LayoutParams cardLp=p(-1,-2);
                cardLp.setMargins(px(3),px(4),px(3),px(4));container.addView(card,cardLp);
                analyticsText(card,period.name,FG,17);
                analyticsText(card,period.start+" — "+period.end,ACCENT,14);
                if(!period.note.isEmpty())analyticsText(card,period.note,FG,14);
                // One action row, rather than two tall stacked buttons per period.
                LinearLayout actions=row();actions.setPadding(0,px(7),0,0);
                card.addView(actions,p(-1,-2));
                Button edit=btn("Изменить");edit.setContentDescription("Изменить даты и описание периода "+period.name);
                actions.addView(edit,new LinearLayout.LayoutParams(0,px(44),1));
                edit.setOnClickListener(v->editCalendarPeriod(period));
                Button remove=btn("Удалить");remove.setContentDescription("Удалить период "+period.name);
                actions.addView(remove,new LinearLayout.LayoutParams(0,px(44),1));
                remove.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Удалить период?")
                    .setMessage("Удалится только личная отметка. Переписка останется.").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->worker.execute(()->{source.deleteCalendarPeriod(period.id);runOnUiThread(()->{if(key.equals(activeKey)&&"chronology".equals(screen)&&"calendar".equals(chronologySection))renderChronologyPage();});})).show());
            }
        });}catch(Exception error){runOnUiThread(()->{if(token==loadToken)analyticsText(container,"Не удалось прочитать периоды",SUB,14);});}});
    }
    /** A section hub, not a stack of every chronological report on one page. */
    private void renderChronologyPage(){
        final int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        // This page is reached directly after saving an event from the chat.
        // Reset the header: otherwise the chat-only PDF/overflow buttons leak here.
        refreshTabs();
        bottomNav.setVisibility(View.GONE);
        title.setText("Хронология");subtitle.setText("Этапы, даты и графики · офлайн");
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        list.removeAllViews();list.setBackgroundColor(BG);
        status.setVisibility(View.VISIBLE);status.setText("Загружаю выбранный раздел…");
        scroll.post(()->{if("chronology".equals(screen))scroll.scrollTo(0,0);});

        final String section=chronologySection;
        if("overview".equals(section)){
            // One consistent chronology hub. The old second menu duplicated the
            // events/stages/calendar buttons and made history feel like a maze.
            screen="relationships";
            historyEventsExpanded=false;
            renderRelationshipsPage();
            return;
        }
        // A selected section is a focused detail page, not the whole hub repeated.
        // The header already has Back. A quiet breadcrumb is enough here;
        // a second full-width primary-looking Back button dominated the page.
        TextView sections=text("‹ К хронологии",13,ACCENT);
        sections.setGravity(Gravity.CENTER_VERTICAL);
        sections.setPadding(px(13),px(9),px(13),px(9));
        sections.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227ad2ff),
            shape(Color.TRANSPARENT,11),null));
        list.addView(sections,p(-1,39));
        sections.setOnClickListener(v->{chronologySection="overview";screen="relationships";showPage();});
        String sectionTitle="stages".equals(section)?"Этапы переписки":
            "calendar".equals(section)?"Периоды по датам":
            "chart".equals(section)?"Сравнение этапов":"Раздел истории";
        TextView heading=text(sectionTitle,20,FG);heading.setTypeface(null,Typeface.BOLD);
        heading.setPadding(px(12),px(13),px(12),px(7));list.addView(heading);
        if("calendar".equals(section)){
            Button add=btn("+ Добавить период по датам");list.addView(add,p(-1,49));
            add.setOnClickListener(v->editCalendarPeriod(null));
            LinearLayout calendar=column();list.addView(calendar,p(-1,-2));
            renderCalendarPeriods(calendar,db,activeKey,token);
            status.setText("");status.setVisibility(View.GONE);
            return;
        }
        if("stages".equals(section)){
            Button fromChat=btn("+ Отметить начало периода в переписке");
            list.addView(fromChat,p(-1,49));
            fromChat.setOnClickListener(v->{screen="chat";showPage();toast("Удерживай сообщение → Отметить начало периода");});
            Button help=btn("Как создать этап  ▾");list.addView(help,p(-1,42));
            LinearLayout explanation=column();explanation.setVisibility(View.GONE);list.addView(explanation,p(-1,-2));
            analyticsText(explanation,"Удерживай первое сообщение → отметь начало периода. Затем удерживай последнее → отметь конец. Исходная переписка остаётся нетронутой.",SUB,13);
            help.setOnClickListener(v->{
                boolean open=explanation.getVisibility()!=View.VISIBLE;
                explanation.setVisibility(open?View.VISIBLE:View.GONE);
                help.setText(open?"Скрыть подсказку  ▴":"Как создать этап  ▾");
            });
        }
        final MessageDb source=db;final String archive=activeKey;
        worker.execute(()->{
            try{
                List<ConversationPeriods.Chapter> chapters=source.periods();
                LinkedHashMap<Long,ConversationPeriods.Stats> stats=new LinkedHashMap<>();
                for(ConversationPeriods.Chapter chapter:chapters)
                    stats.put(chapter.id,source.periodStats(chapter));
                runOnUiThread(()->{
                    if(isFinishing()||token!=loadToken||!archive.equals(activeKey)||db!=source
                            ||!"chronology".equals(screen)||!section.equals(chronologySection))return;
                    if("chart".equals(section)){
                        if(chapters.size()<2)
                            analyticsText(list,"Для сравнения нужны минимум два отмеченных этапа.",SUB,13);
                        else renderPeriodOverlay(chapters,stats);
                    }else if("stages".equals(section)){
                        if(chapters.isEmpty())
                            analyticsText(list,"Пока нет этапов. Создай первый через сообщение в переписке.",SUB,13);
                        long peak=1;
                        for(ConversationPeriods.Stats st:stats.values())
                            for(long n:st.bins)peak=Math.max(peak,n);
                        final long maximum=peak;
                        int order=0;
                        for(ConversationPeriods.Chapter chapter:chapters){
                            final ConversationPeriods.Stats st=stats.get(chapter.id);
                            LinearLayout card=analyticsCard((++order)+". "+chapter.name);
                            analyticsText(card,st.firstDate+" — "+st.lastDate+"  ·  "+st.messages+" сообщений",ACCENT,12);
                            Button expand=btn("Подробнее об этапе  ▾");card.addView(expand,p(-1,43));
                            LinearLayout details=column();details.setVisibility(View.GONE);card.addView(details,p(-1,-2));
                            expand.setOnClickListener(v->{
                                boolean opening=details.getVisibility()!=View.VISIBLE;
                                if(opening&&details.getChildCount()==0){
                                    if(!chapter.note.isEmpty())analyticsText(details,chapter.note,FG,13);
                                    analyticsText(details,"Медиа: "+st.media+" · активных дней: "+st.activeDays+
                                        " · сообщений в активный день: "+
                                        String.format(Locale.ROOT,"%.1f",st.dailyAverage()),FG,13);
                                    analyticsText(details,"Начало: #"+chapter.start.messageId+
                                        " · конец: #"+chapter.end.messageId,SUB,12);
                                    renderPeriodBars(details,st,maximum);
                                    // Load links only for the opened stage; every page remains reachable.
                                    LinearLayout attachments=column();details.addView(attachments,p(-1,-2));
                                    loadHistoryAttachmentPage(attachments,-chapter.id,-1,source,archive,token,0);
                                    Button link=btn("+ Прикрепить медиа");details.addView(link,p(-1,43));
                                    link.setOnClickListener(w->chooseRelationshipAttachment(-chapter.id,0));
                                    LinearLayout actions=row();details.addView(actions,p(-1,44));
                                    Button jump=btn("↗ К началу");actions.addView(jump,new LinearLayout.LayoutParams(0,-1,1f));
                                    jump.setOnClickListener(w->jump(chapter.start.messageId,"chronology"));
                                    Button remove=btn("Удалить этап");actions.addView(remove,new LinearLayout.LayoutParams(0,-1,1f));
                                    remove.setOnClickListener(w->new FontDialog.Builder(this)
                                        .setTitle("Удалить «"+chapter.name+"»?")
                                        .setMessage("Удалится только твоя отметка. Переписка и медиа останутся.")
                                        .setNegativeButton("Отмена",null)
                                        .setPositiveButton("Удалить",(d,which)->worker.execute(()->{
                                            source.removePeriod(chapter.id);
                                            runOnUiThread(()->{if(archive.equals(activeKey)&&"chronology".equals(screen))renderChronologyPage();});
                                        })).show());
                                }
                                details.setVisibility(opening?View.VISIBLE:View.GONE);
                                expand.setText(opening?"Свернуть подробности  ▴":"Подробнее об этапе  ▾");
                            });
                        }
                    }
                    status.setText("");status.setVisibility(View.GONE);
                });
            }catch(Exception e){
                runOnUiThread(()->{if(token==loadToken&&archive.equals(activeKey))
                    status.setText("Ошибка хронологии: "+e.getMessage());});
            }
        });
    }
    private void renderCinemaPage(){
        int token=++loadToken;
        if(home){screen="chat";showPage();return;}
        refreshTabs();
        title.setText("Медиа по датам");subtitle.setText("Годы и месяцы");
        filterBar.removeAllViews();searchArea.removeAllViews();
        bottomPager.setVisibility(View.GONE);verticalTimeline.setVisibility(View.GONE);
        list.removeAllViews();list.setBackgroundColor(BG);
        // The header already says "Кинолента". Keep one clear page title.
        analyticsText(list,"Выбери месяц, чтобы открыть вложения.",SUB,13);
        String returnLabel="relationships".equals(cinemaReturnScreen)?"К хронологии":
            "media".equals(cinemaReturnScreen)?"К медиатеке":
            "analytics".equals(cinemaReturnScreen)?"К анализу":
            "search".equals(cinemaReturnScreen)?"К поиску":"К переписке";
        TextView back=text("‹ "+returnLabel,13,ACCENT);
        back.setPadding(px(13),px(9),px(13),px(9));
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227ad2ff),
            shape(Color.TRANSPARENT,11),null));
        list.addView(back,p(-1,39));back.setOnClickListener(v->backWithinHistory());
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
                    String monthName=month.month;try{monthName=java.time.YearMonth.parse(month.month).format(java.time.format.DateTimeFormatter.ofPattern("LLLL",Locale.forLanguageTag("ru-RU")));}catch(Exception ignored){}
                    final String monthLabel=monthName.substring(0,1).toUpperCase(Locale.ROOT)+monthName.substring(1)+"  ·  "+month.count+" файлов";
                    TextView title=text(monthLabel+"  ▾",15,FG);
                    title.setPadding(px(13),px(12),px(8),px(12));block.addView(title,p(-1,-2));
                    LinearLayout previews=column();block.addView(previews,p(-1,-2));
                    title.setOnClickListener(v->{
                        if(previews.getVisibility()==View.VISIBLE&&previews.getChildCount()>0){previews.setVisibility(View.GONE);title.setText(monthLabel+"  ▾");return;}
                        if(previews.getChildCount()>0){previews.setVisibility(View.VISIBLE);title.setText(monthLabel+"  ▴");return;}
                        if(!title.isEnabled())return;
                        title.setEnabled(false);title.setText(monthLabel+"  ·  Загружаю…");
                        worker.execute(()->{try{List<MessageDb.Attachment> attachments=source.mediaForMonth(month.month,18);
                            runOnUiThread(()->{
                                if(token!=loadToken||!archive.equals(activeKey))return;
                                title.setEnabled(true);title.setText(monthLabel+"  ▴");
                                for(MessageDb.Attachment item:attachments){
                                    LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
                                    line.setPadding(px(10),px(5),px(10),px(5));previews.addView(line,p(-1,94));
                                    View tile=mediaTile(item,true);line.addView(tile,p(93,90));
                                    TextView detail=text(mediaLabel(item.kind)+" · "+day(item.date),13,FG);
                                    detail.setPadding(px(10),0,0,0);line.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                                    line.setOnClickListener(vv->openMedia(item));
                                    line.setOnLongClickListener(vv->{mediaActions(item);return true;});
                                }
                                if(month.count>18)analyticsText(previews,"Показаны первые 18 вложений. Для остальных используй «Медиа».",SUB,12);
                            });
                        }catch(Exception error){runOnUiThread(()->{if(token==loadToken){title.setEnabled(true);title.setText(monthLabel+"  ▾");toast("Не удалось открыть месяц");}});}});
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
        final MessageDb source=db;final String key=activeKey;final int token=loadToken;
        if(source==null||home)return;
        worker.execute(()->{
            MessageDb.Item selected=new MessageDb.Item();selected.id=messageId;
            Map<Long,List<MessageDb.Attachment>> result=source.attachmentsForMessages(java.util.Collections.singletonList(selected));
            List<MessageDb.Attachment> media=result.get(messageId);
            runOnUiThread(()->{
                if(!sameScreenRequest(source,key,token))return;
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
        if(source==null||home)return;
        worker.execute(()->{
            boolean on=source.toggleFavorite(id);
            runOnUiThread(()->{
                if(!archive.equals(activeKey)||source!=db||isFinishing()||isDestroyed()||home)return;
                toast(on?"★ Добавлено в избранное":"Убрано из избранного");
                // Refresh the filtered collection only; do not teleport the chat reader.
                if("favorites".equals(screen)&&!on){favOffset=Math.max(0,favOffset);showPage();}
            });
        });
    }
    /** Media grid is independent of the message ScrollView. RecyclerView recycles
     * thumbnails so a chat with thousands of rounds never retains thousands of views. */
    private boolean mediaGridEnabled(){
        return MediaGalleryState.supportsGrid(mediaKind)&&getPreferences(MODE_PRIVATE).getBoolean("gallery_grid_"+mediaKind,
            MediaGalleryState.defaultGrid(mediaKind));
    }
    private void updateMediaLayout(boolean preservePosition){
        if(mediaRecycler==null||mediaAdapter==null)return;
        int pos=0,offset=0;
        if(preservePosition&&mediaLayout!=null){
            pos=Math.max(0,mediaLayout.findFirstVisibleItemPosition());
            View first=mediaLayout.findViewByPosition(pos);
            if(first!=null)offset=first.getTop()-mediaRecycler.getPaddingTop();
        }
        final boolean grid=mediaGridEnabled();
        int columns=grid?MediaGalleryState.clampColumns(mediaColumns):1;
        mediaLayout=new androidx.recyclerview.widget.GridLayoutManager(this,columns);
        final int width=columns;
        mediaLayout.setSpanSizeLookup(new androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup(){
            @Override public int getSpanSize(int position){
                return mediaAdapter.isHeader(position)?width:1;
            }
        });
        mediaRecycler.setLayoutManager(mediaLayout);
        mediaRecycler.setZoom(grid,delta->changeMediaColumns(delta));
        mediaAdapter.notifyDataSetChanged();
        if(preservePosition&&mediaAdapter.getItemCount()>0)
            mediaLayout.scrollToPositionWithOffset(Math.min(pos,mediaAdapter.getItemCount()-1),offset);
        if(mediaZoomLabel!=null)mediaZoomLabel.setText(columns+" в ряд");
        if(mediaZoomSeek!=null)mediaZoomSeek.setProgress(mediaColumns-2);
    }
    private void changeMediaColumns(int delta){
        int next=MediaGalleryState.zoom(mediaColumns,delta);
        if(next==mediaColumns||!"media".equals(screen))return;
        mediaColumns=next;
        getPreferences(MODE_PRIVATE).edit().putInt("gallery_columns",next).apply();
        updateMediaLayout(true);
    }
    private void toggleMediaLayout(){
        if(!MediaGalleryState.supportsGrid(mediaKind))return;
        boolean next=!mediaGridEnabled();
        getPreferences(MODE_PRIVATE).edit().putBoolean("gallery_grid_"+mediaKind,next).apply();
        mediaAdapter.regroup();
        configureBars();
        updateMediaLayout(true);
    }
    /** The right track reflects the absolute SQLite attachment index even in paged views. */
    private void syncMediaFastScroll(){
        if(mediaFastScroller==null||mediaLayout==null||mediaAdapter==null||
            mediaTotal<=0||!"media".equals(screen)||home)return;
        int first=mediaLayout.findFirstVisibleItemPosition();
        int absolute=mediaWindowStart+mediaAdapter.ordinalAt(first);
        mediaOffset=absolute; // persisted across Activity recreation
        mediaFastScroller.setProgress(absolute,mediaTotal);
    }
    /** A fast seek opens the requested source page directly. It never copies media bytes. */
    private void jumpToMediaFraction(float fraction){
        if(mediaFastScroller==null||mediaLayout==null||mediaAdapter==null||
            !"media".equals(screen)||home||importing||db==null||mediaTotal<=0)return;
        final int target=MediaFastScroller.indexForFraction(fraction,mediaTotal);
        mediaOffset=target;
        mediaFastScroller.setProgress(target,mediaTotal);
        if(target>=mediaWindowStart&&target<mediaLoaded){
            int adapterPos=mediaAdapter.adapterPositionForOrdinal(target-mediaWindowStart);
            if(adapterPos>=0)mediaLayout.scrollToPositionWithOffset(adapterPos,0);
            return;
        }
        // Invalidate pending prefetch/append callbacks before swapping the visible window.
        final int token=++loadToken;mediaAppending=true;mediaPrepending=false;
        final String archive=activeKey,kind=mediaKind;
        final MessageDb source=db;final int expectedTotal=mediaTotal;
        status.setVisibility(View.VISIBLE);
        status.setText("Открываю файл "+(target+1)+" из "+expectedTotal+"…");
        worker.execute(()->{
            List<MessageDb.Attachment> page;
            String failure=null;
            try{page=source.mediaPage(kind,target,MediaGalleryState.PAGE_SIZE);}
            catch(Exception error){page=java.util.Collections.emptyList();failure=error.getMessage();}
            final List<MessageDb.Attachment> results=page;
            final String problem=failure;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||token!=loadToken||db!=source||
                    !archive.equals(activeKey)||!"media".equals(screen)||
                    !kind.equals(mediaKind))return;
                mediaAppending=false;
                if(problem!=null){
                    status.setText("Не удалось перейти к выбранному файлу: "+problem);
                    syncMediaFastScroll();return;
                }
                if(results.isEmpty()){
                    status.setText("В этом месте больше нет файлов. Обнови медиатеку.");
                    syncMediaFastScroll();return;
                }
                mediaWindowStart=target;
                mediaLoaded=target+results.size();
                mediaAdapter.reset();mediaAdapter.append(results);
                updateMediaLayout(false);
                mediaLayout.scrollToPositionWithOffset(0,0);
                mediaFastScroller.setProgress(target,mediaTotal);
                status.setText("Файл "+(target+1)+" из "+mediaTotal+
                    " · перелистывай или двигай ползунок справа");
                scheduleMediaPrefetch(token,kind,mediaLoaded,mediaTotal);
                mediaRecycler.post(this::maybeAppendMedia);
            });
        });
    }
    /** Prepend earlier pages when scrolling up after a fast seek. Preserve reading position. */
    private void maybePrependMedia(){
        if(mediaRecycler==null||mediaLayout==null||mediaAdapter==null||
            mediaAppending||mediaPrepending||mediaWindowStart<=0||
            !"media".equals(screen)||home||importing||db==null)return;
        int first=mediaLayout.findFirstVisibleItemPosition();
        if(first>8)return;
        mediaPrepending=true;
        final int start=Math.max(0,mediaWindowStart-MediaGalleryState.PAGE_SIZE);
        final int amount=mediaWindowStart-start;
        final String archive=activeKey,kind=mediaKind;
        final MessageDb source=db;final int token=loadToken;
        worker.execute(()->{
            List<MessageDb.Attachment> earlier;
            try{earlier=source.mediaPage(kind,start,amount);}
            catch(Exception error){
                runOnUiThread(()->{
                    if(token==loadToken&&db==source&&"media".equals(screen)&&kind.equals(mediaKind)){
                        mediaPrepending=false;
                        status.setText("Не удалось загрузить более ранние файлы");
                    }
                });return;
            }
            final List<MessageDb.Attachment> prior=earlier;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed()||token!=loadToken||db!=source||
                    !archive.equals(activeKey)||!"media".equals(screen)||!kind.equals(mediaKind))return;
                mediaPrepending=false;
                if(prior.isEmpty())return;
                int firstVisible=Math.max(0,mediaLayout.findFirstVisibleItemPosition());
                int ordinal=mediaAdapter.ordinalAt(firstVisible);
                View firstView=mediaLayout.findViewByPosition(firstVisible);
                int relativeTop=firstView==null?0:firstView.getTop()-mediaRecycler.getPaddingTop();
                java.util.ArrayList<MessageDb.Attachment> combined=new java.util.ArrayList<>(prior);
                combined.addAll(mediaAdapter.loadedAttachments());
                mediaWindowStart=start;
                mediaAdapter.reset();mediaAdapter.append(combined);
                int anchor=mediaAdapter.adapterPositionForOrdinal(prior.size()+ordinal);
                if(anchor>=0)mediaLayout.scrollToPositionWithOffset(anchor,relativeTop);
                syncMediaFastScroll();
                status.setText("Открыт участок "+(mediaWindowStart+1)+"–"+mediaLoaded+
                    " из "+mediaTotal+" · ползунок справа");
            });
        });
    }
    /** Fetch only the next 30 from SQLite; never reset scroll when the next page arrives. */
    private void maybeAppendMedia(){
        if(mediaRecycler==null||mediaLayout==null||mediaAppending
            ||mediaPrepending||!"media".equals(screen)||home||importing||db==null
            ||mediaLoaded>=mediaTotal||mediaAdapter.getItemCount()==0)return;
        int last=mediaLayout.findLastVisibleItemPosition();
        if(last<mediaAdapter.getItemCount()-10)return;
        mediaAppending=true;
        final MessageDb source=db;
        final String archive=activeKey,filter=mediaKind;
        final int token=loadToken,from=mediaLoaded;
        worker.execute(()->{
            List<MessageDb.Attachment> next;
            try{next=source.mediaPage(filter,from,MediaGalleryState.PAGE_SIZE);}
            catch(Exception error){
                runOnUiThread(()->{
                    if(token==loadToken&&db==source&&"media".equals(screen)&&filter.equals(mediaKind)){
                        mediaAppending=false;status.setText("Не удалось догрузить медиа. Прокрути ещё раз.");
                    }
                });return;
            }
            runOnUiThread(()->{
                if(isFinishing()||token!=loadToken||db!=source||!archive.equals(activeKey)
                    ||!"media".equals(screen)||!filter.equals(mediaKind))return;
                mediaAdapter.append(next);
                mediaLoaded+=next.size();
                if(next.isEmpty())mediaLoaded=mediaTotal;
                mediaAppending=false;
                syncMediaFastScroll();
                status.setText("Открыт участок "+(mediaWindowStart+1)+"–"+mediaLoaded+
                    " из "+mediaTotal+" · ползунок справа");
                if(mediaLoaded<mediaTotal)mediaRecycler.post(this::maybeAppendMedia);
            });
        });
    }
    /** Every header spans the full grid; dates group round/voice/audio galleries. */
    private final class MediaGalleryAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>{
        private final ArrayList<Object> entries=new ArrayList<>();
        // Maps zero-based attachment ordinals to adapter positions; headers are skipped.
        private final ArrayList<Integer> attachmentPositions=new ArrayList<>();
        private String lastGroup=null;
        void reset(){entries.clear();attachmentPositions.clear();lastGroup=null;notifyDataSetChanged();}
        java.util.ArrayList<MessageDb.Attachment> loadedAttachments(){
            java.util.ArrayList<MessageDb.Attachment> out=new java.util.ArrayList<>();
            for(Object item:entries)if(item instanceof MessageDb.Attachment)
                out.add((MessageDb.Attachment)item);
            return out;
        }
        int ordinalAt(int adapterPosition){
            if(attachmentPositions.isEmpty())return 0;
            int n=java.util.Collections.binarySearch(attachmentPositions,Math.max(0,adapterPosition));
            if(n<0)n=-n-2;return Math.max(0,n);
        }
        int adapterPositionForOrdinal(int ordinal){
            return ordinal<0||ordinal>=attachmentPositions.size()?-1:attachmentPositions.get(ordinal);
        }
        boolean isHeader(int position){return position>=0&&position<entries.size()&&entries.get(position) instanceof String;}
        void regroup(){
            ArrayList<MessageDb.Attachment> all=new ArrayList<>();
            for(Object item:entries)if(item instanceof MessageDb.Attachment)
                all.add((MessageDb.Attachment)item);
            entries.clear();attachmentPositions.clear();lastGroup=null;
            for(MessageDb.Attachment item:all){
                boolean byDay=mediaGridEnabled()&&
                    ("round".equals(mediaKind)||"voice".equals(mediaKind)||"audio".equals(mediaKind));
                String group=byDay?day(item.date):MediaTimeline.month(item.date);
                if(group==null||group.isEmpty())group="Без даты";
                if(!group.equals(lastGroup)){entries.add(group);lastGroup=group;}
                attachmentPositions.add(entries.size());
                entries.add(item);
            }
            // Dataset was replaced (not inserted); notify accordingly to avoid
            // RecyclerView's invalid holder positions during mode switches.
            notifyDataSetChanged();
        }
        void append(List<MessageDb.Attachment> page){
            int from=entries.size();
            for(MessageDb.Attachment item:page){
                boolean byDay=mediaGridEnabled()&&
                    ("round".equals(mediaKind)||"voice".equals(mediaKind)||"audio".equals(mediaKind));
                String group=byDay?day(item.date):MediaTimeline.month(item.date);
                if(group==null||group.isEmpty())group="Без даты";
                if(!group.equals(lastGroup)){
                    entries.add(group);
                    lastGroup=group;
                }
                attachmentPositions.add(entries.size());
                entries.add(item);
            }
            if(entries.size()>from)notifyItemRangeInserted(from,entries.size()-from);
        }
        @Override public int getItemCount(){return entries.size();}
        @Override public int getItemViewType(int position){return isHeader(position)?0:1;}
        final class TileHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            final FrameLayout tile;
            final ImageView image;
            final TextView symbol,detail,selected;
            TileHolder(FrameLayout frame){
                super(frame);tile=frame;
                image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
                symbol=text("",24,FG);symbol.setGravity(Gravity.CENTER);
                frame.addView(symbol,new FrameLayout.LayoutParams(-1,-1));
                detail=text("",11,FG);detail.setSingleLine(true);
                detail.setEllipsize(TextUtils.TruncateAt.END);
                detail.setBackgroundColor(0xb600101a);
                detail.setPadding(px(3),px(2),px(3),px(2));
                frame.addView(detail,new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.BOTTOM));
                selected=text("✓",18,FG);selected.setGravity(Gravity.CENTER);
                selected.setBackground(shape(0xff247e8a,100));
                frame.addView(selected,new FrameLayout.LayoutParams(px(24),px(24),Gravity.TOP|Gravity.END));
            }
        }
        @Override public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(
                android.view.ViewGroup parent,int type){
            if(type==0){
                TextView header=text("",14,FG);header.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
                header.setPadding(px(10),px(14),px(4),px(7));
                header.setBackgroundColor(BG);
                header.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,-2));
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(header){};
            }
            FrameLayout tile=new FrameLayout(MainActivity.this);
            tile.setBackground(outlined(PANEL,10,false));
            tile.setClipToOutline(true);
            tile.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,px(90)));
            return new TileHolder(tile);
        }
        @Override public void onBindViewHolder(androidx.recyclerview.widget.RecyclerView.ViewHolder holder,int position){
            Object entry=entries.get(position);
            if(entry instanceof String){((TextView)holder.itemView).setText((String)entry);return;}
            MessageDb.Attachment a=(MessageDb.Attachment)entry;
            TileHolder h=(TileHolder)holder;
            boolean grid=mediaGridEnabled();
            int width=mediaRecycler.getWidth()-mediaRecycler.getPaddingLeft()-mediaRecycler.getPaddingRight();
            if(width<=0)width=getResources().getDisplayMetrics().widthPixels-px(24);
            int side=MediaGalleryState.tilePx(width,mediaColumns,px(2));
            androidx.recyclerview.widget.RecyclerView.LayoutParams params=
                new androidx.recyclerview.widget.RecyclerView.LayoutParams(-1,grid?side+px(3):px(72));
            params.setMargins(px(2),px(2),px(2),px(2));
            h.tile.setLayoutParams(params);
            h.tile.setContentDescription(mediaLabel(a.kind)+" · "+day(a.date)+
                " · нажми для открытия, удерживай для действий");
            h.image.setImageDrawable(null);h.image.setTag(null);
            h.image.setClipToOutline(false);h.image.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            h.image.setVisibility(View.GONE);
            h.symbol.setVisibility(View.GONE);
            h.detail.setTag(null);
            h.detail.setVisibility(View.VISIBLE);
            h.detail.setTextColor(grid?Color.WHITE:FG);
            h.detail.setBackgroundColor(grid?0xb600101a:Color.TRANSPARENT);
            h.selected.setTextColor(Color.WHITE);
            h.selected.setVisibility(mediaSelection.containsKey(a.id)?View.VISIBLE:View.GONE);
            if(grid){
                h.image.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
                h.symbol.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
                h.detail.setLayoutParams(new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.BOTTOM));
                h.detail.setTextSize(mediaColumns>=7?8:11);
            }else{
                h.image.setLayoutParams(new FrameLayout.LayoutParams(px(70),-1,Gravity.START));
                h.symbol.setLayoutParams(new FrameLayout.LayoutParams(px(70),-1,Gravity.START));
                FrameLayout.LayoutParams detailParams=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER_VERTICAL);
                detailParams.leftMargin=px(80);h.detail.setLayoutParams(detailParams);
                h.detail.setTextSize(13);
            }
            boolean picture="image".equals(a.kind)||"video".equals(a.kind)
                ||"gif".equals(a.kind)||"sticker".equals(a.kind)||"round".equals(a.kind);
            if(picture){
                h.image.setVisibility(View.VISIBLE);
                if("round".equals(a.kind)){
                    h.image.setOutlineProvider(new ViewOutlineProvider(){
                        @Override public void getOutline(View v,Outline o){
                            o.setOval(0,0,v.getWidth(),v.getHeight());
                        }
                    });
                    h.image.setClipToOutline(true);
                }
                if("sticker".equals(a.kind)&&isTgsPath(a.path)){
                    // Keep TGS animation overlays out of recycled view holders.
                    // Tapping still opens the full local animated sticker.
                    h.symbol.setVisibility(View.VISIBLE);h.symbol.setText("✦");
                }else loadThumbnail(a.path,"round".equals(a.kind)?"round":
                    isVideoStickerPath(a.path)?"video":a.kind,h.image);
            }else{
                h.symbol.setVisibility(View.VISIBLE);
                h.symbol.setText("voice".equals(a.kind)?"▂▆▃▇": "audio".equals(a.kind)?"♫":"▤");
                h.symbol.setTextSize(grid?Math.max(14,Math.min(29,side/4)):24);
            }
            boolean video="video".equals(a.kind)||"round".equals(a.kind);
            if(video){
                h.detail.setText("▶");
                if(!grid||side>=px(70))loadDuration(a.path,h.detail,"▶ ");
            }else if(grid)h.detail.setText("voice".equals(a.kind)?"Голос":"audio".equals(a.kind)?"Музыка":
                "gif".equals(a.kind)?"GIF":"sticker".equals(a.kind)?"Стикер":"");
            else h.detail.setText(mediaLabel(a.kind)+" · "+day(a.date)+" · "+clip(filename(a.path),24));
            if(grid&&("image".equals(a.kind)))h.detail.setVisibility(View.GONE);
            h.tile.setOnClickListener(v->{
                if(multiSelectMedia){
                    toggleMediaSelection(a);
                    h.selected.setVisibility(mediaSelection.containsKey(a.id)?View.VISIBLE:View.GONE);
                }else openMedia(a);
            });
            h.tile.setOnLongClickListener(v->{
                if(multiSelectMedia){toggleMediaSelection(a);h.selected.setVisibility(mediaSelection.containsKey(a.id)?View.VISIBLE:View.GONE);}else mediaActions(a);
                return true;
            });
        }
    }
    private void renderEmptyMediaState(){
        boolean all="all".equals(mediaKind);
        String detail=all?"В этой переписке нет зарегистрированных вложений. Импорт другого архива не добавит файлы в этот чат.":
            "В выбранной категории нет вложений. Открой все файлы, чтобы проверить остальные типы.";
        emptyWorkspace(list,all?"В переписке нет медиа":"Нет файлов этой категории",detail,"Медиа");
        if(!all){
            Button showAll=btn("Показать все файлы");LinearLayout.LayoutParams bounds=p(-1,48);
            bounds.setMargins(px(3),px(2),px(3),px(8));list.addView(showAll,bounds);
            showAll.setOnClickListener(v->{mediaKind="all";mediaOffset=0;showPage();});
        }
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
        // The gallery is inside a padded scrolling column, which can be far
        // narrower than the display (split-screen, narrow phones, tablets).
        int availableWidth=list.getWidth()-list.getPaddingLeft()-list.getPaddingRight();
        if(availableWidth<=0)
            availableWidth=Math.max(px(96),getResources().getDisplayMetrics().widthPixels-
                px(24)-list.getPaddingLeft()-list.getPaddingRight());
        MediaGridSizing.Spec gridSpec=MediaGridSizing.fit(availableWidth,px(96),px(2));
        String currentMonth=null;GridLayout gallery=null;
        for(MessageDb.Attachment a:media){
            String month=MediaTimeline.month(a.date);
            if(!month.equals(currentMonth)){
                currentMonth=month;
                TextView group=text("▦  "+month,15,FG);group.setTypeface(null,Typeface.BOLD);
                group.setPadding(px(12),px(15),px(12),px(7));list.addView(group,p(-1,-2));
                if(grid){gallery=new GridLayout(this);gallery.setColumnCount(gridSpec.columns);list.addView(gallery,p(-1,-2));}
            }
            if(grid){
                FrameLayout tile=new FrameLayout(this);tile.setBackground(outlined(PANEL,13,false));tile.setClipToOutline(true);
                GridLayout.LayoutParams t=new GridLayout.LayoutParams();
                t.width=gridSpec.tilePx;t.height=gridSpec.tilePx;
                t.setMargins(gridSpec.marginPx,gridSpec.marginPx,gridSpec.marginPx,gridSpec.marginPx);
                gallery.addView(tile,t);
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
            if(a.kind.equals("video")||isVideoStickerPath(a.path)||a.kind.equals("gif")&&a.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$")){TextView play=text("▶",24,Color.WHITE);play.setGravity(Gravity.CENTER);play.setBackground(shape(0xbb0b111b,100));FrameLayout.LayoutParams playSize=new FrameLayout.LayoutParams(px(52),px(52),Gravity.CENTER);box.addView(play,playSize);}
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
        TextView overlay=text("▶",size>130?38:28,Color.WHITE);overlay.setGravity(Gravity.CENTER);
        overlay.setBackgroundColor(0x38000000);holder.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout wrap=column();wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        wrap.addView(holder,p(size,size));
        TextView duration=text("00:00",12,SUB);duration.setGravity(Gravity.CENTER);wrap.addView(duration,p(size,24));
        loadDuration(a.path,duration);
        RoundControls controls=new RoundControls();
        controls.sourceAttachmentId=a.id;
        controls.timer=duration;controls.preview=preview;controls.overlay=overlay;
        if(chatCircle){
            SeekBar seek=new SeekBar(this);seek.setMax(1000);seek.setProgress(0);
            seek.setVisibility(View.GONE);wrap.addView(seek,p(size,34));controls.seek=seek;
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onStartTrackingTouch(SeekBar bar){
                    controls.dragging=true;controls.wasPlaying=false;
                    try{if(player!=null&&RoundPlaybackTarget.isSameTarget(playingPath,a.path,inlineHolder,holder)){
                        controls.wasPlaying=player.isPlaying();if(controls.wasPlaying)player.pause();
                    }}catch(Exception ignored){}
                }
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){
                    if(!user)return;
                    try{if(player!=null&&RoundPlaybackTarget.isSameTarget(playingPath,a.path,inlineHolder,holder)){
                        int length=Math.max(0,player.getDuration());
                        duration.setText(formatTime(Math.round(length*value/1000f)/1000)+" / "+formatTime(length/1000));
                    }}catch(Exception ignored){}
                }
                @Override public void onStopTrackingTouch(SeekBar bar){
                    try{if(player!=null&&RoundPlaybackTarget.isSameTarget(playingPath,a.path,inlineHolder,holder)){
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
        holder.setOnTouchListener(new View.OnTouchListener(){
            private boolean pinch;
            @Override public boolean onTouch(View v,MotionEvent e){
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN)pinch=false;
                videoZoom.onTouchEvent(e);
                if(e.getPointerCount()>1||action==MotionEvent.ACTION_POINTER_DOWN){
                    if(!pinch){v.cancelLongPress();v.setPressed(false);}
                    pinch=true;
                }
                if(pinch){
                    if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                        pinch=false;v.cancelLongPress();v.setPressed(false);
                        MotionEvent cancel=MotionEvent.obtain(e);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        try{v.onTouchEvent(cancel);}finally{cancel.recycle();}
                    }
                    return true;
                }
                // Normal tap/long press uses the View's standard accessible click handling.
                return false;
            }
        });
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
            // Replace an earlier floating video rather than leaving its stale window visible.
            if(floatingPanel!=null)closeFloatingRound();
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
        final SeekBar voiceSeek;
        if("voice".equals(a.kind)){
            voiceSeek=new SeekBar(this);voiceSeek.setMax(1000);voiceSeek.setEnabled(false);
            voiceSeek.setContentDescription("Перемотка голосового сообщения");
            details.addView(voiceSeek,p(-1,25));
            voiceSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                @Override public void onProgressChanged(SeekBar bar,int value,boolean user){}
                @Override public void onStartTrackingTouch(SeekBar bar){voiceSeekDragging=true;}
                @Override public void onStopTrackingTouch(SeekBar bar){
                    voiceSeekDragging=false;
                    if(player==null||activeVoiceSeek!=bar||!a.path.equals(playingPath))return;
                    try{player.seekTo((int)(Math.max(0,player.getDuration())*(long)bar.getProgress()/1000));}
                    catch(IllegalStateException ignored){}
                }
            });
        }else{
            voiceSeek=null;
            TextView bars=text("▂▆▃▇▄▅▂▆▃▄▇▂▅",gallery?11:15,ACCENT);
            bars.setSingleLine(true);details.addView(bars);
        }
        TextView duration=text("00:00",11,SUB);details.addView(duration);loadDuration(a.path,duration);
        play.setOnClickListener(v->{
            if("audio".equals(a.kind)){openMusicPlayer(a);return;}
            startAudio(a.path,play,duration,voiceSeek);
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
                hideGalleryOverlay();
                scroll.setVisibility(View.VISIBLE);
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
    private AlertDialog mediaActions(MessageDb.Attachment a){
        boolean round="round".equals(a.kind);
        MediaSavePolicy.Spec spec=MediaSavePolicy.plan(a.path,a.kind,false);
        ArrayList<String> names=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
        if(round){
            names.add("Плавающий кружок");actions.add(()->showFloatingRound(a));
            names.add("Полный экран / PiP");actions.add(()->openRoundPlayer(a));
            names.add("Внешний плеер");actions.add(()->resolveMedia(a.path,uri->external(uri,a.path)));
        }else{
            names.add("Открыть вложение");actions.add(()->openMedia(a));
        }
        names.add("Перейти к сообщению");actions.add(()->jump(a.messageId,"media".equals(screen)?"media":""));
        names.add("Изменить отметку «Избранное»");actions.add(()->toggleStar(a.messageId));
        String saveLabel=spec.destination==MediaSavePolicy.Destination.PICTURES||spec.destination==MediaSavePolicy.Destination.MOVIES
            ?"Сохранить в галерею":spec.destination==MediaSavePolicy.Destination.MUSIC?"Сохранить в музыку":"Сохранить в Загрузки";
        names.add(saveLabel);actions.add(()->saveAttachment(a,false));
        if(spec.destination!=MediaSavePolicy.Destination.DOWNLOADS){
            names.add("Сохранить в Загрузки");actions.add(()->saveAttachment(a,true));
        }
        names.add("Выбрать несколько файлов");actions.add(()->{
            if(!activeKey.equals(selectedMediaArchive))clearMediaSelection();
            multiSelectMedia=true;selectedMediaArchive=activeKey;
            toggleMediaSelection(a);screen="media";showPage();
        });
        names.add("Добавить в коллекцию");actions.add(()->addMediaToCollection(java.util.Collections.singletonList(a)));
        return new FontDialog.Builder(this).setTitle(mediaLabel(a.kind)+" · "+day(a.date))
            .setItems(names.toArray(new String[0]),(dlg,which)->actions.get(which).run())
            .setNegativeButton("Закрыть",null).show();
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
        final int requestedToken=loadToken;
        final String requestedScreen=screen;
        final int resultScroll="search".equals(source)&&"search".equals(screen)?scroll.getScrollY():-1;
        final int analysisScroll="analytics".equals(source)&&"analytics".equals(screen)?scroll.getScrollY():-1;
        if(requestedDb==null)return;
        worker.execute(()->{
            if(!requestedArchive.equals(activeKey)||db!=requestedDb)return;
            int at=requestedDb.hasMessage(id)?requestedDb.positionFor(id):-1;
            // A search/reply/media jump must not rotate the pinned-message
            // selector. Only explicit pinned controls should select another pin.
            runOnUiThread(()->{
                if(!requestedArchive.equals(activeKey)||db!=requestedDb||isFinishing()||requestedToken!=loadToken||!requestedScreen.equals(screen))return;
                if(at<0){toast("Сообщение не найдено");if("chat".equals(screen)&&list.getChildCount()==0){returnScreen=source;showPage();}return;}
                home=false;autoLoaded=PAGE;
                if(resultScroll>=0)searchReturnScroll=resultScroll;
                if(analysisScroll>=0)analyticsReturnScroll=analysisScroll;
                // An explicit jump must take precedence over a saved tab position.
                pendingRestoreId=-1;
                returnScreen=source;screen="chat";chatOffset=Math.max(0,at-7);highlightId=id;showPage();
            });
        });
    }
    private void restoreReturn(){
        if(returnScreen.isEmpty())return;
        String target=returnScreen;
        closeFloatingRound();stopPlayback();
        returnScreen="";highlightId=-1;screen=target;home=false;
        if("search".equals(target)){
            pendingSearchScroll=searchReturnScroll;searchReturnScroll=-1;
            if(searchReturnsToAnalytics)returnScreen="analytics";
        }
        if("analytics".equals(target)){
            pendingAnalyticsScroll=analyticsReturnScroll;analyticsReturnScroll=-1;searchReturnsToAnalytics=false;
        }
        autoFloating=false;showPage();
    }

    private void resolveMedia(String path,java.util.function.Consumer<Uri> success){
        resolveMedia(path,success,null);
    }
    private void resolveMedia(String path,java.util.function.Consumer<Uri> success,Runnable onMissing){
        if(folder==null){
            toast("Сначала выбери папку с экспортом");
            if(onMissing!=null)handler.post(onMissing);
            return;
        }
        DocumentFolder current=folder;
        final String requestedArchive=activeKey;
        final int requestedLoad=loadToken;
        final int requestedPlayback=playbackGeneration;
        mediaWorker.execute(()->{
            Uri result=null;try{result=current.resolve(path);}catch(Exception ignored){}
            Uri uri=result;runOnUiThread(()->{
                // A slow SAF provider must never open media from a previously
                // selected chat after navigation or player closure.
                if(isFinishing()||isDestroyed()||!MediaRequestGuard.isCurrent(requestedArchive,activeKey,
                        requestedLoad,loadToken,requestedPlayback,playbackGeneration))return;
                if(uri==null){
                    status.setText("Файл не найден: "+path);
                    toast("Медиа не найдено в выбранной папке");
                    if(onMissing!=null)onMissing.run();
                }else success.accept(uri);
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
        else if(a.kind.equals("voice")){
            // Voice messages use the same built-in offline player whether the
            // user taps the play icon or the media-gallery card.
            startAudio(a.path,playerPause,playerText);
            playingAttachment=a;playerStrip.setVisibility(View.VISIBLE);
            playerText.setText("Голосовое · "+(a.author==null?"":a.author));
        }else if(ArchiveReadOnly.isArchive(a.path)||OfficePreviewText.supported(a.path))
            resolveMedia(a.path,uri->showReadOnlyPreview(uri,a.path));
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
                    TgsPrecompSafety.requireAcyclic(payload);
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
                TgsPrecompSafety.requireAcyclic(obj);
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
        // Children + padding exceed 114dp, so the fixed container clipped the
        // timecode and made playback controls look stacked/overlapped.
        frame.addView(bottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
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
        inlineHolder=null;playingAttachment=null;activeAudioButton=null;
        if(activeVoiceSeek!=null){activeVoiceSeek.setEnabled(false);activeVoiceSeek.setProgress(0);activeVoiceSeek=null;}
        voiceSeekDragging=false;
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
        startInlineRound(path,holder,poster,overlay,0);
    }
    private void startInlineRound(String path,FrameLayout holder,ImageView poster,TextView overlay,int resumeMs){
        startInlineRound(path,holder,poster,overlay,resumeMs,true);
    }
    private void startInlineRound(String path,FrameLayout holder,ImageView poster,TextView overlay,int resumeMs,boolean resumePlaying){
        // A repeated Telegram MP4 path does not identify a specific message.
        // Only tapping the active tile toggles the current decoder.
        if(player!=null&&RoundPlaybackTarget.isSameTarget(playingPath,path,inlineHolder,holder)){
            try{
                if(player.isPlaying()){player.pause();overlay.setVisibility(View.VISIBLE);}
                else{player.start();overlay.setVisibility(View.GONE);}
                if(playerPause!=null)playerPause.setText(player.isPlaying()?"⏸":"▶");
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
                            if(resumeMs>0){
                                try{mp.seekTo(Math.min(resumeMs,Math.max(0,mp.getDuration()-1)),MediaPlayer.SEEK_CLOSEST);}
                                catch(IllegalStateException failure){toast("Не удалось восстановить позицию видео");}
                            }
                            mp.start();
                            if(!resumePlaying)mp.pause();
                            if(playerPause!=null)playerPause.setText(mp.isPlaying()?"⏸":"▶");
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
        },()->{
            // A missing original is common in Telegram exports without media.
            // Never leave a fake "playing" strip when no decoder exists.
            if(generation==playbackGeneration){
                stopPlayback();
                overlay.setText("⚠ Нет файла");
                overlay.setTextSize(14);
                overlay.setVisibility(View.VISIBLE);
            }
        });
    }
    private void startAudio(String path,TextView play,TextView timer){startAudio(path,play,timer,null);}
    private void startAudio(String path,TextView play,TextView timer,SeekBar voiceSeek){
        if(player!=null&&path.equals(playingPath)&&activeAudioButton==play){
            try{if(player.isPlaying()){player.pause();play.setText("▶");}else{player.start();play.setText("❚❚");}
                if(playerPause!=null)playerPause.setText(player.isPlaying()?"⏸":"▶");
            }catch(Exception ignored){}
            return;
        }
        stopPlayback();final int request=playbackGeneration;resolveMedia(path,uri->{
            if(request!=playbackGeneration||isFinishing()||isDestroyed()||!play.isAttachedToWindow())return;
            try{
                MediaPlayer p=new MediaPlayer();player=p;playingPath=path;activeAudioButton=play;activeVoiceSeek=voiceSeek;
                p.setDataSource(this,uri);
                p.setOnPreparedListener(mp->{if(mp!=player)return;mp.start();play.setText("❚❚");
                    if(activeVoiceSeek!=null)activeVoiceSeek.setEnabled(true);
                    if(playerPause!=null)playerPause.setText("⏸");
                    progressUpdater=new Runnable(){@Override public void run(){
                        if(mp!=player)return;
                        try{int seconds=mp.getCurrentPosition()/1000;
                            int durationMs=mp.getDuration();int len=durationMs/1000;
                            if(activeVoiceSeek!=null&&!voiceSeekDragging&&durationMs>0)
                                activeVoiceSeek.setProgress((int)(1000L*mp.getCurrentPosition()/durationMs));
                            timer.setText(formatTime(seconds)+" / "+formatTime(len));
                            handler.postDelayed(this,500);
                        }catch(Exception ignored){}
                    }};handler.post(progressUpdater);
                });
                p.setOnCompletionListener(mp->{if(mp!=player)return;play.setText("▶");stopPlayback();});
                p.setOnErrorListener((mp,what,extra)->{if(mp!=player)return true;toast("Не удалось воспроизвести аудио");stopPlayback();play.setText("▶");return true;});
                p.prepareAsync();
            }catch(Exception e){stopPlayback();toast("Аудиофайл недоступен");}
        },()->{
            if(request==playbackGeneration){
                stopPlayback();
                play.setText("▶");
                timer.setText("Файл не найден");
            }
        });
    }
    private String formatTime(int s){return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private void loadDuration(String path,TextView target){
        loadDuration(path,target,"");
    }
    private void loadDuration(String path,TextView target,String prefix){
        if(folder==null)return;
        DocumentFolder current=folder;int token=loadToken;
        String requestedArchive=activeKey;
        String root=current.rootId();
        String cacheKey=requestedArchive.length()+":"+requestedArchive+root.length()+":"+root+path;
        target.setTag(cacheKey);
        String cached=durations.get(cacheKey);
        if(cached!=null){target.setText(prefix+cached);return;}
        mediaWorker.execute(()->{
            String time=null;
            try{Uri uri=current.resolve(path);if(uri!=null){MediaMetadataRetriever reader=new MediaMetadataRetriever();
                try{reader.setDataSource(this,uri);String raw=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(raw!=null)time=formatTime((int)(Long.parseLong(raw)/1000));}finally{reader.release();}
            }}catch(Exception ignored){}
            String result=time;
            runOnUiThread(()->{
                if(!isFinishing()&&token==loadToken&&current==folder&&requestedArchive.equals(activeKey)){
                    // A missing or temporarily inaccessible original must not poison the cache.
                    if(result!=null)durations.put(cacheKey,result);
                    if(cacheKey.equals(target.getTag()))
                        target.setText(prefix+(result==null?"—":result));
                }
            });
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
        caption.setSingleLine(true);caption.setEllipsize(TextUtils.TruncateAt.END);
        // The 48dp close action is an overlay. Do not render the filename below it.
        caption.setPadding(px(54),0,px(54),0);
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
        startRoundPlayback(attachment,target,poster,overlay,0);
    }
    private void startRoundPlayback(MessageDb.Attachment attachment,FrameLayout target,ImageView poster,TextView overlay,int resumeMs){
        startRoundPlayback(attachment,target,poster,overlay,resumeMs,true);
    }
    private void startRoundPlayback(MessageDb.Attachment attachment,FrameLayout target,ImageView poster,TextView overlay,int resumeMs,boolean resumePlaying){
        startInlineRound(attachment.path,target,poster,overlay,resumeMs,resumePlaying);
        playingAttachment=attachment;
        inlineHolder=target;
        if(playerStrip!=null){
            playerStrip.setVisibility(View.VISIBLE);
            playerText.setText("◉ "+(attachment.author==null||attachment.author.trim().isEmpty()?"Видеосообщение":attachment.author));
            boolean actuallyPlaying=false;
            try{actuallyPlaying=player!=null&&player.isPlaying();}catch(Exception ignored){}
            playerPause.setText(actuallyPlaying?"⏸":"▶");
        }
    }
    private void toggleActivePlayback(){
        if(player==null)return;
        try{
            if(player.isPlaying())player.pause();else player.start();
            boolean playing=player.isPlaying();
            if(playerPause!=null)playerPause.setText(playing?"⏸":"▶");
            if(activeAudioButton!=null&&activeAudioButton!=playerPause)
                activeAudioButton.setText(playing?"❚❚":"▶");
        }catch(Exception ignored){}
    }
    private void playAdjacent(boolean forward){
        if(playingAttachment==null)return;
        final MessageDb.Attachment current=playingAttachment;
        final MessageDb source=db;
        final String requestedArchive=activeKey;
        final int requestedPlayback=playbackGeneration;
        worker.execute(()->{
            // Skip queued work once closing or switching invalidated this request.
            if(requestedPlayback!=playbackGeneration)return;
            MessageDb.Attachment found=source.adjacentMedia(current.kind,current.messageId,current.id,forward);
            runOnUiThread(()->{
                // A delayed query must not reopen media after closing the player,
                // switching chats, or starting another message in this chat.
                if(isFinishing()||isDestroyed()||source!=db||!requestedArchive.equals(activeKey)||
                    requestedPlayback!=playbackGeneration||playingAttachment!=current)return;
                if(found==null){toast("Других сообщений нет");return;}
                if(found.kind.equals("round"))showFloatingRound(found);
                else if(found.kind.equals("voice")||found.kind.equals("audio")){
                    startAudio(found.path,playerPause,playerText);
                    playingAttachment=found;playerStrip.setVisibility(View.VISIBLE);
                }
            });
        });
    }
    private void settingsMenu(){new FontDialog.Builder(this).setTitle("Настройки переписки")
        .setItems(new String[]{"Скругление сообщений","Плотность сообщений","Моё имя (сообщения справа)",
            "Цвет одного участника","Один цвет нескольким участникам"},(d,i)->{
            if(i==0){bubbleStyleDialog();return;}
            if(i==1){densityDialog();return;}
            if(home){toast("Открой нужную переписку и вернись в настройки");return;}
            if(i==2)identityDialog();else if(i==3)participantSettings();else if(i==4)groupedParticipantColors();
        }).setPositiveButton("Закрыть",null).show();}
    private String displayDocumentName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        return uri.getLastPathSegment()==null?"Резервная копия":uri.getLastPathSegment();
    }
    private void backupPicker(){
        new FontDialog.Builder(this).setTitle("Создать резервную копию")
            .setItems(new String[]{"ПЕРЕЕЗД · Полная копия со всеми медиа и заметками","КОМПАКТНАЯ · Без медиа и вложений заметок"},(dlg,choice)->{
                fullBackupMode=choice==0;
                // Keep the essential migration warning accessible in a separate
                // confirmation dialog rather than hiding the type-choice list.
                String explanation=fullBackupMode?
                    "Полная копия включает медиа, чаты, аватары, обои, избранное, цитаты, этапы истории и файлы заметок. Перед удалением оригиналов проверь копию.":
                    "Компактная копия сохраняет базы и настройки, но НЕ включает медиа и вложения заметок. Для переноса на другой телефон выбери полную копию.";
                // The selection dialog automatically dismisses AFTER invoking its
                // item listener. Open the confirmation on the next UI-loop turn so
                // the original dismissal cannot obscure the new dialog.
                handler.post(()->{
                    if(isFinishing()||isDestroyed())return;
                    new FontDialog.Builder(this).setTitle(fullBackupMode?"Полная резервная копия":"Компактная резервная копия")
                        .setMessage(explanation).setNegativeButton("Назад",(d,w)->handler.post(this::backupPicker))
                        .setPositiveButton("Продолжить",(d,w)->askBackupPassword()).show();
                });
            }).setNegativeButton("Отмена",null).show();
    }
    private EditText privatePasswordField(String hint){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSaveEnabled(false);field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setHint(hint);return field;
    }
    private static char[] takePassword(EditText field){
        char[] value=new char[field.length()];field.getText().getChars(0,field.length(),value,0);
        field.getText().clear();return value;
    }
    private void showPasswordDialog(AlertDialog.Builder builder,EditText field){
        AlertDialog dialog=builder.create();
        dialog.setOnDismissListener(d->field.getText().clear());
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private void clearBackupPassword(){
        if(backupPassword!=null)java.util.Arrays.fill(backupPassword,(char)0);
        backupPassword=null;
    }
    private void askBackupPassword(){
        LinearLayout layout=column();layout.setPadding(px(23),px(5),px(23),px(3));
        EditText pass=privatePasswordField("Пароль, не меньше 8 символов");layout.addView(pass,p(-1,53));
        TextView note=text("Без пароля создаётся обычный ZIP. Забытый пароль восстановить невозможно.",12,SUB);layout.addView(note);
        showPasswordDialog(new FontDialog.Builder(this).setTitle("Защитить копию паролем").setView(layout)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Без пароля",(d,w)->{clearBackupPassword();pickBackupFile();})
            .setPositiveButton("С паролем",(d,w)->{
                char[] value=takePassword(pass);
                if(value.length<8){java.util.Arrays.fill(value,(char)0);toast("Пароль должен содержать минимум 8 символов");return;}
                clearBackupPassword();backupPassword=value;pickBackupFile();
            }),pass);
    }
    private void pickBackupFile(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/octet-stream");i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_TITLE,"TelegramArchive-"+(fullBackupMode?"FULL":"COMPACT")+(backupPassword==null?".zip":".tgar"));
        try{startActivityForResult(i,41);}catch(RuntimeException error){clearBackupPassword();toast("Не удалось выбрать файл копии");}
    }
    private void dryRunPicker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i,44);
    }
    private void dryRunSelected(Uri uri){
        try{
            if(SecureArchive.isEncrypted(this,uri)){
                EditText input=privatePasswordField("Пароль резервной копии");
                showPasswordDialog(new FontDialog.Builder(this).setTitle("Пробное восстановление").setView(input)
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Проверить",(d,w)->runDryRestore(uri,takePassword(input))),input);
            }else runDryRestore(uri,null);
        }catch(Exception e){status.setText("Ошибка чтения: "+e.getMessage());}
    }
    private void runDryRestore(Uri uri,char[] pass){
        if(importing||restoringBackup){if(pass!=null)java.util.Arrays.fill(pass,(char)0);return;}
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
                EditText entry=privatePasswordField("Пароль для проверки архива");
                showPasswordDialog(new FontDialog.Builder(this).setTitle("Проверка защищённой копии")
                    .setView(entry).setNegativeButton("Отмена",null)
                    .setPositiveButton("Проверить",(d,w)->runBackupVerify(uri,takePassword(entry))),entry);
            }else runBackupVerify(uri,null);
        }catch(Exception problem){status.setText("Невозможно открыть копию: "+problem.getMessage());}
    }
    private void runBackupVerify(Uri uri,char[] password){
        if(importing||restoringBackup){if(password!=null)java.util.Arrays.fill(password,(char)0);return;}restoringBackup=true;
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
            if(!SecureArchive.isEncrypted(this,uri)){clearBackupPassword();backupAction(uri,true);return;}
            EditText entry=privatePasswordField("Пароль резервной копии");
            showPasswordDialog(new FontDialog.Builder(this).setTitle("Зашифрованная копия")
                .setMessage("Введите пароль, чтобы проверить и восстановить данные")
                .setView(entry).setNegativeButton("Отмена",null)
                .setPositiveButton("Продолжить",(d,w)->{
                    clearBackupPassword();backupPassword=takePassword(entry);
                    backupAction(uri,true);
                }),entry);
        }catch(Exception error){status.setText("Не удалось прочитать архив: "+error.getMessage());}
    }
    private void backupAction(Uri uri,boolean restore){
        if(restoringBackup||importing){
            clearBackupPassword();
            return;
        }
        final char[] password=backupPassword;
        final boolean exportFull=fullBackupMode;
        final String exportName=selectedBackupName;
        backupPassword=null;restoringBackup=true;
        status.setText(restore?"Проверяю архив перед восстановлением…":"Создаю и проверяю резервную копию…");
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
                    BackupVerifier.Result checked=BackupExportGate.saveAndVerify(
                        this,prefs,uri,exportFull,password,
                        msg->runOnUiThread(()->status.setText(msg)));
                    runOnUiThread(()->{
                        restoringBackup=false;
                        status.setText("Копия «"+exportName+"» записана и прочитана обратно. Проверено ZIP-файлов: "+
                            checked.files+". Перед удалением оригиналов проверь восстановление.");
                        toast("Резервная копия проверена");
                    });
                }
            }catch(Exception error){
                runOnUiThread(()->{
                    restoringBackup=false;
                    status.setText(restore?"Ошибка восстановления: "+error.getMessage():
                        "Экспорт не подтверждён: "+error.getMessage()+
                        ". Выбранный файл может быть неполным. Не удаляй предыдущую резервную копию.");
                });
            }finally{if(password!=null)java.util.Arrays.fill(password,(char)0);}
        });
    }
    private final class VerticalTimeline extends View {
       private final Paint brush=new Paint(3);private int position,count;
       VerticalTimeline(){super(MainActivity.this);}
       void setPosition(int p,int c){position=p;count=c;invalidate();}
       @Override protected void onDraw(Canvas c){
         super.onDraw(c);float x=getWidth()-px(5),top=px(13),bottom=getHeight()-px(13);
         brush.setColor(themeIndex==2?0x554b7897:0x88536f88);
         brush.setStrokeWidth(px(2));c.drawLine(x,top,x,bottom,brush);
         float ratio=count<=1?0:(float)position/(count-1);
         brush.setColor(ACCENT);c.drawCircle(x,top+(bottom-top)*ratio,px(3),brush);
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
       autoFloating=false;
       autoFloatSourceHolder=null;autoFloatSourcePoster=null;autoFloatSourceOverlay=null;
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
        round.setOnClickListener(v->startRoundPlayback(a,round,preview,play));
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
        startRoundPlayback(a,round,preview,play,resume);
    }

    @Override protected void onPause(){
        handler.removeCallbacks(deferredReadingSave);
        handler.removeCallbacks(deferredFloatingCheck);
        saveReadingProgress();
        // Releasing a codec is insufficient: the floating FrameLayout would remain grey.
        closeFloatingRound();
        stopPlayback();
        super.onPause();
    }
    @Override protected void onDestroy(){
        batchCancelled.set(true);
        clearBackupPassword();
        if(archivePasswordDialog!=null){archivePasswordDialog.dismiss();archivePasswordDialog=null;}
        if(pendingCapsulePassword!=null){java.util.Arrays.fill(pendingCapsulePassword,(char)0);pendingCapsulePassword=null;}
        if(scheduledPrefetch!=null)handler.removeCallbacks(scheduledPrefetch);
        handler.removeCallbacks(deferredReadingSave);
        handler.removeCallbacks(deferredFloatingCheck);
        stopPlayback();final MessageDb closing=db;if(closing!=null)worker.execute(closing::close);worker.shutdown();mediaWorker.shutdown();super.onDestroy();}
}
