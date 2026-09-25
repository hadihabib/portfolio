package com.hadi.personalbackup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class AutoTxtBackup {
    private static final String PREFS = "pbackup_storage";
    private static final String KEY_TREE_URI = "tree_uri";

    // Hidden / unobtrusive names chosen by the device owner.
    private static final String HIDDEN_FOLDER = ".pbackup";
    private static final String MESSAGES_FILE = ".mstore.dat";
    private static final String CALLS_FILE = ".cstore.dat";

    private AutoTxtBackup() {}

    public static boolean hasBackupLocation(Context context) {
        return getTreeUri(context) != null;
    }

    public static String locationLabel(Context context) {
        Uri tree = getTreeUri(context);
        if (tree == null) return "Not selected";

        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            if (id != null && !id.isEmpty()) {
                int colon = id.indexOf(':');
                String value = colon >= 0 ? id.substring(colon + 1) : id;
                return value.isEmpty() ? "Selected folder" : value;
            }
        } catch (Exception ignored) {}

        return "Selected folder";
    }

    public static synchronized boolean setBackupLocation(
            Context context,
            Uri treeUri,
            String fullSmsText,
            String fullCallsText
    ) {
        if (treeUri == null) return false;

        try {
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (Exception ignored) {
            // Some providers grant access without a persistable call.
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).apply();

        try {
            Uri dir = getOrCreateHiddenDirectory(context);
            if (dir == null) throw new IllegalStateException("Could not create hidden backup folder");

            writeFull(context, getOrCreateFile(context, dir, MESSAGES_FILE), fullSmsText);
            writeFull(context, getOrCreateFile(context, dir, CALLS_FILE), fullCallsText);
            DiagnosticState.markFileWrite(context, true);
            return true;
        } catch (Exception e) {
            DiagnosticState.markFileWrite(context, false);
            return false;
        }
    }

    public static synchronized void ensureFiles(Context context, String fullSmsText, String fullCallsText) {
        if (!hasBackupLocation(context)) return;

        try {
            Uri dir = getOrCreateHiddenDirectory(context);
            if (dir == null) return;

            Uri messages = findChild(context, dir, MESSAGES_FILE);
            if (messages == null) {
                messages = getOrCreateFile(context, dir, MESSAGES_FILE);
                writeFull(context, messages, fullSmsText);
            }

            Uri calls = findChild(context, dir, CALLS_FILE);
            if (calls == null) {
                calls = getOrCreateFile(context, dir, CALLS_FILE);
                writeFull(context, calls, fullCallsText);
            }
            DiagnosticState.markFileWrite(context, true);
        } catch (Exception ignored) {
            DiagnosticState.markFileWrite(context, false);
        }
    }

    public static synchronized void appendMessage(Context context, String entry, String fullFallbackText) {
        appendOrRecreate(context, MESSAGES_FILE, entry, fullFallbackText);
    }

    public static synchronized void appendCall(Context context, String entry, String fullFallbackText) {
        appendOrRecreate(context, CALLS_FILE, entry, fullFallbackText);
    }

    private static void appendOrRecreate(Context context, String fileName, String entry, String fullFallbackText) {
        if (!hasBackupLocation(context)) return;

        try {
            Uri dir = getOrCreateHiddenDirectory(context);
            if (dir == null) {
                DiagnosticState.markFileWrite(context, false);
                return;
            }

            Uri file = findChild(context, dir, fileName);
            if (file == null) {
                file = getOrCreateFile(context, dir, fileName);
                writeFull(context, file, fullFallbackText);
                DiagnosticState.markFileWrite(context, true);
                return;
            }

            try {
                append(context, file, entry);
            } catch (Exception appendFailed) {
                // Keep the SAME document. If a provider does not support append mode,
                // rewrite the existing document URI from the local database snapshot.
                writeFull(context, file, fullFallbackText);
            }

            DiagnosticState.markFileWrite(context, true);
        } catch (Exception ignored) {
            DiagnosticState.markFileWrite(context, false);
        }
    }

    private static Uri getTreeUri(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);

        if (value == null || value.trim().isEmpty()) return null;

        try {
            return Uri.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static Uri getOrCreateHiddenDirectory(Context context) throws Exception {
        Uri tree = getTreeUri(context);
        if (tree == null) return null;

        String rootId = DocumentsContract.getTreeDocumentId(tree);
        Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(tree, rootId);

        Uri existing = findChild(context, rootDocument, HIDDEN_FOLDER);
        if (existing != null) return existing;

        Uri created = DocumentsContract.createDocument(
                context.getContentResolver(),
                rootDocument,
                DocumentsContract.Document.MIME_TYPE_DIR,
                HIDDEN_FOLDER
        );

        if (created == null) {
            throw new IllegalStateException("Hidden directory could not be created");
        }

        return created;
    }

    private static Uri getOrCreateFile(Context context, Uri parent, String name) throws Exception {
        Uri existing = findChild(context, parent, name);
        if (existing != null) return existing;

        Uri created = DocumentsContract.createDocument(
                context.getContentResolver(),
                parent,
                "application/octet-stream",
                name
        );

        if (created == null) {
            throw new IllegalStateException("Backup file could not be created");
        }

        return created;
    }

    private static Uri findChild(Context context, Uri parentDocument, String displayName) {
        ContentResolver resolver = context.getContentResolver();

        try {
            String parentId = DocumentsContract.getDocumentId(parentDocument);
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(parentDocument, parentId);

            String[] projection = new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
            };

            try (Cursor cursor = resolver.query(children, projection, null, null, null)) {
                if (cursor == null) return null;

                int idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameCol);
                    if (displayName.equals(name)) {
                        String documentId = cursor.getString(idCol);
                        return DocumentsContract.buildDocumentUriUsingTree(parentDocument, documentId);
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    private static void writeFull(Context context, Uri fileUri, String text) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(fileUri, "wt")) {
            if (out == null) throw new IllegalStateException("Could not open backup file");
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static void append(Context context, Uri fileUri, String text) throws Exception {
        try (OutputStream out = context.getContentResolver().openOutputStream(fileUri, "wa")) {
            if (out == null) throw new IllegalStateException("Could not append backup file");
            out.write((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
