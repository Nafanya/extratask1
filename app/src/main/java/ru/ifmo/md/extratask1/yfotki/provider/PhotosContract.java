package ru.ifmo.md.extratask1.yfotki.provider;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Created by Nikita Yaschenko on 13.01.15.
 */
public final class PhotosContract {

    public interface PhotoColumns {
        String PHOTO_TITLE = "photo_title";
        String PHOTO_CONTENT_URL = "photo_content_url";
        String PHOTO_WATCH_URL = "photo_watch_url";
        String PHOTO_PREFIX_URL = "photo_prefix_url";
        String PHOTO_TYPE = "photo_type";
    }

    public static final int TYPE_TOP = 0;
    public static final int TYPE_RECENT = 1;
    public static final int TYPE_POD = 2;

    public static final String CONTENT_AUTHORITY = "ru.ifmo.md.extratask1.yfotki";

    public static final Uri BASE_CONTENT_URI = Uri.parse("content://" + CONTENT_AUTHORITY);

    private static final String PATH_PHOTOS = "photos";

    public static class Photo implements PhotoColumns, BaseColumns {

        public static final Uri CONTENT_URI =
                BASE_CONTENT_URI.buildUpon().appendPath(PATH_PHOTOS).build();

        public static final String[] ALL_COLUMNS = {
                BaseColumns._ID,
                PhotoColumns.PHOTO_TITLE,
                PhotoColumns.PHOTO_CONTENT_URL,
                PhotoColumns.PHOTO_WATCH_URL,
                PhotoColumns.PHOTO_PREFIX_URL,
        };

        public static Uri buildPhotoUri(String photoId) {
            return CONTENT_URI.buildUpon().appendPath(photoId).build();
        }
    }

}
