package org.openarchive.telegram;

import java.io.IOException;

/** Low-allocation ZIP path validation, including directory entries. */
final class BackupEntryPolicy {
    static final int MAX_ENTRIES=260_000;
    private BackupEntryPolicy() {}
    static void requireSafe(String name, boolean directory) throws IOException {
        if(name==null || name.isEmpty() || name.length()>1500 || name.startsWith("/") ||
           name.indexOf('\\')>=0 || name.indexOf('\0')>=0 || name.indexOf(':')>=0)
            throw new IOException("Опасный путь в ZIP");
        String[] components=name.split("/",-1);
        for(int i=0;i<components.length;i++) {
            String part=components[i];
            if(part.equals(".") || part.equals("..") || (part.isEmpty() && i!=components.length-1))
                throw new IOException("Опасный компонент пути ZIP");
        }
        if(directory && !name.endsWith("/"))throw new IOException("Некорректный каталог ZIP");
    }
    static void requireFullBackupPath(String name, boolean directory)throws IOException {
        requireSafe(name,directory);
        if(directory){
            if(!(name.equals("media/") || name.equals("custom/") || name.equals("note_files/") ||
                 name.matches("media/chat-[0-9]{1,4}/(?:[^/]+/)*")))
                throw new IOException("Неожиданный каталог ZIP");
        }else if(!name.equals("metadata.zip") && !name.equals("full-manifest.json") &&
                  !name.matches("media/chat-[0-9]{1,4}/.+") &&
                  !name.matches("custom/[a-zA-Z0-9_-]{1,96}\\.jpg") && !name.matches("note_files/[1-9][0-9]{0,17}")){
            throw new IOException("Недопустимый элемент резервной копии");
        }
    }
}
