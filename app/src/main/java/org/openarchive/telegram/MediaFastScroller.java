package org.openarchive.telegram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.Consumer;

/** Right edge media scrubber; it seeks source SQLite offsets, not RecyclerView's loaded window. */
final class MediaFastScroller extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private int itemCount=0,position=0,committedPosition=0;
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
        int previousTotal=itemCount;
        itemCount=Math.max(0,total);
        committedPosition=Math.max(0,Math.min(Math.max(0,itemCount-1),index));
        if(dragging&&previousTotal!=itemCount)finishDrag(false);
        if(!dragging)position=committedPosition;
        updateDescription();
        invalidate();
    }
    private void updateDescription(){
        setContentDescription("Быстрая прокрутка медиатеки, файл "+(itemCount==0?0:position+1)+" из "+itemCount);
    }
    private void finishDrag(boolean commit){
        if(!dragging)return;
        dragging=false;
        if(!commit)position=committedPosition;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        if(onDrag!=null)onDrag.accept(position,false);
        updateDescription();
        invalidate();
    }
    @Override protected void onDetachedFromWindow(){
        finishDrag(false);
        super.onDetachedFromWindow();
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.SeekBar.class.getName());
        boolean available=itemCount>1&&onSeek!=null&&isEnabled();
        info.setScrollable(available);
        if(itemCount>0){
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,1,itemCount,position+1));
        }
        if(available){
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            if(position<itemCount-1)info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
            if(position>0)info.addAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        }
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        boolean direct=action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId();
        boolean forward=action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD;
        boolean backward=action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        if(direct||forward||backward){
            if(itemCount<=1||onSeek==null||!isEnabled()||dragging)return false;
            int target;
            if(direct){
                if(args==null||!args.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE))return false;
                float value=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                if(!Float.isFinite(value))return false;
                target=(int)Math.max(0,Math.min((long)itemCount-1,(long)Math.round(value)-1));
            }else{
                target=(int)Math.max(0,Math.min((long)itemCount-1,
                    (long)position+(forward?MediaGalleryState.PAGE_SIZE:-MediaGalleryState.PAGE_SIZE)));
            }
            if(target==position)return false;
            position=target;
            updateDescription();
            invalidate();
            onSeek.accept(fractionForIndex(target,itemCount));
            return true;
        }
        return super.performAccessibilityAction(action,args);
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
        if(event.getActionMasked()==MotionEvent.ACTION_CANCEL&&dragging){finishDrag(false);return true;}
        if(itemCount<=1||onSeek==null||!isEnabled())return false;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                dragging=true;
                position=indexForFraction(fractionForY(event.getY()),itemCount);
                if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
                if(onDrag!=null)onDrag.accept(position,true);
                updateDescription();invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                if(!dragging)return false;
                position=indexForFraction(fractionForY(event.getY()),itemCount);
                if(onDrag!=null)onDrag.accept(position,true);
                updateDescription();invalidate();return true;
            case MotionEvent.ACTION_UP:
                if(!dragging)return false;
                float fraction=fractionForY(event.getY());
                position=indexForFraction(fraction,itemCount);
                finishDrag(true);
                performClick();
                onSeek.accept(fraction);
                return true;
            default:return false;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
