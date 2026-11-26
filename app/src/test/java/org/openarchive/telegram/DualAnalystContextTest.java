package org.openarchive.telegram;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class DualAnalystContextTest {
    private static DeepChatContext sample(String body,int count){
        List<DeepChatContext.Source> rows=new ArrayList<>();
        for(int i=1;i<=count;i++)rows.add(new DeepChatContext.Source(i,"Одинаковое имя","2026-01-01",body+" "+i));
        return new DeepChatContext(rows,count,"Всего: "+count,"test");
    }
    @Test public void identicalOriginalIdsRemainSeparateAndEvidenceBalanced(){
        DualAnalystContext d=DualAnalystContext.combine("first",sample("Первая",16),"second",sample("Вторая",16));
        assertEquals(16,d.context.sources.size());
        assertEquals(16,d.links.size());
        assertEquals(8,d.links.values().stream().filter(x->x.archive.equals("first")).count());
        assertEquals(8,d.links.values().stream().filter(x->x.archive.equals("second")).count());
        assertEquals(1,d.links.get(1L).messageId);
        assertEquals(1,d.links.get(2L).messageId);
        assertEquals("first",d.links.get(1L).archive);
        assertEquals("second",d.links.get(2L).archive);
        assertEquals(16,d.links.get(15L).messageId);
        assertEquals(16,d.links.get(16L).messageId);
        String prompt=d.prompt("Кто поддерживал собеседника?");
        assertTrue(prompt.length()<=DeepChatContext.MAX_PROMPT_CHARS);
        for(long id=1;id<=16;id++)assertTrue(DeepChatContext.sentSource(prompt,id));
        assertTrue(prompt.contains("А: Одинаковое имя"));
        assertTrue(prompt.contains("Б: Одинаковое имя"));
    }
    @Test public void smallArchiveIsNotDuplicatedToFillQuota(){
        DualAnalystContext d=DualAnalystContext.combine("first",sample("A",1),"second",sample("B",16));
        assertEquals(9,d.links.size());
        assertEquals(1,d.links.values().stream().filter(x->x.archive.equals("first")).count());
    }
    @Test(expected=IllegalArgumentException.class) public void sameArchiveRejected(){
        DualAnalystContext.combine("same",sample("A",1),"same",sample("B",1));
    }

    @Test public void comparativeAnswerMustCiteBothRealSources(){
        DualAnalystContext d=DualAnalystContext.combine("first",sample("A",1),"second",sample("B",1));
        String prompt=d.prompt("Сравни чаты");
        assertTrue(d.citesBoth("[#1] и [#2]",prompt));
        assertFalse(d.citesBoth("[#1] и [#999]",prompt));
        assertFalse(d.citesBoth("[#1] [#1]",prompt));
        assertFalse(d.citesBoth("",prompt));
    }
}
