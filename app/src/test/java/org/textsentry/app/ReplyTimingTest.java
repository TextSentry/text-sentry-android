package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class ReplyTimingTest {
    @Test public void newlyReceivedMessagesMayAutoReply() {
        long now = 1_800_000_000_000L;
        assertTrue(ReplyTiming.isFresh(now, now));
        assertTrue(ReplyTiming.isFresh(now - ReplyTiming.MAX_AUTO_REPLY_AGE_MS, now));
    }

    @Test public void oneHourLateMmsNeverTriggersStop() {
        long now = 1_800_000_000_000L;
        assertFalse(ReplyTiming.isFresh(now - 54 * 60_000L, now));
        assertFalse(ReplyTiming.isFresh(now - ReplyTiming.MAX_AUTO_REPLY_AGE_MS - 1, now));
    }

    @Test public void missingAndFarFutureTimestampsFailClosed() {
        long now = 1_800_000_000_000L;
        assertFalse(ReplyTiming.isFresh(0L, now));
        assertFalse(ReplyTiming.isFresh(-1L, now));
        assertFalse(ReplyTiming.isFresh(now, 0L));
        assertFalse(ReplyTiming.isFresh(now + 5 * 60_000L, now));
    }
}
