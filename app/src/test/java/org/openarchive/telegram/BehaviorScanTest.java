package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class BehaviorScanTest {
    @Test public void patternsCountMessagesAndKeepBothSides(){
        BehaviorScan.Report r=new BehaviorScan.Report();
        r.add(1,"A","2026-01-01","Ты идиот. Идиот!");
        r.add(2,"B","2026-01-01","Прости. Спасибо.");
        r.add(3,"A","2026-01-01","Ты всегда так делаешь");
        assertEquals(3,r.scanned);assertEquals(1,r.totals[0]);assertEquals(1,r.totals[2]);assertEquals(1,r.totals[3]);assertEquals(1,r.totals[4]);
        assertEquals(2,r.people.get("A").messages);assertEquals(1,r.people.get("B").messages);
    }
    @Test public void modelCatalogHasDistinctPinnedFiles(){
        java.util.HashSet<String> paths=new java.util.HashSet<>();
        for(AnalystModelCatalog.Model spec:AnalystModelCatalog.MODELS){
            assertTrue(spec.sha.matches("[0-9a-f]{64}"));assertTrue(spec.size>0);
            assertTrue(AnalystModelDownload.allowed(java.net.URI.create(spec.url)));
            assertTrue(paths.add(spec.file(new java.io.File("/tmp")).getName()));
        }
        assertEquals(3,paths.size());
        assertFalse(paths.contains(AnalystModelCatalog.file(new java.io.File("/tmp"),AnalystModelCatalog.CUSTOM).getName()));
    }
    @Test public void modelDownloadAllowsOnlyPinnedHttpsHosts(){
        assertTrue(AnalystModelDownload.allowed(java.net.URI.create(AnalystModelDownload.URL)));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("http://huggingface.co/model")));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("https://huggingface.co.evil.example/model")));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("https://huggingface.co@evil.example/model")));
    }
}
