# PBackup v5.1 — Diagnostic build

This is the v5 code line with deeper diagnostics added to find out why a message was missed.

## What it records

- Current background-monitor state.
- A service heartbeat every 15 seconds.
- Last service start time and what triggered it.
- Last `onDestroy()` time.
- Last time the app was removed from Recents while the service was alive.
- Last boot/update event seen by PBackup.
- Android process-exit reason on Android 11+ using `ApplicationExitInfo`.
- Last `SMS_RECEIVED` broadcast.
- Last time SMS parts were parsed.
- Receiver pipeline stage.
- Receiver exception, if any.
- Database insert result.
- Last message saved.
- Last SMS provider sync.
- Last call saved / call-log sync.
- Last external hidden-file write and whether it succeeded.

## Why this is useful

After a missed SMS, open PBackup and look at **Likely cause**.

Examples:

- **Last SMS broadcast did not change**:
  Android never delivered the SMS event to PBackup. Check permissions, Force stop, OEM/background restrictions, or system protection.

- **Last SMS broadcast changed but Last message saved did not**:
  The receiver ran but the save pipeline failed. The receiver stage/error and DB result should show where.

- **Last message saved changed but file write is FAILED**:
  The message is still inside PBackup's internal SQLite database; only the hidden external file write failed.

- **Heartbeat stopped**:
  The background monitor was no longer alive. On Android 11+, **Last process exit** may identify crash, low memory, user-requested stop, signal kill, app freezer, package update, etc.

Android/OEM firmware does not always provide an exact reason for every process kill, so the app may sometimes report the most likely cause instead of a guaranteed cause.

## Test

1. Install/update PBackup v5.1.
2. Open it once and verify permissions.
3. Verify **Backup monitor healthy**.
4. Lock the phone.
5. Receive an SMS.
6. Delete it immediately from the normal Messages app.
7. Reopen PBackup.
8. If the SMS is missing, press **Copy diagnostics** and send the copied text or a screenshot.

## Build

Upload this project over the existing GitHub repository and use:

**Actions → Build Android APK → Run workflow**

Download the `PBackup-APK` artifact.
