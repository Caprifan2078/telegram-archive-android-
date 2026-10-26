package org.openarchive.telegram;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MusicQueueTest {
    private MusicQueue.Track t(int id){return new MusicQueue.Track(id,"music/track"+id+".mp3","A","2026");}
    @Test public void sortedWindowAndSelection(){
        MusicQueue q=new MusicQueue(Arrays.asList(t(2),t(1)),Arrays.asList(t(3),t(4)),3);
        assertEquals(2,q.selected);assertEquals(1,q.tracks.get(0).attachmentId);
        assertEquals(3,q.adjacent(q.selected,true));assertEquals(1,q.adjacent(q.selected,false));
    }
    @Test public void edgesAndMissingAnchor(){
        MusicQueue q=new MusicQueue(Collections.emptyList(),Arrays.asList(t(4)),4);
        assertEquals(-1,q.adjacent(0,false));assertEquals(-1,q.adjacent(0,true));
        assertEquals(-1,new MusicQueue(null,Arrays.asList(t(4)),123).selected);
    }
    @Test public void metadataAndInputValidation(){
        assertEquals("track",MusicQueue.cleanTitle("music/track.OGG"));
        assertEquals("Без названия",MusicQueue.cleanTitle(""));
        assertTrue(MusicQueue.validChatKey("default"));
        assertFalse(MusicQueue.validChatKey("../../bad"));
        assertFalse(MusicQueue.validChatKey(""));
    }
    @Test public void queueIsImmutable(){
        MusicQueue q=new MusicQueue(null,Arrays.asList(t(4)),4);
        try{q.tracks.clear();fail();}catch(UnsupportedOperationException expected){}
    }
}
