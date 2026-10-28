package org.openarchive.telegram;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Portable ZIP v2. Contains v1 metadata backup plus original Telegram media trees.
 * Streams files without loading media in memory. Archives are private user data: no networking. */
final class FullBackup {
    private static final long MAX_BYTES=80L*1024*1024*1024;
    private static final int MAX_FILES=250000;
    interface Progress {void show(String text);}
    private static long stream(InputStream in,OutputStream out,long max) throws IOException {
        byte[] buf=new byte[128*1024];long count=0;int n;
        while((n=in.read(buf))!=-1){count+=n;if(count>max)throw new IOException("Слишком большой файл");out.write(buf,0,n);}
        return count;
    }
    static void export(Context ctx,SharedPreferences prefs,Uri target,Progress progress) throws Exception {
        try(OutputStream output=ctx.getContentResolver().openOutputStream(target,"w")){
            if(output==null)throw new IOException("Нельзя открыть файл назначения");
            export(ctx,prefs,output,progress);
        }
    }
    static void export(Context ctx,SharedPreferences prefs,OutputStream target,Progress progress) throws Exception {
        String registrySnapshot=prefs.getString("archive_registry","[]");
        JSONArray chats=new JSONArray(registrySnapshot);
        File metadata=File.createTempFile("tg-metadata-", ".zip",ctx.getCacheDir());
        try{
            ArchiveBackup.write(ctx,prefs,Uri.fromFile(metadata),progress::show);
            {
                try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(target,128*1024))){
                    zip.setLevel(1); // media is typically already compressed
                    writeZipFile(zip,metadata,"metadata.zip");
                    JSONObject index=new JSONObject();
                    index.put("format","telegram-archive-full");index.put("version",2);
                    index.put("mediaIncluded",true);
                    JSONArray roots=new JSONArray();long[] total={0};int[] files={0};
                    MediaIntegrity mediaHash=new MediaIntegrity();
                    MediaIntegrity customHash=new MediaIntegrity();
                    for(int i=0;i<chats.length();i++){
                        JSONObject chat=chats.getJSONObject(i);
                        String stored=chat.optString("tree","");
                        if(stored.isEmpty())throw new IOException("Для чата «"+chat.optString("name")+"» не подключена исходная папка");
                        DocumentFolder source=stored.startsWith("local:")
                            ?new DocumentFolder(ctx.getContentResolver(),new File(stored.substring(6)))
                            :new DocumentFolder(ctx.getContentResolver(),Uri.parse(stored));
                        JSONObject root=new JSONObject();root.put("chatIndex",i);root.put("prefix","media/chat-"+i+"/");roots.put(root);
                        progress.show("Сохраняю исходные медиа "+(i+1)+"/"+chats.length());
                        traverse(ctx,source,source.rootId(),"","media/chat-"+i+"/",zip,progress,total,files,mediaHash,0);
                    }
                    File custom=new File(ctx.getFilesDir(),"personal_images");
                    File[] photos=custom.listFiles();
                    if(photos!=null)for(File asset:photos){
                        if(asset.isFile()&&asset.getName().matches("[a-zA-Z0-9_-]{1,96}\\.jpg"))
                            writeZipFile(zip,asset,"custom/"+asset.getName(),customHash);
                    }
                    // Attachments on global notes are SAF references from the old phone.
                    // Include the underlying bytes, or abort rather than claiming a portable copy.
                    JSONArray noteFiles=new JSONArray();long[] noteTotal={0};
                    MediaIntegrity noteHash=new MediaIntegrity();
                    SavedStore noteStore=new SavedStore(ctx);
                    try{
                        android.database.sqlite.SQLiteDatabase notesDb=noteStore.getReadableDatabase();
                        try(android.database.Cursor c=notesDb.rawQuery("SELECT id,file_id FROM saved_notes WHERE file_id IS NOT NULL ORDER BY id",null)){
                            while(c.moveToNext()){
                                long noteId=c.getLong(0);
                                String reference=c.getString(1);
                                Uri source=reference.startsWith(PortableNoteFiles.PREFIX)
                                    ?PortableNoteFiles.openUri(ctx,reference):Uri.parse(reference);
                                String name="note_files/"+noteId;
                                zip.putNextEntry(new ZipEntry(name));noteHash.begin(name);
                                long bytes=0;
                                try(InputStream in=ctx.getContentResolver().openInputStream(source)){
                                    if(in==null)throw new IOException("Вложение заметки "+noteId+" недоступно на этом устройстве");
                                    byte[] buf=new byte[128*1024];int read;
                                    while((read=in.read(buf))!=-1){
                                        bytes+=read;noteTotal[0]+=read;
                                        if(bytes>2L*1024*1024*1024||noteTotal[0]+total[0]>MAX_BYTES)
                                            throw new IOException("Слишком много данных во вложении заметки");
                                        zip.write(buf,0,read);noteHash.update(buf,0,read);
                                    }
                                }catch(Exception error){throw new IOException("Не удалось добавить вложение заметки "+noteId+" в полный архив",error);}
                                zip.closeEntry();noteHash.end();
                                noteFiles.put(noteId);
                                if(noteFiles.length()>5000)throw new IOException("Слишком много вложений к заметкам");
                            }
                        }
                    }finally{noteStore.close();}
                    index.put("noteFiles",noteFiles);index.put("noteSha256",noteHash.hex());
                    // The metadata ZIP and media tree must describe the SAME registry.
                    // Do not publish a deceptively complete backup if chats changed
                    // while copying gigabytes of original media.
                    if(!registrySnapshot.equals(prefs.getString("archive_registry","[]")))
                        throw new IOException("Список переписок изменился во время резервного копирования. Повтори операцию");
                    index.put("chats",roots);index.put("files",files[0]);index.put("bytes",total[0]);
                    index.put("mediaSha256",mediaHash.hex());index.put("customSha256",customHash.hex());
                    zip.putNextEntry(new ZipEntry("full-manifest.json"));
                    zip.write(index.toString().getBytes("UTF-8"));zip.closeEntry();
                }
            }
        }finally{metadata.delete();}
    }
    private static void writeZipFile(ZipOutputStream zip,File file,String name) throws Exception{
        writeZipFile(zip,file,name,null);
    }
    private static void writeZipFile(ZipOutputStream zip,File file,String name,MediaIntegrity hash) throws Exception{
        zip.putNextEntry(new ZipEntry(name));
        if(hash!=null)hash.begin(name);
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[128*1024];int n;long size=0;
            while((n=in.read(buf))!=-1){
                size+=n;if(size>3L*1024*1024*1024)throw new IOException("Слишком большой файл");
                zip.write(buf,0,n);if(hash!=null)hash.update(buf,0,n);
            }
        }
        zip.closeEntry();if(hash!=null)hash.end();
    }
    private static void traverse(Context ctx,DocumentFolder folder,String id,String relative,String prefix,ZipOutputStream zip,
                                 Progress progress,long[] total,int[] count,MediaIntegrity mediaHash,int depth) throws Exception {
        if(depth>32)throw new IOException("Слишком глубокие каталоги");
        for(DocumentFolder.Entry item:folder.children(id)){
            String name=item.name;
            if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\\')>=0)throw new IOException("Недопустимое имя файла");
            String path=relative+name;
            if(path.length()>1200)throw new IOException("Слишком длинный путь");
            if("vnd.android.document/directory".equals(item.mime))traverse(ctx,folder,item.id,path+"/",prefix,zip,progress,total,count,mediaHash,depth+1);
            else{
                if(++count[0]>MAX_FILES)throw new IOException("Слишком много файлов");
                zip.putNextEntry(new ZipEntry(prefix+path));
                mediaHash.begin(prefix+path);
                try(InputStream in=ctx.getContentResolver().openInputStream(folder.uriFor(item.id))){
                    if(in==null)throw new IOException("Нет доступа к "+path);
                    byte[] buffer=new byte[128*1024];int n;long fileSize=0;
                    while((n=in.read(buffer))!=-1){
                        fileSize+=n;total[0]+=n;
                        if(total[0]>MAX_BYTES)throw new IOException("Архив превышает установленный предел");
                        zip.write(buffer,0,n);mediaHash.update(buffer,0,n);
                    }
                }
                zip.closeEntry();mediaHash.end();
                if(count[0]%200==0)progress.show("Упаковано "+count[0]+" файлов · "+total[0]/1048576+" МБ");
            }
        }
    }
    private static boolean sameContent(File first,File second) throws IOException {
        if(first.length()!=second.length())return false;
        try(InputStream a=new FileInputStream(first);InputStream b=new FileInputStream(second)){
            byte[] left=new byte[32*1024],right=new byte[32*1024];int n;
            while((n=a.read(left))!=-1){
                int offset=0;
                while(offset<n){int got=b.read(right,offset,n-offset);if(got<0)return false;offset+=got;}
                for(int i=0;i<n;i++)if(left[i]!=right[i])return false;
            }
            return b.read()==-1;
        }
    }
    static int restore(Context ctx,SharedPreferences prefs,Uri uri,Progress progress) throws Exception {
        try(InputStream in=ctx.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Архив недоступен");
            return restore(ctx,prefs,in,progress);
        }
    }
    static int restore(Context ctx,SharedPreferences prefs,InputStream streamed,Progress progress)throws Exception{
        File staging=new File(new File(ctx.getFilesDir(),"telegram_imports"),"restore-"+UUID.randomUUID().toString().replace("-",""));
        if(!staging.mkdirs())throw new IOException("Невозможно подготовить восстановление");
        File metadata=new File(staging,"metadata.zip");
        int count=0,mediaFiles=0;long total=0,mediaBytes=0;
        int maxSeenMediaChat=-1;
        boolean complete=false;JSONObject manifest=null;
        RestoreJournal journal=null;
        MediaIntegrity mediaHash=new MediaIntegrity();
        MediaIntegrity customHash=new MediaIntegrity();
        MediaIntegrity noteHash=new MediaIntegrity();
        Map<Long,String> noteReferences=new HashMap<>();
        try{
            // Journal exists before extraction: a process kill during a 9 GB import
            // must not strand gigabytes of unregistered media on the next launch.
            journal=RestoreJournal.begin(ctx,staging);
            {
                try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(streamed,128*1024))){
                    ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null){
                        if(++count>BackupEntryPolicy.MAX_ENTRIES)throw new IOException("Превышен лимит элементов ZIP");
                        String name=entry.getName();
                        BackupEntryPolicy.requireFullBackupPath(name,entry.isDirectory());
                        if(entry.isDirectory()){zip.closeEntry();continue;}
                        if(name.equals("full-manifest.json")){
                            if(manifest!=null)throw new IOException("Повторный full-manifest.json");
                            ByteArrayOutputStream out=new ByteArrayOutputStream();stream(zip,out,2*1024*1024);
                            manifest=new JSONObject(out.toString("UTF-8"));
                        }else{
                            File file=new File(staging,name);
                            if(!file.getCanonicalPath().startsWith(staging.getCanonicalPath()+File.separator))throw new IOException("Выход за пределы папки");
                            if(file.exists())throw new IOException("Повторный файл в архиве");
                            if(name.equals("metadata.zip")&&metadata.isFile())throw new IOException("Повторный metadata.zip");
                            File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Нельзя создать папку");
                            if(name.startsWith("media/")) {
                                maxSeenMediaChat=Math.max(maxSeenMediaChat,FullBackupLayout.mediaChatIndex(name));
                                mediaHash.begin(name);
                            }
                            if(name.startsWith("custom/"))customHash.begin(name);
                            if(name.startsWith("note_files/"))noteHash.begin(name);
                            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
                                byte[] buf=new byte[128*1024];int n;long fileBytes=0;
                                while((n=zip.read(buf))!=-1){
                                    fileBytes+=n;total+=n;
                                    if(total>MAX_BYTES)throw new IOException("Превышен лимит распаковки");
                                    if(name.startsWith("media/")){mediaBytes+=n;mediaHash.update(buf,0,n);}
                                    if(name.startsWith("custom/"))customHash.update(buf,0,n);
                                    if(name.startsWith("note_files/"))noteHash.update(buf,0,n);
                                    if(name.startsWith("custom/")&&fileBytes>20L*1024*1024)
                                        throw new IOException("Слишком большое пользовательское изображение");
                                    out.write(buf,0,n);
                                }
                            }
                            if(name.startsWith("media/")){mediaFiles++;mediaHash.end();}
                            if(name.startsWith("custom/"))customHash.end();
                            if(name.startsWith("note_files/")){
                                noteHash.end();long noteId=Long.parseLong(name.substring("note_files/".length()));
                                noteReferences.put(noteId,PortableNoteFiles.reference(staging,noteId));
                            }
                        }
                        zip.closeEntry();
                        if(count%200==0)progress.show("Восстановлено "+count+" файлов");
                    }
                }
            }
            if(manifest==null||!"telegram-archive-full".equals(manifest.optString("format"))||
                manifest.optInt("version")!=2||!metadata.isFile())throw new IOException("Некорректная полная резервная копия");
            JSONArray roots=manifest.getJSONArray("chats");
            if(roots.length()>1000)throw new IOException("Слишком много переписок");
            FullBackupLayout.requireWithinChatCount(maxSeenMediaChat,roots.length());
            if(manifest.getLong("files")!=mediaFiles||manifest.getLong("bytes")!=mediaBytes)
                throw new IOException("Количество или размер медиа не совпадают с манифестом");
            // Older v2 backups have totals only; new backups also authenticate all media paths and bytes.
            if(manifest.has("mediaSha256")&&!mediaHash.hex().equalsIgnoreCase(manifest.getString("mediaSha256")))
                throw new IOException("Контрольная сумма медиа не совпадает — восстановление остановлено");
            if(manifest.has("customSha256")&&!customHash.hex().equalsIgnoreCase(manifest.getString("customSha256")))
                throw new IOException("Контрольная сумма аватаров и обоев не совпадает — восстановление остановлено");
            // Authenticate note bytes, identifiers and completeness before publishing databases.
            if(manifest.has("noteFiles")){
                if(!manifest.has("noteSha256")||!noteHash.hex().equalsIgnoreCase(manifest.getString("noteSha256")))
                    throw new IOException("Изменены вложения заметок");
                JSONArray saved=manifest.getJSONArray("noteFiles");
                if(saved.length()!=noteReferences.size())throw new IOException("Неполный набор вложений заметок");
                Set<Long> seenNoteIds=new HashSet<>();
                for(int i=0;i<saved.length();i++){
                    long noteId=saved.getLong(i);
                    if(!seenNoteIds.add(noteId))throw new IOException("Идентификатор вложения заметки повторяется");
                    if(!noteReferences.containsKey(noteId))throw new IOException("Не найдено вложение заметки "+noteId);
                }
            }else if(!noteReferences.isEmpty())throw new IOException("Неизвестные вложения заметок в архиве");
            // Validate the media structure and the metadata *before* any database is registered.
            ArrayList<File> mediaRoots=new ArrayList<>();
            for(int i=0;i<roots.length();i++){
                JSONObject root=roots.getJSONObject(i);
                if(root.getInt("chatIndex")!=i||!("media/chat-"+i+"/").equals(root.getString("prefix")))
                    throw new IOException("Неверный порядок каталогов медиа");
                File media=new File(staging,"media/chat-"+i);
                if(media.exists()&&!media.isDirectory())throw new IOException("Неверный тип каталога медиа");
                if(!media.isDirectory()&&!media.mkdirs())throw new IOException("Не удалось создать каталог медиа");
                mediaRoots.add(media);
            }
            int expected=-1;
            try(InputStream input=new FileInputStream(metadata);
                ZipInputStream nested=new ZipInputStream(new BufferedInputStream(input))){
                ZipEntry part;
                while((part=nested.getNextEntry())!=null){
                    if("manifest.json".equals(part.getName())){
                        ByteArrayOutputStream manifestBytes=new ByteArrayOutputStream();
                        stream(nested,manifestBytes,2*1024*1024);
                        expected=new JSONObject(manifestBytes.toString("UTF-8")).getJSONArray("chats").length();
                        break;
                    }
                }
            }
            if(expected<0||expected!=roots.length())throw new IOException("Неполный архив: число переписок не совпадает");
            // Start a durable recovery record before any avatar, DB or note is installed.
            // Install custom assets before committing metadata, avoiding dangling restored chats.
            File assets=new File(staging,"custom");
            File destination=new File(ctx.getFilesDir(),"personal_images");
            if(assets.isDirectory()){
                if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("Нет каталога аватаров");
                File[] photos=assets.listFiles();
                if(photos==null)throw new IOException("Не удаётся прочитать пользовательские изображения");
                for(File item:photos){
                    if(!item.isFile()||!item.getName().matches("[a-zA-Z0-9_-]{1,96}\\.jpg"))
                        throw new IOException("Недопустимое пользовательское изображение");
                    File target=new File(destination,item.getName());
                    if(target.exists()){
                        if(!sameContent(item,target))
                            throw new IOException("Конфликт имени пользовательского изображения: "+item.getName());
                    }else{
                        File temp=new File(destination,item.getName()+".restoring");
                        if(temp.exists())throw new IOException("Незавершённое восстановление изображения");
                        journal.recordAsset(item.getName()); // durable BEFORE touching destination
                        try(InputStream in=new FileInputStream(item);FileOutputStream out=new FileOutputStream(temp)){
                            stream(in,out,20L*1024*1024);
                            out.getFD().sync();
                        }
                        if(!temp.renameTo(target))throw new IOException("Не удалось сохранить изображение");
                    }
                }
            }
            // Full registry paths are now included in the same commit as the imported metadata.
            int restored;
            try(InputStream input=new FileInputStream(metadata)){
                restored=ArchiveBackup.restore(ctx,prefs,input,progress::show,mediaRoots,journal,noteReferences);
            }
            if(roots.length()!=restored)throw new IOException("Количество медиа-каталогов не совпадает");
            complete=true;return restored;
        }finally{
            metadata.delete();
            if(!complete){
                if(journal!=null){
                    // Read the registry before deciding whether cleanup is safe.
                    RestoreJournal.recover(ctx,prefs,journal);
                }else ArchiveFileImport.deleteTree(staging);
            }
            // On success the full media tree remains in private storage.
        }
    }
}