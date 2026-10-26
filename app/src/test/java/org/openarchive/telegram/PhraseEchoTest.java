package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class PhraseEchoTest {
    @Test public void unicodeCaseAndWhitespace(){assertEquals("доброе утро",PhraseEcho.requirePhrase("  ДОБРОЕ\u00a0 УТРО "));}
    @Test public void countsRealOccurrences(){assertEquals(3,PhraseEcho.count(PhraseEcho.canonical("Да да, ДА"),"да"));}
    @Test public void rejectEmptyAndHuge(){for(String s:new String[]{" ","x","x".repeat(101)})try{PhraseEcho.requirePhrase(s);fail("must reject");}catch(IllegalArgumentException expected){} }
    @Test public void reportsAreImmutable(){PhraseEcho.Report r=new PhraseEcho.Report(java.util.Collections.singletonList(new PhraseEcho.Hit(9,"a","b","c")),1,1,1,false);assertEquals(9,r.examples.get(0).messageId);try{r.examples.clear();fail("mutable");}catch(UnsupportedOperationException expected){} }
    @Test public void excerptSafeOnUnicode(){assertTrue(PhraseEcho.preview("Привет, мир! Как дела?", "мир").contains("мир"));}
    @Test public void noAutomaticLabel(){assertEquals(0,PhraseEcho.count("мы стали друзьями", "неверная фраза"));}
}
