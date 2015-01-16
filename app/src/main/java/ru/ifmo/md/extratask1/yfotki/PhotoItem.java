package ru.ifmo.md.extratask1.yfotki;

/**
 * Created by Nikita Yaschenko on 14.01.15.
 */
public class PhotoItem {
    private String mTitle;
    private String mContentUrl;
    private String mWatchUrl;
    public PhotoItem() {
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(String title) {
        mTitle = title;
    }

    public String getContentUrl() {
        return mContentUrl;
    }

    public void setContentUrl(String contentUrl) {
        mContentUrl = contentUrl;
    }

    public String getWatchUrl() {
        return mWatchUrl;
    }

    public void setWatchUrl(String watchUrl) {
        mWatchUrl = watchUrl;
    }
}
