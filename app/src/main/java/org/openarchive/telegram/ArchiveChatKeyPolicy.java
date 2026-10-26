package org.openarchive.telegram;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

/** Distinct original chat keys are necessary for an unambiguous remapping of
 * annotations, favorites, avatars and saved reading state during restore. */
final class ArchiveChatKeyPolicy {
    private ArchiveChatKeyPolicy() {}
    static void requireUnique(Collection<String> keys) throws IOException {
        if (keys == null || keys.size() > 1000) throw new IOException("Некорректное число переписок");
        HashSet<String> seen = new HashSet<>();
        for (String key : keys) {
            if (key == null || !key.matches("[a-zA-Z0-9_-]{1,80}") || !seen.add(key))
                throw new IOException("Повторяющийся или недопустимый ID переписки в архиве");
        }
    }
}
