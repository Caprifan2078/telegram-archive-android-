package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** No editing, sharing, uploading, code execution or permanent extraction.
 * Opened files remain in their original archive / Telegram export location.
 */
public final class AttachmentPreviewActivity extends Activity {
    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private final List<ArchiveReadOnly.Entry> entries=new ArrayList<>();
    private LinearLayout panel;
    private ScrollView scroll;
    private Uri source;
    private String fileName;
    private char[] password;
    private int page=0,requestId,indexScrollY;
    private boolean nestedOpen;
    private static final int PAGE_SIZE=100;
    private static final int BACKGROUND=0xff101827,FG=0xffeef2f7,SUB=0xffa7b7cc,ACCENT=0xff6ddbc9;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        source=getIntent().getData();fileName=getIntent().getStringExtra("display_name");
        if(fileName==null||fileName.isEmpty())fileName=source==null?"Файл":source.getLastPathSegment();
        if(source==null){finish();return;}
        scroll=new ScrollView(this);
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(15),dp(20),dp(15),dp(20));panel.setBackgroundColor(BACKGROUND);
        scroll.addView(panel);setContentView(scroll);
        showHeader();
        if(ArchiveReadOnly.isArchive(fileName))loadIndex();
        else previewDocument(null);
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(5),dp(9),dp(5),dp(9));return t;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        panel.addView(b,new LinearLayout.LayoutParams(-1,dp(53)));
        b.setOnClickListener(v->action.run());return b;
    }
    private void showHeader(){
        panel.removeAllViews();
        if(scroll!=null)scroll.post(()->scroll.scrollTo(0,0));
        TextView title=text("▣ "+fileName,19,FG);title.setTypeface(null,1);title.setMaxLines(2);
        panel.addView(title);
        panel.addView(text("Только просмотр · локально · исходный файл не меняется",12,SUB));
        button("← Закрыть просмотр",this::finish);
        if(ArchiveReadOnly.isArchive(fileName))
            button("🔐 Ввести / изменить пароль",this::askPassword);
    }
    private void busy(String label){panel.addView(text(label,14,ACCENT));}
    private void fail(String error,boolean forArchive){
        showHeader();
        TextView msg=text("Не удалось открыть: "+error,15,FG);
        panel.addView(msg);
        if(forArchive)button("Повторить",this::loadIndex);
    }
    private void askPassword(){
        EditText input=new EditText(this);input.setHint("Пароль архива");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new AlertDialog.Builder(this).setTitle("Пароль архива")
            .setMessage("Пароль используется только в памяти для чтения локального файла.")
            .setView(input).setNegativeButton("Отмена",null)
            .setPositiveButton("Открыть",(d,which)->{
                if(password!=null)Arrays.fill(password,'\0');
                password=input.getText().toString().toCharArray();
                loadIndex();
            }).show();
    }
    private char[] passCopy(){return password==null?null:password.clone();}
    private void loadIndex(){
        final int current=++requestId;
        showHeader();busy("Читаю список вложений без распаковки всего архива…");
        char[] pass=passCopy();
        background.execute(()->{
            try{
                List<ArchiveReadOnly.Entry> found=ArchiveReadOnly.list(this,source,fileName,pass);
                runOnUiThread(()->{
                    if(current!=requestId||isFinishing()||isDestroyed())return;
                    entries.clear();entries.addAll(found);page=0;indexScrollY=0;renderIndex();
                });
            }catch(Exception error){
                runOnUiThread(()->{if(current==requestId&&!isFinishing())fail(error.getMessage(),true);});
            }finally{if(pass!=null)Arrays.fill(pass,'\0');}
        });
    }
    private void renderIndex(){
        // Returning while a selected file is still decoding must invalidate
        // its callback; otherwise that old result replaces the archive list.
        ++requestId;nestedOpen=false;
        showHeader();
        panel.addView(text("Вложений: "+entries.size()+" · показаны не более "+PAGE_SIZE+" на странице",13,ACCENT));
        int from=page*PAGE_SIZE,to=Math.min(entries.size(),from+PAGE_SIZE);
        for(int i=from;i<to;i++){
            ArchiveReadOnly.Entry entry=entries.get(i);
            String label=(entry.directory?"📁 ":"▣ ")+entry.name+
                (entry.encrypted?"  🔐":"")+(entry.size>=0?" · "+(entry.size/1024)+" КБ":"");
            if(entry.directory){panel.addView(text(label,13,SUB));continue;}
            button(label,()->previewDocument(entry.name));
        }
        if(page>0)button("‹ Предыдущая сотня",()->{page--;indexScrollY=0;renderIndex();});
        if(to<entries.size())button("Следующая сотня ›",()->{page++;indexScrollY=0;renderIndex();});
        if(entries.isEmpty())panel.addView(text("Архив пуст или содержит только неподдерживаемые ссылки.",13,SUB));
        final int restoreY=indexScrollY,renderToken=requestId;
        panel.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                if(panel.getViewTreeObserver().isAlive())panel.getViewTreeObserver().removeOnPreDrawListener(this);
                if(renderToken==requestId&&!nestedOpen)scroll.scrollTo(0,restoreY);
                return true;
            }
        });
    }
    private void previewDocument(String nested){
        if(nested!=null&&!nestedOpen)indexScrollY=scroll.getScrollY();
        nestedOpen=nested!=null;
        final int current=++requestId;
        showHeader();
        if(nested!=null)button("‹ Вернуться к списку",this::renderIndex);
        busy("Читаю файл…");
        char[] pass=passCopy();
        background.execute(()->{
            try{
                byte[] bytes;
                String name=nested==null?fileName:nested;
                if(nested!=null)bytes=ArchiveReadOnly.read(this,source,fileName,pass,nested);
                else try(InputStream raw=getContentResolver().openInputStream(source)){
                    if(raw==null)throw new java.io.IOException("Нет доступа к исходному файлу");
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                    byte[] buf=new byte[8192];int n,total=0;
                    while((n=raw.read(buf))!=-1){
                        if(n>ArchiveReadOnly.MAX_PREVIEW_BYTES-total)
                            throw new java.io.IOException("Слишком большой файл для встроенного просмотра");
                        total+=n;out.write(buf,0,n);
                    }
                    bytes=out.toByteArray();
                }
                String ext=OfficePreviewText.extension(name);
                if(ext.equals("png")||ext.equals("jpg")||ext.equals("jpeg")||ext.equals("webp")||ext.equals("gif")){
                    final Bitmap bitmap=decodePicture(bytes);
                    if(bitmap==null)throw new java.io.IOException("Изображение не декодируется");
                    runOnUiThread(()->{
                        if(current!=requestId||isFinishing()||isDestroyed()){bitmap.recycle();return;}
                        showHeader();if(nested!=null)button("‹ К архиву",this::renderIndex);
                        ImageView image=new ImageView(this);image.setImageBitmap(bitmap);
                        image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        panel.addView(image,new LinearLayout.LayoutParams(-1,-2));
                    });
                }else{
                    if(!OfficePreviewText.supported(name))
                        throw new java.io.IOException("Для этого формата пока нет встроенного безопасного просмотрщика. Файл не повреждён.");
                    String result=OfficePreviewText.render(new ByteArrayInputStream(bytes),name);
                    runOnUiThread(()->{
                        if(current!=requestId||isFinishing()||isDestroyed())return;
                        showHeader();if(nested!=null)button("‹ К архиву",this::renderIndex);
                        TextView viewer=text(result,14,FG);
                        if(ext.equals("xlsx")||ext.equals("csv"))viewer.setTypeface(android.graphics.Typeface.MONOSPACE);
                        viewer.setTextIsSelectable(true);panel.addView(viewer);
                        if(ext.equals("docx")||ext.equals("xlsx")||ext.equals("pptx"))
                            panel.addView(text("Предпросмотр текста и данных. Сложная разметка, рисунки, диаграммы и анимация Office здесь не воспроизводятся.",12,SUB));
                        if(ext.equals("xlsx"))
                            panel.addView(text("Формулы не пересчитываются: показаны сохранённые значения. Даты могут отображаться числами.",12,SUB));
                    });
                }
            }catch(Exception ex){
                runOnUiThread(()->{
                    if(current!=requestId||isFinishing()||isDestroyed())return;
                    fail(ex.getMessage(),nested!=null);
                    if(nested!=null)button("‹ К списку",this::renderIndex);
                });
            }finally{if(pass!=null)Arrays.fill(pass,'\0');}
        });
    }
    private Bitmap decodePicture(byte[] data){
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
        long pixels=(long)bounds.outWidth*bounds.outHeight;int sample=1;
        while(pixels/(sample*sample)>4_000_000L)sample*=2;
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=sample;
        return BitmapFactory.decodeByteArray(data,0,data.length,opt);
    }
    @Override public void onBackPressed(){
        if(nestedOpen){renderIndex();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){
        requestId++;background.shutdownNow();
        if(password!=null)Arrays.fill(password,'\0');
        password=null;super.onDestroy();
    }
}
