package ru.ifmo.md.extratask1.yfotki;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.sax.Element;
import android.sax.ElementListener;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import ru.ifmo.md.extratask1.yfotki.provider.PhotosContract;


public class ImageLoaderService extends IntentService {
    private static final String ACTION_LOAD = "ru.ifmo.md.extratask1.yfotki.action.LOAD";

    private static final String EXTRA_PARAM_SECTION_TYPE = "ru.ifmo.md.extratask1.yfotki.extra.SECTION_TYPE";

    public static final int SECTION_TOP = 0;
    public static final int SECTION_RECENT = 1;
    public static final int SECTION_POD = 2;

    private BroadcastNotifier mNotifier = new BroadcastNotifier(this);

    public static void startActionLoad(Context context, int type) {
        Intent intent = new Intent(context, ImageLoaderService.class);
        intent.setAction(ACTION_LOAD);
        intent.putExtra(EXTRA_PARAM_SECTION_TYPE, type);
        context.startService(intent);
    }

    public ImageLoaderService() {
        super("ImageLoaderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_LOAD.equals(action)) {
                final int type = intent.getIntExtra(EXTRA_PARAM_SECTION_TYPE, 0);
                mNotifier.broadcastIntentWithState(Constants.STATE_ACTION_STARTED, type);
                handleActionLoad(type);
            }
        }
    }

    private void handleActionLoad(int type) {
        try {
            loadPhotos(type);
        } catch (IOException | SAXException e) {
            mNotifier.broadcastIntentWithState(Constants.STATE_ACTION_ERROR, type);
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    private void loadPhotos(int type) throws IOException, SAXException {

        final URL url;
        try {
            url = new URL(buildSectionUrl(type));
        } catch (MalformedURLException e) {
            throw e;
        }

        if (!isOnline()) {
            mNotifier.broadcastIntentWithState(Constants.STATE_ACTION_NO_INTERNET, type);
            return;
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();
        InputStream is = connection.getInputStream();
        InputStreamReader isr = new InputStreamReader(is);
        ArrayList<PhotoItem> items = PhotoParser.parse(isr);

        for (PhotoItem item : items) {
            Cursor cursor = getContentResolver().query(
                    PhotosContract.Photo.CONTENT_URI,
                    new String[]{PhotosContract.PhotoColumns.PHOTO_CONTENT_URL},
                    PhotosContract.PhotoColumns.PHOTO_CONTENT_URL + " = ?",
                    new String[]{item.getContentUrl()},
                    null
            );
            if (cursor.getCount() > 0) {
                cursor.close();
                continue;
            }

            ContentValues values = new ContentValues();
            values.put(PhotosContract.PhotoColumns.PHOTO_TITLE, item.getTitle());
            values.put(PhotosContract.PhotoColumns.PHOTO_TYPE, type);
            values.put(PhotosContract.PhotoColumns.PHOTO_WATCH_URL, item.getWatchUrl());
            values.put(PhotosContract.PhotoColumns.PHOTO_CONTENT_URL, item.getContentUrl());
            values.put(PhotosContract.PhotoColumns.PHOTO_PREFIX_URL, item.getPrefixUrl());
            Uri insertedUri = getContentResolver().insert(PhotosContract.Photo.CONTENT_URI, values);
        }
        mNotifier.broadcastIntentWithState(Constants.STATE_ACTION_COMPLETE, type);
    }

    private String buildSectionUrl(int type) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("http")
                .authority("api-fotki.yandex.ru")
                .appendPath("api");
        switch (type) {
            case SECTION_TOP:
                builder.appendPath("top");
                break;
            case SECTION_RECENT:
                builder.appendPath("recent");
                break;
            case SECTION_POD:
                builder.appendPath("podhistory");
                break;
        }
        return builder.build().toString() + "/";
    }

    private static class PhotoParser {
        private static final String ATOM_NAMESPACE = "http://www.w3.org/2005/Atom";

        private static final RootElement root = new RootElement(ATOM_NAMESPACE, "feed");
        private static final Element entry = root.getChild(ATOM_NAMESPACE, "entry");
        private static final Element title = entry.getChild(ATOM_NAMESPACE, "title");
        private static final Element link = entry.getChild(ATOM_NAMESPACE, "link");
        private static final Element content = entry.getChild(ATOM_NAMESPACE, "content");

        private static PhotoItem item;
        private static ArrayList<PhotoItem> items;

        static {
            entry.setStartElementListener(new StartElementListener() {
                @Override
                public void start(Attributes attributes) {
                    item = new PhotoItem();
                }
            });

            entry.setEndElementListener(new EndElementListener() {
                @Override
                public void end() {
                    items.add(item);
                }
            });

            title.setEndTextElementListener(new EndTextElementListener() {
                @Override
                public void end(String s) {
                    item.setTitle(s);
                }
            });

            link.setElementListener(new ElementListener() {
                @Override
                public void end() {
                }

                @Override
                public void start(Attributes attributes) {
                    final String url = attributes.getValue("href");
                    final String rel = attributes.getValue("rel");
                    if (url != null && "alternate".equals(rel)) {
                        item.setWatchUrl(url);
                    }
                }
            });

            content.setElementListener(new ElementListener() {
                @Override
                public void end() {
                }

                @Override
                public void start(Attributes attributes) {
                    String url = attributes.getValue("src");
                    item.setContentUrl(url);
                    int underscoreIndex = url.lastIndexOf('_');
                    String prefixUrl = url.substring(0, underscoreIndex + 1);
                    item.setPrefixUrl(prefixUrl);
                }
            });
        }

        public static ArrayList<PhotoItem> parse(InputStreamReader isr) throws IOException, SAXException {
            items = new ArrayList<>();
            Xml.parse(isr, root.getContentHandler());
            return items;
        }

    }
}
