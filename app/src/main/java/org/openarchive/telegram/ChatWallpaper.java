package org.openarchive.telegram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Deterministic, offline chat wallpapers. No bitmap allocations in draw(). */
final class ChatWallpaper extends Drawable {
    private final int style;
    private final float strength;
    private final boolean light;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape=new Path();

    ChatWallpaper(int style,float strength,boolean light){
        this.style=Math.max(0,Math.min(3,style));
        this.strength=Math.max(0f,Math.min(1f,strength));
        this.light=light;
    }
    @Override public void draw(Canvas canvas){
        int width=getBounds().width(),height=getBounds().height();
        if(width<1||height<1)return;
        int a=light?0xfff7f9fd:0xff0b1525,b=light?0xffe8f1f7:0xff122c3c;
        if(style==1){a=light?0xfff9f2e8:0xff19182a;b=light?0xffe9d9cb:0xff29223d;}
        if(style==2){a=light?0xfff4f8fa:0xff111c1e;b=light?0xffdbe8df:0xff193d34;}
        if(style==3){a=light?0xfffbf9fd:0xff131b2e;b=light?0xffe9e4f2:0xff242a43;}
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
        paint.setShader(new LinearGradient(0,0,width,height,a,b,Shader.TileMode.CLAMP));
        canvas.drawRect(getBounds(),paint);paint.setShader(null);
        if(strength<0.02f)return;
        int ink=light?0xff66798d:0xffa4c7dd;
        if(style==0||style==3){
            paint.setColor(ink);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(style==0?1.2f:1.8f);
            paint.setAlpha((int)(strength*(light?28:20)));
            int gap=style==0?90:118;
            for(int x=-80;x<width+gap;x+=gap){
                for(int y=-80;y<height+gap;y+=gap){
                    shape.reset();
                    if(style==0){
                        shape.moveTo(x,y+20);shape.cubicTo(x+25,y-13,x+65,y+60,x+90,y+24);
                    }else{
                        shape.moveTo(x+8,y+8);shape.lineTo(x+30,y+25);shape.lineTo(x+8,y+42);
                        shape.moveTo(x+44,y+8);shape.lineTo(x+66,y+25);shape.lineTo(x+44,y+42);
                    }
                    canvas.drawPath(shape,paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
        }else if(style==1){
            paint.setStyle(Paint.Style.FILL);paint.setColor(ink);
            for(int y=35;y<height+60;y+=95)for(int x=30;x<width+60;x+=88){
                float offset=((y/95)&1)==0?0:42;
                paint.setAlpha((int)(strength*35));
                canvas.drawCircle(x+offset,y,1.8f,paint);
                paint.setAlpha((int)(strength*13));
                canvas.drawCircle(x+offset,y,15,paint);
            }
        }else{
            paint.setStyle(Paint.Style.STROKE);paint.setColor(ink);
            paint.setStrokeWidth(1.3f);paint.setAlpha((int)(strength*29));
            for(int y=-50;y<height+100;y+=105){
                shape.reset();shape.moveTo(-15,y+38);
                for(int x=0;x<width+90;x+=90)
                    shape.cubicTo(x+25,y-14,x+65,y+80,x+90,y+38);
                canvas.drawPath(shape,paint);
            }
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setAlpha(255);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.OPAQUE;}
}