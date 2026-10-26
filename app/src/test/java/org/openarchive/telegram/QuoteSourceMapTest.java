package org.openarchive.telegram;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuoteSourceMapTest {
    private static QuoteSourceMap.Entry m(long id){return new QuoteSourceMap.Entry(id,"A","2024-01-01","hello");}
    @Test public void anchorConfirmedOnlyWithExactId(){
        QuoteSourceMap.Context c=new QuoteSourceMap.Context(50,m(50),Arrays.asList(m(2),m(40)),Arrays.asList(m(70),m(80)));
        assertTrue(c.found);assertEquals(5,c.messages.size());assertEquals(2,c.sourceIndex);
        assertEquals("● Исходное сообщение",c.positionLabel(2));
        assertEquals("↑ Ранее в архиве",c.positionLabel(0));
        assertEquals("↓ Позже в архиве",c.positionLabel(4));
    }
    @Test public void missingAnchorNeverInventsNeighbours(){
        QuoteSourceMap.Context c=new QuoteSourceMap.Context(50,null,Arrays.asList(m(40)),Arrays.asList(m(60)));
        assertFalse(c.found);assertTrue(c.messages.isEmpty());assertEquals(-1,c.sourceIndex);
    }
    @Test public void rejectsMismatchedOrOutOfOrderIds(){
        try{new QuoteSourceMap.Context(50,m(51),null,null);fail();}catch(IllegalArgumentException expected){}
        try{new QuoteSourceMap.Context(50,m(50),Arrays.asList(m(40),m(30)),null);fail();}catch(IllegalArgumentException expected){}
        try{new QuoteSourceMap.Context(50,m(50),null,Arrays.asList(m(60),m(60)));fail();}catch(IllegalArgumentException expected){}
        try{new QuoteSourceMap.Context(50,m(50),Arrays.asList(m(1),m(2),m(3)),null);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void resultIsImmutable(){
        QuoteSourceMap.Context c=new QuoteSourceMap.Context(3,m(3),Collections.emptyList(),Collections.emptyList());
        try{c.messages.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void neverSplitsSupplementaryUnicodeAtPreviewLimit(){
        String s="🎉".repeat(140)+"Z";
        String p=QuoteSourceMap.preview(s);
        assertEquals(140,p.codePointCount(0,p.length())-1);assertTrue(p.endsWith("…"));
        assertEquals("(сообщение без текста)",QuoteSourceMap.preview(null));
    }
}
