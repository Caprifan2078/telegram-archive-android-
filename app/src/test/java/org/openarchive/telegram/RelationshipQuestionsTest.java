package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class RelationshipQuestionsTest {
    @Test public void eightyUniqueQuestionsInTenCategories(){
        java.util.Set<String> seen=new java.util.HashSet<>();
        assertEquals(10,RelationshipQuestions.GROUPS.length);
        for(String[] group:RelationshipQuestions.GROUPS){
            assertEquals(9,group.length);
            for(int i=1;i<group.length;i++){assertTrue(seen.add(group[i]));assertTrue(group[i].length()<=240);}
        }
        assertEquals(80,seen.size());
    }
}
