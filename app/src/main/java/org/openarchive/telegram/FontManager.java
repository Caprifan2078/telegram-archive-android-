package org.openarchive.telegram;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
/** All font data stays in app-private storage. No storage permission or server needed. */
final class FontManager {
    private static final String PREFS="ta-typography",CHOICE="font-choice";
    static final String[] LABELS={"Как на устройстве · по умолчанию","Sans · современный",
        "Serif · классический","Monospace · печатный","Condensed · компактный",
        "Casual · рукописный","Sans Light · тонкий","Sans Medium · выразительный","Мой импортированный шрифт"};
    private FontManager(){}
    static String choice(Context c){
        String key=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(CHOICE,"system");
        return FontPolicy.known(key)?key:"system";
    }
    static int choiceIndex(Context c){
        String s=choice(c);
        for(int i=0;i<FontPolicy.KEYS.length;i++)if(FontPolicy.KEYS[i].equals(s))return i;
        return 0;
    }
    static void select(Context c,String key){
        if(!FontPolicy.known(key))throw new IllegalArgumentException("Unknown font option");
        if("custom".equals(key)&&!customFile(c).isFile())throw new IllegalStateException("Сначала импортируй шрифт");
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(CHOICE,key).apply();
    }
    static File customFile(Context c){return new File(new File(c.getFilesDir(),"typography"),"user-font.otf");}
    static Typeface resolve(Context c){
        switch(choice(c)){
            case "sans":return Typeface.SANS_SERIF;
            case "serif":return Typeface.SERIF;
            case "mono":return Typeface.MONOSPACE;
            case "condensed":return Typeface.create("sans-serif-condensed",Typeface.NORMAL);
            case "casual":return Typeface.create("casual",Typeface.NORMAL);
            case "light":return Typeface.create("sans-serif-light",Typeface.NORMAL);
            case "medium":return Typeface.create("sans-serif-medium",Typeface.NORMAL);
            case "custom":try{return Typeface.createFromFile(customFile(c));}catch(Exception ignored){return Typeface.DEFAULT;}
            default:return Typeface.DEFAULT; // Enforce the device UI default, including explicitly styled legacy buttons.
        }
    }
    static void importFile(Context c,Uri source) throws IOException {
        if(source==null)throw new IOException("Файл не выбран");
        String name="";
        try(Cursor cur=c.getContentResolver().query(source,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cur!=null&&cur.moveToFirst())name=cur.getString(0);
        }catch(SecurityException e){throw new IOException("Нет доступа к файлу",e);}
        if(!FontPolicy.acceptedName(name))throw new IOException("Выбери шрифт .ttf или .otf");
        File folder=new File(c.getFilesDir(),"typography");
        if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Не удалось создать каталог шрифтов");
        File temp=new File(folder,"incoming-font.tmp");
        try{
            byte[] buf=new byte[8192];long total=0;int count;
            byte[] header=new byte[4];int seen=0;
            try(InputStream in=c.getContentResolver().openInputStream(source);
                FileOutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Файл недоступен");
                while((count=in.read(buf))!=-1){
                    if(count==0)continue;
                    if(total+count>FontPolicy.MAX_BYTES)throw new IOException("Шрифт больше 5 МБ");
                    for(int i=0;i<count&&seen<4;i++)header[seen++]=buf[i];
                    out.write(buf,0,count);total+=count;
                }
                out.getFD().sync();
            }
            if(!FontPolicy.withinLimit(total)||!FontPolicy.validHeader(header,seen))
                throw new IOException("Некорректный TTF/OTF-файл");
            try{Typeface.createFromFile(temp);}catch(RuntimeException bad){throw new IOException("Android не распознал шрифт",bad);}
            File target=customFile(c);
            try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(java.nio.file.AtomicMoveNotSupportedException ex){
                Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            select(c,"custom");
        }finally{if(temp.exists())temp.delete();}
    }
}
