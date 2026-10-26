package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class HelpContentTest {
    @Test public void coversNewAndOldFeatures(){
        assertTrue(HelpContent.all().size()>=15);
        for(String term:new String[]{"100 файлов","коллекции","MP3","анализ","резервную копию","диагностика","стекло","ZIP"})
            assertFalse("Missing instructions: "+term,HelpContent.find(term).isEmpty());
    }
    @Test public void noFakeMp3Claims(){
        assertTrue(HelpContent.find("MP3").stream().anyMatch(c->c.description.contains("не делает настоящий MP3")));
    }
    @Test public void quoteTutorialIsFindable(){
        assertTrue(HelpContent.find("цитировать").stream().anyMatch(c->c.description.contains("В заметки")&&c.description.contains("Личные заметки")));
        assertTrue(HelpContent.find("исходному сообщению").stream().anyMatch(c->c.description.contains("Цитата")||c.description.contains("цитата")));
        assertFalse(HelpContent.find("уменьшение движения").isEmpty());
    }
    @Test public void searchIsCaseInsensitiveAndStable(){
        assertEquals(HelpContent.find("медиа").size(),HelpContent.find("МЕДИА").size());
        assertEquals(HelpContent.all().size(),HelpContent.find("").size());
        assertTrue(HelpContent.find("sourceless-abc-123").isEmpty());
    }
}
