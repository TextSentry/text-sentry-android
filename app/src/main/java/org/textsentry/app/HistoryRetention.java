package org.textsentry.app;

/** A confirmed Android send has no automatic expiration; all other decisions last 24 h. */
public final class HistoryRetention {
    public static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;
    private HistoryRetention() {}

    public static boolean expired(String status, long timestamp, long now) {
        return !"sent".equals(status) && timestamp <= now - ONE_DAY_MS;
    }
}
