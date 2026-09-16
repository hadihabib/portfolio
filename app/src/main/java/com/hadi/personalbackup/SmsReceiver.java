package com.hadi.personalbackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (messages == null || messages.length == 0) return;
                String phone = messages[0].getOriginatingAddress();
                long time = messages[0].getTimestampMillis();
                StringBuilder body = new StringBuilder();
                for (SmsMessage msg : messages) if (msg != null && msg.getMessageBody() != null) body.append(msg.getMessageBody());
                BackupDb db = new BackupDb(app);
                db.insertSms(phone, body.toString(), "IN", time);
                db.close();
            } finally {
                pending.finish();
                executor.shutdown();
            }
        });
    }
}
