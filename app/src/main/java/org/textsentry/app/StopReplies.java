package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;

/** Both reply controls use the same explicit consent and outgoing SMS grant. */
final class StopReplies {
    static final int PERMISSION_REQUEST = 302;
    private StopReplies() {}

    static void confirm(Activity activity, Settings settings, Runnable refresh) {
        if (!settings.monitoring() || !SetupPermissions.hasIncoming(activity)
                || !SetupPermissions.hasContacts(activity)) {
            refresh.run();
            return;
        }
        new AlertDialog.Builder(activity).setTitle("Enable automatic STOP replies?")
                .setMessage("TextSentry can send STOP when a message meets your rules. A wrong decision "
                        + "may confirm that your number is active. Carrier charges may apply.")
                .setPositiveButton("Enable", (dialog, which) -> {
                    if (SetupPermissions.hasOutgoing(activity)) {
                        finishRequest(activity, settings, refresh);
                    } else {
                        SetupPermissions.requestOutgoing(activity, PERMISSION_REQUEST);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static void finishRequest(Activity activity, Settings settings, Runnable refresh) {
        if (activity.isFinishing() || !SetupGate.canEnterApp(activity)) return;
        if (SetupPermissions.hasOutgoing(activity)) {
            if (settings.monitoring() && SetupPermissions.hasIncoming(activity)
                    && SetupPermissions.hasContacts(activity)) {
                settings.setAutomation(true, true);
            }
        } else {
            new AlertDialog.Builder(activity).setTitle("SMS reply access needed")
                    .setMessage("Allow SMS in Android app permissions, then return here to enable replies.")
                    .setPositiveButton("Open app settings", (d, w) -> SetupPermissions.openAppSettings(activity))
                    .setNegativeButton("Done", null).show();
        }
        refresh.run();
    }
}
