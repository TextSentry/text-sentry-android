package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationService;
import android.content.ActivityNotFoundException;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/** One intentionally minimal preferences screen shared by both Android flavors. */
public final class SettingsActivity extends Activity {
    private Settings settings;
    private KeyVault vault;
    private EditText apiKey;
    private CheckBox voteCategory, politicalCategory, marketingCategory;
    private TextView generatedInstructions;
    private TextView confidenceLabel;
    private TextView keyState;
    private int confidence;
    private int developerTaps;
    private LinearLayout root, developerCard, smsPermissionCard;
    private SmsPermissionStep smsPermissionStep = new SmsPermissionStep();
    private static final int RECEIVE_SMS_REQUEST = 301;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            smsPermissionStep = new SmsPermissionStep(
                    state.getBoolean("sms_attempted", false),
                    state.getBoolean("sms_settings_opened", false));
        }
        if (!SetupGate.enforce(this)) return;
        settings = new Settings(this);
        vault = new KeyVault(this);
        confidence = settings.confidence();

        root = UiKit.screen(this, "Settings", true);
        LinearLayout provider = UiKit.card(this, root, 0xFFFFFFFF);
        TextView heading = UiKit.heading(this, provider, "AI connection");
        // Easter egg: seven taps still unlock encrypted developer diagnostics.
        heading.setOnClickListener(v -> {
            if (settings.developerMode()) return;
            if (++developerTaps >= 7) {
                developerTaps = 0;
                new AlertDialog.Builder(this)
                        .setTitle("Enable developer mode?")
                        .setMessage("Stores up to five encrypted model requests and responses on this device.")
                        .setPositiveButton("Enable", (dialog, which) -> {
                            settings.setDeveloperMode(true);
                            refreshDeveloperCard();
                        }).setNegativeButton("Cancel", null).show();
            }
        });
        UiKit.chip(this, provider, AnalysisAccess.status(this));
        UiKit.button(this, provider, "Manage account", false, () ->
                startActivity(new Intent(this, AccountActivity.class)));
        boolean managedAccount = new AuthSessionStore(this).hasSession();
        if (managedAccount) {
            UiKit.button(this, provider, "Manage subscription", true, this::manageSubscription);
        }
        if (!managedAccount) {
        apiKey = UiKit.input(this, provider, "Your TypeSafe key (optional)", "", false, true);
        apiKey.setHint("API key");
        keyState = UiKit.description(this, provider, "");
        refreshKey();
        UiKit.button(this, provider, "Remove BYOK key", false, () ->
                new AlertDialog.Builder(this)
                        .setTitle("Remove your key?")
                        .setMessage("Monitoring will pause if you aren't signed in.")
                        .setPositiveButton("Remove", (d, w) -> {
                            if (!new AuthSessionStore(this).hasSession()) settings.pause();
                            vault.clear();
                            apiKey.setText("");
                            refreshKey();
                            if (!SetupGate.hasAccess(this)) SetupGate.enforce(this);
                        }).setNegativeButton("Cancel", null).show());
        }

        LinearLayout rules = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, rules, "Filter categories");
        voteCategory = addCategory(rules, "Voting reminders", "", MessageCategories.VOTE);
        politicalCategory = addCategory(rules, "Political campaigns", "", MessageCategories.POLITICAL);
        marketingCategory = addCategory(rules, "Marketing", "", MessageCategories.MARKETING);
        generatedInstructions = UiKit.description(this, rules, "");
        generatedInstructions.setVisibility(android.view.View.GONE);
        confidenceLabel = UiKit.description(this, rules, "Minimum match: " + confidence + "%");
        SeekBar slider = new SeekBar(this);
        slider.setMax(50);
        slider.setProgress(confidence - 50);
        slider.setContentDescription("Minimum match confidence");
        rules.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                confidence = 50 + progress;
                confidenceLabel.setText("Minimum match: " + confidence + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        UiKit.button(this, rules, "Save settings", true, this::save);

        LinearLayout data = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, data, "Data & privacy");
        UiKit.button(this, data, "History", false, () ->
                startActivity(new Intent(this, HistoryActivity.class)));
        UiKit.button(this, data, "Clear history", false, () ->
                new AlertDialog.Builder(this)
                        .setTitle("Clear local history?")
                        .setMessage("Only TextSentry history is deleted, not your SMS inbox.")
                        .setPositiveButton("Clear", (d, w) -> {
                            settings.clearHistory();
                            toast("History cleared");
                        }).setNegativeButton("Cancel", null).show());
        UiKit.button(this, data, "Privacy policy", false, () -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://textsentry.org/privacy.html"))));
        UiKit.button(this, data, "Delete app data", false, () ->
                new AlertDialog.Builder(this)
                        .setTitle("Erase TextSentry data?")
                        .setMessage("Removes settings, keys and local history. Does not delete SMS.")
                        .setPositiveButton("Erase", (d, w) -> {
                            DataControl.deleteLocalData(this);
                            finish();
                        }).setNegativeButton("Cancel", null).show());
        if (SetupPermissions.supportsMonitoring()) {
            // Keep Android's restricted-permission and battery guidance accessible
            // after checkout; neither permission is silently granted during setup.
            smsPermissionCard = UiKit.card(this, root, 0xFFFFFFFF);
            renderSmsPermissionCard();

            LinearLayout background = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, background, "Background operation");
            UiKit.chip(this, background, SetupPermissions.unrestricted(this)
                    ? "✓ Battery optimization exemption detected"
                    : "○ Battery optimization exemption not detected");
            batteryInstruction(background, "1. Open TextSentry app settings below.");
            UiKit.button(this, background, "Open TextSentry app settings", false,
                    () -> SetupPermissions.openBatterySettings(this));
            batteryInstruction(background, "2. Tap App battery usage (or Battery).");
            batteryInstruction(background, "3. Choose Unrestricted.");
            UiKit.description(this, background,
                    "Recommended for timely monitoring; optional and may use more battery. "
                            + "This does not grant SMS access.");
        }

        LinearLayout sampleCard = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, sampleCard, "Try it");
        EditText sample = UiKit.input(this, sampleCard, "Sample message",
                "Sale today! Reply STOP to unsubscribe.", true, false);
        TextView sampleResult = UiKit.description(this, sampleCard, "");
        UiKit.button(this, sampleCard, "Analyze sample", true, () -> {
            String content = sample.getText().toString();
            if (!MessagePolicy.eligible("+15555550123", content)) {
                sampleResult.setText("Blocked by safety rules.");
                return;
            }
            String instructions;
            try {
                instructions = MessageCategories.compose(selectedMask(), settings.prompt());
            } catch (IllegalArgumentException invalid) {
                sampleResult.setText("Check your filter rules.");
                return;
            }
            if (instructions.isEmpty()) {
                sampleResult.setText("Select a filter category first.");
                return;
            }
            sampleResult.setText("Analyzing…");
            final String policy = instructions;
            new Thread(() -> {
                String answer;
                try {
                    SystemOneClient.Evaluation result = AnalysisAccess.evaluate(this,
                            policy, content, confidence / 100.0,
                            DeveloperMode.traceSink(this, "sample"));
                    answer = (result.decision.shouldReply ? "Suggest STOP" : "No reply")
                            + " · " + Math.round(result.confidence * 100) + "% match";
                } catch (Exception ignored) {
                    answer = "Analysis unavailable. Check your connection.";
                }
                String finalAnswer = answer;
                runOnUiThread(() -> sampleResult.setText(finalAnswer));
            }, "textsentry-sample").start();
        });

    }

    /** Readable battery guidance is available in Settings, not the first-run wizard. */
    private void batteryInstruction(LinearLayout card, String instruction) {
        TextView step = UiKit.text(this, instruction, 18, UiKit.INK, false);
        step.setBackground(UiKit.shape(UiKit.HIGHLIGHT, UiKit.dp(this, 12), 0));
        step.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 14),
                UiKit.dp(this, 14), UiKit.dp(this, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = UiKit.dp(this, 12);
        card.addView(step, params);
    }

    private void renderSmsPermissionCard() {
        if (smsPermissionCard == null || isFinishing()) return;
        smsPermissionCard.removeAllViews();
        UiKit.heading(this, smsPermissionCard, "SMS access");
        boolean allowed = SetupPermissions.hasIncoming(this);
        UiKit.chip(this, smsPermissionCard, allowed
                ? "✓ Incoming SMS allowed" : "○ Incoming SMS permission needed");
        UiKit.description(this, smsPermissionCard,
                "Grant incoming SMS access before starting monitoring on Home. "
                        + "Replies remain off unless you explicitly enable them.");
        if (!allowed) {
            if (smsPermissionStep.showAppSettings(false)) {
                SmsPermissionGuideUi.show(this, smsPermissionCard);
                UiKit.button(this, smsPermissionCard, "Open app settings", true, () -> {
                    smsPermissionStep.settingsOpened();
                    SetupPermissions.openAppSettings(this);
                });
            } else {
                UiKit.button(this, smsPermissionCard, "Allow incoming SMS", true,
                        this::requestIncomingSms);
            }
        }
    }

    private void requestIncomingSms() {
        smsPermissionStep.permissionRequested();
        SetupPermissions.requestIncoming(this, RECEIVE_SMS_REQUEST);
        renderSmsPermissionCard();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("sms_attempted", smsPermissionStep.attempted());
        out.putBoolean("sms_settings_opened", smsPermissionStep.awaitingSettingsReturn());
        super.onSaveInstanceState(out);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECEIVE_SMS_REQUEST) {
            smsPermissionStep.permissionRequested();
            renderSmsPermissionCard();
        }
    }

    private void manageSubscription() {
        AuthSessionStore sessions = new AuthSessionStore(this);
        if (!sessions.hasSession()) {
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        AuthorizationService service = new AuthorizationService(this);
        try {
            AuthState state = sessions.load();
            if (state == null || !state.isAuthorized()) {
                showPortalHelp();
                service.dispose();
                return;
            }
            state.performActionWithFreshTokens(service, (accessToken, idToken, error) -> {
                if (error != null || accessToken == null) {
                    runOnUiThread(this::showPortalHelp);
                    service.dispose();
                    return;
                }
                try {
                    sessions.save(state);
                } catch (Exception ignored) {
                    runOnUiThread(this::showPortalHelp);
                    service.dispose();
                    return;
                }
                new Thread(() -> {
                    String destination = null;
                    try {
                        destination = WebCheckout.managementUrlFromAccount(
                                WebCheckout.fetchAccount(accessToken));
                    } catch (Exception ignored) {
                        // Never display tokens or provider responses.
                    }
                    final String target = destination;
                    runOnUiThread(() -> {
                        service.dispose();
                        if (isFinishing() || isDestroyed()) return;
                        if (target == null) {
                            showPortalHelp();
                            return;
                        }
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                    .addCategory(Intent.CATEGORY_BROWSABLE));
                        } catch (ActivityNotFoundException ignored) {
                            showPortalHelp();
                        }
                    });
                }, "textsentry-subscription-portal").start();
            });
        } catch (Exception ignored) {
            service.dispose();
            showPortalHelp();
        }
    }

    private void showPortalHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Manage subscription")
                .setMessage("Your subscription portal is unavailable right now. Use the customer portal link in your RevenueCat purchase or renewal email to manage or cancel billing.")
                .setPositiveButton("OK", null).show();
    }

    private CheckBox addCategory(LinearLayout parent, String title, String subtitle, int bit) {
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextSize(15);
        box.setTextColor(UiKit.INK);
        box.setPadding(0, UiKit.dp(this, 10), 0, 0);
        box.setChecked((settings.categoryMask() & bit) != 0);
        parent.addView(box);
        return box;
    }

    private int selectedMask() {
        int selected = 0;
        if (voteCategory.isChecked()) selected |= MessageCategories.VOTE;
        if (politicalCategory.isChecked()) selected |= MessageCategories.POLITICAL;
        if (marketingCategory.isChecked()) selected |= MessageCategories.MARKETING;
        return selected;
    }

    private void updatePreview() {
        try {
            String combined = MessageCategories.compose(selectedMask(), settings.prompt());
            generatedInstructions.setText(combined.isEmpty()
                    ? "No categories selected — messages will not be evaluated."
                    : "Combined AI instructions (one request):\n" + combined);
        } catch (IllegalArgumentException invalid) {
            generatedInstructions.setText("Rules are too long. Shorten the extra instructions.");
        }
    }

    private void refreshKey() {
        if (keyState == null) return;
        keyState.setText(vault.hasKey()
                ? "✓ Own key saved"
                : "Optional for signed-in users");
    }

    private void save() {
        String entered = apiKey == null ? "" : apiKey.getText().toString().trim();
        if (!entered.isEmpty()) {
            OwnKeyAgreement.show(this, () -> saveAccepted(entered));
            return;
        }
        saveAccepted("");
    }

    private void saveAccepted(String entered) {
        String policy = settings.prompt(); // Keep the developer prompt on normal saves.
        try {
            // If saving the key fails, never enable automation or silently lose settings.
            if (!entered.isEmpty()) vault.save(entered);
            settings.saveCategoryPolicy(policy, confidence, selectedMask());
            if (apiKey != null) apiKey.setText("");
            toast("Settings saved");
            finish();
        } catch (Exception ex) {
            toast("Couldn't save. Check the key and instructions.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        if (root != null) {
            if (SetupPermissions.supportsMonitoring()
                    && smsPermissionStep.retryAfterSettings(SetupPermissions.hasIncoming(this))) {
                requestIncomingSms();
            }
            renderSmsPermissionCard();
            refreshDeveloperCard();
        }
    }

    private void refreshDeveloperCard() {
        if (!settings.developerMode()) {
            if (developerCard != null) {
                root.removeView(developerCard);
                developerCard = null;
            }
            return;
        }
        if (developerCard != null) return;
        developerCard = UiKit.card(this, root, UiKit.HIGHLIGHT);
        UiKit.heading(this, developerCard, "Developer mode");

        UiKit.button(this, developerCard, "Open developer tools  →", true, () ->
                startActivity(new Intent(this, DeveloperActivity.class)));
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
