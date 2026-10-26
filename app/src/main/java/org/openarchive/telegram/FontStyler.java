package org.openarchive.telegram;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.WeakHashMap;
/** Typography binding to both Activity roots and Dialog windows, including late-loaded views. */
final class FontStyler {
    private FontStyler(){}
    static void bind(Activity owner){bind(owner.getWindow().getDecorView(),owner);}
    static void bind(Dialog dialog){
        if(dialog.getWindow()!=null)bind(dialog.getWindow().getDecorView(),dialog.getContext());
    }
    private static void bind(View decor,Context context){
        Typeface selected=FontManager.resolve(context);
        if(selected==null||decor==null)return;
        final WeakHashMap<TextView,Integer> done=new WeakHashMap<>();
        final Handler ui=new Handler(Looper.getMainLooper());
        final Runnable[] work=new Runnable[1];
        work[0]=()->{walk(decor,selected,done);};
        // Rate-limit traversal. TextViews are processed once per style even on large message pages.
        decor.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            ui.removeCallbacks(work[0]);ui.postDelayed(work[0],85);
        });
        ui.post(work[0]);
    }
    private static void walk(View view,Typeface face,WeakHashMap<TextView,Integer> done){
        if("ta-font-preview".equals(view.getTag()))return;
        if(view instanceof TextView){
            TextView tv=(TextView)view;
            Typeface current=tv.getTypeface();
            int style=current==null?Typeface.NORMAL:current.getStyle();
            Integer previous=done.get(tv);
            if(previous==null||previous!=style){
                tv.setTypeface(Typeface.create(face,style));done.put(tv,style);
            }
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),face,done);
        }
    }
}
