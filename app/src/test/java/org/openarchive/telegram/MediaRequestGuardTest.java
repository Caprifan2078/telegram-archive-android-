package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaRequestGuardTest {
    @Test public void currentRequestAllowed(){
        assertTrue(MediaRequestGuard.isCurrent("abc","abc",3,3,8,8));
    }
    @Test public void differentArchiveRejected(){
        assertFalse(MediaRequestGuard.isCurrent("abc","def",3,3,8,8));
    }
    @Test public void switchedScreenRejected(){
        assertFalse(MediaRequestGuard.isCurrent("abc","abc",3,4,8,8));
    }
    @Test public void closedPlayerRejected(){
        assertFalse(MediaRequestGuard.isCurrent("abc","abc",3,3,8,9));
    }
    @Test public void nullArchiveRejected(){
        assertFalse(MediaRequestGuard.isCurrent(null,"abc",3,3,8,8));
    }
    @Test public void multipleSwitchesNeverBecomeCurrent(){
        for(int i=0;i<2000;i++)assertFalse(MediaRequestGuard.isCurrent("a","a",10,10+i+1,5,5));
    }
}
