package org.textsentry.app;

/** No surprise STOP after a queued SMS/MMS has grown stale. */
final class ReplyTiming {
    static final long MAX_AUTO_REPLY_AGE_MS = 5L * 60 * 1000;
    private static final long MAX_CLOCK_SKEW_MS = 2L * 60 * 1000;

    private ReplyTiming() {}

    static boolean isFresh(long receivedAtMs, long nowMs) {
        if (receivedAtMs <= 0 || nowMs <= 0) return false;
        long age = nowMs - receivedAtMs;
        return age >= -MAX_CLOCK_SKEW_MS && age <= MAX_AUTO_REPLY_AGE_MS;
    }
}
