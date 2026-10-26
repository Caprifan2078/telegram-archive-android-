package org.openarchive.telegram;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.parser.StreamParser;
import org.jsoup.select.Evaluator;
import org.jsoup.select.Selector;

/** Progressive Telegram HTML reader. Callers must consume each element before next(). */
final class TelegramHtmlStream implements Closeable {
    // Limit a single message/header/footer, not the size of an export or HTML part.
    static final int MAX_SEGMENT_CHARS = 8 * 1024 * 1024;
    private final SegmentReader input;
    private final StreamParser parser;
    private final Evaluator messages=Selector.evaluatorOf(
        "div.history > div.message, div.message.default, div.message.service");
    private Element previous;
    private boolean initialized;

    TelegramHtmlStream(InputStream stream) {
        this(new InputStreamReader(stream,StandardCharsets.UTF_8),MAX_SEGMENT_CHARS);
    }
    TelegramHtmlStream(Reader reader,int segmentLimit) {
        input=new SegmentReader(reader,segmentLimit);
        parser=new StreamParser(Parser.htmlParser());
    }
    Element next() throws IOException {
        if(previous!=null){
            // Telegram inserts whitespace between messages; release it as well.
            while(previous.previousSibling()!=null)previous.previousSibling().remove();
            previous.remove();previous=null;input.resetSegment();
        }
        try{
            if(!initialized){parser.parse(input,"");initialized=true;}
            previous=parser.selectNext(messages);
            return previous;
        }catch(UncheckedIOException failure){throw failure.getCause();}
    }
    @Override public void close(){parser.close();previous=null;}

    private static final class SegmentReader extends Reader {
        private final Reader delegate;private final int limit;private int consumed;
        SegmentReader(Reader delegate,int limit){
            if(limit<1)throw new IllegalArgumentException("segment limit");
            this.delegate=delegate;this.limit=limit;
        }
        void resetSegment(){consumed=0;}
        @Override public int read(char[] chars,int offset,int length)throws IOException{
            if(length==0)return 0;
            int count=delegate.read(chars,offset,Math.min(length,limit-consumed+1));
            if(count>0){consumed+=count;if(consumed>limit)throw new IOException(
                "Слишком большой HTML-фрагмент без границы сообщения. Разделите экспорт на меньшие части; прежний архив сохранён.");}
            return count;
        }
        @Override public void close()throws IOException{delegate.close();}
    }
}
