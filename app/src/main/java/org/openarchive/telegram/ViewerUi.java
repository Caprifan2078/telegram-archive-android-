package org.openarchive.telegram;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.graphics.Typeface;

/** Shared native UI tokens. No bitmap shaders, blur, network resources or extra dependencies. */
final class ViewerUi {
    static final int BACKGROUND=0xff0b111b, SURFACE=0xff172231, RAISED=0xff203044;
    static final int TEXT=0xfff1f5fa, MUTED=0xffaebdce, ACCENT=0xff81caff;
    static final int CARD=16, CONTROL=12, HERO=20;
    private ViewerUi(){}
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static int mix(int color,int tint,int percent){
        int p=Math.max(0,Math.min(100,percent)),q=100-p;
        return Color.argb(Color.alpha(color),
            (Color.red(color)*q+Color.red(tint)*p+50)/100,
            (Color.green(color)*q+Color.green(tint)*p+50)/100,
            (Color.blue(color)*q+Color.blue(tint)*p+50)/100);
    }
    static GradientDrawable surface(Context c,int color,int radius,boolean light,boolean sheen){
        int top= sheen?mix(color,light?Color.WHITE:0xffc7e1ff,light?4:4):color;
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{top,color});
        d.setCornerRadius(dp(c,radius));
        d.setStroke(Math.max(1,dp(c,1)),light?0x2070879f:0x24b2c8e4);
        return d;
    }
    static RippleDrawable control(Context c,int color,int radius,boolean light){
        GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(c,radius));
        return new RippleDrawable(ColorStateList.valueOf(light?0x201468a4:0x3681caff),surface(c,color,radius,light,false),mask);
    }
    static void styleButton(Button b,int color,int foreground,boolean light){
        b.setAllCaps(false);b.setTextColor(foreground);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(control(b.getContext(),color,CONTROL,light));
        b.setElevation(0);b.setStateListAnimator(null);b.setMinHeight(dp(b.getContext(),44));
        b.setMinWidth(0);b.setMinimumWidth(0);
    }
    static boolean light(Context c){return c.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getInt("appearance_theme",0)==2;}
}
