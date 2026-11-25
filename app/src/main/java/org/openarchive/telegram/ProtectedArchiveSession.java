package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
import com.github.junrar.rarfile.FileHeader;
import java.io.*;
import java.util.*;
import net.lingala.zip4j.ZipFile;

/**
 * Read-only session for one ZIP/RAR. No extracted tree, no plaintext files,
 * no password persistence. SAF providers without a usable local file need
 * ONE bounded copy of the original container, never one copy per attachment.
 * Call on a background thread and close after use.
 */
final class ProtectedArchiveSession implements AutoCloseable {
    static final int MAX_ENTRIES=200000;
    static final class Entry {
        final String name; final long size; final boolean encrypted;
        Entry(String name,long size,boolean encrypted){
            this.name=name;this.size=size;this.encrypted=encrypted;
        }
    }
    private final File file;
    private final boolean temporary;
    private final ParcelFileDescriptor retainedDescriptor;
    private final char[] password;
    private final String format;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private ZipFile zipReader;
    private final Map<String,net.lingala.zip4j.model.FileHeader> zipHeaders=new HashMap<>();
    private boolean closed;

    static ProtectedArchiveSession open(Context context,Uri uri,String format,char[] secret)throws Exception{
        if(!"zip".equals(format)&&!"rar".equals(format))
            throw new IOException("Защищённый просмотр: только ZIP и RAR");
        File source=null;
        if("file".equals(uri.getScheme())&&uri.getPath()!=null){
            File candidate=new File(uri.getPath());
            if(candidate.isFile()&&candidate.canRead())source=candidate;
        }
        // Most local SAF providers hand out a seekable descriptor. Reopen its
        // proc-fd alias only when Android permits read access and seeking.
        // No path guessing or broad storage permission.
        ParcelFileDescriptor retained=null;
        if(source==null&&"content".equals(uri.getScheme())){
            try{
                retained=context.getContentResolver().openFileDescriptor(uri,"r");
                if(retained!=null){
                    File alias=new File("/proc/self/fd/"+retained.getFd());
                    try(RandomAccessFile probe=new RandomAccessFile(alias,"r")){
                        if(probe.length()>0){probe.seek(0);source=alias;}
                    }
                }
            }catch(IOException|SecurityException ignored){}
            if(source==null&&retained!=null){try{retained.close();}catch(IOException ignored){}retained=null;}
        }
        boolean temporary=source==null;
        if(temporary){
            source=File.createTempFile("protected-container-","."+format,context.getCacheDir());
            try(InputStream in=context.getContentResolver().openInputStream(uri);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(source))){
                if(in==null)throw new IOException("Архив недоступен");
                long limit=ArchiveFileImport.maxTemporaryCopyForFreeBytes(context.getCacheDir().getUsableSpace());
                transfer(in,out,limit);
            }catch(Exception ex){source.delete();throw ex;}
        }
        ProtectedArchiveSession result=new ProtectedArchiveSession(source,temporary,retained,format,secret);
        try{result.index();return result;}
        catch(Exception|OutOfMemoryError ex){result.close();throw ex;}
    }
    private ProtectedArchiveSession(File file,boolean temporary,ParcelFileDescriptor retained,String format,char[] secret){
        this.file=file;this.temporary=temporary;this.retainedDescriptor=retained;this.format=format;
        password=secret==null?new char[0]:secret.clone();
    }
    private static void checkCancelled()throws InterruptedIOException{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Чтение отменено");
    }
    private static long transfer(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] buffer=new byte[65536];long total=0;int n;
        try{
            while((n=in.read(buffer))!=-1){
                checkCancelled();
                if(n>limit-total)throw new IOException("Превышен лимит чтения или свободного места");
                out.write(buffer,0,n);total+=n;
            }
            return total;
        }finally{Arrays.fill(buffer,(byte)0);}
    }
    private static String normalized(String name){
        if(!ArchiveReadOnly.safeName(name))return null;
        String value=name.replace('\\','/');
        if(value.contains("//")||value.endsWith("/")||value.contains(":"))return null;
        return value;
    }
    private void add(String name,long size,boolean encrypted)throws IOException{
        String key=normalized(name);
        if(key==null)return;
        if(entries.size()>=MAX_ENTRIES)throw new IOException("Слишком много вложений");
        if(entries.putIfAbsent(key,new Entry(key,size,encrypted))!=null)
            throw new IOException("В архиве повторяются имена файлов");
    }
    private Archive openRar()throws Exception{
        return new Archive(file,ArchiveOptions.builder()
            .password(password.length==0?null:password)
            .maxDictionarySize(128L*1024*1024).build());
    }
    private void index()throws Exception{
        if("zip".equals(format)){
            zipReader=new ZipFile(file,password);
            if(zipReader.isSplitArchive())throw new IOException("Многотомный ZIP не поддерживается");
            for(net.lingala.zip4j.model.FileHeader header:zipReader.getFileHeaders()){
                checkCancelled();
                String name=normalized(header.getFileName());
                if(!header.isDirectory()&&name!=null){
                    add(header.getFileName(),header.getUncompressedSize(),header.isEncrypted());
                    zipHeaders.put(name,header);
                }
            }
        }else{
            try(Archive rar=openRar()){
                for(FileHeader header:rar.getFileHeaders()){
                    checkCancelled();
                    if(!header.isDirectory()&&header.getRedirection()==null)
                        add(header.getFileName(),header.getFullUnpackSize(),header.isEncrypted());
                }
            }
        }
    }
    private void requireOpen()throws IOException{
        if(closed)throw new IOException("Сеанс заблокирован");
    }
    synchronized List<Entry> list()throws IOException{
        requireOpen();return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }
    /** Read exactly one selected member. Caller must discard output if this throws. */
    synchronized void copyEntry(String name,OutputStream destination,long limit)throws Exception{
        requireOpen();
        Entry entry=entries.get(normalized(name));
        if(entry==null)throw new FileNotFoundException("Вложение отсутствует");
        if(limit<0||entry.size<0||entry.size>limit)throw new IOException("Вложение превышает лимит чтения");
        if(entry.encrypted&&password.length==0)throw new IOException("Требуется пароль");
        if("zip".equals(format)){
            net.lingala.zip4j.model.FileHeader h=zipHeaders.get(entry.name);
            if(h==null)throw new FileNotFoundException("Вложение отсутствует");
            try(InputStream in=zipReader.getInputStream(h)){
                if(transfer(in,destination,limit)!=entry.size)
                    throw new IOException("Размер ZIP-вложения не совпадает");
            }
            return;
        }else{
            try(Archive rar=openRar()){
                for(FileHeader h:rar.getFileHeaders()){
                    if(h.isDirectory()||h.getRedirection()!=null||!entry.name.equals(normalized(h.getFileName())))continue;
                    final long[] count={0};
                    rar.extractFile(h,new OutputStream(){
                        @Override public void write(int value)throws IOException{
                            checkCancelled();
                            if(count[0]>=limit)throw new IOException("Превышен лимит вложения");
                            destination.write(value);count[0]++;
                        }
                        @Override public void write(byte[] b,int off,int len)throws IOException{
                            checkCancelled();
                            if(len>limit-count[0])throw new IOException("Превышен лимит вложения");
                            destination.write(b,off,len);count[0]+=len;
                        }
                    });
                    if(count[0]!=entry.size)throw new IOException("Размер RAR-вложения не совпадает");
                    return;
                }
            }
        }
        throw new FileNotFoundException("Вложение отсутствует");
    }
    synchronized byte[] read(String name,int limit)throws Exception{
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        copyEntry(name,data,limit);
        return data.toByteArray();
    }
    synchronized boolean usesContainerCopy(){return temporary;}
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;Arrays.fill(password,'\0');entries.clear();zipHeaders.clear();
        if(zipReader!=null){try{zipReader.close();}catch(IOException ignored){}zipReader=null;}
        if(retainedDescriptor!=null)try{retainedDescriptor.close();}catch(IOException ignored){}
        if(temporary)file.delete();
    }
}
