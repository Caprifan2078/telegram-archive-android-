package org.openarchive.telegram;
import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;
public class FuzzyWordPolicyTest {
 @Test public void singleLetterMistake(){assertTrue(FuzzyWordPolicy.near("плахо","плохо"));}
 @Test public void oneInsertion(){assertTrue(FuzzyWordPolicy.near("плхо","плохо"));}
 @Test public void oneDeletion(){assertTrue(FuzzyWordPolicy.near("плоохо","плохо"));}
 @Test public void transposition(){assertTrue(FuzzyWordPolicy.near("плоох","полох")==false);assertTrue(FuzzyWordPolicy.near("похло","похол"));}
 @Test public void guardedSmallWordsAndUnrelated(){assertFalse(FuzzyWordPolicy.near("комп","кома"));assertFalse(FuzzyWordPolicy.near("плахо","печать"));}
 @Test public void lookupIsOnlyFromActualWords(){List<String> r=FuzzyWordPolicy.suggest("плахо",Arrays.asList("компания","плохо","плохо","плоды"));assertEquals(Arrays.asList("плохо"),r);}
 @Test public void boundedInput(){assertFalse(FuzzyWordPolicy.near("п".repeat(30),"плоховатый"));}
 @Test public void appendedToPlanWithLabel(){Map<String,List<String>> m=new LinkedHashMap<>();m.put("плахо",Arrays.asList("плохо"));
  OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("плахо",true,false,m);
  assertTrue(p.fts.contains("body:плохо"));assertTrue(p.fuzzy);assertTrue(p.sourceMatch("Мне плохо").contains("исправление опечатки"));}
 @Test public void explicitOffKeepsExact(){OfflineSynonymSearch.Plan p=OfflineSynonymSearch.make("плахо",true,false);assertFalse(p.fuzzy);assertFalse(p.fts.contains("body:плохо"));}
}
