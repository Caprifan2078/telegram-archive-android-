package org.openarchive.telegram;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class SearchHighlightPolicyTest {
    private void check(String body,String query,boolean deep,int start,int end) {
        List<int[]> ranges=SearchHighlightPolicy.ranges(body,query,deep);
        assertEquals(1,ranges.size());assertArrayEquals(new int[]{start,end},ranges.get(0));
        assertTrue(ranges.get(0)[1]<=body.length());
    }
    @Test public void caseExpansionDoesNotShiftOffsets(){check("İx","x",false,1,2);}
    @Test public void surrogatePairBeforeMatchUsesUtf16Offsets(){check("😀x","x",false,2,3);}
    @Test public void literalRegexCharactersAreNotOperators(){check("a+b","a+b",true,0,3);}
    @Test public void cyrillicCaseWorks(){check("ПРИВЕТ","привет",false,0,6);}
    @Test public void emptyInputAndMatchLimit(){
        assertTrue(SearchHighlightPolicy.ranges("abc","",false).isEmpty());
        StringBuilder s=new StringBuilder();for(int i=0;i<35;i++)s.append("x ");
        assertEquals(30,SearchHighlightPolicy.ranges(s.toString(),"x",false).size());
    }
}
