package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.widget.SeekBar;
import java.util.Locale;
import java.io.ByteArrayOutputStream;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Completely isolated interactive tutorial: never touches user's chat database. */
public final class DemoChatActivity extends Activity {
    private static final int BG=0xff0b1424, PANEL=0xff1a2b42, AQUA=0xff89d9f5;
    private static final int IN=0xff283c56, OUT=0xff21564f, FG=0xfff1f7ff;
    private final DemoConversation[] scenarios={new DemoConversation(),new DemoConversation(1)};
    private int scenario=0;
    private DemoConversation model=scenarios[0];
    private LinearLayout feed, pinned, footer;
    private MediaPlayer player;
    private final Handler audioUi=new Handler(Looper.getMainLooper());
    private Runnable audioTicker;
    private int audioSerial;
    private long activeAudioId=-1;
    private boolean audioPrepared;
    private Dialog videoDialog;
    private android.media.AudioManager audio;
    private final android.media.AudioManager.OnAudioFocusChangeListener focus=change->{if(change<0)audioUi.post(()->{if(player!=null&&audioPrepared)try{player.pause();}catch(Exception ignored){}});};
    private boolean requestSound(){return audio==null||audio.requestAudioFocus(focus,android.media.AudioManager.STREAM_MUSIC,android.media.AudioManager.AUDIOFOCUS_GAIN)==android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;}

    private int px(float dp){return (int)(getResources().getDisplayMetrics().density*dp+0.5f);}
    private GradientDrawable bg(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(px(radius));return d;
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private Button button(String s){
        Button b=new Button(this);b.setText(s);b.setTextColor(FG);b.setAllCaps(false);
        b.setBackground(bg(0xff255978,16));return b;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);FontStyler.bind(this);audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);draw();}
    private void draw(){
        stopAudio();
        LinearLayout root=column();root.setBackgroundColor(BG);root.setFitsSystemWindows(true);setContentView(root);
        LinearLayout bar=new LinearLayout(this);bar.setPadding(px(12),px(16),px(12),px(12));
        bar.setGravity(Gravity.CENTER_VERTICAL);bar.setBackground(bg(PANEL,0));root.addView(bar);
        Button back=button("‹");bar.addView(back,new LinearLayout.LayoutParams(px(48),px(50)));
        back.setOnClickListener(v->finish());
        LinearLayout headline=column();headline.setPadding(px(12),0,0,0);
        bar.addView(headline,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text("Учебная переписка",21,FG);title.setTypeface(null,Typeface.BOLD);headline.addView(title);
        headline.addView(text("Сценарий "+(scenario+1)+" из 2 · 9 типов медиа · офлайн",12,AQUA));
        Button reset=button("Сценарии");bar.addView(reset,new LinearLayout.LayoutParams(px(108),px(50)));
        reset.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Учебные диалоги")
            .setItems(new String[]{"1. Знакомство с медиа","2. История путешественника","Сбросить текущий сценарий"},
                (d,index)->{
                    if(index<2){scenario=index;model=scenarios[index];draw();}
                    else new FontDialog.Builder(this).setTitle("Сбросить текущую тренировку?")
                        .setMessage("Удалятся только учебные изменения в этом сценарии. Настоящие архивы не затрагиваются.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(ok,which)->{model.reset();draw();}).show();
                }).show());
        TextView notice=text("ПЕСОЧНИЦА · демонстрационные данные, не настоящая переписка",12,AQUA);
        notice.setPadding(px(17),px(11),px(12),px(11));root.addView(notice);
        pinned=column();root.addView(pinned);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        feed=column();feed.setPadding(px(10),px(14),px(10),px(16));scroll.addView(feed);
        footer=column();footer.setPadding(px(12),px(8),px(12),px(9));footer.setBackground(bg(PANEL,0));root.addView(footer);
        refresh();
    }
    private String kindName(DemoConversation.Kind k){
        switch(k){case PHOTO:return "ФОТО";case VIDEO:return "ВИДЕО";
        case ROUND:return "КРУЖОК";case VOICE:return "ГОЛОСОВОЕ";
        case MUSIC:return "МУЗЫКА";case GIF:return "GIF";
        case STICKER:return "СТИКЕР";case FILE:return "ФАЙЛ";default:return "СООБЩЕНИЕ";}
    }
    private void refresh(){
        // Refreshing the exercise replaces controls: never leave an orphaned audio ticker.
        stopAudio();
        pinned.removeAllViews();feed.removeAllViews();footer.removeAllViews();
        TextView p=text("Закреплено: "+model.pinnedCount()+"  ·  Избранное: "+model.savedCount(),12,AQUA);
        p.setPadding(px(17),px(8),px(13),px(8));pinned.addView(p);
        TextView intro=text("Нажми на медиа для просмотра. Удерживай сообщение для действий. Все изменения останутся только в этой учебной переписке.",13,0xffb9cce1);
        intro.setPadding(px(14),px(12),px(12),px(17));feed.addView(intro);
        for(DemoConversation.Item e:model.items())feed.addView(renderItem(e));
        TextView progress=text("Практика: закрепить / открепить · ответить · цитировать · избранное",13,FG);
        progress.setPadding(px(6),px(4),px(6),px(6));footer.addView(progress);
        Button compose=button("✎ Написать учебное сообщение");footer.addView(compose);
        compose.setOnClickListener(v->{
            android.widget.EditText input=new android.widget.EditText(this);
            input.setHint("Напиши сообщение — оно останется только в песочнице");
            input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
            new FontDialog.Builder(this).setTitle("Учебное сообщение").setView(input)
                .setNegativeButton("Отмена",null).setPositiveButton("Добавить",(dialog,which)->{
                    String body=input.getText().toString().trim();
                    if(!model.appendText(body)){toast("Введи сообщение до 2000 символов");return;}
                    refresh();toast("Учебное сообщение добавлено");
                }).show();
        });
        Button guide=button("Как пользоваться · подсказки");footer.addView(guide);
        guide.setOnClickListener(v->new FontDialog.Builder(this).setTitle("Попробуй 5 действий")
            .setMessage("1. Удерживай текстовое сообщение.\n2. Нажми «Закрепить»; повтори для открепления.\n3. Выбери «Ответить», чтобы увидеть поле ответа.\n4. Нажми «Цитировать» для точной учебной цитаты.\n5. Добавь запись в избранное и снова нажми — уберётся.\n\nФото, видео, кружок, GIF, стикер, голосовое, музыка и файл открываются по обычному нажатию.\n\nЭто только тренировка. Настоящие переписки, файлы и настройки не изменяются.")
            .setPositiveButton("Понятно",null).show());
    }
    private View renderItem(DemoConversation.Item e){
        LinearLayout line=column();line.setPadding(px(5),px(3),px(5),px(6));
        boolean own=e.author.equals("Ты");line.setGravity(own?Gravity.RIGHT:Gravity.LEFT);
        LinearLayout bubble=column();bubble.setPadding(px(15),px(12),px(15),px(12));
        bubble.setBackground(bg(own?OUT:IN,19));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,-2);
        bp.width=Math.min(px(325),getResources().getDisplayMetrics().widthPixels-px(51));
        line.addView(bubble,bp);
        TextView author=text(e.author,12,AQUA);author.setTypeface(null,Typeface.BOLD);bubble.addView(author);
        TextView message=text(e.text,15,FG);message.setPadding(0,px(6),0,px(5));bubble.addView(message);
        if(e.kind!=DemoConversation.Kind.TEXT){
            TextView tag=text("▸  "+kindName(e.kind)+" · открыть",12,AQUA);
            tag.setPadding(0,px(3),0,px(7));bubble.addView(tag);
            if(e.kind==DemoConversation.Kind.PHOTO||e.kind==DemoConversation.Kind.STICKER||e.kind==DemoConversation.Kind.GIF){
                if(e.kind==DemoConversation.Kind.GIF){
                    bubble.addView(gifView(e.asset),new LinearLayout.LayoutParams(-1,px(155)));
                }else{
                    ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);
                    preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    try(InputStream in=getAssets().open(e.asset)){
                        Bitmap b=BitmapFactory.decodeStream(in);if(b!=null)preview.setImageBitmap(b);
                    }catch(Exception ignored){}
                    bubble.addView(preview,new LinearLayout.LayoutParams(-1,px(e.kind==DemoConversation.Kind.STICKER?120:155)));
                }
            }
            if(e.kind==DemoConversation.Kind.VOICE||e.kind==DemoConversation.Kind.MUSIC){
                LinearLayout controls=new LinearLayout(this);
                controls.setGravity(Gravity.CENTER_VERTICAL);
                Button toggle=button("▶");toggle.setContentDescription("Воспроизвести или остановить учебное аудио");
                controls.addView(toggle,new LinearLayout.LayoutParams(px(54),px(47)));
                TextView timing=text("0:00 / 0:00",12,AQUA);
                timing.setPadding(px(8),0,0,0);
                controls.addView(timing,new LinearLayout.LayoutParams(0,-2,1));
                bubble.addView(controls,new LinearLayout.LayoutParams(-1,px(49)));
                SeekBar seek=new SeekBar(this);seek.setMax(1000);seek.setEnabled(false);
                seek.setContentDescription("Перемотать учебное аудио");
                bubble.addView(seek,new LinearLayout.LayoutParams(-1,px(35)));
                toggle.setOnClickListener(v->playDemoAudio(e,toggle,timing,seek));
                seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                    @Override public void onStartTrackingTouch(SeekBar bar){}
                    @Override public void onStopTrackingTouch(SeekBar bar){
                        if(player!=null&&audioPrepared&&activeAudioId==e.id)
                            try{player.seekTo((int)((long)bar.getProgress()*player.getDuration()/1000));}
                            catch(Exception ignored){}
                    }
                    @Override public void onProgressChanged(SeekBar bar,int value,boolean user){}
                });
            }
        }
        String flags=(e.pinned?"📌 закреплено  ":"")+(e.saved?"★ избранное  ":"");
        if(!flags.isEmpty()){TextView badges=text(flags,11,AQUA);badges.setPadding(0,px(7),0,0);bubble.addView(badges);}
        bubble.setContentDescription(kindName(e.kind)+" от "+e.author+". Удерживай для действий.");
        bubble.setOnLongClickListener(v->{actions(e);return true;});
        bubble.setOnClickListener(v->{if(e.asset==null)actions(e);else openAsset(e);});
        return line;
    }
    private void actions(DemoConversation.Item e){
        String[] a={e.pinned?"Открепить":"Закрепить",e.saved?"Убрать из избранного":"В избранное", "Ответить", "Цитировать", "Открыть медиа"};
        new FontDialog.Builder(this).setTitle(e.author+" · "+kindName(e.kind))
            .setItems(a,(dialog,index)->{
                if(index==0){model.togglePin(e.id);refresh();toast(e.pinned?"Закреплено в учебном чате":"Откреплено");}
                else if(index==1){model.toggleSave(e.id);refresh();}
                else if(index==2){model.reply(e.id);replyDialog(e,false);}
                else if(index==3){model.reply(e.id);replyDialog(e,true);}
                else if(e.asset!=null)openAsset(e);else toast("Это текстовое сообщение");
            }).setNegativeButton("Закрыть",null).show();
    }
    private void replyDialog(DemoConversation.Item e,boolean quote){
        android.widget.EditText input=new android.widget.EditText(this);
        input.setSingleLine(false);input.setMinLines(2);
        input.setText(quote?"«"+e.text+"»\n":"");
        input.setSelection(input.length());
        new FontDialog.Builder(this).setTitle(quote?"Учебная цитата":"Учебный ответ")
            .setMessage("Исходное сообщение №"+e.id+": "+e.text+"\n\nТекст останется только в тренировке.")
            .setView(input).setNegativeButton("Отмена",null)
            .setPositiveButton("Добавить в переписку",(d,w)->{
                if(model.appendText(input.getText().toString())){refresh();toast("Учебная запись добавлена");}
                else toast("Текст пустой или слишком длинный");
            }).show();
    }
    private File localFile(String asset)throws Exception{
        if(asset==null||!asset.matches("tutorial/[A-Za-z0-9_.-]{1,80}"))throw new IllegalArgumentException("Недопустимый учебный файл");
        File dir=new File(getCacheDir(),"tutorial");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("Не создан кэш");
        File dest=new File(dir,asset.substring(asset.lastIndexOf('/')+1));
        if(!dest.isFile()||dest.length()==0){
            File tmp=new File(dir,dest.getName()+".tmp");
            try(InputStream in=getAssets().open(asset);FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buf=new byte[8192];int n;long size=0;
                while((n=in.read(buf))!=-1){size+=n;if(size>800000)throw new java.io.IOException("Демонстрационный файл слишком большой");out.write(buf,0,n);}
                out.getFD().sync();
            }
            if(!tmp.renameTo(dest)){tmp.delete();throw new java.io.IOException("Не удалось создать файл");}
        }
        return dest;
    }
    private void openAsset(DemoConversation.Item e){
        try{
            if(e.kind==DemoConversation.Kind.VOICE||e.kind==DemoConversation.Kind.MUSIC){
                playDemoAudio(e,null,null,null);return;
            }
            if(e.kind==DemoConversation.Kind.VIDEO||e.kind==DemoConversation.Kind.ROUND){
                showVideo(localFile(e.asset),e.kind==DemoConversation.Kind.ROUND);return;
            }
            if(e.kind==DemoConversation.Kind.FILE){
                new FontDialog.Builder(this).setTitle("Пример документа")
                    .setMessage("Файл README.txt встроен в приложение. В настоящих чатах документы можно сохранить отдельно. Демо ничего не скачивает и не отправляет.")
                    .setPositiveButton("Понятно",null).show();return;
            }
            if(e.kind==DemoConversation.Kind.GIF){
                new FontDialog.Builder(this).setTitle("GIF · учебная анимация")
                    .setView(gifView(e.asset)).setPositiveButton("Закрыть",null).show();
                return;
            }
            ImageView image=new ImageView(this);image.setAdjustViewBounds(true);
            image.setBackgroundColor(0xff101f33);
            image.setPadding(px(8),px(8),px(8),px(8));
            try(InputStream in=getAssets().open(e.asset)){image.setImageBitmap(BitmapFactory.decodeStream(in));}
            new FontDialog.Builder(this).setTitle(kindName(e.kind)+" · учебное медиа")
                .setView(image).setPositiveButton("Закрыть",null).show();
        }catch(Exception err){toast("Не удалось открыть демо: "+err.getClass().getSimpleName());}
    }
    private View gifView(String asset){
        try(InputStream in=getAssets().open(asset);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;int total=0;
            while((n=in.read(buffer))!=-1){total+=n;if(total>200000)throw new java.io.IOException("GIF too large");out.write(buffer,0,n);}
            Movie gif=Movie.decodeByteArray(out.toByteArray(),0,total);
            if(gif!=null)return new GifCanvas(this,gif);
        }catch(Exception ignored){}
        return text("Анимация недоступна",14,AQUA);
    }
    private static final class GifCanvas extends View {
        private final Movie animation;private long started;
        GifCanvas(Context ctx,Movie gif){super(ctx);animation=gif;setBackgroundColor(0xff102137);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int target=(int)(getResources().getDisplayMetrics().density*210f);
            setMeasuredDimension(MeasureSpec.getSize(widthSpec),resolveSize(target,heightSpec));
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(started==0)started=SystemClock.uptimeMillis();
            int duration=Math.max(100,animation.duration());
            animation.setTime((int)((SystemClock.uptimeMillis()-started)%duration));
            int w=Math.max(1,animation.width()),h=Math.max(1,animation.height());
            float ratio=Math.min((float)getWidth()/w,(float)getHeight()/h);
            canvas.save();canvas.translate((getWidth()-w*ratio)/2f,(getHeight()-h*ratio)/2f);
            canvas.scale(ratio,ratio);animation.draw(canvas,0,0);canvas.restore();
            if(isAttachedToWindow())postInvalidateDelayed(40);
        }
    }
    private void showVideo(File file,boolean round){
        VideoView video=new VideoView(this);
        video.setVideoURI(Uri.fromFile(file));
        FrameLayout frame=new FrameLayout(this);frame.setPadding(px(8),px(8),px(8),px(8));
        frame.addView(video,new FrameLayout.LayoutParams(-1,px(round?240:230),Gravity.CENTER));
        if(round){video.setClipToOutline(true);video.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,Outline outline){
                int w=view.getWidth(),h=view.getHeight(),diameter=Math.min(w,h);
                outline.setOval((w-diameter)/2,(h-diameter)/2,(w+diameter)/2,(h+diameter)/2);
            }
        });}
        videoDialog=new FontDialog(this);videoDialog.setContentView(frame);
        videoDialog.setOnDismissListener(d->{video.stopPlayback();videoDialog=null;});
        video.setOnPreparedListener(mp->{mp.setLooping(true);video.start();});
        video.setOnClickListener(v->{if(video.isPlaying())video.pause();else video.start();});
        videoDialog.show();
        if(videoDialog.getWindow()!=null)videoDialog.getWindow().setLayout(-1,-2);
    }
    private static String audioTime(int ms){
        int sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d",sec/60,sec%60);
    }
    /** Asynchronous, bounded demo audio. No access to actual Telegram chat databases. */
    private void playDemoAudio(DemoConversation.Item item,Button toggle,TextView time,SeekBar seek){
        if(player!=null&&audioPrepared&&activeAudioId==item.id){
            try{
                if(player.isPlaying()){player.pause();if(toggle!=null)toggle.setText("▶");}
                else if(requestSound()){player.start();if(toggle!=null)toggle.setText("Ⅱ");}
            }catch(Exception e){stopAudio();toast("Ошибка учебного аудио");}
            return;
        }
        stopAudio();final int ticket=audioSerial;activeAudioId=item.id;
        if(toggle!=null)toggle.setText("…");
        try{
            MediaPlayer mp=new MediaPlayer();player=mp;
            mp.setDataSource(localFile(item.asset).getAbsolutePath());
            mp.setOnPreparedListener(p->{
                if(ticket!=audioSerial||p!=player)return;
                audioPrepared=true;
                if(seek!=null)seek.setEnabled(true);
                try{if(requestSound())p.start();else{stopAudio();toast("Звук занят другим приложением");return;}}catch(Exception ex){stopAudio();toast("Не запускается учебное аудио");return;}
                if(toggle!=null)toggle.setText("Ⅱ");
                audioTicker=new Runnable(){@Override public void run(){
                    if(ticket!=audioSerial||p!=player||!audioPrepared)return;
                    try{
                        int length=Math.max(1,p.getDuration()),pos=Math.max(0,p.getCurrentPosition());
                        if(toggle!=null){toggle.setText(p.isPlaying()?"Ⅱ":"▶");toggle.setContentDescription(p.isPlaying()?"Пауза":"Воспроизвести");}
                        if(time!=null)time.setText(audioTime(pos)+" / "+audioTime(length));
                        if(seek!=null&&!seek.isPressed())seek.setProgress((int)(1000L*pos/length));
                        audioUi.postDelayed(this,400);
                    }catch(Exception e){stopAudio();}
                }};
                audioUi.post(audioTicker);
            });
            mp.setOnCompletionListener(p->{
                if(p!=player)return;
                if(toggle!=null)toggle.setText("▶");
                if(seek!=null)seek.setProgress(0);
                if(time!=null)time.setText(audioTime(0)+" / "+audioTime(Math.max(0,p.getDuration())));
                stopAudio();
            });
            mp.setOnErrorListener((p,what,extra)->{
                if(p==player){stopAudio();if(toggle!=null)toggle.setText("▶");toast("Учебное аудио недоступно");}
                return true;
            });
            mp.prepareAsync();
        }catch(Exception e){stopAudio();if(toggle!=null)toggle.setText("▶");toast("Учебный файл не открылся");}
    }
    private void stopAudio(){
        audioSerial++;audioPrepared=false;activeAudioId=-1;
        if(audio!=null)audio.abandonAudioFocus(focus);
        if(audioTicker!=null){audioUi.removeCallbacks(audioTicker);audioTicker=null;}
        if(player!=null){
            MediaPlayer old=player;player=null;
            try{old.stop();}catch(Exception ignored){}
            try{old.release();}catch(Exception ignored){}
        }
    }
    @Override protected void onPause(){super.onPause();stopAudio();}
    @Override protected void onStop(){super.onStop();stopAudio();if(videoDialog!=null){videoDialog.dismiss();videoDialog=null;}}
}
