package org.textsentry.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.widget.Switch;
import android.widget.LinearLayout;
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
        boolean monitoring = settings.monitoring();
        boolean incoming = SetupPermissions.hasIncoming(this);
        boolean contacts = SetupPermissions.hasContacts(this);
        if (incoming && contacts) permissionBlocked = false;
        boolean needsAccess = (!incoming || !contacts) && (permissionBlocked || monitoring);
        boolean replyAccessMissing = settings.autoSend() && !SetupPermissions.hasOutgoing(this);
        String title;
        String explanation;
        if (needsAccess) {
            title = !contacts ? "Contacts access needed" : "SMS access needed";
            explanation = !contacts ? "Allow contacts access to exclude saved people from filtering."
                    : "Allow incoming messages in Settings to monitor your inbox.";
        } else if (!monitoring) {
            title = "Monitoring paused";
            explanation = "Start monitoring to review incoming messages.";
        } else if (settings.categoryMask() == 0) {
            title = "No filters selected";
            explanation = "Choose what to filter in Settings to resume analysis.";
        } else if (replyAccessMissing) {
            title = "Reply access needed";
            explanation = "Monitoring is active. Allow outgoing SMS to send STOP replies.";
        } else {
            title = "Monitoring active";
            explanation = settings.autoSend() ? "Matching messages receive a STOP reply."
                    : "Review suggested replies in History.";
        }

        UiKit.brandIntro(this, root, title, explanation);
        addReplySwitch(root, monitoring && incoming && contacts);

        if (needsAccess) {
            UiKit.button(this, root, "Review message access", true, () ->
                    startActivity(new Intent(this, SettingsActivity.class)
                            .putExtra(SettingsActivity.EXTRA_SMS_ACCESS, true)));
        }
        if (replyAccessMissing && monitoring && incoming && contacts) {
            UiKit.button(this, root, "Restore reply access", false, this::confirmReplies);
        }
        if (monitoring) {
            UiKit.button(this, root, "Pause Monitoring", !needsAccess, () -> {
                settings.pause();
                showHome();
            });
        } else if (!needsAccess) {
            UiKit.button(this, root, "Start Monitoring", true, this::startMonitoring);
        }
    }

    private void addReplySwitch(LinearLayout root, boolean canEnable) {
        View divider = new View(this);
        divider.setBackgroundColor(0xFF33475F);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, UiKit.dp(this, 1));
        line.topMargin = UiKit.dp(this, 22);
        root.addView(divider, line);

        Switch toggle = new Switch(this);
        toggle.setId(R.id.stop_replies);
        toggle.setSaveEnabled(false);
        toggle.setText("STOP replies");
        toggle.setTextSize(17);
        toggle.setMinHeight(UiKit.dp(this, 64));
        boolean optedIn = settings.autoSend();
        toggle.setEnabled(canEnable || optedIn);
        toggle.setTextColor(toggle.isEnabled() ? Color.WHITE : 0xFFB1BED0);
        int[][] states = {{android.R.attr.state_checked}, {}};
        toggle.setThumbTintList(new ColorStateList(states, new int[]{UiKit.ACCENT, 0xFFCDD6E3}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{0xFF317C8F, 0xFF526177}));
        toggle.setChecked(optedIn);
        root.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (checked == settings.autoSend()) return;
            if (!checked) {
                settings.setAutomation(settings.monitoring(), false);
                showHome();
            } else {
                toggle.setChecked(false);
                confirmReplies();
            }
        });
    }

    private void confirmReplies() {
        StopReplies.confirm(this, settings, this::showHome);
    }

    private void startMonitoring() {
        if (!AnalysisAccess.hasProvider(this)) {
            Toast.makeText(this, "Sign in or add your own key first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountActivity.class));
            return;
        }
        String[] missing = MonitoringPermissions.missing(
                SetupPermissions.hasIncoming(this), SetupPermissions.hasInbox(this),
                SetupPermissions.hasContacts(this));
        if (missing.length > 0) {
            // One Android permission request covers both message formats, without
            // a second MMS agreement or an extra Home toggle.
            if (!SmsPermissionRequestGuard.run(() ->
                    requestPermissions(missing, MESSAGE_PERMISSION_REQUEST))) {
                // Restricted READ_SMS may be rejected by the installer. Contact
                // access remains mandatory even when only SMS can be monitored.
                finishMonitoringRequest();
            }
            return;
        }
        finishMonitoringRequest();
    }

    private void finishMonitoringRequest() {
        permissionBlocked = !SetupPermissions.hasIncoming(this)
                || !SetupPermissions.hasContacts(this);
        if (!permissionBlocked && AnalysisAccess.hasProvider(this)) {
            settings.startMonitoring();
        }
        showHome();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MESSAGE_PERMISSION_REQUEST) {
            // Both incoming SMS and contacts must be granted; READ_SMS is optional.
            finishMonitoringRequest();
        } else if (requestCode == StopReplies.PERMISSION_REQUEST) {
            StopReplies.finishRequest(this, settings, this::showHome);
        }
    }
}
