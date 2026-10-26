package org.openarchive.telegram;

/** Pure math used by pinch-to-zoom so a moving pointer does not move the picture unexpectedly. */
final class PhotoZoomMath {
    private PhotoZoomMath(){}
    static float translationAfterScale(float finger,float pivot,float translation,float before,float after){
        if(before<=0||!Float.isFinite(before)||!Float.isFinite(after)||!Float.isFinite(translation))
            return 0;
        return finger-pivot-(finger-pivot-translation)*after/before;
    }
    static float clamp(float translation,float extent){
        if(!Float.isFinite(translation)||!Float.isFinite(extent))return 0;
        float bound=Math.max(0,extent);
        return Math.max(-bound,Math.min(bound,translation));
    }
}
