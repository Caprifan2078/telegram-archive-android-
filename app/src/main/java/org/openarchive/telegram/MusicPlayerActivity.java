package org.openarchive.telegram;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline, foreground-only music player. References original files without copying or altering them. */
public final class MusicPlayerActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private static final int BG=0xff081321, TEXT=0xfff5f8ff, MUTED=0xffb7ccdf, ACCENT=0xff77d5ff;
    private static final float[] SPEEDS={0.25f,0.5f,0.75f,1f,1.25f,1.5f,2f};
    private MusicQueue queue;
    private int position=-1,serial=0;
    private float speed=1f;
    private boolean prepared=false,scrubbing=false,foreground=false;
    private MediaPlayer player;
    private DocumentFolder source;
    private String chatKey;
    private TextView title,artist,positionText,clock,speedText,notice;
    private ImageView art;
    private Button toggle,previous,next;
    private SeekBar seek;
    private AudioManager audio;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChange=change->{
        if(change<0){
            ui.post(()->{if(player!=null&&prepared)try{player.pause();refreshPlayback();}catch(Exception ignored){}});
        }
    };
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!isFinishing()&&player!=null&&prepared){
            try{
                int duration=Math.max(0,player.getDuration());
                int progress=Math.max(0,player.getCurrentPosition());
                if(!scrubbing){seek.setMax(Math.max(1,duration));seek.setProgress(Math.min(duration,progress));}
                clock.setText(time(progress)+" / "+time(duration));
                refreshPlayback();
            }catch(Exception ignored){}
        }
        if(!isFinishing())ui.postDelayed(this,400);
    }};
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private static String time(int ms){int s=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private GradientDrawable gradient(int top,int bottom,int radius){
        GradientDrawable result=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});
        result.setCornerRadius(dp(radius));result.setStroke(dp(1),0x4469a9c5);return result;
    }
    private Button control(String text,int width){
        Button b=new Button(this);b.setText(text);b.setTextColor(TEXT);b.setTextSize(18);b.setAllCaps(false);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(6),0,dp(6),0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4496dffc),gradient(0xff233e59,0xff14283f,17),null));
        b.setStateListAnimator(null);b.setElevation(0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(width),dp(51)));return b;
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER);
        return t;
    }
    private void feedback(String message){notice.setText(message);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);FontStyler.bind(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        chatKey=getIntent().getStringExtra("chat_key");
        String tree=getIntent().getStringExtra("tree");
        final long attachmentId=getIntent().getLongExtra("attachment_id",-1);
        if(!MusicQueue.validChatKey(chatKey)||attachmentId<=0||tree==null){
            Toast.makeText(this,"Неверные данные музыкального плеера",Toast.LENGTH_LONG).show();finish();return;
        }
        try{
            if(tree.startsWith("local:")){
                File directory=new File(tree.substring(6)).getCanonicalFile();
                File allowed=new File(getFilesDir(),"telegram_imports").getCanonicalFile();
                if(!directory.getPath().startsWith(allowed.getPath()+File.separator))throw new SecurityException("Недопустимая папка");
                source=new DocumentFolder(getContentResolver(),directory);
            }else{
                Uri uri=Uri.parse(tree);
                if(!"content".equals(uri.getScheme()))throw new SecurityException("Недопустимый адрес");
                source=new DocumentFolder(getContentResolver(),uri);
            }
        }catch(Exception e){Toast.makeText(this,"Нет доступа к папке с музыкой",Toast.LENGTH_LONG).show();finish();return;}
        audio=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
        buildUi();
        if(state!=null)speed=state.getFloat("speed",1f);
        speedText.setText("Скорость · "+String.format(Locale.ROOT,"%s×",speed));
        io.execute(()->loadQueue(attachmentId));
        ui.post(ticker);
    }
    private void buildUi(){
        android.widget.ScrollView screen=new android.widget.ScrollView(this);screen.setFillViewport(true);
        screen.setBackgroundColor(BG);setContentView(screen);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(18),dp(18),dp(18),dp(24));screen.addView(panel,new android.widget.ScrollView.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(header,new LinearLayout.LayoutParams(-1,dp(55)));
        Button back=control("‹",55);back.setContentDescription("Назад в медиатеку");header.addView(back);
        back.setOnClickListener(v->finish());
        TextView heading=text("МУЗЫКА · ОФЛАЙН",15,ACCENT);heading.setTypeface(null,Typeface.BOLD);
        header.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
        Button settings=control("⚙",55);settings.setContentDescription("Настройки скорости музыки");header.addView(settings);
        settings.setOnClickListener(v->chooseSpeed());
        FrameLayout artwork=new FrameLayout(this);artwork.setBackground(gradient(0xff2f5f79,0xff172e48,26));
        artwork.setClipToOutline(true);int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int artworkHeight=Math.min(screenWidth-dp(36),dp(340));
        LinearLayout.LayoutParams artworkBox=new LinearLayout.LayoutParams(-1,artworkHeight);
        artworkBox.topMargin=dp(21);artworkBox.bottomMargin=dp(23);
        panel.addView(artwork,artworkBox);
        // On compact screens the cover remains responsive, while the controls stay readable.
        art=new ImageView(this);art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artwork.addView(art,new FrameLayout.LayoutParams(-1,-1));
        TextView placeholder=text("♫",110,0xffa8e3ff);placeholder.setGravity(Gravity.CENTER);
        artwork.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        art.setTag(placeholder);
        title=text("Загружаю треки…",21,TEXT);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        title.setMaxLines(2);panel.addView(title,new LinearLayout.LayoutParams(-1,dp(61)));
        artist=text("Музыка из выбранной переписки",13,MUTED);panel.addView(artist,new LinearLayout.LayoutParams(-1,dp(30)));
        positionText=text("",12,ACCENT);panel.addView(positionText,new LinearLayout.LayoutParams(-1,dp(28)));
        seek=new SeekBar(this);seek.setEnabled(false);seek.setContentDescription("Перемотка трека");
        panel.addView(seek,new LinearLayout.LayoutParams(-1,dp(54)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar b){scrubbing=true;}
            @Override public void onProgressChanged(SeekBar b,int p,boolean byUser){
                if(byUser&&player!=null&&prepared){try{clock.setText(time(p)+" / "+time(player.getDuration()));}catch(Exception ignored){}}
            }
            @Override public void onStopTrackingTouch(SeekBar b){
                if(player!=null&&prepared){try{player.seekTo(b.getProgress());}catch(Exception e){feedback("Перемотка недоступна");}}
                scrubbing=false;
            }
        });
        clock=text("0:00 / 0:00",13,MUTED);panel.addView(clock,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(11),0,dp(10));
        panel.addView(controls,new LinearLayout.LayoutParams(-1,dp(91)));
        previous=control("⏮",77);previous.setContentDescription("Предыдущий трек");controls.addView(previous);
        previous.setOnClickListener(v->skip(false));
        toggle=control("▶",88);toggle.setContentDescription("Воспроизвести или поставить на паузу");
        LinearLayout.LayoutParams playParams=(LinearLayout.LayoutParams)toggle.getLayoutParams();playParams.leftMargin=dp(9);playParams.rightMargin=dp(9);
        controls.addView(toggle,playParams);toggle.setOnClickListener(v->pauseOrPlay());
        next=control("⏭",77);next.setContentDescription("Следующий трек");controls.addView(next);
        next.setOnClickListener(v->skip(true));
        speedText=text("Скорость · 1×",13,ACCENT);speedText.setPadding(0,dp(8),0,dp(8));
        panel.addView(speedText,new LinearLayout.LayoutParams(-1,dp(44)));speedText.setOnClickListener(v->chooseSpeed());
        notice=text("Оригиналы не копируются. Плеер работает только на устройстве.",12,MUTED);
        notice.setMaxLines(3);panel.addView(notice,new LinearLayout.LayoutParams(-1,dp(63)));
        updateButtons();
    }
    private void loadQueue(long anchor){
        ArrayList<MusicQueue.Track> before=new ArrayList<>(),after=new ArrayList<>();
        String databaseName="default".equals(chatKey)?"telegram_archive.db":"telegram_"+chatKey+".db";
        File file=getDatabasePath(databaseName);
        if(!file.isFile()){ui.post(()->feedback("База выбранного чата не найдена"));return;}
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY)){
            long messageId;
            try(Cursor c=db.rawQuery("SELECT message_id FROM attachments WHERE id=? AND kind='audio'",new String[]{String.valueOf(anchor)})){
                if(!c.moveToFirst()){ui.post(()->feedback("Трек не найден в архиве"));return;}
                messageId=c.getLong(0);
            }
            String columns="SELECT a.id,a.path,m.author,m.date_text FROM attachments a JOIN messages m ON m.id=a.message_id WHERE a.kind='audio' AND ";
            String[] args={String.valueOf(messageId),String.valueOf(messageId),String.valueOf(anchor)};
            try(Cursor c=db.rawQuery(columns+"(a.message_id < ? OR (a.message_id = ? AND a.id < ?)) ORDER BY a.message_id DESC,a.id DESC LIMIT "+MusicQueue.BEFORE,args)){
                while(c.moveToNext())before.add(new MusicQueue.Track(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
            }
            try(Cursor c=db.rawQuery(columns+"(a.message_id > ? OR (a.message_id = ? AND a.id >= ?)) ORDER BY a.message_id,a.id LIMIT "+MusicQueue.AFTER,args)){
                while(c.moveToNext())after.add(new MusicQueue.Track(c.getLong(0),c.getString(1),c.getString(2),c.getString(3)));
            }
            MusicQueue result=new MusicQueue(before,after,anchor);
            ui.post(()->{
                if(isFinishing())return;
                queue=result;
                if(queue.selected<0){feedback("Не удалось сформировать очередь музыки");return;}
                openTrack(queue.selected);
            });
        }catch(Exception e){ui.post(()->feedback("Ошибка чтения очереди музыки. Проверь целостность архива."));}
    }
    private void openTrack(int index){
        if(queue==null||index<0||index>=queue.tracks.size())return;
        releasePlayer();
        position=index;
        final int request=++serial;
        MusicQueue.Track song=queue.tracks.get(index);
        title.setText(MusicQueue.cleanTitle(song.path));artist.setText(song.author.isEmpty()?"Неизвестный исполнитель":song.author);
        positionText.setText("Трек "+(index+1)+" из "+queue.tracks.size()+" · окно архива");
        clock.setText("0:00 / 0:00");seek.setEnabled(false);seek.setProgress(0);
        art.setImageDrawable(null);setCoverVisible(false);
        feedback("Открываю оригинальный аудиофайл…");updateButtons();
        io.execute(()->{
            Uri uri=null;String name=null,artistName=null;Bitmap cover=null;
            try{
                uri=source.resolve(song.path);
                if(uri!=null){MediaMetadataRetriever reader=new MediaMetadataRetriever();
                    try{
                        reader.setDataSource(MusicPlayerActivity.this,uri);
                        name=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                        artistName=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                        byte[] artwork=reader.getEmbeddedPicture();
                        if(artwork!=null&&artwork.length>0&&artwork.length<=1_500_000){
                            BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
                            cover=BitmapFactory.decodeByteArray(artwork,0,artwork.length,opts);
                        }
                    }finally{reader.release();}
                }
            }catch(Exception ignored){}
            final Uri resolved=uri;final String metadataTitle=name,metadataArtist=artistName;final Bitmap bitmap=cover;
            ui.post(()->{
                if(isFinishing()||isDestroyed()||request!=serial)return;
                if(metadataTitle!=null&&!metadataTitle.trim().isEmpty())title.setText(metadataTitle.trim());
                if(metadataArtist!=null&&!metadataArtist.trim().isEmpty())artist.setText(metadataArtist.trim());
                if(bitmap!=null){art.setImageBitmap(bitmap);setCoverVisible(true);}
                if(resolved==null){feedback("Файл отсутствует в папке экспорта. Выбери соседний трек.");return;}
                startMedia(resolved,request);
            });
        });
    }
    private void setCoverVisible(boolean on){View placeholder=(View)art.getTag();if(placeholder!=null)placeholder.setVisibility(on?View.GONE:View.VISIBLE);}
    private void startMedia(Uri uri,int request){
        try{
            MediaPlayer mp=new MediaPlayer();player=mp;
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(this,uri);
            mp.setOnPreparedListener(p->{
                if(p!=player||request!=serial)return;
                prepared=true;seek.setEnabled(true);
                seek.setMax(Math.max(1,p.getDuration()));
                clock.setText("0:00 / "+time(p.getDuration()));
                try{
                    if(foreground&&(audio==null||audio.requestAudioFocus(audioFocusChange,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)){
                        p.start();applySpeed();feedback("Воспроизведение · без интернета");
                    }else feedback("Другое приложение использует звук. Нажми ▶ позже.");
                }catch(Exception e){feedback("Ошибка запуска аудио. Проверь формат файла.");}
                refreshPlayback();
            });
            mp.setOnCompletionListener(p->{if(p!=player||request!=serial)return;
                if(queue!=null&&queue.adjacent(position,true)>=0)openTrack(queue.adjacent(position,true));
                else {refreshPlayback();feedback("Конец очереди");}
            });
            mp.setOnErrorListener((p,what,extra)->{
                if(p==player&&request==serial){feedback("Не удалось декодировать этот трек. Оригинал сохранён.");releasePlayer();updateButtons();}
                return true;
            });
            mp.prepareAsync();
        }catch(Exception e){releasePlayer();feedback("Файл не поддерживается или недоступен. Оригинал сохранён.");}
    }
    private void releasePlayer(){
        prepared=false;
        if(player!=null){MediaPlayer old=player;player=null;try{old.reset();}catch(Exception ignored){}
            try{old.release();}catch(Exception ignored){}}
        if(audio!=null)audio.abandonAudioFocus(audioFocusChange);
        if(toggle!=null)toggle.setText("▶");
    }
    private void skip(boolean forward){
        if(queue==null)return;
        int p=queue.adjacent(position,forward);
        if(p<0){feedback(forward?"Это последний трек очереди":"Это первый трек очереди");return;}
        openTrack(p);
    }
    private void pauseOrPlay(){
        if(player==null||!prepared)return;
        try{
            if(player.isPlaying()){player.pause();feedback("Пауза");}
            else if(foreground&&(audio==null||audio.requestAudioFocus(audioFocusChange,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)){
                player.start();applySpeed();feedback("Воспроизведение · без интернета");
            }else feedback("Звук сейчас занят другим приложением");
            refreshPlayback();
        }catch(Exception e){feedback("Невозможно возобновить воспроизведение");}
    }
    private void chooseSpeed(){
        String[] labels={"0,25×","0,5×","0,75×","1×","1,25×","1,5×","2×"};
        new FontDialog.Builder(this).setTitle("Скорость музыки")
            .setItems(labels,(dialog,which)->{
                float old=speed;speed=SPEEDS[which];
                if(prepared&&player!=null){
                    boolean playing=false;try{playing=player.isPlaying();}catch(Exception ignored){}
                    if(playing&&!applySpeed()){
                        speed=old;feedback("Этот файл или кодек не поддерживает выбранную скорость");return;
                    }
                }
                speedText.setText("Скорость · "+labels[which]);
            }).setNegativeButton("Отмена",null).show();
    }
    private boolean applySpeed(){
        if(player==null||!prepared)return false;
        try{
            android.media.PlaybackParams params=player.getPlaybackParams();
            params.setSpeed(speed);player.setPlaybackParams(params);return true;
        }catch(Exception e){return false;}
    }
    private void updateButtons(){
        if(previous!=null)previous.setEnabled(queue!=null&&position>0);
        if(next!=null)next.setEnabled(queue!=null&&position<queue.tracks.size()-1);
    }
    private void refreshPlayback(){if(toggle!=null&&player!=null&&prepared){
        try{toggle.setText(player.isPlaying()?"Ⅱ":"▶");}catch(Exception ignored){toggle.setText("▶");}
    }}
    @Override protected void onSaveInstanceState(Bundle b){b.putFloat("speed",speed);super.onSaveInstanceState(b);}
    @Override protected void onResume(){super.onResume();foreground=true;ui.removeCallbacks(ticker);ui.post(ticker);}
    @Override protected void onPause(){
        foreground=false;ui.removeCallbacks(ticker);
        if(player!=null&&prepared)try{player.pause();refreshPlayback();}catch(Exception ignored){}
        if(audio!=null)audio.abandonAudioFocus(audioFocusChange);
        super.onPause();
    }
    @Override protected void onDestroy(){serial++;ui.removeCallbacks(ticker);releasePlayer();io.shutdownNow();super.onDestroy();}
}
