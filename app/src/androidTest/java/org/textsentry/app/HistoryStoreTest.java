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

        byte[] database = java.nio.file.Files.readAllBytes(
                app.getDatabasePath("textsentry_history.db").toPath());
        String ciphertext = new String(database, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(ciphertext.contains(original));
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
            String ciphertext = new String(java.nio.file.Files.readAllBytes(
                    app.getDatabasePath("textsentry_history.db").toPath()),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(ciphertext.contains("Vote Candidate"));
        } finally {
            history.clear();
        }
    }


    @Test public void confirmedRepliesSurviveAcrossDaysAndBeyondOriginalHundredRecordCap()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long started = 1_800_000_000_000L;
        HistoryStore history = new HistoryStore(app, () -> started);
        history.clear();
        try {
            for (int i = 0; i < 125; i++) {
                String id = history.add("+15550001234", "promo-" + i, 0.98,
                        "STOP", "submitting", "sms");
                history.markSendResult(id, true);
            }
            history.add("+15550002222", "normal message", 0.12, "",
                    "no_reply", "sms");
            history.add("+15550003333", "would reply if enabled", 0.99,
                    "STOP", "preview", "sms");
            String pending = history.add("+15550004444", "not confirmed", 0.99,
                    "STOP", "submitting", "sms");
            HistoryStore afterDay = new HistoryStore(app,
                    () -> started + HistoryRetention.ONE_DAY_MS + 1L);
            assertEquals(125, afterDay.list().size());
            assertEquals(125, afterDay.summary().total);
            assertEquals(50, afterDay.list(50, 0, true).size());
            assertEquals(25, afterDay.list(50, 100, true).size());
            assertEquals("sent", afterDay.list().get(0).status);
            afterDay.markSendResult(pending, true);
            assertEquals(125, afterDay.list().size());
            String dbBytes = new String(java.nio.file.Files.readAllBytes(
                    app.getDatabasePath("textsentry_history.db").toPath()),
                    java.nio.charset.StandardCharsets.ISO_8859_1);
            assertFalse(dbBytes.contains("promo-"));
            assertFalse(dbBytes.contains("normal message"));
        } finally {
            history.clear();
        }
    }

    @Test public void legacyEncryptedRecordsMigrateWithoutLosingConfirmedReplies()
            throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        long base = 1_800_000_000_000L;
        HistoryStore history = new HistoryStore(app, () -> base);
        history.clear();
        // Initialize the legacy Keystore alias and then seed a synthetic old-format archive.
        history.add("+15550001111", "temporary setup", 0.5, "", "no_reply", "sms");
        history.clear();
        org.json.JSONArray legacy = new org.json.JSONArray();
        legacy.put(legacyItem("11111111-1111-4111-8111-111111111111",
                "confirmed older reply", "sent", base - 90L * HistoryRetention.ONE_DAY_MS));
        legacy.put(legacyItem("22222222-2222-4222-8222-222222222222",
                "old preview", "preview", base - 2L * HistoryRetention.ONE_DAY_MS));
        legacy.put(legacyItem("33333333-3333-4333-8333-333333333333",
                "recent preview", "preview", base - 1000L));
        java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        javax.crypto.SecretKey key = ((java.security.KeyStore.SecretKeyEntry) keys.getEntry(
                "textsentry_message_history_aes_v1", null)).getSecretKey();
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
        String encoded = android.util.Base64.encodeToString(cipher.getIV(),
                android.util.Base64.NO_WRAP) + ":"
                + android.util.Base64.encodeToString(cipher.doFinal(
                        legacy.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        android.util.Base64.NO_WRAP);
        app.getSharedPreferences("textsentry_message_history", Context.MODE_PRIVATE)
                .edit().putString("encrypted_records", encoded).commit();
        try {
            assertEquals(2, new HistoryStore(app, () -> base).list().size());
            assertEquals("confirmed older reply", history.list().get(1).body);
            assertFalse(app.getSharedPreferences("textsentry_message_history",
                    Context.MODE_PRIVATE).contains("encrypted_records"));
            assertEquals(2, new HistoryStore(app, () -> base).list().size());
        } finally {
            history.clear();
        }
    }

    private static org.json.JSONObject legacyItem(String id, String body, String status, long time)
            throws Exception {
        return new org.json.JSONObject()
                .put("id", id).put("timestamp", time).put("sender", "+15550009999")
                .put("body", body).put("confidence", 0.99).put("reply", "STOP")
                .put("status", status).put("source", "sms");
    }

    @Test public void fullDataEraseRemovesHistory() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        HistoryStore history = new HistoryStore(app);
        history.clear();
        String id = history.add("+15550001234", "confirmed", 0.99, "STOP",
                "submitting", "sms");
        history.markSendResult(id, true);
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
