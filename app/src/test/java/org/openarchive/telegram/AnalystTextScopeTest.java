package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class AnalystTextScopeTest {
    @Test public void actualAppLimitsNotVendorPromises(){
        assertEquals(560,AnalystTextScope.evidenceCharacters(8));
        assertEquals(5120,AnalystTextScope.evidenceCharacters(10));
        assertEquals(0,AnalystTextScope.evidenceCharacters(AnalystModelCatalog.CUSTOM));
        assertTrue(AnalystTextScope.label(8,1000000).contains("по частям"));
        assertTrue(AnalystTextScope.label(10,400).contains("небольшой архив"));
        assertTrue(AnalystTextScope.label(8,-1).contains("за ответ"));
    }
}