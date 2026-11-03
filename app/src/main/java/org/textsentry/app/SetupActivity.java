package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** First run: sign in or BYOK, verify managed subscription, review SMS permissions,
 * then open Home. No permissions or monitoring are enabled without user action. */
public final class SetupActivity extends Activity {
    private static final int RECEIVE_SMS_REQUEST = 301;
    private boolean openingHome;
    private boolean showingOwnKey;
    private boolean subscriptionOpened;
    private SmsPermissionStep smsPermissionStep = new SmsPermissionStep();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            showingOwnKey = state.getBoolean("show_own_key", false);
            subscriptionOpened = state.getBoolean("subscription_opened", false);
            smsPermissionStep = new SmsPermissionStep(
                    state.getBoolean("sms_attempted", false),
                    state.getBoolean("sms_settings_opened", false));
        }
        display();
    }

    @Override protected void onResume() {
        super.onResume();
        // Only retry once, after the user returns from Android App info.
        if (SetupPermissions.supportsMonitoring()
                && smsPermissionStep.retryAfterSettings(SetupPermissions.hasIncoming(this))) {
            requestIncomingSms();
        }
        display();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("show_own_key", showingOwnKey);
        out.putBoolean("subscription_opened", subscriptionOpened);
        out.putBoolean("sms_attempted", smsPermissionStep.attempted());
        out.putBoolean("sms_settings_opened", smsPermissionStep.awaitingSettingsReturn());
        super.onSaveInstanceState(out);
    }

    private void display() {
        if (isFinishing() || openingHome) return;
        boolean complete = SetupGate.canEnterApp(this);
        boolean needsBilling = SetupFlow.needsSubscriptionPrompt(this);
        boolean needsPermissions = SetupFlow.needsPermissionsStep(this,
                SetupPermissions.supportsMonitoring(), SetupPermissions.hasIncoming(this));
        // An existing installation goes to Home unchanged unless it still owes
        // the one-time verified checkout for a Google-managed sideload account.
        if (!needsPermissions && SetupFlow.shouldReturnHome(complete,
                BuildConfig.WEB_CHECKOUT_ENABLED, needsBilling)) {
            openApp(MainActivity.class);
            return;
        }
        if (!SetupGate.hasAccess(this)) {
            renderLogin();
        } else if (!SetupFlow.readyForSettings(
                new AuthSessionStore(this).hasSession(), new KeyVault(this).hasKey(),
                "sideload".equals(BuildConfig.FLAVOR), SetupFlow.subscriptionPromptSeen(this))) {
            // Open the one-action checkout screen immediately after Google login.
            // If the user backs out, show a simple way to resume, not a loop.
            if (BuildConfig.WEB_CHECKOUT_ENABLED && !subscriptionOpened) {
                subscriptionOpened = true;
                startActivity(SubscriptionOnboarding.continueSetupIntent(this));
            } else {
                renderSubscription();
            }
        } else if (needsPermissions) {
            renderPermissions();
        } else {
            // Play's manual-only flavor requires no restricted SMS permissions.
            if (!SetupFlow.isComplete(this)) new Settings(this).pause();
            SetupFlow.complete(this);
            openApp(MainActivity.class);
        }
    }

    private void openApp(Class<?> destination) {
        if (openingHome || isFinishing()) return;
        openingHome = true;
        Intent next = new Intent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.isEmpty() && destination == MainActivity.class) {
            next.setAction(Intent.ACTION_SEND).setType("text/plain");
            next.putExtra(Intent.EXTRA_TEXT, shared);
        }
        startActivity(next);
        finish();
    }

    private LinearLayout stage(String label, String title, String subtitle) {
        LinearLayout root = UiKit.screen(this, "", false);
        TextView step = UiKit.text(this, label, 12, UiKit.MUTED, true);
        step.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 8));
        root.addView(step);
        TextView heading = UiKit.text(this, title, 29, UiKit.INK, true);
        heading.setLetterSpacing(-0.035f);
        root.addView(heading);
        TextView description = UiKit.text(this, subtitle, 15, UiKit.MUTED, false);
        description.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 18));
        root.addView(description);
        return root;
    }

    private void renderLogin() {
        LinearLayout root = stage("GET STARTED", "Stop sorting through spam.",
                "Sign in to subscribe, or use your own TypeSafe key.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        if (!showingOwnKey) {
            UiKit.googleSignInButton(this, card, () -> {
                Intent intent = new Intent(this, AccountActivity.class);
                intent.putExtra("welcome", true);
                intent.putExtra("auto_login", true);
                startActivity(intent);
            });
            smallLink(card, "Use your own TypeSafe API key", () ->
                    OwnKeyAgreement.show(this, () -> {
                        showingOwnKey = true;
                        renderLogin();
                    }));
        } else {
            EditText key = UiKit.input(this, card, "TypeSafe API key", "", false, true);
            key.setHint("Paste your key");
            TextView error = UiKit.description(this, card, "");
            UiKit.button(this, card, "Continue", true, () -> {
                String value = key.getText().toString().trim();
                if (value.isEmpty()) {
                    error.setText("Enter your key to continue.");
                    return;
                }
                try {
                    new KeyVault(this).save(value);
                    display();
                } catch (Exception e) {
                    error.setText("Could not save your key.");
                }
            });
            smallLink(card, "Use Google instead", () -> {
                showingOwnKey = false;
                renderLogin();
            });
        }
        TextView note = UiKit.description(this, root,
                "Private by design. Your texts never reach TextSentry servers.");
        note.setGravity(Gravity.CENTER);
    }

    private void renderSubscription() {
        LinearLayout root = stage("SUBSCRIPTION", "Activate TextSentry.",
                "Subscribe to unlock managed AI access.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        if (BuildConfig.WEB_CHECKOUT_ENABLED) {
            UiKit.button(this, card, "Subscribe to TextSentry", true, () ->
                    startActivity(SubscriptionOnboarding.continueSetupIntent(this)));
            UiKit.description(this, card,
                    "Already subscribed? Continue to automatically verify your account.");
        } else {
            UiKit.description(this, card,
                    "Subscriptions are unavailable in this build. Install the latest signed "
                            + "sideload release or use your own API key.");
        }
        smallLink(card, "Use your own key instead", () -> {
            // A Google session always takes precedence over BYOK in AI requests.
            // Remove it before the user enters a personal key.
            new Settings(this).pause();
            ManagedKeyMemory.clear();
            new AuthSessionStore(this).clear();
            SetupFlow.resetSubscriptionPrompt(this);
            showingOwnKey = false;
            display();
        });
    }

    /** One focused permission step. Sending STOP and MMS inbox access remain
     * opt-in on Home; a blocked installer permission cannot trap onboarding. */
    private void renderPermissions() {
        LinearLayout root = stage("PERMISSIONS", "Allow SMS access.",
                "TextSentry needs incoming SMS permission to monitor messages.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        boolean allowed = SetupPermissions.hasIncoming(this);
        UiKit.chip(this, card, allowed
                ? "✓ Incoming SMS allowed" : "○ Incoming SMS not allowed");
        if (!allowed) {
            if (smsPermissionStep.showAppSettings(false)) {
                SmsPermissionGuideUi.show(this, card);
                UiKit.button(this, card, "Open app settings", true, () -> {
                    smsPermissionStep.settingsOpened();
                    SetupPermissions.openAppSettings(this);
                });
            } else {
                UiKit.button(this, card, "Allow incoming SMS", true, this::requestIncomingSms);
            }
            smallLink(root, "Continue without monitoring", this::finishPermissions);
        } else {
            UiKit.heading(this, card, "Keep monitoring reliable");
            UiKit.description(this, card,
                    "Recommended: Open App battery usage (or Battery) and select "
                            + "Unrestricted. This is optional and may use more battery.");
            if (!SetupPermissions.unrestricted(this)) {
                UiKit.button(this, card, "Open battery settings", false,
                        () -> SetupPermissions.openBatterySettings(this));
            }
            UiKit.button(this, root, "Continue to TextSentry", true,
                    this::finishPermissions);
        }
        UiKit.description(this, root,
                "Monitoring starts paused. Automatic STOP replies and MMS require "
                        + "separate consent on the main page.");
    }

    private void finishPermissions() {
        if (!SetupGate.hasAccess(this) || !SetupFlow.readyForSettings(
                new AuthSessionStore(this).hasSession(), new KeyVault(this).hasKey(),
                "sideload".equals(BuildConfig.FLAVOR), SetupFlow.subscriptionPromptSeen(this))) {
            display();
            return;
        }
        // Declining SMS access must never leave stale monitoring or replies on.
        if (!SetupFlow.isComplete(this) || !SetupPermissions.hasIncoming(this)) {
            new Settings(this).pause();
        }
        SetupFlow.completePermissionsStep(this);
        SetupFlow.complete(this);
        openApp(MainActivity.class);
    }

    private void requestIncomingSms() {
        smsPermissionStep.permissionRequested();
        SetupPermissions.requestIncoming(this, RECEIVE_SMS_REQUEST);
        // Also update when Android refuses to show any permission dialog.
        display();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECEIVE_SMS_REQUEST) {
            smsPermissionStep.permissionRequested();
            display();
        }
    }

    private void smallLink(LinearLayout parent, String label, Runnable action) {
        TextView link = UiKit.description(this, parent, label);
        link.setGravity(Gravity.CENTER);
        link.setTextSize(13);
        link.setTextColor(UiKit.INK);
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> action.run());
    }
}
