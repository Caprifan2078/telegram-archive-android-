package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure-Java statistics remain testable independently of Android. */
public class ChatAnalyticsTest {
    @Test public void authorNormalizationAndWordCounts(){
        ChatAnalytics.Accumulator a=new ChatAnalytics.Accumulator();
        a.message("  Катя  ","24.10.2026 22:10","Привет, привет 😊");
        a.message("КАТЯ","2026-10-24 22:11","Привет!");
        a.message("Макс","2026-10-24","Спасибо, Катя");
        ChatAnalytics.Snapshot s=a.snapshot();
        assertEquals(3,s.totalMessages);assertEquals(2,s.people.size());
        assertEquals(2,s.people.get(0).messages);assertEquals(5,s.totalWords);
        assertEquals(1,s.days.size());assertEquals(3,s.people.get(0).words);
        assertEquals(100.0,ChatAnalytics.percent(3,3),0.01);
    }
    @Test public void differentMediaCountAndUnknownNames(){
        ChatAnalytics.Accumulator a=new ChatAnalytics.Accumulator();
        a.message(null,null,"");a.media("", "round",3);a.media(" ","voice",2);
        ChatAnalytics.Snapshot s=a.snapshot();
        assertEquals(1,s.people.size());assertEquals(5,s.totalMedia);
        assertEquals(3,s.type("round"));assertEquals(2,s.type("voice"));
    }
    @Test public void emptyAndUndatedChatsHaveSafePercentages(){
        ChatAnalytics.Snapshot s=new ChatAnalytics.Accumulator().snapshot();
        assertEquals(0,s.totalMessages);assertEquals(0,ChatAnalytics.percent(0,0),.01);
        assertEquals("",s.firstDay);assertTrue(s.topWords.isEmpty());
    }
    @Test public void meaningfulWordsExcludeNumericFragmentsWithoutChangingWordTotals(){
        ChatAnalytics.Accumulator accumulator=new ChatAnalytics.Accumulator();
        accumulator.message("Аня","2026-11-23 10:00","Встретимся в 17:00, 17:00. PDF pdf и фотографии фотографии.");
        ChatAnalytics.Snapshot snapshot=accumulator.snapshot();
        boolean numericRaw=false,numericMeaningful=false,pdfMeaningful=false,photoMeaningful=false;
        for(ChatAnalytics.WordCount w:snapshot.topWords)
            if(w.word.equals("17")||w.word.equals("00"))numericRaw=true;
        for(ChatAnalytics.WordCount w:snapshot.topMeaningfulWords){
            if(w.word.equals("17")||w.word.equals("00"))numericMeaningful=true;
            if(w.word.equals("pdf"))pdfMeaningful=true;
            if(w.word.equals("фотографии"))photoMeaningful=true;
        }
        assertTrue("Original unfiltered words remain available",numericRaw);
        assertFalse("Meaningful words should not be polluted by clock fragments",numericMeaningful);
        assertTrue(pdfMeaningful);
        assertTrue(photoMeaningful);
        assertEquals("Word counting must be independent of presentation filtering",9,snapshot.totalWords);
    }
}

