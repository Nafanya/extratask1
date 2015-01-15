package ru.ifmo.md.extratask1.yfotki;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.ByteArrayOutputStream;
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

    public interface Listener<Handle> {
        void onImageDownloaded(Handle handle, Bitmap bitmap);
    }

    public void setListener(Listener<Handle> listener) {
        mListener = listener;
    }

    public ImageDownloader(Handler responseHandler) {
        super("ImageDownloader");
        mResponseHandler = responseHandler;
    }

    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_DOWNLOAD) {
                    @SuppressWarnings("unchecked")
                    Handle handle = (Handle)msg.obj;
                    Log.i("TAG", "Got a request for url: " + mRequestMap.get(handle));
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

            byte[] bitmapBytes = loadUrlBytes(url);
            final Bitmap bitmap = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);

            mResponseHandler.post(new Runnable() {
                public void run() {
                    if (mRequestMap.get(handle) != url)
                        return;

                    mRequestMap.remove(handle);
                    mListener.onImageDownloaded(handle, bitmap);
                }
            });
        } catch (IOException e) {
            Log.e("TAG", "Error downloading image", e);
        }
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
        mRequestMap.put(handle, url);

        mHandler.obtainMessage(MESSAGE_DOWNLOAD, handle).sendToTarget();
    }

    public void clearQueue() {
        mHandler.removeMessages(MESSAGE_DOWNLOAD);
        mRequestMap.clear();
    }
}
