package org.openarchive.telegram;

import java.io.IOException;

/** Validates ownership of every extracted media file by exactly one backed-up chat.
 * Does not allocate a list of 250,000 paths for very large exports. */
final class FullBackupLayout {
    private FullBackupLayout() {}

    static int mediaChatIndex(String path) throws IOException {
        if (path == null || !path.startsWith("media/chat-"))
            throw new IOException("Некорректная папка медиа в полной копии");
        int start = "media/chat-".length();
        int slash = path.indexOf('/', start);
        if (slash < 0 || slash == path.length() - 1)
            throw new IOException("Нет имени медиафайла в полной копии");
        String decimal = path.substring(start, slash);
        if (decimal.isEmpty() || decimal.length() > 4 ||
            (decimal.length() > 1 && decimal.charAt(0) == '0'))
            throw new IOException("Неканонический номер чата в полной копии");
        int index = 0;
        for (int j = 0; j < decimal.length(); j++) {
            char c = decimal.charAt(j);
            if (c < '0' || c > '9')
                throw new IOException("Некорректный номер чата в полной копии");
            index = index * 10 + c - '0';
        }
        return index;
    }

    static void requireWithinChatCount(int maximumSeenIndex, int chatCount) throws IOException {
        if (chatCount < 0 || chatCount > 1000 || maximumSeenIndex >= chatCount)
            throw new IOException("Медиа привязаны к несуществующей переписке");
    }
}
