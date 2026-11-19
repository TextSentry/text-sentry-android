package org.textsentry.app;

import android.app.Activity;
import android.widget.LinearLayout;

final class AppUpdates {
    private AppUpdates() {}

    static void addSettingsAction(Activity activity, LinearLayout parent) {
        UiKit.link(activity, parent, "Check for updates", () -> GitHubUpdates.checkNow(activity));
    }
}
