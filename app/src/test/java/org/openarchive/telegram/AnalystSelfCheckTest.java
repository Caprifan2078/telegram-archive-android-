package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystSelfCheckTest {
    @Test public void syntheticCheckFlagsMissingAndUnknownSources(){
        String good=AnalystSelfCheck.assess("Анна оскорбила Бориса [#11]. Анна извинилась [#13].");
        assertTrue(good.contains("ссылки")||good.contains("Ссылки"));
        assertTrue(good.contains("извинение: есть"));
        assertTrue(good.contains("Неизвестные номера: нет"));
        assertTrue(AnalystSelfCheck.assess("Анна [#999]").contains("Неизвестные номера: есть"));
        assertTrue(AnalystSelfCheck.assess("999").contains("Ответ только 999: да"));
        assertEquals("Нет ответа.",AnalystSelfCheck.assess(""));
    }
}
