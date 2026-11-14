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
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Independently encrypted allow-list for suppressing automatic STOP. Survives clearing
 * message history, but is destroyed by Delete app data. No SMS body is stored here.
 */
public final class ExceptionStore {
    private static final String PREF = "textsentry_exceptions";
    private static final String ALIAS = "textsentry_exceptions_aes_v1";
    private static final String DATA = "rules";
    private static final Object LOCK = new Object();
    public static final int MAX_RULES = 100;

    public static final class Rule {
        public final String type;
        public final String value;
        private Rule(JSONObject value) {
            type = value.optString("type", "");
            this.value = value.optString("value", "");
        }
    }

    private final Context context;
    private final SharedPreferences prefs;

    public ExceptionStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
        keys.load(null);
        if (keys.containsAlias(ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keys.getEntry(ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private JSONArray load() throws Exception {
        String encrypted = prefs.getString(DATA, "");
        if (encrypted.isEmpty()) return new JSONArray();
        if (encrypted.length() > 100000) throw new IllegalStateException("Invalid exception size");
        String[] fields = encrypted.split(":", 2);
        if (fields.length != 2) throw new IllegalStateException("Invalid encrypted exceptions");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(fields[0], Base64.NO_WRAP)));
        return new JSONArray(new String(cipher.doFinal(
                Base64.decode(fields[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
    }

    private void save(JSONArray rules) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
                Base64.encodeToString(cipher.doFinal(
                        rules.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString(DATA, encrypted).commit()) {
            throw new IllegalStateException("Could not save exceptions");
        }
        // Drop work queued under the previous exception policy before a future send.
        new Settings(context).onExceptionsChanged();
    }

    private static String normalized(String type, String value) {
        if ("sender".equals(type)) return ExceptionRules.canonicalSender(value);
        if ("phrase".equals(type) && ExceptionRules.validPhrase(value)) {
            return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        return "";
    }

    private boolean add(String type, String value) throws Exception {
        String key = normalized(type, value);
        if (key.isEmpty()) throw new IllegalArgumentException("Invalid exception");
        synchronized (LOCK) {
            JSONArray rules = load();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject existing = rules.getJSONObject(i);
                if (type.equals(existing.optString("type"))
                        && key.equals(normalized(type, existing.optString("value")))) return false;
            }
            if (rules.length() >= MAX_RULES)
                throw new IllegalStateException("Exception limit reached");
            rules.put(new JSONObject().put("type", type).put("value",
                    "sender".equals(type) ? key : value.trim()));
            save(rules);
            return true;
        }
    }

    public boolean addSender(String sender) throws Exception {
        return add("sender", sender);
    }

    public boolean addPhrase(String phrase) throws Exception {
        return add("phrase", phrase);
    }

    public boolean remove(String type, String value) throws Exception {
        String key = normalized(type, value);
        if (key.isEmpty()) return false;
        synchronized (LOCK) {
            JSONArray existing = load();
            JSONArray remaining = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < existing.length(); i++) {
                JSONObject item = existing.getJSONObject(i);
                if (type.equals(item.optString("type"))
                        && key.equals(normalized(type, item.optString("value")))) {
                    removed = true;
                } else {
                    remaining.put(item);
                }
            }
            if (removed) save(remaining);
            return removed;
        }
    }

    /** Throws on decrypt/corruption errors; the worker must never send on a failed read. */
    public boolean matches(String sender, String message) throws Exception {
        synchronized (LOCK) {
            JSONArray rules = load();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                String type = rule.getString("type");
                String value = rule.getString("value");
                if (("sender".equals(type) && ExceptionRules.matchesSender(value, sender))
                        || ("phrase".equals(type)
                            && ExceptionRules.matchesPhrase(value, message))) return true;
            }
            return false;
        }
    }

    public List<Rule> list() throws Exception {
        synchronized (LOCK) {
            JSONArray rules = load();
            List<Rule> result = new ArrayList<>();
            for (int i = 0; i < rules.length(); i++)
                result.add(new Rule(rules.getJSONObject(i)));
            return result;
        }
    }

    public void deleteAll() throws Exception {
        synchronized (LOCK) {
            if (!prefs.edit().clear().commit())
                throw new IllegalStateException("Could not erase exceptions");
            KeyStore keys = KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias(ALIAS)) keys.deleteEntry(ALIAS);
        }
    }
}
