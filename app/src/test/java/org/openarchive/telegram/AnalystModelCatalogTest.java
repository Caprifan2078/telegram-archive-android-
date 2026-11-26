package org.openarchive.telegram;
import java.io.File;
import java.net.URI;
import java.util.HashSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnalystModelCatalogTest {
    @Test public void oldSelectionAndCustomFileAreNotReassigned(){
        assertEquals(0,AnalystModelCatalog.initialSelection(null,0,false));
        assertEquals(1,AnalystModelCatalog.initialSelection(null,1,false));
        assertEquals(2,AnalystModelCatalog.initialSelection(null,2,false));
        assertEquals(AnalystModelCatalog.CUSTOM,AnalystModelCatalog.initialSelection(null,3,false));
        assertEquals(AnalystModelCatalog.CUSTOM,AnalystModelCatalog.initialSelection(null,-1,true));
        assertEquals(4,AnalystModelCatalog.initialSelection("qwen352",3,false));
        assertEquals("custom",AnalystModelCatalog.key(AnalystModelCatalog.CUSTOM));
    }
    @Test public void everyDownloadIsPinnedAndHasUniquePrivateFilename(){
        HashSet<String> keys=new HashSet<>();
        for(AnalystModelCatalog.Model model:AnalystModelCatalog.MODELS){
            assertTrue(keys.add(model.key));assertTrue(model.size>0);
            assertTrue(model.sha.matches("[a-f0-9]{64}"));
            assertTrue(model.url.matches("https://huggingface[.]co/[^/]+/[^/]+/resolve/[a-f0-9]{40}/[^/]+[.]litertlm"));
            assertTrue(AnalystModelDownload.allowed(URI.create(model.url)));
            assertEquals("analyst-"+model.key+".litertlm",model.file(new File("models")).getName());
        }
        assertEquals(8,keys.size());
    }
    @Test public void presentationDoesNotChangeStoredSelection(){
        java.util.HashSet<Integer> seen=new java.util.HashSet<>();
        for(int i:AnalystModelCatalog.pickerOrder())assertTrue(seen.add(i));
        assertEquals(AnalystModelCatalog.CUSTOM+1,seen.size());
        assertTrue(AnalystModelCatalog.MODELS[6].modified());
        assertTrue(AnalystModelCatalog.MODELS[7].modified());
        assertEquals(6,AnalystModelCatalog.initialSelection("huihui352",3,false));
    }
    @Test public void hybridAndCustomModelsDoNotReuseState(){
        assertTrue(AnalystModelCatalog.canReuseEngine("/private/analyst-qwen06.litertlm"));
        assertFalse(AnalystModelCatalog.canReuseEngine("/private/analyst-qwen352.litertlm"));
        assertFalse(AnalystModelCatalog.canReuseEngine("/private/analyst.litertlm"));
        assertTrue(AnalystModelCatalog.requiredFreeBytes(1000,false)>AnalystModelCatalog.requiredFreeBytes(1000,true));
        assertEquals(Long.MAX_VALUE,AnalystModelCatalog.requiredFreeBytes(Long.MAX_VALUE,false));
    }
}
