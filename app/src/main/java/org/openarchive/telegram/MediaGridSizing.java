package org.openarchive.telegram;

/** Pixel-based layout policy for the scrolling media gallery.
 * Uses the actual content width, never the full device display width.
 */
final class MediaGridSizing {
    private MediaGridSizing() {}

    static final class Spec {
        final int columns, tilePx, marginPx;
        Spec(int columns,int tilePx,int marginPx){
            this.columns=columns;this.tilePx=tilePx;this.marginPx=marginPx;
        }
    }

    static Spec fit(int contentWidthPx,int minimumTilePx,int requestedMarginPx){
        if(contentWidthPx<=0||minimumTilePx<=0||requestedMarginPx<0)
            throw new IllegalArgumentException("Invalid media grid dimensions");
        // On extremely narrow content, margins must shrink before tiles do.
        int margin=Math.min(requestedMarginPx,contentWidthPx/8);
        int gutter=2*margin;
        int minSlot=minimumTilePx+gutter;
        int columns=Math.max(1,Math.min(4,contentWidthPx/minSlot));
        int slot=contentWidthPx/columns;
        int tile=Math.max(1,slot-gutter);
        return new Spec(columns,tile,margin);
    }
}
