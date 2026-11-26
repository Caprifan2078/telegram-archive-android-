package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatQuestionAnalystTest {
    private ChatQuestionAnalyst.Accumulator query(String q){return new ChatQuestionAnalyst.Accumulator(ChatQuestionAnalyst.parse(q));}
    @Test public void firstUsesActualDateNotImportOrder(){
        ChatQuestionAnalyst.Accumulator a=query("Кто первый написал?");
        a.add(1,"Б","2026-02-02 12:00","Позже");
        a.add(2,"А","01.02.2026 10:00","Раньше");
        assertEquals(2,a.finish().sources.get(0).id);
        assertTrue(a.finish().text.contains("А · 01.02.2026"));
    }
    @Test public void ambiguousAndMissingDatesAreExplicit(){
        ChatQuestionAnalyst.Accumulator a=query("Первое сообщение");
        a.add(1,"А","2026-02-01","A");a.add(2,"Б","2026-02-01 11:00","B");a.add(3,"В","bad","C");
        assertTrue(a.finish().text.contains("однозначный порядок не установлен"));
        assertTrue(a.finish().text.contains("Без распознанной даты: 1"));
    }
    @Test public void literalTopicAndCounts(){
        ChatQuestionAnalyst.Accumulator a=query("Найди «встреча»");
        a.add(1,"А","2026-02-01","ВСТРЕЧА");a.add(2,"Б","2026-02-02","встреча встреча");a.add(3,"А","2026-02-03","другое");
        assertEquals(2,a.finish().sources.size());assertTrue(a.finish().text.contains("»: 2"));
    }
    @Test public void unsupportedQuestionDoesNotInvent(){
        assertEquals(ChatQuestionAnalyst.Kind.UNKNOWN,ChatQuestionAnalyst.parse("Почему она меня игнорирует?").kind);
        assertEquals(ChatQuestionAnalyst.Kind.UNKNOWN,ChatQuestionAnalyst.parse("Сколько сообщений за 2020?").kind);
        assertEquals(ChatQuestionAnalyst.Kind.UNKNOWN,ChatQuestionAnalyst.parse("Что писали 31.02.2026?").kind);
    }
    @Test public void leadersAndDay(){
        ChatQuestionAnalyst.Accumulator a=query("Кто больше всего писал?");
        a.add(1,"А","","");a.add(2,"Б","","");assertTrue(a.finish().text.contains("Одинаковый максимум"));
        a=query("Что писали 01.02.2026?");
        a.add(1,"А","2026-02-01 12:00","текст");a.add(2,"Б","2026-02-02","другое");assertEquals(1,a.finish().sources.size());
    }
    @Test public void examplesAreBoundedButCountIsExact(){
        ChatQuestionAnalyst.Accumulator a=query("Найди «тест»");
        for(int i=0;i<1000;i++)a.add(i,"А","2026-02-01 12:00","тест");
        assertEquals(8,a.finish().sources.size());assertTrue(a.finish().text.contains("»: 1000"));
    }
}
