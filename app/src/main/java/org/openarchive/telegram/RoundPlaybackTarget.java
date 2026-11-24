package org.openarchive.telegram;

/** The same MP4 can be referenced by distinct exported Telegram messages. */
final class RoundPlaybackTarget {
    private RoundPlaybackTarget(){}
    /** Release the inline decoder only when its whole tile has left the chat viewport. */
    static boolean isOutside(int tileTop,int tileBottom,int viewportTop,int viewportBottom){
        return tileBottom<=viewportTop||tileTop>=viewportBottom;
    }

    /**
     * Chat pagination can remove a playing tile without a final scroll event.
     * A detached playing source should float, not continue invisibly.
     */
    static boolean shouldAutoFloat(boolean attached,int top,int bottom,int viewportTop,int viewportBottom){
        return !attached||isOutside(top,bottom,viewportTop,viewportBottom);
    }

    /** Require two-thirds visibility to return from the bubble, avoiding edge flicker. */
    static boolean isMostlyVisible(int tileTop,int tileBottom,int viewportTop,int viewportBottom){
        int height=tileBottom-tileTop;
        if(height<=0||viewportBottom<=viewportTop)return false;
        int overlap=Math.max(0,Math.min(tileBottom,viewportBottom)-Math.max(tileTop,viewportTop));
        return (long)overlap*3 >= (long)height*2;
    }
    static boolean isSameTarget(String playingPath,String requestedPath,
                                Object activeTile,Object touchedTile){
        return playingPath!=null && playingPath.equals(requestedPath)
            && activeTile!=null && activeTile==touchedTile;
    }
}
