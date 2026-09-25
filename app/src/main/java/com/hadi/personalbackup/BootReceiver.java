package com.hadi.personalbackup;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        boolean supported =
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (!supported) return;

        DiagnosticState.markBootEvent(context);

        boolean hasSms =
                context.checkSelfPermission(Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED;

        boolean hasCalls =
                context.checkSelfPermission(Manifest.permission.READ_CALL_LOG)
                        == PackageManager.PERMISSION_GRANTED;

        if (!hasSms && !hasCalls) return;

        BackupService.start(
                context,
                Intent.ACTION_BOOT_COMPLETED.equals(action)
                        ? "Device boot completed"
                        : "PBackup package updated"
        );
    }
}
