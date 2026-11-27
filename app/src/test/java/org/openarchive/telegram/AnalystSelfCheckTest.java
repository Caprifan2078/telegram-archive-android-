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
    @Test public void strictConflictGateRejectsPreviouslyGreenCitationDump(){
        assertFalse(AnalystSelfCheck.conflictDefects("[#11] Борис: [#11] Анна: [#12] Борис: [#13] Анна: [#14] Борис: [#15] Анна: 999.").isEmpty());
        assertFalse(AnalystSelfCheck.conflictDefects("Оскорбление: Анна [#11]\nИзвинение: Борис [#13]").isEmpty());
        assertFalse(AnalystSelfCheck.conflictDefects("Оскорбление: Анна [#13]\nИзвинение: Анна [#11]").isEmpty());
        assertFalse(AnalystSelfCheck.conflictDefects("Оскорбление: Анна [#11]\nИзвинение: Анна [#13]\n999").isEmpty());
        assertTrue(AnalystSelfCheck.conflictDefects("Оскорбление: Анна [#11]\nИзвинение: Анна [#13]").isEmpty());
    }
    @Test public void diagnosticDoesNotAdvertiseCitationDumpAsSuccessful(){
        assertTrue(AnalystSelfCheck.assess("Оскорбление: Анна [#11]\\nИзвинение: Анна [#13]".replace("\\n","\n")).contains("Строгая проверка: пройдена"));
        assertTrue(AnalystSelfCheck.assess("[#11] Анна [#13] 999").contains("Строгая проверка: не пройдена"));
    }
}
