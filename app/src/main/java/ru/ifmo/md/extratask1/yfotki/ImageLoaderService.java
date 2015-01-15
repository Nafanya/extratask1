package ru.ifmo.md.extratask1.yfotki;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
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
    private static final String ACTION_LOAD_TOP = "ru.ifmo.md.extratask1.yfotki.action.LOAD_TOP";
    private static final String ACTION_LOAD_RECENT = "ru.ifmo.md.extratask1.yfotki.action.LOAD_RECENT";
    private static final String ACTION_LOAD_POD = "ru.ifmo.md.extratask1.yfotki.action.LOAD_POD";

    private static final String EXTRA_PARAM1 = "ru.ifmo.md.extratask1.yfotki.extra.PARAM1";
    private static final String EXTRA_PARAM2 = "ru.ifmo.md.extratask1.yfotki.extra.PARAM2";
    public enum SectionType {
        TOP,
        RECENT,
        POD
    }

    public static void startActionLoadTop(Context context) {
        Intent intent = new Intent(context, ImageLoaderService.class);
        intent.setAction(ACTION_LOAD_TOP);
        context.startService(intent);
    }

    public ImageLoaderService() {
        super("ImageLoaderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_LOAD_TOP.equals(action)) {
                handleActionLoadTop();
            }
        }
    }

    private void handleActionLoadTop() {
        try {
            loadPhotos(SectionType.TOP);
            // TODO: proper handling
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
    }

    private void loadPhotos(SectionType type) throws IOException, SAXException {

        final URL url;
        try {
            url = new URL(buildSectionUrl(type));
        } catch (MalformedURLException e) {
            e.printStackTrace();
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
            values.put(PhotosContract.PhotoColumns.PHOTO_TYPE, getType(type));
            values.put(PhotosContract.PhotoColumns.PHOTO_WATCH_URL, item.getWatchUrl());
            values.put(PhotosContract.PhotoColumns.PHOTO_CONTENT_URL, item.getContentUrl());
            Uri insertedUri = getContentResolver().insert(PhotosContract.Photo.CONTENT_URI, values);
        }
    }

    private int getType(SectionType type) {
        switch (type) {
            case TOP: return 0;
            case RECENT: return 1;
            case POD: return 2;
            default:
                throw new IllegalArgumentException("Unknown type");
        }
    }

    private String buildSectionUrl(SectionType type) {
        Uri.Builder builder = new Uri.Builder();
        builder.scheme("http")
                .authority("api-fotki.yandex.ru")
                .appendPath("api");
        switch (type) {
            case TOP:
                builder.appendPath("top");
                break;
            case RECENT:
                builder.appendPath("recent");
                break;
            case POD:
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
                    int underscoreIndex = url.lastIndexOf('_');
                    url = url.substring(0, underscoreIndex + 1);
                    item.setContentUrl(url);
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