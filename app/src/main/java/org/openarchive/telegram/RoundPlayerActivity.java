package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.animation.ValueAnimator;
import android.view.animation.DecelerateInterpolator;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;
import java.util.Locale;

/** Single, touch-friendly offline round-video player. No duplicate system MediaController. */
public class RoundPlayerActivity extends Activity {
    private VideoView video;
    private LinearLayout topBar,bottomBar;
    private Button playPause;
    private TextView time;
    private SeekBar timeline;
    private MediaPlayer currentPlayer;
    private boolean prepared=false,scrubbing=false,foreground=false,playWhenReady=true,released=false;
    private Uri sourceUri;
    private final Runnable prepareTimeout=()->{if(!prepared&&!isFinishing()){video.stopPlayback();released=true;time.setText("Подготовка не завершена. Нажми ▶ для повтора.");}};
    private boolean controlsVisible=true;
    private float speed=1f;
    private int restoredPosition;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable update=new Runnable(){@Override public void run(){
        if(!isFinishing()&&video!=null&&prepared){
            try{
                int duration=Math.max(0,video.getDuration());
                int progress=Math.max(0,video.getCurrentPosition());
                if(!scrubbing){timeline.setMax(Math.max(1,duration));timeline.setProgress(Math.min(duration,progress));}
                time.setText(clock(progress)+" / "+clock(duration));
                playPause.setText(video.isPlaying()?"Ⅱ":"▶");
                playPause.setContentDescription(video.isPlaying()?"Пауза":"Воспроизвести");
            }catch(Exception ignored){}
        }
        if(!isFinishing())ui.postDelayed(this,350);
    }};
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable glass(int radius){
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xf121394d,0xf20c1727});
        bg.setCornerRadius(dp(radius));bg.setStroke(dp(1),0x447fb3cd);return bg;
    }
    private Button button(String title,int width){
        Button b=new Button(this);b.setText(title);b.setTextSize(17);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3c7ad2ff),glass(14),null));
        b.setElevation(0);b.setStateListAnimator(null);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(width),dp(46)));
        return b;
    }
    private static String clock(int ms){int v=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%02d:%02d",v/60,v%60);}
    private int motionDuration(){return ValueAnimator.areAnimatorsEnabled()?170:0;}
    private void toggleControls(){
        controlsVisible=!controlsVisible;
        animateControl(topBar,controlsVisible,-dp(8));
        animateControl(bottomBar,controlsVisible,dp(8));
    }
    private void animateControl(View view,boolean shown,int offset){
        view.animate().cancel();
        int duration=motionDuration();
        if(duration==0){view.setVisibility(shown?View.VISIBLE:View.GONE);view.setAlpha(1f);view.setTranslationY(0);return;}
        if(shown){view.setVisibility(View.VISIBLE);view.setAlpha(0f);view.setTranslationY(offset);
            view.animate().alpha(1f).translationY(0).setDuration(duration)
                .setInterpolator(new DecelerateInterpolator()).start();
        }else{
            view.animate().alpha(0f).translationY(offset).setDuration(duration)
                .withEndAction(()->{if(!controlsVisible){view.setVisibility(View.GONE);view.setAlpha(1f);view.setTranslationY(0);}}).start();
        }
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);FontStyler.bind(this);
        if(state!=null){restoredPosition=state.getInt("round_playback_ms",0);playWhenReady=state.getBoolean("round_playing",false);speed=state.getFloat("round_speed",1f);}
        getWindow().setStatusBarColor(0xff0b1522);getWindow().setNavigationBarColor(0xff0b1522);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff080e19);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int safeTop=0,safeBottom=0;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.statusBars()|
                    android.view.WindowInsets.Type.navigationBars()|android.view.WindowInsets.Type.displayCutout());
                safeTop=safe.top;safeBottom=safe.bottom;
            }else{safeTop=insets.getSystemWindowInsetTop();safeBottom=insets.getSystemWindowInsetBottom();}
            if(topBar!=null&&bottomBar!=null){
                FrameLayout.LayoutParams topParams=(FrameLayout.LayoutParams)topBar.getLayoutParams();
                topParams.topMargin=safeTop+dp(8);topBar.setLayoutParams(topParams);
                FrameLayout.LayoutParams bottomParams=(FrameLayout.LayoutParams)bottomBar.getLayoutParams();
                bottomParams.bottomMargin=safeBottom+dp(10);bottomBar.setLayoutParams(bottomParams);
            }
            return insets;
        });
        video=new VideoView(this);
        root.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));

        topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);topBar.setPadding(dp(12),dp(8),dp(12),dp(8));
        topBar.setBackground(glass(20));
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,dp(64),Gravity.TOP);
        top.setMargins(dp(12),dp(12),dp(12),0);root.addView(topBar,top);
        Button back=button("‹",48);back.setContentDescription("Вернуться назад");topBar.addView(back);
        back.setOnClickListener(v->finish());
        TextView title=new TextView(this);title.setText("Видеосообщение");title.setTextColor(Color.WHITE);title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(14),0,0,0);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium",0));
        topBar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Button speedButton=button("1×",54);speedButton.setContentDescription("Скорость видео");topBar.addView(speedButton);
        speedButton.setOnClickListener(v->chooseSpeed(speedButton));
        Button pip=button("⊞",48);pip.setContentDescription("Плавающий экран Picture-in-Picture");topBar.addView(pip);
        pip.setOnClickListener(v->enterPip());

        bottomBar=new LinearLayout(this);bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setPadding(dp(10),dp(7),dp(10),dp(10));bottomBar.setBackground(glass(18));
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,dp(116),Gravity.BOTTOM);
        bottom.setMargins(dp(12),0,dp(12),dp(16));root.addView(bottomBar,bottom);
        timeline=new SeekBar(this);timeline.setContentDescription("Перемотка видео");bottomBar.addView(timeline,new LinearLayout.LayoutParams(-1,dp(40)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onStartTrackingTouch(SeekBar b){scrubbing=true;}
            @Override public void onStopTrackingTouch(SeekBar b){if(prepared)video.seekTo(b.getProgress());scrubbing=false;}
            @Override public void onProgressChanged(SeekBar b,int p,boolean byUser){if(byUser)time.setText(clock(p)+" / "+clock(video.getDuration()));}
        });
        LinearLayout bottomControls=new LinearLayout(this);bottomControls.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        bottomBar.addView(bottomControls,new LinearLayout.LayoutParams(-1,dp(51)));
        Button minus=button("−10",64);minus.setContentDescription("На 10 секунд назад");bottomControls.addView(minus);
        minus.setOnClickListener(v->skip(-10000));
        playPause=button("▶",66);playPause.setContentDescription("Пауза или воспроизведение");bottomControls.addView(playPause);
        playPause.setOnClickListener(v->{
            if(!prepared){if(released){playWhenReady=true;loadVideo();}return;}
            if(video.isPlaying()){video.pause();playWhenReady=false;}else{video.start();playWhenReady=true;}
            playPause.setText(video.isPlaying()?"Ⅱ":"▶");
            playPause.setContentDescription(video.isPlaying()?"Пауза":"Воспроизвести");
        });
        Button plus=button("+10",64);plus.setContentDescription("На 10 секунд вперёд");bottomControls.addView(plus);
        plus.setOnClickListener(v->skip(10000));
        time=new TextView(this);time.setText("00:00 / 00:00");time.setTextColor(0xffbed6e4);
        time.setTextSize(12);time.setGravity(Gravity.CENTER);
        bottomControls.addView(time,new LinearLayout.LayoutParams(0,dp(44),1));
        video.setOnClickListener(v->toggleControls());
        sourceUri=getIntent().getData();if(sourceUri==null){finish();return;}
        video.setOnPreparedListener(mp->{
            ui.removeCallbacks(prepareTimeout);
            prepared=true;currentPlayer=mp;mp.setLooping(true);
            if(restoredPosition>0)video.seekTo(restoredPosition);
            if(foreground&&playWhenReady){video.start();applySpeed();}else video.pause();
            ui.removeCallbacks(update);if(foreground)ui.post(update);
        });
        video.setOnErrorListener((mp,what,extra)->{
            prepared=false;currentPlayer=null;ui.removeCallbacks(update);ui.removeCallbacks(prepareTimeout);video.stopPlayback();released=true;
            time.setText("Ошибка видео. Нажми ▶ для повтора. Оригинал сохранён.");return true;
        });
        loadVideo();
        root.requestApplyInsets();
    }
    private void loadVideo(){
        prepared=false;released=false;currentPlayer=null;time.setText("Подготовка видео…");
        ui.removeCallbacks(prepareTimeout);video.setVideoURI(sourceUri);ui.postDelayed(prepareTimeout,15000);
    }
    private void chooseSpeed(Button control){
        final float[] values={0.25f,0.5f,0.75f,1f,1.25f,1.5f,2f};
        String[] choices={"0.25×","0.5×","0.75×","1×","1.25×","1.5×","2×"};
        new FontDialog.Builder(this).setTitle("Скорость воспроизведения")
            .setItems(choices,(d,pos)->{speed=values[pos];if(applySpeed())control.setText(choices[pos]);
                else {speed=1f;control.setText("1×");android.widget.Toast.makeText(this,"Этот кодек не поддерживает скорость",android.widget.Toast.LENGTH_SHORT).show();}}).show();
    }
    private boolean applySpeed(){
        if(currentPlayer==null||Build.VERSION.SDK_INT<23)return false;
        try{boolean playing=video.isPlaying();android.media.PlaybackParams params=currentPlayer.getPlaybackParams();params.setSpeed(speed);currentPlayer.setPlaybackParams(params);if(!playing)video.pause();return true;}
        catch(Exception ignored){return false;}
    }
    private void skip(int ms){if(!prepared)return;
        video.seekTo(Math.max(0,Math.min(Math.max(0,video.getDuration()-300),video.getCurrentPosition()+ms)));
    }
    private void enterPip(){
        if(!prepared||Build.VERSION.SDK_INT<26)return;
        try{enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(1,1)).build());}
        catch(Exception ignored){}
    }
    // PiP must be explicit: pressing Home should not surprise the user with a
    // floating window or trigger device-specific Picture-in-Picture races.
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();}
    @Override public void onPictureInPictureModeChanged(boolean mode,Configuration config){
        super.onPictureInPictureModeChanged(mode,config);
        topBar.animate().cancel();bottomBar.animate().cancel();
        topBar.setVisibility(mode?View.GONE:View.VISIBLE);bottomBar.setVisibility(mode?View.GONE:View.VISIBLE);
        controlsVisible=!mode;topBar.setAlpha(1f);bottomBar.setAlpha(1f);
        topBar.setTranslationY(0);bottomBar.setTranslationY(0);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(video!=null&&prepared)state.putInt("round_playback_ms",Math.max(0,video.getCurrentPosition()));
        state.putBoolean("round_playing",prepared&&video.isPlaying());state.putFloat("round_speed",speed);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume(){super.onResume();foreground=true;if(released)loadVideo();if(prepared){ui.removeCallbacks(update);ui.post(update);}}
    @Override protected void onPause(){
        foreground=false;
        if(!isInPictureInPictureMode()){
            playWhenReady=false;
            if(prepared)try{restoredPosition=video.getCurrentPosition();video.pause();}catch(Exception ignored){}
        }
        super.onPause();
    }
    @Override protected void onStop(){
        ui.removeCallbacks(update);ui.removeCallbacks(prepareTimeout);
        if(video!=null){if(prepared)try{restoredPosition=video.getCurrentPosition();}catch(Exception ignored){}video.stopPlayback();}
        prepared=false;currentPlayer=null;released=true;super.onStop();
    }
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(video!=null)video.stopPlayback();super.onDestroy();}
}
