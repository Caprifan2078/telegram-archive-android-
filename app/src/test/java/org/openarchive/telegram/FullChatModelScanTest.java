package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class FullChatModelScanTest {
    @Test public void longBodiesAreSplitWithoutLosingTextAndBlocksAreBounded()throws Exception{
        List<String> out=new ArrayList<>();FullChatModelScan.Chunks chunks=new FullChatModelScan.Chunks(out::add);
        String raw=String.join("",Collections.nCopies(2500,"я"))+"😀конец";
        chunks.add(1,"A","date",raw);chunks.add(2,"B","date","следующее");chunks.flush();
        String joined=String.join("",out);
        assertEquals(2500,joined.chars().filter(c->c=='я').count());
        assertTrue(joined.contains("😀конец"));assertTrue(joined.contains("следующее"));
        for(String block:out)assertTrue(block.length()<=FullChatModelScan.BLOCK);
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L,2L)),FullChatModelScan.ids(joined));
    }
    @Test public void lightweightReadsEveryLogicalBlockAndKeepsIndependentNotes()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        List<String> seen=new ArrayList<>();
        FullChatModelScan.Session session=new FullChatModelScan.Session(report,"Что произошло?",true,new java.util.concurrent.atomic.AtomicBoolean(),prompt->{
            assertTrue(prompt.length()<=1300);seen.add(prompt);
            // deterministic fake generator: preserve all distinct source references.
            StringBuilder result=new StringBuilder();
            for(Long id:FullChatModelScan.ids(prompt))result.append("[#").append(id).append("] ");
            return result.toString();
        },(a,b,c)->{});
        String body=String.join("",Collections.nCopies(10000,"я"));
        session.add(1,"Анна","2026-01-01",body);
        session.add(2,"Борис","2026-01-02",body);
        session.add(3,"Анна","2026-01-03","😀конец");
        session.finish();
        assertEquals(20006,report.chars);
        assertEquals(3,report.blocks);
        assertEquals(3,report.blockNotes.size());
        assertTrue(report.blockNotes.get(0).contains("10000 знаков"));
        assertTrue(report.blockNotes.get(1).contains("[#2]"));
        assertTrue(report.blockNotes.get(2).contains("6 знаков"));
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L,2L,3L)),FullChatModelScan.ids(report.notes));
        assertTrue(report.passes>report.blocks);
        assertTrue(seen.stream().anyMatch(s->s.contains("😀конец")));
    }
    @Test public void fabricatedReferenceIsNotCarriedIntoSummary()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        FullChatModelScan.Session session=new FullChatModelScan.Session(report,"Вопрос",true,new java.util.concurrent.atomic.AtomicBoolean(),p->"[#999] [#1]",(a,b,c)->{});
        session.add(1,"А","date","текст");session.finish();
        assertTrue(report.unverified);
        assertFalse(FullChatModelScan.ids(report.notes).contains(999L));
    }
}
