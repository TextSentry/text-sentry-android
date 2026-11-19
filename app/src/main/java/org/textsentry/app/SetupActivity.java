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
        setTheme(R.style.Theme_TextSentry);
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

    private LinearLayout stage(String title, String subtitle) {
        LinearLayout root = UiKit.screen(this, "", false);
        UiKit.title(this, root, title);
        TextView description = UiKit.description(this, root, subtitle);
        description.setTextSize(16);
        description.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 20));
        return root;
    }

    private void renderLogin() {
        LinearLayout root = showingOwnKey
                ? stage("Connect your key", "Enter your TypeSafe API key to get started.")
                : UiKit.screen(this, "", false);
        if (!showingOwnKey) {
            UiKit.brandPanel(this, root, "Get started", "Sign in with Google to set up TextSentry.");
        }
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
    }

    private void renderSubscription() {
        LinearLayout root = stage("Activate TextSentry", "Connect your subscription to continue.");
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        if (BuildConfig.WEB_CHECKOUT_ENABLED) {
            UiKit.button(this, card, "Subscribe to TextSentry", true, () ->
                    startActivity(SubscriptionOnboarding.continueSetupIntent(this)));
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

    /** Incoming access is optional; monitoring still requires a separate action on Home. */
    private void renderPermissions() {
        boolean allowed = SetupPermissions.hasIncoming(this);
        LinearLayout root = allowed
                ? stage("SMS access granted", "You can start monitoring when you're ready.")
                : stage("Allow SMS access", "TextSentry needs SMS access to monitor incoming messages.");
        if (allowed) {
            UiKit.button(this, root, "Continue to TextSentry", true, this::finishPermissions);
            return;
        }
        if (smsPermissionStep.showAppSettings(false)) {
            UiKit.button(this, root, "Open app settings", true, this::openSmsSettings);
            smallLink(root, "SMS permission help", () ->
                    SmsPermissionGuideUi.show(this, this::openSmsSettings));
        } else {
            UiKit.button(this, root, "Allow incoming SMS", true, this::requestIncomingSms);
        }
        smallLink(root, "Continue without monitoring", this::finishPermissions);
    }

    private void openSmsSettings() {
        smsPermissionStep.settingsOpened();
        SetupPermissions.openAppSettings(this);
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
        link.setMinHeight(UiKit.dp(this, 48));
        link.setTextSize(13);
        link.setTextColor(UiKit.INK);
        link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        link.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16));
        link.setClickable(true);
        link.setFocusable(true);
        link.setOnClickListener(v -> action.run());
    }
}
