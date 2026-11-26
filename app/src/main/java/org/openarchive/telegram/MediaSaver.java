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
    /** Separate from write failures: callers must never delete an aliased original. */
    static final class SameSourceException extends IOException {
        SameSourceException(){super("Нельзя сохранять поверх исходного файла");}
    }
    static void requireDistinct(Context context,Uri source,Uri destination)throws IOException{
        if(source==null||destination==null)throw new IOException("Не выбран файл");
        if(source.equals(destination))throw new SameSourceException();
        if("file".equals(source.getScheme())&&"file".equals(destination.getScheme())){
            java.io.File a=new java.io.File(source.getPath()).getCanonicalFile();
            java.io.File b=new java.io.File(destination.getPath()).getCanonicalFile();
            if(a.equals(b)||(a.exists()&&b.exists()&&java.nio.file.Files.isSameFile(a.toPath(),b.toPath())))
                throw new SameSourceException();
        }
        if("content".equals(source.getScheme())&&"content".equals(destination.getScheme())&&
            java.util.Objects.equals(source.getAuthority(),destination.getAuthority())){
            try{
                if(DocumentsContract.isDocumentUri(context,source)&&DocumentsContract.isDocumentUri(context,destination)&&
                    DocumentsContract.getDocumentId(source).equals(DocumentsContract.getDocumentId(destination)))
                    throw new SameSourceException();
            }catch(IllegalArgumentException ignored){/* not a document URI */}
        }
    }
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
        } catch(SameSourceException alias) {
            finished=true; // Do not remove the original on an unexpected alias.
            throw alias;
        } finally {
            if(!finished)try{resolver.delete(destination,null,null);}catch(Exception ignored){}
        }
    }
    static long copy(Context context,Uri source,Uri destination) throws IOException {
        requireDistinct(context,source,destination);
        ContentResolver resolver=context.getContentResolver();
        try(InputStream input=resolver.openInputStream(source)) {
            if(input==null)throw new IOException("Нет доступа к исходному файлу");
            try(OutputStream output=resolver.openOutputStream(destination,"w")){
                if(output==null)throw new IOException("Нет доступа к файлу назначения");
                byte[] buffer=new byte[128*1024];long count=0;
                for(int n;(n=input.read(buffer))!=-1;){
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Сохранение отменено");
                    if(n==0)continue;
                    output.write(buffer,0,n);count+=n;
                }
                output.flush();return count;
            }
        }
    }
    static void discardIncompleteDocument(Context context, Uri uri){
        if(uri!=null)try{DocumentsContract.deleteDocument(context.getContentResolver(),uri);}catch(Exception ignored){}
    }
}
