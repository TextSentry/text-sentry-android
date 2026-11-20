package org.textsentry.app;

import android.Manifest;
import java.util.ArrayList;
import java.util.List;

/** Monitoring requires the contact exclusion in addition to incoming SMS access. */
final class MonitoringPermissions {
    private MonitoringPermissions() {}
    static String[] missing(boolean receiveGranted, boolean readGranted, boolean contactsGranted) {
        List<String> missing = new ArrayList<>();
        if (!receiveGranted) missing.add(Manifest.permission.RECEIVE_SMS);
        if (!readGranted) missing.add(Manifest.permission.READ_SMS);
        if (!contactsGranted) missing.add(Manifest.permission.READ_CONTACTS);
        return missing.toArray(new String[0]);
    }
}
