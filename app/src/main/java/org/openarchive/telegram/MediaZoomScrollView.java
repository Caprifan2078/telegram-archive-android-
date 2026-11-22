package org.openarchive.telegram;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ScrollView;
import java.util.function.IntConsumer;

/** Dispatches gallery pinch without intercepting normal vertical scrolling. */
final class MediaZoomScrollView extends ScrollView {
    private IntConsumer zoomListener;
    private boolean zoomEnabled;
    private float scaleAccumulated=1f;
    private final ScaleGestureDetector detector;
    MediaZoomScrollView(Context context){
        super(context);
        detector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScaleBegin(ScaleGestureDetector d){
                scaleAccumulated=1f;return zoomEnabled;
            }
            @Override public boolean onScale(ScaleGestureDetector d){
                if(!zoomEnabled||zoomListener==null)return false;
                scaleAccumulated*=d.getScaleFactor();
                if(scaleAccumulated>=1.18f){
                    scaleAccumulated=1f;post(()->{if(zoomEnabled&&zoomListener!=null)zoomListener.accept(-1);});
                }else if(scaleAccumulated<=.85f){
                    scaleAccumulated=1f;post(()->{if(zoomEnabled&&zoomListener!=null)zoomListener.accept(1);});
                }
                return true;
            }
        });
    }
    void setZoomEnabled(boolean enabled,IntConsumer onZoom){
        zoomEnabled=enabled;zoomListener=onZoom;
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        if(zoomEnabled)detector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }
}
