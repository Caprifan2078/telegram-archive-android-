package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalendarPeriodTest {
 @Test public void acceptsDatesOutsideChatAndSingleDay(){CalendarPeriod p=new CalendarPeriod(0,"Учёба","", "1999-01-01","1999-01-01");assertEquals("1999-01-01",p.start);}
 @Test(expected=IllegalArgumentException.class) public void rejectsReversedDates(){new CalendarPeriod(0,"Период","","2026-03-01","2026-02-01");}
 @Test(expected=IllegalArgumentException.class) public void rejectsEmptyName(){new CalendarPeriod(0,"  ","","2026-01-01","2026-01-01");}
}
