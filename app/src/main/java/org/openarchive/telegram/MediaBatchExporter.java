package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Batch copies, never moves. MediaStore on Q+; a user-selected SAF folder on O/P. */
final class MediaBatchExporter {
    interface Progress { void update(int completed,int total,int failed); }
    static final class Entry { final String path,kind; Entry(String path,String kind){this.path=path;this.kind=kind;} }
    static final class Report { int saved,failed;long bytes; }
    static Report export(Context ctx,DocumentFolder source,List<Entry> items,boolean downloads,
                         Uri safTree,AtomicBoolean cancel,Progress update){
        Report result=new Report();
        final int total=items.size();
        for(Entry item:items){
            if(cancel.get()||Thread.currentThread().isInterrupted())break;
            try {
                Uri file=source.resolve(item.path);
                if(file==null)throw new IOException("Файл отсутствует");
                MediaSavePolicy.Spec spec=MediaSavePolicy.plan(item.path,item.kind,downloads);
                if(Build.VERSION.SDK_INT>=29){
                    result.bytes+=MediaSaver.saveToLibrary(ctx,file,spec).bytes;
                }else{
                    if(safTree==null)throw new IOException("Выбери папку сохранения");
                    Uri folder=DocumentsContract.buildDocumentUriUsingTree(safTree,DocumentsContract.getTreeDocumentId(safTree));
                    Uri destination=DocumentsContract.createDocument(ctx.getContentResolver(),folder,spec.mime,spec.filename);
                    if(destination==null)throw new IOException("Нельзя создать файл");
                    boolean done=false;
                    try{
                        long size=MediaSaver.copy(ctx,file,destination);
                        if(size==0)throw new IOException("Пустой файл");
                        result.bytes+=size;done=true;
                    }finally{if(!done)MediaSaver.discardIncompleteDocument(ctx,destination);}
                }
                result.saved++;
            }catch(Exception e){result.failed++;}
            if(update!=null)update.update(result.saved+result.failed,total,result.failed);
        }
        return result;
    }
}
