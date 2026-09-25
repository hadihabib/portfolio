package com.hadi.personalbackup;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ExitInfoHelper {
    private ExitInfoHelper() {}

    public static String latestExitSummary(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            return "Not available (requires Android 11+)";
        }

        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return "Unavailable";

            List<ApplicationExitInfo> history =
                    manager.getHistoricalProcessExitReasons(
                            context.getPackageName(),
                            0,
                            10
                    );

            if (history == null || history.isEmpty()) {
                return "No process-exit record";
            }

            ApplicationExitInfo newest = history.get(0);
            for (ApplicationExitInfo item : history) {
                if (item.getTimestamp() > newest.getTimestamp()) {
                    newest = item;
                }
            }

            StringBuilder text = new StringBuilder();
            text.append(formatTime(newest.getTimestamp()));
            text.append(" — ");
            text.append(reasonName(newest.getReason()));

            CharSequence description = newest.getDescription();
            if (description != null && description.length() > 0) {
                text.append(" (").append(description).append(")");
            }

            if (newest.getStatus() != 0) {
                text.append(" [status ").append(newest.getStatus()).append("]");
            }

            return text.toString();
        } catch (Throwable error) {
            return "Could not read exit reason: " + error.getClass().getSimpleName();
        }
    }

    public static long latestExitTimestamp(Context context) {
        if (Build.VERSION.SDK_INT < 30) return 0L;

        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            if (manager == null) return 0L;

            List<ApplicationExitInfo> history =
                    manager.getHistoricalProcessExitReasons(
                            context.getPackageName(),
                            0,
                            10
                    );

            long newest = 0L;
            if (history != null) {
                for (ApplicationExitInfo item : history) {
                    newest = Math.max(newest, item.getTimestamp());
                }
            }
            return newest;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String reasonName(int reason) {
        switch (reason) {
            case ApplicationExitInfo.REASON_EXIT_SELF:
                return "Normal/self exit";
            case ApplicationExitInfo.REASON_SIGNALED:
                return "Killed by OS signal";
            case ApplicationExitInfo.REASON_LOW_MEMORY:
                return "Killed because of low memory";
            case ApplicationExitInfo.REASON_CRASH:
                return "App crash";
            case ApplicationExitInfo.REASON_CRASH_NATIVE:
                return "Native crash";
            case ApplicationExitInfo.REASON_ANR:
                return "App not responding (ANR)";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
                return "Initialization failure";
            case ApplicationExitInfo.REASON_PERMISSION_CHANGE:
                return "Runtime permission changed";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE:
                return "Excessive resource usage";
            case ApplicationExitInfo.REASON_USER_REQUESTED:
                return "User/system requested stop (for example Force stop or Recents)";
            case ApplicationExitInfo.REASON_USER_STOPPED:
                return "Android user profile stopped";
            case ApplicationExitInfo.REASON_DEPENDENCY_DIED:
                return "Dependency process died";
            case ApplicationExitInfo.REASON_OTHER:
                return "Other system reason";
            default:
                if (Build.VERSION.SDK_INT >= 33
                        && reason == ApplicationExitInfo.REASON_FREEZER) {
                    return "Android app freezer";
                }
                if (Build.VERSION.SDK_INT >= 34
                        && reason == ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE) {
                    return "Package/component state changed";
                }
                if (Build.VERSION.SDK_INT >= 34
                        && reason == ApplicationExitInfo.REASON_PACKAGE_UPDATED) {
                    return "App package updated";
                }
                return "Unknown reason (" + reason + ")";
        }
    }

    private static String formatTime(long value) {
        if (value <= 0L) return "Unknown time";
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                Locale.getDefault()
        ).format(new Date(value));
    }
}
