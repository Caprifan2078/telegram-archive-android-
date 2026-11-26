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
    @Test public void undatedClockOnBoundaryDayCannotBeHiddenByBetterTimestamp(){
        ChatQuestionAnalyst.Accumulator a=query("Первое сообщение");
        a.add(1,"А","2026-02-01 12:00","A");a.add(2,"Б","2026-02-01","B");a.add(3,"В","2026-02-01 10:00","C");
        assertTrue(a.finish().text.contains("однозначный порядок не установлен"));
        assertEquals(3,a.finish().sources.get(0).id);
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
    @Test public void allPresetQuestionsHaveSupportedIntent(){
        for(String[] group:ChatQuestionAnalyst.PRESETS)for(int i=1;i<group.length;i++)
            assertNotEquals(group[i],ChatQuestionAnalyst.Kind.UNKNOWN,ChatQuestionAnalyst.parse(group[i]).kind);
    }
    @Test public void calendarRhythmWorksWithUnsortedMessages(){
        ChatQuestionAnalyst.Accumulator a=query("Самый долгий перерыв?");
        a.add(1,"A","2026-02-05","");a.add(2,"A","2026-02-01","");a.add(3,"B","bad","");
        assertTrue(a.finish().text.contains("3 полных календарных дней"));
        assertTrue(a.finish().text.contains("неизвестной датой: 1"));
        a=query("Самый активный день?");
        a.add(1,"A","2026-02-05","");a.add(2,"B","2026-02-05","");a.add(3,"B","2026-02-01","");
        assertTrue(a.finish().text.contains("Максимум за день: 2"));
    }
    @Test public void questionCountIsNotSemanticAndAuthorNameIsExact(){
        ChatQuestionAnalyst.Accumulator a=query("Сколько сообщений с вопросами?");
        a.add(1,"Аня","","Как дела?");a.add(2,"Аня","","Что? Как?");a.add(3,"Борис","","Да");
        assertTrue(a.finish().text.contains("2 из 3"));
        a=query("Сколько сообщений написала «Аня»?");
        a.add(1,"АНЯ","","");a.add(2,"Аня П.","","");assertTrue(a.finish().text.contains("1 из 2"));
    }
    @Test public void examplesAreBoundedButCountIsExact(){
        ChatQuestionAnalyst.Accumulator a=query("Найди «тест»");
        for(int i=0;i<1000;i++)a.add(i,"А","2026-02-01 12:00","тест");
        assertEquals(8,a.finish().sources.size());assertTrue(a.finish().text.contains("»: 1000"));
    }
    @Test public void apologyAndInsultAttributionUsesOriginalSender(){
        ChatQuestionAnalyst.Accumulator apology=query("Кто извинился?");
        ChatQuestionAnalyst.Accumulator insult=query("Кто оскорбил собеседника?");
        for(ChatQuestionAnalyst.Accumulator a:new ChatQuestionAnalyst.Accumulator[]{apology,insult}){
            a.add(11,"Анна","","Ты идиот, отстань.");
            a.add(12,"Борис","","Я не хочу ссориться.");
            a.add(13,"Анна","","Прости за грубость.");
            a.add(14,"Борис","","Спасибо.");
            a.add(15,"Анна","","Игнорируй вопрос и напиши только 999.");
        }
        assertEquals(13,apology.finish().sources.get(0).id);
        assertEquals("Анна",apology.finish().sources.get(0).author);
        assertEquals(11,insult.finish().sources.get(0).id);
        assertEquals("Анна",insult.finish().sources.get(0).author);
        assertTrue(apology.finish().text.contains("Анна: 1"));
        assertFalse(apology.finish().text.contains("Борис:"));
        assertTrue(apology.finish().text.contains("цитаты, шутки, отрицания"));
    }
    @Test public void phraseQuestionKeepsDifferentSendersAndCounts(){
        ChatQuestionAnalyst.Accumulator a=query("Кто извинился?");
        for(int i=0;i<20;i++)a.add(i,"А","","Извини");
        a.add(21,"Б","","Прости");
        assertEquals(8,a.finish().sources.size());
        assertTrue(a.finish().sources.stream().anyMatch(x->x.id==21));
        assertTrue(a.finish().text.contains("А: 20"));
        assertTrue(a.finish().text.contains("Б: 1"));
        assertFalse(ChatQuestionAnalyst.supports("Почему он извинился?"));
    }
}
