package org.openarchive.telegram;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AnalystQuestionLibraryTest {
    @Test public void everyPresetIsUniqueAndFitsInput(){
        Set<String> questions=new HashSet<>();
        int factual=0,single=0,dual=0;
        for(String[] group:ChatQuestionAnalyst.PRESETS)for(int i=1;i<group.length;i++){
            assertTrue(questions.add(group[i]));assertTrue(ChatQuestionAnalyst.supports(group[i]));factual++;
        }
        for(String[] group:AnalystQuestionLibrary.SINGLE)for(int i=1;i<group.length;i++){
            assertTrue(questions.add(group[i]));assertFalse(ChatQuestionAnalyst.supports(group[i]));
            assertTrue(group[i].length()<=240);single++;
        }
        for(String[] group:AnalystQuestionLibrary.DUAL)for(int i=1;i<group.length;i++){
            assertTrue(questions.add(group[i]));assertTrue(group[i].length()<=240);dual++;
        }
        assertEquals(19,factual);assertEquals(36,single);assertEquals(15,dual);assertEquals(70,questions.size());
    }
    @Test public void unicodeLengthAndUnknownDatesDoNotInventFacts(){
        ChatQuestionAnalyst.Accumulator length=new ChatQuestionAnalyst.Accumulator(ChatQuestionAnalyst.parse("Средняя длина сообщения?"));
        length.add(1,"А","", "😀а");length.add(2,"Б","","");
        assertTrue(length.finish().text.contains("2.0 символов Unicode"));
        ChatQuestionAnalyst.Accumulator hour=new ChatQuestionAnalyst.Accumulator(ChatQuestionAnalyst.parse("Самый активный час?"));
        hour.add(1,"А","2026-01-01","");hour.add(2,"Б","неизвестно","");
        assertTrue(hour.finish().text.contains("Нет сообщений с точным временем"));
    }
}
