package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Simple home/dashboard for the experimental SMS-monitoring flavor. */
public final class MainActivity extends Activity {
    private Settings settings;
    private boolean permissionBlocked;
    private static final int MESSAGE_PERMISSION_REQUEST = 100;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        permissionBlocked = state != null && state.getBoolean("permission_blocked");
        SetupGate.enforce(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        showHome();
        GitHubUpdates.checkOnOpen(this);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("permission_blocked", permissionBlocked);
        super.onSaveInstanceState(out);
    }

    private void showHome() {
        MmsScanWorker.sync(this);
        LinearLayout root = UiKit.home(this);
        LinearLayout overview = UiKit.card(this, root, UiKit.NIGHT);
        ImageView shield = new ImageView(this);
        shield.setImageResource(R.drawable.ic_shield);
        shield.setImageTintList(ColorStateList.valueOf(UiKit.ACCENT));
        shield.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shield.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 14),
                UiKit.dp(this, 14), UiKit.dp(this, 14));
        shield.setBackground(UiKit.shape(0xFF142B48, UiKit.dp(this, 17), 0));
        overview.addView(shield, new LinearLayout.LayoutParams(UiKit.dp(this, 56), UiKit.dp(this, 56)));

        boolean monitoring = settings.monitoring();
        boolean incoming = SetupPermissions.hasIncoming(this);
        if (incoming) permissionBlocked = false;
        boolean needsAccess = !incoming && (permissionBlocked || monitoring);
        boolean replying = monitoring && settings.autoSend() && SetupPermissions.hasOutgoing(this);
        String title;
        String explanation;
        if (needsAccess) {
            title = "SMS access needed";
            explanation = "Allow incoming messages in Settings to monitor your inbox.";
        } else if (!monitoring) {
            title = "Monitoring paused";
            explanation = "Start monitoring to preview which messages qualify for a STOP reply.";
        } else if (settings.categoryMask() == 0) {
            title = "No filters selected";
            explanation = "Choose what to filter in Settings to resume analysis.";
        } else if (replying) {
            title = "Auto-replies on";
            explanation = "TextSentry sends STOP when a message matches your rules.";
        } else {
            title = "Previewing messages";
            explanation = "Automatic replies are off. Review suggestions in History.";
        }

        TextView status = UiKit.title(this, overview, title);
        status.setTextColor(Color.WHITE);
        status.setPadding(0, UiKit.dp(this, 18), 0, 0);
        TextView detail = UiKit.description(this, overview, explanation);
        detail.setTextColor(0xFFD0DFFF);
        detail.setTextSize(15);
        detail.setPadding(0, UiKit.dp(this, 8), 0, UiKit.dp(this, 8));

        if (needsAccess) {
            UiKit.button(this, overview, "Review SMS access", true, () ->
                    startActivity(new Intent(this, SettingsActivity.class)
                            .putExtra(SettingsActivity.EXTRA_SMS_ACCESS, true)));
        }
        if (monitoring) {
            UiKit.button(this, overview, "Pause Monitoring", !needsAccess, () -> {
                settings.pause();
                showHome();
            });
        } else if (!needsAccess) {
            UiKit.button(this, overview, "Start Monitoring", true, this::startMonitoring);
        }
    }

    private void startMonitoring() {
        if (!AnalysisAccess.hasProvider(this)) {
            Toast.makeText(this, "Sign in or add your own key first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        String[] missing = MonitoringPermissions.missing(
                SetupPermissions.hasIncoming(this), SetupPermissions.hasInbox(this));
        if (missing.length > 0) {
            // One Android permission request covers both message formats, without
            // a second MMS agreement or an extra Home toggle.
            if (!SmsPermissionRequestGuard.run(() ->
                    requestPermissions(missing, MESSAGE_PERMISSION_REQUEST))) {
                // Restricted READ_SMS may be rejected by the installer. Incoming SMS
                // monitoring should still start when RECEIVE_SMS is available.
                finishMonitoringRequest();
            }
            return;
        }
        finishMonitoringRequest();
    }

    private void finishMonitoringRequest() {
        permissionBlocked = !SetupPermissions.hasIncoming(this);
        if (!permissionBlocked && AnalysisAccess.hasProvider(this)) {
            settings.startMonitoring();
        }
        showHome();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MESSAGE_PERMISSION_REQUEST) {
            // Check the actual RECEIVE_SMS grant; READ_SMS denial must not block SMS.
            finishMonitoringRequest();
        }
    }
}
