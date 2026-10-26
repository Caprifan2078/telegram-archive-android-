package org.openarchive.telegram;

/** Centralized motion rules. The OS reduced-motion preference always wins. */
public final class PremiumMotion {
    private PremiumMotion(){}
    public static int duration(int requested,boolean reduced,boolean osAnimatorsEnabled,int performanceMode){
        if(requested<=0||reduced||!osAnimatorsEnabled||performanceMode==2)return 0;
        int capped=Math.min(400,requested);
        return performanceMode==1?Math.round(capped*.75f):capped;
    }
}
