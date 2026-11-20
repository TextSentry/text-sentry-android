package org.textsentry.app;

import static org.junit.Assert.*;

import android.Manifest;
import org.junit.Test;

/** Monitoring must have contact access before any SMS/MMS analysis. */
public class MonitoringPermissionsTest {
    @Test public void requestsAllMissingPermissionsTogether() {
        assertArrayEquals(new String[]{Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS},
                MonitoringPermissions.missing(false, false, false));
    }

    @Test public void requiresContactsEvenWhenSmsWasAlreadyAllowed() {
        assertArrayEquals(new String[]{Manifest.permission.READ_CONTACTS},
                MonitoringPermissions.missing(true, true, false));
        assertArrayEquals(new String[]{Manifest.permission.READ_SMS,
                Manifest.permission.READ_CONTACTS}, MonitoringPermissions.missing(true, false, false));
    }

    @Test public void doesNotReRequestGrantedPermissionsOrSendSms() {
        assertArrayEquals(new String[0], MonitoringPermissions.missing(true, true, true));
        assertArrayEquals(new String[]{Manifest.permission.RECEIVE_SMS},
                MonitoringPermissions.missing(false, true, true));
        for (String permission : MonitoringPermissions.missing(false, false, false)) {
            assertNotEquals(Manifest.permission.SEND_SMS, permission);
        }
    }
}
