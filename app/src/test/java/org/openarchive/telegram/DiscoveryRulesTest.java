package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiscoveryRulesTest {
 @Test public void escapesSqlWildcards(){assertEquals("%a\\%b\\_c\\\\%",DiscoveryRules.likePattern("A%B_C\\"));}
 @Test public void refusesInvalidSource(){try{new DiscoveryRules.Hit("../../db","",2,"","","");fail();}catch(IllegalArgumentException expected){}}
 @Test public void preservesCyrillicSearch(){assertEquals("%привет%",DiscoveryRules.likePattern(" ПРИВЕТ "));}
 @Test public void showsSnippetOnly(){DiscoveryRules.Hit h=new DiscoveryRules.Hit("valid","Чат",1,"Анна","2024-01-01", "привет\nмир");assertTrue(h.label().contains("привет мир"));}
 @Test public void refusesOversizedInput(){try{DiscoveryRules.likePattern("x".repeat(121));fail();}catch(IllegalArgumentException expected){}}
}
