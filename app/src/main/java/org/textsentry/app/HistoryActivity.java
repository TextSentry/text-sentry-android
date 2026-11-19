package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
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
    private long roundupStart, roundupEnd;
    private boolean roundupView() { return roundupStart > 0 && roundupEnd > roundupStart; }
    private int visibleLimit = 50;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (!SetupGate.enforce(this)) return;
        roundupStart = getIntent().getLongExtra(RoundupWorker.EXTRA_START, 0);
        roundupEnd = getIntent().getLongExtra(RoundupWorker.EXTRA_END, 0);
        if (state != null) {
            roundupStart = state.getLong("roundup_start");
            roundupEnd = state.getLong("roundup_end");
            repliesOnly = state.getBoolean("replies_only");
            visibleLimit = state.getInt("visible_limit", 50);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (!SetupGate.enforce(this)) return;
        roundupStart = intent.getLongExtra(RoundupWorker.EXTRA_START, 0);
        roundupEnd = intent.getLongExtra(RoundupWorker.EXTRA_END, 0);
        repliesOnly = false;
        visibleLimit = 50;
        render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putLong("roundup_start", roundupStart);
        out.putLong("roundup_end", roundupEnd);
        out.putBoolean("replies_only", repliesOnly);
        out.putInt("visible_limit", visibleLimit);
        super.onSaveInstanceState(out);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SetupGate.enforce(this)) return;
        render();
    }

    private void render() {
        LinearLayout root = UiKit.screen(this, roundupView() ? "STOP roundup" : "History", true);
        List<HistoryStore.Entry> entries;
        HistoryStore.Summary counts;
        int roundupCount = 0;
        try {
            HistoryStore store = new HistoryStore(this);
            counts = store.summary();
            if (roundupView()) {
                roundupCount = store.roundupCount(roundupStart, roundupEnd);
                entries = store.roundupEntries(roundupStart, roundupEnd, visibleLimit);
            } else {
                entries = store.list(visibleLimit, 0, repliesOnly);
            }
        } catch (Exception error) {
            LinearLayout card = UiKit.card(this, root, 0xFFFFFFFF);
            UiKit.heading(this, card, "History unavailable");
            UiKit.description(this, card, "Could not unlock local message history. No entries were deleted.");
            return;
        }
        if (roundupView()) {
            LinearLayout summary = UiKit.card(this, root, Color.WHITE);
            UiKit.heading(this, summary, roundupCount + (roundupCount == 1 ? " STOP reply sent" : " STOP replies sent"));
            UiKit.description(this, summary, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(roundupStart)) + " – "
                    + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(new Date(roundupEnd)));
            UiKit.link(this, summary, "View full history", () -> {
                roundupStart = roundupEnd = 0;
                repliesOnly = false;
                visibleLimit = 50;
                render();
            });
        } else if (counts.total > 0 || repliesOnly) {
            UiKit.button(this, root, repliesOnly ? "Show all decisions" : "Show replies only",
                    false, () -> { repliesOnly = !repliesOnly; visibleLimit = 50; render(); });
            UiKit.description(this, root, repliesOnly
                    ? counts.replied + " replies sent or submitted"
                    : counts.total + " decisions");
        }
        for (HistoryStore.Entry entry : entries) addEntry(root, entry);

        int totalVisible = roundupView() ? roundupCount : repliesOnly ? counts.replied : counts.total;
        if (entries.size() < totalVisible) {
            UiKit.button(this, root, "Load older messages", false, () -> {
                visibleLimit += 50;
                render();
            });
        }
        if (entries.isEmpty()) {
            LinearLayout empty = UiKit.card(this, root, Color.WHITE);
            UiKit.heading(this, empty, roundupView() ? "No replies in this roundup" : repliesOnly ? "No replies yet" : "No messages reviewed yet");
            UiKit.description(this, empty, roundupView() ? "These history entries may have been cleared." : repliesOnly
                    ? "Show all decisions to see previews and messages that received no reply."
                    : "Reviewed messages will appear here.");
        }
    }

    private void addEntry(LinearLayout root, HistoryStore.Entry entry) {
        LinearLayout card = UiKit.card(this, root, Color.WHITE);
        card.setBackground(UiKit.interaction(this, Color.WHITE, 19, 0xFFDEE7F7));
        card.setFocusable(true);
        card.setOnClickListener(v -> showDetails(entry));

        LinearLayout header = UiKit.row(this);
        card.addView(header);
        LinearLayout senderAndTime = UiKit.vertical(this);
        header.addView(senderAndTime, new LinearLayout.LayoutParams(0, -2, 1f));
        UiKit.heading(this, senderAndTime, sender(entry));
        UiKit.description(this, senderAndTime, formattedTime(entry));
        TextView menu = UiKit.text(this, "⋮", 24, UiKit.INK, true);
        menu.setGravity(Gravity.CENTER);
        menu.setBackground(UiKit.interaction(this, UiKit.HIGHLIGHT, 12, 0));
        menu.setContentDescription("Message options");
        menu.setFocusable(true);
        header.addView(menu, new LinearLayout.LayoutParams(UiKit.dp(this, 48), UiKit.dp(this, 48)));
        menu.setOnClickListener(v -> showMessageActions(menu, entry));

        TextView preview = UiKit.text(this, entry.body, 15, UiKit.INK, false);
        preview.setMaxLines(2);
        preview.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(preview);
        UiKit.chip(this, card, statusLabel(entry.status));
    }

    private void showDetails(HistoryStore.Entry entry) {
        StringBuilder details = new StringBuilder(formattedTime(entry))
                .append("\n\n").append(entry.body).append("\n\n")
                .append(statusLabel(entry.status));
        if ("mms".equals(entry.source)) details.append(" · MMS text");
        if (Double.isFinite(entry.confidence)) {
            details.append("\nJev match probability: ").append(Math.round(entry.confidence * 100)).append("%");
        }
        if (!entry.reply.isEmpty()) details.append("\nSuggested reply: ").append(entry.reply);
        details.append("\nDeletion: ").append(entry.deletionStatus());
        UiKit.info(this, sender(entry), details.toString());
    }

    private String sender(HistoryStore.Entry entry) {
        return entry.sender.isEmpty() ? "Manual review" : entry.sender;
    }

    private String formattedTime(HistoryStore.Entry entry) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(entry.timestamp));
    }

    private void showMessageActions(TextView anchor, HistoryStore.Entry item) {
        PopupMenu actions = new PopupMenu(this, anchor);
        actions.getMenu().add(0, 2, 0, "Add exception");
        actions.getMenu().add(0, 1, 1, "Send for Review");
        actions.setOnMenuItemClickListener(option -> {
            if (option.getItemId() == 2) {
                showExceptionChoice(item);
                return true;
            }
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


    private void showExceptionChoice(HistoryStore.Entry item) {
        boolean senderAvailable = ExceptionRules.canonicalSender(item.sender).length() > 0
                && !"manual".equals(item.source);
        String[] choices = senderAvailable
                ? new String[]{"Protect this sender", "Suggest phrase with Jev"}
                : new String[]{"Protect a suggested phrase"};
        new AlertDialog.Builder(this)
                .setTitle("Add exception")
                .setItems(choices, (d, which) -> {
                    if (senderAvailable && which == 0) {
                        confirmSenderException(item.sender);
                    } else {
                        promptPhraseException(item.body);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmSenderException(String sender) {
        new AlertDialog.Builder(this)
                .setTitle("Never STOP this sender?")
                .setMessage("Messages from " + sender + " will still be classified, "
                        + "but automatic STOP replies will be suppressed.")
                .setPositiveButton("Protect", (d, which) -> {
                    try {
                        boolean added = new ExceptionStore(this).addSender(sender);
                        Toast.makeText(this, added ? "Sender protected" : "Already protected",
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this, "Could not save exception", Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    /** Play manual consent and sideload opt-in are explicit for this additional Jev call. */
    private void promptPhraseException(String message) {
        new AlertDialog.Builder(this)
                .setTitle("Ask Jev to suggest a phrase?")
                .setMessage("TextSentry will send this selected message to your current "
                        + "Jev provider for one additional AI decision. Jev will choose "
                        + "from names found in the message. No rule is saved until you approve it.")
                .setPositiveButton("Ask Jev", (dialog, which) -> requestJevSuggestion(message))
                .setNeutralButton("Enter manually", (dialog, which) -> showPhraseEditor("", false))
                .setNegativeButton("Cancel", null).show();
    }

    private void requestJevSuggestion(String message) {
        List<String> candidates = ExceptionPhraseCandidates.extract(message);
        if (candidates.isEmpty()) {
            Toast.makeText(this, "No candidate names found; enter a phrase manually.",
                    Toast.LENGTH_LONG).show();
            showPhraseEditor("", false);
            return;
        }
        ProgressBar progress = new ProgressBar(this);
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("Consulting Jev")
                .setMessage("Selecting a specific brand or event name…")
                .setView(progress)
                .setCancelable(false)
                .create();
        loading.show();
        new Thread(() -> {
            String phrase = "";
            boolean failed = false;
            try {
                phrase = AnalysisAccess.suggestExceptionPhrase(this, message, candidates);
            } catch (Exception error) {
                failed = true;
            }
            final String selected = phrase;
            final boolean unavailable = failed;
            runOnUiThread(() -> {
                loading.dismiss();
                if (isFinishing() || isDestroyed()) return;
                if (unavailable) {
                    Toast.makeText(this, "Jev unavailable. Enter a phrase manually.",
                            Toast.LENGTH_LONG).show();
                } else if (selected.isEmpty()) {
                    Toast.makeText(this, "Jev did not find a specific match. Enter a phrase.",
                            Toast.LENGTH_LONG).show();
                }
                showPhraseEditor(selected, !unavailable && !selected.isEmpty());
            });
        }, "textsentry-jev-exception-suggestion").start();
    }

    private void showPhraseEditor(String suggested, boolean selectedByJev) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Specific brand or event name");
        input.setText(suggested);
        input.setSelection(input.length());
        new AlertDialog.Builder(this)
                .setTitle(selectedByJev ? "Review Jev suggestion" : "Protect a phrase")
                .setMessage(selectedByJev
                        ? "Jev selected this phrase from names in your message. "
                            + "Edit it before saving. Matching messages will still be "
                            + "classified, but won't get an automatic STOP."
                        : "Enter a specific brand or event name. Matching messages "
                            + "will still be classified, but won't get STOP.")
                .setView(input)
                .setPositiveButton("Save", (d, which) -> {
                    try {
                        boolean added = new ExceptionStore(this)
                                .addPhrase(input.getText().toString());
                        Toast.makeText(this, added ? "Phrase protected" : "Already protected",
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception error) {
                        Toast.makeText(this,
                                "Use a specific brand or event name (at least 4 characters).",
                                Toast.LENGTH_LONG).show();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    static String statusLabel(String value) {
        switch (value) {
            case "sent": return "✓ Sent (Android confirmed)";
            case "submitting": return "Submitted · awaiting Android confirmation";
            case "send_failed": return "Send failed";
            case "preview": return "Preview · not sent";
            case "manual_suggestion": return "Suggested · manual send unverified";
            case "no_reply": return "No reply recommended";
            case "excepted": return "Exception matched · STOP suppressed";
            case "blocked": return "Blocked · not sent";
            default: return "Not sent";
        }
    }
}
