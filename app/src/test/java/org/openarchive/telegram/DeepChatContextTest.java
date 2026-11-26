package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class DeepChatContextTest {
    @Test public void rejectsArchivePathTraversal(){
        assertEquals("telegram_archive.db",DeepChatContext.dbName("default"));
        try{DeepChatContext.dbName("../other");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void promptIsBoundedAndUnknownSourcesAreFlagged(){
        List<DeepChatContext.Source> rows=new ArrayList<>();
        for(int i=1;i<=16;i++)rows.add(new DeepChatContext.Source(i,"A","2026-01-01",String.join("",Collections.nCopies(600,"я"))));
        DeepChatContext ctx=new DeepChatContext(rows,100,"Факты","Тест");
        String prompt=ctx.prompt("Что обсуждали?");
        assertTrue(prompt.length()<=DeepChatContext.MAX_PROMPT_CHARS);
        assertTrue(prompt.contains("[#1]"));
        assertEquals(Collections.singletonList(999L),DeepChatContext.unknownCitations("[#1] [#999]",prompt));
    }
}
