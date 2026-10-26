package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class FontPolicyTest {
 @Test public void acceptsCaseInsensitiveTtfOtf(){
  assertTrue(FontPolicy.acceptedName("Minecraft.TTF"));
  assertTrue(FontPolicy.acceptedName("my script.otf"));
  assertFalse(FontPolicy.acceptedName("font.ttf.exe"));
  assertFalse(FontPolicy.acceptedName("photo.png"));
  assertFalse(FontPolicy.acceptedName(null));
 }
 @Test public void checksMagicNotJustFileName(){
  assertTrue(FontPolicy.validHeader(new byte[]{0,1,0,0},4));
  assertTrue(FontPolicy.validHeader(new byte[]{'O','T','T','O'},4));
  assertTrue(FontPolicy.validHeader(new byte[]{'t','r','u','e'},4));
  assertFalse(FontPolicy.validHeader(new byte[]{'Z','I','P','!'},4));
  assertFalse(FontPolicy.validHeader(new byte[]{0,1,0,0},3));
 }
 @Test public void sizesAndKnownModes(){
  assertTrue(FontPolicy.withinLimit(FontPolicy.MAX_BYTES));
  assertFalse(FontPolicy.withinLimit(FontPolicy.MAX_BYTES+1L));
  assertFalse(FontPolicy.withinLimit(0));
  assertTrue(FontPolicy.known("system"));
  assertTrue(FontPolicy.known("custom"));
  assertFalse(FontPolicy.known("unknown"));
 }
}
