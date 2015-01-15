package ru.ifmo.md.extratask1.yfotki.provider;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Created by Nikita Yaschenko on 13.01.15.
 */
public class PhotosDatabase extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "photos.db";

    private static int VERSION = 1;

    interface Tables {
        String PHOTOS = "photos";
    }

    public PhotosDatabase(Context context) {
        super(context, DATABASE_NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + Tables.PHOTOS + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + PhotosContract.PhotoColumns.PHOTO_TITLE + " TEXT NOT NULL, "
                + PhotosContract.PhotoColumns.PHOTO_TYPE + " INT NOT NULL,"
                + PhotosContract.PhotoColumns.PHOTO_CONTENT_URL + " TEXT, "
                + PhotosContract.PhotoColumns.PHOTO_WATCH_URL + " TEXT);"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int i, int i2) {

    }
}
