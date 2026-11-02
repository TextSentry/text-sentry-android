package org.textsentry.app;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure safety rules for carrier MMS already downloaded into Android's inbox. */
final class MmsInboxPolicy {
    private MmsInboxPolicy() {}

    static boolean isNewInboxMessage(long dateSeconds, long enabledAtMillis, boolean inbox) {
        return inbox && enabledAtMillis > 0 && dateSeconds > 0
                && dateSeconds >= (enabledAtMillis + 999L) / 1000L;
    }

    static boolean mayReply(String sender, int recipientCount) {
        return recipientCount == 1 && MessagePolicy.eligible(sender, "Ordinary message");
    }

    static boolean canClassify(String body, String sender) {
        return MessagePolicy.eligible(sender, body);
    }

    static String combineText(String subject, List<String> parts, List<String> ocr) {
        StringBuilder result = new StringBuilder();
        Set<String> unique = new HashSet<>();
        append(result, unique, subject);
        if (parts != null) for (String part : parts) append(result, unique, part);
        if (ocr != null) for (String part : ocr) append(result, unique, part);
        return result.toString();
    }

    private static void append(StringBuilder out, Set<String> seen, String value) {
        if (value == null || out.length() >= 1600) return;
        String text = value.trim();
        if (text.isEmpty() || !seen.add(text)) return;
        if (out.length() > 0) out.append('\n');
        if (out.length() > 1600) out.setLength(1600);
        int available = 1600 - out.length();
        out.append(text, 0, Math.min(available, text.length()));
    }
}
