package org.openarchive.telegram;

/** Pure arithmetic policy for the optional bounded on-screen chat window. No database writes. */
final class ChatWindowPolicy {
    static int cap(int mode){return mode==2?120:mode==1?200:Integer.MAX_VALUE;}
    static int trim(int mode,int visible,int pageSize){
        if(visible<0||pageSize<=0)throw new IllegalArgumentException("invalid window");
        long overflow=(long)visible-cap(mode);
        return overflow<=0?0:(int)Math.min(pageSize,overflow);
    }
    static int afterEvictionOffset(int previousOffset,int trimmed){
        if(previousOffset<0||trimmed<0||previousOffset>Integer.MAX_VALUE-trimmed)
            throw new IllegalArgumentException("invalid offset");
        return previousOffset+trimmed;
    }
}
