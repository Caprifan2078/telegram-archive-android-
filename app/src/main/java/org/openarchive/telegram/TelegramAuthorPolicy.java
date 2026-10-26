package org.openarchive.telegram;

/** Stable author attribution across Telegram HTML service events and multipart imports. */
final class TelegramAuthorPolicy {
    private TelegramAuthorPolicy() {}

    static String nextAuthor(String previous, String displayed, boolean service) {
        String last = previous == null ? "" : previous;
        if (service || displayed == null || displayed.trim().isEmpty()) return last;
        return displayed.trim();
    }

    static String stageAuthor(String current, boolean service) {
        return service ? "Системное сообщение" : current == null ? "" : current;
    }
}
