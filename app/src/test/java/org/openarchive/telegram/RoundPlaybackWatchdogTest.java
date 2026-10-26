package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class RoundPlaybackWatchdogTest {
 @Test public void missingFirstFrameIsReported(){
  RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(1000,0,true,false));
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(7499,0,true,false));
  assertEquals(RoundPlaybackWatchdog.State.FIRST_FRAME_TIMEOUT,guard.observe(7500,0,true,false));
 }
 @Test public void realPlaybackProgressPreventsFalseAlarms(){
  RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(10,0,true,true));
  for(int n=1;n<=30;n++) assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(10+n*1000,n*500,true,true));
 }
 @Test public void stallIsReportedButNormalPauseIsNot(){
  RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();
  guard.observe(0,800,true,true);guard.observe(3000,800,true,true);
  assertEquals(RoundPlaybackWatchdog.State.STALLED,guard.observe(5500,800,true,true));
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(6000,800,false,true));
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(10000,800,false,true));
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(10010,950,true,true));
 }
 @Test public void loopBackToZeroCountsAsProgress(){
  RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();
  guard.observe(100,2000,true,true);
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(5000,0,true,true));
  assertEquals(RoundPlaybackWatchdog.State.OK,guard.observe(8000,1000,true,true));
 }
 @Test public void smallJitterIsNotConsideredProgress(){
  RoundPlaybackWatchdog guard=new RoundPlaybackWatchdog();guard.observe(100,1000,true,true);
  assertEquals(RoundPlaybackWatchdog.State.STALLED,guard.observe(5700,1010,true,true));
 }
}
