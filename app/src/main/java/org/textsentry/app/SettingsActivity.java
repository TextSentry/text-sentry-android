package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationService;

/** Filtering, account and device controls shared by the Android editions. */
public final class SettingsActivity extends Activity {
    static final String EXTRA_SMS_ACCESS = "focus_sms_access";
    private static final int RECEIVE_SMS_REQUEST = 301;
    private static final int SEND_SMS_REQUEST = 302;
    private static final int ROUNDUP_PERMISSION_REQUEST = 303;

    private Settings settings;
    private KeyVault vault;
    private CheckBox voteCategory, politicalCategory, marketingCategory;
    private int confidence;
    private int developerTaps;
    private LinearLayout root, developerCard, smsPermissionCard, replyCard;
    private TextView connectionStatus, batteryStatus, roundupStatus;
    private SmsPermissionStep smsPermissionStep = new SmsPermissionStep();

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
        addFilters();
        if (SetupPermissions.supportsMonitoring()) {
            replyCard = UiKit.card(this, root, 0xFFFFFFFF);
            smsPermissionCard = UiKit.card(this, root, 0xFFFFFFFF);
            if (getIntent().getBooleanExtra(EXTRA_SMS_ACCESS, false) && state == null) {
                smsPermissionStep.permissionRequested();
                smsPermissionCard.post(() -> smsPermissionCard.requestRectangleOnScreen(
                        new Rect(0, 0, smsPermissionCard.getWidth(), smsPermissionCard.getHeight()), true));
            }
            addRoundupSettings();
            addBatterySettings();
        }
        addAccount();
        addPrivacy();
        LinearLayout about = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, about, "About");
        UiKit.description(this, about, "Version " + BuildConfig.VERSION_NAME
                + " · Build " + BuildConfig.VERSION_CODE);
        AppUpdates.addSettingsAction(this, about);
        addSample();
    }

    private void addFilters() {
        LinearLayout filters = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.sectionHelp(this, filters, "Filter categories", () -> UiKit.info(this,
                "Filter categories",
                "Voting reminders cover unsolicited get-out-the-vote texts. Political campaigns "
                        + "include political surveys and issue outreach. Marketing covers promotions "
                        + "and ads. Selecting at least one category enables analysis."));
        voteCategory = addCategory(filters, "Voting reminders", MessageCategories.VOTE);
        politicalCategory = addCategory(filters, "Political campaigns", MessageCategories.POLITICAL);
        marketingCategory = addCategory(filters, "Marketing", MessageCategories.MARKETING);
        UiKit.sectionHelp(this, filters, "Minimum match", () -> UiKit.info(this, "Minimum match",
                "This is how confident Jev must be before TextSentry recommends STOP. "
                        + "A higher value makes the filter more selective."));
        TextView confidenceLabel = UiKit.description(this, filters, confidence + "%");
        SeekBar slider = new SeekBar(this);
        slider.setMax(50);
        slider.setProgress(confidence - 50);
        slider.setContentDescription("Minimum match confidence");
        filters.addView(slider, new LinearLayout.LayoutParams(-1, UiKit.dp(this, 48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                confidence = 50 + progress;
                confidenceLabel.setText(confidence + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        UiKit.button(this, filters, "Save filters", true, this::saveFilters);
        UiKit.link(this, filters, "Manage exceptions", () ->
                startActivity(new Intent(this, ExceptionsActivity.class)));
    }

    private void addAccount() {
        LinearLayout connection = UiKit.card(this, root, 0xFFFFFFFF);
        TextView heading = UiKit.heading(this, connection, "AI connection");
        // Seven taps unlock the existing encrypted developer diagnostics.
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
        connectionStatus = UiKit.description(this, connection, AnalysisAccess.status(this));
        UiKit.link(this, connection, "Manage account", () ->
                startActivity(new Intent(this, AccountActivity.class)));
        if (new AuthSessionStore(this).hasSession()) {
            UiKit.link(this, connection, "Manage subscription", this::manageSubscription);
        } else {
            UiKit.link(this, connection, "Change TypeSafe key", this::changeKey);
            UiKit.link(this, connection, "Remove TypeSafe key", () ->
                    new AlertDialog.Builder(this)
                            .setTitle("Remove your key?")
                            .setMessage("This pauses monitoring and returns you to setup.")
                            .setPositiveButton("Remove", (d, w) -> {
                                if (!new AuthSessionStore(this).hasSession()) settings.pause();
                                vault.clear();
                                SetupGate.enforce(this);
                            }).setNegativeButton("Cancel", null).show());
        }
    }

    private void changeKey() {
        LinearLayout body = UiKit.vertical(this);
        body.setPaddingRelative(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), UiKit.dp(this, 16));
        EditText key = UiKit.input(this, body, "TypeSafe API key", "", false, true);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Change TypeSafe key")
                .setView(UiKit.scrollable(this, body)).setPositiveButton("Continue", null)
                .setNegativeButton("Cancel", null).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String entered = key.getText().toString().trim();
            if (entered.isEmpty()) {
                key.setError("Enter your TypeSafe key");
                return;
            }
            OwnKeyAgreement.show(this, () -> {
                try {
                    vault.save(entered);
                    settings.invalidateQueuedWork();
                    connectionStatus.setText(AnalysisAccess.status(this));
                    dialog.dismiss();
                    toast("TypeSafe key saved");
                } catch (Exception error) {
                    key.setError("Couldn't save this key");
                }
            });
        });
    }

    private void addPrivacy() {
        LinearLayout data = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.sectionHelp(this, data, "Data & privacy", () -> UiKit.info(this, "Local history",
                "TextSentry encrypts its history on your device. Confirmed STOP replies stay until "
                        + "you clear history. Other decisions expire after 24 hours."));
        UiKit.link(this, data, "Privacy policy", () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://textsentry.org/privacy.html")));
            } catch (ActivityNotFoundException ignored) {
                toast("No browser is available");
            }
        });
        UiKit.link(this, data, "Clear history", () -> new AlertDialog.Builder(this)
                .setTitle("Clear local history?")
                .setMessage("This erases TextSentry's saved message history. Your original messages stay in your inbox.")
                .setPositiveButton("Clear", (d, w) -> {
                    settings.clearHistory();
                    toast("History cleared");
                }).setNegativeButton("Cancel", null).show());
        UiKit.link(this, data, "Delete app data", () -> new AlertDialog.Builder(this)
                .setTitle("Erase TextSentry data?")
                .setMessage("This removes settings, keys and local history. Your original messages stay in your inbox.")
                .setPositiveButton("Erase", (d, w) -> {
                    DataControl.deleteLocalData(this);
                    SetupGate.enforce(this);
                }).setNegativeButton("Cancel", null).show());
    }

    private void addBatterySettings() {
        LinearLayout battery = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.sectionHelp(this, battery, "Background operation", this::showBatteryHelp)
                .setId(R.id.battery_help);
        batteryStatus = UiKit.description(this, battery, "");
        UiKit.link(this, battery, "Open TextSentry app settings", () ->
                SetupPermissions.openBatterySettings(this));
    }

    private void showBatteryHelp() {
        LinearLayout steps = UiKit.vertical(this);
        steps.setPaddingRelative(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), UiKit.dp(this, 16));
        for (String instruction : new String[]{
                "1. Open TextSentry app settings.",
                "2. Tap App battery usage (or Battery).",
                "3. Choose Unrestricted."
        }) {
            TextView step = UiKit.description(this, steps, instruction);
            step.setTextColor(UiKit.INK);
            step.setTextSize(18);
        }
        UiKit.description(this, steps, "This can help monitoring run on time and may use more battery.");
        new AlertDialog.Builder(this).setTitle("Background operation")
                .setView(UiKit.scrollable(this, steps))
                .setPositiveButton("Open app settings", (dialog, which) ->
                        SetupPermissions.openBatterySettings(this))
                .setNegativeButton("Done", null).show();
    }

    private void renderSmsPermissionCard() {
        if (smsPermissionCard == null || isFinishing()) return;
        smsPermissionCard.removeAllViews();
        UiKit.sectionHelp(this, smsPermissionCard, "SMS access", this::showSmsHelp)
                .setId(R.id.sms_help);
        boolean allowed = SetupPermissions.hasIncoming(this);
        UiKit.description(this, smsPermissionCard, !allowed ? "Incoming SMS access needed"
                : SetupPermissions.hasInbox(this) ? "Incoming SMS and MMS access allowed"
                : "Incoming SMS allowed · MMS access needed");
        if (allowed) {
            UiKit.link(this, smsPermissionCard, "Open Android permissions", () ->
                    SetupPermissions.openAppSettings(this));
        } else if (smsPermissionStep.showAppSettings(false)) {
            UiKit.button(this, smsPermissionCard, "Open app settings", true, this::openSmsSettings);
        } else {
            UiKit.button(this, smsPermissionCard, "Allow incoming SMS", true, this::requestIncomingSms);
        }
    }

    private void showSmsHelp() {
        if (SetupPermissions.hasIncoming(this)) {
            UiKit.info(this, "SMS and MMS access",
                    "Monitoring reads incoming texts. Reading text from picture or group messages "
                            + "also requires inbox access, which your Android installer may restrict.");
        } else {
            SmsPermissionGuideUi.show(this, this::openSmsSettings);
        }
    }

    private void openSmsSettings() {
        smsPermissionStep.settingsOpened();
        SetupPermissions.openAppSettings(this);
    }

    private void requestIncomingSms() {
        smsPermissionStep.permissionRequested();
        SetupPermissions.requestIncoming(this, RECEIVE_SMS_REQUEST);
        renderSmsPermissionCard();
    }

    private void renderReplyCard() {
        if (replyCard == null || isFinishing()) return;
        replyCard.removeAllViews();
        UiKit.sectionHelp(this, replyCard, "Automatic STOP replies", () -> UiKit.info(this,
                "Automatic STOP replies", "TextSentry can send a fixed STOP reply for messages that "
                        + "meet your rules. You can review suggested replies in History before enabling this. "
                        + "Pausing monitoring also turns off automatic replies."));
        if (settings.autoSend()) {
            boolean allowed = SetupPermissions.hasOutgoing(this);
            UiKit.description(this, replyCard, allowed ? "On" : "SMS reply access needed");
            if (!allowed) {
                UiKit.link(this, replyCard, "Restore reply access", this::confirmAutoSend);
            }
            UiKit.button(this, replyCard, "Turn off auto-replies", false, () -> {
                settings.setAutomation(settings.monitoring(), false);
                renderReplyCard();
            });
        } else if (settings.monitoring() && SetupPermissions.hasIncoming(this)) {
            UiKit.button(this, replyCard, "Enable auto-replies", false, this::confirmAutoSend);
        } else {
            UiKit.description(this, replyCard, "Off · Start monitoring to enable");
        }
    }

    private void confirmAutoSend() {
        if (!settings.monitoring() || !SetupPermissions.hasIncoming(this)) {
            showSmsHelp();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Enable automatic replies?")
                .setMessage("TextSentry can send STOP when a message meets your rules. A wrong decision "
                        + "may confirm that your number is active. Carrier charges may apply.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    if (SetupPermissions.hasOutgoing(this)) {
                        enableAutoSend();
                    } else {
                        SetupPermissions.requestOutgoing(this, SEND_SMS_REQUEST);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void enableAutoSend() {
        if (settings.monitoring() && SetupPermissions.hasIncoming(this)
                && SetupPermissions.hasOutgoing(this)) {
            settings.setAutomation(true, true);
        }
        renderReplyCard();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("sms_attempted", smsPermissionStep.attempted());
        out.putBoolean("sms_settings_opened", smsPermissionStep.awaitingSettingsReturn());
        super.onSaveInstanceState(out);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ROUNDUP_PERMISSION_REQUEST) {
            refreshRoundup();
        } else if (requestCode == RECEIVE_SMS_REQUEST) {
            smsPermissionStep.permissionRequested();
            renderSmsPermissionCard();
            renderReplyCard();
        } else if (requestCode == SEND_SMS_REQUEST) {
            if (SetupPermissions.hasOutgoing(this)) {
                enableAutoSend();
            } else {
                new AlertDialog.Builder(this).setTitle("SMS reply access needed")
                        .setMessage("Allow SMS in Android app permissions, then return here to enable replies.")
                        .setPositiveButton("Open app settings", (d, w) -> SetupPermissions.openAppSettings(this))
                        .setNegativeButton("Done", null).show();
            }
        }
    }

    private void addRoundupSettings() {
        LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.sectionHelp(this, card, "STOP roundup", () -> UiKit.info(this, "STOP roundup",
                "A quiet reminder to review confirmed STOP replies. No reminder is sent when there are no new replies. Android may delay background delivery."));
        roundupStatus = UiKit.description(this, card, "");
        UiKit.link(this, card, "Change frequency", this::chooseRoundup);
        refreshRoundup();
    }

    private void refreshRoundup() {
        if (roundupStatus == null) return;
        int days = RoundupWorker.frequency(this);
        String label = days == 1 ? "Every day" : days == 2 ? "Every other day"
                : days == 7 ? "Once a week" : "Off";
        if (days != 0 && !RoundupWorker.notificationsAllowed(this))
            label += " · Android notifications disabled";
        roundupStatus.setText(label);
    }

    private void chooseRoundup() {
        int[] days = {0, 1, 2, 7};
        int current = RoundupWorker.frequency(this);
        int selected = current == 1 ? 1 : current == 2 ? 2 : current == 7 ? 3 : 0;
        new AlertDialog.Builder(this).setTitle("STOP roundup")
                .setSingleChoiceItems(new String[]{"Off", "Every day", "Every other day", "Once a week"},
                        selected, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        RoundupWorker.setFrequency(this, days[which]);
                        refreshRoundup();
                        if (days[which] != 0 && !RoundupWorker.notificationsAllowed(this)) {
                            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                                            != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    && !getSharedPreferences("textsentry_settings", MODE_PRIVATE).getBoolean("roundup_permission_requested", false)) {
                                getSharedPreferences("textsentry_settings", MODE_PRIVATE).edit().putBoolean("roundup_permission_requested", true).apply();
                                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                                        ROUNDUP_PERMISSION_REQUEST);
                            } else {
                                new AlertDialog.Builder(this).setTitle("Allow roundup notifications")
                                        .setMessage("Enable TextSentry notifications in Android settings to receive roundups.")
                                        .setPositiveButton("Open settings", (d, w) -> startActivity(
                                                new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName())))
                                        .setNegativeButton("Not now", null).show();
                            }
                        }
                    } catch (RuntimeException failure) {
                        Toast.makeText(this, "Could not update roundup", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void addSample() {
        LinearLayout sampleCard = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, sampleCard, "Try it");
        UiKit.link(this, sampleCard, "Analyze a sample", () -> {
            LinearLayout body = UiKit.vertical(this);
            body.setPaddingRelative(UiKit.dp(this, 24), 0, UiKit.dp(this, 24), UiKit.dp(this, 16));
            EditText sample = UiKit.input(this, body, "Sample message",
                    "Sale today! Reply STOP to unsubscribe.", true, false);
            UiKit.description(this, body, "Analyze sends this sample to your AI provider.");
            TextView result = UiKit.description(this, body, "");
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Try your filters")
                    .setView(UiKit.scrollable(this, body)).setPositiveButton("Analyze", null)
                    .setNegativeButton("Done", null).show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    analyzeSample(sample.getText().toString(), result));
        });
    }

    private void analyzeSample(String content, TextView result) {
        if (!MessagePolicy.eligible("+15555550123", content)) {
            result.setText("This text couldn't be analyzed.");
            return;
        }
        final String policy;
        try {
            policy = MessageCategories.compose(selectedMask(), settings.prompt());
        } catch (IllegalArgumentException invalid) {
            result.setText("Check your filter rules.");
            return;
        }
        if (policy.isEmpty()) {
            result.setText("Select a filter category first.");
            return;
        }
        result.setText("Analyzing…");
        final double threshold = confidence / 100.0;
        new Thread(() -> {
            String answer;
            try {
                SystemOneClient.Evaluation evaluation = AnalysisAccess.evaluate(this,
                        policy, content, threshold, DeveloperMode.traceSink(this, "sample"));
                answer = (evaluation.decision.shouldReply ? "Suggest STOP" : "No reply")
                        + " · " + Math.round(evaluation.confidence * 100) + "% match";
            } catch (Exception ignored) {
                answer = "Analysis unavailable. Check your connection.";
            }
            String finalAnswer = answer;
            runOnUiThread(() -> result.setText(finalAnswer));
        }, "textsentry-sample").start();
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

    private CheckBox addCategory(LinearLayout parent, String title, int bit) {
        CheckBox box = new CheckBox(this);
        box.setText(title);
        box.setTextSize(15);
        box.setTextColor(UiKit.INK);
        box.setMinHeight(UiKit.dp(this, 48));
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

    private void saveFilters() {
        try {
            settings.saveCategoryPolicy(settings.prompt(), confidence, selectedMask());
            toast("Filters saved");
            finish();
        } catch (Exception ex) {
            toast("Couldn't save. Check your filter rules.");
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
            renderReplyCard();
            if (batteryStatus != null) {
                batteryStatus.setText(SetupPermissions.unrestricted(this)
                        ? "Battery use unrestricted" : "Using Android's battery limits");
            }
            refreshRoundup();
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

        UiKit.link(this, developerCard, "Open developer tools", () ->
                startActivity(new Intent(this, DeveloperActivity.class)));
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
