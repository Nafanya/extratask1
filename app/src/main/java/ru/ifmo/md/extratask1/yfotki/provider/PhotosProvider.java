package ru.ifmo.md.extratask1.yfotki.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

public class PhotosProvider extends ContentProvider {

    private PhotosDatabase mOpenHelper;

    private static UriMatcher sUriMatcher = buildMatcher();

    private static final int PHOTOS = 100;
    private static final int PHOTOS_ID = 101;

    private static UriMatcher buildMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        final String authority = PhotosContract.CONTENT_AUTHORITY;

        matcher.addURI(authority, "photos", PHOTOS);
        matcher.addURI(authority, "photos/#", PHOTOS_ID);

        return matcher;
    }

    public PhotosProvider() {
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Implement this to handle requests to delete one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public String getType(Uri uri) {
        // TODO: Implement this to handle requests for the MIME type of the data
        // at the given URI.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final int match = sUriMatcher.match(uri);
        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        final long id;

        switch (match) {
            case PHOTOS:
                id = db.insert(PhotosDatabase.Tables.PHOTOS, null, values);
                notifyChange(uri);
                return PhotosContract.Photo.buildPhotoUri(Long.toString(id));
            default:
                throw new IllegalArgumentException("No such uri match: " + match);
        }
    }

    @Override
    public boolean onCreate() {
        mOpenHelper = new PhotosDatabase(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        final int match = sUriMatcher.match(uri);
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        switch (match) {
            case PHOTOS:
                builder.setTables(PhotosDatabase.Tables.PHOTOS);
                break;
            case PHOTOS_ID:
                builder.setTables(PhotosDatabase.Tables.PHOTOS);
                builder.appendWhere(PhotosContract.Photo._ID + " = " + uri.getLastPathSegment());
                break;
            default:
                throw new IllegalArgumentException("No such uri match: " + match);
        }
        return builder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        // TODO: Implement this to handle requests to update one or more rows.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void notifyChange(Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null);
    }
}
