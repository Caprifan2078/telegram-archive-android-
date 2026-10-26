package org.openarchive.telegram;

/** Validate actual chart palette contrast without an Android SDK. */
public final class AnalyticsContrastHostCheck {
    public static void main(String[] args) {
        int dark=0xff000000, light=0xfff5f9ff;
        int[] palette={0xff293449,0xff3bbd96,0xffffc857,0xffed744b};
        for(int bg:palette){
            int fg=AnalyticsContrastPolicy.readableTextColor(bg,dark,light);
            double ratio=AnalyticsContrastPolicy.contrast(bg,fg);
            if(ratio<4.5)throw new AssertionError("WCAG AA low contrast for "+
                Integer.toHexString(bg)+": "+ratio);
            if((bg==0xff293449&&fg!=light)||(bg!=0xff293449&&fg!=dark))
                throw new AssertionError("Unexpected foreground on "+Integer.toHexString(bg));
        }
        System.out.println("PASS: all four analytics heatmap palette colors meet WCAG 4.5:1");
    }
}
