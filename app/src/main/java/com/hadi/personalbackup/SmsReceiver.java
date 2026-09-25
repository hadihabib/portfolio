package com.hadi.personalbackup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        final Context app = context.getApplicationContext();
        DiagnosticState.markSmsBroadcast(app);

        // Receiving an SMS is also a useful opportunity to revive the monitor.
        BackupService.start(app, "Incoming SMS broadcast");

        final PendingResult pending = goAsync();
        final SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (messages == null || messages.length == 0) {
                    DiagnosticState.markSmsReceiverStage(app, "Broadcast contained no SMS parts");
                    return;
                }

                DiagnosticState.markSmsParsed(app, messages.length);

                String phone = messages[0].getOriginatingAddress();
                long time = messages[0].getTimestampMillis();

                StringBuilder body = new StringBuilder();
                for (SmsMessage msg : messages) {
                    if (msg != null && msg.getMessageBody() != null) {
                        body.append(msg.getMessageBody());
                    }
                }

                DiagnosticState.markSmsReceiverStage(app, "Writing message to local database");

                BackupDb db = new BackupDb(app);
                boolean inserted = db.insertSms(
                        phone,
                        body.toString(),
                        "IN",
                        time
                );
                db.close();

                if (inserted) {
                    DiagnosticState.markSmsDbResult(app, "Saved successfully");
                    DiagnosticState.markSmsReceiverStage(app, "Completed successfully");
                } else {
                    DiagnosticState.markSmsDbResult(
                            app,
                            "Already existed / duplicate, or database insert failed"
                    );
                    DiagnosticState.markSmsReceiverStage(
                            app,
                            "Finished without creating a new database row"
                    );
                }
            } catch (Throwable error) {
                DiagnosticState.markSmsReceiverError(app, error);
            } finally {
                pending.finish();
                executor.shutdown();
            }
        });
    }
}
