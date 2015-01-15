package ru.ifmo.md.extratask1.yfotki;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikita Yaschenko on 14.01.15.
 */
public class ImageDownloader<Handle> extends HandlerThread {

    private static final int MESSAGE_DOWNLOAD = 1;

    private Handler mHandler;
    private Map<Handle, String> mRequestMap = Collections.synchronizedMap(new HashMap<Handle, String>());
    private Handler mResponseHandler;
    private Listener<Handle> mListener;
    private Context mContext;

    public interface Listener<Handle> {
        void onImageDownloaded(Handle handle, String url, Bitmap bitmap);
    }

    public void setListener(Listener<Handle> listener) {
        mListener = listener;
    }

    public ImageDownloader(Handler responseHandler, Context context) {
        super("ImageDownloader");
        mResponseHandler = responseHandler;
        mContext = context;
    }

    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    @SuppressWarnings("unchecked")
                    Handle handle = (Handle)msg.obj;
                    handleRequest(handle);
                }
            }
        };
    }

    private void handleRequest(final Handle handle) {
        try {
            final String url = mRequestMap.get(handle);
            if (url == null)
                return;

            Bitmap tempBitmap = loadImageFromStorage(url);
            if (tempBitmap == null) {
                Log.d("TAG", "Loading image at " + url);
                byte[] bitmapBytes = loadUrlBytes(url);
                tempBitmap = BitmapFactory.decodeByteArray(
                        bitmapBytes, 0, bitmapBytes.length);
                saveImageToFile(tempBitmap, url);
            } else {
                Log.d("TAG", "Image from disk cache");
            }

            final Bitmap bitmap = tempBitmap;

            mResponseHandler.post(new Runnable() {
                public void run() {
                    if (mRequestMap.get(handle) != url)
                        return;

                    mRequestMap.remove(handle);
                    if (mListener != null) {
                        mListener.onImageDownloaded(handle, url, bitmap);
                    }
                }
            });
        } catch (IOException e) {
            Log.e("TAG", "Error downloading image", e);
        }
    }

    private Bitmap loadImageFromStorage(String url)  {
        ContextWrapper cw = new ContextWrapper(mContext.getApplicationContext());
        final String filename = getFileName(url);
        File path = new File(cw.getCacheDir(), filename);

        if (!path.exists() || path.isDirectory()) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        return BitmapFactory.decodeFile(path.getAbsolutePath(), options);
    }

    private void saveImageToFile(Bitmap bitmap, String url) {
        final String fileName = getFileName(url);
        ContextWrapper cw = new ContextWrapper(mContext.getApplicationContext());
        File directory = cw.getCacheDir();
        File path = new File(directory, fileName);

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(path);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String getFileName(final String url) {
        int lastSlash = url.lastIndexOf('/');
        final String name = url.substring(lastSlash + 1);
        return name;
    }

    private byte[] loadUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }

            int bytesRead = 0;
            byte[] buffer = new byte[4 * 1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    public void queueImage(Handle handle, String url) {
        if (mRequestMap.containsKey(handle)) {
            if (mRequestMap.get(handle).equals(url)) {
                return;
            }
        }
        mRequestMap.put(handle, url);

        mHandler.obtainMessage(MESSAGE_DOWNLOAD, handle).sendToTarget();
    }

    public void clearQueue() {
        mHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }
}
