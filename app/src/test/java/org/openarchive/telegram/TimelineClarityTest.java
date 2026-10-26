package org.openarchive.telegram;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;
public class TimelineClarityTest {
  @Test public void fullHistoryIncludesFirstAndLastYear(){
    ChatTrends.Activity a=new ChatTrends.Activity();
    a.days.put(LocalDate.of(2013,5,1),7L);a.days.put(LocalDate.of(2026,10,1),11L);
    ChatTrends.Series b=ChatTrends.allTimeYears(a);
    assertEquals("2013",b.labels.get(0));assertEquals("2026",b.labels.get(1));
    assertEquals(18,b.visibleCount);assertFalse(b.clipped);
  }
  @Test public void twelveMonthsKeepsZerosAndCounts(){
    ChatTrends.Activity a=new ChatTrends.Activity();
    a.days.put(LocalDate.of(2026,1,1),3L);a.days.put(LocalDate.of(2026,12,31),9L);
    a.days.put(LocalDate.of(2025,2,2),55L);
    ChatTrends.Series s=ChatTrends.twelveMonths(a,2026);
    assertEquals(12,s.values.length);assertEquals(3,s.values[0]);assertEquals(0,s.values[1]);
    assertEquals(9,s.values[11]);assertEquals(12,s.visibleCount);
  }
  @Test public void levelIsNumericAndMonotonic(){
    assertEquals(0,ChatTrends.heatLevel(0,100));
    assertEquals(10,ChatTrends.heatLevel(100,100));
    for(int i=1;i<=100;i++) assertTrue(ChatTrends.heatLevel(i,100)>=ChatTrends.heatLevel(i-1,100));
  }
}
