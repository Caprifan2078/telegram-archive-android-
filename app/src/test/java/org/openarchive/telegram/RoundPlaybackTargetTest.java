package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoundPlaybackTargetTest {
    @Test public void onlyThePlayingMessageCanToggleItsDecoder(){
        Object first=new Object(), second=new Object();
        assertTrue(RoundPlaybackTarget.isSameTarget("video/round.mp4","video/round.mp4",first,first));
        assertFalse(RoundPlaybackTarget.isSameTarget("video/round.mp4","video/round.mp4",first,second));
        assertFalse(RoundPlaybackTarget.isSameTarget("video/round.mp4","video/other.mp4",first,first));
        assertFalse(RoundPlaybackTarget.isSameTarget(null,"video/round.mp4",first,first));
        assertFalse(RoundPlaybackTarget.isSameTarget("video/round.mp4","video/round.mp4",null,null));
    }
}
