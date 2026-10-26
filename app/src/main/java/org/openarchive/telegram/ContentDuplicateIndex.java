package org.openarchive.telegram;

import java.io.InputStream;
import java.io.FilterInputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Explicit, read-only exact-content duplicate detection. No deletion or network access. */
final class ContentDuplicateIndex {
    static final long MAX_BYTES_PER_FILE=128L*1024*1024; // 128 MiB per file, bounded read-only scan.
    static final long MAX_TOTAL_BYTES=512L*1024*1024;
    /** A shared budget counts ALL bytes read, including damaged/oversized files. */
    static final class BudgetedInput extends FilterInputStream {
        private final AtomicLong total;
        private final long max;
        BudgetedInput(InputStream input,AtomicLong total,long max){super(input);this.total=total;this.max=max;}
        @Override public int read(byte[] data,int off,int len)throws IOException {
            if(len==0)return 0;
            long remaining=max-total.get();
            if(remaining<=0)throw new IOException("Достигнут общий лимит проверки");
            int n=super.read(data,off,(int)Math.min(len,remaining));
            if(n>0)total.addAndGet(n);
            return n;
        }
        @Override public int read()throws IOException {
            if(total.get()>=max)throw new IOException("Достигнут общий лимит проверки");
            int b=super.read();if(b>=0)total.incrementAndGet();return b;
        }
    }
    static final class Digest {
        final String hash; final long size;
        Digest(String hash,long size){this.hash=hash;this.size=size;}
    }
    static Digest fingerprint(InputStream stream,long cap) throws IOException {
        return fingerprint(stream,cap,()->false);
    }
    static Digest fingerprint(InputStream stream,long cap,java.util.function.BooleanSupplier cancel) throws IOException {
        try {
            MessageDigest dig=MessageDigest.getInstance("SHA-256");
            byte[] buf=new byte[64*1024];long length=0;
            for(int n;(n=stream.read(buf))!=-1;){
                if(Thread.currentThread().isInterrupted()||cancel.getAsBoolean())throw new IOException("Проверка остановлена");
                if(n==0)continue;
                length+=n;
                if(length>cap)throw new IOException("Файл превышает лимит анализа");
                dig.update(buf,0,n);
            }
            byte[] result=dig.digest();StringBuilder hex=new StringBuilder(64);
            for(byte b:result)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return new Digest(hex.toString(),length);
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    static final class Index {
        final Map<String,List<String>> byDigest=new HashMap<>();
        void add(String filename,Digest value){
            if(filename==null||value==null)return;
            String key=value.size+":"+value.hash;
            List<String> group=byDigest.get(key);
            if(group==null){group=new ArrayList<>();byDigest.put(key,group);}
            if(!group.contains(filename))group.add(filename);
        }
        List<List<String>> repeated(){
            List<List<String>> out=new ArrayList<>();
            for(List<String> group:byDigest.values())if(group.size()>1)out.add(group);
            out.sort((a,b)->Integer.compare(b.size(),a.size()));return out;
        }
    }
}
