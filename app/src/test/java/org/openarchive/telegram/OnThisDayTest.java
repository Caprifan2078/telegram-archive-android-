package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class OnThisDayTest {
    @Test public void recognisesRussianAndIso(){
        assertTrue(OnThisDay.matches("24.10.2021 22:04:11",10,24));
        assertTrue(OnThisDay.matches("2024-10-24T01:05:40",10,24));
        assertFalse(OnThisDay.matches("2024-10-23",10,24));
    }
    @Test public void leapDayAndInvalidDates(){
        assertTrue(OnThisDay.matches("29.02.2024 13:30",2,29));
        assertFalse(OnThisDay.matches("29.02.2023 13:30",2,29));
        assertFalse(OnThisDay.matches("2026-00-24",0,24));
        assertFalse(OnThisDay.matches(null,10,24));
    }
    @Test public void boundsLongPreviewByCodePoints(){
        String emoji="😀".repeat(200);
        OnThisDay.Entry item=new OnThisDay.Entry(9,"a","2026-10-24",emoji);
        assertEquals(121,item.preview.codePointCount(0,item.preview.length()));
        assertTrue(item.preview.endsWith("…"));
    }
}
