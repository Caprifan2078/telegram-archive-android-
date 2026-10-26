package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class AnalyticsContrastPolicyTest {
    private static final int DARK=0xff000000, LIGHT=0xfff5f9ff;
    @Test public void heatmapLabelsMeetWcagNormalTextContrast(){
        int[] backgrounds={0xff293449,0xff3bbd96,0xffffc857,0xffed744b};
        for(int background:backgrounds){
            int foreground=AnalyticsContrastPolicy.readableTextColor(background,DARK,LIGHT);
            assertTrue("Low contrast on "+Integer.toHexString(background),
                AnalyticsContrastPolicy.contrast(background,foreground)>=4.5);
        }
        assertEquals(LIGHT,AnalyticsContrastPolicy.readableTextColor(0xff293449,DARK,LIGHT));
        assertEquals(DARK,AnalyticsContrastPolicy.readableTextColor(0xffffc857,DARK,LIGHT));
    }
}
