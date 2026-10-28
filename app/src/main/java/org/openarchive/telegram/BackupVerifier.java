package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.util.zip.*;
import org.json.JSONObject;
import org.json.JSONArray;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import java.util.*;
import java.security.MessageDigest;

/** Read-only backup integrity check: consumes every entry/CRC without changing any chat. */
final class BackupVerifier {
    interface Progress {void onProgress(String message);}
    static final class Result {int files;long bytes;boolean full;boolean mediaHashVerified,customHashVerified;}
    static final class DryRun {
        int chats,messages,favorites,pins,notes,quotes,noteAttachments,periods,events,collections,mediaFiles;
        long bytes;
        String summary(){
            return "Переписок: "+chats+"\nСообщений: "+messages+"\nИзбранных сообщений: "+favorites+
                "\nЗакреплений: "+pins+"\nЗаметок: "+notes+"\nФайлов медиа: "+mediaFiles+
                "\nПрочитано: "+(bytes/1048576)+" МБ";
        }
    }
    /** Dry-run in app cache only: no changes to production DBs or chat registry. */
    static DryRun simulate(Context context,Uri uri,char[] password,Progress progress)throws Exception{
        java.io.File stage=new java.io.File(context.getCacheDir(),"backup-test-"+java.util.UUID.randomUUID().toString());
        if(!stage.mkdirs())throw new IOException("Не получилось создать временную область");
        try{
            DryRun report=new DryRun();
            try(InputStream source=SecureArchive.openInput(context,uri,password)){
                scanBackup(context,source,stage,report,progress);
            }
            return report;
        }finally{ArchiveFileImport.deleteTree(stage);}
    }
    private static void scanBackup(Context context,InputStream source,File stage,DryRun result,Progress progress)throws Exception {
        boolean full=false;File inner=null;JSONObject manifest=null,compactManifest=null;
        boolean permitsFull=true,permitsCompact=true,hasCompactManifest=false,hasFullManifest=false,hasNotesDb=false;
        MediaIntegrity mediaHash=new MediaIntegrity();MediaIntegrity customHash=new MediaIntegrity();MediaIntegrity noteHash=new MediaIntegrity();long mediaBytes=0;int mediaFiles=0;
        Map<String,Long> databaseLengths=new HashMap<>();
        Map<String,String> databaseDigests=new HashMap<>();
        java.util.HashSet<String> entries=new java.util.HashSet<>();
        InputStream keep=new FilterInputStream(source){@Override public void close()throws IOException{}};
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(keep,128*1024))){
            ZipEntry entry;int count=0;
            while((entry=zip.getNextEntry())!=null){
                if(++count>BackupEntryPolicy.MAX_ENTRIES)throw new IOException("Превышен лимит элементов ZIP");
                String name=entry.getName();
                BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                permitsFull &= BackupEnvelopePolicy.isFullEntry(name,entry.isDirectory());
                permitsCompact &= BackupEnvelopePolicy.isCompactEntry(name,entry.isDirectory());
                if("manifest.json".equals(name))hasCompactManifest=true;
                if("full-manifest.json".equals(name))hasFullManifest=true;
                if("db/notes.sqlite".equals(name))hasNotesDb=true;
                if(entry.isDirectory()){zip.closeEntry();continue;}
                if(!entries.add(name))throw new IOException("Повторяющийся файл");
                boolean database=name.matches("db/(?:chat-[0-9]{1,5}|notes)\\.sqlite");
                boolean innerMeta=name.equals("metadata.zip");
                boolean media=name.startsWith("media/");
                if(media){mediaHash.begin(name);mediaFiles++;}
                if(name.startsWith("note_files/"))noteHash.begin(name);
                if(name.startsWith("custom/"))customHash.begin(name);
                ByteArrayOutputStream fullManifestBytes=(name.equals("full-manifest.json")||name.equals("manifest.json"))?
                    new ByteArrayOutputStream():null;
                MessageDigest dbSha=database?MessageDigest.getInstance("SHA-256"):null;
                if(innerMeta){full=true;inner=new File(stage,"metadata.zip");}
                File extracted=database?new File(stage,name.replace('/','_')):innerMeta?inner:null;
                if(extracted!=null){
                    try(OutputStream out=new FileOutputStream(extracted)){
                        byte[] buf=new byte[65536];int n;long size=0;
                        while((n=zip.read(buf))!=-1){
                            size+=n;result.bytes+=n;
                            if(size>ArchiveBackup.MAX_DB_BYTES)throw new IOException("Временная база слишком велика");
                            if(result.bytes>90L*1024*1024*1024)throw new IOException("Архив слишком велик");
                            if(dbSha!=null)dbSha.update(buf,0,n);
                            out.write(buf,0,n);
                        }
                    }
                    if(database){
                        databaseLengths.put(name,extracted.length());
                        StringBuilder hex=new StringBuilder(64);
                        for(byte b:dbSha.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
                        databaseDigests.put(name,hex.toString());
                        checkDatabase(extracted,name,result);
                    }
                }else{
                    byte[] buf=new byte[65536];int n;
                    while((n=zip.read(buf))!=-1){
                        result.bytes+=n;
                        if(media){mediaHash.update(buf,0,n);mediaBytes+=n;}
                        if(name.startsWith("custom/"))customHash.update(buf,0,n);
                        if(name.startsWith("note_files/"))noteHash.update(buf,0,n);
                        if(fullManifestBytes!=null){if(fullManifestBytes.size()+n>2*1024*1024)throw new IOException("Манифест слишком велик");fullManifestBytes.write(buf,0,n);}
                        if(result.bytes>90L*1024*1024*1024)throw new IOException("Архив слишком велик");
                    }
                    if(media)result.mediaFiles++;
                }
                zip.closeEntry();
                if(media)mediaHash.end();
                if(name.startsWith("custom/"))customHash.end();
                if(name.startsWith("note_files/"))noteHash.end();
                if(fullManifestBytes!=null){
                    JSONObject parsed=new JSONObject(fullManifestBytes.toString("UTF-8"));
                    if(name.equals("manifest.json"))compactManifest=parsed;
                    else manifest=parsed;
                }
                if(count%250==0)progress.onProgress("Пробное восстановление: "+count+" файлов");
            }
        }
        // ZIP parsing may stop before the encrypted stream's authenticated end marker.
        // Force an EOF read so tampered/truncated .tgar trailers cannot pass dry-run.
        byte[] trailer=new byte[128*1024];while(source.read(trailer)!=-1){}
        BackupEnvelopePolicy.requireValid(full,permitsFull,permitsCompact,
            inner!=null,hasFullManifest,hasCompactManifest,hasNotesDb);
        if(!full){
            // A valid SQLite file and ZIP CRC do not prove it is the DB listed in
            // the authenticated compact manifest. Check its exact size and digest.
            if(compactManifest==null||!"telegram-archive-metadata".equals(compactManifest.optString("format"))||
                compactManifest.optInt("version")!=ArchiveBackup.VERSION)
                throw new IOException("Некорректный манифест метаданных");
            JSONArray chats=compactManifest.getJSONArray("chats");
            if(chats.length()>1000)throw new IOException("Слишком много переписок");
            Set<String> verified=new HashSet<>();
            verifyCompactDb(compactManifest.getJSONObject("notes"),"db/notes.sqlite",
                databaseLengths,databaseDigests,verified);
            for(int i=0;i<chats.length();i++)
                verifyCompactDb(chats.getJSONObject(i),"db/chat-"+i+".sqlite",
                    databaseLengths,databaseDigests,verified);
            if(verified.size()!=databaseLengths.size())
                throw new IOException("Лишний файл базы данных вне манифеста");
        }
        if(full){
            if(manifest==null||!"telegram-archive-full".equals(manifest.optString("format"))||manifest.optInt("version")!=2)throw new IOException("Некорректная полная резервная копия");
            if(manifest.getLong("files")!=mediaFiles||manifest.getLong("bytes")!=mediaBytes)throw new IOException("Неверные размеры или количество медиа");
            if(manifest.has("mediaSha256")&&!mediaHash.hex().equalsIgnoreCase(manifest.getString("mediaSha256")))throw new IOException("Не совпала контрольная сумма медиа");
            if(manifest.has("noteFiles")&&!noteHash.hex().equalsIgnoreCase(manifest.getString("noteSha256")))throw new IOException("Не совпали файлы заметок");
            if(manifest.has("customSha256")&&!customHash.hex().equalsIgnoreCase(manifest.getString("customSha256")))throw new IOException("Не совпала контрольная сумма аватаров");
            if(inner==null||!inner.isFile())throw new IOException("Отсутствует metadata.zip");
            try(InputStream nested=new FileInputStream(inner)){scanBackup(context,nested,stage,result,progress);}
        }
        if(result.chats==0)throw new IOException("Не найдены проверяемые переписки");
    }
    private static void checkDatabase(File file,String name,DryRun report)throws Exception{
        // This file is an isolated throwaway cache copy, never the original backup
        // or a user's live database. Android 15 FTS integrity_check can write to
        // its index, so read-only handles incorrectly reject valid backups.
        SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getAbsolutePath(),null,SQLiteDatabase.OPEN_READWRITE);
        try{
            try(Cursor c=db.rawQuery("PRAGMA integrity_check",null)){
                if(!c.moveToFirst()||!"ok".equalsIgnoreCase(c.getString(0)))throw new IOException("Повреждена база: "+name);
            }
            if(name.endsWith("notes.sqlite")){
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM saved_notes",null)){if(c.moveToFirst())report.notes+=c.getInt(0);}
                try(Cursor columns=db.rawQuery("PRAGMA table_info(saved_notes)",null)){
                    boolean hasOrigin=false;while(columns.moveToNext())if("origin_archive".equals(columns.getString(1)))hasOrigin=true;
                    if(hasOrigin){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM saved_notes WHERE origin_archive IS NOT NULL",null)){
                        if(c.moveToFirst())report.quotes+=c.getInt(0);
                    }}
                }
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM saved_notes WHERE file_id IS NOT NULL",null)){
                    if(c.moveToFirst())report.noteAttachments+=c.getInt(0);
                }
            }else{
                report.chats++;
                // Optional tables did not exist in the earliest backup versions.
                for(String[] tbl:new String[][]{{"conversation_periods","periods"},{"calendar_periods","periods"},
                        {"relationship_events","events"},{"media_collections","collections"}}){
                    try(Cursor exists=db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",new String[]{tbl[0]})){
                        if(exists.moveToFirst())try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+tbl[0],null)){
                            if(c.moveToFirst()){
                                int count=c.getInt(0);
                                if("periods".equals(tbl[1]))report.periods+=count;
                                else if("events".equals(tbl[1]))report.events+=count;
                                else report.collections+=count;
                            }
                        }
                    }
                }
                for(String[] pair:new String[][]{{"messages","messages"},{"favorites","favorites"},{"pins","pins"}}){
                    try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+pair[0],null)){
                        if(c.moveToFirst()){
                            int n=c.getInt(0);
                            if(pair[1].equals("messages"))report.messages+=n;
                            if(pair[1].equals("favorites"))report.favorites+=n;
                            if(pair[1].equals("pins"))report.pins+=n;
                        }
                    }
                }
            }
        }finally{db.close();}
    }
    static Result verify(Context context,Uri uri,char[] password,Progress progress)throws Exception {
        try(InputStream source=SecureArchive.openInput(context,uri,password)){
            return verifyStream(source,progress);
        }
    }
    /** Verify the content digest and exact DB inventory, not just the outer ZIP CRC. */
    private static void verifyCompactDb(JSONObject rec,String expectedName,
                                        Map<String,Long> lengths,Map<String,String> digests,
                                        Set<String> alreadyVerified)throws Exception{
        String name=rec.getString("file");
        if(!expectedName.equals(name)||!alreadyVerified.add(name)||
            !lengths.containsKey(name)||lengths.get(name).longValue()!=rec.getLong("size")||
            !digests.get(name).equalsIgnoreCase(rec.getString("sha256")))
            throw new IOException("Метаданные ZIP не совпадают с содержимым: "+expectedName);
    }
    static Result verifyStream(InputStream source,Progress progress)throws Exception{
        Result result=new Result();boolean metadata=false,manifest=false;
        InputStream keep=new FilterInputStream(source){@Override public void close()throws IOException{}};
        MediaIntegrity mediaHash=new MediaIntegrity();
        MediaIntegrity customHash=new MediaIntegrity();
        MediaIntegrity noteHash=new MediaIntegrity();
        long mediaBytes=0;int mediaFiles=0;JSONObject fullManifest=null;
        HashSet<String> names=new HashSet<>();
        boolean permitsFull=true,permitsCompact=true;
        boolean hasCompactManifest=false,hasNotesDb=false;
        JSONObject compactManifest=null;
        Map<String,String> databaseDigests=new HashMap<>();
        Map<String,Long> databaseLengths=new HashMap<>();
        int maxSeenMediaChat=-1;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(keep,128*1024))){
            ZipEntry entry;byte[] buf=new byte[128*1024];
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName();
                if(++result.files>260000)throw new IOException("Слишком много элементов");
                BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                permitsFull &= BackupEnvelopePolicy.isFullEntry(name,entry.isDirectory());
                permitsCompact &= BackupEnvelopePolicy.isCompactEntry(name,entry.isDirectory());
                if(entry.isDirectory()){zip.closeEntry();continue;}
                if(name==null||name.startsWith("/")||name.contains("\\")||name.contains("\0"))throw new IOException("Недопустимый путь");
                if(!names.add(name))throw new IOException("Повторяющийся файл ZIP");
                for(String part:name.split("/"))if(part.equals("..")||part.equals("."))throw new IOException("Недопустимый путь");
                if("metadata.zip".equals(name))metadata=true;
                if("manifest.json".equals(name))hasCompactManifest=true;
                if("db/notes.sqlite".equals(name))hasNotesDb=true;
                if("full-manifest.json".equals(name)){manifest=true;result.full=true;}
                if(name.startsWith("media/")){
                    maxSeenMediaChat=Math.max(maxSeenMediaChat,FullBackupLayout.mediaChatIndex(name));
                    mediaHash.begin(name);mediaFiles++;
                }
                if(name.startsWith("note_files/"))noteHash.begin(name);
                if(name.startsWith("custom/"))customHash.begin(name);
                ByteArrayOutputStream manifestBytes=("full-manifest.json".equals(name)||
                    "manifest.json".equals(name))?new ByteArrayOutputStream():null;
                MessageDigest dbSha=name.matches("db/(?:chat-[0-9]{1,5}|notes)\\.sqlite")?
                    MessageDigest.getInstance("SHA-256"):null;
                long size=0;int n;
                while((n=zip.read(buf))!=-1){
                    size+=n;result.bytes+=n;
                    if(name.startsWith("media/")){mediaHash.update(buf,0,n);mediaBytes+=n;}
                    if(name.startsWith("custom/"))customHash.update(buf,0,n);
                    if(name.startsWith("note_files/"))noteHash.update(buf,0,n);
                    if(dbSha!=null)dbSha.update(buf,0,n);
                    if(manifestBytes!=null){if(size>2L*1024*1024)throw new IOException("Слишком большой манифест");manifestBytes.write(buf,0,n);}
                    if(result.bytes>90L*1024*1024*1024)throw new IOException("Архив слишком велик");
                }
                zip.closeEntry(); // validates ZIP CRC
                if(name.startsWith("media/"))mediaHash.end();
                if(name.startsWith("custom/"))customHash.end();
                if(name.startsWith("note_files/"))noteHash.end();
                if(dbSha!=null){
                    StringBuilder shaHex=new StringBuilder(64);
                    for(byte b:dbSha.digest())shaHex.append(String.format(Locale.ROOT,"%02x",b&255));
                    databaseDigests.put(name,shaHex.toString());databaseLengths.put(name,size);
                }
                if(manifestBytes!=null){
                    JSONObject parsed=new JSONObject(manifestBytes.toString("UTF-8"));
                    if("manifest.json".equals(name))compactManifest=parsed;
                    else fullManifest=parsed;
                }
                if(result.files%200==0)progress.onProgress("Проверяю: "+result.files+" файлов");
            }
        }
        byte[] remaining=new byte[128*1024];while(source.read(remaining)!=-1){}
        if(result.files==0)throw new IOException("Пустой ZIP");
        BackupEnvelopePolicy.requireValid(result.full,permitsFull,permitsCompact,
            metadata,manifest,hasCompactManifest,hasNotesDb);
        if(!result.full){
            if(compactManifest==null || !"telegram-archive-metadata".equals(compactManifest.optString("format")) ||
                compactManifest.optInt("version")!=ArchiveBackup.VERSION)
                throw new IOException("Некорректный манифест метаданных");
            JSONArray chats=compactManifest.getJSONArray("chats");
            if(chats.length()>1000)throw new IOException("Слишком много переписок");
            HashSet<String> listed=new HashSet<>();
            verifyCompactDb(compactManifest.getJSONObject("notes"),
                "db/notes.sqlite",databaseLengths,databaseDigests,listed);
            for(int i=0;i<chats.length();i++){
                verifyCompactDb(chats.getJSONObject(i),"db/chat-"+i+".sqlite",
                    databaseLengths,databaseDigests,listed);
            }
            if(listed.size()!=databaseLengths.size())
                throw new IOException("Лишний файл базы данных вне манифеста");
        }
        if(result.full){
            if(fullManifest==null||!"telegram-archive-full".equals(fullManifest.optString("format"))||fullManifest.optInt("version")!=2)throw new IOException("Неверный манифест резервной копии");
            FullBackupLayout.requireWithinChatCount(maxSeenMediaChat,fullManifest.getJSONArray("chats").length());
            if(fullManifest.getLong("files")!=mediaFiles||fullManifest.getLong("bytes")!=mediaBytes)throw new IOException("Количество или размер медиа не совпадают");
            if(fullManifest.has("noteFiles")&&!noteHash.hex().equalsIgnoreCase(fullManifest.getString("noteSha256")))
                throw new IOException("Изменено вложение заметки");
            if(fullManifest.has("mediaSha256")){
                if(!mediaHash.hex().equalsIgnoreCase(fullManifest.getString("mediaSha256")))throw new IOException("Контрольная сумма медиа не совпадает");
                result.mediaHashVerified=true;
            }
            if(fullManifest.has("customSha256")){
                if(!customHash.hex().equalsIgnoreCase(fullManifest.getString("customSha256")))throw new IOException("Контрольная сумма аватаров не совпадает");
                result.customHashVerified=true;
            }
        }
        progress.onProgress("Контрольные суммы ZIP проверены: "+result.files+" файлов");
        return result;
    }
}