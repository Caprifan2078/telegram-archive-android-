package org.openarchive.telegram;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import java.util.Locale;

/** Cohesive 24dp stroke icons; avoids mixing Android-era bitmap icons and emojis. */
final class MenuOutlineIcon extends Drawable {
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int type;
    MenuOutlineIcon(String label,int color){
        String value=label==null?"":label.toLowerCase(Locale.ROOT);
        if(value.contains("замет")||value.contains("шрифт"))type=1;
        else if(value.contains("резерв")||value.contains("архив")||value.contains("копи")||value.contains("корзин"))type=2;
        else if(value.contains("помощ")||value.contains("инструкц"))type=3;
        else if(value.contains("диагност")||value.contains("провер"))type=4;
        else if(value.contains("учебн"))type=5;
        else if(value.contains("поиск"))type=6;
        else if(value.contains("оформлен")||value.contains("настрой")||value.contains("шрифт"))type=7;
        else if(value.contains("медиа")||value.contains("галере")||value.contains("кинолент"))type=8;
        else if(value.contains("хронолог")||value.contains("истори"))type=9;
        else if(value.contains("сравнен")||value.contains("двух"))type=10;
        else type=0;
        stroke.setColor(color);stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.8f);stroke.setStrokeCap(Paint.Cap.ROUND);stroke.setStrokeJoin(Paint.Join.ROUND);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,stroke);}
    private void round(Canvas c,float a,float b,float d,float e,float radius){c.drawRoundRect(a,b,d,e,radius,radius,stroke);}
    @Override public void draw(Canvas output){
        android.graphics.Rect bounds=getBounds();
        int saved=output.save();
        output.translate(bounds.left,bounds.top);
        output.scale(bounds.width()/24f,bounds.height()/24f);
        switch(type){
            case 1: // note
                round(output,5,3.5f,19,20.5f,2);
                line(output,8,9,16,9);line(output,8,13,16,13);line(output,8,17,13,17);break;
            case 2: // archive box
                round(output,3.5f,5,20.5f,19.5f,2);
                line(output,4.5f,10,19.5f,10);
                line(output,10,14.5f,14,14.5f);break;
            case 3: // help
                output.drawCircle(12,12,9,stroke);
                output.drawArc(new RectF(9,5.5f,16,13),205,235,false,stroke);
                line(output,12,12.7f,12,14);output.drawPoint(12,17,stroke);break;
            case 4: // diagnostics
                output.drawCircle(12,12,9,stroke);
                line(output,12,10,12,17);output.drawPoint(12,7,stroke);break;
            case 5: // study cap
                line(output,3,9,12,4);line(output,12,4,21,9);
                line(output,21,9,12,14);line(output,12,14,3,9);
                line(output,6.5f,11,6.5f,17);line(output,6.5f,17,12,20);
                line(output,12,20,17.5f,17);line(output,17.5f,17,17.5f,11);break;
            case 6: // magnifier
                output.drawCircle(10.5f,10.5f,6.2f,stroke);line(output,15,15,20.5f,20.5f);break;
            case 7: // settings sliders
                line(output,4,6,20,6);line(output,4,12,20,12);line(output,4,18,20,18);
                output.drawCircle(9,6,2.2f,stroke);output.drawCircle(16,12,2.2f,stroke);
                output.drawCircle(10.5f,18,2.2f,stroke);break;
            case 8: // media image
                round(output,3.5f,5,20.5f,19,2);
                output.drawCircle(9,9,1.5f,stroke);
                line(output,6,16,10.5f,12);line(output,10.5f,12,13,14);
                line(output,13,14,16,11);line(output,16,11,19,15);break;
            case 9: // clock
                output.drawCircle(12,12,8.5f,stroke);
                line(output,12,7,12,12);line(output,12,12,16,14.5f);break;
            case 10: // comparison
                line(output,4,8,20,8);line(output,16,4,20,8);line(output,20,8,16,12);
                line(output,20,16,4,16);line(output,8,12,4,16);line(output,4,16,8,20);break;
            default: // chats: two message outlines
                round(output,3.5f,4.5f,20.5f,17.5f,3);
                line(output,8,9,16,9);line(output,8,13,13,13);
                line(output,8,17.5f,6,21);
        }
        output.restoreToCount(saved);
    }
    @Override public void setAlpha(int alpha){stroke.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){stroke.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
