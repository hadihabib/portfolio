package com.hadi.personalbackup;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
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
    private static final long HEARTBEAT_MS = 15000L;

    private static volatile boolean running = false;

    private ExecutorService executor;
    private ContentObserver smsObserver;
    private ContentObserver callObserver;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

    public static boolean isRunning() {
        return running;
    }

    public static void start(Context context) {
        start(context, "Unspecified");
    }

    public static void start(Context context, String trigger) {
        Context app = context.getApplicationContext();
        Intent service = new Intent(app, BackupService.class);
        service.putExtra("pbackup_start_trigger", trigger);

        DiagnosticState.markServiceStartTrigger(app, trigger);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(service);
            } else {
                app.startService(service);
            }
        } catch (Throwable error) {
            DiagnosticState.markServiceStartError(app, error);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        running = true;
        DiagnosticState.markServiceStart(this);
        DiagnosticState.markHeartbeat(this);

        createNotificationChannel();

        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        startHeartbeat();

        executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        smsObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                syncRecentSms();
            }
        };

        callObserver = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                syncRecentCalls();
            }
        };

        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            getContentResolver().registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    smsObserver
            );
        }

        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            getContentResolver().registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    callObserver
            );
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

    private void startHeartbeat() {
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    DiagnosticState.markHeartbeat(getApplicationContext());
                    heartbeatHandler.postDelayed(this, HEARTBEAT_MS);
                }
            }
        };
        heartbeatHandler.post(heartbeatRunnable);
    }

    private void syncRecentSms() {
        if (executor == null || executor.isShutdown()) return;

        executor.execute(() -> {
            try {
                BackupDb db = new BackupDb(getApplicationContext());
                db.importRecentSms();
                db.close();
                DiagnosticState.markSmsSync(getApplicationContext());
            } catch (Throwable error) {
                DiagnosticState.markSmsReceiverError(getApplicationContext(), error);
            }
        });
    }

    private void syncRecentCalls() {
        if (executor == null || executor.isShutdown()) return;

        executor.execute(() -> {
            BackupDb db = new BackupDb(getApplicationContext());
            db.importRecentCalls();
            db.close();
            DiagnosticState.markCallSync(getApplicationContext());
        });
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("PBackup active")
                .setContentText("Background monitor is running")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PBackup background monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(
                    "Persistent notification while monitoring SMS and call-log changes"
            );
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        running = true;
        DiagnosticState.markServiceStart(this);
        DiagnosticState.markHeartbeat(this);

        String trigger;
        if (intent == null) {
            trigger = "Android START_STICKY restart after process/service termination";
        } else {
            trigger = intent.getStringExtra("pbackup_start_trigger");
            if (trigger == null || trigger.trim().isEmpty()) {
                trigger = "Service start";
            }
        }

        DiagnosticState.markServiceStartTrigger(this, trigger);
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        DiagnosticState.markTaskRemoved(this);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        running = false;
        DiagnosticState.markServiceStop(this);

        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }

        try {
            if (smsObserver != null) {
                getContentResolver().unregisterContentObserver(smsObserver);
            }
        } catch (Exception ignored) {
        }

        try {
            if (callObserver != null) {
                getContentResolver().unregisterContentObserver(callObserver);
            }
        } catch (Exception ignored) {
        }

        if (executor != null) {
            executor.shutdownNow();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
