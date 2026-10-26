package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Extract a user-selected Telegram ZIP/RAR inside private Android app storage.
 * Never trusts entry paths and never writes outside its own dedicated directory.
 */
final class ArchiveFileImport {
    interface Progress { void update(String message); }
    private static final long MAX_ENTRY=12L*1024*1024*1024; // a large exported video can exceed 4 GiB
    private static final long MAX_TOTAL=60L*1024*1024*1024;
    private static final int MAX_FILES=200000;
    static final class Budget {
        long total; int count;
        void next(String name) throws IOException{
            if(++count>MAX_FILES)throw new IOException("Слишком много файлов в архиве");
            if(name.length()>1500)throw new IOException("Очень длинное имя файла в архиве");
        }
        void add(long count,long fileBytes) throws IOException{
            if(fileBytes>MAX_ENTRY||total>MAX_TOTAL-count)throw new IOException("Лимит безопасной распаковки превышен");
            total+=count;
        }
    }
    static File extract(Context ctx,Uri uri,String suffix,String key,Progress progress) throws Exception{
        return extract(ctx,uri,suffix,key,null,progress);
    }
    static File extract(Context ctx,Uri uri,String suffix,String key,char[] password,Progress progress) throws Exception{
        if(!key.matches("[a-f0-9]{32}"))throw new IOException("Недопустимый идентификатор импорта");
        File root=new File(new File(ctx.getFilesDir(),"telegram_imports"),key);
        if(root.exists())throw new IOException("Папка архива уже существует");
        if(!root.mkdirs())throw new IOException("Не удалось создать папку для распаковки");
        try{
            Budget budget=new Budget();
            String fmt=suffix.toLowerCase(Locale.ROOT);
            if(fmt.equals("zip")){
                if(password!=null&&password.length>0)extractZipWithPassword(ctx,uri,root,password,budget,progress);
                else extractZip(ctx,uri,root,budget,progress);
            }else if(fmt.equals("rar")||fmt.equals("rar5"))extractRar(ctx,uri,root,password,budget,progress);
            else if(fmt.equals("7z"))extract7z(ctx,uri,root,password,budget,progress);
            else if(fmt.equals("tar")||fmt.equals("tar.gz")||fmt.equals("tgz")||
                fmt.equals("tar.xz")||fmt.equals("txz")||fmt.equals("tar.bz2")||fmt.equals("tbz2"))
                extractTar(ctx,uri,root,fmt,budget,progress);
            else throw new IOException("Неподдерживаемый формат архива");
            File selected=findChatRoot(root,0);
            if(selected==null)throw new IOException("В архиве не обнаружены messages.html или messages2.html. Экспортируй чат Telegram в формате HTML");
            progress.update("Распаковано файлов: "+budget.count+"; найден экспорт Telegram");
            return selected;
        }catch(Exception fail){deleteTree(root);throw fail;}
    }
    private static File safeFile(File root,String raw) throws IOException{
        if(raw==null)throw new IOException("Отсутствует имя файла");
        String path=raw.replace('\\','/');
        if(path.isEmpty()||path.startsWith("/")||path.startsWith("//")||path.contains("\0")
                ||path.matches("^[a-zA-Z]:.*"))throw new IOException("Недопустимый путь внутри архива");
        for(String part:path.split("/"))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Попытка выхода за пределы папки");
        File target=new File(root,path);
        String rootPath=root.getCanonicalPath()+File.separator;
        if(!target.getCanonicalPath().startsWith(rootPath))throw new IOException("Опасный путь в архиве");
        return target;
    }
    private static void emit(Budget budget,Progress progress){
        if(budget.count==1||budget.count%100==0)
            progress.update("Распаковываю: "+budget.count+" файлов · "+(budget.total/1048576)+" МБ");
    }
    private static void extractZip(Context ctx,Uri uri,File root,Budget budget,Progress progress) throws Exception{
        try(InputStream source=ctx.getContentResolver().openInputStream(uri)){
            if(source==null)throw new IOException("Не удалось открыть ZIP");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,128*1024))){
                ZipEntry entry;byte[] bytes=new byte[128*1024];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();
                    budget.next(name==null?"":name);
                    BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                    if(entry.isDirectory()){zip.closeEntry();continue;}
                    File file=safeFile(root,name);
                    if(entry.getSize()>MAX_ENTRY)throw new IOException("Слишком большой файл в ZIP");
                    File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось создать каталог ZIP");
                    if(file.exists())throw new IOException("Повторное имя файла в архиве");
                    long written=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
                        int n;while((n=zip.read(bytes))!=-1){
                            written+=n;
                            if(written>MAX_ENTRY)throw new IOException("Слишком большой файл в ZIP");
                            budget.add(n,written);
                            out.write(bytes,0,n);
                        }
                    }
                    zip.closeEntry();emit(budget,progress);
                }
            }
        }
    }
    private static void extractRar(Context ctx,Uri uri,File root,char[] password,Budget budget,Progress progress) throws Exception{
        // Junrar needs random access for efficient RAR5/solid decoding. Keep a temporary copy
        // until import has finished; original remains at its selected SAF location.
        File compressed=File.createTempFile("telegram-", ".rar",ctx.getCacheDir());
        try{
            try(InputStream source=ctx.getContentResolver().openInputStream(uri);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(compressed))){
                if(source==null)throw new IOException("RAR недоступен");
                byte[] buffer=new byte[128*1024];int n;
                long archiveBytes=0;
                while((n=source.read(buffer))!=-1){
                    archiveBytes+=n;
                    if(archiveBytes>MAX_TOTAL)throw new IOException("Слишком большой исходный RAR");
                    out.write(buffer,0,n);
                }
            }
            try(Archive rar=new Archive(compressed,
                    com.github.junrar.ArchiveOptions.builder().password(password)
                        .maxDictionarySize(128L*1024*1024).build())){
                for(FileHeader entry:rar.getFileHeaders()){
                    // Even skipped directories and links consume the bounded entry budget.
                    String name=entry.getFileName();
                    budget.next(name==null?"":name);
                    if(entry.isDirectory())continue;
                    if(entry.getRedirection()!=null)continue; // never materialize archive links
                    File target=safeFile(root,name);
                    if(entry.getFullUnpackSize()>MAX_ENTRY)throw new IOException("Слишком большой файл в RAR");
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось создать каталог RAR");
                    if(target.exists())throw new IOException("Повторный файл в RAR");
                    try(OutputStream raw=new BufferedOutputStream(new FileOutputStream(target))){
                        OutputStream bounded=new FilterOutputStream(raw){
                            long size;
                            @Override public void write(int one) throws IOException{
                                size++;budget.add(1,size);out.write(one);
                            }
                            @Override public void write(byte[] data,int off,int len) throws IOException{
                                size+=len;budget.add(len,size);out.write(data,off,len);
                            }
                        };
                        rar.extractFile(entry,bounded);bounded.flush();
                    }
                    emit(budget,progress);
                }
            }
        }finally{compressed.delete();}
    }
    /** Password-aware streaming ZIP decoder. Does not mutate or replace the original ZIP. */
    private static void extractZipWithPassword(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        try(InputStream source=ctx.getContentResolver().openInputStream(uri)){
            if(source==null)throw new IOException("Нет доступа к ZIP");
            try(net.lingala.zip4j.io.inputstream.ZipInputStream zip=
                    new net.lingala.zip4j.io.inputstream.ZipInputStream(
                        new BufferedInputStream(source,128*1024),password)){
                net.lingala.zip4j.model.LocalFileHeader entry;
                byte[] bytes=new byte[128*1024];
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getFileName();
                    budget.next(name==null?"":name);
                    BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                    if(entry.isDirectory()){zip.closeEntry();continue;}
                    File target=safeFile(root,name);
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не создана папка ZIP");
                    if(target.exists())throw new IOException("Повторное имя в ZIP");
                    long written=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                        int count;while((count=zip.read(bytes))!=-1){
                            written+=count;budget.add(count,written);
                            out.write(bytes,0,count);
                        }
                    }
                    zip.closeEntry();emit(budget,progress);
                }
            }
        }
    }
    /** Selective streaming 7z import with AES password, 128 MiB decoder ceiling. */
    private static void extract7z(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        try(android.os.ParcelFileDescriptor descriptor=ctx.getContentResolver().openFileDescriptor(uri,"r")){
            if(descriptor==null)throw new IOException("Нет доступа к 7z");
            try(FileInputStream input=new FileInputStream(descriptor.getFileDescriptor());
                org.apache.commons.compress.archivers.sevenz.SevenZFile archive=
                    org.apache.commons.compress.archivers.sevenz.SevenZFile.builder()
                        .setSeekableByteChannel(input.getChannel())
                        .setPassword(password)
                        .setMaxMemoryLimitKiB(131072).get()){
                org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
                byte[] bytes=new byte[128*1024];
                while((entry=archive.getNextEntry())!=null){
                    String name=entry.getName();budget.next(name==null?"":name);
                    BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                    if(entry.isDirectory())continue;
                    if(entry.getSize()>MAX_ENTRY)throw new IOException("7z содержит слишком большой файл");
                    File target=safeFile(root,name);
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не создана папка 7z");
                    if(target.exists())throw new IOException("Повторное имя в 7z");
                    long written=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                        int count;while((count=archive.read(bytes))!=-1){
                            written+=count;budget.add(count,written);
                            out.write(bytes,0,count);
                        }
                    }
                    emit(budget,progress);
                }
            }
        }
    }
    private static void extractTar(Context ctx,Uri uri,File root,String extension,
            Budget budget,Progress progress)throws Exception{
        try(InputStream input=ctx.getContentResolver().openInputStream(uri)){
            if(input==null)throw new IOException("Нет доступа к TAR");
            InputStream compressed=input;
            if(extension.endsWith(".gz")||extension.equals("tgz"))
                compressed=new java.util.zip.GZIPInputStream(input);
            else if(extension.endsWith(".xz")||extension.equals("txz"))
                compressed=new org.apache.commons.compress.compressors.xz.XZCompressorInputStream(input);
            else if(extension.endsWith(".bz2")||extension.equals("tbz2"))
                compressed=new org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream(input);
            try(org.apache.commons.compress.archivers.tar.TarArchiveInputStream tar=
                    new org.apache.commons.compress.archivers.tar.TarArchiveInputStream(compressed)){
                org.apache.commons.compress.archivers.tar.TarArchiveEntry entry;
                byte[] bytes=new byte[128*1024];
                while((entry=tar.getNextEntry())!=null){
                    String name=entry.getName();budget.next(name==null?"":name);
                    if(entry.isSymbolicLink()||entry.isLink())continue;
                    BackupEntryPolicy.requireSafe(name,entry.isDirectory());
                    if(entry.isDirectory())continue;
                    if(entry.getSize()>MAX_ENTRY)throw new IOException("TAR содержит слишком большой файл");
                    File target=safeFile(root,name);
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не создана папка TAR");
                    if(target.exists())throw new IOException("Повторное имя в TAR");
                    long written=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                        int count;while((count=tar.read(bytes))!=-1){
                            written+=count;budget.add(count,written);
                            out.write(bytes,0,count);
                        }
                    }
                    emit(budget,progress);
                }
            }
        }
    }
    private static File findChatRoot(File folder,int depth){
        if(depth>5)return null;
        File[] items=folder.listFiles();if(items==null)return null;
        for(File f:items)if(f.isFile()&&f.getName().matches("(?i)^messages\\d*\\.html$"))return folder;
        for(File f:items)if(f.isDirectory()){
            File match=findChatRoot(f,depth+1);
            if(match!=null)return match;
        }
        return null;
    }
    static void deleteTree(File file){
        if(file==null)return;
        if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File c:children)deleteTree(c);}
        file.delete();
    }
}
