package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression for switching between inline and floating round-video surfaces. */
public final class RoundPlaybackReturnPolicyTest {
    @Test public void requiresFullExitBeforeFloating(){
        assertFalse(RoundPlaybackTarget.isOutside(0,200,0,800));
        assertFalse(RoundPlaybackTarget.isOutside(790,990,0,800));
        assertTrue(RoundPlaybackTarget.isOutside(810,1010,0,800));
        assertTrue(RoundPlaybackTarget.isOutside(-220,-20,0,800));
        assertTrue(RoundPlaybackTarget.isOutside(-200,0,0,800));
    }

    @Test public void returnsOnlyWhenMostOfOriginalRoundIsVisible(){
        assertFalse(RoundPlaybackTarget.isMostlyVisible(-130,70,0,800));
        assertFalse(RoundPlaybackTarget.isMostlyVisible(-70,130,0,800));
        assertTrue(RoundPlaybackTarget.isMostlyVisible(-60,140,0,800));
        assertTrue(RoundPlaybackTarget.isMostlyVisible(25,225,0,800));
        assertFalse(RoundPlaybackTarget.isMostlyVisible(790,990,0,800));
    }

    @Test public void rejectsInvalidGeometryAndKeepsDistinctTargetsSeparate(){
        assertFalse(RoundPlaybackTarget.isMostlyVisible(20,20,0,800));
        assertFalse(RoundPlaybackTarget.isMostlyVisible(20,200,800,800));
        assertFalse(RoundPlaybackTarget.isSameTarget("a.mp4","a.mp4",new Object(),new Object()));
        Object tile=new Object();
        assertTrue(RoundPlaybackTarget.isSameTarget("a.mp4","a.mp4",tile,tile));
    }
}
