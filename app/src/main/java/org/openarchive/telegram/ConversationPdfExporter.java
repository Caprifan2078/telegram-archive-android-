package org.openarchive.telegram;

import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.io.IOException;
import java.io.OutputStream;

/** Offline, text-only export. The cursor is streamed, not collected in memory. */
final class ConversationPdfExporter {
    private static final int WIDTH=595, HEIGHT=842, MARGIN=42, MAX_PAGES=250;
    private final PdfDocument document=new PdfDocument();
    private final TextPaint paint=new TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private PdfDocument.Page page;
    private int pageCount, y;

    private void nextPage() throws IOException {
        if(page!=null){document.finishPage(page);page=null;}
        if(pageCount>=MAX_PAGES)throw new IOException("Переписка слишком большая для одного PDF (более 250 страниц)");
        page=document.startPage(new PdfDocument.PageInfo.Builder(WIDTH,HEIGHT,++pageCount).create());
        page.getCanvas().drawColor(Color.WHITE);y=MARGIN;
    }
    private void paragraph(String text, float size, boolean bold) throws IOException {
        paint.setColor(Color.BLACK);paint.setTextSize(size);
        paint.setTypeface(bold?android.graphics.Typeface.DEFAULT_BOLD:android.graphics.Typeface.DEFAULT);
        String value=text==null?"":text;
        if(value.isEmpty())value=" ";
        // Bound layout memory even for a single abnormally long message.
        for(int start=0;start<value.length();){
            int end=Math.min(value.length(),start+4000);
            if(end<value.length()&&Character.isHighSurrogate(value.charAt(end-1)))end--;
            StaticLayout layout=StaticLayout.Builder.obtain(value,start,end,paint,WIDTH-2*MARGIN)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
            int line=0;
            while(line<layout.getLineCount()){
                if(page==null||y+layout.getLineBottom(line)-layout.getLineTop(line)>HEIGHT-MARGIN)nextPage();
                int first=line, top=layout.getLineTop(first);
                while(line<layout.getLineCount() && y+layout.getLineBottom(line)-top<=HEIGHT-MARGIN)line++;
                if(line==first)throw new IOException("Не удалось разместить текст на странице");
                int bottom=layout.getLineBottom(line-1);
                Canvas canvas=page.getCanvas();canvas.save();
                canvas.clipRect(MARGIN,y,WIDTH-MARGIN,y+bottom-top);
                canvas.translate(MARGIN,y-top);layout.draw(canvas);canvas.restore();
                y+=bottom-top;
            }
            start=end;
        }
        y+=8;
    }
    static int write(MessageDb db,String title,OutputStream output) throws IOException {
        ConversationPdfExporter exporter=new ConversationPdfExporter();
        try {
            exporter.paragraph(title,16,true);
            exporter.paragraph("Текст переписки. Вложения обозначены текстом и в PDF не включены.",10,false);
            int count=0;
            try(Cursor rows=db.getReadableDatabase().rawQuery(
                "SELECT author,date_text,body,media,media_type FROM messages ORDER BY id",null)) {
                while(rows.moveToNext()){
                    String author=rows.isNull(0)?"":rows.getString(0);
                    String date=rows.isNull(1)?"":rows.getString(1);
                    String body=rows.isNull(2)?"":rows.getString(2);
                    exporter.paragraph(author+"  "+date,11,true);
                    if(!body.isEmpty())exporter.paragraph(body,11,false);
                    if(!rows.isNull(3)&&!rows.getString(3).isEmpty()){
                        String type=rows.isNull(4)?"файл":rows.getString(4);
                        exporter.paragraph("[Вложение: "+type+"]",10,false);
                    }
                    count++;
                }
            }
            if(count==0)exporter.paragraph("В переписке нет сообщений.",11,false);
            if(exporter.page!=null){exporter.document.finishPage(exporter.page);exporter.page=null;}
            exporter.document.writeTo(output);output.flush();
            return exporter.pageCount;
        } finally {
            if(exporter.page!=null)exporter.document.finishPage(exporter.page);
            exporter.document.close();
        }
    }
}
