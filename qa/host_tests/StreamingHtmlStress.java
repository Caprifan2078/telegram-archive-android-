package org.openarchive.telegram;
import java.io.Reader;
import org.jsoup.nodes.Element;

/** Runs the production reader against generated HTML with a deliberately small JVM heap. */
public final class StreamingHtmlStress {
    public static void main(String[] args)throws Exception{
        final int total=args.length==0?1000000:Integer.parseInt(args[0]);
        Reader generated=new Reader(){
            int emitted=0,offset=0;String chunk="<html><body><div class='history'>";boolean ended;
            public int read(char[] out,int off,int len){
                if(len==0)return 0;int copied=0;
                while(copied<len){
                    if(offset==chunk.length()){
                        if(ended)break;
                        if(emitted<total)chunk="\n<div class='message default' id='message"+(emitted++)+
                            "'><div class='body'><div class='from_name'>Синтетический автор</div>"+
                            "<div class='text'>Тест 😀 &amp; текст</div><a href='photos/test.jpg'>Photo</a></div></div>";
                        else{chunk="</div></body></html>";ended=true;}
                        offset=0;
                    }
                    int n=Math.min(len-copied,chunk.length()-offset);
                    chunk.getChars(offset,offset+n,out,off+copied);offset+=n;copied+=n;
                }
                return copied==0?-1:copied;
            }
            public void close(){}
        };
        int count=0;long start=System.nanoTime();
        try(TelegramHtmlStream html=new TelegramHtmlStream(generated,TelegramHtmlStream.MAX_SEGMENT_CHARS)){
            Element m;while((m=html.next())!=null){
                if(!m.id().equals("message"+count))throw new AssertionError("Order at "+count);
                if(!m.selectFirst(".text").text().equals("Тест 😀 & текст"))throw new AssertionError("Text at "+count);
                if(m.parent().childNodeSize()>8)throw new AssertionError("Retained old nodes at "+count);
                count++;
            }
        }
        if(count!=total)throw new AssertionError("Expected "+total+", got "+count);
        System.out.println("PASS production streaming parser: messages="+count+", max_heap_bytes="+
            Runtime.getRuntime().maxMemory()+", seconds="+(System.nanoTime()-start)/1e9);
    }
}
