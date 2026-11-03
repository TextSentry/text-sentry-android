package org.textsentry.app;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Readable recovery steps for sideload SMS setup on different Android versions. */
final class SmsPermissionGuideUi {
    private SmsPermissionGuideUi() {}

    static void show(Activity activity, LinearLayout card) {
        UiKit.heading(activity, card, "SMS permission blocked?");
        step(activity, card, "1. Tap Open app settings below.");
        step(activity, card, "2. If shown, tap the three-dot menu (top right), then Allow restricted settings.");
        step(activity, card, "3. Open Permissions → SMS → Allow, if SMS is available.");
        step(activity, card, "4. Return to TextSentry. We'll ask for SMS permission again.");
        TextView caution = UiKit.description(activity, card,
                "Note: Allow restricted settings does not override Android's installer restrictions " +
                "on SMS permissions. If SMS is still missing or blocked, a compatible " +
                "installation method may be required.");
        caution.setTextSize(15);
        caution.setTextColor(UiKit.INK);
    }

    private static void step(Activity activity, LinearLayout card, String message) {
        TextView line = UiKit.text(activity, message, 18, UiKit.INK, false);
        line.setPadding(UiKit.dp(activity, 4), UiKit.dp(activity, 12),
                UiKit.dp(activity, 4), UiKit.dp(activity, 12));
        card.addView(line);
    }
}
