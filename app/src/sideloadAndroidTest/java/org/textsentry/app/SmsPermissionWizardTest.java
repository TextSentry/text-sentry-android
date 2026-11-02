package org.textsentry.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression: setup has no permission/battery wizard and never auto-starts SMS. */
@RunWith(AndroidJUnit4.class)
public class SmsPermissionWizardTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void freshInstallShowsSignInInsteadOfPermissionOrBatteryActions() throws Exception {
        new AuthSessionStore(context).clear();
        new KeyVault(context).clear();
        SetupFlow.reset(context);
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "Stop sorting through spam."));
            assertTrue(contains(root, "Use your own TypeSafe API key instead"));
            assertFalse(contains(root, "Allow incoming SMS"));
            assertFalse(contains(root, "Continue to battery settings"));
            assertFalse(contains(root, "Start monitoring"));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            SetupFlow.reset(context);
        }
    }

    @Test public void byokGoesDirectlyToSettingsWithMonitoringPaused() throws Exception {
        new AuthSessionStore(context).clear();
        SetupFlow.reset(context);
        new Settings(context).pause();
        new KeyVault(context).save("synthetic-short-onboarding-test");
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue("Setup should finish without the old permission/battery steps",
                    SetupFlow.isComplete(context));
            assertTrue(SetupGate.canEnterApp(context));
            assertFalse("Setup must not silently start monitoring",
                    new Settings(context).monitoring());
            assertFalse("Setup must never enable SMS replies",
                    new Settings(context).autoSend());
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            new KeyVault(context).clear();
            SetupFlow.reset(context);
        }
    }

    private static boolean contains(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText()))
            return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (contains(group.getChildAt(i), value)) return true;
            }
        }
        return false;
    }
}
