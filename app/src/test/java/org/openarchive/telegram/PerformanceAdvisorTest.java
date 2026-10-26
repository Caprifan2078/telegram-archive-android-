package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class PerformanceAdvisorTest {
 @Test public void picksLowerMemoryMode(){assertEquals(2,PerformanceAdvisor.mode(128,false));assertEquals(2,PerformanceAdvisor.mode(384,true));assertEquals(1,PerformanceAdvisor.mode(256,false));}
 @Test public void cacheLimits(){assertEquals(4*1024*1024,PerformanceAdvisor.thumbnailCacheBytes(128,3));assertEquals(8*1024*1024,PerformanceAdvisor.thumbnailCacheBytes(256,3));assertEquals(16*1024*1024,PerformanceAdvisor.thumbnailCacheBytes(512,3));}
 @Test public void reportHasNoVariablePrivateFields(){String x=DiagnosticReport.render("0.25-qa",35,256,1,false,true);assertTrue(x.contains("Android API: 35"));assertTrue(x.contains("Класс памяти"));assertFalse(x.contains("/storage/"));}
}
