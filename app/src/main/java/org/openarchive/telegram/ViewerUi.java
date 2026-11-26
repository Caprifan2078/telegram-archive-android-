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
        int top=sheen?mix(color,light?Color.WHITE:0xffc7e1ff,4):color;
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
        b.setAllCaps(false);b.setTextColor(new ColorStateList(
            new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
            new int[]{mix(foreground,color,42),foreground}));
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        b.setBackground(control(b.getContext(),color,CONTROL,light));
        b.setElevation(0);b.setStateListAnimator(null);b.setMinHeight(dp(b.getContext(),44));
        b.setMinWidth(0);b.setMinimumWidth(0);
    }
    static int themeIndex(Context c){
        // Activity.getPreferences uses the actual local class name; applicationId
        // suffixes can make that the full Java class name instead of MainActivity.
        String name=new android.content.ComponentName(c,MainActivity.class).getShortClassName();
        if(name.startsWith("."))name=name.substring(1);
        return c.getSharedPreferences(name,Context.MODE_PRIVATE).getInt("appearance_theme",0);
    }
    static ViewerPalette palette(Context c){return ViewerPalette.of(themeIndex(c));}
    static boolean light(Context c){return palette(c).light;}
    static void applyActivityTheme(android.app.Activity a){
        a.setTheme(light(a)?R.style.AppThemeLight:R.style.AppTheme);
    }
    static void systemBars(android.app.Activity a,ViewerPalette p){
        a.getWindow().setStatusBarColor(p.background);a.getWindow().setNavigationBarColor(p.background);
        int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        View decor=a.getWindow().getDecorView();decor.setSystemUiVisibility((decor.getSystemUiVisibility()&~flags)|(p.light?flags:0));
        if(android.os.Build.VERSION.SDK_INT>=30){
            android.view.WindowInsetsController controller=a.getWindow().getInsetsController();
            if(controller!=null){int mask=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(p.light?mask:0,mask);}
        }
    }
    static void styleInput(android.widget.EditText v,ViewerPalette p){
        v.setTextColor(p.text);v.setHintTextColor(p.muted);v.setTextSize(14);
        v.setBackground(surface(v.getContext(),p.surface,CONTROL,p.light,false));
        v.setPadding(dp(v.getContext(),12),dp(v.getContext(),8),dp(v.getContext(),12),dp(v.getContext(),8));
        v.setMinHeight(dp(v.getContext(),48));
    }
}
