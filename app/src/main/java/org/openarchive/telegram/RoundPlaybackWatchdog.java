package org.openarchive.telegram;

/** Only checks decoder progress; never seeks, deletes files or restarts codecs. */
final class RoundPlaybackWatchdog {
    static final long FIRST_FRAME_TIMEOUT_MS = 6500L;
    static final long STALL_TIMEOUT_MS = 5500L;
    enum State { OK, FIRST_FRAME_TIMEOUT, STALLED }
    private long openedAt = -1, lastProgressAt = -1;
    private int lastPosition = -1;
    private boolean firstFrameSeen;

    State observe(long now, int positionMs, boolean playing, boolean newFrameSeen) {
        if (now < 0) throw new IllegalArgumentException("Negative time");
        if (openedAt < 0 || now < openedAt) { openedAt=now; lastProgressAt=now; lastPosition=-1; firstFrameSeen=false; }
        if (newFrameSeen) firstFrameSeen=true;
        if (!playing) {
            // A deliberate pause must never be reported as a frozen decoder.
            if (!firstFrameSeen) openedAt=now;
            lastProgressAt=now; lastPosition=positionMs;
            return State.OK;
        }
        if (!firstFrameSeen) {
            return now-openedAt >= FIRST_FRAME_TIMEOUT_MS ? State.FIRST_FRAME_TIMEOUT : State.OK;
        }
        // Video may jump back to zero on normal loop, which is also progress.
        if (lastPosition < 0 || Math.abs(positionMs-lastPosition)>=150) {
            lastProgressAt=now;lastPosition=positionMs;
        }
        return now-lastProgressAt >= STALL_TIMEOUT_MS ? State.STALLED : State.OK;
    }
}
