package org.openarchive.telegram;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/** Streaming user-initiated copy. Never modifies the Telegram export source. */
final class MediaSaver {
    static final class Result {
        final Uri uri;
        final long bytes;
        Result(Uri uri,long bytes){this.uri=uri;this.bytes=bytes;}
    }
    private MediaSaver(){ }
    static Result saveToLibrary(Context context, Uri source, MediaSavePolicy.Spec spec) throws IOException {
        if(Build.VERSION.SDK_INT<29)throw new IOException("На Android 8–9 выберите папку через системное окно");
        ContentResolver resolver=context.getContentResolver();
        Uri collection;
        String relative;
        switch(spec.destination) {
            case PICTURES:
                collection=MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                relative=Environment.DIRECTORY_PICTURES+"/Telegram Archive";break;
            case MOVIES:
                collection=MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                relative=Environment.DIRECTORY_MOVIES+"/Telegram Archive";break;
            case MUSIC:
                collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                relative=Environment.DIRECTORY_MUSIC+"/Telegram Archive";break;
            default:
                collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                relative=Environment.DIRECTORY_DOWNLOADS+"/Telegram Archive";break;
        }
        ContentValues values=new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,"TA_"+System.currentTimeMillis()+"_"+spec.filename);
        values.put(MediaStore.MediaColumns.MIME_TYPE,spec.mime);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,relative);
        values.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri destination=resolver.insert(collection,values);
        if(destination==null)throw new IOException("Android не смог создать файл в медиатеке");
        boolean finished=false;
        try {
            long bytes=copy(context,source,destination);
            if(bytes==0)throw new IOException("Исходный файл пуст");
            ContentValues publish=new ContentValues();publish.put(MediaStore.MediaColumns.IS_PENDING,0);
            if(resolver.update(destination,publish,null,null)!=1)throw new IOException("Android не подтвердил сохранение файла");
            finished=true;
            return new Result(destination,bytes);
        } finally {
            if(!finished)try{resolver.delete(destination,null,null);}catch(Exception ignored){}
        }
    }
    static long copy(Context context,Uri source,Uri destination) throws IOException {
        if(source==null||destination==null)throw new IOException("Не выбран файл");
        ContentResolver resolver=context.getContentResolver();
        try(InputStream input=resolver.openInputStream(source);
            OutputStream output=resolver.openOutputStream(destination,"w")) {
            if(input==null||output==null)throw new IOException("Нет доступа к файлу");
            byte[] buffer=new byte[128*1024];long count=0;
            for(int n;(n=input.read(buffer))!=-1;){
                if(n==0)continue;
                count+=n;
                output.write(buffer,0,n);
            }
            output.flush();return count;
        }
    }
    static void discardIncompleteDocument(Context context, Uri uri){
        if(uri!=null)try{DocumentsContract.deleteDocument(context.getContentResolver(),uri);}catch(Exception ignored){}
    }
}
