package org.openarchive.telegram;
import java.io.IOException;

/** Normalizes only safe archive-relative paths; backup validation remains strict. */
final class ArchiveImportPath {
    private ArchiveImportPath(){}
    static String normalize(String raw,boolean directory)throws IOException{
        if(raw==null||raw.isEmpty()||raw.length()>1500||raw.indexOf('\0')>=0||raw.indexOf(':')>=0)
            throw new IOException("Недопустимый путь внутри архива");
        String path=raw.replace('\\','/');
        if(path.startsWith("/"))throw new IOException("Абсолютный путь внутри архива");
        while(path.startsWith("./"))path=path.substring(2);
        if(directory&&(path.isEmpty()||path.equals(".")))return "";
        if(path.isEmpty())throw new IOException("Пустое имя файла в архиве");
        if(directory&&!path.endsWith("/"))path+="/";
        BackupEntryPolicy.requireSafe(path,directory);
        if(!directory&&path.endsWith("/"))throw new IOException("Некорректное имя файла");
        return path;
    }
}
