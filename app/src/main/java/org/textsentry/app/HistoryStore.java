package org.textsentry.app;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.LongSupplier;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Encrypted local decision history. The SQLite index stores only IDs, timestamps and
 * status; sender, full text, and model outcome remain AES-256-GCM encrypted per row.
 * Confirmed Android sends do not expire. Every other status expires after 24 hours.
 */
public final class HistoryStore {
    private static final String PREF = "textsentry_message_history";
    private static final String ALIAS = "textsentry_message_history_aes_v1";
    private static final String LEGACY_DATA = "encrypted_records";
    private static final String TRACES = "encrypted_traces";
    private static final String DB_NAME = "textsentry_history.db";
    private static final String TABLE = "messages";
    private static final Object LOCK = new Object();
    private static HistoryDatabase sharedDatabase;

    public static final int MAX_TRACES = 5; // Raw responses can be up to 64 KiB.

    private final Context context;
    private final SharedPreferences prefs;
    private final LongSupplier clock;

    private static final class HistoryDatabase extends SQLiteOpenHelper {
        HistoryDatabase(Context context) {
            super(context, DB_NAME, null, 1);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE messages ("
                    + "id TEXT PRIMARY KEY NOT NULL, "
                    + "timestamp INTEGER NOT NULL, "
                    + "status TEXT NOT NULL, "
                    + "payload BLOB NOT NULL)");
            db.execSQL("CREATE INDEX history_time ON messages(timestamp DESC)");
            db.execSQL("CREATE INDEX history_expire ON messages(status, timestamp)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new IllegalStateException("Unsupported history database schema");
        }
    }

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

    public static final class Summary {
        public final int total, replied, preview;
        Summary(int total, int replied, int preview) {
            this.total = total;
            this.replied = replied;
            this.preview = preview;
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
        this(context, System::currentTimeMillis);
    }

    /** Injectable clock for deterministic instrumentation tests. */
    HistoryStore(Context context, LongSupplier clock) {
        this.context = context.getApplicationContext();
        this.clock = clock;
        prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private SQLiteDatabase database() {
        if (sharedDatabase == null) sharedDatabase = new HistoryDatabase(context);
        return sharedDatabase.getWritableDatabase();
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

    /** Old preference archive and developer traces share the original Keystore key. */
    private JSONArray readLegacy(String slot) throws Exception {
        String saved = prefs.getString(slot, "");
        if (saved.isEmpty()) return new JSONArray();
        if (saved.length() > 600000) throw new IllegalStateException("Invalid old archive size");
        String[] fields = saved.split(":", 2);
        if (fields.length != 2) throw new IllegalStateException("Invalid encrypted archive");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(fields[0], Base64.NO_WRAP)));
        byte[] plaintext = cipher.doFinal(Base64.decode(fields[1], Base64.NO_WRAP));
        return new JSONArray(new String(plaintext, StandardCharsets.UTF_8));
    }

    private void writeTraces(JSONArray records) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(
                        records.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString(TRACES, encoded).commit()) {
            throw new IllegalStateException("Could not persist diagnostic history");
        }
    }

    /** Encrypt the entire record. Never store message text in a plaintext database column. */
    private byte[] seal(JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] ciphertext = cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer output = ByteBuffer.allocate(iv.length + ciphertext.length);
        output.put(iv).put(ciphertext);
        return output.array();
    }

    private JSONObject unseal(byte[] value) throws Exception {
        if (value == null || value.length < 29) {
            throw new IllegalStateException("Invalid encrypted history record");
        }
        ByteBuffer source = ByteBuffer.wrap(value);
        byte[] iv = new byte[12];
        source.get(iv);
        byte[] ciphertext = new byte[source.remaining()];
        source.get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new JSONObject(new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8));
    }

    private void insert(SQLiteDatabase db, JSONObject item, int conflictPolicy) throws Exception {
        String id = item.getString("id");
        if (!id.matches("[0-9a-f-]{36}")) throw new IllegalStateException("Invalid record ID");
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("timestamp", item.getLong("timestamp"));
        values.put("status", item.getString("status"));
        values.put("payload", seal(item));
        if (db.insertWithOnConflict(TABLE, null, values, conflictPolicy) == -1L) {
            throw new IllegalStateException("Could not persist encrypted history");
        }
    }

    /** Crash-safe import: do not erase old ciphertext until the new records commit. */
    private void migrate(SQLiteDatabase db) throws Exception {
        if (!prefs.contains(LEGACY_DATA)) return;
        JSONArray old = readLegacy(LEGACY_DATA);
        db.beginTransaction();
        try {
            for (int i = 0; i < old.length(); i++) {
                insert(db, old.getJSONObject(i), SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (!prefs.edit().remove(LEGACY_DATA).commit()) {
            throw new IllegalStateException("Could not complete history migration");
        }
    }

    private void prune(SQLiteDatabase db) {
        db.delete(TABLE, "status <> ? AND timestamp <= ?",
                new String[]{"sent", Long.toString(clock.getAsLong() - HistoryRetention.ONE_DAY_MS)});
    }

    private SQLiteDatabase ready() throws Exception {
        SQLiteDatabase db = database();
        migrate(db);
        prune(db);
        return db;
    }

    /** Expiration also runs on read/write so it never relies solely on periodic Android work. */
    public void pruneExpired() throws Exception {
        synchronized (LOCK) { ready(); }
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
                .put("id", id).put("timestamp", clock.getAsLong())
                .put("sender", sender).put("body", body).put("confidence", confidence)
                .put("reply", reply).put("status", status).put("source", source);
        synchronized (LOCK) {
            SQLiteDatabase db = ready();
            insert(db, item, SQLiteDatabase.CONFLICT_ABORT);
        }
        try {
            HistoryCleanupWorker.schedule(context);
        } catch (RuntimeException ignored) {
            // A scheduler failure does not affect the write or 24-hour read-time pruning.
        }
        return id;
    }

    /** Never change an already concluded send; only update an exact pending ID. */
    public void markSendResult(String id, boolean sent) throws Exception {
        if (id == null || !id.matches("[0-9a-f-]{36}")) return;
        synchronized (LOCK) {
            SQLiteDatabase db = ready();
            db.beginTransaction();
            try (Cursor cursor = db.query(TABLE, new String[]{"payload"}, "id=? AND status=?",
                    new String[]{id, "submitting"}, null, null, null)) {
                if (cursor.moveToFirst()) {
                    JSONObject value = unseal(cursor.getBlob(0));
                    value.put("status", sent ? "sent" : "send_failed");
                    ContentValues updated = new ContentValues();
                    updated.put("status", sent ? "sent" : "send_failed");
                    updated.put("payload", seal(value));
                    if (db.update(TABLE, updated, "id=? AND status=?",
                            new String[]{id, "submitting"}) != 1) {
                        throw new IllegalStateException("Could not update send result");
                    }
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    /** Main UI loads a bounded page, while confirmed send records can grow without a cap. */
    public List<Entry> list(int limit, int offset, boolean repliedOnly) throws Exception {
        if (limit < 1 || offset < 0) throw new IllegalArgumentException("Invalid history page");
        synchronized (LOCK) {
            SQLiteDatabase db = ready();
            List<Entry> result = new ArrayList<>();
            try (Cursor cursor = db.query(TABLE, new String[]{"payload"},
                    repliedOnly ? "status IN (?,?)" : null,
                    repliedOnly ? new String[]{"sent", "submitting"} : null,
                    null, null, "timestamp DESC, rowid DESC",
                    offset + "," + limit)) {
                while (cursor.moveToNext()) result.add(new Entry(unseal(cursor.getBlob(0))));
            }
            return result;
        }
    }

    /** Legacy/test API; the History UI uses paging instead. */
    public List<Entry> list() throws Exception {
        return list(Integer.MAX_VALUE, 0, false);
    }

    public Summary summary() throws Exception {
        synchronized (LOCK) {
            SQLiteDatabase db = ready();
            try (Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*), "
                    + "COALESCE(SUM(CASE WHEN status IN ('sent','submitting') THEN 1 ELSE 0 END),0), "
                    + "COALESCE(SUM(CASE WHEN status IN ('preview','manual_suggestion') THEN 1 ELSE 0 END),0) "
                    + "FROM messages", null)) {
                cursor.moveToFirst();
                return new Summary(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2));
            }
        }
    }

    /** A short, encrypted diagnostic ring; never includes HTTP Authorization headers. */
    public void addTrace(String source, String request, String response) throws Exception {
        if (!("sms".equals(source) || "mms".equals(source) || "sample".equals(source) || "manual".equals(source))
                || request == null || request.isEmpty() || request.length() > 12000
                || response == null || response.length() > 65536) {
            throw new IllegalArgumentException("Invalid model trace");
        }
        JSONObject item = new JSONObject().put("timestamp", clock.getAsLong())
                .put("source", source).put("request", request).put("response", response);
        synchronized (LOCK) {
            JSONArray existing = readLegacy(TRACES);
            JSONArray latest = new JSONArray().put(item);
            for (int i = 0; i < existing.length() && i < MAX_TRACES - 1; i++)
                latest.put(existing.getJSONObject(i));
            writeTraces(latest);
        }
    }

    public List<TraceEntry> traces() throws Exception {
        synchronized (LOCK) {
            JSONArray saved = readLegacy(TRACES);
            List<TraceEntry> output = new ArrayList<>();
            for (int i = 0; i < saved.length(); i++)
                output.add(new TraceEntry(saved.getJSONObject(i)));
            return output;
        }
    }

    public void clearTraces() {
        synchronized (LOCK) {
            if (!prefs.edit().remove(TRACES).commit())
                throw new IllegalStateException("Could not clear model traces");
        }
    }

    public void clear() {
        synchronized (LOCK) {
            SQLiteDatabase db = database();
            db.delete(TABLE, null, null);
            if (!prefs.edit().clear().commit())
                throw new IllegalStateException("Could not clear local history");
        }
    }

    public void deleteAll() throws Exception {
        synchronized (LOCK) {
            clear();
            if (sharedDatabase != null) {
                sharedDatabase.close();
                sharedDatabase = null;
            }
            if (!context.deleteDatabase(DB_NAME)) {
                // SQLite database can be absent on a first launch; records are erased above.
                if (context.getDatabasePath(DB_NAME).exists())
                    throw new IllegalStateException("Could not erase history database");
            }
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias(ALIAS)) keys.deleteEntry(ALIAS);
        }
    }
}
