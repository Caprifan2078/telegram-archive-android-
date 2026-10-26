package org.openarchive.telegram;

import org.junit.Test;
import static org.junit.Assert.*;

public class TelegramAuthorPolicyTest {
    @Test public void serviceEventMustNotInheritPersonName() {
        String last = TelegramAuthorPolicy.nextAuthor("Alice", "Bob", true);
        assertEquals("Alice", last);
        assertEquals("Системное сообщение", TelegramAuthorPolicy.stageAuthor(last, true));
        assertEquals("Alice", TelegramAuthorPolicy.stageAuthor(last, false));
    }
    @Test public void serviceEventMustNotPoisonNextMultipartContinuation() {
        String last = TelegramAuthorPolicy.nextAuthor("Alice", "Admin Bot", true);
        last = TelegramAuthorPolicy.nextAuthor(last, "", false);
        assertEquals("Alice", last);
        last = TelegramAuthorPolicy.nextAuthor(last, "  Bob  ", false);
        assertEquals("Bob", last);
    }
    @Test public void missingAuthorAtStartIsNotFabricated() {
        assertEquals("", TelegramAuthorPolicy.nextAuthor(null, null, false));
        assertEquals("", TelegramAuthorPolicy.stageAuthor(null, false));
    }
}
