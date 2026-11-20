package org.textsentry.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

/** Capture SMS quickly and delegate networking to WorkManager. */
public final class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        Settings settings = new Settings(context);
        if (!settings.monitoring()) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        String sender = parts[0].getOriginatingAddress();
        if (sender == null || !ContactSafety.canAnalyze(context, sender)) return;
        StringBuilder message = new StringBuilder();
        for (SmsMessage part : parts) {
            if (!sender.equals(part.getOriginatingAddress())) return;
            if (part.getMessageBody() != null) message.append(part.getMessageBody());
        }
        Data data = new Data.Builder().putString("sender", sender)
                .putString("sms", message.toString())
                .putLong("received_at_ms", parts[0].getTimestampMillis())
                .putString("generation", settings.generation()).build();
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(SmsWorker.class)
                .setInputData(data)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(context).enqueue(work);
    }
}
