package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;

/** Only resolves files created by a successful full restore. No arbitrary filesystem paths. */
final class PortableNoteFiles {
    static final String PREFIX="archive-note:";
    private PortableNoteFiles(){}
    static String reference(File staging,long noteId)throws IOException{
        if(noteId<=0)throw new IOException("Некорректный ID заметки");
        String folder=staging.getName();
        if(!folder.matches("restore-[a-f0-9]{32}"))throw new IOException("Неизвестная папка восстановления");
        return PREFIX+folder+"/note_files/"+noteId;
    }
    static Uri openUri(Context context,String reference)throws IOException{
        if(reference==null||!reference.matches("archive-note:restore-[a-f0-9]{32}/note_files/[1-9][0-9]{0,17}"))
            throw new IOException("Неверная ссылка на вложение заметки");
        String path=reference.substring(PREFIX.length());
        File base=new File(context.getFilesDir(),"telegram_imports").getCanonicalFile();
        File source=new File(base,path).getCanonicalFile();
        if(!source.getPath().startsWith(base.getPath()+File.separator)||!source.isFile())
            throw new IOException("Вложение не найдено");
        return FileProvider.getUriForFile(context,context.getPackageName()+".files",source);
    }
}
