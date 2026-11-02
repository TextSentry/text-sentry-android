package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class PlayPolicyTest {
    @Test public void playBuildRequestsNoRestrictedTelephonyPermissions() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        Set<String> permissions = new HashSet<>(info.requestedPermissions == null
                ? java.util.Collections.emptyList() : Arrays.asList(info.requestedPermissions));
        if ("play".equals(BuildConfig.FLAVOR)) {
            for (String name : new String[] {"READ_SMS", "RECEIVE_SMS", "SEND_SMS",
                    "WRITE_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "READ_CALL_LOG",
                    "WRITE_CALL_LOG", "PROCESS_OUTGOING_CALLS"}) {
                assertFalse("Play build must not request " + name,
                        permissions.contains("android.permission." + name));
            }
        } else {
            assertTrue(permissions.contains("android.permission.RECEIVE_SMS"));
            assertTrue(permissions.contains("android.permission.SEND_SMS"));
            assertTrue(permissions.contains("android.permission.READ_SMS"));
            assertTrue(permissions.contains("android.permission.RECEIVE_MMS"));
            assertTrue(permissions.contains("android.permission.RECEIVE_WAP_PUSH"));
        }
    }

    @Test public void githubUpdateWorkerExistsOnlyInSideloadVariant() throws Exception {
        if ("play".equals(BuildConfig.FLAVOR)) {
            try {
                Class.forName("org.textsentry.app.GitHubUpdateWorker");
                fail("The Play APK must not include GitHub sideload update polling");
            } catch (ClassNotFoundException expected) {
                // No updater or background GitHub polling in Play.
            }
        } else {
            assertNotNull(Class.forName("org.textsentry.app.GitHubUpdateWorker"));
        }
    }

    @Test public void localDataDeletionResetsConsentAndApiKey() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Settings settings = new Settings(context);
        KeyVault key = new KeyVault(context);
        key.save("fake-test-secret");
        settings.save(Settings.DEFAULT_ENDPOINT, Settings.DEFAULT_MODEL,
                Settings.DEFAULT_PROMPT, true, true);
        DataControl.deleteLocalData(context);
        assertFalse(settings.monitoring());
        assertFalse(settings.autoSend());
        assertFalse(key.hasKey());
    }
}
