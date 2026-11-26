package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystFinalAnswerTest {
    @Test public void citationsInThinkingAreNotFinalEvidence(){
        assertEquals("Ответ [#2]",AnalystFinalAnswer.clean("<think>Источник [#1]</think>Ответ [#2]"));
        assertEquals("",AnalystFinalAnswer.clean("<think>Незавершённый текст [#1]"));
        assertEquals("Ответ",AnalystFinalAnswer.clean("</think>Ответ"));
        assertEquals("Ответ [#3]",AnalystFinalAnswer.clean("Ответ [#3]"));
    }
}
