package org.textsentry.app;

/** A roundup is due at most once per chosen interval, measured from the last check. */
final class RoundupPolicy {
    private static final long DAY = 86_400_000L;
    static boolean valid(int days) { return days == 0 || days == 1 || days == 2 || days == 7; }
    static boolean due(int days, long lastCheck, long now) {
        return valid(days) && days != 0 && lastCheck > 0 && now >= lastCheck
                && now - lastCheck >= days * DAY;
    }
    private RoundupPolicy() {}
}
