package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class DemoConversationTest {
 @Test public void includesEveryMediaKindAndAllAssetsRemainLocal(){
  DemoConversation c=new DemoConversation();
  java.util.Set<DemoConversation.Kind> types=new java.util.HashSet<>();
  for(DemoConversation.Item item:c.items()){
   types.add(item.kind);
   assertTrue(item.asset==null||item.asset.startsWith("tutorial/"));
  }
  assertEquals(DemoConversation.Kind.values().length,types.size());
  assertEquals(13,c.items().size());
 }
 @Test public void pinsAndSavedAreIndependent(){
  DemoConversation c=new DemoConversation();
  assertTrue(c.togglePin(1));assertTrue(c.toggleSave(1));
  assertFalse(c.togglePin(1));assertEquals(1,c.savedCount());
  assertEquals(0,c.pinnedCount());c.reset();assertEquals(0,c.savedCount());
 }
 @Test public void invalidIdIsIgnored(){
  DemoConversation c=new DemoConversation();assertNull(c.get(-1));
  assertFalse(c.togglePin(99));c.reply(99);assertNull(c.lastReply());
  c.reply(5);assertEquals(Long.valueOf(5),c.lastReply());
 }
 @Test public void learningMessagesAppearAndResetPerScenario(){
  DemoConversation one=new DemoConversation();
  DemoConversation two=new DemoConversation(1);
  assertEquals(13,one.items().size());assertEquals(13,two.items().size());
  assertTrue(one.appendText("Ответ на учебное сообщение"));
  assertEquals(14,one.items().size());assertEquals("Ответ на учебное сообщение",one.get(14).text);
  assertEquals(13,two.items().size());
  assertFalse(one.appendText("   "));one.reset();
  assertEquals(13,one.items().size());
  assertNotEquals(one.get(1).text,two.get(1).text);
 }
}
