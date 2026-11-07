package org.textsentry.app;

import android.content.Intent;
import android.net.Uri;
import java.text.DateFormat;
import java.util.Date;

/** User-initiated email draft only: TextSentry never transmits a review automatically. */
final class HistoryReviewEmail {
    private static final String SUPPORT = "support@textsentry.org";
    private static final String SUBJECT = "TextSentry message review";

    private HistoryReviewEmail() {}

    static Intent createIntent(HistoryStore.Entry item) {
        String message = body(item);
        // Some mail clients ignore EXTRA_TEXT on ACTION_SENDTO. Include the body
        // in the mailto URI as well, while retaining extras for other clients.
        Uri uri = Uri.parse("mailto:" + SUPPORT).buildUpon()
                .appendQueryParameter("subject", SUBJECT)
                .appendQueryParameter("body", message)
                .build();
        Intent email = new Intent(Intent.ACTION_SENDTO, uri);
        email.putExtra(Intent.EXTRA_EMAIL, new String[]{SUPPORT});
        email.putExtra(Intent.EXTRA_SUBJECT, SUBJECT);
        email.putExtra(Intent.EXTRA_TEXT, message);
        return email;
    }

    private static String body(HistoryStore.Entry item) {
        String type = "mms".equals(item.source) ? "MMS"
                : "sms".equals(item.source) ? "SMS" : "Manual analysis";
        String probability = Double.isFinite(item.confidence)
                ? Math.round(item.confidence * 100) + "%" : "Unavailable";
        StringBuilder report = new StringBuilder()
                .append("Please review this TextSentry classification.\n\n")
                .append("Message:\n").append(item.body)
                .append("\n\nSender: ").append(item.sender)
                .append("\nReceived: ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(item.timestamp)))
                .append("\nMessage type: ").append(type)
                .append("\nJev decision: ").append(decision(item.status))
                .append("\nJev match probability: ").append(probability)
                .append("\nReply status: ").append(HistoryActivity.statusLabel(item.status));
        if (!item.reply.isEmpty()) {
            report.append("\nSuggested reply: ").append(item.reply);
        }
        return report.toString();
    }

    /** History stores the resulting action, not raw model output. Never guess unknown states. */
    private static String decision(String status) {
        switch (status) {
            case "no_reply": return "No match";
            case "sent":
            case "submitting":
            case "send_failed":
            case "preview":
            case "manual_suggestion":
            case "blocked": return "Match";
            default: return "Unavailable";
        }
    }
}
