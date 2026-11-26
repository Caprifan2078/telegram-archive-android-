package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;
public class ChatSurfacePolicyTest {
    @Test public void bodyAndMetadataStayReadableAcrossThemesAndCustomColors(){
        for(int theme=0;theme<3;theme++){
            ViewerPalette p=ViewerPalette.of(theme);
            for(int author:new int[]{0xff000000,0xffffffff,0xff0000ff,0xffff0000,0xff00ff00,0xff315c79,0xff665184,0xff326c65}){
                assertTrue(AnalyticsContrastPolicy.contrast(author,ChatSurfacePolicy.avatarText(author))>=4.5);
                for(boolean own:new boolean[]{false,true}){
                    int base=ChatSurfacePolicy.bubble(p,author,own);
                    for(int color:new int[]{base,ChatSurfacePolicy.highlight(p,base),ChatSurfacePolicy.searchHighlight(p,base)}){
                        assertTrue(AnalyticsContrastPolicy.contrast(color,p.text)>=4.5);
                        assertTrue(AnalyticsContrastPolicy.contrast(color,ChatSurfacePolicy.secondary(p,color))>=4.5);
                    }
                }
            }
        }
    }
    @Test public void lightAndDarkChatSurfacesAreNotIdentical(){
        int author=0xff315c79;
        assertNotEquals(ChatSurfacePolicy.bubble(ViewerPalette.of(0),author,false),ChatSurfacePolicy.bubble(ViewerPalette.of(2),author,false));
    }
}
