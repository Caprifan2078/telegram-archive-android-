package org.openarchive.telegram;

import android.content.Context;
import android.media.MediaDataSource;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seekable cache for a single large attachment. Only authenticated ciphertext
 * reaches disk. Fresh key per object, unique nonce per block, no persisted key.
 */
final class ProtectedEncryptedMediaSource extends MediaDataSource {
    static final int BLOCK=1024*1024, TAG=16;
    private final File file;
    private final RandomAccessFile storage;
    private final byte[] key=new byte[32],noncePrefix=new byte[8];
    private final long limit;
    private byte[] pending=new byte[BLOCK],cached;
    private int used,blocks,cachedIndex=-1;
    private long size;
    private boolean closed,sealed;

    ProtectedEncryptedMediaSource(File directory,long limit)throws IOException{
        if(limit<=0||limit>12L*1024*1024*1024)throw new IOException("Invalid cache limit");
        this.limit=limit;
        SecureRandom random=new SecureRandom();random.nextBytes(key);random.nextBytes(noncePrefix);
        File target=File.createTempFile("protected-media-",".enc",directory);
        RandomAccessFile handle;
        try{handle=new RandomAccessFile(target,"rw");}
        catch(IOException e){target.delete();Arrays.fill(key,(byte)0);throw e;}
        file=target;storage=handle;
    }
    static ProtectedEncryptedMediaSource load(Context context,ProtectedArchiveSession session,String path,long limit)throws Exception{
        ProtectedEncryptedMediaSource result=new ProtectedEncryptedMediaSource(context.getCacheDir(),limit);
        try{
            session.copyEntry(path,result.sink(),limit);result.seal();return result;
        }catch(Exception|OutOfMemoryError e){result.close();throw e;}
    }
    private Cipher cipher(int mode,int block,int bytes)throws GeneralSecurityException{
        byte[] nonce=ByteBuffer.allocate(12).put(noncePrefix).putInt(block).array();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(ByteBuffer.allocate(8).putInt(block).putInt(bytes).array());
        return cipher;
    }
    OutputStream sink(){
        return new OutputStream(){
            @Override public void write(int b)throws IOException{byte[] one={(byte)b};append(one,0,1);}
            @Override public void write(byte[] b,int off,int len)throws IOException{append(b,off,len);}
        };
    }
    private synchronized void append(byte[] b,int off,int len)throws IOException{
        if(closed||sealed)throw new IOException("Cache is locked");
        if(off<0||len<0||off>b.length-len)throw new IndexOutOfBoundsException();
        if(len>limit-size)throw new IOException("Attachment exceeds cache limit");
        while(len>0){
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();
            int n=Math.min(len,BLOCK-used);
            System.arraycopy(b,off,pending,used,n);used+=n;size+=n;off+=n;len-=n;
            if(used==BLOCK)flushBlock();
        }
    }
    private void flushBlock()throws IOException{
        if(used==0)return;
        try{
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,blocks,used).doFinal(pending,0,used);
            storage.seek((long)blocks*(BLOCK+TAG));storage.write(encrypted);
            blocks++;used=0;
        }catch(GeneralSecurityException e){throw new IOException("Encrypted media cache failed",e);}
        finally{Arrays.fill(pending,(byte)0);}
    }
    synchronized void seal()throws IOException{
        if(closed||sealed||size==0)throw new IOException("Empty or locked cache");
        flushBlock();pending=null;sealed=true;
    }
    private void loadBlock(int index)throws IOException{
        if(cachedIndex==index&&cached!=null)return;
        if(cached!=null){Arrays.fill(cached,(byte)0);cached=null;}
        cachedIndex=-1;
        int length=(int)Math.min(BLOCK,size-(long)index*BLOCK);
        byte[] encrypted=new byte[length+TAG];
        storage.seek((long)index*(BLOCK+TAG));storage.readFully(encrypted);
        try{cached=cipher(Cipher.DECRYPT_MODE,index,length).doFinal(encrypted);cachedIndex=index;}
        catch(GeneralSecurityException e){throw new IOException("Encrypted media cache integrity check failed",e);}
    }
    @Override public synchronized int readAt(long position,byte[] buffer,int offset,int count)throws IOException{
        if(closed||!sealed)throw new IOException("Cache is locked");
        if(position<0||offset<0||count<0||offset>buffer.length-count)throw new IndexOutOfBoundsException();
        if(count==0)return 0;if(position>=size)return -1;
        int remain=(int)Math.min(count,size-position),result=remain;
        while(remain>0){
            int index=(int)(position/BLOCK),local=(int)(position%BLOCK);
            loadBlock(index);int n=Math.min(remain,cached.length-local);
            System.arraycopy(cached,local,buffer,offset,n);
            position+=n;offset+=n;remain-=n;
        }
        return result;
    }
    @Override public synchronized long getSize()throws IOException{
        if(closed||!sealed)throw new IOException("Cache is locked");return size;
    }
    File cacheFileForTest(){return file;}
    @Override public synchronized void close(){
        if(closed)return;closed=true;
        Arrays.fill(key,(byte)0);Arrays.fill(noncePrefix,(byte)0);
        if(pending!=null)Arrays.fill(pending,(byte)0);
        if(cached!=null)Arrays.fill(cached,(byte)0);
        pending=null;cached=null;size=0;
        try{storage.close();}catch(IOException ignored){}
        file.delete();
    }
}
