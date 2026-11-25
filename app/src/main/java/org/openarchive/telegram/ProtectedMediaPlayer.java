package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.*;

/** Internal player: no external intents, no cleartext cache, no persistent playback state. */
final class ProtectedMediaPlayer implements AutoCloseable {
    private final android.media.MediaDataSource source;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AlertDialog dialog;
    private boolean ready,closed,dragging,failed;
    private final TextView status;
    private final Button play;
    private final SeekBar seek;
    private final Runnable prepareTimeout=()->{
        if(!closed&&!ready)error("Подготовка медиа не завершилась. Закрой окно и попробуй снова.");
    };
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(closed||!ready||player==null)return;
            try{
                int position=Math.max(0,player.getCurrentPosition());
                if(!dragging){seek.setProgress(position);status.setText(clock(position)+" / "+clock(seek.getMax()));}
                play.setText(player.isPlaying()?"Пауза":"Воспроизвести");
                handler.postDelayed(this,250);
            }catch(IllegalStateException e){error();}
        }
    };
    ProtectedMediaPlayer(Activity context,android.media.MediaDataSource source,boolean video){
        this.source=source;
        LinearLayout box=new LinearLayout(context);box.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(12*context.getResources().getDisplayMetrics().density);
        box.setPadding(p,p,p,p);
        status=new TextView(context);status.setText("Подготовка защищённого медиа…");
        if(video){
            SurfaceView surface=new SurfaceView(context);surface.setSecure(true);
            box.addView(surface,new LinearLayout.LayoutParams(-1,p*22));
            surface.getHolder().addCallback(new SurfaceHolder.Callback(){
                @Override public void surfaceCreated(SurfaceHolder holder){start(holder);}
                @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
                @Override public void surfaceDestroyed(SurfaceHolder holder){close();}
            });
        }
        box.addView(status);
        seek=new SeekBar(context);seek.setContentDescription("Перемотка защищённого медиа");seek.setEnabled(false);box.addView(seek);
        play=new Button(context);play.setText("Воспроизвести");play.setEnabled(false);box.addView(play);
        play.setOnClickListener(v->{
            if(!ready||closed)return;
            try{if(player.isPlaying())player.pause();else player.start();}
            catch(IllegalStateException e){error();}
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user){}
            @Override public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){
                dragging=false;
                if(!ready||closed)return;
                try{player.seekTo((long)bar.getProgress(),MediaPlayer.SEEK_CLOSEST);}catch(IllegalStateException e){error();}
            }
        });
        dialog=new FontDialog.Builder(context).setTitle(video?"Видео":"Аудио")
            .setView(box).setPositiveButton("Закрыть",null).create();
        dialog.setOnDismissListener(d->close());
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try{
            handler.postDelayed(prepareTimeout,15000);
            dialog.show();
            if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            if(!video)start(null);
        }catch(RuntimeException e){close();throw e;}
    }
    private void start(SurfaceHolder surface){
        if(closed||failed||player!=null)return;
        try{
            player=new MediaPlayer();
            player.setDataSource(source);
            if(surface!=null)player.setDisplay(surface);
            player.setOnPreparedListener(p->{
                if(closed||player!=p)return;
                handler.removeCallbacks(prepareTimeout);
                ready=true;status.setText("Медиа открыто · защищённый режим");
                seek.setMax(Math.max(0,p.getDuration()));seek.setEnabled(true);play.setEnabled(true);
                try{p.start();handler.post(ticker);}catch(IllegalStateException e){error();}
            });
            player.setOnCompletionListener(p->{if(!closed&&player==p)play.setText("Воспроизвести");});
            player.setOnErrorListener((p,w,e)->{if(player==p)error();return true;});
            player.prepareAsync();
        }catch(Exception e){error();}
    }
    private static String clock(int ms){int seconds=Math.max(0,ms)/1000;return String.format(java.util.Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);}
    private void error(){error("Не удалось воспроизвести: формат или кодек не поддерживается");}
    private void error(String message){
        if(closed||failed)return;
        failed=true;
        status.setText(message);
        release();
    }
    private void release(){
        ready=false;handler.removeCallbacks(ticker);handler.removeCallbacks(prepareTimeout);
        play.setEnabled(false);seek.setEnabled(false);
        if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
        try{source.close();}catch(java.io.IOException ignored){}
    }
    @Override public void close(){
        if(closed)return;
        closed=true;release();
        if(dialog!=null&&dialog.isShowing())dialog.dismiss();
    }
}
