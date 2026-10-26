package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class PremiumMotionTest {
 @Test public void osReducedMotionOverridesEveryMode(){for(int mode=0;mode<4;mode++)assertEquals(0,PremiumMotion.duration(250,false,false,mode));}
 @Test public void userReducedMotionOverridesEveryMode(){for(int mode=0;mode<4;mode++)assertEquals(0,PremiumMotion.duration(250,true,true,mode));}
 @Test public void adaptiveAndClassicAllowStandardTiming(){assertEquals(220,PremiumMotion.duration(220,false,true,3));assertEquals(220,PremiumMotion.duration(220,false,true,0));}
 @Test public void economyRemovesAnimations(){assertEquals(0,PremiumMotion.duration(180,false,true,2));}
 @Test public void smoothShortensAndCaps(){assertEquals(150,PremiumMotion.duration(200,false,true,1));assertEquals(400,PremiumMotion.duration(1000,false,true,0));assertEquals(0,PremiumMotion.duration(-5,false,true,0));}
}
