package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import com.github.junrar.rarfile.FileHeader;
import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

/** Read-only, bounded viewing of user-selected archives. The original archive is never modified.
 * Solid 7z may require decoding preceding blocks to preview a selected entry.
 */
final class ArchiveReadOnly {
    static final int MAX_ENTRIES=20000, MAX_PREVIEW_BYTES=16*1024*1024;
    static final long MAX_TEMP_BYTES=512L*1024*1024;
    static final class Entry {
        final String name;final long size;final boolean directory,encrypted;
        Entry(String n,long s,boolean d,boolean e){name=n;size=s;directory=d;encrypted=e;}
    }
    private ArchiveReadOnly(){}
    static boolean isArchive(String name){
        if(name==null)return false;
        String n=name.toLowerCase(Locale.ROOT);
        int query=n.indexOf('?'),fragment=n.indexOf('#');
        int end=n.length();
        if(query>=0)end=Math.min(end,query);
        if(fragment>=0)end=Math.min(end,fragment);
        n=n.substring(0,end);
        return n.endsWith(".7z")||n.endsWith(".zip")||n.endsWith(".rar")||n.endsWith(".rar5")
            ||n.endsWith(".tar")||n.endsWith(".tar.gz")||n.endsWith(".tgz")
            ||n.endsWith(".tar.xz")||n.endsWith(".txz")||n.endsWith(".tar.bz2")||n.endsWith(".tbz2");
    }
    static boolean safeName(String raw){
        if(raw==null||raw.isEmpty()||raw.length()>1600||raw.indexOf('\0')>=0)return false;
        String x=raw.replace('\\','/');
        if(x.startsWith("/")||x.startsWith("//")||x.matches("^[A-Za-z]:.*"))return false;
        for(String part:x.split("/"))if(part.equals(".")||part.equals(".."))return false;
        return true;
    }
    static List<Entry> list(Context ctx,Uri uri,String filename,char[] password)throws Exception{
        List<Entry> found=new ArrayList<>();
        access(ctx,uri,filename,password,null,MAX_PREVIEW_BYTES,found);
        return found;
    }
    static byte[] read(Context ctx,Uri uri,String filename,char[] password,String requested)throws Exception{
        if(!safeName(requested))throw new IOException("Неподходящее имя вложенного файла");
        return access(ctx,uri,filename,password,requested,MAX_PREVIEW_BYTES,null);
    }
    private static void add(List<Entry> result,String name,long size,boolean dir,boolean encrypted)throws IOException{
        if(!safeName(name))return; // Never expose dangerous paths or links.
        if(result.size()>=MAX_ENTRIES)throw new IOException("В архиве слишком много элементов для просмотра");
        result.add(new Entry(name,size,dir,encrypted));
    }
    private static byte[] copyLimited(InputStream input,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream(Math.min(8192,limit));
        byte[] buffer=new byte[32768];int read,total=0;
        while((read=input.read(buffer))!=-1){
            if(read==0)continue;
            if(read>limit-total)throw new IOException("Этот файл слишком большой для безопасного предпросмотра");
            out.write(buffer,0,read);total+=read;
        }
        return out.toByteArray();
    }
    private static byte[] copy7z(SevenZFile seven,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buf=new byte[16384];int n,total=0;
        while((n=seven.read(buf))!=-1){
            if(n>limit-total)throw new IOException("Вложение слишком велико для просмотра");
            total+=n;out.write(buf,0,n);
        }
        return out.toByteArray();
    }
    private static byte[] access(Context ctx,Uri uri,String filename,char[] password,String requested,
                                 int limit,List<Entry> found)throws Exception{
        String name=filename==null?"":filename.toLowerCase(Locale.ROOT);
        if(name.endsWith(".7z"))return seven(ctx,uri,password,requested,limit,found);
        if(name.endsWith(".zip"))return zip(ctx,uri,password,requested,limit,found);
        if(name.endsWith(".rar")||name.endsWith(".rar5"))return rar(ctx,uri,password,requested,limit,found);
        if(name.endsWith(".tar")||name.endsWith(".tar.gz")||name.endsWith(".tgz")||
            name.endsWith(".tar.xz")||name.endsWith(".txz")||name.endsWith(".tar.bz2")||name.endsWith(".tbz2"))
            return tar(ctx,uri,name,requested,limit,found);
        throw new IOException("Формат архива пока не поддерживается");
    }
    private static byte[] seven(Context ctx,Uri uri,char[] password,String requested,int limit,List<Entry> found)throws Exception{
        try(ParcelFileDescriptor fd=ctx.getContentResolver().openFileDescriptor(uri,"r")){
            if(fd==null)throw new IOException("7z недоступен");
            try(FileInputStream stream=new FileInputStream(fd.getFileDescriptor());
                SevenZFile archive=SevenZFile.builder().setChannel(stream.getChannel())
                    .setMaxMemoryLimitKiB(65536).setPassword(password).get()){
                SevenZArchiveEntry e;boolean matched=false;
                while((e=archive.getNextEntry())!=null){
                    String path=e.getName();
                    if(found!=null)add(found,path,e.getSize(),e.isDirectory(),e.getContentMethods()!=null&&
                        e.getContentMethods().toString().contains("AES"));
                    if(requested!=null&&requested.equals(path)&&!e.isDirectory()){
                        if(matched)throw new IOException("Повторяющееся имя файла в архиве");
                        matched=true;
                        if(e.getSize()>limit)throw new IOException("Вложение слишком большое");
                        return copy7z(archive,limit);
                    }
                }
                if(requested!=null)throw new IOException("Вложенный файл не найден");
                return null;
            }
        }
    }
    private static byte[] zip(Context ctx,Uri uri,char[] password,String requested,int limit,List<Entry> found)throws Exception{
        try(ParcelFileDescriptor fd=ctx.getContentResolver().openFileDescriptor(uri,"r")){
            if(fd==null)throw new IOException("ZIP недоступен");
            try(FileInputStream stream=new FileInputStream(fd.getFileDescriptor());
                org.apache.commons.compress.archivers.zip.ZipFile archive=
                    org.apache.commons.compress.archivers.zip.ZipFile.builder().setChannel(stream.getChannel()).get()){
                Enumeration<ZipArchiveEntry> entries=archive.getEntries();int count=0;
                while(entries.hasMoreElements()){
                    ZipArchiveEntry e=entries.nextElement();
                    if(++count>MAX_ENTRIES)throw new IOException("Слишком много элементов ZIP");
                    boolean encrypted=e.getGeneralPurposeBit().usesEncryption();
                    if(found!=null)add(found,e.getName(),e.getSize(),e.isDirectory(),encrypted);
                    if(requested!=null&&requested.equals(e.getName())&&!e.isDirectory()){
                        if(!safeName(e.getName()))throw new IOException("Небезопасный путь вложения");
                        if(e.getSize()>limit)throw new IOException("Вложение слишком большое");
                        if(encrypted){
                            if(password==null||password.length==0)throw new IOException("ZIP зашифрован: введите пароль");
                            return encryptedZip(ctx,uri,requested,password,limit);
                        }
                        if(!archive.canReadEntryData(e))throw new IOException("Сжатие вложения не поддерживается");
                        try(InputStream input=archive.getInputStream(e)){return copyLimited(input,limit);}
                    }
                }
            }
        }
        if(requested!=null)throw new IOException("Вложение отсутствует");
        return null;
    }
    private static File cached(Context ctx,Uri uri,String suffix)throws IOException{
        File temp=File.createTempFile("archive-view-","."+suffix,ctx.getCacheDir());
        try(InputStream raw=ctx.getContentResolver().openInputStream(uri);
            OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
            if(raw==null)throw new IOException("Не удалось открыть архив");
            byte[] buf=new byte[65536];int n;long copied=0;
            while((n=raw.read(buf))!=-1){
                if(n==0)continue;
                copied+=n;
                if(copied>MAX_TEMP_BYTES)throw new IOException("RAR/ZIP с шифрованием превышает лимит временного просмотра 512 МБ");
                out.write(buf,0,n);
            }
            return temp;
        }catch(Exception ex){temp.delete();throw ex;}
    }
    private static byte[] encryptedZip(Context ctx,Uri uri,String path,char[] password,int limit)throws Exception{
        File file=cached(ctx,uri,"zip");
        try(net.lingala.zip4j.ZipFile archive=new net.lingala.zip4j.ZipFile(file,password)){
            net.lingala.zip4j.model.FileHeader e=archive.getFileHeader(path);
            if(e==null||e.isDirectory())throw new IOException("Файл в ZIP отсутствует");
            if(e.getUncompressedSize()>limit)throw new IOException("Вложение слишком большое");
            try(InputStream input=archive.getInputStream(e)){return copyLimited(input,limit);}
        }finally{file.delete();}
    }
    private static byte[] rar(Context ctx,Uri uri,char[] password,String requested,int limit,List<Entry> found)throws Exception{
        File file=cached(ctx,uri,"rar");
        try(Archive archive=new Archive(file,ArchiveOptions.builder().password(password).maxDictionarySize(128L*1024*1024).build())){
            int count=0;
            for(FileHeader e:archive.getFileHeaders()){
                if(++count>MAX_ENTRIES)throw new IOException("Слишком много элементов RAR");
                String path=e.getFileName();
                if(e.getRedirection()!=null)continue; // Never follow archive links.
                if(found!=null)add(found,path,e.getFullUnpackSize(),e.isDirectory(),false);
                if(requested!=null&&requested.equals(path)&&!e.isDirectory()){
                    if(!safeName(path))throw new IOException("Небезопасное имя файла");
                    if(e.getFullUnpackSize()>limit)throw new IOException("Вложение слишком велико");
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    archive.extractFile(e,new FilterOutputStream(out){
                        int size;
                        @Override public void write(int value)throws IOException{
                            if(++size>limit)throw new IOException("Превышен лимит вложения");
                            super.write(value);
                        }
                        @Override public void write(byte[] b,int off,int len)throws IOException{
                            if(len>limit-size)throw new IOException("Превышен лимит вложения");
                            size+=len;out.write(b,off,len);
                        }
                    });
                    return out.toByteArray();
                }
            }
        }finally{file.delete();}
        if(requested!=null)throw new IOException("Вложение не найдено");
        return null;
    }
    private static byte[] tar(Context ctx,Uri uri,String name,String requested,int limit,List<Entry> found)throws Exception{
        try(InputStream raw=ctx.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new IOException("TAR недоступен");
            InputStream compression=raw;
            if(name.endsWith(".tgz")||name.endsWith(".tar.gz"))compression=new GZIPInputStream(raw);
            else if(name.endsWith(".txz")||name.endsWith(".tar.xz"))compression=new XZCompressorInputStream(raw);
            else if(name.endsWith(".tbz2")||name.endsWith(".tar.bz2"))compression=new BZip2CompressorInputStream(raw);
            try(TarArchiveInputStream tar=new TarArchiveInputStream(compression)){
                TarArchiveEntry e;int count=0;
                while((e=tar.getNextEntry())!=null){
                    if(++count>MAX_ENTRIES)throw new IOException("Слишком много элементов TAR");
                    if(e.isSymbolicLink()||e.isLink())continue;
                    if(found!=null)add(found,e.getName(),e.getSize(),e.isDirectory(),false);
                    if(requested!=null&&requested.equals(e.getName())&&!e.isDirectory()){
                        if(!safeName(e.getName()))throw new IOException("Небезопасное имя файла");
                        if(e.getSize()>limit)throw new IOException("Вложение слишком велико");
                        return copyLimited(tar,limit);
                    }
                }
            }
        }
        if(requested!=null)throw new IOException("Вложение не найдено");
        return null;
    }
}
