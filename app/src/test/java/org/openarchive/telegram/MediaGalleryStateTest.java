package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MediaGalleryStateTest {
    @Test public void columnsAreClampedToTelegramLikeRange(){
        assertEquals(2,MediaGalleryState.clampColumns(-10));
        assertEquals(2,MediaGalleryState.clampColumns(1));
        assertEquals(9,MediaGalleryState.clampColumns(10));
        assertEquals(2,MediaGalleryState.zoom(2,-1));
        assertEquals(9,MediaGalleryState.zoom(9,1));
        assertEquals(5,MediaGalleryState.zoom(6,-1));
    }
    @Test public void incrementalLoadNeverSkipsOrExceedsTotal(){
        assertEquals(30,MediaGalleryState.nextCount(0,95));
        assertEquals(60,MediaGalleryState.nextCount(30,95));
        assertEquals(90,MediaGalleryState.nextCount(60,95));
        assertEquals(95,MediaGalleryState.nextCount(90,95));
        assertEquals(95,MediaGalleryState.nextCount(95,95));
        assertFalse(MediaGalleryState.shouldAppend(0,200,2000,30,95,200));
        assertTrue(MediaGalleryState.shouldAppend(1650,200,2000,30,95,200));
        assertFalse(MediaGalleryState.shouldAppend(1800,200,2000,95,95,200));
    }
    @Test public void thumbnailsFitAtBothZoomExtremes(){
        for(int columns=2;columns<=9;columns++){
            int px=MediaGalleryState.tilePx(360,columns,2);
            assertTrue(px>0);
            assertTrue(columns*(px+4)<=360);
        }
    }
    @Test public void mixedPhotosAndVideoAndCircleGridAreDistinct(){
        assertTrue(MediaGalleryState.showsPhotoVideo("visual"));
        assertTrue(MediaGalleryState.supportsGrid("round"));
        assertTrue(MediaGalleryState.supportsGrid("voice"));
        assertTrue(MediaGalleryState.supportsGrid("audio"));
        assertFalse(MediaGalleryState.supportsGrid("file"));
        assertTrue(MediaGalleryState.defaultGrid("visual"));
        assertFalse(MediaGalleryState.defaultGrid("round"));
    }
}
