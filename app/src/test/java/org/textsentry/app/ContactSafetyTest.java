package org.textsentry.app;

import static org.junit.Assert.*;
import org.junit.Test;

/** A missing/revoked permission or failed contacts lookup always blocks classification. */
public class ContactSafetyTest {
    @Test public void savedSenderNeverGetsAnalyzed() {
        assertFalse(ContactSafety.canAnalyze(true, "+15555550123", sender -> true));
    }

    @Test public void unknownNumberCanBeAnalyzedWhenLookupSucceeds() {
        assertTrue(ContactSafety.canAnalyze(true, "+15555550123", sender -> {
            assertEquals("+15555550123", sender);
            return false;
        }));
    }

    @Test public void missingContactsPermissionMustNotCallProvider() {
        assertFalse(ContactSafety.canAnalyze(false, "+15555550123", sender -> {
            fail("Contacts lookup should not run without permission");
            return false;
        }));
    }

    @Test public void lookupFailureOrUnavailableProviderFailsClosed() {
        assertFalse(ContactSafety.canAnalyze(true, "+15555550123", sender -> null));
        assertFalse(ContactSafety.canAnalyze(true, "+15555550123", sender -> {
            throw new SecurityException("revoked");
        }));
        assertFalse(ContactSafety.canAnalyze(true, "+15555550123", sender -> {
            throw new IllegalStateException("unavailable");
        }));
    }

    @Test public void invalidSenderFailsClosedBeforeLookup() {
        assertFalse(ContactSafety.canAnalyze(true, "promo", sender -> {
            fail("Invalid sender should not be queried");
            return false;
        }));
    }
}
