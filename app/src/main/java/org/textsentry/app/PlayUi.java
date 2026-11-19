package org.textsentry.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Play flavor never reads, deletes or sends carrier SMS; it reviews user-submitted text. */
public final class PlayUi {
    private PlayUi() {}

    static EditText show(Activity activity, Settings settings, String incoming) {
        LinearLayout root = UiKit.home(activity);
        UiKit.brandIntro(activity, root, "Review a message",
                "Paste a text or share it from your messaging app.");

        LinearLayout review = UiKit.card(activity, root, 0xFFFFFFFF);
        LinearLayout.LayoutParams reviewParams = (LinearLayout.LayoutParams) review.getLayoutParams();
        reviewParams.topMargin = UiKit.dp(activity, 16);
        review.setLayoutParams(reviewParams);
        EditText message = UiKit.input(activity, review, "Message", incoming, true, false);
        message.setHint("Paste message text");
        CheckBox consent = new CheckBox(activity);
        consent.setText("Send this text to my AI provider for analysis");
        consent.setTextColor(UiKit.INK);
        consent.setMinHeight(UiKit.dp(activity, 48));
        review.addView(consent);
        TextView outcome = UiKit.description(activity, review, "");
        outcome.setVisibility(View.GONE);
        outcome.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Button analyze = UiKit.button(activity, review, "Analyze message", true, () -> {});
        String[] suggestedReply = {""};
        analyze.setOnClickListener(button -> {
            outcome.setVisibility(View.VISIBLE);
            suggestedReply[0] = "";
            if (!consent.isChecked()) {
                outcome.setText("Please consent to this analysis first.");
                return;
            }
            String input = message.getText().toString().trim();
            if (!MessagePolicy.eligible("+15555550123", input)) {
                outcome.setText("This text is blocked by local safety rules.");
                return;
            }
            if (!AnalysisAccess.hasProvider(activity)) {
                outcome.setText("Sign in or bring your own key to analyze.");
                return;
            }
            final String policy;
            try {
                policy = settings.effectivePrompt();
            } catch (IllegalArgumentException invalid) {
                outcome.setText("Combined rules are too long. Update Settings.");
                return;
            }
            if (policy.isEmpty()) {
                outcome.setText("Select a message category in Settings first.");
                return;
            }
            final double threshold = settings.confidence() / 100.0;
            consent.setChecked(false);
            consent.setEnabled(false);
            message.setEnabled(false);
            analyze.setEnabled(false);
            outcome.setText("Analyzing…");
            new Thread(() -> {
                String status, suggestion;
                try {
                    SystemOneClient.Evaluation evaluation = AnalysisAccess.evaluate(activity,
                            policy, input, threshold,
                            DeveloperMode.traceSink(activity, "manual"));
                    AiDecision answer = evaluation.decision;
                    new HistoryStore(activity).add("Manual review", input, evaluation.confidence,
                            answer.shouldReply ? "STOP" : "",
                            answer.shouldReply ? "manual_suggestion" : "no_reply", "manual");
                    status = (answer.shouldReply ? "Suggested reply: STOP" : "No reply recommended")
                            + " · " + Math.round(evaluation.confidence * 100) + "% match";
                    suggestion = answer.shouldReply ? "STOP" : "";
                } catch (Exception ignored) {
                    status = "Analysis failed. Check your account/key or connection.";
                    suggestion = "";
                }
                String result = status, draft = suggestion;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    outcome.setText(result);
                    suggestedReply[0] = draft;
                    consent.setEnabled(true);
                    message.setEnabled(true);
                    analyze.setEnabled(true);
                });
            }).start();
        });

        UiKit.link(activity, review, "Compose a reply", () -> composeReply(activity, suggestedReply[0]));
        return message;
    }

    private static void composeReply(Activity activity, String suggestion) {
        LinearLayout form = UiKit.vertical(activity);
        int padding = UiKit.dp(activity, 24);
        form.setPaddingRelative(padding, 0, padding, padding);
        EditText number = UiKit.input(activity, form, "Recipient number", "", false, false);
        number.setInputType(InputType.TYPE_CLASS_PHONE);
        EditText reply = UiKit.input(activity, form, "Reply", suggestion, true, false);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Compose a reply")
                .setView(UiKit.scrollable(activity, form)).setPositiveButton("Open SMS app", null)
                .setNegativeButton("Cancel", null).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String to = number.getText().toString().trim();
            String body = reply.getText().toString().trim();
            if (!to.matches("\\+?[0-9][0-9 ()-]{7,19}")) {
                number.setError("Enter a valid phone number");
                return;
            }
            if (!SystemOneClient.validReply(body)) {
                reply.setError("Enter a reply of up to 120 characters");
                return;
            }
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("smsto:" + Uri.encode(to)));
            intent.putExtra("sms_body", body);
            try {
                activity.startActivity(intent);
                dialog.dismiss();
            } catch (ActivityNotFoundException ignored) {
                UiKit.info(activity, "SMS app unavailable", "Install an SMS app to compose a reply.");
            }
        });
    }
}
