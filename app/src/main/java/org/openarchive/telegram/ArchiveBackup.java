package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Offline portable backup of indexed chat text, media references, favorites, pins and notes.
 * Deliberately does not copy the potentially enormous Telegram media folders. */
final class ArchiveBackup {
    interface Progress {void update(String message);}
    static final int VERSION=1;
    static final long MAX_DB_BYTES=2L*1024*1024*1024; // refuse malformed or huge ZIP expansion
    static final long MAX_STAGED_METADATA_BYTES=24L*1024*1024*1024;
    private static String hex(byte[] data){
        StringBuilder out=new StringBuilder();for(byte b:data)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static MessageDigest digest() throws Exception{return MessageDigest.getInstance("SHA-256");}
    private static long copy(InputStream input,OutputStream output,MessageDigest sha,long limit) throws IOException{
        byte[] buf=new byte[64*1024];long size=0;int n;
        while((n=input.read(buf))!=-1){
            size+=n;if(size>limit)throw new IOException("Превышен допустимый размер данных в резервной копии");
            output.write(buf,0,n);if(sha!=null)sha.update(buf,0,n);
        }return size;
    }
    private static JSONObject exportedDb(ZipOutputStream zip,File file,String entryName) throws Exception{
        if(!file.isFile())throw new IOException("Не найдена локальная база: "+file.getName());
        MessageDigest sha=digest();zip.putNextEntry(new ZipEntry(entryName));
        long len;try(InputStream input=new BufferedInputStream(new FileInputStream(file))){
            len=copy(input,zip,sha,MAX_DB_BYTES);
        }
        zip.closeEntry();
        JSONObject o=new JSONObject();o.put("file",entryName);o.put("sha256",hex(sha.digest()));o.put("size",len);return o;
    }
    private static void checkpoint(SQLiteDatabase db) throws Exception{
        // Export only committed pages from the main .db file; do not silently omit WAL.
        try(Cursor c=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){
            if(c.moveToFirst()&&c.getInt(0)!=0)throw new IOException("База занята, повтори резервное копирование");
        }
    }
    static void write(Context ctx,SharedPreferences prefs,Uri destination,Progress progress) throws Exception {
        try(OutputStream out=ctx.getContentResolver().openOutputStream(destination,"w")){
            if(out==null)throw new IOException("Невозможно открыть место сохранения");
            write(ctx,prefs,out,progress);
        }
    }
    static void write(Context ctx,SharedPreferences prefs,OutputStream output,Progress progress) throws Exception {
        JSONArray chats=new JSONArray(prefs.getString("archive_registry","[]"));
        ArrayList<String> backupChatKeys=new ArrayList<>();
        for(int i=0;i<chats.length();i++)backupChatKeys.add(chats.getJSONObject(i).optString("key","default"));
        ArchiveChatKeyPolicy.requireUnique(backupChatKeys);
        JSONObject manifest=new JSONObject();
        manifest.put("format","telegram-archive-metadata");manifest.put("version",VERSION);
        manifest.put("created",System.currentTimeMillis());manifest.put("mediaIncluded",false);
        JSONArray records=new JSONArray();
        JSONObject settings=new JSONObject();
        for(Map.Entry<String,?> item:prefs.getAll().entrySet()){
            Object v=item.getValue();
            if(v instanceof String||v instanceof Number||v instanceof Boolean)settings.put(item.getKey(),v);
        }
        manifest.put("settings",settings);
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
                for(int i=0;i<chats.length();i++){
                    JSONObject chat=chats.getJSONObject(i);
                    String key=chat.optString("key","default"),name=chat.optString("name","Переписка");
                    if(!key.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Некорректный ID переписки");
                    progress.update("Сохраняю "+(i+1)+"/"+chats.length()+": "+name);
                    MessageDb helper=new MessageDb(ctx,key);
                    File dbFile=ctx.getDatabasePath("default".equals(key)?"telegram_archive.db":"telegram_"+key+".db");
                    try{checkpoint(helper.getWritableDatabase());}finally{helper.close();}
                    JSONObject entry=exportedDb(zip,dbFile,"db/chat-"+i+".sqlite");
                    entry.put("key",key);entry.put("name",name);
                    records.put(entry);
                }
                progress.update("Сохраняю избранное и личные заметки");
                SavedStore notes=new SavedStore(ctx);
                try{checkpoint(notes.getWritableDatabase());}finally{notes.close();}
                manifest.put("chats",records);
                manifest.put("notes",exportedDb(zip,ctx.getDatabasePath("global_saved_notes.db"),"db/notes.sqlite"));
                zip.putNextEntry(new ZipEntry("manifest.json"));
                byte[] bytes=manifest.toString().getBytes("UTF-8");
                zip.write(bytes);zip.closeEntry();
        }
    }
    private static File stage(Context ctx,String journalId,ZipInputStream zip,ZipEntry entry,Map<String,File> files,Map<String,String> hashes) throws Exception{
        if(entry.isDirectory()||!entry.getName().matches("db/(?:chat-[0-9]{1,5}|notes)\\.sqlite"))throw new IOException("Недопустимый файл в архиве");
        if(files.containsKey(entry.getName()))throw new IOException("Повторяющийся элемент ZIP");
        File temp=File.createTempFile("tgrestore-"+journalId+"-", ".sqlite",ctx.getCacheDir());
        MessageDigest sha=digest();
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
            copy(zip,out,sha,MAX_DB_BYTES);
        }catch(Exception e){temp.delete();throw e;}
        files.put(entry.getName(),temp);hashes.put(entry.getName(),hex(sha.digest()));
        return temp;
    }
    private static void verifyDb(File path,String table) throws Exception{
        SQLiteDatabase db=SQLiteDatabase.openDatabase(path.getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
        try{
            try(Cursor c=db.rawQuery("PRAGMA quick_check",null)){
                if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IOException("Повреждена база в резервной копии");
            }
            try(Cursor c=db.rawQuery("SELECT 1 FROM "+table+" LIMIT 1",null)){} // schema presence
        }finally{db.close();}
    }
    private static void verifyRecord(JSONObject rec,Map<String,File> files,Map<String,String> sha) throws Exception{
        String name=rec.getString("file");File file=files.get(name);
        if(file==null||file.length()!=rec.getLong("size"))throw new IOException("Неполные данные ZIP: "+name);
        if(!sha.get(name).equals(rec.getString("sha256")))throw new IOException("Контрольная сумма не совпадает: "+name);
    }
    private static void replaceWithCopy(File src,File dst) throws Exception{
        if(dst.exists())throw new IOException("База с таким именем уже существует");
        File parent=dst.getParentFile();if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Нет доступа к каталогу баз");
        File temp=new File(parent,dst.getName()+".restoring");
        try(InputStream in=new FileInputStream(src);FileOutputStream out=new FileOutputStream(temp)){
            copy(in,out,null,MAX_DB_BYTES);
            out.flush();
            out.getFD().sync();
        }
        if(!temp.renameTo(dst)){temp.delete();throw new IOException("Не удалось сохранить базу переписки");}
    }
    static int restore(Context ctx,SharedPreferences prefs,Uri source,Progress progress) throws Exception{
        try(InputStream in=ctx.getContentResolver().openInputStream(source)){
            if(in==null)throw new IOException("Архив недоступен");
            return restore(ctx,prefs,in,progress);
        }
    }
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress)throws Exception{
        return restore(ctx,prefs,streamed,progress,null,null,null);
    }
    /** Supplies media paths before the first registry commit, so a full restore
     * never publishes temporary empty trees and then patches them afterwards. */
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress,List<File> mediaRoots)throws Exception{
        return restore(ctx,prefs,streamed,progress,mediaRoots,null,null);
    }
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress,
                       List<File> mediaRoots,RestoreJournal callerJournal)throws Exception{
        return restore(ctx,prefs,streamed,progress,mediaRoots,callerJournal,null);
    }
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress,
                       List<File> mediaRoots,RestoreJournal callerJournal,
                       Map<Long,String> restoredNoteFiles)throws Exception{
        Map<String,File> files=new HashMap<>();Map<String,String> hashes=new HashMap<>();
        JSONObject manifest=null;ArrayList<File> created=new ArrayList<>();
        RestoreJournal journal=callerJournal==null?RestoreJournal.begin(ctx,null):callerJournal;
        try{
            {
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(streamed))){
                    ZipEntry entry;int entries=0;long stagedBytes=0;
                    while((entry=zip.getNextEntry())!=null){
                        if(++entries>3000)throw new IOException("Слишком много файлов в архиве");
                        if("manifest.json".equals(entry.getName())){
                            if(manifest!=null)throw new IOException("Повторяющийся manifest.json");
                            ByteArrayOutputStream out=new ByteArrayOutputStream();
                            copy(zip,out,null,2*1024*1024);manifest=new JSONObject(out.toString("UTF-8"));
                        }else{
                            File staged=stage(ctx,journal.id,zip,entry,files,hashes);
                            stagedBytes+=staged.length();
                            if(stagedBytes>MAX_STAGED_METADATA_BYTES)
                                throw new IOException("Метаданные превышают безопасный лимит распаковки");
                        }
                        zip.closeEntry();
                    }
                }
            }
            if(manifest==null||!"telegram-archive-metadata".equals(manifest.optString("format"))||
                    manifest.optInt("version")!=VERSION)throw new IOException("Неподдерживаемая резервная копия");
            JSONArray chats=manifest.getJSONArray("chats");
            JSONObject notes=manifest.getJSONObject("notes");verifyRecord(notes,files,hashes);
            verifyDb(files.get(notes.getString("file")),"saved_notes");
            // A full backup must preserve EVERY note attachment. Matching the
            // outer ZIP manifest alone cannot detect a legitimate note omitted
            // from note_files/; compare against the authenticated notes SQLite.
            if(restoredNoteFiles!=null){
                Set<Long> attachedNotes=new HashSet<>();
                SQLiteDatabase sourceNotes=SQLiteDatabase.openDatabase(
                    files.get(notes.getString("file")).getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
                try{
                    try(Cursor cursor=sourceNotes.rawQuery(
                        "SELECT id FROM saved_notes WHERE file_id IS NOT NULL",null)){
                        while(cursor.moveToNext())attachedNotes.add(cursor.getLong(0));
                    }
                }finally{sourceNotes.close();}
                NoteAttachmentParity.requireExact(attachedNotes,restoredNoteFiles.keySet());
            }
            if(chats.length()>1000)throw new IOException("Слишком много переписок");
            ArrayList<String> originalChatKeys=new ArrayList<>();
            for(int i=0;i<chats.length();i++)
                originalChatKeys.add(chats.getJSONObject(i).optString("key","default"));
            ArchiveChatKeyPolicy.requireUnique(originalChatKeys);
            if(mediaRoots!=null&&mediaRoots.size()!=chats.length())
                throw new IOException("Количество папок медиа не совпадает с переписками");
            for(int i=0;i<chats.length();i++){
                JSONObject record=chats.getJSONObject(i);verifyRecord(record,files,hashes);
                verifyDb(files.get(record.getString("file")),"messages");
            }
            JSONArray registered=new JSONArray(prefs.getString("archive_registry","[]"));
            JSONObject prefsSaved=manifest.optJSONObject("settings");
            SharedPreferences.Editor edit=prefs.edit();
            Map<String,String> keyMap=new HashMap<>();
            ArrayList<String> newKeys=new ArrayList<>();
            for(int i=0;i<chats.length();i++){
                String key=UUID.randomUUID().toString().replace("-","");
                File dbTarget=ctx.getDatabasePath("telegram_"+key+".db");
                if(dbTarget.exists()||new File(dbTarget.getParentFile(),dbTarget.getName()+".restoring").exists())
                    throw new IOException("Обнаружен конфликт ID восстановленной базы");
                newKeys.add(key);
            }
            journal.recordKeys(newKeys);
            for(int i=0;i<chats.length();i++){
                JSONObject rec=chats.getJSONObject(i);
                String newKey=newKeys.get(i);
                String oldKey=rec.optString("key","default");
                String name=rec.optString("name","Архив")+" (восстановлен)";
                keyMap.put(oldKey,newKey);
                File destination=ctx.getDatabasePath("telegram_"+newKey+".db");
                progress.update("Восстанавливаю "+(i+1)+"/"+chats.length()+": "+name);
                replaceWithCopy(files.get(rec.getString("file")),destination);
                created.add(destination);
                // Compare the actual SQLite bytes on storage BEFORE exposing this chat.
                CopyAudit.requireIdentical(files.get(rec.getString("file")),destination);
                JSONObject archive=new JSONObject();
                archive.put("key",newKey);archive.put("name",name);
                archive.put("tree",mediaRoots==null?"":"local:"+mediaRoots.get(i).getAbsolutePath());
                registered.put(archive);
            }
            // Notes are inserted in one transaction. If registration fails, inserted notes roll back.
            SavedStore store=new SavedStore(ctx);
            SQLiteDatabase localNotes=store.getWritableDatabase();
            localNotes.beginTransaction();
            try{
                SQLiteDatabase other=SQLiteDatabase.openDatabase(files.get(notes.getString("file")).getAbsolutePath(),null,SQLiteDatabase.OPEN_READONLY);
                try{
                boolean hasOrigin=false;
                try(Cursor col=other.rawQuery("PRAGMA table_info(saved_notes)",null)){
                    while(col.moveToNext())if("origin_archive".equals(col.getString(1)))hasOrigin=true;
                }
                int sourceNotes=0,restoredNotes=0;
                try(Cursor count=other.rawQuery("SELECT COUNT(*) FROM saved_notes",null)){
                    if(count.moveToFirst())sourceNotes=count.getInt(0);
                }
                String projection=hasOrigin?
                    "SELECT id,created,body,file_name,file_id,mime,origin_archive,origin_message FROM saved_notes ORDER BY id":
                    "SELECT id,created,body,file_name,file_id,mime,NULL AS origin_archive,NULL AS origin_message FROM saved_notes ORDER BY id";
                try(Cursor c=other.rawQuery(projection,null)){
                    while(c.moveToNext()){
                        long oldNoteId=c.getLong(0);
                        String oldOrigin=c.getString(6);
                        String newOrigin=oldOrigin==null?null:keyMap.get(oldOrigin);
                        // Never leave a misleading reference into another, unrelated chat.
                        long messageId=c.isNull(7)?-1:c.getLong(7);
                        if(newOrigin==null)messageId=-1;
                        String newFile=restoredNoteFiles==null?null:restoredNoteFiles.get(oldNoteId);
                        long noteId=store.addRestored(c.getLong(1),c.getString(2),c.getString(3),newFile,c.getString(5),newOrigin,messageId);
                        RestoreJournal.recordNote(localNotes,journal.id,noteId);
                        restoredNotes++;
                    }
                }
                if(sourceNotes!=restoredNotes)throw new IOException("Неполное восстановление заметок");
                edit.putString("last_restore_receipt", "Последняя проверка переноса:\n"+
                    "Восстановлено переписок: "+chats.length()+"\n"+
                    "Восстановлено заметок и цитат: "+restoredNotes+"\n"+
                    "SQLite базы скопированы с совпадением SHA-256.\n"+
                    "Медиа и дополнительные файлы проверяются по манифесту полной копии.\n"+
                    "Проверка воспроизведения файлов на устройстве выполняется отдельно.");
                }finally{other.close();}
            if(prefsSaved!=null){
                for(Iterator<String> it=prefsSaved.keys();it.hasNext();){
                    String key=it.next();
                    if(key.startsWith("sender_")){
                        for(Map.Entry<String,String> map:keyMap.entrySet()){
                            String prefix="sender_"+map.getKey()+"_";
                            if(key.startsWith(prefix)){edit.putInt("sender_"+map.getValue()+"_"+key.substring(prefix.length()),prefsSaved.optInt(key));break;}
                        }
                    }else if(key.startsWith("person_avatar_")||key.startsWith("chat_avatar_")||key.startsWith("wallpaper_image_")){
                        for(Map.Entry<String,String> map:keyMap.entrySet()){
                            for(String prefix:new String[]{"person_avatar_","chat_avatar_","wallpaper_image_"}){
                                String oldPrefix=prefix+map.getKey();
                                if(key.equals(oldPrefix)||key.startsWith(oldPrefix+"_")){
                                    String newKey=prefix+map.getValue()+key.substring(oldPrefix.length());
                                    edit.putString(newKey,prefsSaved.optString(key));
                                    break;
                                }
                            }
                        }
                    }else if(key.startsWith("appearance_")||key.equals("mine")){
                        // Portable global appearance and own-sender settings, never overwrite
                        // pre-existing target preferences with device-local URI paths.
                        Object value=prefsSaved.opt(key);
                        if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
                        else if(value instanceof Number)edit.putInt(key,((Number)value).intValue());
                        else if(value instanceof String)edit.putString(key,(String)value);
                    }else if(key.startsWith("pinned_chat_")){
                        String original=key.substring("pinned_chat_".length());
                        if(keyMap.containsKey(original))
                            edit.putBoolean("pinned_chat_"+keyMap.get(original),prefsSaved.optBoolean(key));
                    }else if(key.startsWith("read_id_")||key.startsWith("read_top_")){
                        String prefix=key.startsWith("read_id_")?"read_id_":"read_top_";
                        String original=key.substring(prefix.length());
                        if(keyMap.containsKey(original)){
                            if(prefix.equals("read_id_"))edit.putLong(prefix+keyMap.get(original),prefsSaved.optLong(key,-1));
                            else edit.putInt(prefix+keyMap.get(original),prefsSaved.optInt(key));
                        }
                    }else if(key.startsWith("read_offset_")){
                        String original=key.substring("read_offset_".length());
                        if(keyMap.containsKey(original))edit.putInt("read_offset_"+keyMap.get(original),prefsSaved.optInt(key));
                    }
                }
            }
                localNotes.setTransactionSuccessful();
            }finally{localNotes.endTransaction();store.close();}
            // Publish registry only after the SQLite notes transaction is safely committed.
            // Nothing that writes user databases runs after this publication step.
            edit.putString("archive_registry",registered.toString());
            if(!edit.commit())throw new IOException("Не удалось сохранить список архивов");
            // Unlink temporary DB copies before closing the durable journal.
            for(File file:files.values())file.delete();
            // Publication has succeeded. Recovery now cleans journal bookkeeping only.
            RestoreJournal.recover(ctx,prefs,journal);
            return chats.length();
        }catch(Exception e){
            // SharedPreferences.commit() can report a disk failure after writing the registry.
            // Recovery consults actual published keys instead of guessing from return values.
            if(journal!=null){
                try{RestoreJournal.recover(ctx,prefs,journal);}catch(Exception recovery){e.addSuppressed(recovery);}
            }else{
                for(File file:created)file.delete();
            }
            throw e;
        }finally{
            for(File file:files.values())file.delete();
        }
    }
}
