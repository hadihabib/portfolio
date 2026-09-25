package com.hadi.personalbackup;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 2001;
    private static final int FOLDER_REQUEST = 2002;

    private TextView statusText;
    private TextView countText;
    private TextView locationText;
    private TextView diagnosisText;
    private TextView diagnosticsText;

    private ExecutorService executor;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable diagnosticsRefresh = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        buildUi();
        refreshStatus();

        if (hasCorePermissions()) {
            BackupService.start(this, "App opened");
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(30), dp(24), dp(30));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("PBackup");
        title.setTextSize(30);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("\nv5.1 diagnostic build — SMS and call backup");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setPadding(0, dp(24), 0, dp(10));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap());

        countText = new TextView(this);
        countText.setTextSize(17);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(0, 0, 0, dp(14));
        root.addView(countText, matchWrap());

        locationText = new TextView(this);
        locationText.setTextSize(15);
        locationText.setTextColor(Color.DKGRAY);
        locationText.setGravity(Gravity.START);
        locationText.setPadding(0, 0, 0, dp(18));
        root.addView(locationText, matchWrap());

        TextView diagnosisTitle = new TextView(this);
        diagnosisTitle.setText("Likely cause");
        diagnosisTitle.setTextSize(21);
        diagnosisTitle.setTextColor(Color.BLACK);
        diagnosisTitle.setPadding(0, dp(8), 0, dp(8));
        root.addView(diagnosisTitle, matchWrap());

        diagnosisText = new TextView(this);
        diagnosisText.setTextSize(15);
        diagnosisText.setTextColor(Color.BLACK);
        diagnosisText.setGravity(Gravity.START);
        diagnosisText.setPadding(dp(12), dp(12), dp(12), dp(12));
        diagnosisText.setBackgroundColor(Color.rgb(238, 244, 248));
        root.addView(diagnosisText, matchWrap());

        TextView diagnosticsTitle = new TextView(this);
        diagnosticsTitle.setText("Diagnostics timeline");
        diagnosticsTitle.setTextSize(21);
        diagnosticsTitle.setTextColor(Color.BLACK);
        diagnosticsTitle.setPadding(0, dp(20), 0, dp(8));
        root.addView(diagnosticsTitle, matchWrap());

        diagnosticsText = new TextView(this);
        diagnosticsText.setTextSize(14);
        diagnosticsText.setTextColor(Color.DKGRAY);
        diagnosticsText.setGravity(Gravity.START);
        diagnosticsText.setPadding(dp(12), dp(12), dp(12), dp(12));
        diagnosticsText.setBackgroundColor(Color.rgb(245, 245, 245));
        root.addView(diagnosticsText, matchWrap());

        Button copy = makeButton("Copy diagnostics");
        copy.setOnClickListener(v -> copyDiagnostics());
        root.addView(copy, buttonLayout());

        Button restart = makeButton("Restart background monitor");
        restart.setOnClickListener(v -> restartMonitor());
        root.addView(restart, buttonLayout());

        Button permissions = makeButton("Grant permissions");
        permissions.setOnClickListener(v -> requestNeededPermissions());
        root.addView(permissions, buttonLayout());

        Button location = makeButton("Choose / Change backup location");
        location.setOnClickListener(v -> chooseBackupFolder());
        root.addView(location, buttonLayout());

        Button start = makeButton("Start automatic backup");
        start.setOnClickListener(v -> {
            if (!hasCorePermissions()) {
                requestNeededPermissions();
                return;
            }

            if (!AutoTxtBackup.hasBackupLocation(this)) {
                Toast.makeText(
                        this,
                        "Choose a backup location first.",
                        Toast.LENGTH_LONG
                ).show();
                chooseBackupFolder();
                return;
            }

            BackupService.start(this, "Start automatic backup button");
            refreshStatus();

            Toast.makeText(
                    this,
                    "Automatic backup started",
                    Toast.LENGTH_SHORT
            ).show();
        });
        root.addView(start, buttonLayout());

        Button messages = makeButton("Messages Log");
        messages.setOnClickListener(v ->
                startActivity(new Intent(this, MessagesActivity.class))
        );
        root.addView(messages, buttonLayout());

        Button calls = makeButton("Calls Log");
        calls.setOnClickListener(v ->
                startActivity(new Intent(this, CallsActivity.class))
        );
        root.addView(calls, buttonLayout());

        Button importExisting = makeButton("Import existing records");
        importExisting.setOnClickListener(v -> {
            if (!hasCorePermissions()) {
                requestNeededPermissions();
                return;
            }

            Toast.makeText(this, "Import started...", Toast.LENGTH_SHORT).show();

            executor.execute(() -> {
                BackupDb db = new BackupDb(getApplicationContext());
                db.importAllSms();
                db.importAllCalls();

                AutoTxtBackup.ensureFiles(
                        getApplicationContext(),
                        db.exportSmsText(),
                        db.exportCallsText()
                );

                db.close();

                runOnUiThread(() -> {
                    refreshStatus();
                    Toast.makeText(
                            this,
                            "Import finished",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            });
        });
        root.addView(importExisting, buttonLayout());

        Button settings = makeButton("Open app settings");
        settings.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        root.addView(settings, buttonLayout());

        Button battery = makeButton("Open battery optimization settings");
        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        root.addView(battery, buttonLayout());

        TextView note = new TextView(this);
        note.setText(
                "\nHow to test: keep this build installed, receive an SMS, delete it immediately, " +
                "then open PBackup. Check “Last SMS broadcast”, “Receiver stage”, “DB result”, " +
                "“Last message saved”, and “Last process exit”. If the SMS broadcast time never " +
                "changes, Android did not deliver the SMS event to PBackup."
        );
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.START);
        root.addView(note, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
    }

    private void restartMonitor() {
        try {
            stopService(new Intent(this, BackupService.class));
        } catch (Exception ignored) {
        }

        handler.postDelayed(() -> {
            BackupService.start(this, "Restart monitor button");
            refreshStatus();
            Toast.makeText(
                    this,
                    "Background monitor restarted",
                    Toast.LENGTH_SHORT
            ).show();
        }, 350);
    }

    private void chooseBackupFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(intent, FOLDER_REQUEST);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(17);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams buttonLayout() {
        LinearLayout.LayoutParams p = matchWrap();
        p.topMargin = dp(10);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean hasCorePermissions() {
        return hasPermission(Manifest.permission.RECEIVE_SMS)
                && hasPermission(Manifest.permission.READ_SMS)
                && hasPermission(Manifest.permission.READ_CALL_LOG);
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();

        addIfMissing(needed, Manifest.permission.RECEIVE_SMS);
        addIfMissing(needed, Manifest.permission.READ_SMS);
        addIfMissing(needed, Manifest.permission.READ_CALL_LOG);
        addIfMissing(needed, Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
        }

        if (needed.isEmpty()) {
            BackupService.start(this, "Permissions already granted");
            refreshStatus();
            Toast.makeText(
                    this,
                    "Permissions already granted",
                    Toast.LENGTH_SHORT
            ).show();
        } else {
            requestPermissions(
                    needed.toArray(new String[0]),
                    PERMISSION_REQUEST
            );
        }
    }

    private void addIfMissing(List<String> list, String permission) {
        if (!hasPermission(permission)) {
            list.add(permission);
        }
    }

    private String yesNo(boolean ok) {
        return ok ? "OK" : "MISSING";
    }

    private String time(long value) {
        if (value <= 0L) return "Never";

        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date(value));
    }

    private boolean heartbeatFresh(long heartbeat) {
        return heartbeat > 0L
                && System.currentTimeMillis() - heartbeat < 45000L;
    }

    private String buildDiagnosis() {
        if (!hasPermission(Manifest.permission.RECEIVE_SMS)) {
            return "SMS reception permission is missing. Android cannot deliver SMS_RECEIVED to PBackup.";
        }

        long broadcast = DiagnosticState.getLastSmsBroadcast(this);
        long saved = DiagnosticState.getLastMessageSaved(this);
        long heartbeat = DiagnosticState.getLastHeartbeat(this);
        long stop = DiagnosticState.getLastServiceStop(this);
        long exit = ExitInfoHelper.latestExitTimestamp(this);

        String stage = DiagnosticState.getLastSmsReceiverStage(this);
        String receiverError = DiagnosticState.getLastSmsReceiverError(this);
        String startError = DiagnosticState.getLastServiceStartError(this);

        if (startError != null && !startError.isEmpty()) {
            return "Android rejected a background-service start. Last error: " + startError;
        }

        if (receiverError != null && !receiverError.isEmpty()) {
            return "The SMS broadcast reached PBackup, but the receiver failed: " + receiverError;
        }

        if (broadcast > 0L && broadcast > saved + 3000L) {
            return "An SMS broadcast reached PBackup, but no new message was saved afterward. " +
                    "Receiver stage: " + stage + ". DB result: " +
                    DiagnosticState.getLastSmsDbResult(this) + ".";
        }

        if (!BackupService.isRunning() && !heartbeatFresh(heartbeat)) {
            if (exit > heartbeat && exit > 0L) {
                return "The previous PBackup process stopped. Android recorded: " +
                        ExitInfoHelper.latestExitSummary(this) + ".";
            }

            if (stop > 0L && stop >= heartbeat) {
                return "The background service stopped normally enough for onDestroy() to run at " +
                        time(stop) + ".";
            }

            return "The background monitor is not alive. Last confirmed heartbeat was " +
                    time(heartbeat) + ". Android may have killed/frozen the process without calling " +
                    "onDestroy(), or the app may have been force-stopped/restricted.";
        }

        if (broadcast == 0L) {
            return "No SMS broadcast has reached this build yet. Send one test SMS while the phone " +
                    "is locked, then reopen PBackup.";
        }

        if (broadcast <= saved + 3000L) {
            if (DiagnosticState.getLastFileWrite(this) > 0L
                    && !DiagnosticState.wasLastFileWriteOk(this)) {
                return "The SMS was saved inside PBackup, but the external backup-file write failed.";
            }

            return "The latest received SMS passed the receiver and was saved. If a different SMS " +
                    "was missed later, compare its time with “Last SMS broadcast”.";
        }

        return "No single cause is confirmed yet. Use the timestamps below after the next missed SMS.";
    }

    private String buildDiagnosticsText() {
        boolean notificationOk =
                Build.VERSION.SDK_INT < 33
                        || hasPermission(Manifest.permission.POST_NOTIFICATIONS);

        long heartbeat = DiagnosticState.getLastHeartbeat(this);

        return
                "Background monitor now: " +
                        (BackupService.isRunning() ? "RUNNING" : "STOPPED") +
                "\nHeartbeat fresh: " + (heartbeatFresh(heartbeat) ? "YES" : "NO") +
                "\nLast heartbeat: " + time(heartbeat) +
                "\nLast service start: " + time(DiagnosticState.getLastServiceStart(this)) +
                "\nStart trigger: " + DiagnosticState.getLastServiceStartTrigger(this) +
                "\nLast service stop/onDestroy: " + time(DiagnosticState.getLastServiceStop(this)) +
                "\nLast task removed from Recents: " + time(DiagnosticState.getLastTaskRemoved(this)) +
                "\nLast boot/update event: " + time(DiagnosticState.getLastBootEvent(this)) +
                "\nLast process exit: " + ExitInfoHelper.latestExitSummary(this) +
                "\n\nSMS receiver permission: " + yesNo(hasPermission(Manifest.permission.RECEIVE_SMS)) +
                "\nRead SMS permission: " + yesNo(hasPermission(Manifest.permission.READ_SMS)) +
                "\nCall log permission: " + yesNo(hasPermission(Manifest.permission.READ_CALL_LOG)) +
                "\nNotification permission: " + yesNo(notificationOk) +
                "\nBackup folder: " + yesNo(AutoTxtBackup.hasBackupLocation(this)) +
                "\n\nLast SMS broadcast: " + time(DiagnosticState.getLastSmsBroadcast(this)) +
                "\nLast SMS parsed: " + time(DiagnosticState.getLastSmsParsed(this)) +
                "\nReceiver stage: " + DiagnosticState.getLastSmsReceiverStage(this) +
                "\nReceiver error: " + emptyAsNone(DiagnosticState.getLastSmsReceiverError(this)) +
                "\nDB result: " + DiagnosticState.getLastSmsDbResult(this) +
                "\nLast message saved: " + time(DiagnosticState.getLastMessageSaved(this)) +
                "\nLast SMS provider sync: " + time(DiagnosticState.getLastSmsSync(this)) +
                "\n\nLast call saved: " + time(DiagnosticState.getLastCallSaved(this)) +
                "\nLast call-log sync: " + time(DiagnosticState.getLastCallSync(this)) +
                "\n\nLast file write: " + time(DiagnosticState.getLastFileWrite(this)) +
                " (" +
                (DiagnosticState.getLastFileWrite(this) == 0L
                        ? "No data"
                        : (DiagnosticState.wasLastFileWriteOk(this) ? "OK" : "FAILED")) +
                ")";
    }

    private String emptyAsNone(String value) {
        return value == null || value.trim().isEmpty() ? "None" : value;
    }

    private void copyDiagnostics() {
        String report =
                "PBackup v5.1\n\nLIKELY CAUSE\n" +
                buildDiagnosis() +
                "\n\nDIAGNOSTICS\n" +
                buildDiagnosticsText();

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        clipboard.setPrimaryClip(
                ClipData.newPlainText("PBackup diagnostics", report)
        );

        Toast.makeText(
                this,
                "Diagnostics copied",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void refreshStatus() {
        boolean permissionsOk = hasCorePermissions();
        boolean locationOk = AutoTxtBackup.hasBackupLocation(this);
        boolean monitorRunning = BackupService.isRunning();
        boolean heartbeatOk = heartbeatFresh(DiagnosticState.getLastHeartbeat(this));

        statusText.setText(
                permissionsOk && locationOk && monitorRunning && heartbeatOk
                        ? "● Backup monitor healthy"
                        : (!permissionsOk
                            ? "● Permissions required"
                            : (!locationOk
                                ? "● Backup location required"
                                : "● Monitor needs attention"))
        );

        statusText.setTextColor(
                permissionsOk && locationOk && monitorRunning && heartbeatOk
                        ? Color.rgb(20, 120, 55)
                        : Color.rgb(180, 70, 40)
        );

        locationText.setText(
                "Backup location: " + AutoTxtBackup.locationLabel(this) +
                "\nHidden data: .pbackup/.mstore.dat + .cstore.dat"
        );

        diagnosisText.setText(buildDiagnosis());
        diagnosticsText.setText(buildDiagnosticsText());

        if (executor == null || executor.isShutdown()) return;

        executor.execute(() -> {
            BackupDb db = new BackupDb(getApplicationContext());
            int sms = db.smsCount();
            int calls = db.callCount();
            db.close();

            runOnUiThread(() ->
                    countText.setText(
                            "Saved messages: " + sms +
                            "\nSaved calls: " + calls
                    )
            );
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FOLDER_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            Uri uri = data.getData();

            executor.execute(() -> {
                BackupDb db = new BackupDb(getApplicationContext());

                boolean ok = AutoTxtBackup.setBackupLocation(
                        getApplicationContext(),
                        uri,
                        db.exportSmsText(),
                        db.exportCallsText()
                );

                db.close();

                runOnUiThread(() -> {
                    refreshStatus();

                    if (ok) {
                        if (hasCorePermissions()) {
                            BackupService.start(this, "Backup folder selected");
                        }

                        Toast.makeText(
                                this,
                                "Backup location saved",
                                Toast.LENGTH_SHORT
                        ).show();
                    } else {
                        Toast.makeText(
                                this,
                                "Could not use this folder. Try another folder.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISSION_REQUEST) {
            if (hasCorePermissions()) {
                BackupService.start(this, "Permissions granted");
                Toast.makeText(
                        this,
                        "Automatic backup enabled",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this,
                        "SMS and call-log permissions are required for backup.",
                        Toast.LENGTH_LONG
                ).show();
            }

            refreshStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(diagnosticsRefresh);
        handler.post(diagnosticsRefresh);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(diagnosticsRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(diagnosticsRefresh);

        if (executor != null) {
            executor.shutdownNow();
        }

        super.onDestroy();
    }
}
