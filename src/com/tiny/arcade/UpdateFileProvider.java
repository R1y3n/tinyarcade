package com.tiny.arcade;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Tiny read-only provider for the downloaded update APK. Using a content URI keeps
 * the installer from rejecting file:// paths on Android 7+.
 */
public final class UpdateFileProvider extends ContentProvider {
    public static final String AUTHORITY_SUFFIX = ".updates";

    public static Uri uriFor(Context context, File file) {
        return new Uri.Builder()
            .scheme("content")
            .authority(context.getPackageName() + AUTHORITY_SUFFIX)
            .appendPath(file.getName())
            .build();
    }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri.getPathSegments().size() != 1) throw new FileNotFoundException("Unknown update path");
        File root = getContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if (root == null) root = new File(getContext().getFilesDir(), "updates");
        File file = new File(root, uri.getPathSegments().get(0));
        try {
            if (!file.getCanonicalPath().equals(new File(root, file.getName()).getCanonicalPath())) {
                throw new FileNotFoundException("Invalid update path");
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("Invalid update path");
        }
        return file;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only provider");
        File file = resolve(uri);
        if (!file.isFile()) throw new FileNotFoundException("Update not found");
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            MatrixCursor cursor = new MatrixCursor(
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName(), file.length()});
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
