package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;

public class WrappedSummaryTest {
    @Test public void leapYearAndAbsentDates(){
        ChatTrends.Activity a=new ChatTrends.Activity();
        a.days.put(LocalDate.of(2024,2,29),3L);
        WrappedSummary w=WrappedSummary.create(new ChatTrends.Snapshot(a,new LinkedHashMap<>()),2024);
        assertEquals(3L,w.messages);assertEquals(1,w.activeDays);assertEquals(2,w.peakMonth);
        assertEquals("2024-02",MediaTimeline.month("29.02.2024 11:30"));
        assertEquals("Дата не указана",MediaTimeline.month("31.02.2024"));
    }
    @Test public void emptyYearDoesNotInventFacts(){
        WrappedSummary w=WrappedSummary.create(new ChatTrends.Snapshot(new ChatTrends.Activity(),new LinkedHashMap<>()),2025);
        assertEquals(0L,w.messages);assertNull(w.busiestDay);assertEquals(0,w.peakMonth);
    }
    @Test public void boundedChatKeepsEverySequencePosition(){
        int offset=0,visible=40;
        for(int i=0;i<1000;i++){
            visible+=40;int removed=ChatWindowPolicy.trim(1,visible,40);
            offset=ChatWindowPolicy.afterEvictionOffset(offset,removed);visible-=removed;
            assertEquals((i+2)*40,offset+visible);
            assertTrue(visible<=240);
        }
    }
}
