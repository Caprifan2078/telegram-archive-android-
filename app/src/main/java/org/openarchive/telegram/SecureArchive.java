package org.openarchive.telegram;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Optional password protection for backup ZIP data, with authenticated 64 KiB records.
 * A separate authenticated final record detects truncated files. All data stays offline. */
final class SecureArchive {
    static final byte[] MAGIC={'T','G','A','R','C','H','0','2'};
    static final int CHUNK=64*1024,ITERATIONS=240000;
    private static final int TAG_BYTES=16;
    private static final SecureRandom RANDOM=new SecureRandom();

    private static SecretKey makeKey(char[] password,byte[] salt) throws Exception{
        if(password==null||password.length<8)throw new IOException("Для шифрования нужен пароль минимум из 8 символов");
        PBEKeySpec spec=new PBEKeySpec(password,salt,ITERATIONS,256);
        try{
            byte[] raw=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try{return new SecretKeySpec(raw,"AES");}finally{Arrays.fill(raw,(byte)0);}
        }finally{spec.clearPassword();}
    }
    static boolean isEncrypted(Context ctx,Uri uri) throws IOException{
        try(InputStream raw=ctx.getContentResolver().openInputStream(uri)){
            if(raw==null)throw new IOException("Архив недоступен");
            byte[] magic=new byte[8];
            int total=0,n;while(total<magic.length&&(n=raw.read(magic,total,magic.length-total))!=-1)total+=n;
            return total==8&&Arrays.equals(magic,MAGIC);
        }
    }
    static OutputStream encryptedOutput(OutputStream raw,char[] password) throws Exception{
        byte[] salt=new byte[16],nonce=new byte[8];RANDOM.nextBytes(salt);RANDOM.nextBytes(nonce);
        SecretKey key=makeKey(password,salt);
        DataOutputStream destination=new DataOutputStream(new BufferedOutputStream(raw,CHUNK));
        destination.write(MAGIC);destination.write(salt);destination.write(nonce);
        return new Encrypting(destination,key,nonce);
    }
    static InputStream openInput(Context ctx,Uri uri,char[] password) throws Exception{
        InputStream raw=ctx.getContentResolver().openInputStream(uri);
        if(raw==null)throw new IOException("Файл не открылся");
        return decryptedInput(raw,password);
    }
    static InputStream decryptedInput(InputStream raw,char[] password) throws Exception{
        BufferedInputStream buffered=new BufferedInputStream(raw,CHUNK);
        buffered.mark(40);
        byte[] magic=new byte[8];int read=0,n;
        while(read<8&&(n=buffered.read(magic,read,8-read))!=-1)read+=n;
        if(read!=8||!Arrays.equals(magic,MAGIC)){buffered.reset();return buffered;}
        if(password==null||password.length==0){buffered.close();throw new IOException("Архив зашифрован. Введи пароль.");}
        try{
            DataInputStream source=new DataInputStream(buffered);
            byte[] salt=new byte[16],nonce=new byte[8];source.readFully(salt);source.readFully(nonce);
            SecretKey key=makeKey(password,salt);
            return new Decrypting(source,key,nonce);
        }catch(Exception bad){buffered.close();throw bad;}
    }
    private static Cipher cipher(int mode,SecretKey key,byte[] prefix,int sequence)throws Exception{
        byte[] iv=Arrays.copyOf(prefix,12);
        iv[8]=(byte)(sequence>>>24);iv[9]=(byte)(sequence>>>16);iv[10]=(byte)(sequence>>>8);iv[11]=(byte)sequence;
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,key,new GCMParameterSpec(128,iv));
        cipher.updateAAD(MAGIC);
        cipher.updateAAD(new byte[]{iv[8],iv[9],iv[10],iv[11]});
        return cipher;
    }
    private static final class Encrypting extends OutputStream {
        private final DataOutputStream out;private final SecretKey key;private final byte[] nonce;
        private final byte[] pending=new byte[CHUNK];private int length,sequence;private boolean closed;
        Encrypting(DataOutputStream destination,SecretKey secret,byte[] prefix){out=destination;key=secret;nonce=prefix;}
        @Override public void write(int one)throws IOException{pending[length++]=(byte)one;if(length==CHUNK)flushChunk();}
        @Override public void write(byte[] bytes,int off,int len)throws IOException{
            if(closed)throw new IOException("Поток закрыт");
            if(bytes==null)throw new NullPointerException();
            if(off<0||len<0||off>bytes.length-len)throw new IndexOutOfBoundsException();
            while(len>0){int n=Math.min(len,CHUNK-length);System.arraycopy(bytes,off,pending,length,n);length+=n;off+=n;len-=n;if(length==CHUNK)flushChunk();}
        }
        private void flushChunk()throws IOException{
            if(length==0)return;
            try{
                byte[] data=cipher(Cipher.ENCRYPT_MODE,key,nonce,sequence++).doFinal(pending,0,length);
                out.writeInt(data.length);out.write(data);length=0;
            }catch(Exception e){throw new IOException("Ошибка шифрования",e);}
        }
        @Override public void flush()throws IOException{flushChunk();out.flush();}
        @Override public void close()throws IOException{
            if(closed)return;closed=true;
            try{
                flushChunk();
                byte[] authentication=cipher(Cipher.ENCRYPT_MODE,key,nonce,sequence++).doFinal(new byte[0]);
                out.writeInt(-1);out.write(authentication);out.flush();
            }catch(Exception failure){throw new IOException("Не удалось завершить защищённый архив",failure);}
            finally{Arrays.fill(pending,(byte)0);out.close();}
        }
    }
    private static final class Decrypting extends InputStream {
        private final DataInputStream in;private final SecretKey key;private final byte[] nonce;
        private byte[] data=new byte[0];private int offset,sequence;private boolean finished,closed;
        Decrypting(DataInputStream source,SecretKey secret,byte[] prefix){in=source;key=secret;nonce=prefix;}
        private void load() throws IOException{
            if(finished)return;
            try{
                int length=in.readInt();
                if(length==-1){
                    byte[] tag=new byte[TAG_BYTES];in.readFully(tag);
                    byte[] plain=cipher(Cipher.DECRYPT_MODE,key,nonce,sequence++).doFinal(tag);
                    if(plain.length!=0||in.read()!=-1)throw new IOException("Лишние данные после конца архива");
                    finished=true;data=new byte[0];return;
                }
                if(length<TAG_BYTES+1||length>CHUNK+TAG_BYTES)throw new IOException("Повреждён зашифрованный архив");
                byte[] encoded=new byte[length];in.readFully(encoded);
                data=cipher(Cipher.DECRYPT_MODE,key,nonce,sequence++).doFinal(encoded);
                if(data.length!=length-TAG_BYTES)throw new IOException("Неверная длина защищённого блока");
                offset=0;
            }catch(IOException exception){throw exception;}
            catch(Exception bad){throw new IOException("Пароль неверен либо архив повреждён",bad);}
        }
        @Override public int read()throws IOException{
            byte[] one=new byte[1];return read(one,0,1)<0?-1:one[0]&255;
        }
        @Override public int read(byte[] bytes,int off,int len)throws IOException{
            if(closed)throw new IOException("Поток закрыт");
            if(bytes==null)throw new NullPointerException();
            if(off<0||len<0||off>bytes.length-len)throw new IndexOutOfBoundsException();
            if(len==0)return 0;
            if(offset>=data.length){load();if(finished)return -1;}
            int n=Math.min(len,data.length-offset);System.arraycopy(data,offset,bytes,off,n);offset+=n;return n;
        }
        @Override public void close()throws IOException{closed=true;Arrays.fill(data,(byte)0);in.close();}
    }
}
