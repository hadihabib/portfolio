package com.hadi.personalbackup;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PERMISSION_REQUEST = 2001;
    private static final int FOLDER_REQUEST = 2002;

    private TextView statusText;
    private TextView countText;
    private TextView locationText;
    private ExecutorService executor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = Executors.newSingleThreadExecutor();
        buildUi();
        refreshStatus();

        if (hasCorePermissions()) {
            startBackupService();
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
        subtitle.setText("\nLocal backup for SMS messages and call history.");
        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWrap());

        statusText = new TextView(this);
        statusText.setTextSize(18);
        statusText.setPadding(0, dp(28), 0, dp(10));
        statusText.setGravity(Gravity.CENTER);
        root.addView(statusText, matchWrap());

        countText = new TextView(this);
        countText.setTextSize(17);
        countText.setGravity(Gravity.CENTER);
        countText.setPadding(0, 0, 0, dp(16));
        root.addView(countText, matchWrap());

        locationText = new TextView(this);
        locationText.setTextSize(15);
        locationText.setTextColor(Color.DKGRAY);
        locationText.setGravity(Gravity.START);
        locationText.setPadding(0, 0, 0, dp(18));
        root.addView(locationText, matchWrap());

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
                Toast.makeText(this, "Choose a backup location first.", Toast.LENGTH_LONG).show();
                chooseBackupFolder();
                return;
            }

            startBackupService();
            refreshStatus();
            Toast.makeText(this, "Automatic backup started", Toast.LENGTH_SHORT).show();
        });
        root.addView(start, buttonLayout());

        Button messages = makeButton("Messages Log");
        messages.setOnClickListener(v -> startActivity(new Intent(this, MessagesActivity.class)));
        root.addView(messages, buttonLayout());

        Button calls = makeButton("Calls Log");
        calls.setOnClickListener(v -> startActivity(new Intent(this, CallsActivity.class)));
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
                    Toast.makeText(this, "Import finished", Toast.LENGTH_SHORT).show();
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

        TextView note = new TextView(this);
        note.setText(
            "\nThe selected folder contains a hidden .pbackup folder with two fixed files. " +
            "New records are appended automatically to the same files. " +
            "Hidden folders can still be shown by file managers that have “Show hidden files” enabled."
        );
        note.setTextSize(14);
        note.setTextColor(Color.GRAY);
        note.setGravity(Gravity.START);
        root.addView(note, matchWrap());

        scroll.addView(root);
        setContentView(scroll);
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
        return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED;
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
            startBackupService();
            refreshStatus();
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show();
        } else {
            requestPermissions(needed.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    private void addIfMissing(List<String> list, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            list.add(permission);
        }
    }

    private void startBackupService() {
        Intent intent = new Intent(this, BackupService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not start backup service: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refreshStatus() {
        boolean permissionsOk = hasCorePermissions();
        boolean locationOk = AutoTxtBackup.hasBackupLocation(this);

        statusText.setText(
                permissionsOk && locationOk
                        ? "● Backup ready"
                        : (!permissionsOk ? "● Permissions required" : "● Backup location required")
        );

        statusText.setTextColor(
                permissionsOk && locationOk
                        ? Color.rgb(20, 120, 55)
                        : Color.rgb(180, 70, 40)
        );

        locationText.setText(
                "Backup location: " + AutoTxtBackup.locationLabel(this) +
                "\nHidden data: .pbackup/.mstore.dat + .cstore.dat"
        );

        executor.execute(() -> {
            BackupDb db = new BackupDb(getApplicationContext());
            int sms = db.smsCount();
            int calls = db.callCount();
            db.close();

            runOnUiThread(() ->
                    countText.setText("Saved messages: " + sms + "\nSaved calls: " + calls)
            );
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FOLDER_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
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
                        if (hasCorePermissions()) startBackupService();
                        Toast.makeText(this, "Backup location saved", Toast.LENGTH_SHORT).show();
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST) {
            if (hasCorePermissions()) {
                startBackupService();
                Toast.makeText(this, "Automatic backup enabled", Toast.LENGTH_SHORT).show();
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
        if (statusText != null) refreshStatus();
    }

    @Override
    protected void onDestroy() {
        if (executor != null) executor.shutdownNow();
        super.onDestroy();
    }
}
