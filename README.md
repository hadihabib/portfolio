# PBackup v4

PBackup keeps a local copy of SMS messages and call history.

## New in v4

The user chooses the backup location from Android's system folder picker.

Inside the selected folder PBackup creates:

- `.pbackup/.mstore.dat` — messages
- `.pbackup/.cstore.dat` — calls

The folder and files use dot-prefixed names so many Android file managers hide them unless
"Show hidden files" is enabled.

New records are appended to the same two files automatically. PBackup also keeps its internal
SQLite database as a recovery copy.

## First setup

1. Install the APK.
2. Open PBackup.
3. Grant SMS / call-log / notification permissions.
4. Tap **Choose / Change backup location** and select a folder.
5. Tap **Start automatic backup**.
6. On Xiaomi / HyperOS / MIUI, enable Autostart and set battery usage to No restrictions.

## Changing location later

Tap **Choose / Change backup location** again. PBackup writes the complete current backup into
the hidden `.pbackup` folder at the newly selected location and continues there.

## Privacy note

A dot-prefixed folder is only casual hiding, not encryption. A file manager with "Show hidden
files" enabled can reveal it.

## GitHub APK build

Use **Actions → Build Android APK → Run workflow** and download the `PBackup-APK` artifact.
