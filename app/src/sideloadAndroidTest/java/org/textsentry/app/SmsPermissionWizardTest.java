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

/** Regression: exercise actual permission wizard rendering for Google-session and BYOK paths. */
@RunWith(AndroidJUnit4.class)
public class SmsPermissionWizardTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void googleAccountPermissionsScreenRendersWithoutCrash() throws Exception {
        // Setup only checks whether a session is present. This is not a real login
        // token and is removed immediately; no network or actual SMS is involved.
        context.getSharedPreferences("textsentry_auth_session", Context.MODE_PRIVATE)
                .edit().putString("state", "screen-only-test-session").commit();
        new KeyVault(context).clear();
        SetupFlow.reset(context);
        SetupFlow.completeSubscriptionPrompt(context);
        assertEquals(SetupFlow.PERMISSIONS, SetupFlow.stage(context));
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "Allow message access."));
            assertTrue(contains(root, "Android permission settings"));
            assertTrue(contains(root, "Continue to battery settings"));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            context.getSharedPreferences("textsentry_auth_session", Context.MODE_PRIVATE)
                    .edit().remove("state").commit();
            SetupFlow.reset(context);
        }
    }

    @Test public void byokPermissionsScreenRendersWithoutCrash() throws Exception {
        SetupFlow.reset(context);
        new KeyVault(context).save("synthetic-screen-test");
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "Allow message access."));
            assertTrue(contains(root, "Allow incoming SMS"));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            new KeyVault(context).clear();
            SetupFlow.reset(context);
        }
    }

    @Test public void batteryStepExplainsUnrestrictedInLargeReadableSteps() throws Exception {
        SetupFlow.reset(context);
        new KeyVault(context).save("synthetic-battery-guide-test");
        SetupFlow.advance(context, SetupFlow.BATTERY);
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "Set battery use to Unrestricted"));
            assertTrue(contains(root, "Open TextSentry app settings"));
            assertTrue(contains(root, "Continue to start monitoring"));
            for (String instruction : new String[] {
                    "1. Tap the button below to open TextSentry settings.",
                    "2. In App info, tap App battery usage (or Battery).",
                    "3. Select Unrestricted, not Optimized or Restricted.",
                    "4. Return to TextSentry to check the updated status."
            }) {
                TextView step = findText(root, instruction);
                assertNotNull("Missing step: " + instruction, step);
                float sp = step.getTextSize() /
                        context.getResources().getDisplayMetrics().scaledDensity;
                assertTrue("Too small for setup guidance: " + instruction, sp >= 18f);
            }
            assertTrue(contains(root,
                    "Optional: this does not grant SMS access and may use more battery."));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            new KeyVault(context).clear();
            SetupFlow.reset(context);
        }
    }

    private static TextView findText(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText()))
            return (TextView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), value);
                if (found != null) return found;
            }
        }
        return null;
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
