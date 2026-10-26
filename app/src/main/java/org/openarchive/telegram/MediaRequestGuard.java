package org.openarchive.telegram;

/** Pure immutable-token decision for async media actions. No private data stored. */
final class MediaRequestGuard {
    private MediaRequestGuard() {}
    static boolean isCurrent(String requestedArchive,String activeArchive,
                             int requestedLoad,int activeLoad,
                             int requestedPlayback,int activePlayback){
        return requestedArchive!=null&&requestedArchive.equals(activeArchive)
            && requestedLoad==activeLoad&&requestedPlayback==activePlayback;
    }
}
