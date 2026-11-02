package org.textsentry.app;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GuidedSetupTest {
    private Context app() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test public void existingByokUsersMustFinishWizardBeforeAccessingHome() throws Exception {
        Context context = app();
        SetupFlow.reset(context);
        KeyVault keys = new KeyVault(context);
        keys.save("synthetic-guided-setup-key");
        assertTrue(SetupGate.hasAccess(context));
        assertFalse(SetupFlow.isComplete(context));
        assertFalse(SetupGate.canEnterApp(context));
        SetupFlow.complete(context);
        assertTrue(SetupGate.canEnterApp(context));
        keys.clear();
        SetupFlow.reset(context);
    }

    @Test public void stepsCannotBeAdvancedWithoutPreviousStage() {
        assertEquals(SetupFlow.LOGIN, SetupFlow.validatedStage(SetupFlow.LOGIN, false));
        assertEquals(SetupFlow.PERMISSIONS, SetupFlow.validatedStage(SetupFlow.PERMISSIONS, true));
        assertEquals(SetupFlow.BATTERY, SetupFlow.validatedStage(SetupFlow.BATTERY, true));
        assertTrue("permissions precede battery", SetupFlow.PERMISSIONS < SetupFlow.BATTERY);
        assertEquals(SetupFlow.LOGIN, SetupFlow.validatedStage(SetupFlow.PERMISSIONS, false));
        assertEquals(SetupFlow.LOGIN, SetupFlow.validatedStage(SetupFlow.ACTIVATE, false));
    }

    @Test public void newUsersOnlyEnterSettingsAfterRequiredAccessIsReady() {
        // Sideload managed accounts must complete verified subscription handoff.
        assertFalse(SetupFlow.readyForSettings(false, false, true, false));
        assertFalse(SetupFlow.readyForSettings(true, false, true, false));
        assertTrue(SetupFlow.readyForSettings(true, false, true, true));
        // BYOK keeps its explicit provider-responsibility path.
        assertTrue(SetupFlow.readyForSettings(false, true, true, false));
        // Play has no hosted subscription checkout or restricted SMS setup.
        assertTrue(SetupFlow.readyForSettings(true, false, false, false));
    }

    @Test public void switchingAccountsInvalidatesPriorSubscriptionHandoff() {
        Context context = app();
        SetupFlow.reset(context);
        SetupFlow.completeSubscriptionPrompt(context);
        assertTrue(SetupFlow.subscriptionPromptSeen(context));
        SetupFlow.resetSubscriptionPrompt(context);
        assertFalse(SetupFlow.subscriptionPromptSeen(context));
        assertFalse(SetupFlow.subscriptionHandoffPending(context));
        SetupFlow.reset(context);
    }

    @Test public void sideloadGoogleUsersSeeSubscriptionBeforePermissions() {
        assertTrue(SetupFlow.shouldPromptSubscription(true, false, true, false));
        assertFalse(SetupFlow.shouldPromptSubscription(true, false, true, true));
        assertFalse(SetupFlow.shouldPromptSubscription(true, false, false, false));
        assertFalse(SetupFlow.shouldPromptSubscription(false, true, true, false));
        assertFalse(SetupFlow.shouldPromptSubscription(true, true, true, false));
    }

    @Test public void oldSetupProgressMigratesWithoutSkippingSmsInstructions() {
        assertEquals(SetupFlow.PERMISSIONS, SetupFlow.migrateLegacyStage(0));
        assertEquals(SetupFlow.PERMISSIONS, SetupFlow.migrateLegacyStage(1));
        assertEquals(SetupFlow.PERMISSIONS, SetupFlow.migrateLegacyStage(2));
        assertEquals(SetupFlow.ACTIVATE, SetupFlow.migrateLegacyStage(3));
    }

    @Test public void existingUsersSeeOneTimePromptOnlyInCheckoutEnabledBuilds() {
        assertFalse(SetupFlow.shouldReturnHome(true, true, true));
        assertTrue(SetupFlow.shouldReturnHome(true, true, false));
        assertTrue(SetupFlow.shouldReturnHome(true, false, true));
        assertFalse(SetupFlow.shouldReturnHome(false, true, false));
    }

    @Test public void subscriptionPromptIsRecordedIndependentlyOfPermissionSteps() {
        Context context = app();
        SetupFlow.reset(context);
        assertFalse(SetupFlow.subscriptionPromptSeen(context));
        SetupFlow.completeSubscriptionPrompt(context);
        assertTrue(SetupFlow.subscriptionPromptSeen(context));
        assertFalse(SetupFlow.isComplete(context));
        SetupFlow.reset(context);
    }

    @Test public void paidOnboardingIntentReturnsToRequiredSetup() {
        android.content.Intent intent = SubscriptionOnboarding.continueSetupIntent(app());
        assertTrue(intent.getBooleanExtra("continue_setup", false));
        assertEquals(AccountActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(SetupFlow.subscriptionHandoffPending(app()));
        // Simulate a fresh Activity after a web checkout redirect.
        assertTrue(SetupFlow.subscriptionHandoffPending(app()));
        SetupFlow.completeSubscriptionPrompt(app());
        assertFalse(SetupFlow.subscriptionHandoffPending(app()));
        SetupFlow.reset(app());
    }

    @Test public void onboardingOnlyAdvancesAfterManagedKeyInstalledInMemory() {
        Context context = app();
        SetupFlow.reset(context);
        ManagedKeyMemory.clear();
        assertFalse(SubscriptionOnboarding.completeAfterVerifiedKey(context));
        assertFalse(SetupFlow.subscriptionPromptSeen(context));
        try {
            ManagedKeyMemory.install("synthetic-test-key",
                    System.currentTimeMillis() + 60000);
            assertTrue(SubscriptionOnboarding.completeAfterVerifiedKey(context));
            assertTrue(SetupFlow.subscriptionPromptSeen(context));
        } finally {
            ManagedKeyMemory.clear();
            SetupFlow.reset(context);
        }
    }

    @Test public void deletionClearsCompletedSetup() throws Exception {
        Context context = app();
        new KeyVault(context).save("synthetic-reset-test");
        SetupFlow.complete(context);
        assertTrue(SetupFlow.isComplete(context));
        DataControl.deleteLocalData(context);
        assertFalse(SetupFlow.isComplete(context));
    }
}
