package org.textsentry.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.telephony.SmsManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Fail closed: model/network/permission failures never trigger a send. */
public final class SmsWorker extends Worker {
    public SmsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull @Override public Result doWork() {
        Context context = getApplicationContext();
        Settings settings = new Settings(context);
        String queuedGeneration = getInputData().getString("generation");
        if (!SetupGate.canEnterApp(context) || !settings.monitoring()
                || !settings.generation().equals(queuedGeneration)) return Result.success();
        String source = "mms".equals(getInputData().getString("source")) ? "mms" : "sms";
        boolean replyAllowed = !"mms".equals(source)
                || getInputData().getBoolean("reply_allowed", false);
        if ("mms".equals(source) && (!settings.mmsEnabled()
                || context.checkSelfPermission(Manifest.permission.READ_SMS)
                    != PackageManager.PERMISSION_GRANTED)) return Result.success();
        String sender = getInputData().getString("sender");
        String message = getInputData().getString("sms");
        if (!MessagePolicy.eligible(sender, message)) {
            settings.addHistory("Ignored (local safety rule)");
            return Result.success();
        }
        try {
            String combinedInstructions = settings.effectivePrompt();
            if (combinedInstructions.isEmpty()) {
                settings.addHistory("Ignored (no categories selected)");
                return Result.success();
            }
            if (!AnalysisAccess.hasProvider(context)) {
                settings.addHistory("Skipped (sign-in or API key needed)");
                return Result.success();
            }
            ReplyLedger ledger = new ReplyLedger(context);
            if (!ledger.reserveMessage(sender, message, System.currentTimeMillis())) {
                settings.addHistory("Ignored (duplicate SMS)");
                return Result.success();
            }

            SystemOneClient.Evaluation evaluation = AnalysisAccess.evaluate(context,
                    combinedInstructions, message, settings.confidence() / 100.0,
                    DeveloperMode.traceSink(context, source));
            if (!settings.monitoring() || !settings.generation().equals(queuedGeneration)
                    || ("mms".equals(source) && !settings.mmsEnabled())) {
                return Result.success();
            }
            AiDecision decision = evaluation.decision;
            HistoryStore history = new HistoryStore(context);
            if (!decision.shouldReply) {
                history.add(sender, message, evaluation.confidence, "", "no_reply", source);
                settings.addHistory("AI: no reply");
            } else if (!replyAllowed) {
                history.add(sender, message, evaluation.confidence, "", "blocked", source);
                settings.addHistory("Group MMS: automatic STOP blocked");
            } else if (!settings.autoSend()) {
                history.add(sender, message, evaluation.confidence, decision.reply, "preview", source);
                settings.addHistory("Preview only: " + decision.reply);
            } else if (context.checkSelfPermission(Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                history.add(sender, message, evaluation.confidence, decision.reply, "blocked", source);
                settings.addHistory("Blocked: send permission/consent missing");
            } else {
                if (!ledger.reserveSend(sender, System.currentTimeMillis())) {
                    history.add(sender, message, evaluation.confidence, decision.reply, "blocked", source);
                    settings.addHistory("Blocked: recipient daily reply limit");
                    return Result.success();
                }
                SmsManager manager = context.getSystemService(SmsManager.class);
                if (manager == null) throw new IllegalStateException("SMS service unavailable");
                // Audit before invoking carrier APIs. A queued send is NOT a confirmed send.
                String historyId = history.add(sender, message, evaluation.confidence,
                        decision.reply, "submitting", source);
                Intent sentCallback = new Intent(context, SmsSentReceiver.class)
                        .setAction(SmsSentReceiver.ACTION)
                        .setData(Uri.parse("textsentry://sent/" + historyId));
                PendingIntent sent = PendingIntent.getBroadcast(context, historyId.hashCode(),
                        sentCallback, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                try {
                    manager.sendTextMessage(sender, null, decision.reply, sent, null);
                    settings.addHistory("SMS send submitted: " + decision.reply);
                } catch (Exception failure) {
                    history.markSendResult(historyId, false);
                    throw failure;
                }
            }
        } catch (Exception e) {
            settings.addHistory("No reply: provider or Android error");
        }
        return Result.success();
    }
}
