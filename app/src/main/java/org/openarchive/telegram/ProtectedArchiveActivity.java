package org.openarchive.telegram;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.jsoup.nodes.Element;

/** Experimental protected viewer: separate from persistent SQLite and backup features. */
public final class ProtectedArchiveActivity extends Activity {
    private static final int PICK=81, HTML_LIMIT=32*1024*1024, IMAGE_LIMIT=16*1024*1024;
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private ProtectedArchiveSession session;
    private Future<?> task;
    private char[] pendingPassword;
    private ProtectedMediaPlayer mediaPlayer;
    private int previewGeneration;
    private int generation;
    private AlertDialog prompt;
    private ArrayList<Line> lines;
    private String root="";
    private static final class Line {
        final String text;final List<TelegramImporter.Media> media;
        Line(String text,List<TelegramImporter.Media> media){this.text=text;this.media=media;}
        @Override public String toString(){return text;}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int p=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);
        setContentView(body);library();
    }
    private SharedPreferences pins(){return getSharedPreferences("protected_archive_pins",MODE_PRIVATE);}
    private JSONArray entries(){
        try{return new JSONArray(pins().getString("items","[]"));}
        catch(JSONException e){return new JSONArray();}
    }
    private void title(String s){TextView t=new TextView(this);t.setTextSize(21);t.setText(s);body.addView(t);}
    private void label(String s){TextView t=new TextView(this);t.setText(s);body.addView(t);}
    private void button(String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setOnClickListener(v->action.run());body.addView(b);
    }
    private void library(){
        body.removeAllViews();
        title("Защищённые архивы · экспериментально");
        label("ZIP / RAR · HTML Telegram. Сохраняется только ссылка на файл. Пароль нужен при каждом открытии.");
        button("Добавить архив",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,PICK);
        });
        JSONArray list=entries();
        for(int j=0;j<list.length();j++){
            JSONObject item=list.optJSONObject(j);if(item==null)continue;
            String name=item.optString("name","Архив");Uri uri=Uri.parse(item.optString("uri"));
            Button b=new Button(this);b.setText("🔒 "+name);body.addView(b);
            b.setOnClickListener(v->ask(uri,name));
            b.setOnLongClickListener(v->{
                new AlertDialog.Builder(this).setTitle("Убрать из списка?")
                    .setMessage("Исходный архив останется на телефоне.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Убрать",(d,w)->{unpin(uri);library();}).show();
                return true;
            });
        }
        button("Назад",this::finish);
    }
    private void unpin(Uri uri){
        JSONArray old=entries(),next=new JSONArray();
        for(int j=0;j<old.length();j++){
            JSONObject item=old.optJSONObject(j);
            if(item!=null&&!uri.toString().equals(item.optString("uri")))next.put(item);
        }
        pins().edit().putString("items",next.toString()).apply();
        // Do not release URI permissions here: another app feature may use the same file.
    }
    private void pin(Uri uri,String name)throws JSONException{
        JSONArray list=entries();
        for(int j=0;j<list.length();j++){
            JSONObject item=list.optJSONObject(j);
            if(item!=null&&uri.toString().equals(item.optString("uri")))return;
        }
        JSONObject item=new JSONObject();item.put("uri",uri.toString());item.put("name",name);
        list.put(item);pins().edit().putString("items",list.toString()).apply();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();String name="Архив";
        try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }catch(Exception ignored){}
        if(name==null)name="Архив";
        try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pin(uri,name);library();ask(uri,name);
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Android не предоставил постоянный доступ. Выбери локальный файл через системный проводник.").setPositiveButton("OK",null).show();}
    }
    private void ask(Uri uri,String name){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSaveEnabled(false);field.setHint("Пароль архива");
        prompt=new AlertDialog.Builder(this).setTitle(name).setView(field)
            .setNegativeButton("Отмена",(d,w)->field.getText().clear())
            .setPositiveButton("Открыть",(d,w)->{
                char[] password=new char[field.length()];field.getText().getChars(0,field.length(),password,0);
                field.getText().clear();open(uri,name,password);
            }).create();
        prompt.setOnDismissListener(d->field.getText().clear());prompt.show();
    }
    private void open(Uri uri,String name,char[] password){
        lock();pendingPassword=password;final int token=++generation;
        body.removeAllViews();title(name);label("Открываю каталог и читаю сообщения без распаковки медиа…");
        button("Отмена",()->{lock();library();});
        task=reader.submit(()->{
            ProtectedArchiveSession opened=null;
            try{
                String format=ArchiveFileImport.detectFormat(this,uri,name);
                opened=ProtectedArchiveSession.open(this,uri,format,password);
                List<ProtectedArchiveSession.Entry> parts=new ArrayList<>();
                String selectedRoot=null;
                for(ProtectedArchiveSession.Entry e:opened.list()){
                    String base=e.name.substring(e.name.lastIndexOf('/')+1);
                    if(!base.matches("(?i)^messages\\d*\\.html$"))continue;
                    String parent=e.name.substring(0,e.name.length()-base.length());
                    if(selectedRoot==null)selectedRoot=parent;
                    else if(!selectedRoot.equals(parent))throw new IOException("В архиве несколько чатов");
                    parts.add(e);
                }
                if(parts.isEmpty())throw new IOException("Нет HTML Telegram");
                parts.sort(Comparator.comparingInt(e->DocumentFolder.partNo(e.name.substring(e.name.lastIndexOf('/')+1))));
                ArrayList<Line> messages=new ArrayList<>();long characters=0;String previousAuthor="";
                for(ProtectedArchiveSession.Entry part:parts){
                    if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                    byte[] html=opened.read(part.name,HTML_LIMIT);
                    try(TelegramHtmlStream stream=new TelegramHtmlStream(new ByteArrayInputStream(html))){
                        Element msg;
                        while((msg=stream.next())!=null){
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
                            Element from=msg.selectFirst(".body .from_name"),date=msg.selectFirst(".date.details"),text=msg.selectFirst(".body .text");
                            boolean service=msg.hasClass("service");
                            previousAuthor=TelegramAuthorPolicy.nextAuthor(previousAuthor,from==null?null:from.text(),service);
                            String value=(previousAuthor.isEmpty()?"":previousAuthor+"\n")+
                                (date==null?"":date.hasAttr("title")?date.attr("title")+"\n":date.text()+"\n")+
                                (text==null?"":text.wholeText());
                            List<TelegramImporter.Media> media=TelegramImporter.findMedia(msg);
                            if(text==null&&media.isEmpty()&&service){
                                Element details=msg.selectFirst(".body .details, .body");
                                if(details!=null)value+=details.text();
                            }
                            if(!media.isEmpty())value+="\nВложения: "+media.size()+" · нажми для просмотра";
                            if(value.trim().isEmpty())continue;
                            characters+=value.length();
                            for(TelegramImporter.Media m:media)characters+=m.path.length();
                            if(messages.size()>=100000||characters>16L*1024*1024)
                                throw new IOException("Чат слишком большой для текущего защищённого режима");
                            messages.add(new Line(value,media));
                        }
                    }finally{Arrays.fill(html,(byte)0);}
                }
                if(messages.isEmpty())throw new IOException("Сообщения не найдены");
                final ProtectedArchiveSession ready=opened;final String prefix=selectedRoot;
                opened=null;
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed()){ready.close();return;}
                    session=ready;lines=messages;root=prefix;showChat(name);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    library();
                    new AlertDialog.Builder(this).setTitle("Не удалось открыть")
                        .setMessage("Проверь пароль, формат и свободное место. Эта версия поддерживает один HTML-чат; некоторые архивы могут не поддерживаться.")
                        .setNegativeButton("Закрыть",null).setPositiveButton("Повторить",(d,w)->ask(uri,name)).show();
                });
            }finally{Arrays.fill(password,'\0');if(opened!=null)opened.close();}
        });
    }
    private void showChat(String name){
        body.removeAllViews();title(name);
        label(lines.size()+" сообщений · пароль только в памяти");
        button("Заблокировать",()->{lock();library();});
        ListView list=new ListView(this);
        list.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,lines));
        list.setOnItemClickListener((a,v,position,id)->{
            Line line=lines.get(position);if(line.media.isEmpty())return;
            String[] names=new String[line.media.size()];
            for(int j=0;j<names.length;j++)names[j]=line.media.get(j).path;
            prompt=new AlertDialog.Builder(this).setItems(names,(d,w)->preview(line.media.get(w))).create();prompt.show();
        });
        body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void preview(TelegramImporter.Media media){
        if(session==null)return;
        if(!"image".equals(media.kind)){
            boolean video="video".equals(media.kind)||"round".equals(media.kind)||"gif".equals(media.kind)&&media.path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|m4v)([?#].*)?");
            if(video||"audio".equals(media.kind)||"voice".equals(media.kind)){playMedia(media,video);return;}
            Toast.makeText(this,"Этот тип вложения пока не поддерживается в защищённом режиме",Toast.LENGTH_SHORT).show();return;
        }
        cancelPreview();final ProtectedArchiveSession current=session;final int token=generation,previewToken=previewGeneration;
        String raw=media.path.split("[?#]",2)[0];
        String path=root+Uri.decode(raw);
        task=reader.submit(()->{
            byte[] data=null;
            try{
                data=current.read(path,IMAGE_LIMIT);
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,opts);
                if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Некорректное фото");
                opts.inSampleSize=1;
                while(Math.max(opts.outWidth,opts.outHeight)/opts.inSampleSize>2048)opts.inSampleSize*=2;
                opts.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);
                if(bitmap==null)throw new IOException("Некорректное фото");
                runOnUiThread(()->{
                    if(token!=generation||previewToken!=previewGeneration||isFinishing()||isDestroyed()){bitmap.recycle();return;}
                    ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);
                    prompt=new AlertDialog.Builder(this).setView(image).setPositiveButton("Закрыть",null).create();
                    prompt.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});prompt.show();
                });
            }catch(Exception e){runOnUiThread(()->{if(token==generation&&previewToken==previewGeneration)Toast.makeText(this,"Фото не прочитано",Toast.LENGTH_SHORT).show();});}
            finally{if(data!=null)Arrays.fill(data,(byte)0);}
        });
    }
    private static void closeMediaSource(android.media.MediaDataSource source){try{source.close();}catch(IOException ignored){}}
    private void cancelPreview(){
        previewGeneration++;
        if(mediaPlayer!=null){mediaPlayer.close();mediaPlayer=null;}
        if(task!=null){task.cancel(true);task=null;}
    }
    private void playMedia(TelegramImporter.Media media,boolean video){
        cancelPreview();
        final ProtectedArchiveSession current=session;final int token=generation,previewToken=previewGeneration;
        final String path=root+Uri.decode(media.path.split("[?#]",2)[0]);
        Runtime runtime=Runtime.getRuntime();
        long headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());
        final long limit=Math.min(64L*1024*1024,Math.max(0,headroom/3));

        Toast.makeText(this,"Читаю выбранное вложение…",Toast.LENGTH_SHORT).show();
        task=reader.submit(()->{
            android.media.MediaDataSource source=null;
            try{
                long size=-1;
                String wanted=path.replace('\\','/');
                for(ProtectedArchiveSession.Entry e:current.list())if(e.name.equals(wanted)){size=e.size;break;}
                if(size<0)throw new FileNotFoundException("Вложение отсутствует");
                if(size<=limit&&limit>0)source=ProtectedMediaSource.load(current,path,limit);
                else{
                    long free=ArchiveFileImport.maxTemporaryCopyForFreeBytes(getCacheDir().getUsableSpace());
                    long diskLimit=Math.min(8L*1024*1024*1024,free-free/1024);
                    if(size>diskLimit||diskLimit<=0)throw new IOException("Недостаточно места для зашифрованного кеша");
                    source=ProtectedEncryptedMediaSource.load(this,current,path,diskLimit);
                }
                final android.media.MediaDataSource ready=source;
                source=null;
                runOnUiThread(()->{
                    if(token!=generation||previewToken!=previewGeneration||isFinishing()||isDestroyed()){ready.close();return;}
                    try{mediaPlayer=new ProtectedMediaPlayer(this,ready,video);}
                    catch(RuntimeException e){closeMediaSource(ready);Toast.makeText(this,"Плеер не открылся",Toast.LENGTH_SHORT).show();}
                });
            }catch(Exception|OutOfMemoryError e){
                runOnUiThread(()->{if(token==generation&&previewToken==previewGeneration)
                    Toast.makeText(this,"Вложение не прочитано: проверь формат и свободное место",Toast.LENGTH_LONG).show();});
            }finally{if(source!=null)closeMediaSource(source);}
        });
    }
    private void lock(){
        generation++;cancelPreview();
        if(prompt!=null){prompt.dismiss();prompt=null;}
        if(task!=null){task.cancel(true);task=null;}
        if(pendingPassword!=null){Arrays.fill(pendingPassword,'\0');pendingPassword=null;}
        ProtectedArchiveSession old=session;session=null;
        if(old!=null)reader.execute(old::close);
        if(lines!=null){lines.clear();lines=null;}
        root="";body.removeAllViews();
    }
    @Override protected void onStop(){lock();library();super.onStop();}
    @Override protected void onDestroy(){lock();reader.shutdown();super.onDestroy();}
}
