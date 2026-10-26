package org.openarchive.telegram;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class ConversationGapNavigatorTest {
    @Test public void findsAndOrdersOnlyLongPauses(){
        ConversationGapNavigator n=new ConversationGapNavigator();
        n.accept(1,LocalDate.of(2021,1,1));
        n.accept(2,LocalDate.of(2021,1,3));
        n.accept(3,LocalDate.of(2021,1,13));
        n.accept(4,LocalDate.of(2021,2,13));
        assertEquals(2,n.gaps().size());
        assertEquals(31,n.gaps().get(0).days);
        assertEquals(3,n.gaps().get(0).fromId);
        assertEquals(4,n.gaps().get(0).toId);
    }
    @Test public void invalidAndBackwardsCannotCreateFictitiousPause(){
        ConversationGapNavigator n=new ConversationGapNavigator();
        n.accept(1,LocalDate.of(2022,1,4));
        n.accept(2,null);
        n.accept(3,LocalDate.of(2021,1,1));
        n.accept(4,LocalDate.of(2022,1,5));
        assertEquals(1,n.invalid());assertEquals(1,n.outOfOrder());
        assertTrue(n.gaps().isEmpty());
    }
    @Test public void keepsOnlyTwelveAndDoesNotMutateReturnedList(){
        ConversationGapNavigator n=new ConversationGapNavigator();
        for(int i=0;i<50;i++)n.accept(i+1,LocalDate.of(2018,1,1).plusDays(i*8L));
        assertEquals(12,n.gaps().size());
        try{n.gaps().clear();fail("mutable");}catch(UnsupportedOperationException expected){}
    }
}
