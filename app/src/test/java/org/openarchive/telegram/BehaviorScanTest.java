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
    @Test public void modelDownloadAllowsOnlyPinnedHttpsHosts(){
        assertTrue(AnalystModelDownload.allowed(java.net.URI.create(AnalystModelDownload.URL)));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("http://huggingface.co/model")));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("https://huggingface.co.evil.example/model")));
        assertFalse(AnalystModelDownload.allowed(java.net.URI.create("https://huggingface.co@evil.example/model")));
    }
}
