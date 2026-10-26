package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class ParallelCalendarsTest {
    @Test public void doesNotBlendChats(){
        ParallelCalendars.YearData a=new ParallelCalendars.YearData(2024),b=new ParallelCalendars.YearData(2024);
        a.add("01.01.2024 18:00:00");b.add("2024-02-28T09:00:00");b.add("2024-02-29");
        ParallelCalendars.Comparison c=new ParallelCalendars.Comparison(a,b);
        assertTrue(c.complete());assertEquals(1,a.total());assertEquals(2,b.total());
        assertEquals(1,a.months[0]);assertEquals(2,b.months[1]);
    }
    @Test public void ignoresBadAndDifferentYearDates(){
        ParallelCalendars.YearData a=new ParallelCalendars.YearData(2024);
        a.add("31.02.2024");a.add("2025-01-01");a.add(null);a.add("2024-12-20");
        assertEquals(1,a.total());assertEquals(3,a.rejected);
    }
    @Test public void scannedCapIsExplicit(){
        ParallelCalendars.YearData a=new ParallelCalendars.YearData(2024);
        a.scanned=ParallelCalendars.MAX_DATE_ROWS;
        a.add("2024-01-01");assertTrue(a.partial);assertEquals(0,a.total());
    }
    @Test public void cannotCompareDifferentYears(){
        try{new ParallelCalendars.Comparison(new ParallelCalendars.YearData(2023),new ParallelCalendars.YearData(2024));fail();}
        catch(IllegalArgumentException expected){}
    }
    @Test public void copyPreventsResultMutation(){
        ParallelCalendars.YearData a=new ParallelCalendars.YearData(2024);a.add("2024-01-01");
        long[] copy=a.counts();copy[0]=555;assertEquals(1,a.total());
    }
}
