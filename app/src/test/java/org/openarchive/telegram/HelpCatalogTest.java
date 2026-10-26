package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class HelpCatalogTest {
 @Test public void everyChapterReachable(){assertTrue(HelpCatalog.hasEveryChapter());}
 @Test public void exportIsItsOwnSection(){
   int[] p=HelpCatalog.findChapter(28);assertNotNull(p);
   assertEquals("Экспорт из Telegram",HelpCatalog.GROUPS[p[0]].shelves[p[1]].title);
 }
 @Test public void noInvalidIndices(){
   for(HelpCatalog.Group g:HelpCatalog.GROUPS)for(HelpCatalog.Shelf s:g.shelves)
    for(int id:s.chapterIds)assertTrue(id>=1&&id<=HelpContent.all().size());
 }
 @Test public void comparisonAndPausesDocumented(){
   assertNotNull(HelpCatalog.findChapter(29));assertNotNull(HelpCatalog.findChapter(30));
   assertNotNull(HelpCatalog.findChapter(31));assertNotNull(HelpCatalog.findChapter(32));
 }
}
