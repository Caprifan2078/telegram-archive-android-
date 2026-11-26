package org.openarchive.telegram;

/** Readable chat surfaces, even with arbitrary custom participant colors. */
final class ChatSurfacePolicy {
    private ChatSurfacePolicy(){}
    static int blend(int a,int b,int percentage){
        int p=Math.max(0,Math.min(100,percentage)),q=100-p;
        return 0xff000000|((((a>>>16&255)*q+(b>>>16&255)*p+50)/100)<<16)
            |((((a>>>8&255)*q+(b>>>8&255)*p+50)/100)<<8)|(( (a&255)*q+(b&255)*p+50)/100);
    }
    static int bubble(ViewerPalette p,int author,boolean own){
        int base=own?p.outgoing:p.incoming;
        int tint=blend(base,author,p.light?8:12);
        return AnalyticsContrastPolicy.contrast(tint,p.text)>=4.5?tint:base;
    }
    static int highlight(ViewerPalette p,int base){
        int tint=blend(base,p.accent,p.light?16:12);
        return AnalyticsContrastPolicy.contrast(tint,p.text)>=4.5?tint:base;
    }
    static int secondary(ViewerPalette p,int background){
        return AnalyticsContrastPolicy.contrast(background,p.muted)>=4.5?p.muted:p.text;
    }
    static int avatarText(int color){
        return AnalyticsContrastPolicy.readableTextColor(color,0xff000000,0xffffffff);
    }
    static int searchHighlight(ViewerPalette p,int background){
        int tint=blend(background,p.light?0xffb26e08:0xffffca65,20);
        return AnalyticsContrastPolicy.contrast(tint,p.text)>=4.5?tint:background;
    }
}
