package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import com.github.junrar.Archive;
import com.github.junrar.ArchiveOptions;
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
    /** Signals an actual password barrier, never a generic broken archive. */
    static final class PasswordRequired extends IOException {
        PasswordRequired(String message){super(message);}
        PasswordRequired(String message,Throwable cause){super(message,cause);}
    }
    /** The decoder finished; only discovery of a Telegram export failed. */
    static final class ExportNotFound extends IOException {
        ExportNotFound(String message){super(message);}
    }
    static boolean isPasswordFailure(Throwable error){
        if(error instanceof ExportNotFound)return false;
        for(Throwable e=error;e!=null;e=e.getCause()){
            if(e instanceof PasswordRequired)return true;
            String type=e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String message=e.getMessage()==null?"":e.getMessage().toLowerCase(Locale.ROOT);
            if(type.contains("password")||message.contains("password")
                ||message.contains("encrypted")||message.contains("encryption")
                ||message.contains("зашифр")||message.contains("парол")
                ||message.contains("bad padding")||message.contains("wrong key"))
                return true;
        }
        return false;
    }
    /** Trust the archive's magic bytes, not its file-provider extension or 7-Zip app label. */
    static String detectFormat(Context ctx,Uri uri,String displayName)throws IOException{
        byte[] bytes=new byte[8];int read=0;
        try(InputStream input=ctx.getContentResolver().openInputStream(uri)){
            if(input==null)throw new IOException("Нет доступа к выбранному файлу");
            while(read<bytes.length){
                int count=input.read(bytes,read,bytes.length-read);
                if(count<0)break;
                if(count==0)continue;
                read+=count;
            }
        }
        if(read>=6&&(bytes[0]&255)==0x37&&(bytes[1]&255)==0x7a
            &&(bytes[2]&255)==0xbc&&(bytes[3]&255)==0xaf
            &&(bytes[4]&255)==0x27&&(bytes[5]&255)==0x1c)return "7z";
        if(read>=4&&bytes[0]=='P'&&bytes[1]=='K'&&
            ((bytes[2]==3&&bytes[3]==4)||(bytes[2]==5&&bytes[3]==6)
              ||(bytes[2]==7&&bytes[3]==8)))return "zip";
        if(read>=7&&bytes[0]=='R'&&bytes[1]=='a'&&bytes[2]=='r'&&bytes[3]=='!'
            &&(bytes[4]&255)==0x1a&&(bytes[5]&255)==7
            &&((bytes[6]&255)==0||(bytes[6]&255)==1))return "rar";
        throw new IOException("Файл не похож на ZIP, 7z или RAR. Проверь формат архива.");
    }
    private static final long MAX_ENTRY=12L*1024*1024*1024; // a large exported video can exceed 4 GiB
    private static final long MAX_TOTAL=60L*1024*1024*1024;
    private static final int MAX_FILES=200000;
    private static final long MIB=1024L*1024L;
    /** Allow large Telegram exports when there is room; never fill user storage. */
    static long maxTemporaryCopyForFreeBytes(long freeBytes){
        if(freeBytes<=0)return 0; // no confirmed free space: never risk filling storage
        long reserve=Math.max(256L*MIB,freeBytes/10L);
        return Math.min(MAX_TOTAL,Math.max(0,freeBytes-reserve));
    }
    static final class Budget {
        long total; int count;
        int sevenZDeclared=-1,sevenZHtml=-1,sevenZHtmlCandidates=-1;
        String sevenZReadMode="";
        long sevenZSourceBytes=-1;
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
            if("zip".equalsIgnoreCase(suffix)){
                // Zip4j handles plain ZIP and encrypted ZipCrypto/AES using the same
                // extraction path. A password is requested only for encrypted entries.
                try{
                    extractZipWithPassword(ctx,uri,root,password,budget,progress);
                }catch(Exception primary){
                    if(!unsupportedZipCodec(primary))throw primary;
                    progress.update("Использую дополнительный распаковщик ZIP…");
                    deleteTree(root);
                    if(!root.mkdirs())throw new IOException("Не удалось подготовить временную папку",primary);
                    budget.count=0;budget.total=0;
                    try{
                        extractExtendedZip(ctx,uri,root,password,budget,progress);
                    }catch(Exception fallback){fallback.addSuppressed(primary);throw fallback;}
                }
            }else if("7z".equalsIgnoreCase(suffix)){
                extractSevenZ(ctx,uri,root,password,budget,progress);
            }else if("rar".equalsIgnoreCase(suffix)){
                extractRar(ctx,uri,root,password,budget,progress);
            }
            else throw new IOException("Ожидался ZIP, 7z или RAR");
            progress.update("Распаковка завершена. Ищу HTML переписки…");
            File selected;
            try{selected=findChatRoot(root,0);}
            catch(ExportNotFound notFound){
                if(budget.sevenZDeclared>=0)
                    throw new ExportNotFound(notFound.getMessage()+"\n7z: записей в заголовке "+budget.sevenZDeclared+
                        ", стандартных HTML в заголовке "+budget.sevenZHtml+
                        ", обработано записей "+budget.count+
                        ", распаковано байт "+budget.total+
                        ", размер прочитанного архива "+budget.sevenZSourceBytes+" байт; чтение: "+budget.sevenZReadMode+".");
                throw notFound;
            }
            progress.update("Распаковано файлов: "+budget.count+"; найден экспорт Telegram");
            return selected;
        }catch(Exception fail){
            deleteTree(root);
            if((password==null||password.length==0)&&isPasswordFailure(fail))
                throw new PasswordRequired(suffix.toUpperCase(Locale.ROOT)+
                    " защищён паролем. Введи пароль от архива.",fail);
            throw fail;
        }
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
                    String raw=entry.getName();
                    budget.next(raw==null?"":raw);
                    String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
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
    private static void extractZipWithPassword(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        // Each reader gets its own password copy; no retry depends on a reader's internal buffer.
        char[] attempt=password==null?null:password.clone();
        try{
            extractZipStreaming(ctx,uri,root,attempt,budget,progress);
        }catch(net.lingala.zip4j.exception.ZipException|java.util.zip.ZipException streamFailure){
            if((password==null||password.length==0)&&isPasswordFailure(streamFailure))
                throw streamFailure;
            // A streaming decoder may misread a local header or data descriptor.
            // Even WRONG_PASSWORD can be an incorrect result for unusual ZIP headers.
            // Confirm via the central directory once, including when no password was
            // supplied and the local header could not be parsed.
            progress.update("Проверяю ZIP по центральному каталогу…");
            deleteTree(root);
            if(!root.mkdirs())throw new IOException("Не удалось очистить временную папку ZIP",streamFailure);
            budget.count=0;budget.total=0;
            char[] retry=password==null?null:password.clone();
            try{
                extractZipFromCentralDirectory(ctx,uri,root,retry,budget,progress);
            }catch(Exception recoveryFailure){
                recoveryFailure.addSuppressed(streamFailure);
                throw recoveryFailure;
            }finally{if(retry!=null)Arrays.fill(retry,'\0');}
        }finally{if(attempt!=null)Arrays.fill(attempt,'\0');}
    }
    private static boolean unsupportedZipCodec(Throwable error){
        for(Throwable e=error;e!=null;e=e.getCause()){
            String message=e.getMessage()==null?"":e.getMessage().toLowerCase(Locale.ROOT);
            if((message.contains("compression")||message.contains("compress method"))&&
                (message.contains("unsupported")||message.contains("not supported")||message.contains("unknown")))return true;
            for(Throwable suppressed:e.getSuppressed()) {
                String detail=suppressed.getMessage()==null?"":suppressed.getMessage().toLowerCase(Locale.ROOT);
                if(detail.contains("compression")&&(detail.contains("unsupported")||detail.contains("not supported")||detail.contains("unknown")))return true;
            }
        }
        return false;
    }
    /** Additional decoder only for unsupported codecs; never bypasses authentication or path policy.
     * Commons Compress reads the central directory for independent entry metadata.
     * The alternative decoder provides streams; it is never allowed to extract paths itself. */
    private static void extractExtendedZip(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        File temporary=File.createTempFile("telegram-zip-codec-", ".zip",ctx.getCacheDir());
        char[] secret=password==null?null:password.clone();
        try{
            long limit=maxTemporaryCopyForFreeBytes(ctx.getCacheDir().getUsableSpace()),copied=0;
            try(InputStream source=ctx.getContentResolver().openInputStream(uri);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(temporary))){
                if(source==null)throw new IOException("Не удалось открыть исходный ZIP");
                byte[] buf=new byte[128*1024];int n;
                while((n=source.read(buf))!=-1){
                    copied+=n;if(copied>limit)throw new IOException("Недостаточно свободного места для дополнительного распаковщика ZIP");
                    out.write(buf,0,n);
                }
            }
            // Do not accept split archives, links, duplicate normalized paths,
            // or entries that the alternate parser silently omits.
            Map<String,Long> files=new LinkedHashMap<>();
            Set<String> all=new HashSet<>();
            try(org.apache.commons.compress.archivers.zip.ZipFile metadata=
                    org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(temporary).get()){
                Enumeration<org.apache.commons.compress.archivers.zip.ZipArchiveEntry> entries=metadata.getEntries();
                while(entries.hasMoreElements()){
                    org.apache.commons.compress.archivers.zip.ZipArchiveEntry entry=entries.nextElement();
                    String raw=entry.getName();budget.next(raw==null?"":raw);
                    String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
                    if(entry.isUnixSymlink())throw new IOException("Ссылки внутри ZIP не поддерживаются");
                    if(!all.add(name))throw new IOException("Повторное имя в ZIP");
                    if(entry.getGeneralPurposeBit().usesEncryption()&&(secret==null||secret.length==0))
                        throw new PasswordRequired("ZIP защищён паролем. Введи пароль от архива.");
                    if(entry.isDirectory())continue;
                    long size=entry.getSize();
                    if(size<0||size>MAX_ENTRY)throw new IOException("Некорректный размер файла в ZIP");
                    files.put(name,size);
                }
            }
            ru.olegcherednik.zip4jvm.UnzipIt unzip=ru.olegcherednik.zip4jvm.UnzipIt.zip(temporary.toPath());
            if(secret!=null&&secret.length>0)unzip.password(secret);
            ru.olegcherednik.zip4jvm.ZipFile.Reader reader=unzip.open();
            if(reader.isSplit())throw new IOException("Многотомный ZIP не поддерживается");
            byte[] buf=new byte[128*1024];
            for(ru.olegcherednik.zip4jvm.ZipFile.Entry entry:reader){
                String name=ArchiveImportPath.normalize(entry.getName(),entry.isDir());
                if(entry.isSymlink())throw new IOException("Ссылки внутри ZIP не поддерживаются");
                if(entry.isDir()){
                    if(!all.contains(name))throw new IOException("Каталог ZIP отсутствует в исходном списке");
                    continue;
                }
                Long size=files.remove(name);
                if(size==null||entry.getUncompressedSize()!=size)throw new IOException("Несогласованный список файлов ZIP");
                File target=safeFile(root,name);
                File parent=target.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не создана папка ZIP");
                if(target.exists())throw new IOException("Повторное имя в ZIP");
                long written=0;
                // Iteration exposes metadata only; extract(name) assigns the configured password.
                // Still stream into our own checked output path, never call extract(Path).
                try(InputStream in=reader.extract(entry.getName()).getInputStream();OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                    int n;while((n=in.read(buf))!=-1){written+=n;if(written>size)throw new IOException("Размер ZIP не совпадает");budget.add(n,written);out.write(buf,0,n);}
                }
                if(written!=size)throw new IOException("Файл ZIP распакован не полностью");
                emit(budget,progress);
            }
            if(!files.isEmpty())throw new IOException("Дополнительный распаковщик пропустил файлы ZIP");
        }finally{
            if(secret!=null)Arrays.fill(secret,'\0');
            temporary.delete();
        }
    }
    private static void extractZipStreaming(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        try(InputStream source=ctx.getContentResolver().openInputStream(uri)){
            if(source==null)throw new IOException("Нет доступа к ZIP");
            try(net.lingala.zip4j.io.inputstream.ZipInputStream zip=
                    new net.lingala.zip4j.io.inputstream.ZipInputStream(
                        new BufferedInputStream(source,128*1024),password)){
                net.lingala.zip4j.model.LocalFileHeader entry;
                byte[] bytes=new byte[128*1024];
                while((entry=zip.getNextEntry())!=null){
                    String raw=entry.getFileName();
                    budget.next(raw==null?"":raw);
                    String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
                    if(entry.isEncrypted()&&(password==null||password.length==0))
                        throw new PasswordRequired("ZIP защищён паролем. Введи пароль от архива.");
                    if(entry.isDirectory())continue;
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
                    emit(budget,progress);
                }
            }
        }
    }
    /**
     * Central-directory fallback for password-protected ZIPs with unusual
     * headers. A SAF provider may return a pipe rather than a seekable file;
     * the bounded private temporary copy is deleted even if decoding fails.
     */
    static void extractZipFromCentralDirectory(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        File temporary=File.createTempFile("telegram-zip-retry-", ".zip",ctx.getCacheDir());
        try{
            final long copyLimit=maxTemporaryCopyForFreeBytes(ctx.getCacheDir().getUsableSpace());
            long copied=0;
            try(InputStream source=ctx.getContentResolver().openInputStream(uri);
                OutputStream destination=new BufferedOutputStream(new FileOutputStream(temporary))){
                if(source==null)throw new IOException("Повторно открыть ZIP не удалось");
                byte[] buffer=new byte[128*1024];int n;
                while((n=source.read(buffer))!=-1){
                    copied+=n;
                    if(copied>copyLimit)
                        throw new IOException("Недостаточно свободного места для временного открытия большого ZIP. Освободи место на телефоне и повтори. Исходный файл не изменён.");
                    destination.write(buffer,0,n);
                }
            }
            try(net.lingala.zip4j.ZipFile archive=new net.lingala.zip4j.ZipFile(temporary,password)){
                List<net.lingala.zip4j.model.FileHeader> entries=archive.getFileHeaders();
                byte[] buffer=new byte[128*1024];
                for(net.lingala.zip4j.model.FileHeader entry:entries){
                    String raw=entry.getFileName();
                    budget.next(raw==null?"":raw);
                    String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
                    if(entry.isEncrypted()&&(password==null||password.length==0))
                        throw new PasswordRequired("ZIP защищён паролем. Введи пароль от архива.");
                    if(entry.isDirectory())continue;
                    if(entry.getUncompressedSize()>MAX_ENTRY)
                        throw new IOException("Слишком большой файл в ZIP");
                    File target=safeFile(root,name);
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())
                        throw new IOException("Не создана папка ZIP");
                    if(target.exists())throw new IOException("Повторное имя в ZIP");
                    long written=0;
                    try(InputStream input=archive.getInputStream(entry);
                        OutputStream output=new BufferedOutputStream(new FileOutputStream(target))){
                        int n;
                        while((n=input.read(buffer))!=-1){
                            written+=n;budget.add(n,written);
                            output.write(buffer,0,n);
                        }
                    }
                    emit(budget,progress);
                }
            }
        }finally{
            if(!temporary.delete())temporary.deleteOnExit();
        }
    }
    private static void extractSevenZ(Context ctx,Uri uri,File root,char[] password,
            Budget budget,Progress progress)throws Exception{
        try{
            android.os.ParcelFileDescriptor descriptor=null;
            try{descriptor=ctx.getContentResolver().openFileDescriptor(uri,"r");}
            catch(java.io.FileNotFoundException unavailable){ /* stream-only provider */ }
            if(descriptor!=null){
                // One owner of the descriptor; do not close a raw fd twice.
                try(FileInputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)){
                    boolean seekable;
                    try{input.getChannel().position(0);input.getChannel().size();seekable=true;}
                    catch(IOException|UnsupportedOperationException notSeekable){seekable=false;}
                    if(seekable){
                        budget.sevenZReadMode="прямое";
                        readSevenZ(input.getChannel(),root,password,budget,progress);
                        if(budget.sevenZHtmlCandidates!=0||!"content".equalsIgnoreCase(uri.getScheme()))
                            return;
                        // Only after full successful decoding with no HTML at all,
                        // retry a content URI once through its stream interface.
                        // No password/integrity/path failure triggers this retry.
                    }
                }
                if(budget.sevenZDeclared>=0){
                    progress.update("В заголовке 7z нет HTML. Проверяю тот же файл через совместимый режим…");
                    deleteTree(root);
                    if(!root.mkdirs())throw new IOException("Не удалось подготовить повторное чтение 7z");
                    budget.count=0;budget.total=0;
                    budget.sevenZDeclared=-1;budget.sevenZHtml=-1;budget.sevenZHtmlCandidates=-1;
                }
            }
            File temporary=File.createTempFile("telegram-sevenz-", ".7z",ctx.getCacheDir());
            try{
                final long copyLimit=maxTemporaryCopyForFreeBytes(ctx.getCacheDir().getUsableSpace());
                long copied=0;
                try(InputStream input=ctx.getContentResolver().openInputStream(uri);
                    OutputStream output=new BufferedOutputStream(new FileOutputStream(temporary))){
                    if(input==null)throw new IOException("Не удалось повторно открыть 7z");
                    byte[] block=new byte[128*1024];int count;
                    while((count=input.read(block))!=-1){
                        copied+=count;
                        if(copied>copyLimit)throw new IOException("Недостаточно свободного места для совместимого открытия 7z. Исходный архив не изменён.");
                        output.write(block,0,count);
                    }
                }
                try(FileInputStream local=new FileInputStream(temporary)){
                    budget.sevenZReadMode="совместимое";
                    readSevenZ(local.getChannel(),root,password,budget,progress);
                }
            }finally{temporary.delete();}
        }catch(Exception failure){
            if((password==null||password.length==0)&&isPasswordFailure(failure))
                throw new PasswordRequired("7z защищён паролем. Введи пароль от архива.",failure);
            throw failure;
        }
    }
    private static void readSevenZ(java.nio.channels.SeekableByteChannel channel,File root,
            char[] password,Budget budget,Progress progress)throws Exception{
        try(org.apache.commons.compress.archivers.sevenz.SevenZFile archive=
                org.apache.commons.compress.archivers.sevenz.SevenZFile.builder()
                    .setSeekableByteChannel(channel).setPassword(password)
                    .setMaxMemoryLimitKiB(131072).get()){
            // Compare the decoder's header inventory with actual extraction;
            // a successful return must not hide missing or truncated entries.
            int declared=0,canonical=0,htmlCandidates=0;
            long declaredBytes=0;
            for(org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry header:archive.getEntries()){
                if(++declared>MAX_FILES)throw new IOException("Слишком много файлов в 7z");
                if(header.isAntiItem())throw new IOException("7z содержит записи удаления, не обычный экспорт Telegram");
                String name=ArchiveImportPath.normalize(header.getName(),header.isDirectory());
                if(header.isDirectory())continue;
                long size=header.getSize();
                if(size<0||size>MAX_ENTRY||declaredBytes>MAX_TOTAL-size)
                    throw new IOException("Лимит безопасной распаковки 7z превышен");
                declaredBytes+=size;
                String leaf=new File(name).getName();
                if(TelegramExportHtml.canonical(leaf))canonical++;
                if(TelegramExportHtml.candidate(leaf))htmlCandidates++;
            }
            budget.sevenZDeclared=declared;budget.sevenZHtml=canonical;budget.sevenZHtmlCandidates=htmlCandidates;
            budget.sevenZSourceBytes=channel.size();
            progress.update("7z: "+declared+" записей, "+canonical+" стандартных HTML. Распаковываю…");
            org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry entry;
            byte[] bytes=new byte[128*1024];
            while((entry=archive.getNextEntry())!=null){
                String raw=entry.getName();budget.next(raw==null?"":raw);
                String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
                if(entry.isDirectory())continue;
                if(entry.getSize()>MAX_ENTRY)throw new IOException("7z содержит слишком большой файл");
                File target=safeFile(root,name);
                File parent=target.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не создана папка 7z");
                if(target.exists())throw new IOException("Повторное имя в 7z");
                long written=0;
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                    int count;while((count=archive.read(bytes))!=-1){
                        written+=count;
                        if(written>entry.getSize())throw new IOException("Размер распакованного файла 7z превышает объявленный");
                        budget.add(count,written);out.write(bytes,0,count);
                    }
                }
                if(written!=entry.getSize())throw new IOException("Файл 7z распакован не полностью");
                emit(budget,progress);
            }
            if(budget.count!=declared||budget.total!=declaredBytes)
                throw new IOException("7z распакован не полностью: обработано "+budget.count+" из "+declared+" записей");
        }
    }
    private static void extractRar(Context ctx,Uri uri,File root,char[] password,Budget budget,Progress progress) throws Exception{
        // Junrar needs random access for efficient RAR5/solid decoding. Keep a temporary copy
        // until import has finished; original remains at its selected SAF location.
        File compressed=File.createTempFile("telegram-", ".rar",ctx.getCacheDir());
        try{
            // RAR also needs a seekable temporary copy. Apply the same free-space
            // reserve as ZIP/7z rather than relying on the 60 GiB archive limit:
            // otherwise a large import could consume nearly all phone storage.
            final long copyLimit=maxTemporaryCopyForFreeBytes(ctx.getCacheDir().getUsableSpace());
            try(InputStream source=ctx.getContentResolver().openInputStream(uri);
                OutputStream out=new BufferedOutputStream(new FileOutputStream(compressed))){
                if(source==null)throw new IOException("RAR недоступен");
                byte[] buffer=new byte[128*1024];int n;
                long archiveBytes=0;
                while((n=source.read(buffer))!=-1){
                    archiveBytes+=n;
                    if(archiveBytes>copyLimit)
                        throw new IOException("Недостаточно свободного места для временного открытия RAR. Освободи место на телефоне; исходный архив не изменён.");
                    out.write(buffer,0,n);
                }
            }
            // Junrar supports password-protected RAR4/RAR5 entries and encrypted
            // RAR5 headers. Keep authentication within the bounded extractor.
            try(Archive rar=new Archive(compressed,ArchiveOptions.builder()
                    .password(password).maxDictionarySize(128L*1024*1024).build())){
                for(FileHeader entry:rar.getFileHeaders()){
                    // Even skipped directories and links consume the bounded entry budget.
                    String raw=entry.getFileName();
                    budget.next(raw==null?"":raw);
                    String name=ArchiveImportPath.normalize(raw,entry.isDirectory());
                    if(entry.isDirectory())continue;
                    if(entry.getRedirection()!=null)continue; // never materialize archive links
                    if(entry.isEncrypted()&&(password==null||password.length==0))
                        throw new PasswordRequired("RAR защищён паролем. Введи пароль от архива.");
                    File target=safeFile(root,name);
                    if(entry.getFullUnpackSize()>MAX_ENTRY)throw new IOException("Слишком большой файл в RAR");
                    File parent=target.getParentFile();
                    if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Не удалось создать каталог RAR");
                    if(target.exists())throw new IOException("Повторный файл в RAR");
                    try(OutputStream rawOutput=new BufferedOutputStream(new FileOutputStream(target))){
                        OutputStream bounded=new FilterOutputStream(rawOutput){
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
    private static File findChatRoot(File folder,int depth)throws IOException{
        Set<File> canonical=new LinkedHashSet<>();
        List<File> otherHtml=new ArrayList<>();
        ArrayDeque<File> pending=new ArrayDeque<>();
        pending.add(folder);
        int files=0,html=0,nested=0;
        while(!pending.isEmpty()){
            File[] items=pending.removeLast().listFiles();
            if(items==null)throw new ExportNotFound("Не удалось прочитать распакованную папку.");
            for(File f:items){
                if(f.isDirectory()){pending.add(f);continue;}
                if(!f.isFile())continue;
                files++;
                String n=f.getName().toLowerCase(Locale.ROOT);
                if(n.endsWith(".zip")||n.endsWith(".7z")||n.endsWith(".rar"))nested++;
                if(TelegramExportHtml.candidate(n)){
                    html++;
                    if(TelegramExportHtml.canonical(n))canonical.add(f.getParentFile());
                    else if(otherHtml.size()<256)otherHtml.add(f);
                }
            }
        }
        if(canonical.size()>1)throw new ExportNotFound("В архиве найдено несколько переписок. Выбери архив одной переписки; данные не изменены.");
        if(canonical.size()==1)return canonical.iterator().next();
        if(otherHtml.size()<html)throw new ExportNotFound("Слишком много HTML без стандартных имён для безопасного автоматического выбора: "+html+". Выбери нужный HTML вручную.");
        File selected=null;
        for(File f:otherHtml){
            try(InputStream in=new BufferedInputStream(new FileInputStream(f))){
                if(!TelegramExportHtml.recognized(in))continue;
            }
            if(selected!=null)throw new ExportNotFound("Найдено несколько HTML переписок с нестандартными именами. Выбери нужный HTML вручную.");
            selected=f;
        }
        if(selected!=null)return selected.getParentFile();
        throw new ExportNotFound("Архив распакован, но HTML переписки не найден. Файлов: "+files+
            "; HTML: "+html+"; вложенных ZIP/7z/RAR: "+nested+". "+
            (nested>0?"Возможно, переписка находится во вложенном архиве. Выбери его отдельно.":"Проверь имя HTML, который открывается после ручной распаковки."));
    }
    static void deleteTree(File file){
        if(file==null)return;
        if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File c:children)deleteTree(c);}
        file.delete();
    }
}
