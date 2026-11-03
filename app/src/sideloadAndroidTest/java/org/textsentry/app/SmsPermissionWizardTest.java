package org.textsentry.app;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Regression: sign-in/subscription → permission guidance → Home, including BYOK. */
@RunWith(AndroidJUnit4.class)
public class SmsPermissionWizardTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test public void managedOnboardingOnlyOffersSubscriptionNotPermissionsOrSkip() throws Exception {
        new AuthSessionStore(context).clear();
        context.getSharedPreferences("textsentry_auth_session", Context.MODE_PRIVATE)
                .edit().putString("state", "screen-only-test-session").commit();
        new KeyVault(context).clear();
        SetupFlow.reset(context);
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            assertFalse(SetupFlow.isComplete(context));
            if (BuildConfig.WEB_CHECKOUT_ENABLED) {
                // Checkout-enabled builds go directly to Account, without
                // showing an additional subscription-introduction screen.
                assertTrue(SetupFlow.subscriptionHandoffPending(context));
            } else {
                View root = activity.getWindow().getDecorView();
                assertTrue(contains(root, "Activate TextSentry."));
                assertFalse(contains(root, "Continue to SMS permissions"));
                assertFalse(contains(root, "Continue to battery settings"));
                assertFalse(contains(root, "Continue to SMS permissions without a key"));
                assertEquals(0, buttonCount(root));
            }
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            new AuthSessionStore(context).clear();
            SetupFlow.reset(context);
        }
    }

    @Test public void byokOpensPermissionStepAndOnlyFinishesAfterContinue()
            throws Exception {
        new AuthSessionStore(context).clear();
        SetupFlow.reset(context);
        KeyVault key = new KeyVault(context);
        key.save("synthetic-setup-key");
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "Allow SMS access."));
            assertFalse(SetupFlow.isComplete(context));
            if (SetupPermissions.hasIncoming(activity)) {
                assertTrue(contains(root, "Continue to TextSentry"));
            } else {
                assertTrue(contains(root, "Allow incoming SMS"));
                assertTrue(contains(root, "Continue without monitoring"));
            }
            final View action = SetupPermissions.hasIncoming(activity)
                    ? findText(root, "Continue to TextSentry")
                    : findText(root, "Continue without monitoring");
            assertNotNull(action);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(action::performClick);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertTrue(SetupFlow.permissionsStepSeen(context));
            assertTrue(SetupFlow.isComplete(context));
            assertTrue(SetupGate.canEnterApp(context));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            key.clear();
            SetupFlow.reset(context);
        }
    }

    @Test public void verifiedManagedAccountGoesToPermissionsNotHome() throws Exception {
        new AuthSessionStore(context).clear();
        context.getSharedPreferences("textsentry_auth_session", Context.MODE_PRIVATE)
                .edit().putString("state", "screen-only-test-session").commit();
        new KeyVault(context).clear();
        SetupFlow.reset(context);
        SetupFlow.completeSubscriptionPrompt(context);
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SetupActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            assertFalse(SetupFlow.isComplete(context));
            assertTrue(contains(activity.getWindow().getDecorView(), "Allow SMS access."));
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            new AuthSessionStore(context).clear();
            SetupFlow.reset(context);
        }
    }

    @Test public void settingsRetainReadableSmsAndUnrestrictedBatteryGuidance()
            throws Exception {
        new AuthSessionStore(context).clear();
        SetupFlow.reset(context);
        KeyVault key = new KeyVault(context);
        key.save("synthetic-battery-guide-test");
        SetupFlow.complete(context);
        Activity activity = null;
        try {
            activity = InstrumentationRegistry.getInstrumentation().startActivitySync(
                    new Intent(context, SettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            View root = activity.getWindow().getDecorView();
            assertTrue(contains(root, "SMS access"));
            assertTrue(contains(root, "Background operation"));
            assertTrue(contains(root, "Open TextSentry app settings"));
            for (String instruction : new String[]{
                    "1. Open TextSentry app settings below.",
                    "2. Tap App battery usage (or Battery).",
                    "3. Choose Unrestricted."
            }) {
                TextView step = findText(root, instruction);
                assertNotNull("Missing step: " + instruction, step);
                float sp = step.getTextSize() /
                        context.getResources().getDisplayMetrics().scaledDensity;
                assertTrue("Unreadably small: " + instruction, sp >= 18f);
            }
        } finally {
            if (activity != null) activity.runOnUiThread(activity::finish);
            key.clear();
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
        return findText(view, value) != null;
    }

    private static int buttonCount(View view) {
        int count = view instanceof Button ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                count += buttonCount(group.getChildAt(i));
        }
        return count;
    }
}
