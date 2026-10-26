package org.openarchive.telegram;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class SharedMemoriesTest {
    @Test public void twoArchivesHaveIndependentIdentity(){
        SharedMemories.Entry a=new SharedMemories.Entry("a","A",1,"a","2024-08-02 12:00","hello");
        SharedMemories.Entry b=new SharedMemories.Entry("b","B",1,"b","2024-08-03 12:00","world");
        List<SharedMemories.Entry> merged=SharedMemories.merge(Arrays.asList(a),Arrays.asList(b),50);
        assertEquals("b",merged.get(0).archiveKey);assertEquals("a",merged.get(1).archiveKey);
        assertEquals(1,merged.get(0).messageId);assertEquals(1,merged.get(1).messageId);
        assertTrue(SharedMemories.belongs(a,"a","b"));
        assertFalse(SharedMemories.belongs(a,"b","c"));
    }
    @Test public void stripsNewlineAndDoesNotSplitEmoji(){
        String value="😀".repeat(200);
        SharedMemories.Entry a=new SharedMemories.Entry("k","",1,"","2024-01-01",value);
        assertEquals(181,a.preview.codePointCount(0,a.preview.length()));
        assertTrue(a.preview.endsWith("…"));
    }
    @Test public void russianDatesSortAndCap(){
        SharedMemories.Entry a=new SharedMemories.Entry("a","A",1,"","31.12.2020 21:00","A");
        SharedMemories.Entry b=new SharedMemories.Entry("b","B",5,"","2021-01-01 03:00","B");
        assertSame(b,SharedMemories.merge(Arrays.asList(a),Arrays.asList(b),1).get(0));
        assertEquals(0,SharedMemories.merge(Collections.emptyList(),Collections.emptyList(),100).size());
    }
    @Test(expected=IllegalArgumentException.class)public void noInvalidMessageIds(){
        new SharedMemories.Entry("a","A",0,"","2023-01-01","text");
    }
    @Test(expected=IllegalArgumentException.class)public void noInventedDates(){
        new SharedMemories.Entry("a","A",7,"","2023-02-30","text");
    }
    @Test public void busyChatCannotHideSecondChat(){
        java.util.ArrayList<SharedMemories.Entry> a=new java.util.ArrayList<>();
        for(int i=0;i<80;i++)a.add(new SharedMemories.Entry("busy","Busy",i+1,"",
                "2026-04-01 18:00","msg"));
        SharedMemories.Entry b=new SharedMemories.Entry("quiet","Quiet",3,"","2025-01-01","old");
        List<SharedMemories.Entry> view=SharedMemories.mergeBalanced(a,Arrays.asList(b),30);
        assertEquals(31,view.size());
        assertTrue(view.stream().anyMatch(e->e.archiveKey.equals("quiet")));
    }
    @Test public void overlapAndLeapDay(){
        ParallelCalendars.YearData a=new ParallelCalendars.YearData(2024);
        ParallelCalendars.YearData b=new ParallelCalendars.YearData(2024);
        a.add("2024-02-29 12:00");a.add("2024-03-01");a.add("2024-02-29 13:00");
        b.add("29.02.2024 14:00");b.add("2024-01-01");
        assertEquals(2,a.activeDays());assertEquals(2,b.activeDays());
        assertEquals(1,new ParallelCalendars.Comparison(a,b).sharedActiveDays());
        assertEquals(3,a.total());
    }
}
