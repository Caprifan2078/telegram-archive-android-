package org.openarchive.telegram;

import android.content.Context;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.*;
import java.util.*;

/** Offline visual export. Every message is streamed from SQLite; original media are never modified. */
final class ConversationPdfExporter {
    private static final int WIDTH=595,HEIGHT=842,MARGIN=34,TOP=80,BOTTOM=800;
    static final int MAX_PAGES=2000;
    private static final long MAX_PREVIEW_BYTES=128L*1024*1024;
    private static final int INK=0xff172b3d,MUTED=0xff536879,BLUE=0xff286d9b;
    private final PdfDocument document=new PdfDocument();
    private final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint graphics=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final String title;
    private final ArrayList<Bitmap> pageBitmaps=new ArrayList<>();
    private PdfDocument.Page page;
    private int pageCount,y,previews,missing;
    private long previewBytes;
    private int bubbleColor=0xffedf4f8;
    private String lastDay="";
    static final class Result {
        final int pages,previews,missing;
        Result(int p,int v,int m){pages=p;previews=v;missing=m;}
    }
    private ConversationPdfExporter(String title){this.title=title==null||title.trim().isEmpty()?"Переписка":title;}
    private void finishPage(){
        if(page==null)return;
        Canvas c=page.getCanvas();
        graphics.setColor(0xffdbe6ed);c.drawLine(MARGIN,814,WIDTH-MARGIN,814,graphics);
        plain(c,"Telegram Archive • локальный экспорт",MARGIN,829,9,MUTED,false);
        String n="Страница "+pageCount;
        paint.setTextSize(9);paint.setTypeface(Typeface.DEFAULT);
        plain(c,n,WIDTH-MARGIN-paint.measureText(n),829,9,MUTED,false);
        document.finishPage(page);page=null;
        // Do not recycle bitmap pixels referenced by the native PDF document.
        // Drop Java references after completing the page; total decoded pixels are budgeted.
        pageBitmaps.clear();
    }
    private void nextPage()throws IOException{
        finishPage();
        if(pageCount>=MAX_PAGES)throw new IOException("Более "+MAX_PAGES+" страниц. Экспорт не завершён.");
        page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageCount).create());
        Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
        graphics.setColor(BLUE);c.drawRoundRect(MARGIN,25,MARGIN+5,59,2,2,graphics);
        String display=android.text.TextUtils.ellipsize(title,configured(16,INK,true),WIDTH-2*MARGIN-19,android.text.TextUtils.TruncateAt.END).toString();
        plain(c,display,MARGIN+15,42,16,INK,true);
        plain(c,"Хронология сообщений • офлайн",MARGIN+15,58,9,MUTED,false);
        graphics.setColor(0xffdbe6ed);c.drawLine(MARGIN,69,WIDTH-MARGIN,69,graphics);
        y=TOP;
    }
    private TextPaint configured(float size,int color,boolean bold){
        paint.setColor(color);paint.setTextSize(size);paint.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);return paint;
    }
    private void plain(Canvas c,String s,float x,float baseline,float size,int color,boolean bold){
        c.drawText(s,x,baseline,configured(size,color,bold));
    }
    private void ensure(int h)throws IOException{if(page==null||y+h>BOTTOM)nextPage();}
    private void background(int height,int color){
        graphics.setColor(color);page.getCanvas().drawRoundRect(MARGIN,y,WIDTH-MARGIN,y+height,8,8,graphics);
    }
    private void paragraph(String text,float size,boolean bold,int color,boolean card)throws IOException{
        if(text==null||text.isEmpty())return;
        for(int start=0;start<text.length();){
            int end=Math.min(text.length(),start+4000);
            if(end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
            CharSequence chunk=text.subSequence(start,end);
            int inset=card?12:0, width=WIDTH-2*(MARGIN+inset);
            TextPaint tp=configured(size,color,bold);
            StaticLayout layout=StaticLayout.Builder.obtain(chunk,0,chunk.length(),tp,width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(2,1f).build();
            int line=0;
            while(line<layout.getLineCount()){
                ensure(layout.getLineBottom(line)-layout.getLineTop(line)+8);
                int first=line,top=layout.getLineTop(first);
                while(line<layout.getLineCount()&&y+layout.getLineBottom(line)-top+8<=BOTTOM)line++;
                if(first==line)throw new IOException("Не удалось разместить сообщение");
                int h=layout.getLineBottom(line-1)-top;
                if(card)background(h+8,bubbleColor);
                // Rebuild only the visible lines: clipping a full layout can duplicate
                // off-page glyphs in PDF text extraction.
                CharSequence visible=layout.getText().subSequence(layout.getLineStart(first),layout.getLineEnd(line-1));
                StaticLayout vl=StaticLayout.Builder.obtain(visible,0,visible.length(),tp,width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(2,1f).build();
                Canvas c=page.getCanvas();c.save();c.clipRect(MARGIN+inset,y+4,WIDTH-MARGIN-inset,y+4+h);
                c.translate(MARGIN+inset,y+4);vl.draw(c);c.restore();y+=h+8;
            }
            start=end;
        }
    }
    private void messageHeader(String author,String date)throws IOException{
        String day=date.length()>=10?date.substring(0,10):date;
        if(!day.equals(lastDay)){
            ensure(54);paragraph(day,10,true,BLUE,false);y+=3;lastDay=day;
        }
        ensure(46);
        bubbleColor=(author.hashCode()&1)==0?0xffedf4f8:0xfff1f5f1;
        paragraph((author.isEmpty()?"Неизвестный отправитель":author)+"  •  "+date,10,true,INK,true);
    }
    private static String kindLabel(String kind){
        switch(kind){
            case "image":return "Фото";
            case "round":return "Видеокружок";
            case "video":return "Видео";
            case "gif":return "Анимация";
            case "sticker":return "Стикер";
            case "voice":return "Голосовое сообщение";
            case "audio":return "Аудио";
            default:return "Вложение";
        }
    }
    private static String filename(String path){int slash=Math.max(path.lastIndexOf('/'),path.lastIndexOf('\\'));return path.substring(slash+1);}
    private void attachment(Context context,DocumentFolder folder,String path,String kind,boolean include)throws IOException{
        if(kind==null)kind="";
        String label=kindLabel(kind);
        boolean video="round".equals(kind)||"video".equals(kind)||path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)(\\?.*)?$");
        boolean picture="image".equals(kind)||"gif".equals(kind)||"sticker".equals(kind)||video;
        Bitmap bitmap=null;
        if(include&&picture&&context!=null&&folder!=null&&previewBytes<MAX_PREVIEW_BYTES){
            try{
                Uri uri=folder.resolve(path);
                if(uri!=null){
                    MediaVisionInput.Sample sample=MediaVisionInput.read(context,uri,video,new java.util.concurrent.atomic.AtomicBoolean());
                    Bitmap raw=BitmapFactory.decodeByteArray(sample.jpeg,0,sample.jpeg.length);
                    if(raw!=null){
                        if("round".equals(kind)&&sample.frames>1){
                            int frameWidth=raw.getWidth()/sample.frames;
                            Bitmap center=Bitmap.createBitmap(raw,frameWidth*(sample.frames/2),0,frameWidth,raw.getHeight());
                            if(center!=raw)raw.recycle();raw=center;
                        }
                        int maxEdge=Math.max(raw.getWidth(),raw.getHeight());
                        if(maxEdge>480){Bitmap scaled=Bitmap.createScaledBitmap(raw,Math.max(1,raw.getWidth()*480/maxEdge),Math.max(1,raw.getHeight()*480/maxEdge),true);if(scaled!=raw)raw.recycle();raw=scaled;}
                        long bytes=raw.getAllocationByteCount();
                        if(bytes<=MAX_PREVIEW_BYTES-previewBytes){bitmap=raw;previewBytes+=bytes;}else raw.recycle();
                    }
                }
            }catch(Exception|LinkageError ignored){/* Explicit fallback below. */}
        }
        if(bitmap==null){
            if(include&&picture){missing++;label+=" • превью недоступно";}
            paragraph(label+" — "+filename(path),10,false,MUTED,true);
            return;
        }
        boolean round="round".equals(kind);
        float maxW=round?134:WIDTH-2*MARGIN-24,maxH=round?134:210;
        float factor=Math.min(maxW/bitmap.getWidth(),maxH/bitmap.getHeight());
        float bw=bitmap.getWidth()*factor,bh=bitmap.getHeight()*factor;
        if(round){bw=bh=Math.min(bw,bh);}
        ensure((int)Math.ceil(bh)+38);
        paragraph(label+(video?" • кадры без звука":""),10,true,BLUE,true);
        ensure((int)Math.ceil(bh)+10);
        background((int)Math.ceil(bh)+10,bubbleColor);
        Canvas c=page.getCanvas();float left=MARGIN+12,top=y+5;
        RectF dest=new RectF(left,top,left+bw,top+bh);
        c.save();
        Path mask=new Path();if(round)mask.addOval(dest,Path.Direction.CW);else mask.addRoundRect(dest,6,6,Path.Direction.CW);
        c.clipPath(mask);
        Rect source=null;
        if(round){int side=Math.min(bitmap.getWidth(),bitmap.getHeight());int x=(bitmap.getWidth()-side)/2,yy=(bitmap.getHeight()-side)/2;source=new Rect(x,yy,x+side,yy+side);}
        c.drawBitmap(bitmap,source,dest,graphics);c.restore();
        pageBitmaps.add(bitmap);previews++;y+=(int)Math.ceil(bh)+10;
    }
    static int write(MessageDb db,String title,OutputStream output)throws IOException{
        return write(db,title,output,null,null,false).pages;
    }
    static Result write(MessageDb db,String title,OutputStream output,Context context,DocumentFolder folder,boolean include)throws IOException{
        ConversationPdfExporter e=new ConversationPdfExporter(title);
        try{
            e.nextPage();
            e.paragraph(include?"Фото и кадры видео встраиваются в PDF. Воспроизведение видео и звука — в исходном архиве.":"Текстовая версия • вложения обозначены по имени.",10,false,MUTED,false);e.y+=8;
            int count=0;
            android.database.sqlite.SQLiteDatabase sql=db.getReadableDatabase();
            // A second ordered cursor keeps multi-attachment messages complete without N+1 queries.
            try(Cursor rows=sql.rawQuery("SELECT id,author,date_text,body,media,media_type FROM messages ORDER BY id",null);
                Cursor attachments=sql.rawQuery("SELECT message_id,path,kind FROM attachments ORDER BY message_id,id",null)){
                boolean has=attachments.moveToFirst();
                while(rows.moveToNext()){
                    long id=rows.getLong(0);String author=rows.isNull(1)?"":rows.getString(1),date=rows.isNull(2)?"":rows.getString(2);
                    String body=rows.isNull(3)?"":rows.getString(3);
                    e.messageHeader(author,date);e.paragraph(body,11,false,INK,true);
                    while(has&&attachments.getLong(0)<id)has=attachments.moveToNext();
                    boolean found=false;
                    while(has&&attachments.getLong(0)==id){
                        e.attachment(context,folder,attachments.getString(1),attachments.getString(2),include);
                        found=true;has=attachments.moveToNext();
                    }
                    if(!found&&!rows.isNull(4)&&!rows.getString(4).isEmpty())e.attachment(context,folder,rows.getString(4),rows.getString(5),include);
                    e.y+=7;count++;
                }
            }
            if(count==0)e.paragraph("В переписке нет сообщений.",11,false,INK,false);
            if(include&&e.missing>0){
                e.y+=8;e.paragraph("Превью не удалось встроить: "+e.missing+". Возможные причины: файл отсутствует, формат не поддерживается или достигнут лимит памяти PDF. Все сообщения сохранены.",10,false,MUTED,false);
            }
            e.finishPage();e.document.writeTo(output);output.flush();return new Result(e.pageCount,e.previews,e.missing);
        }catch(OutOfMemoryError memory){throw new IOException("Недостаточно памяти для PDF. Попробуй текстовый экспорт. Исходная переписка сохранена.",memory);}
        finally{try{e.finishPage();}finally{e.document.close();}}
    }
}
