package com.hadi.personalbackup;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.CallLog;
import android.provider.Telephony;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "personal_backup.db";
    private static final int DB_VERSION = 1;
    private final Context context;

    public BackupDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
            "CREATE TABLE sms_backup (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "dedupe_key TEXT NOT NULL UNIQUE," +
            "phone TEXT," +
            "body TEXT," +
            "direction TEXT NOT NULL," +
            "event_time INTEGER NOT NULL," +
            "saved_time INTEGER NOT NULL)"
        );

        db.execSQL(
            "CREATE TABLE call_backup (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "dedupe_key TEXT NOT NULL UNIQUE," +
            "phone TEXT," +
            "call_type INTEGER NOT NULL," +
            "event_time INTEGER NOT NULL," +
            "duration INTEGER NOT NULL," +
            "saved_time INTEGER NOT NULL)"
        );

        db.execSQL("CREATE INDEX idx_sms_time ON sms_backup(event_time)");
        db.execSQL("CREATE INDEX idx_call_time ON call_backup(event_time)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public synchronized boolean insertSms(String phone, String body, String direction, long eventTime) {
        if (phone == null) phone = "";
        if (body == null) body = "";
        if (direction == null) direction = "UNKNOWN";

        ContentValues cv = new ContentValues();
        cv.put("dedupe_key", stableKey("SMS", phone, body, direction, String.valueOf(eventTime / 1000L)));
        cv.put("phone", phone);
        cv.put("body", body);
        cv.put("direction", direction);
        cv.put("event_time", eventTime);
        cv.put("saved_time", System.currentTimeMillis());

        final long rowId;
        try {
            rowId = getWritableDatabase().insertWithOnConflict(
                    "sms_backup", null, cv, SQLiteDatabase.CONFLICT_IGNORE
            );
        } catch (Throwable error) {
            DiagnosticState.markSmsDbResult(
                    context,
                    "Database error: " + error.getClass().getSimpleName() +
                    (error.getMessage() == null ? "" : " - " + error.getMessage())
            );
            return false;
        }

        if (rowId != -1) {
            DiagnosticState.markMessageSaved(context);
            DiagnosticState.markSmsDbResult(context, "Saved successfully");
            AutoTxtBackup.appendMessage(
                    context,
                    formatSmsEntry(phone, body, direction, eventTime),
                    exportSmsText()
            );
            return true;
        }

        DiagnosticState.markSmsDbResult(context, "Duplicate / already stored");
        return false;
    }

    public synchronized void insertCall(String systemId, String phone, int callType, long eventTime, long duration) {
        if (phone == null) phone = "";

        String key = (systemId != null && !systemId.isEmpty())
            ? "CALL_ID_" + systemId
            : stableKey("CALL", phone, String.valueOf(callType), String.valueOf(eventTime), String.valueOf(duration));

        ContentValues cv = new ContentValues();
        cv.put("dedupe_key", key);
        cv.put("phone", phone);
        cv.put("call_type", callType);
        cv.put("event_time", eventTime);
        cv.put("duration", duration);
        cv.put("saved_time", System.currentTimeMillis());

        long rowId = getWritableDatabase().insertWithOnConflict(
            "call_backup", null, cv, SQLiteDatabase.CONFLICT_IGNORE
        );

        if (rowId != -1) {
            DiagnosticState.markCallSaved(context);
            AutoTxtBackup.appendCall(
                context,
                formatCallEntry(phone, callType, eventTime, duration),
                exportCallsText()
            );
        }
    }

    public void importAllSms() {
        importSmsInternal(Integer.MAX_VALUE);
    }

    public void importRecentSms() {
        importSmsInternal(20);
    }

    private void importSmsInternal(int limit) {
        if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                new String[]{
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                },
                null,
                null,
                Telephony.Sms.DATE + " DESC"
            );

            if (c == null) return;

            int addressIndex = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            int bodyIndex = c.getColumnIndexOrThrow(Telephony.Sms.BODY);
            int dateIndex = c.getColumnIndexOrThrow(Telephony.Sms.DATE);
            int typeIndex = c.getColumnIndexOrThrow(Telephony.Sms.TYPE);

            int read = 0;
            while (c.moveToNext() && read < limit) {
                int type = c.getInt(typeIndex);
                String direction;

                if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                    direction = "IN";
                } else if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                    direction = "OUT";
                } else {
                    direction = "OTHER";
                }

                insertSms(
                    c.getString(addressIndex),
                    c.getString(bodyIndex),
                    direction,
                    c.getLong(dateIndex)
                );
                read++;
            }
        } catch (SecurityException ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    public void importAllCalls() {
        importCallsInternal(Integer.MAX_VALUE);
    }

    public void importRecentCalls() {
        importCallsInternal(20);
    }

    private void importCallsInternal(int limit) {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                },
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            );

            if (c == null) return;

            int idIndex = c.getColumnIndexOrThrow(CallLog.Calls._ID);
            int numberIndex = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
            int typeIndex = c.getColumnIndexOrThrow(CallLog.Calls.TYPE);
            int dateIndex = c.getColumnIndexOrThrow(CallLog.Calls.DATE);
            int durationIndex = c.getColumnIndexOrThrow(CallLog.Calls.DURATION);

            int read = 0;
            while (c.moveToNext() && read < limit) {
                insertCall(
                    c.getString(idIndex),
                    c.getString(numberIndex),
                    c.getInt(typeIndex),
                    c.getLong(dateIndex),
                    c.getLong(durationIndex)
                );
                read++;
            }
        } catch (SecurityException ignored) {
        } finally {
            if (c != null) c.close();
        }
    }

    public int smsCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sms_backup", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int callCount() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM call_backup", null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public List<String> getSmsDisplayRows() {
        List<String> rows = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        Cursor c = getReadableDatabase().rawQuery(
            "SELECT phone, body, direction, event_time FROM sms_backup ORDER BY event_time DESC",
            null
        );

        try {
            while (c.moveToNext()) {
                String phone = c.getString(0) == null ? "" : c.getString(0);
                String body = c.getString(1) == null ? "" : c.getString(1);
                String direction = c.getString(2);
                String directionName = "IN".equals(direction) ? "Incoming" : ("OUT".equals(direction) ? "Outgoing" : "Other");
                String when = df.format(new Date(c.getLong(3)));

                rows.add(
                    directionName + "  •  " + when + "\n" +
                    phone + "\n" +
                    body
                );
            }
        } finally {
            c.close();
        }

        return rows;
    }

    public List<String> getCallDisplayRows() {
        List<String> rows = new ArrayList<>();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        Cursor c = getReadableDatabase().rawQuery(
            "SELECT phone, call_type, event_time, duration FROM call_backup ORDER BY event_time DESC",
            null
        );

        try {
            while (c.moveToNext()) {
                String phone = c.getString(0) == null ? "" : c.getString(0);
                String type = callTypeName(c.getInt(1));
                String when = df.format(new Date(c.getLong(2)));
                long duration = c.getLong(3);

                rows.add(
                    type + "  •  " + when + "\n" +
                    phone + "\n" +
                    "Duration: " + duration + " sec"
                );
            }
        } finally {
            c.close();
        }

        return rows;
    }

    public String exportSmsText() {
        StringBuilder out = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        out.append("PBackup - Messages Log\n");
        out.append("==============================\n\n");

        Cursor sms = getReadableDatabase().rawQuery(
            "SELECT phone, body, direction, event_time FROM sms_backup ORDER BY event_time DESC",
            null
        );

        try {
            while (sms.moveToNext()) {
                String direction = sms.getString(2);
                String directionName = "IN".equals(direction) ? "Incoming" : ("OUT".equals(direction) ? "Outgoing" : "Other");

                out.append("[MESSAGE]\n");
                out.append("Type: ").append(directionName).append("\n");
                out.append("Number: ").append(sms.getString(0) == null ? "" : sms.getString(0)).append("\n");
                out.append("Time: ").append(df.format(new Date(sms.getLong(3)))).append("\n");
                out.append("Text: ").append(sms.getString(1) == null ? "" : sms.getString(1)).append("\n");
                out.append("------------------------------\n\n");
            }
        } finally {
            sms.close();
        }

        return out.toString();
    }

    public String exportCallsText() {
        StringBuilder out = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        out.append("PBackup - Calls Log\n");
        out.append("==============================\n\n");

        Cursor calls = getReadableDatabase().rawQuery(
            "SELECT phone, call_type, event_time, duration FROM call_backup ORDER BY event_time DESC",
            null
        );

        try {
            while (calls.moveToNext()) {
                out.append("[CALL]\n");
                out.append("Type: ").append(callTypeName(calls.getInt(1))).append("\n");
                out.append("Number: ").append(calls.getString(0) == null ? "" : calls.getString(0)).append("\n");
                out.append("Time: ").append(df.format(new Date(calls.getLong(2)))).append("\n");
                out.append("Duration: ").append(calls.getLong(3)).append(" sec\n");
                out.append("------------------------------\n\n");
            }
        } finally {
            calls.close();
        }

        return out.toString();
    }

    private String formatSmsEntry(String phone, String body, String direction, long eventTime) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String directionName = "IN".equals(direction) ? "Incoming" : ("OUT".equals(direction) ? "Outgoing" : "Other");

        return "[MESSAGE]\n" +
            "Type: " + directionName + "\n" +
            "Number: " + (phone == null ? "" : phone) + "\n" +
            "Time: " + df.format(new Date(eventTime)) + "\n" +
            "Text: " + (body == null ? "" : body) + "\n" +
            "------------------------------\n\n";
    }

    private String formatCallEntry(String phone, int type, long eventTime, long duration) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        return "[CALL]\n" +
            "Type: " + callTypeName(type) + "\n" +
            "Number: " + (phone == null ? "" : phone) + "\n" +
            "Time: " + df.format(new Date(eventTime)) + "\n" +
            "Duration: " + duration + " sec\n" +
            "------------------------------\n\n";
    }

    private String callTypeName(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return "Incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "Outgoing";
            case CallLog.Calls.MISSED_TYPE: return "Missed";
            case CallLog.Calls.REJECTED_TYPE: return "Rejected";
            case CallLog.Calls.BLOCKED_TYPE: return "Blocked";
            default: return "Other";
        }
    }

    private static String stableKey(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                if (part != null) {
                    md.update(part.getBytes(StandardCharsets.UTF_8));
                }
                md.update((byte) 0);
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (String part : parts) sb.append(part).append('|');
            return String.valueOf(sb.toString().hashCode());
        }
    }
}
