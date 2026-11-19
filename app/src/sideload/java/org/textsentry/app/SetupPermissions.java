package org.textsentry.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

/** Sideload-only Android battery/SMS navigation. Permissions are user-initiated. */
final class SetupPermissions {
    private SetupPermissions() {}
    static boolean supportsMonitoring() { return true; }
    static boolean unrestricted(Activity activity) {
        return PermissionSetup.batteryOptimizationsExempt(activity);
    }
    static void openBatterySettings(Activity activity) {
        PermissionSetup.openAppDetails(activity);
    }
    static boolean hasIncoming(Activity activity) {
        return activity.checkSelfPermission(Manifest.permission.RECEIVE_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }
    static boolean hasInbox(Activity activity) {
        return activity.checkSelfPermission(Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }
    static boolean hasOutgoing(Activity activity) {
        return activity.checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }
    static void requestIncoming(Activity activity, int requestCode) {
        requestSafely(activity, Manifest.permission.RECEIVE_SMS, requestCode);
    }
    static void requestOutgoing(Activity activity, int requestCode) {
        requestSafely(activity, Manifest.permission.SEND_SMS, requestCode);
    }

    private static void requestSafely(Activity activity, String permission, int requestCode) {
        boolean requested = SmsPermissionRequestGuard.run(
                () -> activity.requestPermissions(new String[]{permission}, requestCode));
        if (!requested && !activity.isFinishing() && !activity.isDestroyed()) {
            android.widget.Toast.makeText(activity,
                    "Android blocked this SMS permission request. Open App Info → Permissions.",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }
    static void openAppSettings(Activity activity) {
        PermissionSetup.openAppDetails(activity);
    }
    static String help() { return PermissionSetup.smsHelpText(); }
}
