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

        LinearLayout navigate = UiKit.card(this, root, 0xFFFFFFFF);
        UiKit.button(this, navigate, "History", false, () ->
                startActivity(new Intent(this, HistoryActivity.class)));
        UiKit.button(this, navigate, "Settings", false, () ->
                startActivity(new Intent(this, SettingsActivity.class)));
        UiKit.button(this, navigate, "Account", false, () ->
                startActivity(new Intent(this, AccountActivity.class)));
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
