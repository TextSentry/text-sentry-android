package org.textsentry.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Private, AES-GCM-encrypted, bounded local audit history. No inbox access.
 * Only the app's processed messages are recorded, never the complete SMS inbox.
 */
public final class HistoryStore {
    private static final String PREF = "textsentry_message_history";
    private static final String ALIAS = "textsentry_message_history_aes_v1";
    private static final String DATA = "encrypted_records";
    private static final String TRACES = "encrypted_traces";
    private static final Object LOCK = new Object();
    public static final int MAX_RECORDS = 100;
    public static final int MAX_TRACES = 5; // Raw responses can be up to 64 KiB.
    private final SharedPreferences prefs;

    public static final class Entry {
        public final String id, sender, body, reply, status, source;
        public final double confidence;
        public final long timestamp;
        private Entry(JSONObject value) {
            id = value.optString("id", "");
            sender = value.optString("sender", "");
            body = value.optString("body", "");
            reply = value.optString("reply", "");
            status = value.optString("status", "");
            source = value.optString("source", "");
            confidence = value.optDouble("confidence", Double.NaN);
            timestamp = value.optLong("timestamp", 0);
        }
        /** This app does not modify the system SMS inbox. */
        public String deletionStatus() {
            return ("sms".equals(source) || "mms".equals(source)) ? "Not deleted by TextSentry" :
                    "Not applicable — manual analysis";
        }
    }

    public static final class TraceEntry {
        public final String source, request, response;
        public final long timestamp;

        private TraceEntry(JSONObject value) {
            source = value.optString("source", "");
            request = value.optString("request", "");
            response = value.optString("response", "");
            timestamp = value.optLong("timestamp", 0);
        }
    }

    public HistoryStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private JSONArray readRecords() throws Exception { return readRecords(DATA); }

    private JSONArray readRecords(String slot) throws Exception {
        String saved = prefs.getString(slot, "");
        if (saved.isEmpty()) return new JSONArray();
        if (saved.length() > 600000) throw new IllegalStateException("Invalid history size");
        String[] fields = saved.split(":", 2);
        if (fields.length != 2) throw new IllegalStateException("Invalid encrypted history");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(fields[0], Base64.NO_WRAP)));
        byte[] plaintext = cipher.doFinal(Base64.decode(fields[1], Base64.NO_WRAP));
        return new JSONArray(new String(plaintext, StandardCharsets.UTF_8));
    }

    private void writeRecords(JSONArray records) throws Exception { writeRecords(DATA, records); }

    private void writeRecords(String slot, JSONArray records) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(
                        records.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString(slot, encoded).commit()) {
            throw new IllegalStateException("Could not persist message history");
        }
    }

    public String add(String sender, String body, double confidence,
                      String reply, String status, String source) throws Exception {
        if (sender == null || body == null || body.isEmpty() || body.length() > 1600
                || reply == null || reply.length() > 120 || status == null || source == null
                || !("sms".equals(source) || "mms".equals(source) || "manual".equals(source))
                || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Invalid history entry");
        }
        String id = UUID.randomUUID().toString();
        JSONObject item = new JSONObject()
                .put("id", id).put("timestamp", System.currentTimeMillis())
                .put("sender", sender).put("body", body).put("confidence", confidence)
                .put("reply", reply).put("status", status).put("source", source);
        synchronized (LOCK) {
            JSONArray existing = readRecords();
            JSONArray latest = new JSONArray();
            latest.put(item);
            for (int i = 0; i < existing.length() && i < MAX_RECORDS - 1; i++) {
                latest.put(existing.getJSONObject(i));
            }
            writeRecords(latest);
        }
        return id;
    }

    /** Never change a concluded send; only update a pending send by its exact ID. */
    public void markSendResult(String id, boolean sent) throws Exception {
        if (id == null || !id.matches("[0-9a-f-]{36}")) return;
        synchronized (LOCK) {
            JSONArray records = readRecords();
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                if (id.equals(record.optString("id"))
                        && "submitting".equals(record.optString("status"))) {
                    record.put("status", sent ? "sent" : "send_failed");
                    writeRecords(records);
                    return;
                }
            }
        }
    }

    public List<Entry> list() throws Exception {
        synchronized (LOCK) {
            JSONArray records = readRecords();
            List<Entry> list = new ArrayList<>();
            for (int i = 0; i < records.length(); i++) list.add(new Entry(records.getJSONObject(i)));
            return list;
        }
    }

    /** A short, encrypted diagnostic ring; never includes HTTP Authorization headers. */
    public void addTrace(String source, String request, String response) throws Exception {
        if (!("sms".equals(source) || "mms".equals(source) || "sample".equals(source) || "manual".equals(source))
                || request == null || request.isEmpty() || request.length() > 12000
                || response == null || response.length() > 65536) {
            throw new IllegalArgumentException("Invalid model trace");
        }
        JSONObject item = new JSONObject().put("timestamp", System.currentTimeMillis())
                .put("source", source).put("request", request).put("response", response);
        synchronized (LOCK) {
            JSONArray existing = readRecords(TRACES);
            JSONArray latest = new JSONArray().put(item);
            for (int i = 0; i < existing.length() && i < MAX_TRACES - 1; i++) {
                latest.put(existing.getJSONObject(i));
            }
            writeRecords(TRACES, latest);
        }
    }

    public List<TraceEntry> traces() throws Exception {
        synchronized (LOCK) {
            JSONArray entries = readRecords(TRACES);
            List<TraceEntry> result = new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                result.add(new TraceEntry(entries.getJSONObject(i)));
            }
            return result;
        }
    }

    public void clearTraces() {
        synchronized (LOCK) {
            if (!prefs.edit().remove(TRACES).commit()) {
                throw new IllegalStateException("Could not clear model traces");
            }
        }
    }

    public void clear() {
        synchronized (LOCK) {
            if (!prefs.edit().clear().commit()) throw new IllegalStateException("Could not clear history");
        }
    }

    public void deleteAll() throws Exception {
        synchronized (LOCK) {
            clear();
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias(ALIAS)) keys.deleteEntry(ALIAS);
        }
    }
}
