package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class PhotoZoomMathTest {
  @Test public void fixedFingerStaysFixedAtEveryScale(){
    float pivot=160f,finger=97f,scale=1f,translation=0f;
    for(int i=0;i<1000;i++){
      float next=1f+(i%250)/60f;
      float mappedBefore=pivot+(finger-pivot-translation)/scale;
      float tx=PhotoZoomMath.translationAfterScale(finger,pivot,translation,scale,next);
      float mappedAfter=pivot+(finger-pivot-tx)/next;
      assertEquals(mappedBefore,mappedAfter,0.001f);
      translation=tx;scale=next;
    }
  }
  @Test public void limitsDragAndRejectsBrokenState(){
    assertEquals(50f,PhotoZoomMath.clamp(900,50),0);
    assertEquals(-50f,PhotoZoomMath.clamp(-500,50),0);
    assertEquals(0,PhotoZoomMath.translationAfterScale(0,0,0,0,2),0);
  }
}
