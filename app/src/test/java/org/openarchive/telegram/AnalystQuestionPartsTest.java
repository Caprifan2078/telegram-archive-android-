package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalystQuestionPartsTest {
    @Test public void onlySplitExplicitIndependentQuestions(){
        assertEquals(java.util.Arrays.asList("Кто начал разговор?","Кто ответил?"),
            AnalystQuestionParts.split("Кто начал разговор? Кто ответил?"));
        assertEquals(1,AnalystQuestionParts.split("Кто начал разговор и кто ответил?").size());
        assertEquals(1,AnalystQuestionParts.split("Что значит «Кто ответил? Кто ушёл?»").size());
        assertEquals(1,AnalystQuestionParts.split("Кто начал разговор? И потом?").size());
        assertEquals(1,AnalystQuestionParts.split("Кто начал разговор? Кто ответил? Кто ушёл? Кто вернулся?").size());
    }
    @Test public void eachPartMustHaveItsOwnExistingCitation(){
        String prompt="Источники:\n[#18] Ира | Прости.\n";
        assertTrue(AnalystQuestionParts.hasEvidence("Ира [#18]",prompt));
        assertFalse(AnalystQuestionParts.hasEvidence("Ира",prompt));
        assertFalse(AnalystQuestionParts.hasEvidence("Ира [#19]",prompt));
        assertFalse(AnalystQuestionParts.hasEvidence("",prompt));
    }
}
