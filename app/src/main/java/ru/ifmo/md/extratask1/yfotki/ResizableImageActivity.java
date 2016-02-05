package ru.ifmo.md.extratask1.yfotki;

import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;


public class ResizableImageActivity extends AppCompatActivity implements
        View.OnTouchListener,
        ImageDownloader.Listener<ImageView> {

    public static final String EXTRA_CONTENT_URL = "extra_content_url";
    public static final String EXTRA_PREFIX_URL = "extra_prefix_url";
    public static final String EXTRA_WATCH_URL = "extra_watch_url";
    public static final String EXTRA_TITLE = "extra_title";

    private Matrix mMatrix;
    private Matrix mSavedMatrix;

    private PointF start = new PointF();
    private PointF mid = new PointF();
    private float oldDist = 1f;

    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private int mode = NONE;

    private ImageView mImageView;
    private ProgressBar mProgressBar;

    private ImageDownloader<ImageView> mImageDownloader;
    private String mContentUrl;
    private String mWatchUrl;
    private String mPrefixUrl;
    private String mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resizable_image);

        mMatrix = new Matrix();
        mSavedMatrix = new Matrix();
        mProgressBar = (ProgressBar) findViewById(R.id.progress);
        mProgressBar.setIndeterminate(true);

        Intent intent = getIntent();
        mContentUrl = intent.getStringExtra(EXTRA_CONTENT_URL);
        mWatchUrl = intent.getStringExtra(EXTRA_WATCH_URL);
        mPrefixUrl = intent.getStringExtra(EXTRA_PREFIX_URL);
        mTitle = intent.getStringExtra(EXTRA_TITLE);
        if (mTitle == null) {
            mTitle = "Image";
        }
        setTitle(mTitle);

        if (mContentUrl == null || mWatchUrl == null) {
            finish();
        }

        mImageView = (ImageView) findViewById(R.id.imageView);
        mImageView.setOnTouchListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageDownloader.clearQueue();
        mImageDownloader.quit();
        mImageDownloader.setListener(null);
        mImageDownloader = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        installDownloader();
    }

    private void installDownloader() {
        mImageDownloader = new ImageDownloader<>(new Handler(), getApplicationContext());
        mImageDownloader.setListener(this);
        mImageDownloader.start();
        mImageDownloader.getLooper();
        mImageDownloader.queueImage(mImageView, mPrefixUrl + "XL");
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_resizable_image, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_download_orig) {
            downloadOriginal(Uri.parse(mContentUrl));
            return true;
        } else if (id == R.id.action_watch) {
            openInBrowser(mWatchUrl);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void downloadOriginal(Uri uri) {
        DownloadManager.Request r = new DownloadManager.Request(uri);
        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, mTitle + ".jpg");
        r.allowScanningByMediaScanner();
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        dm.enqueue(r);
    }

    private void openInBrowser(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(mWatchUrl));
        startActivity(intent);
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        ImageView imageView = (ImageView) view;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        float scale;

        switch (motionEvent.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mMatrix.set(imageView.getImageMatrix());
                mSavedMatrix.set(mMatrix);
                start.set(motionEvent.getX(), motionEvent.getY());
                mode = DRAG;
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                break;
            case MotionEvent.ACTION_POINTER_DOWN: // first and second finger down
                oldDist = spacing(motionEvent);
                if (oldDist > 5f) {
                    mSavedMatrix.set(mMatrix);
                    midPoint(mid, motionEvent);
                    mode = ZOOM;
                }
                break;

            case MotionEvent.ACTION_MOVE:

                if (mode == DRAG) {
                    mMatrix.set(mSavedMatrix);
                    mMatrix.postTranslate(motionEvent.getX() - start.x, motionEvent.getY() - start.y);
                } else if (mode == ZOOM) {
                    float newDist = spacing(motionEvent);
                    if (newDist > 5f) {
                        mMatrix.set(mSavedMatrix);
                        scale = newDist / oldDist;
                        mMatrix.postScale(scale, scale, mid.x, mid.y);
                    }
                }
                break;
        }

        imageView.setImageMatrix(mMatrix);

        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    @Override
    public void onImageDownloaded(ImageView imageView, String url, Bitmap bitmap) {
        mProgressBar.setVisibility(View.GONE);
        imageView.setImageBitmap(bitmap);
    }
}
