package org.textsentry.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Simple home/dashboard for the experimental SMS-monitoring flavor. */
public final class MainActivity extends Activity {
    private Settings settings;
    private KeyVault vault;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        vault = new KeyVault(this);
        SetupGate.enforce(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        showHome();
    }

    private void showHome() {
        LinearLayout root = UiKit.screen(this, "Less spam. More control.", false);

        LinearLayout overview = UiKit.card(this, root, UiKit.HIGHLIGHT);
        UiKit.heading(this, overview, "Inbox protection");
        String state = settings.monitoring() && !hasReceivePermission()
                ? "SMS permission needed"
                : !settings.monitoring() ? "Paused"
                : settings.autoSend() ? "Auto-replies on" : "Previewing";
        UiKit.chip(this, overview, state + " · " + settings.confidence() + "% match");
        UiKit.description(this, overview, AnalysisAccess.status(this));
        if (!settings.monitoring()) {
            UiKit.button(this, overview, "Start preview", true, this::startPreview);
        } else {
            UiKit.button(this, overview, "Pause", true, () -> {
                settings.pause();
                showHome();
            });
            UiKit.button(this, overview, settings.autoSend()
                    ? "Turn off auto-replies" : "Enable auto-replies", false, () -> {
                if (settings.autoSend()) {
                    settings.setAutomation(true, false);
                    showHome();
                } else confirmAutoSend();
            });
        }

        if (!hasReceivePermission() || (settings.autoSend() && !hasSendPermission())) {
            LinearLayout permissions = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, permissions, "Permissions");
            if (!hasReceivePermission()) {
                UiKit.button(this, permissions, "Allow incoming SMS", false,
                        () -> requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, 102));
            }
            if (!hasSendPermission()) {
                UiKit.button(this, permissions, "Allow SMS replies", false,
                        () -> requestPermissions(new String[]{Manifest.permission.SEND_SMS}, 103));
            }
            UiKit.button(this, permissions, "Permission help", false,
                    this::showPermissionHelp);
            UiKit.button(this, permissions, "Android app settings", false,
                    () -> PermissionSetup.openAppDetails(this));
        }

        addManualReview(root);

        LinearLayout navigate = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.button(this, navigate, "History", false, () ->
                startActivity(new Intent(this, HistoryActivity.class)));
        UiKit.button(this, navigate, "Settings", false, () ->
                startActivity(new Intent(this, SettingsActivity.class)));
        UiKit.button(this, navigate, "Account", false, () ->
                startActivity(new Intent(this, AccountActivity.class)));
    }


    /**
     * Multimedia texts are not delivered to SMS_RECEIVED. Offer an explicitly
     * consented copy/share path, without requesting inbox/MMS permissions.
     */
    private void addManualReview(LinearLayout root) {
        LinearLayout review = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, review, "Review an MMS or missed message");
        UiKit.description(this, review,
                "Automatic monitoring covers SMS only, not picture messages (MMS) or RCS. "
                + "Copy the message text from your messaging app, or share it here. "
                + "Images are not analyzed.");
        String sharedText = "";
        Intent shared = getIntent();
        if (Intent.ACTION_SEND.equals(shared.getAction())
                && "text/plain".equals(shared.getType())) {
            CharSequence received = shared.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (received != null) {
                sharedText = received.subSequence(0, Math.min(1600, received.length()))
                        .toString();
            }
        }
        EditText text = UiKit.input(this, review, "Message text", sharedText, true, false);
        CheckBox consent = new CheckBox(this);
        consent.setText("Send only this text to my AI provider for analysis");
        consent.setChecked(false);
        review.addView(consent);
        TextView outcome = UiKit.description(this, review,
                "Manual reviews appear in encrypted History. No automatic SMS reply is sent.");
        UiKit.button(this, review, "Analyze & save to history", true, () -> {
            if (!consent.isChecked()) {
                outcome.setText("Consent is needed for this manual analysis.");
                return;
            }
            String body = text.getText().toString().trim();
            if (!MessagePolicy.eligible("+15555550123", body)) {
                outcome.setText("Message is empty, too long, or blocked by local safety rules.");
                return;
            }
            if (!AnalysisAccess.hasProvider(this)) {
                outcome.setText("Sign in or add your own key to analyze this message.");
                return;
            }
            final String rule;
            try {
                rule = settings.effectivePrompt();
            } catch (IllegalArgumentException invalid) {
                outcome.setText("Check your filter settings.");
                return;
            }
            if (rule.isEmpty()) {
                outcome.setText("Select a message category in Settings first.");
                return;
            }
            final double threshold = settings.confidence() / 100.0;
            consent.setChecked(false);
            outcome.setText("Analyzing…");
            new Thread(() -> {
                String result;
                try {
                    ManualReviewService.Result evaluated = ManualReviewService.evaluate(
                            body, rule, threshold,
                            (policy, input, minimum) -> AnalysisAccess.evaluate(this,
                                    policy, input, minimum,
                                    DeveloperMode.traceSink(this, "manual")),
                            (input, confidence, reply, status, source) ->
                                    new HistoryStore(this).add("Manual review", input,
                                            confidence, reply, status, source));
                    result = (evaluated.suggestStop ? "Suggest STOP (not sent)"
                            : "No reply recommended") + " · "
                            + Math.round(evaluated.confidence * 100)
                            + "% match · Saved in History";
                } catch (Exception ignored) {
                    result = "Analysis could not be saved. Check your account and connection.";
                }
                final String display = result;
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) outcome.setText(display);
                });
            }, "textsentry-manual-review").start();
        });
    }

    private boolean hasReceivePermission() {
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasSendPermission() {
        return checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionHelp() {
        new AlertDialog.Builder(this)
                .setTitle("Android SMS permission help")
                .setMessage(PermissionSetup.smsHelpText())
                .setPositiveButton("Open app info", (dialog, which) ->
                        PermissionSetup.openAppDetails(this))
                .setNegativeButton("Close", null)
                .show();
    }

    private void startPreview() {
        if (!AnalysisAccess.hasProvider(this)) {
            Toast.makeText(this, "Sign in or add your own key first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        if (!hasReceivePermission()) {
            requestPermissions(new String[]{ Manifest.permission.RECEIVE_SMS }, 100);
            return;
        }
        settings.setAutomation(true, false);
        showHome();
    }

    private void confirmAutoSend() {
        if (!hasReceivePermission()) {
            showPermissionHelp();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable automatic replies?")
                .setMessage("TextSentry may send STOP to eligible SMS senders when Jev's approval probability meets your threshold. A wrong decision may confirm your number is active; carrier charges may apply. You can pause at any time.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    if (!hasSendPermission()) {
                        requestPermissions(new String[]{ Manifest.permission.SEND_SMS }, 101);
                    } else {
                        settings.setAutomation(true, true);
                        showHome();
                    }
                })
                .setNegativeButton("Not now", null).show();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == 100 && granted && AnalysisAccess.hasProvider(this)) {
            settings.setAutomation(true, false);
        } else if (requestCode == 101 && granted && settings.monitoring()
                && hasReceivePermission()) {
            settings.setAutomation(true, true);
        }
        showHome();
        if (requestCode >= 100 && requestCode <= 103 && !granted) {
            showPermissionHelp();
        }
    }
}
