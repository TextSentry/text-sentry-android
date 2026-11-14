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

        // ACTION_SENDTO clients differ: some ignore EXTRA_TEXT but honor URI body.
        String report = body(item);
        // mailto is an opaque URI on Android: construct RFC 6068 query explicitly.
        Uri recipient = Uri.parse("mailto:" + SUPPORT
                + "?subject=" + Uri.encode(SUBJECT)
                + "&body=" + Uri.encode(report));
        Intent email = new Intent(Intent.ACTION_SENDTO, recipient);
        email.putExtra(Intent.EXTRA_EMAIL, new String[]{SUPPORT});
        email.putExtra(Intent.EXTRA_SUBJECT, SUBJECT);
        email.putExtra(Intent.EXTRA_TEXT, report);
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
            case "excepted":
            case "preview":
            case "manual_suggestion":
            case "blocked": return "Match";
            default: return "Unavailable";
        }
    }
}
