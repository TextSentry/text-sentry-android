package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoundupHistoryTest {
    @Test public void onlyConfirmedSendsInExactWindowIncludingLateConfirmation() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AtomicLong now = new AtomicLong(1_800_000_000_000L);
        HistoryStore store = new HistoryStore(app, now::get);
        store.clear();
        try {
            long start = now.get();
            String old = store.add("+15550001111", "Old promotion", .99, "STOP", "submitting", "sms");
            store.markSendResult(old, true);
            String later = store.add("+15550001112", "Later promotion", .99, "STOP", "submitting", "mms");
            now.incrementAndGet();
            store.markSendResult(later, true);
            store.add("+15550001113", "Preview", .99, "STOP", "preview", "sms");
            String failed = store.add("+15550001114", "Failed", .99, "STOP", "submitting", "sms");
            store.markSendResult(failed, false);
            store.add("+15550001115", "Pending", .99, "STOP", "submitting", "sms");
            store.add("", "Manual", .99, "STOP", "manual_suggestion", "manual");
            assertEquals(1, store.roundupCount(start, now.get()));
            assertEquals(later, store.roundupEntries(start, now.get(), 50).get(0).id);
            assertEquals(0, store.roundupCount(now.get(), now.get() + 1));
            store.clear();
            assertEquals(0, store.roundupCount(start, now.get()));
        } finally { store.clear(); }
    }
}
