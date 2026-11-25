package org.openarchive.telegram;

import android.media.MediaDataSource;
import java.io.*;
import java.util.*;

/** Bounded random-access media in RAM, never a decrypted disk file. */
final class ProtectedMediaSource extends MediaDataSource {
    private static final int CHUNK=256*1024;
    private final List<byte[]> chunks=new ArrayList<>();
    private final long limit;
    private long size;
    private boolean closed,sealed;
    ProtectedMediaSource(long limit){
        if(limit<=0)throw new IllegalArgumentException("invalid limit");
        this.limit=limit;
    }
    static ProtectedMediaSource load(ProtectedArchiveSession session,String path,long limit)throws Exception{
        ProtectedMediaSource result=new ProtectedMediaSource(limit);
        try{
            session.copyEntry(path,result.sink(),limit);
            result.seal();
            return result;
        }catch(Exception|OutOfMemoryError error){result.close();throw error;}
    }
    OutputStream sink(){
        return new OutputStream(){
            @Override public void write(int b)throws IOException{byte[] a={(byte)b};append(a,0,1);}
            @Override public void write(byte[] b,int off,int len)throws IOException{append(b,off,len);}
        };
    }
    private synchronized void append(byte[] b,int off,int len)throws IOException{
        if(closed||sealed)throw new IOException("Media source is closed for writing");
        if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
        if(len>limit-size)throw new IOException("Media exceeds the protected session RAM limit");
        while(len>0){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            int index=(int)(size/CHUNK),offset=(int)(size%CHUNK);
            if(index==chunks.size())chunks.add(new byte[(int)Math.min(CHUNK,limit-size)]);
            byte[] dst=chunks.get(index);int n=Math.min(len,dst.length-offset);
            System.arraycopy(b,off,dst,offset,n);off+=n;len-=n;size+=n;
        }
    }
    synchronized void seal()throws IOException{
        if(closed||size==0)throw new IOException("Empty or closed media");
        sealed=true;
    }
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int count)throws IOException{
        if(closed||!sealed)throw new IOException("Media is locked");
        if(position<0||offset<0||count<0||offset>buffer.length-count)throw new IndexOutOfBoundsException();
        if(count==0)return 0;
        if(position>=size)return -1;
        int remaining=(int)Math.min(count,size-position),result=remaining;
        while(remaining>0){
            byte[] src=chunks.get((int)(position/CHUNK));
            int local=(int)(position%CHUNK),n=Math.min(remaining,src.length-local);
            System.arraycopy(src,local,buffer,offset,n);
            position+=n;offset+=n;remaining-=n;
        }
        return result;
    }
    @Override public synchronized long getSize()throws IOException{
        if(closed||!sealed)throw new IOException("Media is locked");
        return size;
    }
    @Override public synchronized void close(){
        if(closed)return;
        closed=true;
        for(byte[] chunk:chunks)Arrays.fill(chunk,(byte)0);
        chunks.clear();size=0;
    }
}
