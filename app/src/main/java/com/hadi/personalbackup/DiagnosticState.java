package com.hadi.personalbackup;

import android.content.Context;
import android.content.SharedPreferences;

public final class DiagnosticState {
    private static final String PREFS = "pbackup_diagnostics";

    private static final String LAST_SERVICE_START = "last_service_start";
    private static final String LAST_SERVICE_STOP = "last_service_stop";
    private static final String LAST_SERVICE_START_TRIGGER = "last_service_start_trigger";
    private static final String LAST_SERVICE_START_ERROR = "last_service_start_error";
    private static final String LAST_HEARTBEAT = "last_heartbeat";
    private static final String LAST_TASK_REMOVED = "last_task_removed";
    private static final String LAST_BOOT_EVENT = "last_boot_event";

    private static final String LAST_SMS_BROADCAST = "last_sms_broadcast";
    private static final String LAST_SMS_PARSED = "last_sms_parsed";
    private static final String LAST_SMS_RECEIVER_STAGE = "last_sms_receiver_stage";
    private static final String LAST_SMS_RECEIVER_ERROR = "last_sms_receiver_error";
    private static final String LAST_SMS_DB_RESULT = "last_sms_db_result";

    private static final String LAST_MESSAGE_SAVED = "last_message_saved";
    private static final String LAST_CALL_SAVED = "last_call_saved";
    private static final String LAST_SMS_SYNC = "last_sms_sync";
    private static final String LAST_CALL_SYNC = "last_call_sync";
    private static final String LAST_FILE_WRITE = "last_file_write";
    private static final String LAST_FILE_WRITE_OK = "last_file_write_ok";

    private DiagnosticState() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void markServiceStart(Context context) {
        prefs(context).edit()
                .putLong(LAST_SERVICE_START, System.currentTimeMillis())
                .apply();
    }

    public static void markServiceStop(Context context) {
        prefs(context).edit()
                .putLong(LAST_SERVICE_STOP, System.currentTimeMillis())
                .apply();
    }

    public static void markServiceStartTrigger(Context context, String trigger) {
        prefs(context).edit()
                .putString(LAST_SERVICE_START_TRIGGER, safe(trigger))
                .putString(LAST_SERVICE_START_ERROR, "")
                .apply();
    }

    public static void markServiceStartError(Context context, Throwable error) {
        prefs(context).edit()
                .putString(LAST_SERVICE_START_ERROR, errorText(error))
                .apply();
    }

    public static void markHeartbeat(Context context) {
        prefs(context).edit()
                .putLong(LAST_HEARTBEAT, System.currentTimeMillis())
                .apply();
    }

    public static void markTaskRemoved(Context context) {
        prefs(context).edit()
                .putLong(LAST_TASK_REMOVED, System.currentTimeMillis())
                .apply();
    }

    public static void markBootEvent(Context context) {
        prefs(context).edit()
                .putLong(LAST_BOOT_EVENT, System.currentTimeMillis())
                .apply();
    }

    public static void markSmsBroadcast(Context context) {
        prefs(context).edit()
                .putLong(LAST_SMS_BROADCAST, System.currentTimeMillis())
                .putString(LAST_SMS_RECEIVER_STAGE, "Broadcast received")
                .putString(LAST_SMS_RECEIVER_ERROR, "")
                .apply();
    }

    public static void markSmsParsed(Context context, int partCount) {
        prefs(context).edit()
                .putLong(LAST_SMS_PARSED, System.currentTimeMillis())
                .putString(LAST_SMS_RECEIVER_STAGE, "Parsed " + partCount + " SMS part(s)")
                .apply();
    }

    public static void markSmsReceiverStage(Context context, String stage) {
        prefs(context).edit()
                .putString(LAST_SMS_RECEIVER_STAGE, safe(stage))
                .apply();
    }

    public static void markSmsReceiverError(Context context, Throwable error) {
        prefs(context).edit()
                .putString(LAST_SMS_RECEIVER_STAGE, "ERROR")
                .putString(LAST_SMS_RECEIVER_ERROR, errorText(error))
                .apply();
    }

    public static void markSmsDbResult(Context context, String result) {
        prefs(context).edit()
                .putString(LAST_SMS_DB_RESULT, safe(result))
                .apply();
    }

    public static void markMessageSaved(Context context) {
        prefs(context).edit()
                .putLong(LAST_MESSAGE_SAVED, System.currentTimeMillis())
                .apply();
    }

    public static void markCallSaved(Context context) {
        prefs(context).edit()
                .putLong(LAST_CALL_SAVED, System.currentTimeMillis())
                .apply();
    }

    public static void markSmsSync(Context context) {
        prefs(context).edit()
                .putLong(LAST_SMS_SYNC, System.currentTimeMillis())
                .apply();
    }

    public static void markCallSync(Context context) {
        prefs(context).edit()
                .putLong(LAST_CALL_SYNC, System.currentTimeMillis())
                .apply();
    }

    public static void markFileWrite(Context context, boolean ok) {
        prefs(context).edit()
                .putLong(LAST_FILE_WRITE, System.currentTimeMillis())
                .putBoolean(LAST_FILE_WRITE_OK, ok)
                .apply();
    }

    public static long getLastServiceStart(Context context) {
        return prefs(context).getLong(LAST_SERVICE_START, 0L);
    }

    public static long getLastServiceStop(Context context) {
        return prefs(context).getLong(LAST_SERVICE_STOP, 0L);
    }

    public static String getLastServiceStartTrigger(Context context) {
        return prefs(context).getString(LAST_SERVICE_START_TRIGGER, "Never");
    }

    public static String getLastServiceStartError(Context context) {
        return prefs(context).getString(LAST_SERVICE_START_ERROR, "");
    }

    public static long getLastHeartbeat(Context context) {
        return prefs(context).getLong(LAST_HEARTBEAT, 0L);
    }

    public static long getLastTaskRemoved(Context context) {
        return prefs(context).getLong(LAST_TASK_REMOVED, 0L);
    }

    public static long getLastBootEvent(Context context) {
        return prefs(context).getLong(LAST_BOOT_EVENT, 0L);
    }

    public static long getLastSmsBroadcast(Context context) {
        return prefs(context).getLong(LAST_SMS_BROADCAST, 0L);
    }

    public static long getLastSmsParsed(Context context) {
        return prefs(context).getLong(LAST_SMS_PARSED, 0L);
    }

    public static String getLastSmsReceiverStage(Context context) {
        return prefs(context).getString(LAST_SMS_RECEIVER_STAGE, "Never");
    }

    public static String getLastSmsReceiverError(Context context) {
        return prefs(context).getString(LAST_SMS_RECEIVER_ERROR, "");
    }

    public static String getLastSmsDbResult(Context context) {
        return prefs(context).getString(LAST_SMS_DB_RESULT, "Never");
    }

    public static long getLastMessageSaved(Context context) {
        return prefs(context).getLong(LAST_MESSAGE_SAVED, 0L);
    }

    public static long getLastCallSaved(Context context) {
        return prefs(context).getLong(LAST_CALL_SAVED, 0L);
    }

    public static long getLastSmsSync(Context context) {
        return prefs(context).getLong(LAST_SMS_SYNC, 0L);
    }

    public static long getLastCallSync(Context context) {
        return prefs(context).getLong(LAST_CALL_SYNC, 0L);
    }

    public static long getLastFileWrite(Context context) {
        return prefs(context).getLong(LAST_FILE_WRITE, 0L);
    }

    public static boolean wasLastFileWriteOk(Context context) {
        return prefs(context).getBoolean(LAST_FILE_WRITE_OK, false);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String errorText(Throwable error) {
        if (error == null) return "Unknown error";
        String name = error.getClass().getSimpleName();
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? name
                : name + ": " + message;
    }
}
