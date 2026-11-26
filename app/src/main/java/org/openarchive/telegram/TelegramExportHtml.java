package org.openarchive.telegram;

import java.io.*;
import java.util.Locale;
import org.jsoup.nodes.Element;

/** Shared recognition of message files in archives and ordinary folders. */
final class TelegramExportHtml {
    private static final int PROBE_BYTES=2*1024*1024;
    private TelegramExportHtml(){}
    static boolean canonical(String name){
        return name!=null&&name.matches("(?i)^messages\\d*\\.html$");
    }
    static boolean candidate(String name){
        if(name==null)return false;
        String lower=name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html")||lower.endsWith(".htm");
    }
    // A renamed page must contain a real Telegram message, not an index,
    // a generic HTML page or an arbitrary attachment with an HTML extension.
    static boolean recognized(InputStream source)throws IOException{
        if(source==null)return false;
        try(InputStream bounded=new FilterInputStream(source){
            private int remaining=PROBE_BYTES;
            @Override public int read()throws IOException{
                if(remaining<=0)return -1;int value=super.read();if(value>=0)remaining--;return value;
            }
            @Override public int read(byte[] bytes,int offset,int length)throws IOException{
                if(length==0)return 0;if(remaining<=0)return -1;
                int n=super.read(bytes,offset,Math.min(length,remaining));if(n>0)remaining-=n;return n;
            }
        };TelegramHtmlStream stream=new TelegramHtmlStream(bounded)){
            for(int i=0;i<8;i++){
                Element e=stream.next();if(e==null)return false;
                if(e.id().matches("message-?\\d+")&&e.selectFirst(".body")!=null)return true;
            }
            return false;
        }
    }
}
