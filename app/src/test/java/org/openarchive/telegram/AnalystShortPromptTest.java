package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnalystShortPromptTest {
    @Test public void completionCuePreservesEvidenceAndQuestion(){
        String original="[#52] Ира: Добрый день.\nВопрос: Кто написал сообщение?";
        String actual=AnalystShortPrompt.prepare(original);
        assertTrue(actual.startsWith(original));
        assertTrue(actual.contains("все части вопроса"));
        assertTrue(actual.length()<=1500);
        assertEquals(384,AnalystShortPrompt.OUTPUT_LIMIT);
    }
    @Test public void longPromptIsNotSilentlyTruncatedOrExpanded(){
        String original=new String(new char[1500]).replace('\0','я');
        assertEquals(original,AnalystShortPrompt.prepare(original));
        try{AnalystShortPrompt.prepare(original+"я");fail("oversized prompt");}
        catch(IllegalArgumentException expected){}
    }
}
