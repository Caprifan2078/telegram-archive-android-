package org.openarchive.telegram;

/** Pure geometry/paging rules for a photo/video/round/audio gallery. */
final class MediaGalleryState {
    static final int MIN_COLUMNS=2;
    static final int MAX_COLUMNS=9;
    static final int PAGE_SIZE=30;
    static int clampColumns(int columns){
        return Math.max(MIN_COLUMNS,Math.min(MAX_COLUMNS,columns));
    }
    static int zoom(int columns,int delta){
        return clampColumns(clampColumns(columns)+delta);
    }
    static int tilePx(int width,int columns,int gap){
        int count=clampColumns(columns);
        return Math.max(1,(Math.max(1,width)-count*Math.max(0,gap)*2)/count);
    }
    static boolean showsPhotoVideo(String kind){
        return "visual".equals(kind)||"image".equals(kind)||"video".equals(kind);
    }
    static boolean supportsGrid(String kind){
        return showsPhotoVideo(kind)||"round".equals(kind)||"voice".equals(kind)
            ||"audio".equals(kind)||"gif".equals(kind)||"sticker".equals(kind);
    }
    static boolean defaultGrid(String kind){
        return showsPhotoVideo(kind)||"gif".equals(kind)||"sticker".equals(kind);
    }
    static int nextCount(int loaded,int total){
        return Math.min(Math.max(0,total),Math.max(0,loaded)+PAGE_SIZE);
    }
    static boolean shouldAppend(int scrollY,int viewportHeight,int contentHeight,
                                int loaded,int total,int thresholdPx){
        return loaded<total && scrollY+viewportHeight>=contentHeight-Math.max(0,thresholdPx);
    }
    private MediaGalleryState(){}
}
