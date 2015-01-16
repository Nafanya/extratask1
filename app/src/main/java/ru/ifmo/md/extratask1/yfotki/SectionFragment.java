package ru.ifmo.md.extratask1.yfotki;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;

import ru.ifmo.md.extratask1.yfotki.provider.PhotosContract;


public class SectionFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>,ImageDownloader.Listener<ImageView> {
    public static final String ARG_SECTION_NUMBER = "number";

    private static final int LOADER_IMAGES = 1;

    private RecyclerView mRecyclerView;
    private SectionAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;

    private ImageDownloader<ImageView> mImageDownloader = null;
    private LruCache<String, Bitmap> mCache;

    private int mSection;
    private ArrayList<PhotoItem> mItems;

    private View.OnClickListener mOnClickListener;

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

        // TODO: don't load each onCreate
        ImageLoaderService.startActionLoad(getActivity(), mSection);
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
                startActivity(intent);
            }
        };

        getLoaderManager().initLoader(LOADER_IMAGES, null, this);

        super.onViewCreated(view, savedInstanceState);
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
            //item.setSmallImagePath(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_SMALL_DISK_PATH)));
            //item.setLargeImagePath(cursor.getString(cursor.getColumnIndex(PhotosContract.Photo.PHOTO_LARGE_DISK_PATH)));
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
            final String url = item.getContentUrl() + "S";
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

}
