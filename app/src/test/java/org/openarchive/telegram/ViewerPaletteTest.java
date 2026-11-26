package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class ViewerPaletteTest {
    @Test public void allThemesHaveReadableBodyAndSecondaryText(){
        for(int index=0;index<3;index++){
            ViewerPalette p=ViewerPalette.of(index);
            for(int bg:new int[]{p.background,p.surface,p.raised,p.incoming,p.outgoing}){
                assertTrue("Body contrast in theme "+index,AnalyticsContrastPolicy.contrast(bg,p.text)>=4.5);
            }
            for(int bg:new int[]{p.background,p.surface,p.raised}){
                assertTrue("Secondary contrast in theme "+index,AnalyticsContrastPolicy.contrast(bg,p.muted)>=4.5);
                assertTrue("Accent contrast in theme "+index,AnalyticsContrastPolicy.contrast(bg,p.accent)>=4.5);
            }
            assertEquals(index==2,p.light);
        }
    }
}
