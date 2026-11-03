package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
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
            LinearLayout cardHeader = new LinearLayout(this);
            cardHeader.setOrientation(LinearLayout.HORIZONTAL);
            cardHeader.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(cardHeader);
            TextView sender = UiKit.text(this,
                    item.sender.isEmpty() ? "Manual review" : item.sender, 19, UiKit.INK, true);
            cardHeader.addView(sender, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView menuButton = UiKit.text(this, "⋮", 28, UiKit.INK, true);
            menuButton.setGravity(Gravity.CENTER);
            menuButton.setBackground(UiKit.shape(UiKit.HIGHLIGHT, UiKit.dp(this, 10), 0));
            menuButton.setContentDescription("Message options");
            menuButton.setClickable(true);
            menuButton.setFocusable(true);
            cardHeader.addView(menuButton, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 48), UiKit.dp(this, 48)));
            menuButton.setOnClickListener(v -> showMessageActions(menuButton, item));
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

    private void showMessageActions(TextView anchor, HistoryStore.Entry item) {
        PopupMenu actions = new PopupMenu(this, anchor);
        actions.getMenu().add(0, 1, 0, "Send for Review");
        actions.setOnMenuItemClickListener(option -> {
            if (option.getItemId() != 1) return false;
            try {
                // Opens the user's email app; no SMS or decision is sent by TextSentry.
                startActivity(HistoryReviewEmail.createIntent(item));
            } catch (ActivityNotFoundException unavailable) {
                Toast.makeText(this, "No email app available", Toast.LENGTH_LONG).show();
            }
            return true;
        });
        actions.show();
    }

    static String statusLabel(String value) {
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
