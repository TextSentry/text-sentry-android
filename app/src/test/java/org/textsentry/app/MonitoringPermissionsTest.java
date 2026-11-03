package org.textsentry.app;

import static org.junit.Assert.*;

import android.Manifest;
import org.junit.Test;

/** Start Monitoring asks for all missing read permissions in one operation. */
public class MonitoringPermissionsTest {
    @Test public void requestsBothMessagePermissionsWhenMissing() {
        assertArrayEquals(new String[]{Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS}, MonitoringPermissions.missing(false, false));
    }

    @Test public void requestsReadSmsOnlyWhenReceiveWasGrantedDuringSetup() {
        assertArrayEquals(new String[]{Manifest.permission.READ_SMS},
                MonitoringPermissions.missing(true, false));
    }

    @Test public void requestsReceiveOnlyWhenReadWasAlreadyGranted() {
        assertArrayEquals(new String[]{Manifest.permission.RECEIVE_SMS},
                MonitoringPermissions.missing(false, true));
    }

    @Test public void neverRequestsSendSmsOrUnnecessaryPrompts() {
        assertArrayEquals(new String[0], MonitoringPermissions.missing(true, true));
        for (String permission : MonitoringPermissions.missing(false, false)) {
            assertNotEquals(Manifest.permission.SEND_SMS, permission);
        }
    }
}
