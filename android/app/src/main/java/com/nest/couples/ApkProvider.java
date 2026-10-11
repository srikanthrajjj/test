package com.nest.couples;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Serves a copy of this app's own APK (cache/share/Nest.apk) so it can be attached to a WhatsApp invite. */
public class ApkProvider extends ContentProvider {
    private File file() {
        return new File(new File(getContext().getCacheDir(), "share"), "Nest.apk");
    }

    private boolean img(Uri uri) {
        return uri != null && uri.getPath() != null && uri.getPath().endsWith(".png");
    }

    private File fileFor(Uri uri) {
        return img(uri) ? new File(new File(getContext().getCacheDir(), "share"), "nest-month.png") : file();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{img(uri) ? "nest-month.png" : "Nest.apk", fileFor(uri).length()});
        return c;
    }

    @Override
    public String getType(Uri uri) {
        return img(uri) ? "image/png" : "application/vnd.android.package-archive";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
