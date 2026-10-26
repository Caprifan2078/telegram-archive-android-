package org.openarchive.telegram;
public final class RoundPlaybackTargetHostCheck {
    public static void main(String[] args) {
        Object first=new Object(), second=new Object();String path="video/round.mp4";
        if(!RoundPlaybackTarget.isSameTarget(path,path,first,first))throw new AssertionError("Active tile did not toggle");
        if(RoundPlaybackTarget.isSameTarget(path,path,first,second))throw new AssertionError("Another tile toggled the active video");
        if(RoundPlaybackTarget.isSameTarget(path,"other.mp4",first,first))throw new AssertionError("Another path toggled");
        if(RoundPlaybackTarget.isSameTarget(path,path,null,null))throw new AssertionError("Null tiles matched");
        System.out.println("PASS: repeated media paths do not toggle a different round-video tile");
    }
}
