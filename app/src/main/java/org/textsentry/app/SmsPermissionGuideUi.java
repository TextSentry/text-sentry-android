package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Readable recovery steps for sideload SMS setup on different Android versions. */
final class SmsPermissionGuideUi {
    private SmsPermissionGuideUi() {}

    static void show(Activity activity, Runnable openSettings) {
        LinearLayout card = UiKit.vertical(activity);
        int padding = UiKit.dp(activity, 24);
        card.setPaddingRelative(padding, 0, padding, padding);
        step(activity, card, "1. Tap Open app settings below.");
        step(activity, card, "2. Open Permissions → SMS and choose Allow if available.");
        step(activity, card, "3. Return to TextSentry to retry.");
        TextView caution = UiKit.description(activity, card,
                "If Android blocks SMS access, you may need to reinstall with a compatible installer. "
                        + "Android's Allow restricted settings option applies to special app access.");
        caution.setTextSize(15);
        caution.setTextColor(UiKit.INK);
        new AlertDialog.Builder(activity).setTitle("Allow incoming SMS").setView(UiKit.scrollable(activity, card))
                .setPositiveButton("Open app settings", (dialog, which) -> openSettings.run())
                .setNegativeButton("Done", null).show();
    }

    private static void step(Activity activity, LinearLayout card, String message) {
        TextView line = UiKit.text(activity, message, 18, UiKit.INK, false);
        line.setPadding(UiKit.dp(activity, 4), UiKit.dp(activity, 12),
                UiKit.dp(activity, 4), UiKit.dp(activity, 12));
        card.addView(line);
    }
}
