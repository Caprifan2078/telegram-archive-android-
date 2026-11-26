package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystFinalAnswerTest {
    @Test public void citationsInThinkingAreNotFinalEvidence(){
        assertEquals("Ответ [#2]",AnalystFinalAnswer.clean("<think>Источник [#1]</think>Ответ [#2]"));
        assertEquals("",AnalystFinalAnswer.clean("<think>Незавершённый текст [#1]"));
        assertEquals("Ответ",AnalystFinalAnswer.clean("</think>Ответ"));
        assertEquals("Ответ [#3]",AnalystFinalAnswer.clean("Ответ [#3]"));
        assertEquals("[#2]: встреча в 17:45. [#999]",AnalystFinalAnswer.clean("#2: встреча в 17:45. #999"));
        assertEquals("abc#2 и [#1]",AnalystFinalAnswer.clean("abc#2 и [#1]"));
        assertEquals("Ответ [#2]",AnalystFinalAnswer.clean("<think>#999</think>Ответ #2"));
    }
}
