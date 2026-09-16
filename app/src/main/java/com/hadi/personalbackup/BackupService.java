package com.hadi.personalbackup;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.Telephony;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupService extends Service {
    private static final String CHANNEL_ID = "backup_active";
    private static final int NOTIFICATION_ID = 1001;
    private ExecutorService executor;
    private ContentObserver smsObserver;
    private ContentObserver callObserver;

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        smsObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { syncRecentSms(); }
        };
        callObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { syncRecentCalls(); }
        };

        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            getContentResolver().registerContentObserver(Telephony.Sms.CONTENT_URI, true, smsObserver);
        }
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            getContentResolver().registerContentObserver(CallLog.Calls.CONTENT_URI, true, callObserver);
        }
        executor.execute(() -> {
            BackupDb db = new BackupDb(getApplicationContext());
            AutoTxtBackup.ensureFiles(
                getApplicationContext(),
                db.exportSmsText(),
                db.exportCallsText()
            );
            db.close();
        });

        syncRecentSms();
        syncRecentCalls();
    }

    private void syncRecentSms() {
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> { BackupDb db = new BackupDb(getApplicationContext()); db.importRecentSms(); db.close(); });
    }

    private void syncRecentCalls() {
        if (executor == null || executor.isShutdown()) return;
        executor.execute(() -> { BackupDb db = new BackupDb(getApplicationContext()); db.importRecentCalls(); db.close(); });
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setContentTitle("PBackup active")
                .setContentText("Saving SMS and call history locally")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "PBackup", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Persistent notification while monitoring SMS and call-log changes");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        try { if (smsObserver != null) getContentResolver().unregisterContentObserver(smsObserver); } catch (Exception ignored) {}
        try { if (callObserver != null) getContentResolver().unregisterContentObserver(callObserver); } catch (Exception ignored) {}
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
