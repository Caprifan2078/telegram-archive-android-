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
    private static final java.util.concurrent.atomic.AtomicBoolean ORPHANS_SCANNED=new java.util.concurrent.atomic.AtomicBoolean();
    private final ExecutorService reader=Executors.newSingleThreadExecutor();
    private final ExecutorService searchWorker=Executors.newSingleThreadExecutor();
    private final android.os.Handler searchHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private Future<?> searchTask;
    private Runnable pendingSearch;
    private int searchGeneration;
    private LinearLayout body;
    private ProtectedArchiveSession session;
    private Future<?> task;
    private char[] pendingPassword;
    private ProtectedMediaPlayer mediaPlayer;
    private int previewGeneration;
    private int generation;
    private AlertDialog prompt,loadingDialog;
    private ArrayList<Line> lines;
    private String openedName="";
    private boolean showingMedia;
    private int chatPosition,chatTop;
    private ListView chatList,activeList;
    private EditText activeSearch;
    private Map<String,List<MediaRow>> mediaIndex=Collections.emptyMap();
    private int mediaCount(){return mediaIndex.getOrDefault("all",Collections.emptyList()).size();}
    private static Map<String,List<MediaRow>> buildMediaIndex(List<Line> messages)throws InterruptedIOException{
        Map<String,List<MediaRow>> index=new HashMap<>();
        List<MediaRow> all=new ArrayList<>();index.put("all",all);
        for(int i=0;i<messages.size();i++){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            for(TelegramImporter.Media attachment:messages.get(i).media){
                MediaRow row=new MediaRow(attachment,i);all.add(row);
                String key=attachment.kind;
                if(key==null||"all".equals(key))key="file";
                index.computeIfAbsent(key,k->new ArrayList<>()).add(row);
            }
        }
        return index;
    }
    private static final class MediaRow {
        final TelegramImporter.Media attachment; final int message;
        MediaRow(TelegramImporter.Media a,int m){attachment=a;message=m;}
        @Override public String toString(){String path=attachment.path;return path.substring(path.lastIndexOf('/')+1)+" · сообщение "+(message+1);}
    }
    private String root="";
    private static final class Line {
        final String text;final List<TelegramImporter.Media> media;
        Line(String text,List<TelegramImporter.Media> media){this.text=text;this.media=media;}
        @Override public String toString(){return text;}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        // After a process crash the ephemeral key is gone. Remove only our own
        // orphaned containers/ciphertext once, before any new session can open.
        if(ORPHANS_SCANNED.compareAndSet(false,true)){
            File[] orphaned=getCacheDir().listFiles((dir,name)->
                name.startsWith("protected-container-")||name.startsWith("protected-media-")&&name.endsWith(".enc"));
            if(orphaned!=null)for(File f:orphaned)if(f.isFile())f.delete();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setSaveEnabled(false);
        body.setSaveFromParentEnabled(false);
        int p=(int)(16*getResources().getDisplayMetrics().density);body.setPadding(p,p,p,p);
        setContentView(body);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(body,(v,insets)->{
            androidx.core.graphics.Insets bars=insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()|
                androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(p+bars.left,p+bars.top,p+bars.right,p+bars.bottom);
            return insets;
        });
        androidx.core.view.ViewCompat.requestApplyInsets(body);
        library();
    }
    private SharedPreferences pins(){return getSharedPreferences("protected_archive_pins",MODE_PRIVATE);}
    private JSONArray entries(){
        try{return new JSONArray(pins().getString("items","[]"));}
        catch(JSONException e){return new JSONArray();}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void title(String s){
        TextView t=new TextView(this);t.setTextSize(21);t.setText(s);
        t.setTypeface(t.getTypeface(),android.graphics.Typeface.BOLD);
        t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setContentDescription(s);t.setPadding(0,dp(4),0,dp(8));body.addView(t);
    }
    private void label(String s){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(14);t.setPadding(0,dp(2),0,dp(6));body.addView(t);
    }
    private Button actionButton(String name){
        Button b=new Button(this);b.setAllCaps(false);b.setText(name);
        b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));return b;
    }
    private void button(String title,Runnable action){
        Button b=actionButton(title);b.setOnClickListener(v->action.run());body.addView(b);
    }
    private void library(){
        body.removeAllViews();
        title("Защищённые переписки");
        label("ZIP / RAR · HTML и JSON Telegram. Сохраняется только ссылка на файл. Пароль нужен при каждом открытии.");
        button("Добавить архив",()->{
            Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(i,PICK);
        });
        ScrollView pinsScroll=new ScrollView(this);LinearLayout pinList=new LinearLayout(this);pinList.setOrientation(LinearLayout.VERTICAL);
        pinsScroll.addView(pinList);body.addView(pinsScroll,new LinearLayout.LayoutParams(-1,0,1));
        JSONArray list=entries();
        for(int j=0;j<list.length();j++){
            JSONObject item=list.optJSONObject(j);if(item==null)continue;
            String name=item.optString("name","Архив");Uri uri=Uri.parse(item.optString("uri"));
            Button b=actionButton("🔒 "+name);b.setMaxLines(2);b.setEllipsize(android.text.TextUtils.TruncateAt.END);
            b.setContentDescription("Открыть защищённую переписку "+name);pinList.addView(b);
            b.setOnClickListener(v->ask(uri,name));
            b.setOnLongClickListener(v->{
                new FontDialog.Builder(this).setTitle("Убрать из списка?")
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
        }catch(Exception e){new FontDialog.Builder(this).setMessage("Android не предоставил постоянный доступ. Выбери локальный файл через системный проводник.").setPositiveButton("OK",null).show();}
    }
    private void ask(Uri uri,String name){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setSaveEnabled(false);field.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);field.setHint("Пароль архива");
        prompt=new FontDialog.Builder(this).setTitle(name).setView(field)
            .setNegativeButton("Отмена",(d,w)->field.getText().clear())
            .setPositiveButton("Открыть",(d,w)->{
                char[] password=new char[field.length()];field.getText().getChars(0,field.length(),password,0);
                field.getText().clear();
                if(password.length==0){Toast.makeText(this,"Введи пароль защищённого архива",Toast.LENGTH_SHORT).show();return;}
                open(uri,name,password);
            }).create();
        prompt.setOnDismissListener(d->field.getText().clear());secureShow(prompt);
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
                ProtectedArchiveSession.Entry jsonPart=null;boolean multipleJson=false;
                for(ProtectedArchiveSession.Entry e:opened.list()){
                    String base=e.name.substring(e.name.lastIndexOf('/')+1);
                    if("result.json".equalsIgnoreCase(base)){
                        if(jsonPart!=null)multipleJson=true;
                        jsonPart=e;
                    }
                    if(!base.matches("(?i)^messages\\d*\\.html$"))continue;
                    String parent=e.name.substring(0,e.name.length()-base.length());
                    if(selectedRoot==null)selectedRoot=parent;
                    else if(!selectedRoot.equals(parent))throw new IOException("В архиве несколько чатов");
                    parts.add(e);
                }
                if(parts.isEmpty()&&(jsonPart==null||multipleJson))throw new IOException("Нет одиночного HTML или JSON чата Telegram");
                parts.sort(Comparator.comparingInt(e->DocumentFolder.partNo(e.name.substring(e.name.lastIndexOf('/')+1))));
                ArrayList<Line> messages=new ArrayList<>();long characters=0;String previousAuthor="";
                if(parts.isEmpty()){
                    if(!jsonPart.encrypted)throw new IOException("JSON не защищён паролем. Используй обычный импорт.");
                    selectedRoot=jsonPart.name.substring(0,jsonPart.name.lastIndexOf('/')+1);
                    byte[] json=opened.read(jsonPart.name,HTML_LIMIT);
                    try{
                        for(ProtectedJsonChat.Message m:ProtectedJsonChat.parse(new ByteArrayInputStream(json)))
                            messages.add(new Line(m.display,m.media));
                    }finally{Arrays.fill(json,(byte)0);}
                }
                for(ProtectedArchiveSession.Entry part:parts){
                    if(!part.encrypted)throw new IOException("Часть сообщений не защищена паролем. Используй обычный импорт.");
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
                final Map<String,List<MediaRow>> index=buildMediaIndex(messages);
                final ProtectedArchiveSession ready=opened;final String prefix=selectedRoot;
                opened=null;
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed()){ready.close();return;}
                    session=ready;lines=messages;root=prefix;openedName=name;
                    mediaIndex=index;
                    showChat(name);
                });
            }catch(Exception|OutOfMemoryError e){
                runOnUiThread(()->{
                    if(token!=generation||isFinishing()||isDestroyed())return;
                    lock();library();
                    prompt=new FontDialog.Builder(this).setTitle("Не удалось открыть")
                        .setMessage("Проверь пароль, формат и свободное место. Поддерживается один зашифрованный HTML или JSON чат; многотомные и некоторые RAR архивы могут не открываться.")
                        .setNegativeButton("Закрыть",null).setPositiveButton("Повторить",(d,w)->ask(uri,name)).create();
                    secureShow(prompt);
                });
            }finally{Arrays.fill(password,'\0');if(opened!=null)opened.close();}
        });
    }
    private void sessionHeader(String name,String detail,Runnable toggle,String toggleLabel){
        cancelPreview();detachPrivateViews();body.removeAllViews();title(name);label(detail);
        LinearLayout controls=new LinearLayout(this);
        Button change=actionButton(toggleLabel);
        controls.addView(change,new LinearLayout.LayoutParams(0,-2,1));change.setOnClickListener(v->toggle.run());
        Button lockButton=actionButton("🔒 Заблокировать");
        controls.addView(lockButton,new LinearLayout.LayoutParams(0,-2,1));
        lockButton.setOnClickListener(v->{lock();library();});
        body.addView(controls);
    }
    private void rememberPosition(){
        if(chatList==null)return;
        int visiblePosition=chatList.getFirstVisiblePosition();
        Object first=chatList.getItemAtPosition(visiblePosition);
        int originalPosition=lines==null?-1:lines.indexOf(first);
        chatPosition=originalPosition>=0?originalPosition:0;
        chatTop=chatList.getChildCount()==0?0:chatList.getChildAt(0).getTop();
    }
    private void configureList(ListView list){
        activeList=list;
        list.setFastScrollEnabled(true);list.setFastScrollAlwaysVisible(true);
        list.setVerticalScrollBarEnabled(true);list.setSaveEnabled(false);
        list.setSaveFromParentEnabled(false);
    }
    private void showChat(String name){
        if(session==null||lines==null)return;
        showingMedia=false;
        sessionHeader(name,lines.size()+" сообщений · защищённый сеанс",()->{
            rememberPosition();showMedia("all");
        },"Медиа · "+mediaCount());
        ListView list=new ListView(this);chatList=list;configureList(list);
        final ArrayAdapter<Line> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());
        adapter.addAll(lines);list.setAdapter(adapter);
        EditText search=new EditText(this);activeSearch=search;search.setSingleLine(true);search.setSaveEnabled(false);search.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setHint("Поиск по переписке");
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(lines==null||activeSearch!=search||activeList!=list)return;
                filterChat(s.toString(),list,adapter);
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });body.addView(search);
        list.setOnItemClickListener((a,v,position,id)->{
            Line line=adapter.getItem(position);if(line==null||line.media.isEmpty())return;
            if(line.media.size()==1){preview(line.media.get(0));return;}
            String[] names=new String[line.media.size()];
            for(int j=0;j<names.length;j++)names[j]=line.media.get(j).path;
            prompt=new FontDialog.Builder(this).setItems(names,(d,w)->preview(line.media.get(w))).create();secureShow(prompt);
        });
        body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setSelectionFromTop(Math.min(chatPosition,Math.max(0,lines.size()-1)),chatTop);
    }
    private void cancelSearch(){
        searchGeneration++;
        if(pendingSearch!=null){searchHandler.removeCallbacks(pendingSearch);pendingSearch=null;}
        if(searchTask!=null){searchTask.cancel(true);searchTask=null;}
    }
    private void filterChat(String query,ListView list,ArrayAdapter<Line> adapter){
        cancelSearch();
        if(lines==null)return;
        final int token=generation,searchToken=searchGeneration;
        final String needle=query.toLowerCase(Locale.ROOT);
        if(needle.isEmpty()){
            adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(lines);adapter.notifyDataSetChanged();
            return;
        }
        // Capture references, not a second copy of message strings. Never read
        // the mutable activity list from the background thread after locking.
        final ArrayList<Line> snapshot=new ArrayList<>(lines);
        pendingSearch=()->{
            pendingSearch=null;
            if(token!=generation||searchToken!=searchGeneration||activeList!=list)return;
            searchTask=searchWorker.submit(()->{
                ArrayList<Line> matches=new ArrayList<>();
                for(Line line:snapshot){
                    if(Thread.currentThread().isInterrupted())return;
                    if(line.text.toLowerCase(Locale.ROOT).contains(needle))matches.add(line);
                }
                runOnUiThread(()->{
                    if(token!=generation||searchToken!=searchGeneration||activeList!=list||lines==null)return;
                    adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(matches);
                    adapter.notifyDataSetChanged();
                });
            });
        };
        searchHandler.postDelayed(pendingSearch,180);
    }
    private static final String[] MEDIA_KEYS={"all","image","video","round","voice","audio","gif","sticker","file"};
    private static final String[] MEDIA_NAMES={"Все","Фото","Видео","Кружки","Голосовые","Музыка","GIF","Стикеры","Файлы"};
    private void showMedia(String category){
        if(session==null||lines==null)return;
        showingMedia=true;chatList=null;
        sessionHeader(openedName,"Медиатека · "+mediaCount()+" вложений",()->showChat(openedName),"Переписка");
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        LinearLayout categories=new LinearLayout(this);
        for(int i=0;i<MEDIA_KEYS.length;i++){
            final String key=MEDIA_KEYS[i];int count=mediaIndex.getOrDefault(key,Collections.emptyList()).size();
            Button chip=actionButton(MEDIA_NAMES[i]+" "+count);
            chip.setEnabled(!key.equals(category));chip.setOnClickListener(v->showMedia(key));categories.addView(chip);
        }
        scroll.addView(categories);body.addView(scroll);
        ArrayList<MediaRow> filtered=new ArrayList<>(mediaIndex.getOrDefault(category,Collections.emptyList()));
        if(filtered.isEmpty()){label("В этой категории нет вложений");return;}
        ListView list=new ListView(this);configureList(list);
        ArrayAdapter<MediaRow> adapter=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>(filtered));
        EditText search=new EditText(this);activeSearch=search;search.setSingleLine(true);search.setSaveEnabled(false);
        search.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        search.setHint("Поиск по имени файла");body.addView(search);
        TextView results=new TextView(this);results.setText("Файлов: "+filtered.size());body.addView(results);
        search.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                if(activeSearch==search&&activeList==list&&session!=null)
                    filterMedia(s.toString(),filtered,list,adapter,results);
            }
            @Override public void afterTextChanged(android.text.Editable e){}
        });
        list.setAdapter(adapter);
        list.setOnItemClickListener((p,v,position,id)->{MediaRow row=adapter.getItem(position);if(row!=null)preview(row.attachment);});
        list.setOnItemLongClickListener((p,v,position,id)->{
            MediaRow row=adapter.getItem(position);if(row==null)return false;
            chatPosition=row.message;chatTop=0;showChat(openedName);return true;
        });
        body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        label("Долгое нажатие — перейти к сообщению");
    }
    private void filterMedia(String query,List<MediaRow> rows,ListView list,ArrayAdapter<MediaRow> adapter,TextView result){
        cancelSearch();
        final int token=generation,searchToken=searchGeneration;
        final String needle=query.trim().toLowerCase(Locale.ROOT);
        if(needle.isEmpty()){
            adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(rows);adapter.notifyDataSetChanged();
            result.setText("Файлов: "+rows.size());return;
        }
        pendingSearch=()->{
            pendingSearch=null;
            if(token!=generation||searchToken!=searchGeneration||activeList!=list)return;
            searchTask=searchWorker.submit(()->{
                ArrayList<MediaRow> matches=new ArrayList<>();
                for(MediaRow row:rows){
                    if(Thread.currentThread().isInterrupted())return;
                    String path=row.attachment.path;
                    String name=Uri.decode(path.substring(path.lastIndexOf('/')+1));
                    if(name.toLowerCase(Locale.ROOT).contains(needle))matches.add(row);
                }
                runOnUiThread(()->{
                    if(token!=generation||searchToken!=searchGeneration||activeList!=list||session==null)return;
                    adapter.setNotifyOnChange(false);adapter.clear();adapter.addAll(matches);adapter.notifyDataSetChanged();
                    result.setText(matches.isEmpty()?"Ничего не найдено":"Найдено: "+matches.size()+" из "+rows.size());
                });
            });
        };
        searchHandler.postDelayed(pendingSearch,180);
    }
    private void secureShow(AlertDialog dialog){
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    @Override public void onBackPressed(){
        if(session!=null&&showingMedia){showChat(openedName);return;}
        if(session!=null){lock();library();return;}
        super.onBackPressed();
    }
    private void preview(TelegramImporter.Media media){
        if(session==null)return;
        String extension=media.path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        boolean picture="image".equals(media.kind)||
            ("gif".equals(media.kind)||"sticker".equals(media.kind))&&
            extension.matches(".*\\.(gif|webp|png|jpg|jpeg|bmp)");
        if(!picture){
            boolean video="video".equals(media.kind)||"round".equals(media.kind)||("gif".equals(media.kind)||"sticker".equals(media.kind))&&extension.matches(".*\\.(mp4|webm|m4v)");
            if(video||"audio".equals(media.kind)||"voice".equals(media.kind)){playMedia(media,video);return;}
            if(extension.matches(".*\\.(txt|csv|json|xml|log|md)")){previewText(media);return;}
            Toast.makeText(this,"Этот тип вложения пока не поддерживается в защищённом режиме",Toast.LENGTH_SHORT).show();return;
        }
        cancelPreview();final ProtectedArchiveSession current=session;final int token=generation,previewToken=previewGeneration;
        String raw=media.path.split("[?#]",2)[0];
        String path=mediaPath(raw);
        showLoading("Читаю фотографию…");
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
                    hideLoading();
                    ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);
                    prompt=new FontDialog.Builder(this).setView(image).setPositiveButton("Закрыть",null).create();
                    prompt.setOnDismissListener(d->{image.setImageDrawable(null);bitmap.recycle();});secureShow(prompt);
                });
            }catch(Exception|OutOfMemoryError e){runOnUiThread(()->{if(token==generation&&previewToken==previewGeneration){hideLoading();Toast.makeText(this,"Фото не прочитано",Toast.LENGTH_SHORT).show();}});}
            finally{if(data!=null)Arrays.fill(data,(byte)0);}
        });
    }
    private void previewText(TelegramImporter.Media media){
        cancelPreview();final ProtectedArchiveSession current=session;
        final int token=generation,previewToken=previewGeneration;
        final String path=mediaPath(media.path.split("[?#]",2)[0]);
        final String name=path.substring(path.lastIndexOf('/')+1);
        showLoading("Читаю текстовый файл…");
        task=reader.submit(()->{
            byte[] bytes=null;
            try{
                bytes=current.read(path,256*1024);
                final String text=ProtectedTextPreview.decode(bytes);
                runOnUiThread(()->{
                    if(token!=generation||previewToken!=previewGeneration||isFinishing()||isDestroyed())return;
                    hideLoading();
                    ScrollView scroll=new ScrollView(this);TextView content=new TextView(this);
                    content.setPadding(dp(16),dp(8),dp(16),dp(16));content.setTextSize(14);
                    content.setTypeface(android.graphics.Typeface.MONOSPACE);content.setText(text);
                    content.setTextIsSelectable(false);content.setSaveEnabled(false);scroll.addView(content);
                    prompt=new FontDialog.Builder(this).setTitle(name).setView(scroll).setPositiveButton("Закрыть",null).create();
                    prompt.setOnDismissListener(d->content.setText(""));secureShow(prompt);
                });
            }catch(Exception|OutOfMemoryError e){
                runOnUiThread(()->{if(token==generation&&previewToken==previewGeneration){
                    hideLoading();Toast.makeText(this,"Не удалось прочитать текст (лимит 256 КБ, UTF-8 / UTF-16)",Toast.LENGTH_LONG).show();
                }});
            }finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
        });
    }
    private static void closeMediaSource(android.media.MediaDataSource source){try{source.close();}catch(IOException ignored){}}
    private String mediaPath(String raw){
        String path=Uri.decode(raw).replace('\\','/');
        while(path.startsWith("./"))path=path.substring(2);
        return root+path;
    }
    private void showLoading(String message){
        hideLoading();
        loadingDialog=new FontDialog.Builder(this).setTitle(message)
            .setMessage("Читается только выбранное вложение")
            .setNegativeButton("Отмена",(d,w)->cancelPreview()).create();
        loadingDialog.setOnCancelListener(d->cancelPreview());secureShow(loadingDialog);
    }
    private void hideLoading(){
        AlertDialog old=loadingDialog;loadingDialog=null;if(old!=null)old.dismiss();
    }
    private void cancelPreview(){
        previewGeneration++;hideLoading();
        if(mediaPlayer!=null){mediaPlayer.close();mediaPlayer=null;}
        if(task!=null){task.cancel(true);task=null;}
    }
    private void playMedia(TelegramImporter.Media media,boolean video){
        cancelPreview();
        final ProtectedArchiveSession current=session;final int token=generation,previewToken=previewGeneration;
        final String path=mediaPath(media.path.split("[?#]",2)[0]);
        Runtime runtime=Runtime.getRuntime();
        long headroom=runtime.maxMemory()-(runtime.totalMemory()-runtime.freeMemory());
        final long limit=Math.min(64L*1024*1024,Math.max(0,headroom/3));

        showLoading("Читаю выбранное вложение…");
        task=reader.submit(()->{
            android.media.MediaDataSource source=null;
            try{
                long size=current.entrySize(path);
                if(size<0)throw new IOException("Некорректный размер вложения");
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
                    if(token!=generation||previewToken!=previewGeneration||isFinishing()||isDestroyed()){closeMediaSource(ready);return;}
                    hideLoading();
                    try{mediaPlayer=new ProtectedMediaPlayer(this,ready,video);}
                    catch(RuntimeException e){closeMediaSource(ready);Toast.makeText(this,"Плеер не открылся",Toast.LENGTH_SHORT).show();}
                });
            }catch(Exception|OutOfMemoryError e){
                runOnUiThread(()->{if(token==generation&&previewToken==previewGeneration){
                    hideLoading();Toast.makeText(this,"Вложение не прочитано: проверь формат и свободное место",Toast.LENGTH_LONG).show();}});
            }finally{if(source!=null)closeMediaSource(source);}
        });
    }
    private void detachPrivateViews(){
        cancelSearch();
        if(activeList!=null){
            android.widget.ListAdapter old=activeList.getAdapter();
            activeList.setOnItemClickListener(null);activeList.setOnItemLongClickListener(null);
            activeList.setAdapter(null);activeList=null;
            if(old instanceof ArrayAdapter)((ArrayAdapter<?>)old).clear();
        }
        if(activeSearch!=null){activeSearch.getText().clear();activeSearch=null;}
    }
    private void lock(){
        generation++;cancelPreview();
        if(prompt!=null){prompt.dismiss();prompt=null;}
        if(task!=null){task.cancel(true);task=null;}
        if(pendingPassword!=null){Arrays.fill(pendingPassword,'\0');pendingPassword=null;}
        ProtectedArchiveSession old=session;session=null;
        if(old!=null)reader.execute(old::close);
        if(lines!=null){lines.clear();lines=null;}
        detachPrivateViews();chatList=null;
        mediaIndex=Collections.emptyMap();showingMedia=false;openedName="";chatPosition=chatTop=0;
        root="";body.removeAllViews();
    }
    @Override protected void onStop(){lock();library();super.onStop();}
    @Override protected void onDestroy(){lock();reader.shutdown();searchWorker.shutdownNow();super.onDestroy();}
}
