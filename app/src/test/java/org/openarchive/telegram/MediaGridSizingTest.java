package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaGridSizingTest {
    @Test public void narrowPhoneUsesTwoColumnsWithoutClipping(){
        MediaGridSizing.Spec v=MediaGridSizing.fit(280,96,2);
        assertEquals(2,v.columns);
        assertEquals(136,v.tilePx);
        assertEquals(280,v.columns*(v.tilePx+2*v.marginPx));
    }
    @Test public void conventionalPhoneUsesThreeColumns(){
        MediaGridSizing.Spec v=MediaGridSizing.fit(340,96,2);
        assertEquals(3,v.columns);
        assertTrue(v.columns*(v.tilePx+2*v.marginPx)<=340);
    }
    @Test public void tabletUsesAtMostFourColumns(){
        MediaGridSizing.Spec v=MediaGridSizing.fit(900,96,2);
        assertEquals(4,v.columns);
        assertTrue(v.columns*(v.tilePx+2*v.marginPx)<=900);
    }
    @Test public void everyPositiveWidthFitsEvenForUnusuallyNarrowWindows(){
        for(int width=1;width<=1000;width++){
            MediaGridSizing.Spec v=MediaGridSizing.fit(width,96,2);
            assertTrue(v.columns>=1&&v.columns<=4);
            assertTrue(v.tilePx>=1&&v.marginPx>=0);
            assertTrue("Width "+width,v.columns*(v.tilePx+2*v.marginPx)<=width);
        }
    }
    @Test public void invalidInputsAreRejected(){
        try{MediaGridSizing.fit(0,96,2);fail("zero width");}catch(IllegalArgumentException expected){}
        try{MediaGridSizing.fit(300,0,2);fail("zero minimum tile");}catch(IllegalArgumentException expected){}
        try{MediaGridSizing.fit(300,96,-1);fail("negative margin");}catch(IllegalArgumentException expected){}
    }
}
