package org.openarchive.telegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.function.Consumer;

/** Right edge media scrubber; it seeks source SQLite offsets, not RecyclerView's loaded window. */
final class MediaFastScroller extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int itemCount=0,position=0;
    private boolean dragging=false;
    private Consumer<Float> onSeek;
    private java.util.function.BiConsumer<Integer,Boolean> onDrag;

    MediaFastScroller(Context context){
        super(context);
        density=getResources().getDisplayMetrics().density;
        setContentDescription("Быстрая прокрутка медиатеки");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setFocusable(true);
    }
    static int indexForFraction(float fraction,int total){
        if(total<=1)return 0;
        if(!Float.isFinite(fraction))fraction=0f;
        fraction=Math.max(0f,Math.min(1f,fraction));
        return Math.max(0,Math.min(total-1,Math.round(fraction*(total-1))));
    }
    static float fractionForIndex(int index,int total){
        if(total<=1)return 0f;
        return Math.max(0f,Math.min(1f,(float)index/(total-1)));
    }
    void setOnSeek(Consumer<Float> listener){onSeek=listener;}
    void setOnDrag(java.util.function.BiConsumer<Integer,Boolean> listener){onDrag=listener;}
    void setProgress(int index,int total){
        itemCount=Math.max(0,total);
        if(!dragging)position=Math.max(0,Math.min(Math.max(0,itemCount-1),index));
        setContentDescription("Быстрая прокрутка медиатеки, файл "+(itemCount==0?0:position+1)+" из "+itemCount);
        invalidate();
    }
    private float dp(float dp){return density*dp;}
    private float top(){return dp(24);}
    private float bottom(){return Math.max(top()+1f,getHeight()-dp(24));}
    private float fractionForY(float y){return Math.max(0f,Math.min(1f,(y-top())/(bottom()-top())));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(itemCount<=1)return;
        float x=getWidth()-dp(15),a=top(),b=bottom();
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0x88415a70);paint.setStrokeWidth(dp(4));
        canvas.drawLine(x,a,x,b,paint);
        float fraction=fractionForIndex(position,itemCount);
        float center=a+fraction*(b-a);
        paint.setColor(0xff70cafa);paint.setStrokeWidth(dp(6));
        canvas.drawLine(x,a,x,center,paint);
        paint.setColor(0xffe7f8ff);
        canvas.drawCircle(x,center,dp(dragging?10:8),paint);
        // The separate tooltip is rendered by the parent to avoid clipping
        // text outside this 39dp-wide touch strip.
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(itemCount<=1||onSeek==null)return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                dragging=true;
                position=indexForFraction(fractionForY(event.getY()),itemCount);
                getParent().requestDisallowInterceptTouchEvent(true);
                if(onDrag!=null)onDrag.accept(position,true);
                invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!dragging)return false;
                position=indexForFraction(fractionForY(event.getY()),itemCount);
                if(onDrag!=null)onDrag.accept(position,true);
                invalidate();return true;
            case MotionEvent.ACTION_UP:
                if(!dragging)return false;
                float fraction=fractionForY(event.getY());
                position=indexForFraction(fraction,itemCount);
                dragging=false;
                if(onDrag!=null)onDrag.accept(position,false);
                invalidate();performClick();
                onSeek.accept(fraction);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging=false;if(onDrag!=null)onDrag.accept(position,false);
                invalidate();return true;
            default:return false;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
