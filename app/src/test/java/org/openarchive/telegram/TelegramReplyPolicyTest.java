package org.openarchive.telegram;
import org.junit.Test;
import static org.junit.Assert.*;
public class TelegramReplyPolicyTest {
  @Test public void parsesTelegramMessageIds(){
    assertEquals(123,TelegramReplyPolicy.sourceMessageId("message123"));
    assertEquals(-1,TelegramReplyPolicy.sourceMessageId("something123"));
    assertEquals(-1,TelegramReplyPolicy.sourceMessageId("message0"));
    assertEquals(-1,TelegramReplyPolicy.sourceMessageId("message9223372036854775808"));
  }
  @Test public void acceptsOnlyInternalReplyAnchors(){
    assertEquals(123,TelegramReplyPolicy.replyTargetFromHref("#go_to_message123"));
    assertEquals(123,TelegramReplyPolicy.replyTargetFromHref("messages2.html#go_to_message123"));
    assertEquals(123,TelegramReplyPolicy.replyTargetFromHref("messages.html#message123"));
    assertEquals(-1,TelegramReplyPolicy.replyTargetFromHref("https://example.com/#go_to_message123"));
    assertEquals(-1,TelegramReplyPolicy.replyTargetFromHref("../../secrets.html#message123"));
    assertEquals(-1,TelegramReplyPolicy.replyTargetFromHref("#go_to_message0"));
  }
}
