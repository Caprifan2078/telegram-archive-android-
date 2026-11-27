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
    @Test public void missingReferenceInMergeIsReportedEvenWithValidRemainingCitation()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        FullChatModelScan.Session s=new FullChatModelScan.Session(report,"Что произошло?",true,new java.util.concurrent.atomic.AtomicBoolean(),p->"[#1] Анна", (a,b,c)->{});
        String merged=s.merge("[#1] Анна","[#2] Борис");
        assertEquals(new LinkedHashSet<>(Arrays.asList(1L,2L)),FullChatModelScan.ids(merged));
        assertTrue(merged.contains("[#2] Борис"));
        assertEquals(1,report.mergeFallbacks);
        assertEquals(1,report.mergeStepsWithLostRefs);
        assertTrue(report.unverified);
    }
    @Test public void oversizedNotesDoNotInventAssociationsOrExceedBudget()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        FullChatModelScan.Session s=new FullChatModelScan.Session(report,"Вопрос",true,new java.util.concurrent.atomic.AtomicBoolean(),p->"[#1] Первый", (a,b,c)->{});
        String first="[#1] "+String.join("",Collections.nCopies(200,"а"));
        String second="[#2] "+String.join("",Collections.nCopies(200,"б"));
        String merged=s.merge(first,second);
        assertTrue(merged.length()<=s.noteLimit);
        assertEquals(1,report.mergeStepsWithLostRefs);
        assertEquals(0,report.mergeFallbacks);
        assertTrue(report.unverified);
    }
    @Test public void retainingAllDistinctReferencesDoesNotFlagCompression()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        FullChatModelScan.Session s=new FullChatModelScan.Session(report,"Вопрос",true,new java.util.concurrent.atomic.AtomicBoolean(),p->"[#1] [#2]",(a,b,c)->{});
        s.merge("[#1] начало [#1] продолжение","[#2] конец");
        assertEquals(0,report.mergeStepsWithLostRefs);
        assertFalse(report.unverified);
    }
    @Test public void clippingModelOutputCannotHideDroppedReference()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        FullChatModelScan.Session s=new FullChatModelScan.Session(report,"Вопрос",true,new java.util.concurrent.atomic.AtomicBoolean(),p->"[#1] "+String.join("",Collections.nCopies(350,"я"))+" [#2]",(a,b,c)->{});
        s.merge("[#1] начало","[#2] конец");
        assertEquals(1,report.mergeStepsWithLostRefs);
        assertTrue(report.unverified);
    }
    @Test public void failedMergeDoesNotErasePublishedBlockNotes()throws Exception{
        FullChatModelScan.Report report=new FullChatModelScan.Report();
        List<String> published=new ArrayList<>();
        FullChatModelScan.Session s=new FullChatModelScan.Session(report,"Вопрос",false,new java.util.concurrent.atomic.AtomicBoolean(),p->{
            if(p.contains("УНИКАЛЬНО_ПЕРВЫЙ")&&p.contains("УНИКАЛЬНО_ВТОРОЙ"))throw new java.io.IOException("Synthetic reduction failure");
            return FullChatModelScan.ids(p).contains(2L)?"[#2] УНИКАЛЬНО_ВТОРОЙ":"[#1] УНИКАЛЬНО_ПЕРВЫЙ";
        },new FullChatModelScan.Progress(){
            public void update(long a,long b,long c){}
            public void block(long index,String note){published.add(note);}
        });
        s.add(1,"А","date",String.join("",Collections.nCopies(10000,"я")));
        s.add(2,"Б","date","конец");
        try{s.finish();fail("Expected reduction failure");}catch(java.io.IOException expected){}
        assertEquals(2,report.blocks);
        assertEquals(2,published.size());
        assertTrue(published.get(0).contains("[#1]"));
        assertTrue(published.get(1).contains("[#2]"));
        assertFalse(report.complete);
    }
}
