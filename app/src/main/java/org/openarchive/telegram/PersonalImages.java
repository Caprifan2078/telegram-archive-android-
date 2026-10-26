package org.openarchive.telegram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.net.Uri;
import android.util.LruCache;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.content.SharedPreferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Local-only user customization media; no network, no external file permissions. */
final class PersonalImages {
    private static final LruCache<String,Bitmap> memory=new LruCache<String,Bitmap>(6*1024*1024){
        @Override protected int sizeOf(String key,Bitmap b){return b.getByteCount();}
    };
    private static final LruCache<String,Bitmap> circles=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String k,Bitmap b){return b.getByteCount();}
    };
    private static final String DIR="personal_images";
    static String avatarKey(String chat,String user){
        return "person_avatar_"+chat+"_"+digest(user==null?"":user.trim().toLowerCase(java.util.Locale.ROOT));
    }
    static String chatAvatarKey(String chat){return "chat_avatar_"+chat;}
    static String wallpaperKey(String chat){return "wallpaper_image_"+chat;}
    static String digest(String value){
        try{
            byte[] result=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b=new StringBuilder();for(byte x:result)b.append(String.format(java.util.Locale.ROOT,"%02x",x));
            return b.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    static String save(Context ctx,Uri source,String filename)throws IOException{
        File dir=new File(ctx.getFilesDir(),DIR);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать папку персонализации");
        Bitmap original=null;
        try{
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inJustDecodeBounds=true;
            try(InputStream input=ctx.getContentResolver().openInputStream(source)){BitmapFactory.decodeStream(input,null,opts);}
            if(opts.outWidth<1||opts.outHeight<1)throw new IOException("Выбранный файл не является изображением");
            int max=Math.max(opts.outWidth,opts.outHeight),sample=1;
            while(max/sample>1600)sample*=2;
            opts.inJustDecodeBounds=false;opts.inSampleSize=sample;
            try(InputStream input=ctx.getContentResolver().openInputStream(source)){original=BitmapFactory.decodeStream(input,null,opts);}
            if(original==null)throw new IOException("Не удалось декодировать фото");
            Bitmap scaled=original;
            if(Math.max(original.getWidth(),original.getHeight())>1280){
                float k=1280f/Math.max(original.getWidth(),original.getHeight());
                scaled=Bitmap.createScaledBitmap(original,Math.round(original.getWidth()*k),Math.round(original.getHeight()*k),true);
            }
            String fileName=filename+".jpg";
            File dest=new File(dir,fileName),temp=new File(dir,fileName+".tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){
                if(!scaled.compress(Bitmap.CompressFormat.JPEG,85,out))throw new IOException("Ошибка сохранения JPEG");
                out.getFD().sync();
            }finally{if(scaled!=original)scaled.recycle();}
            if(dest.exists()){temp.delete();throw new IOException("Изображение с таким именем уже существует");}
            if(!temp.renameTo(dest)){temp.delete();throw new IOException("Не удалось сохранить выбранное фото");}
            synchronized(memory){memory.remove(fileName);}
            return fileName;
        }finally{if(original!=null)original.recycle();}
    }
    static Bitmap load(Context ctx,String file){
        if(file==null||!file.matches("[a-zA-Z0-9_-]{1,96}\\.jpg"))return null;
        synchronized(memory){Bitmap cached=memory.get(file);if(cached!=null&&!cached.isRecycled())return cached;}
        File source=new File(new File(ctx.getFilesDir(),DIR),file);
        if(!source.isFile())return null;
        BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
        Bitmap loaded=BitmapFactory.decodeFile(source.getAbsolutePath(),opts);
        if(loaded==null)return null;
        synchronized(memory){memory.put(file,loaded);}return loaded;
    }
    static Bitmap circularCached(Context ctx,String file,int size){
        if(file==null||size<1||size>512)return null;
        String key=file+"|"+size;
        synchronized(circles){Bitmap c=circles.get(key);if(c!=null&&!c.isRecycled())return c;}
        Bitmap original=load(ctx,file);if(original==null)return null;
        Bitmap result=circular(original,size);
        synchronized(circles){Bitmap other=circles.get(key);
            if(other!=null&&!other.isRecycled()){result.recycle();return other;}
            circles.put(key,result);return result;
        }
    }
    static Bitmap circular(Bitmap bitmap,int size){
        Bitmap image=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(image);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        float scale=Math.max((float)size/bitmap.getWidth(),(float)size/bitmap.getHeight());
        android.graphics.Matrix matrix=new android.graphics.Matrix();
        matrix.setScale(scale,scale);matrix.postTranslate((size-bitmap.getWidth()*scale)*.5f,(size-bitmap.getHeight()*scale)*.5f);
        BitmapShader shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);shader.setLocalMatrix(matrix);
        paint.setShader(shader);canvas.drawCircle(size/2f,size/2f,size/2f,paint);
        return image;
    }
    static Drawable wallpaper(Context ctx,SharedPreferences prefs,String chat,int base){
        String saved=prefs.getString(wallpaperKey(chat),"");
        Bitmap bitmap=load(ctx,saved);
        if(bitmap==null)return null;
        BitmapDrawable drawable=new BitmapDrawable(ctx.getResources(),bitmap);
        drawable.setGravity(Gravity.FILL);
        drawable.setAlpha(235);
        return new LayerDrawable(new Drawable[]{new ColorDrawable(base),drawable});
    }
}
