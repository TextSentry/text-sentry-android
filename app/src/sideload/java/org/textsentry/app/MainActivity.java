package org.textsentry.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Simple home/dashboard for the experimental SMS-monitoring flavor. */
public final class MainActivity extends Activity {
    private Settings settings;
    private KeyVault vault;
    private boolean pendingMmsConsent;

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
        MmsScanWorker.sync(this);
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

        LinearLayout mms = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.heading(this, mms, "Automatic picture-message filtering");
        boolean allowed = checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
        UiKit.chip(this, mms, settings.mmsEnabled()
                ? allowed ? "MMS enabled" : "MMS needs inbox permission"
                : "MMS off");
        UiKit.description(this, mms,
                "Experimental sideload only. Reads new MMS after your messaging app "
                + "downloads them. Text and on-device image OCR are classified automatically; "
                + "no images go to the AI provider. Android restricts SMS inbox access.");
        UiKit.button(this, mms, settings.mmsEnabled() ? "Turn off MMS filtering"
                : "Enable automatic MMS filtering", false, () -> {
            if (settings.mmsEnabled()) {
                settings.setMmsMonitoring(false);
                MmsScanWorker.sync(this);
                showHome();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Allow automatic MMS analysis?")
                        .setMessage("TextSentry will read only newly downloaded MMS from "
                                + "Android's message store and send extracted text, including "
                                + "on-device image OCR, to your selected AI provider. Images "
                                + "stay on your device. MMS auto-replies follow your existing "
                                + "STOP setting, but group MMS replies are always blocked. "
                                + "Android requires restricted READ_SMS permission; "
                                + "some installers cannot grant it.")
                        .setPositiveButton("Continue", (d, w) -> requestMmsPermission())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        LinearLayout navigate = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.button(this, navigate, "History", false, () ->
                startActivity(new Intent(this, HistoryActivity.class)));
        UiKit.button(this, navigate, "Settings", false, () ->
                startActivity(new Intent(this, SettingsActivity.class)));
        UiKit.button(this, navigate, "Account", false, () ->
                startActivity(new Intent(this, AccountActivity.class)));
    }

    private void requestMmsPermission() {
        if (checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED) {
            enableMms();
            return;
        }
        pendingMmsConsent = true;
        if (!SmsPermissionRequestGuard.run(() ->
                requestPermissions(new String[]{Manifest.permission.READ_SMS}, 104))) {
            pendingMmsConsent = false;
            showMmsPermissionHelp();
        }
    }

    private void enableMms() {
        if (!SetupGate.canEnterApp(this)) return;
        if (checkSelfPermission(Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            showMmsPermissionHelp();
            return;
        }
        settings.setMmsMonitoring(true);
        MmsScanWorker.sync(this);
        showHome();
    }

    private void showMmsPermissionHelp() {
        new AlertDialog.Builder(this)
                .setTitle("MMS inbox permission unavailable")
                .setMessage("Android requires installer allowlisting for READ_SMS. "
                        + "Open Android App Info > Permissions to check SMS access. "
                        + "Restricted Settings alone cannot grant a hard-restricted "
                        + "permission. SMS monitoring still works independently.")
                .setPositiveButton("App info", (d, w) -> PermissionSetup.openAppDetails(this))
                .setNegativeButton("Close", null).show();
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
        if (requestCode == 104) {
            boolean consented = pendingMmsConsent;
            pendingMmsConsent = false;
            if (granted && consented) enableMms();
            else showMmsPermissionHelp();
            return;
        }
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
