package org.textsentry.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;

/** Wake the existing inbox scanner; WAP pushes contain notifications, not MMS text. */
public final class MmsWakeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION.equals(intent.getAction())) {
            MmsScanWorker.scanNow(context);
        }
    }
}
