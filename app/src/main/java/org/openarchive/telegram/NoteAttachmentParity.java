package org.openarchive.telegram;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** A full backup is portable only when every referenced note file is present,
 * and no orphan attachment is accepted as a successful restoration. */
final class NoteAttachmentParity {
    private NoteAttachmentParity() {}

    static void requireExact(Collection<Long> referencedNoteIds,
                             Collection<Long> restoredNoteIds) throws IOException {
        if (referencedNoteIds == null || restoredNoteIds == null)
            throw new IOException("Не получен список вложений для сверки");
        Set<Long> expected = checkedSet(referencedNoteIds);
        Set<Long> actual = checkedSet(restoredNoteIds);
        if (expected.size() != referencedNoteIds.size() ||
            actual.size() != restoredNoteIds.size())
            throw new IOException("Повторяющийся идентификатор вложения заметки");
        if (!expected.equals(actual)) {
            // Deliberately omit file/URI names from error messages.
            throw new IOException("Восстановление вложений заметок неполное: в базе " +
                                  expected.size() + ", в архиве " + actual.size());
        }
    }

    private static Set<Long> checkedSet(Collection<Long> ids) throws IOException {
        Set<Long> result = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) throw new IOException("Недопустимый ID вложения заметки");
            result.add(id);
        }
        return result;
    }
}
