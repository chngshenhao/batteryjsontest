package com.example.batteryjsontest;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Exposes this app's Device ID (DEV-...) to other apps on the same device
 * (e.g. return_QR) via content://com.example.batteryjsontest.deviceid/device
 */
public class DeviceIdContentProvider extends ContentProvider {

    public static final String AUTHORITY = "com.example.batteryjsontest.deviceid";
    public static final String COLUMN_DEVICE_NUMBER = "device_number";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/device");

    private static final int MATCH_DEVICE = 1;
    private static final UriMatcher URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        URI_MATCHER.addURI(AUTHORITY, "device", MATCH_DEVICE);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder
    ) {
        if (URI_MATCHER.match(uri) != MATCH_DEVICE) {
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (getContext() == null) {
            return null;
        }

        String deviceNumber = DeviceIdUtil.getOrCreateDeviceNumber(getContext());
        MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_DEVICE_NUMBER});
        cursor.addRow(new Object[]{deviceNumber});
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        if (URI_MATCHER.match(uri) == MATCH_DEVICE) {
            return "vnd.android.cursor.item/vnd." + AUTHORITY + ".device";
        }
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public int delete(
            @NonNull Uri uri,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public int update(
            @NonNull Uri uri,
            @Nullable ContentValues values,
            @Nullable String selection,
            @Nullable String[] selectionArgs
    ) {
        throw new UnsupportedOperationException("Update not supported");
    }
}
