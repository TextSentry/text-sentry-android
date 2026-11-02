package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HistoryStoreTest {
    @Test public void encryptionPersistenceAndConfirmedSend() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        String original = "Exclusive promo for example message with 70% off";
        String id = history.add("+15550001234", original, 0.974, "STOP", "submitting", "sms");
        String encrypted = app.getSharedPreferences("textsentry_message_history", Context.MODE_PRIVATE)
                .getString("encrypted_records", "");
        assertFalse(encrypted.isEmpty());
        assertFalse(encrypted.contains(original));
        List<HistoryStore.Entry> saved = new HistoryStore(app).list();
        assertEquals(1, saved.size());
        assertEquals(original, saved.get(0).body);
        assertEquals(0.974, saved.get(0).confidence, 0.00001);
        assertEquals("Not deleted by TextSentry", saved.get(0).deletionStatus());
        assertEquals("submitting", saved.get(0).status);
        history.markSendResult(id, true);
        assertEquals("sent", history.list().get(0).status);
        history.markSendResult(id, false);
        assertEquals("sent", history.list().get(0).status);
        history.clear();
        assertTrue(history.list().isEmpty());
    }

    @Test public void previewAndManualAreNotMisreportedAsSentOrDeleted() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        String id = history.add("Manual review", "Make a plan to vote", 0.86, "STOP",
                "manual_suggestion", "manual");
        assertEquals("Not applicable — manual analysis", history.list().get(0).deletionStatus());
        history.markSendResult(id, true);
        assertEquals("manual_suggestion", history.list().get(0).status);
        history.add("+15550001234", "Limited weekend sale!", 0.98, "STOP", "preview", "sms");
        assertEquals("preview", history.list().get(0).status);
        history.clear();
    }

    @Test public void monitoredMmsDecisionsAreKeystoreEncryptedAndNotClaimedDeleted() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        try {
            history.add("+15555550123", "Vote Candidate for Senate", 0.97,
                    "", "blocked", "mms");
            assertEquals("mms", history.list().get(0).source);
            assertEquals("Not deleted by TextSentry", history.list().get(0).deletionStatus());
            String ciphertext = app.getSharedPreferences(
                    "textsentry_message_history", Context.MODE_PRIVATE)
                    .getString("encrypted_records", "");
            assertFalse(ciphertext.contains("Vote Candidate"));
        } finally {
            history.clear();
        }
    }

    @Test public void boundedRetentionAndFullDataErase() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        for (int i = 0; i < HistoryStore.MAX_RECORDS + 5; i++) {
            history.add("+15550001234", "example " + i, 0.7, "", "no_reply", "sms");
        }
        assertEquals(HistoryStore.MAX_RECORDS, history.list().size());
        DataControl.deleteLocalData(app);
        assertTrue(new HistoryStore(app).list().isEmpty());
    }
    @Test public void developerTracesAreBoundedEncryptedAndIndependentlyClearable() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore store = new HistoryStore(app);
        store.clear();
        String rawRequest = "Private test SMS";
        String rawResponse = "Raw yes probability 0.97";
        for (int i = 0; i < HistoryStore.MAX_TRACES + 3; i++) {
            store.addTrace("sample", rawRequest + i, rawResponse);
        }
        String ciphertext = app.getSharedPreferences("textsentry_message_history", Context.MODE_PRIVATE)
                .getString("encrypted_traces", "");
        assertFalse(ciphertext.isEmpty());
        assertFalse(ciphertext.contains("Private test SMS"));
        assertEquals(HistoryStore.MAX_TRACES, new HistoryStore(app).traces().size());
        assertEquals(rawResponse, store.traces().get(0).response);
        assertEquals("sample", store.traces().get(0).source);
        store.add("+15550001111", "Ordinary history", 0.5, "", "no_reply", "sms");
        store.clearTraces();
        assertTrue(store.traces().isEmpty());
        assertEquals(1, store.list().size());
        store.clear();
    }

}
