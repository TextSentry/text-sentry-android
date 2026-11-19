package org.textsentry.app;

import android.content.Context;

/** Play manual reviews cannot send confirmed automatic STOP replies. */
final class RoundupWorker {
    static final String EXTRA_START = "roundup_start", EXTRA_END = "roundup_end";
    private RoundupWorker() {}
    static int frequency(Context context) { return 0; }
    static boolean notificationsAllowed(Context context) { return false; }
    static void cancelNotification(Context context) {}
    static void setFrequency(Context context, int days) {
        if (days != 0) throw new IllegalArgumentException("Roundup is sideload-only");
    }
}
