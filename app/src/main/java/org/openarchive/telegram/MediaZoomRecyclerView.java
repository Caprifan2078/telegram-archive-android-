package org.openarchive.telegram;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import androidx.recyclerview.widget.RecyclerView;
import java.util.function.IntConsumer;

/** Recognizes pinch while RecyclerView retains normal scrolling and item gestures. */
final class MediaZoomRecyclerView extends RecyclerView {
    private IntConsumer listener;
    private boolean enabled;
    private float cumulative=1f;
    private final ScaleGestureDetector detector;
    MediaZoomRecyclerView(Context ctx){
        super(ctx);
        detector=new ScaleGestureDetector(ctx,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){
                cumulative=1f;return enabled;
            }
            @Override public boolean onScale(ScaleGestureDetector d){
                if(!enabled||listener==null)return false;
                cumulative*=d.getScaleFactor();
                if(cumulative>=1.18f){
                    cumulative=1f;
                    post(()->{if(enabled&&listener!=null)listener.accept(-1);});
                }else if(cumulative<=.85f){
                    cumulative=1f;
                    post(()->{if(enabled&&listener!=null)listener.accept(1);});
                }
                return true;
            }
        });
    }
    void setZoom(boolean enabled,IntConsumer listener){
        this.enabled=enabled;this.listener=listener;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(enabled)detector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }
}
