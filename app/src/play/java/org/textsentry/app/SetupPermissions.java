package org.textsentry.app;

import android.app.Activity;

/** Play build is manual-only: never requests SMS or background exemptions. */
final class SetupPermissions {
    private SetupPermissions() {}
    static boolean supportsMonitoring() { return false; }
    static boolean unrestricted(Activity activity) { return true; }
    static void openBatterySettings(Activity activity) { /* no background worker */ }
    static boolean hasIncoming(Activity activity) { return false; }
    static boolean hasInbox(Activity activity) { return false; }
    static boolean hasOutgoing(Activity activity) { return false; }
    // Manual Play analysis does not read contacts; these methods are never invoked.
    static boolean hasContacts(Activity activity) { return true; }
    static void requestContacts(Activity activity, int requestCode) { }
    static void requestIncoming(Activity activity, int requestCode) { }
    static void requestOutgoing(Activity activity, int requestCode) { }
    static void openAppSettings(Activity activity) { }
    static String help() { return "This edition reviews messages you choose to share."; }
}
