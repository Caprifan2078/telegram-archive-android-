package org.openarchive.telegram;

/** Small platform-independent WCAG contrast helper for analytics count labels. */
final class AnalyticsContrastPolicy {
    private AnalyticsContrastPolicy() {}

    static int readableTextColor(int background, int dark, int light) {
        return contrast(background,dark)>=contrast(background,light)?dark:light;
    }

    static double contrast(int first, int second) {
        double a=luminance(first), b=luminance(second);
        return (Math.max(a,b)+0.05)/(Math.min(a,b)+0.05);
    }

    private static double luminance(int rgb) {
        double r=linear((rgb>>16)&0xff);
        double g=linear((rgb>>8)&0xff);
        double b=linear(rgb&0xff);
        return 0.2126*r+0.7152*g+0.0722*b;
    }

    private static double linear(int channel) {
        double v=channel/255.0;
        return v<=0.04045?v/12.92:Math.pow((v+0.055)/1.055,2.4);
    }
}
