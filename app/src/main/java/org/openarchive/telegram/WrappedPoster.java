package org.openarchive.telegram;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** Offline PNG; bounded bitmap size, written only to the user-selected SAF destination. */
final class WrappedPoster {
    private WrappedPoster(){}
    static void write(WrappedSummary summary,OutputStream output)throws IOException {
        final int width=900,height=1180;
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try {
            Canvas c=new Canvas(bitmap);
            c.drawColor(Color.rgb(9,20,37));
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.rgb(124,214,255));p.setTextSize(35);p.setFakeBoldText(true);
            c.drawText("TELEGRAM ARCHIVE",60,110,p);
            p.setTextSize(100);p.setColor(Color.WHITE);
            c.drawText(String.valueOf(summary.year),55,230,p);
            p.setTextSize(23);p.setColor(Color.rgb(182,203,227));p.setFakeBoldText(false);
            c.drawText("Год общения · рассчитано полностью офлайн",60,284,p);
            metric(c,p,"Сообщений",summary.messages,350);
            metric(c,p,"Активных дней",summary.activeDays,470);
            metric(c,p,"Лучшая серия дней",summary.streak,590);
            if(summary.busiestDay!=null){
                p.setColor(Color.rgb(185,206,229));p.setTextSize(25);
                c.drawText("Рекордный день: "+summary.busiestDay,60,664,p);
            }
            p.setColor(Color.WHITE);p.setTextSize(26);p.setFakeBoldText(true);
            c.drawText("Сообщения по месяцам",60,765,p);p.setFakeBoldText(false);
            long max=1;for(long n:summary.months)max=Math.max(max,n);
            for(int i=0;i<12;i++){
                float x=66+i*65f;
                float h=(float)summary.months[i]/max*205f;
                p.setColor(Color.rgb(101,202,224));
                c.drawRoundRect(new RectF(x,1015-h,x+42,1015),9,9,p);
                p.setColor(Color.rgb(190,211,232));p.setTextSize(18);
                c.drawText(String.format(Locale.ROOT,"%02d",i+1),x+7,1046,p);
            }
            p.setColor(Color.rgb(161,181,205));p.setTextSize(20);
            c.drawText("Даты и отправители — только из импортированной истории",60,1130,p);
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))
                throw new IOException("Не удалось записать изображение");
            output.flush();
        }finally{bitmap.recycle();}
    }
    private static void metric(Canvas c,Paint p,String name,long count,int y){
        p.setColor(Color.rgb(174,202,222));p.setTextSize(25);p.setFakeBoldText(false);
        c.drawText(name,60,y,p);
        p.setColor(Color.WHITE);p.setTextSize(57);p.setFakeBoldText(true);
        c.drawText(String.format(Locale.ROOT,"%,d",count),60,y+64,p);
    }
}
