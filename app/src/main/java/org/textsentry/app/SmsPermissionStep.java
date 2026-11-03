package org.textsentry.app;

/** In-memory UI progress only; never treats a settings visit as an SMS grant. */
final class SmsPermissionStep {
    private boolean attempted;
    private boolean awaitingSettingsReturn;

    SmsPermissionStep() { this(false, false); }

    SmsPermissionStep(boolean attempted, boolean awaitingSettingsReturn) {
        this.attempted = attempted;
        this.awaitingSettingsReturn = awaitingSettingsReturn;
    }

    void permissionRequested() { attempted = true; }

    boolean showAppSettings(boolean incomingGranted) {
        return attempted && !incomingGranted;
    }

    void settingsOpened() { awaitingSettingsReturn = true; }

    boolean retryAfterSettings(boolean incomingGranted) {
        boolean returned = awaitingSettingsReturn;
        awaitingSettingsReturn = false;
        return returned && !incomingGranted;
    }

    boolean attempted() { return attempted; }

    boolean awaitingSettingsReturn() { return awaitingSettingsReturn; }
}
