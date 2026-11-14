package org.textsentry.app;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, local exception matching: no additional AI call or remote rule storage. */
public final class ExceptionRules {
    private static final Pattern LAYLO = Pattern.compile(
            "https?://(?:www\\.)?([a-z][a-z0-9-]{3,40})\\.laylo\\.co(?:[/?#:]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMED_EVENT = Pattern.compile(
            "\\b[A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,}){1,3}\\b");
    private static final String[] TOO_BROAD = {
            "sale", "deal", "free", "promo", "code", "stop", "help", "click",
            "vote", "text", "offer", "tix", "ticket", "tickets", "event",
            "events", "marketing", "subscribe", "unsubscribe", "discount"
    };

    private ExceptionRules() {}

    /** Normalizes US national and international E.164-style numbers to comparable digits. */
    public static String canonicalSender(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 10) digits = "1" + digits;
        return digits.length() >= 11 && digits.length() <= 15 ? digits : "";
    }

    public static boolean matchesSender(String allowed, String candidate) {
        String a = canonicalSender(allowed);
        return !a.isEmpty() && a.equals(canonicalSender(candidate));
    }

    private static String folded(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean validPhrase(String phrase) {
        if (phrase == null || phrase.length() > 80 || phrase.contains("\n")
                || phrase.contains("\r")) return false;
        String normalized = folded(phrase);
        if (normalized.length() < 4 || normalized.length() > 64
                || !normalized.matches(".*[a-z].*")) return false;
        for (String banned : TOO_BROAD) {
            if (normalized.equals(banned)) return false;
        }
        return true;
    }

    /** Spaces/punctuation are ignored so "Two Friends" also matches twofriends.laylo.co. */
    public static boolean matchesPhrase(String phrase, String message) {
        return validPhrase(phrase) && message != null
                && folded(message).contains(folded(phrase));
    }

    /** A locally extracted candidate is only a suggestion; the user edits/approves it. */
    public static String suggestPhrase(String message) {
        if (message == null) return "";
        Matcher brandedLink = LAYLO.matcher(message);
        if (brandedLink.find()) {
            String label = brandedLink.group(1).replace('-', ' ');
            if (validPhrase(label)) return label;
        }
        Matcher title = NAMED_EVENT.matcher(message);
        while (title.find()) {
            String candidate = title.group();
            if (validPhrase(candidate)) return candidate;
        }
        return "";
    }
}
