package ru.ifmo.md.extratask1.yfotki;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;

import ru.ifmo.md.extratask1.yfotki.provider.PhotosContract;


public class SectionFragment extends Fragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        ImageDownloader.Listener<ImageView>,
        SwipeRefreshLayout.OnRefreshListener {
    public static final String ARG_SECTION_NUMBER = "number";

    private static final int LOADER_IMAGES = 1;

    private static final String FRAGMENT_STATE_REFRESHING = "isRefreshing";

    private RecyclerView mRecyclerView;
    private SectionAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    private ImageDownloader<ImageView> mImageDownloader = null;
    private LruCache<String, Bitmap> mCache;

    private int mSection;
    private ArrayList<PhotoItem> mItems;

    private View.OnClickListener mOnClickListener;

    private SwipeRefreshLayout mRefreshLayout;
    private boolean mIsRefreshing;

    private DownloadStateReceiver mDownloadStateReceiver;

    public static SectionFragment newInstance(int section) {
        SectionFragment fragment = new SectionFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, section);
        fragment.setArguments(args);
        return fragment;
    }

    public SectionFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        mSection = args.getInt(ARG_SECTION_NUMBER);

        checkFirstLaunch();
    }

    private void checkFirstLaunch() {
        final String key = Integer.toString(mSection);
        int isFirstTime = readFromPreferences(key);
        if (isFirstTime == -1) {
            ImageLoaderService.startActionLoad(getActivity(), mSection);
        }
    }

    private void saveToPreferences(String key, int value) {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    private int readFromPreferences(String key) {
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        return sharedPref.getInt(key, -1);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(FRAGMENT_STATE_REFRESHING, mIsRefreshing);
    }

    @Override
    public void onPause() {
        super.onPause();
        mImageDownloader.clearQueue();
        mImageDownloader.quit();
        mImageDownloader.setListener(null);
        mImageDownloader = null;

        if (mDownloadStateReceiver != null) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mDownloadStateReceiver);
            mDownloadStateReceiver = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        installDownloader();
    }

    private void installDownloader() {
        mImageDownloader = new ImageDownloader<>(new Handler(), getActivity().getApplicationContext());
        mImageDownloader.setListener(this);
        mImageDownloader.start();
        mImageDownloader.getLooper();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_section, container, false);

        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.recyclerView);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        final int orientation = getResources().getConfiguration().orientation;
        final int columns;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            columns = 3;
        } else {
            columns = 5;
        }

        mLayoutManager = new GridLayoutManager(getActivity(), columns);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new SectionAdapter(new ArrayList<PhotoItem>());
        mRecyclerView.setAdapter(mAdapter);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = maxMemory / 8;
        mCache = new LruCache<String, Bitmap>(cacheSize) {

            @Override
            protected int sizeOf(String url, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }

        };

        mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int itemPosition = mRecyclerView.getChildPosition(view);
                PhotoItem item = mAdapter.mItems.get(itemPosition);
                Intent intent = new Intent(getActivity(), ResizableImageActivity.class);
                intent.putExtra(ResizableImageActivity.EXTRA_CONTENT_URL, item.getContentUrl());
                intent.putExtra(ResizableImageActivity.EXTRA_WATCH_URL, item.getWatchUrl());
                intent.putExtra(ResizableImageActivity.EXTRA_TITLE, item.getTitle());
                intent.putExtra(ResizableImageActivity.EXTRA_PREFIX_URL, item.getPrefixUrl());
                startActivity(intent);
            }
        };

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container);
        mRefreshLayout.setOnRefreshListener(this);
        mRefreshLayout.setColorSchemeResources(
                android.R.color.holo_orange_dark,
                android.R.color.holo_green_dark,
                android.R.color.holo_red_dark,
                android.R.color.holo_blue_dark
        );

        mRecyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                final int topRowVerticalPosition;
                if (recyclerView == null || recyclerView.getChildCount() == 0) {
                    topRowVerticalPosition = 0;
                } else {
                    topRowVerticalPosition = recyclerView.getChildAt(0).getTop();
                }
                mRefreshLayout.setEnabled(topRowVerticalPosition >= 0);
            }
        });

        IntentFilter statusIntentFilter = new IntentFilter(Constants.BROADCAST_ACTION);
        statusIntentFilter.addCategory(Intent.CATEGORY_DEFAULT);

        mDownloadStateReceiver = new DownloadStateReceiver();
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(mDownloadStateReceiver, statusIntentFilter);

        getLoaderManager().initLoader(LOADER_IMAGES, null, this);

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mIsRefreshing = savedInstanceState.getBoolean(FRAGMENT_STATE_REFRESHING);
            mRefreshLayout.setRefreshing(mIsRefreshing);
        }
    }

    public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            mCache.put(key, bitmap);
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mCache.get(key);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle bundle) {
        switch (loaderId) {
            case LOADER_IMAGES:
                return new CursorLoader(
                        getActivity(),
                        PhotosContract.Photo.CONTENT_URI,
                        PhotosContract.Photo.ALL_COLUMNS,
                        PhotosContract.PhotoColumns.PHOTO_TYPE + " = ?",
                        new String[]{Integer.toString(mSection)},
                        null
                );
            default:
                throw new IllegalArgumentException("Unknown loader id: " + loaderId);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        handleCursor(cursor);
    }

    private void handleCursor(Cursor cursor) {
        mItems = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            PhotoItem item = new PhotoItem();
            item.setTitle(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_TITLE)));
            item.setContentUrl(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_CONTENT_URL)));
            item.setWatchUrl(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_WATCH_URL)));
            item.setPrefixUrl(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_PREFIX_URL)));
            mItems.add(item);
            cursor.moveToNext();
        }
        mAdapter.setItems(mItems);
    }

    @Override
    public void onImageDownloaded(ImageView imageView, String url, Bitmap bitmap) {
        if (isVisible()) {
            imageView.setImageBitmap(bitmap);
            addBitmapToMemoryCache(url, bitmap);
        }
    }

    @Override
    public void onRefresh() {
        ImageLoaderService.startActionLoad(getActivity().getApplicationContext(), mSection);
    }

    private class SectionAdapter extends RecyclerView.Adapter<ViewHolderImage> {

        private ArrayList<PhotoItem> mItems;

        public SectionAdapter(ArrayList<PhotoItem> items) {
            mItems = items;
        }

        public void setItems(ArrayList<PhotoItem> items) {
            this.mItems.clear();
            this.mItems.addAll(items);
            notifyDataSetChanged();
        }

        @Override
        public ViewHolderImage onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.section_item, parent, false);
            v.setOnClickListener(mOnClickListener);
            ViewHolderImage holder = new ViewHolderImage(v);
            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolderImage viewHolderImage, int position) {
            PhotoItem item = mItems.get(position);
            final String url = item.getPrefixUrl() + "S";
            Bitmap cachedBitmap = getBitmapFromMemCache(url);
            if (cachedBitmap != null) {
                viewHolderImage.mImageView.setImageBitmap(cachedBitmap);
            } else {
                mImageDownloader.queueImage(viewHolderImage.mImageView, url);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

    }

    public static class ViewHolderImage extends RecyclerView.ViewHolder {
        public ImageView mImageView;

        public ViewHolderImage(View view) {
            super(view);
            mImageView = (ImageView) view;
        }
    }

    private class DownloadStateReceiver extends BroadcastReceiver {

        public DownloadStateReceiver() {

        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final int status = intent.getIntExtra(Constants.EXTRA_STATUS, Constants.STATE_ACTION_COMPLETE);
            final int section = intent.getIntExtra(Constants.EXTRA_SECTION, -1);
            if (section != mSection) {
                return;
            }
            switch (status)  {
                case Constants.STATE_ACTION_COMPLETE:
                    mIsRefreshing = false;
                    mRefreshLayout.setRefreshing(false);
                    final String key = Integer.toString(mSection);
                    int isFirstTime = readFromPreferences(key);
                    if (isFirstTime == -1) {
                        saveToPreferences(key, 1);
                        getLoaderManager().restartLoader(LOADER_IMAGES, Bundle.EMPTY, SectionFragment.this);
                    }
                    break;
                case Constants.STATE_ACTION_STARTED:
                    mIsRefreshing = true;
                    mRefreshLayout.setRefreshing(true);
                    break;
                case Constants.STATE_ACTION_ERROR:
                    Toast.makeText(getActivity(), "Error during update", Toast.LENGTH_SHORT).show();
                    mIsRefreshing = false;
                    mRefreshLayout.setRefreshing(false);
                    break;
                case Constants.STATE_ACTION_NO_INTERNET:
                    Toast.makeText(getActivity(), "No internet connection", Toast.LENGTH_SHORT).show();
                    mIsRefreshing = false;
                    mRefreshLayout.setRefreshing(false);
                    break;
                default:
                    break;
            }
        }
    }

}
