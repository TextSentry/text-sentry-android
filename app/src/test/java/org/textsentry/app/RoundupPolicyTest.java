package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

public class RoundupPolicyTest {
    @Test public void schedulesAtMostOnePerSelectedInterval() {
        long day = 86_400_000L, start = 1000L;
        assertFalse(RoundupPolicy.due(0, start, start + 7 * day));
        for (int days : new int[]{1, 2, 7}) {
            assertFalse(RoundupPolicy.due(days, start, start + days * day - 1));
            assertTrue(RoundupPolicy.due(days, start, start + days * day));
        }
        assertFalse(RoundupPolicy.due(3, start, start + 7 * day));
        assertFalse(RoundupPolicy.due(1, start, start - day));
    }
}
