package com.ryan.autotyper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

/** Lấy tên hiển thị thật của file từ DocumentProvider. */
public final class FileNameResolver {
    private FileNameResolver() {}

    public static String getDisplayName(Context context, Uri uri) {
        if (uri == null) return "untitled.txt";
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) return name.trim();
                }
            }
        } catch (Exception ignored) {
            // Fallback sang URI path bên dưới.
        } finally {
            if (cursor != null) cursor.close();
        }

        String fallback = uri.getLastPathSegment();
        if (fallback != null && fallback.contains(":")) {
            fallback = fallback.substring(fallback.lastIndexOf(':') + 1);
        }
        return fallback == null || fallback.trim().isEmpty() ? "untitled.txt" : fallback;
    }
}
