package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.LinearLayout;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Private, full message history with model probability and true send status. */
public final class HistoryActivity extends Activity {
    private boolean repliesOnly;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SetupGate.enforce(this)) return;
        render();
    }
    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        render();
    }

    private void render() {
        LinearLayout root = UiKit.screen(this, "History", true);
        List<HistoryStore.Entry> entries;
        try {
            entries = new HistoryStore(this).list();
        } catch (Exception error) {
            LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, card, "History unavailable");
            UiKit.description(this, card, "Could not unlock local message history. No entries were deleted.");
            return;
        }
        int replied = 0, preview = 0;
        for (HistoryStore.Entry entry : entries) {
            if ("sent".equals(entry.status) || "submitting".equals(entry.status)) replied++;
            if ("preview".equals(entry.status) || "manual_suggestion".equals(entry.status)) preview++;
        }
        LinearLayout summary = UiKit.card(this, root, UiKit.HIGHLIGHT);
        UiKit.heading(this, summary, "Activity");
        UiKit.description(this, summary,
                entries.size() + " evaluated · " + replied + " sent/submitted · " + preview + " previews");
        UiKit.description(this, summary, "Encrypted on this device.");
        UiKit.button(this, summary, repliesOnly ? "Show all decisions" : "Show replies only",
                false, () -> { repliesOnly = !repliesOnly; render(); });


        int visible = 0;
        for (HistoryStore.Entry item : entries) {
            if (repliesOnly && !("sent".equals(item.status) || "submitting".equals(item.status))) {
                continue;
            }
            visible++;
            LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, card, item.sender.isEmpty() ? "Manual review" : item.sender);
            UiKit.description(this, card,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(item.timestamp)));
            if ("mms".equals(item.source)) {
                UiKit.chip(this, card, "Multimedia message (MMS) · text only");
            }
            UiKit.chip(this, card, statusLabel(item.status));

            android.widget.TextView body = UiKit.text(this, item.body, 15, UiKit.INK, false);
            body.setPadding(0, UiKit.dp(this, 5), 0, UiKit.dp(this, 8));
            card.addView(body);
            if (Double.isFinite(item.confidence)) {
                UiKit.description(this, card, "Jev match probability: " +
                        Math.round(item.confidence * 100) + "%");
            }
            if (!item.reply.isEmpty()) UiKit.description(this, card, "Suggested reply: " + item.reply);
            UiKit.description(this, card, "Deletion: " + item.deletionStatus());
        }
        if (visible == 0) {
            LinearLayout empty = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, empty, repliesOnly ? "No replies yet" : "No history yet");
            UiKit.description(this, empty, repliesOnly
                    ? "Show all decisions to see previews and ignored messages."
                    : "Evaluated messages will appear here. Older summaries cannot be restored.");
        }
        UiKit.button(this, root, "Clear history", false, () ->
                new AlertDialog.Builder(this)
                        .setTitle("Clear message history?")
                        .setMessage("This permanently removes saved message text, confidence scores " +
                                "and send statuses from TextSentry. It does not delete SMS from the inbox.")
                        .setPositiveButton("Clear", (dialog, which) -> {
                            try {
                                new HistoryStore(this).clear();
                                new Settings(this).clearHistory();
                                render();
                            } catch (Exception error) {
                                new AlertDialog.Builder(this).setMessage(
                                        "Could not clear local history.").setPositiveButton("OK", null).show();
                            }
                        }).setNegativeButton("Cancel", null).show());
    }

    private static String statusLabel(String value) {
        switch (value) {
            case "sent": return "✓ Sent (Android confirmed)";
            case "submitting": return "Submitted · awaiting Android confirmation";
            case "send_failed": return "Send failed";
            case "preview": return "Preview · not sent";
            case "manual_suggestion": return "Suggested · manual send unverified";
            case "no_reply": return "No reply recommended";
            case "blocked": return "Blocked · not sent";
            default: return "Not sent";
        }
    }
}
