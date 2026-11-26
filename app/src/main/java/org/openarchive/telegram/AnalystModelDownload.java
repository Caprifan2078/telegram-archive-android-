package org.openarchive.telegram;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;

/** Fixed public model only. No chat, question, token, or user data enters network request. */
final class AnalystModelDownload {
    static final String URL="https://huggingface.co/litert-community/Qwen3-0.6B/resolve/dd97997951bb15a2a71f539ba17f604707c0b11a/Qwen3-0.6B.litertlm";
    static final long SIZE=614236160L;
    static final String SHA="555579ff2f4fd13379abe69c1c3ab5200f7338bc92471557f1d6614a6e5ab0b4";
    static boolean allowed(URI u){
        String h=u.getHost();
        return "https".equalsIgnoreCase(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443)&&h!=null&&
            (h.equals("huggingface.co")||h.equals("cdn-lfs.huggingface.co")||h.equals("cdn-lfs-us-1.huggingface.co")||
             h.equals("cdn-lfs.hf.co")||h.equals("cdn-lfs-us-1.hf.co")||h.equals("cas-bridge.xethub.hf.co"));
    }
    static void fetch(File model,AtomicBoolean cancel,LongConsumer progress)throws Exception{
        File dir=model.getParentFile();if(dir==null||dir.getUsableSpace()<SIZE+128L*1024*1024)throw new IOException("Not enough storage");
        File tmp=File.createTempFile("analyst-download-",".tmp",dir);
        HttpURLConnection conn=null;
        try{
            URI url=URI.create(URL);
            for(int redirects=0;;redirects++){
                if(cancel.get()||redirects>5||!allowed(url))throw new IOException("Blocked redirect");
                conn=(HttpURLConnection)url.toURL().openConnection();
                conn.setInstanceFollowRedirects(false);conn.setConnectTimeout(20000);conn.setReadTimeout(20000);
                conn.setRequestMethod("GET");conn.setRequestProperty("Accept-Encoding","identity");
                int code=conn.getResponseCode();
                if(code>=300&&code<400){
                    String target=conn.getHeaderField("Location");conn.disconnect();conn=null;
                    if(target==null)throw new IOException("Missing redirect");url=url.resolve(target);continue;
                }
                if(code!=200)throw new IOException("HTTP "+code);break;
            }
            long expected=conn.getContentLengthLong();if(expected>=0&&expected!=SIZE)throw new IOException("Wrong size");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new BufferedInputStream(conn.getInputStream());FileOutputStream out=new FileOutputStream(tmp)){
                byte[] buffer=new byte[262144];long total=0,last=0;int n;
                while(!cancel.get()&&(n=in.read(buffer))!=-1){
                    total+=n;if(total>SIZE)throw new IOException("Size overflow");
                    sha.update(buffer,0,n);out.write(buffer,0,n);
                    if(total-last>=2L*1024*1024){progress.accept(total);last=total;}
                }
                if(cancel.get()||total!=SIZE)throw new IOException("Incomplete");
                out.getFD().sync();
            }
            StringBuilder hex=new StringBuilder();for(byte b:sha.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(!SHA.equals(hex.toString())||cancel.get())throw new IOException("Integrity check failed");
            java.nio.file.Files.move(tmp.toPath(),model.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }finally{if(conn!=null)conn.disconnect();if(tmp.exists())tmp.delete();}
    }
}
