package org.textsentry.app;

import android.Manifest;

/** One monitoring action asks only for missing receive/inbox access, never SEND_SMS. */
final class MonitoringPermissions {
    private MonitoringPermissions() {}

    static String[] missing(boolean receiveGranted, boolean readGranted) {
        if (!receiveGranted && !readGranted) {
            return new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS};
        }
        if (!receiveGranted) return new String[]{Manifest.permission.RECEIVE_SMS};
        if (!readGranted) return new String[]{Manifest.permission.READ_SMS};
        return new String[0];
    }
}
