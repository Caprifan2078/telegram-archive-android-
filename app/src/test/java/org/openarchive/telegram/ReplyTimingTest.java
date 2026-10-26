package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReplyTimingTest {
    @Test public void differentSendersWithinDayCount() {
        ChatAnalytics.Accumulator a=new ChatAnalytics.Accumulator();
        a.message("А","2025-09-22 12:00:00","1");
        a.message("А","2025-09-22 12:01:00","2");
        a.message("Б","2025-09-22 12:11:00","3");
        a.message("А","2025-09-22 12:21:00","4");
        ChatAnalytics.Snapshot result=a.snapshot();
        assertEquals(2,result.switchCount);
        assertEquals(10.0,result.meanSwitchMinutes,0.001);
    }
    @Test public void ignoreTimeGapsAndUnparseableDates() {
        ChatAnalytics.Accumulator a=new ChatAnalytics.Accumulator();
        a.message("А","2025-09-22 12:00:00","X");
        a.message("Б","2025-09-24 12:00:00","Y");
        a.message("А","bad date","Z");
        assertEquals(0,a.snapshot().switchCount);
    }
    @Test public void noGuessAboutRecipientsInGroups(){
        ChatAnalytics.Accumulator a=new ChatAnalytics.Accumulator();
        a.message("А","2025-09-22 12:00:00","X");
        a.message("Б","2025-09-22 12:05:00","Y");
        a.message("В","2025-09-22 12:10:00","Z");
        assertEquals(2,a.snapshot().switchCount);
        assertEquals(5.,a.snapshot().meanSwitchMinutes,0.001);
    }
}
