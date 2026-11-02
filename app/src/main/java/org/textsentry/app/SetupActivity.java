package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Guided setup: sign-in, optional subscription, SMS permissions, battery, start. */
public final class SetupActivity extends Activity {
    private static final int INCOMING_SMS_REQUEST = 301;
    private static final int OUTGOING_SMS_REQUEST = 302;
    private boolean openingHome;
    private boolean showingOwnKey;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        if (savedState != null) showingOwnKey = savedState.getBoolean("show_own_key", false);
        display();
    }

    @Override protected void onResume() {
        super.onResume();
        display();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("show_own_key", showingOwnKey);
        super.onSaveInstanceState(out);
    }

    private void display() {
        if (isFinishing() || openingHome) return;
        // Existing, already configured Google users also need the one-time
        // subscription prompt when upgrading to a checkout-enabled sideload APK.
        // No permissions are reset and no local history is deleted.
        boolean complete = SetupGate.canEnterApp(this);
        boolean needsBilling = SetupFlow.needsSubscriptionPrompt(this);
        if (SetupFlow.shouldReturnHome(complete, BuildConfig.WEB_CHECKOUT_ENABLED,
                needsBilling)) {
            goHome();
            return;
        }
        if (!SetupGate.hasAccess(this)) {
            renderLogin();
        } else if (SetupFlow.needsSubscriptionPrompt(this)) {
            renderSubscription();
        } else if (!SetupPermissions.supportsMonitoring()) {
            renderManualStart();
        } else {
            switch (SetupFlow.stage(this)) {
                case SetupFlow.PERMISSIONS:
                    renderPermissions();
                    break;
                case SetupFlow.BATTERY:
                    renderBattery();
                    break;
                default:
                    renderActivation();
            }
        }
    }

    private void goHome() {
        if (openingHome || isFinishing()) return;
        openingHome = true;
        Intent home = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        String shared = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        if (shared != null && !shared.isEmpty()) {
            home.setAction(Intent.ACTION_SEND).setType("text/plain");
            home.putExtra(Intent.EXTRA_TEXT, shared);
        }
        startActivity(home);
        finish();
    }

    private LinearLayout stage(String label, String title, String subtitle, int number) {
        LinearLayout root = UiKit.screen(this, "", false);
        LinearLayout progress = UiKit.vertical(this);
        progress.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(progress);

        LinearLayout segments = new LinearLayout(this);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        boolean extra = "sideload".equals(BuildConfig.FLAVOR)
                && new AuthSessionStore(this).hasSession()
                && !new KeyVault(this).hasKey();
        int total = SetupPermissions.supportsMonitoring() ? (extra ? 5 : 4) : 2;
        for (int i = 1; i <= total; i++) {
            android.view.View bar = new android.view.View(this);
            bar.setBackground(UiKit.shape(i <= number ? UiKit.ACCENT : 0xFFD6E0F0,
                    UiKit.dp(this, 8), 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, UiKit.dp(this, 5), 1);
            p.setMargins(UiKit.dp(this, 3), 0, UiKit.dp(this, 3), 0);
            segments.addView(bar, p);
        }
        progress.addView(segments, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 5)));

        TextView step = UiKit.text(this, label.toUpperCase(java.util.Locale.US),
                12, UiKit.MUTED, true);
        step.setPadding(0, UiKit.dp(this, 24), 0, UiKit.dp(this, 5));
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
        LinearLayout root = stage("01 / SIGN IN",
                "Stop sorting through spam.", "Less spam. More control.", 1);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.googleSignInButton(this, card, () -> {
            Intent intent = new Intent(this, AccountActivity.class);
            intent.putExtra("welcome", true);
            intent.putExtra("auto_login", true);
            startActivity(intent);
        });

        TextView skip = UiKit.description(this, card, "Or use your own TypeSafe API key");
        skip.setGravity(Gravity.CENTER);
        skip.setTextSize(13);
        skip.setTextColor(UiKit.INK);
        skip.setPaintFlags(skip.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        skip.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 16));
        skip.setClickable(true);
        skip.setFocusable(true);
        skip.setOnClickListener(v -> {
            if (showingOwnKey) {
                showingOwnKey = false;
                renderLogin();
            } else {
                OwnKeyAgreement.show(this, () -> {
                    showingOwnKey = true;
                    renderLogin();
                });
            }
        });

        if (showingOwnKey) {
            EditText key = UiKit.input(this, card, "TypeSafe API key", "", false, true);
            key.setHint("Paste your key");
            TextView error = UiKit.description(this, card, "");
            UiKit.button(this, card, "Continue with my key", true, () -> {
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
        }
        TextView note = UiKit.description(this, root, "Private by design. Your texts never reach TextSentry servers.");
        note.setGravity(Gravity.CENTER);
    }

    /** Optional for beta users, but a clear step before permission requests. */
    private void renderSubscription() {
        LinearLayout root = stage("02 / SUBSCRIPTION",
                "Set up your subscription.", "Choose managed access or your own key.", 2);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, card, "TextSentry access");
        UiKit.description(this, card,
                "Active subscription? We'll set up your key and continue automatically.");
        if (BuildConfig.WEB_CHECKOUT_ENABLED) {
            UiKit.button(this, card, "Check subscription and continue", true, () -> {
                startActivity(SubscriptionOnboarding.continueSetupIntent(this));
            });
            UiKit.description(this, card,
                    "Secure checkout opens in your browser. Payments may be real or test "
                            + "depending on the build.");
        } else {
            UiKit.description(this, card,
                    "Checkout is not available in this build. To subscribe, install the "
                            + "subscription-enabled sideload version.");
            UiKit.button(this, card, "View account", false,
                    () -> startActivity(new Intent(this, AccountActivity.class)));
        }
        UiKit.button(this, root, SetupFlow.isComplete(this)
                ? "Continue to TextSentry" : "Continue to SMS permissions", false, () -> {
            SetupFlow.completeSubscriptionPrompt(this);
            display();
        });
        UiKit.description(this, root,
                "You can set up permissions now, but managed AI analysis needs an active subscription and key.");
    }

    private void renderPermissions() {
        boolean extra = "sideload".equals(BuildConfig.FLAVOR)
                && new AuthSessionStore(this).hasSession()
                && !new KeyVault(this).hasKey();
        LinearLayout root = stage(extra ? "03 / SMS PERMISSIONS" : "02 / SMS PERMISSIONS",
                "Allow message access.",
                "Enable restricted settings if Android blocks SMS permissions.", extra ? 3 : 2);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, card, "1. Allow restricted settings");
        UiKit.description(this, card,
                "Open Android permission settings. On the app info screen, tap the "
                        + "three-dot menu (⋮) → Allow restricted settings, then return "
                        + "here. If that option is not shown, continue below.");
        UiKit.button(this, card, "Android permission settings", true,
                () -> SetupPermissions.openAppSettings(this));

        UiKit.heading(this, card, "2. Grant SMS permissions");
        UiKit.chip(this, card, SetupPermissions.hasIncoming(this)
                ? "✓ Incoming SMS allowed" : "○ Incoming SMS not allowed");
        if (!SetupPermissions.hasIncoming(this)) {
            UiKit.button(this, card, "Allow incoming SMS", false,
                    () -> SetupPermissions.requestIncoming(this, INCOMING_SMS_REQUEST));
        }
        UiKit.chip(this, card, SetupPermissions.hasOutgoing(this)
                ? "✓ Optional replies allowed" : "○ Optional replies off");
        if (!SetupPermissions.hasOutgoing(this)) {
            UiKit.button(this, card, "Allow SMS replies (optional)", false,
                    () -> SetupPermissions.requestOutgoing(this, OUTGOING_SMS_REQUEST));
        }

        UiKit.button(this, root, "Continue to battery settings", true, () -> {
            SetupFlow.advance(this, SetupFlow.BATTERY);
            display();
        });

        TextView help = UiKit.description(this, root,
                "Can't grant SMS access? See permission help.");
        help.setTextSize(12);
        help.setTextColor(UiKit.INK);
        help.setPaintFlags(help.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        help.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("SMS permission help")
                .setMessage(SetupPermissions.help())
                .setPositiveButton("Open app settings", (d, w) ->
                        SetupPermissions.openAppSettings(this))
                .setNegativeButton("Close", null).show());
    }

    private void renderBattery() {
        boolean extra = "sideload".equals(BuildConfig.FLAVOR)
                && new AuthSessionStore(this).hasSession()
                && !new KeyVault(this).hasKey();
        LinearLayout root = stage(extra ? "04 / BACKGROUND ACCESS" : "03 / BACKGROUND ACCESS",
                "Keep TextSentry running.",
                "Choose Unrestricted in Android's battery settings to help prevent delays.",
                extra ? 4 : 3);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        TextView title = UiKit.heading(this, card, "Set battery use to Unrestricted");
        title.setTextSize(23);
        TextView intro = UiKit.text(this,
                "Android may pause background processing to save battery. Follow these steps:",
                17, UiKit.INK, false);
        intro.setPadding(0, UiKit.dp(this, 12), 0, UiKit.dp(this, 8));
        card.addView(intro);
        TextView status = UiKit.chip(this, card, SetupPermissions.unrestricted(this)
                ? "✓ Battery optimization exemption detected"
                : "○ Battery optimization exemption not detected");
        status.setTextSize(15);

        batteryStep(card, "1. Tap the button below to open TextSentry settings.");
        android.widget.Button settings = UiKit.button(this, card,
                "Open TextSentry app settings", true,
                () -> SetupPermissions.openBatterySettings(this));
        settings.setTextSize(16);
        batteryStep(card, "2. In App info, tap App battery usage (or Battery).");
        batteryStep(card, "3. Select Unrestricted, not Optimized or Restricted.");
        batteryStep(card, "4. Return to TextSentry to check the updated status.");

        UiKit.button(this, root, "Continue to start monitoring", false, () -> {
            SetupFlow.advance(this, SetupFlow.ACTIVATE);
            display();
        });
        TextView note = UiKit.text(this,
                "Optional: this does not grant SMS access and may use more battery.",
                14, UiKit.MUTED, false);
        note.setPadding(0, UiKit.dp(this, 10), 0, UiKit.dp(this, 8));
        root.addView(note);
    }

    /** Large, high-contrast instructions remain readable without changing other wizard steps. */
    private void batteryStep(LinearLayout parent, String instruction) {
        TextView step = UiKit.text(this, instruction, 18, UiKit.INK, false);
        step.setBackground(UiKit.shape(UiKit.HIGHLIGHT, UiKit.dp(this, 12), 0));
        step.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 14),
                UiKit.dp(this, 14), UiKit.dp(this, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = UiKit.dp(this, 12);
        parent.addView(step, params);
    }

    private void renderActivation() {
        boolean extra = "sideload".equals(BuildConfig.FLAVOR)
                && new AuthSessionStore(this).hasSession()
                && !new KeyVault(this).hasKey();
        LinearLayout root = stage(extra ? "05 / START PROTECTION" : "04 / START PROTECTION",
                "You're in control.",
                "Preview messages first. Automatic replies are optional.", extra ? 5 : 4);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.chip(this, card, SetupPermissions.hasIncoming(this)
                ? "✓ Incoming SMS ready" : "○ Incoming SMS permission needed");

        if (BuildConfig.WEB_CHECKOUT_ENABLED
                && new AuthSessionStore(this).hasSession()) {
            UiKit.button(this, card, "Subscription options", false,
                    () -> startActivity(new Intent(this, AccountActivity.class)));
        }

        if (SetupPermissions.hasIncoming(this)) {
            UiKit.button(this, card, "Start monitoring", true, () -> {
                if (!SetupGate.hasAccess(this) || !SetupPermissions.hasIncoming(this)) {
                    display();
                    return;
                }
                new Settings(this).setAutomation(true, false);
                SetupFlow.complete(this);
                goHome();
            });
        } else {
            UiKit.button(this, card, "Allow incoming SMS", true,
                    () -> SetupPermissions.requestIncoming(this, INCOMING_SMS_REQUEST));
            UiKit.button(this, card, "Open permission settings", false, () ->
                    SetupPermissions.openAppSettings(this));
            UiKit.button(this, card, "Use app without monitoring", false, () -> {
                new Settings(this).pause();
                SetupFlow.complete(this);
                goHome();
            });
        }
        UiKit.description(this, root, "Preview logs suggestions; it never sends SMS.");
    }

    private void renderManualStart() {
        LinearLayout root = stage("02 / START REVIEWING", "You're in control.",
                "Choose which messages to review. No automatic SMS access.", 2);
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.button(this, card, "Start reviewing", true, () -> {
            SetupFlow.complete(this);
            goHome();
        });
        UiKit.description(this, card, "Every analysis needs your approval.");
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions,
                                                     int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == INCOMING_SMS_REQUEST || code == OUTGOING_SMS_REQUEST) display();
    }
}
