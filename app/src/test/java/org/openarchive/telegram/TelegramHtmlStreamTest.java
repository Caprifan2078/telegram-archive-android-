package org.openarchive.telegram;

import java.io.*;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;

public class TelegramHtmlStreamTest {
    @Test public void unicodeMarkupRepliesAndMediaStayInsideMessage()throws Exception{
        String html="<div class='history'><div class='message default' id='message1'><div class='body'>"+
            "<div class='text'>Привет <b>мир</b> 😀<br>ещё строка</div>"+
            "<div class='reply_to'><a href='messages2.html#go_to_message2'>reply</a></div>"+
            "<a href='photos/a.jpg'>Photo</a></div></div>\n"+
            "<div class='message service'><div class='body'>Событие</div></div></div>";
        try(TelegramHtmlStream in=new TelegramHtmlStream(new StringReader(html),65536)){
            Element first=in.next();assertEquals("message1",first.id());
            assertTrue(first.selectFirst(".text").text().contains("Привет мир 😀"));
            assertEquals("messages2.html#go_to_message2",first.selectFirst(".reply_to a").attr("href"));
            assertEquals(1,TelegramImporter.findMedia(first).size());
            assertTrue(in.next().hasClass("service"));assertNull(first.parent());assertNull(in.next());
        }
    }
    @Test public void exportedFragmentsWithoutHistoryStillWork()throws Exception{
        try(TelegramHtmlStream in=new TelegramHtmlStream(new StringReader(
            "<div class='message default' id='message7'><div class='body'>ok</div></div>"),65536)){
            assertEquals("message7",in.next().id());assertNull(in.next());
        }
    }
    @Test public void malformedHugeSingleFragmentFailsExplicitly()throws Exception{
        try(TelegramHtmlStream in=new TelegramHtmlStream(new StringReader(
            "<div class='history'><div class='message default'>"+"x".repeat(8192)),1024)){
            try{in.next();fail("Oversized fragment accepted");}
            catch(IOException expected){assertTrue(expected.getMessage().contains("HTML"));}
        }
    }
    @Test public void readerFailureIsNotMistakenForEndOfArchive()throws Exception{
        Reader broken=new Reader(){
            public int read(char[] b,int o,int n)throws IOException{throw new IOException("synthetic read failure");}
            public void close(){}
        };
        try(TelegramHtmlStream in=new TelegramHtmlStream(broken,65536)){
            try{in.next();fail("I/O failure swallowed");}
            catch(IOException expected){assertEquals("synthetic read failure",expected.getMessage());}
        }
    }
    @Test public void manyMessagesReleaseNodesAndWhitespace()throws Exception{
        StringBuilder html=new StringBuilder("<div class='history'>");
        for(int i=0;i<10000;i++)html.append("\n<div class='message default' id='message").append(i)
            .append("'><div class='body'><div class='text'>sample</div></div></div>");
        html.append("</div>");int count=0;
        try(TelegramHtmlStream in=new TelegramHtmlStream(new StringReader(html.toString()),65536)){
            Element message;while((message=in.next())!=null){
                assertEquals("message"+count,message.id());
                assertTrue("Old messages retained",message.parent().childNodeSize()<8);count++;
            }
        }
        assertEquals(10000,count);
    }
}
