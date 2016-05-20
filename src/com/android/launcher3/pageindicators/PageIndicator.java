package com.android.launcher3.pageindicators;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/**
 * Base class for a page indicator.
 */
public abstract class PageIndicator extends View {

    protected int mNumPages = 1;

    public PageIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public abstract void setScroll(int currentScroll, int totalScroll);

    public abstract void setActiveMarker(int activePage);

    public void addMarker() {
        mNumPages++;
        onPageCountChanged();
    }

    public void removeMarker() {
        mNumPages--;
        onPageCountChanged();
    }
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;
        onPageCountChanged();
    }

    protected abstract void onPageCountChanged();
}
