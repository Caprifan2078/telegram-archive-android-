package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuoteFormatterTest {
    @Test public void entireTextWhenNoSelection(){
        assertEquals("добрый день",QuoteFormatter.excerpt(" добрый день ",-1,-1));
        assertEquals("целое сообщение",QuoteFormatter.excerpt("целое сообщение",4,4));
    }
    @Test public void selectedFragmentAndMultiline(){
        assertEquals("часть",QuoteFormatter.excerpt("Вот часть сообщения",4,9));
        assertTrue(QuoteFormatter.format("первое\nвторое","Анна","2026-10-24","Диалог").contains("│ второе\n"));
    }
    @Test public void avoidSpoofingWithMetadataLines(){
        String result=QuoteFormatter.format("слова","Имя\nфальшивое","сегодня\nзавтра","чат");
        assertEquals(1,result.chars().filter(c->c=='\n').count());
        assertTrue(result.endsWith("чат"));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectEmpty(){QuoteFormatter.format("   ","a","d","c");}
    @Test public void clipHugeQuote(){
        String huge="x".repeat(10000);
        assertTrue(QuoteFormatter.format(huge,"a","b","c").length()<4400);
    }
}