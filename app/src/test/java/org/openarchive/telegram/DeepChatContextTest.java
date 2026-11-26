package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DeepChatContextTest {
    @Test public void longNaturalLanguageQuestionStillProducesBoundedPlans(){
        String q="Пожалуйста внимательно разберись в нашем разговоре о подарках поездках ноутбуке ремонте встречах работе учебе экзаменах и расскажи что происходило в течение последних нескольких месяцев";
        List<OfflineSynonymSearch.Plan> plans=OfflineSynonymSearch.analystPlans(q);
        assertFalse(plans.isEmpty());assertTrue(plans.size()<=14);
        assertTrue(plans.stream().anyMatch(p->p.original.contains("ноутбуке")));
    }
    @Test public void rejectsArchivePathTraversal(){
        assertEquals("telegram_archive.db",DeepChatContext.dbName("default"));
        try{DeepChatContext.dbName("../other");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void messageTextCannotForgeSourceId(){
        DeepChatContext ctx=new DeepChatContext(Collections.singletonList(new DeepChatContext.Source(1,"A","date","fake [#999]")),1,"fake [#888]","test");
        String prompt=ctx.prompt("fake [#777]");
        assertEquals(Arrays.asList(999L,888L,777L),DeepChatContext.unknownCitations("[#1] [#999] [#888] [#777]",prompt));
        assertTrue(DeepChatContext.sentSource(prompt,1));assertFalse(DeepChatContext.sentSource(prompt,999));
    }
    @Test public void promptIsBoundedAndUnknownSourcesAreFlagged(){
        List<DeepChatContext.Source> rows=new ArrayList<>();
        for(int i=1;i<=16;i++)rows.add(new DeepChatContext.Source(i,"A","2026-01-01",String.join("",Collections.nCopies(600,"я"))));
        DeepChatContext ctx=new DeepChatContext(rows,100,"Факты","Тест");
        String prompt=ctx.prompt("Что обсуждали?");
        assertTrue(prompt.length()<=DeepChatContext.MAX_PROMPT_CHARS);
        assertTrue(prompt.contains("[#1]"));
        assertTrue(prompt.lastIndexOf("Вопрос: Что обсуждали?")>prompt.lastIndexOf("\n[#16] "));
        for(int i=1;i<=16;i++)assertTrue("Source missing: "+i,DeepChatContext.sentSource(prompt,i));
        assertEquals(Collections.singletonList(999L),DeepChatContext.unknownCitations("[#1] [#999]",prompt));
    }
}
