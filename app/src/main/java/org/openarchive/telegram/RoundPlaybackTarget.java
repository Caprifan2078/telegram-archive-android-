package org.openarchive.telegram;

/** The same MP4 can be referenced by distinct exported Telegram messages. */
final class RoundPlaybackTarget {
    private RoundPlaybackTarget(){}
    static boolean isSameTarget(String playingPath,String requestedPath,
                                Object activeTile,Object touchedTile){
        return playingPath!=null && playingPath.equals(requestedPath)
            && activeTile!=null && activeTile==touchedTile;
    }
}
