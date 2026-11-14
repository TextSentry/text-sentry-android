package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class HistoryRetentionTest {
    private static final long NOW = 1_800_000_000_000L;

    @Test public void onlyConfirmedSendsNeverExpire() {
        assertFalse(HistoryRetention.expired("sent", 1L, NOW));
        for (String status : new String[]{
                "submitting", "send_failed", "preview", "manual_suggestion",
                "no_reply", "excepted", "blocked"}) {
            assertFalse(status, HistoryRetention.expired(status,
                    NOW - HistoryRetention.ONE_DAY_MS + 1, NOW));
            assertTrue(status, HistoryRetention.expired(status,
                    NOW - HistoryRetention.ONE_DAY_MS, NOW));
        }
    }

    @Test public void futureTimestampsDoNotExpireEarly() {
        assertFalse(HistoryRetention.expired("no_reply", NOW + 10_000, NOW));
    }
}
