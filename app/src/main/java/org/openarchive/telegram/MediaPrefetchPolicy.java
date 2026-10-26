package org.openarchive.telegram;

import java.util.Locale;
/** Explicit small prefetch budget: images only, never videos/voice or unbounded jobs. */
final class MediaPrefetchPolicy {
    static final int MAX_NEXT_PAGE_PREVIEWS=2;
    static boolean eligible(String kind,String path){
        if(path==null||path.isEmpty()||kind==null)return false;
        String normalized=path.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        return ("image".equals(kind)||"sticker".equals(kind)) &&
            normalized.matches(".*\\.(?:png|jpe?g|webp|bmp)$");
    }
    private MediaPrefetchPolicy(){}
}
