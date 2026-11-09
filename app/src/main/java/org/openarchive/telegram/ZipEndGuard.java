package org.openarchive.telegram;

import java.io.IOException;
import java.io.InputStream;

/** Streaming ZIP footer check. ZipInputStream validates entries but ignores the central directory. */
final class ZipEndGuard extends InputStream {
    private static final int TAIL_BYTES = 65535 + 22 + 20 + 56;
    private final InputStream source;
    private final byte[] tail = new byte[TAIL_BYTES];
    private int next, filled;
    private long total;
    private boolean finished;

    ZipEndGuard(InputStream stream) { source = stream; }

    private void append(byte[] bytes, int off, int length) {
        total += length;
        if (length >= tail.length) {
            System.arraycopy(bytes, off + length - tail.length, tail, 0, tail.length);
            next = 0;
            filled = tail.length;
            return;
        }
        int first = Math.min(length, tail.length - next);
        System.arraycopy(bytes, off, tail, next, first);
        if (length > first) System.arraycopy(bytes, off + first, tail, 0, length - first);
        next = (next + length) % tail.length;
        filled = Math.min(filled + length, tail.length);
    }
    @Override public int read() throws IOException {
        int value = source.read();
        if (value < 0) finished = true;
        else {
            tail[next] = (byte) value;
            next = (next + 1) % tail.length;
            filled = Math.min(filled + 1, tail.length);
            total++;
        }
        return value;
    }
    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (bytes == null) throw new NullPointerException();
        if (offset < 0 || length < 0 || offset > bytes.length - length) throw new IndexOutOfBoundsException();
        if (length == 0) return 0;
        int count = source.read(bytes, offset, length);
        if (count < 0) finished = true;
        else if (count > 0) append(bytes, offset, count);
        return count;
    }
    @Override public void close() throws IOException { source.close(); }

    private static int u16(byte[] b, int p) {
        return (b[p] & 255) | ((b[p + 1] & 255) << 8);
    }
    private static long u32(byte[] b, int p) {
        return (b[p] & 255L) | ((b[p + 1] & 255L) << 8)
            | ((b[p + 2] & 255L) << 16) | ((b[p + 3] & 255L) << 24);
    }
    private static long u64(byte[] b, int p) {
        return u32(b, p) | (u32(b, p + 4) << 32);
    }
    private static boolean signature(byte[] b, int p, long value) {
        return p >= 0 && p + 4 <= b.length && u32(b, p) == value;
    }
    private static void requireDirectory(long offset, long length, long expectedEnd) throws IOException {
        if (offset < 0 || length < 0 || offset > expectedEnd || length != expectedEnd - offset)
            throw new IOException("Повреждён центральный каталог ZIP");
    }

    /** Drain remaining bytes (including encrypted final authentication) and validate ZIP/ZIP64 EOCD. */
    void requireComplete(int localEntries) throws IOException {
        if (!finished) {
            byte[] buffer = new byte[128 * 1024];
            while (read(buffer, 0, buffer.length) != -1) { }
        }
        if (filled < 22) throw new IOException("Неполный ZIP: нет завершающей записи");
        byte[] recent = new byte[filled];
        int start = (next - filled + tail.length) % tail.length;
        int first = Math.min(filled, tail.length - start);
        System.arraycopy(tail, start, recent, 0, first);
        if (first < filled) System.arraycopy(tail, 0, recent, first, filled - first);
        long base = total - filled;
        for (int i = filled - 22; i >= 0 && i >= filled - 22 - 65535; --i) {
            if (!signature(recent, i, 0x06054b50L) || i + 22 + u16(recent, i + 20) != filled)
                continue;
            if (u16(recent, i + 4) != 0 || u16(recent, i + 6) != 0)
                throw new IOException("Неподдерживаемый многотомный ZIP");
            int diskEntries = u16(recent, i + 8), entries = u16(recent, i + 10);
            long directorySize = u32(recent, i + 12), directoryOffset = u32(recent, i + 16);
            if (signature(recent, i - 20, 0x07064b50L)) {
                int loc = i - 20;
                if (u32(recent, loc + 4) != 0 || u32(recent, loc + 16) != 1)
                    throw new IOException("Неверный ZIP64 locator");
                long recordOffset = u64(recent, loc + 8);
                long relative = recordOffset - base;
                if (relative < 0 || relative > Integer.MAX_VALUE)
                    throw new IOException("Отсутствует ZIP64 end record");
                int p = (int) relative;
                if (p + 56 > loc || !signature(recent, p, 0x06064b50L))
                    throw new IOException("Повреждён ZIP64 end record");
                long recordSize = u64(recent, p + 4);
                if (recordSize < 44 || recordSize > loc - p - 12 || p + 12 + recordSize != loc)
                    throw new IOException("Неверная длина ZIP64 end record");
                if (u32(recent, p + 16) != 0 || u32(recent, p + 20) != 0 ||
                    u64(recent, p + 24) != localEntries || u64(recent, p + 32) != localEntries)
                    throw new IOException("Каталог ZIP64 не соответствует файлам");
                long zSize = u64(recent, p + 40), zOffset = u64(recent, p + 48);
                requireDirectory(zOffset, zSize, recordOffset);
                if (diskEntries != 65535 && diskEntries != localEntries)
                    throw new IOException("Неверное число элементов ZIP");
                if (entries != 65535 && entries != localEntries)
                    throw new IOException("Неверное число элементов ZIP");
                if (directorySize != 0xffffffffL && directorySize != zSize)
                    throw new IOException("Неверный размер каталога ZIP");
                if (directoryOffset != 0xffffffffL && directoryOffset != zOffset)
                    throw new IOException("Неверное смещение каталога ZIP");
            } else {
                if (entries != localEntries || diskEntries != localEntries)
                    throw new IOException("Число элементов ZIP не совпадает с каталогом");
                requireDirectory(directoryOffset, directorySize, base + i);
            }
            return;
        }
        throw new IOException("Неполный ZIP: завершающая запись каталога отсутствует");
    }
}
