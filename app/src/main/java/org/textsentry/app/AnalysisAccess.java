package org.textsentry.app;

import android.content.Context;

/** One credential decision for manual review, samples and monitored SMS. */
final class AnalysisAccess {
    enum Mode {
        MANAGED, NEEDS_REFRESH, BYOK, NEEDS_SETUP;
        String endpoint() {
            return this == MANAGED ? SystemOneClient.OPENROUTER_ENDPOINT
                    : this == BYOK ? SystemOneClient.ENDPOINT : "";
        }
    }
    private AnalysisAccess() {}

    static Mode choose(boolean signedIn, boolean managedReady, boolean byokReady) {
        if (signedIn) return managedReady ? Mode.MANAGED : Mode.NEEDS_REFRESH;
        return byokReady ? Mode.BYOK : Mode.NEEDS_SETUP;
    }

    static boolean hasProvider(Context context) {
        return new AuthSessionStore(context).hasSession() || new KeyVault(context).hasKey();
    }

    static String status(Context context) {
        if (new AuthSessionStore(context).hasSession()) {
            return ManagedKeyMemory.hasActiveKey()
                    ? "✓ Google account · Jev via OpenRouter"
                    : "● Google account · key refresh on next analysis";
        }
        return new KeyVault(context).hasKey()
                ? "✓ Bring your own key · TypeSafe Jev"
                : "○ Sign in or use your own key";
    }

    /** Explicit opt-in history suggestion. No AWS/SMS content or managed-key persistence. */
    static String suggestExceptionPhrase(Context context, String message,
                                         java.util.List<String> candidates) throws Exception {
        SystemOneClient client = new SystemOneClient();
        if (new AuthSessionStore(context).hasSession()) {
            return client.suggestExceptionManaged(
                    ManagedKeyAccess.obtain(context), message, candidates);
        }
        String key = new KeyVault(context).read();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Sign in or configure your TypeSafe key");
        }
        return client.suggestException(key, SystemOneClient.MODEL, message, candidates);
    }

    /** Must be called off the UI thread. Never falls back to BYOK for signed-in users. */
    static SystemOneClient.Evaluation evaluate(Context context, String rules, String message,
                                               double threshold, SystemOneClient.TraceSink trace)
            throws Exception {
        boolean signedIn = new AuthSessionStore(context).hasSession();
        SystemOneClient client = new SystemOneClient();
        if (signedIn) {
            // Even if BYOK remains saved, it is never sent to OpenRouter.
            String key = ManagedKeyAccess.obtain(context);
            return client.evaluateManaged(key, rules, message, "STOP", threshold, trace);
        }
        String key = new KeyVault(context).read();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Sign in or configure your own TypeSafe API key");
        }
        return client.evaluate(key, SystemOneClient.MODEL, rules, message,
                "STOP", threshold, trace);
    }
}
