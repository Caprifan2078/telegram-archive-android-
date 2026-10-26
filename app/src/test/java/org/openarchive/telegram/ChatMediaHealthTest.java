package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class ChatMediaHealthTest {
    @Test public void countsAndLimitsExamples(){
        ChatMediaHealth.Builder builder=new ChatMediaHealth.Builder();
        for(int i=0;i<25;i++)builder.record("media/path"+i,i<3);
        ChatMediaHealth.Report r=builder.finish(27);
        assertEquals(25,r.scanned);assertEquals(3,r.accessible);assertEquals(22,r.unresolved);
        assertEquals(12,r.unresolvedExamples.size());assertTrue(r.truncated);
    }
    @Test public void distinguishesEmptyAndFullyScanned(){
        ChatMediaHealth.Report r=new ChatMediaHealth.Builder().finish(0);
        assertEquals(0,r.scanned);assertFalse(r.truncated);
    }
}
