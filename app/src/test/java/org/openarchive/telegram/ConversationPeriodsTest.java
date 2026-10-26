package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConversationPeriodsTest {
    @Test public void preciseBoundariesCompareCharacterOffsets(){
        ConversationPeriods.Boundary a=new ConversationPeriods.Boundary(44,18);
        ConversationPeriods.Boundary b=new ConversationPeriods.Boundary(44,35);
        assertTrue(a.compareTo(b)<0);
        ConversationPeriods.Chapter chapter=new ConversationPeriods.Chapter(1,"Дружба","",a,b);
        assertTrue(ConversationPeriods.touches(chapter,44));
        assertFalse(ConversationPeriods.touches(chapter,43));
        assertFalse(ConversationPeriods.touches(chapter,45));
    }
    @Test public void rejectReversedBoundaries(){
        try{ConversationPeriods.validate("Период","",new ConversationPeriods.Boundary(8,20),
                new ConversationPeriods.Boundary(8,19));fail("Should reject reversed offsets");}
        catch(IllegalArgumentException expected){}
    }
    @Test public void rejectEmptyAndHugeNames(){
        try{ConversationPeriods.validate("  ","",new ConversationPeriods.Boundary(2,0),
                new ConversationPeriods.Boundary(3,0));fail();}catch(IllegalArgumentException expected){}
        try{ConversationPeriods.validate("x".repeat(81),"",new ConversationPeriods.Boundary(2,0),
                new ConversationPeriods.Boundary(3,0));fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void dailyRateIsExplicitAndHandlesMissingDates(){
        ConversationPeriods.Stats stats=new ConversationPeriods.Stats(10,2,8,3,"2026-10-01","2026-10-03",
                new long[]{2,0,0,0,0,0,0,0,0,0,0,6});
        assertEquals(4.0,stats.dailyAverage(),0.000001);
        assertEquals(12,stats.bins.size());
        assertEquals(10,stats.messages);assertEquals(3,stats.media);
    }
    @Test public void doNotInferRelationshipStage(){
        ConversationPeriods.Chapter manual=new ConversationPeriods.Chapter(1,"Переезд","Мы решили назвать так",
                new ConversationPeriods.Boundary(5,0),new ConversationPeriods.Boundary(105,20));
        assertEquals("Переезд",manual.name);assertEquals("Мы решили назвать так",manual.note);
    }
}
