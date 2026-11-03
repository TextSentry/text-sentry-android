package org.textsentry.app;

import static org.junit.Assert.*;

import org.junit.Test;

/** The SMS recovery action must advance only after a failed grant. */
public class SmsPermissionStepTest {
    @Test public void onlyOffersSettingsAfterAnUnsuccessfulRequest() {
        SmsPermissionStep step = new SmsPermissionStep();
        assertFalse(step.showAppSettings(false));
        step.permissionRequested();
        assertTrue(step.showAppSettings(false));
        assertFalse(step.showAppSettings(true));
    }

    @Test public void returningFromSettingsRetriesAtMostOnceIfStillDenied() {
        SmsPermissionStep step = new SmsPermissionStep();
        step.permissionRequested();
        step.settingsOpened();
        assertTrue(step.retryAfterSettings(false));
        assertFalse(step.retryAfterSettings(false));
        assertTrue(step.showAppSettings(false));
    }

    @Test public void manuallyGrantedPermissionNeverCausesAnotherPrompt() {
        SmsPermissionStep step = new SmsPermissionStep();
        step.permissionRequested();
        step.settingsOpened();
        assertFalse(step.retryAfterSettings(true));
        assertFalse(step.retryAfterSettings(false));
    }

    @Test public void stateSurvivesConfigurationRecreation() {
        SmsPermissionStep restored = new SmsPermissionStep(true, true);
        assertTrue(restored.showAppSettings(false));
        assertTrue(restored.retryAfterSettings(false));
    }
}
