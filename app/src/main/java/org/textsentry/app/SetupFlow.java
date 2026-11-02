package org.textsentry.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Guided onboarding state. Never substitutes for live permission checks. */
final class SetupFlow {
    static final int LOGIN = 0;
    static final int PERMISSIONS = 1;
    static final int BATTERY = 2;
    static final int ACTIVATE = 3;
    private static final String PREFS = "textsentry_setup_flow";
    private static final String STEP = "step";
    private static final String STEP_VERSION = "step_version";
    private static final int CURRENT_VERSION = 2;
    private static final String DONE = "completed";
    private static final String SUBSCRIPTION_PROMPT_SEEN = "subscription_prompt_seen";
    private static final String SUBSCRIPTION_HANDOFF_PENDING = "subscription_handoff_pending";

    private SetupFlow() {}

    static boolean isComplete(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(DONE, false);
    }

    static int validatedStage(int saved, boolean authenticated) {
        if (!authenticated) return LOGIN;
        return Math.max(PERMISSIONS, Math.min(ACTIVATE, saved));
    }

    /** Older onboarding placed battery before SMS permissions. Never skip SMS
     * permission guidance when upgrading a partially configured installation. */
    static int migrateLegacyStage(int old) {
        return old >= 3 ? ACTIVATE : PERMISSIONS;
    }

    static int stage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int saved = prefs.getInt(STEP, LOGIN);
        if (prefs.getInt(STEP_VERSION, 0) < CURRENT_VERSION) {
            saved = migrateLegacyStage(saved);
            prefs.edit().putInt(STEP, saved)
                    .putInt(STEP_VERSION, CURRENT_VERSION).commit();
        }
        return validatedStage(saved, SetupGate.hasAccess(context));
    }

    static void advance(Context context, int step) {
        if (!SetupGate.hasAccess(context)) return;
        int target = validatedStage(step, true);
        int current = stage(context);
        if (target > current + 1) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(STEP, target)
                .putInt(STEP_VERSION, CURRENT_VERSION).apply();
    }

    static void complete(Context context) {
        if (!SetupGate.hasAccess(context)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(STEP, ACTIVATE)
                .putInt(STEP_VERSION, CURRENT_VERSION)
                .putBoolean(DONE, true).commit();
    }

    /** Only a verified managed subscription or an explicit personal key unlocks
     * first-run settings. The Play flavor has no hosted checkout today. */
    static boolean readyForSettings(boolean googleSignedIn, boolean hasOwnKey,
                                    boolean sideloadBuild, boolean subscriptionVerified) {
        return hasOwnKey || (googleSignedIn && (!sideloadBuild || subscriptionVerified));
    }

    /** Clear a prior account's handoff before switching/signing in again. */
    static void resetSubscriptionPrompt(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(SUBSCRIPTION_PROMPT_SEEN)
                .remove(SUBSCRIPTION_HANDOFF_PENDING).commit();
    }

    /** A one-time required managed-account checkout step, separate from the saved permission stage. */
    static boolean shouldPromptSubscription(boolean googleSignedIn, boolean hasOwnKey,
                                            boolean sideloadBuild, boolean promptSeen) {
        return sideloadBuild && googleSignedIn && !hasOwnKey && !promptSeen;
    }

    /** An existing completed installation only revisits the wizard when
     * checkout is enabled and the one-time subscription prompt is outstanding. */
    static boolean shouldReturnHome(boolean setupComplete, boolean checkoutEnabled,
                                    boolean needsSubscriptionPrompt) {
        return setupComplete && !(checkoutEnabled && needsSubscriptionPrompt);
    }

    static boolean subscriptionPromptSeen(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(SUBSCRIPTION_PROMPT_SEEN, false);
    }

    static boolean needsSubscriptionPrompt(Context context) {
        return shouldPromptSubscription(new AuthSessionStore(context).hasSession(),
                new KeyVault(context).hasKey(),
                "sideload".equals(BuildConfig.FLAVOR), subscriptionPromptSeen(context));
    }

    static void beginSubscriptionHandoff(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(SUBSCRIPTION_HANDOFF_PENDING, true).commit();
    }

    static boolean subscriptionHandoffPending(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(SUBSCRIPTION_HANDOFF_PENDING, false);
    }

    static void completeSubscriptionPrompt(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(SUBSCRIPTION_PROMPT_SEEN, true)
                .remove(SUBSCRIPTION_HANDOFF_PENDING).commit();
    }

    static void reset(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
    }
}
