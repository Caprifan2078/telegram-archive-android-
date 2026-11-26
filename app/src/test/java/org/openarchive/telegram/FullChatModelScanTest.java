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
}
