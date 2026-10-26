package org.openarchive.telegram;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/** Telegram .tgs = GZIP-compressed Lottie JSON. Fully offline; enforce input and inflation limits. */
final class TgsPayload {
    static final int MAX_COMPRESSED=256*1024;
    static final int MAX_JSON=2*1024*1024;
    private TgsPayload(){}
    static String decode(InputStream input) throws IOException {
        if(input==null)throw new IOException("TGS не найден");
        FilterInputStream bounded=new FilterInputStream(input){
            long readBytes=0;
            @Override public int read() throws IOException {
                int b=super.read();if(b>=0 && ++readBytes>MAX_COMPRESSED)throw new IOException("TGS слишком велик");return b;
            }
            @Override public int read(byte[] data,int offset,int size)throws IOException {
                int n=in.read(data,offset,size);
                if(n>0 && (readBytes+=n)>MAX_COMPRESSED)throw new IOException("TGS слишком велик");
                return n;
            }
        };
        try(GZIPInputStream gzip=new GZIPInputStream(bounded);
            ByteArrayOutputStream result=new ByteArrayOutputStream(64*1024)){
            byte[] block=new byte[8192];int n;
            while((n=gzip.read(block))!=-1){
                if(n==0)continue;
                if(result.size()+n>MAX_JSON)throw new IOException("TGS распаковывается слишком сильно");
                result.write(block,0,n);
            }
            // ByteArrayOutputStream.toString(Charset) is absent on older Android.
            return new String(result.toByteArray(),StandardCharsets.UTF_8);
        }
    }
}
