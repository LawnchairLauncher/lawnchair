/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher2;

import com.android.launcher.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Bitmap.Config;
import android.graphics.PorterDuff.Mode;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

// This class caches the drawing of this View's children in a bitmap when the scale factor
// falls below a certain size. Only used by CellLayout, but in a separate class to keep cache
// logic separate from the other logic in CellLayout
public class CachedViewGroup extends ViewGroup implements VisibilityChangedListener {
    static final String TAG = "CachedViewGroup";

    private Bitmap mCache;
    private Canvas mCacheCanvas;
    private Rect mCacheRect;
    private Paint mCachePaint;

    private boolean mIsCacheEnabled = true;
    private boolean mDisableCacheUpdates = false;
    private boolean mForceCacheUpdate = false;
    private boolean isUpdatingCache = false;
    private boolean mIsCacheDirty = true;
    private float mBitmapCacheScale;
    private float mMaxScaleForUsingBitmapCache;

    private Rect mBackgroundRect;

    public CachedViewGroup(Context context) {
        super(context);
        init();
    }

    public CachedViewGroup(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        mBackgroundRect = new Rect();
        mCacheRect = new Rect();
        final Resources res = getResources();
        mBitmapCacheScale =
            res.getInteger(R.integer.config_workspaceScreenBitmapCacheScale) / 100.0f;
        mMaxScaleForUsingBitmapCache =
            res.getInteger(R.integer.config_maxScaleForUsingWorkspaceScreenBitmapCache) / 100.0f;
        mCacheCanvas = new Canvas();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // sub-classes (namely CellLayout) will need to implement this
        prepareCacheBitmap();
        invalidateCache();
    }

    private void invalidateIfNeeded() {
        if (mIsCacheDirty) {
            // Force a redraw to update the cache if it's dirty
            invalidate();
        }
    }

    public void enableCache() {
        mIsCacheEnabled = true;
        invalidateIfNeeded();
    }

    public void disableCache() {
        mIsCacheEnabled = false;
    }

    public void disableCacheUpdates() {
        mDisableCacheUpdates = true;
        // Force just one update before we enter a period of no cache updates
        mForceCacheUpdate = true;
    }

    public void enableCacheUpdates() {
        mDisableCacheUpdates = false;
        invalidateIfNeeded();
    }

    private void invalidateCache() {
        mIsCacheDirty = true;
        invalidate();
    }

    public void receiveVisibilityChangedMessage(View v) {
        invalidateCache();
    }

    private void prepareCacheBitmap() {
        if (mCache == null) {
            mCache = Bitmap.createBitmap((int) (getWidth() * mBitmapCacheScale),
                    (int) (getHeight() * mBitmapCacheScale), Config.ARGB_8888);

            mCachePaint = new Paint();
            mCachePaint.setFilterBitmap(true);
            mCacheCanvas.setBitmap(mCache);
            mCacheCanvas.scale(mBitmapCacheScale, mBitmapCacheScale);
        }
    }


    public void updateCache() {
        mCacheCanvas.drawColor(0, Mode.CLEAR);

        float alpha = getAlpha();
        setAlpha(1.0f);
        isUpdatingCache = true;
        drawChildren(mCacheCanvas);
        isUpdatingCache = false;
        setAlpha(alpha);

        mIsCacheDirty = false;
    }

    public void drawChildren(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    public void removeAllViews() {
        super.removeAllViews();
        invalidateCache();
    }

    @Override
    public void removeAllViewsInLayout() {
        super.removeAllViewsInLayout();
        invalidateCache();
    }

    @Override
    public void removeView(View view) {
        super.removeView(view);
        invalidateCache();
    }

    @Override
    public void removeViewAt(int index) {
        super.removeViewAt(index);
        invalidateCache();
    }

    @Override
    public void removeViewInLayout(View view) {
        super.removeViewInLayout(view);
        invalidateCache();
    }

    @Override
    public void removeViews(int start, int count) {
        super.removeViews(start, count);
        invalidateCache();
    }

    @Override
    public void removeViewsInLayout(int start, int count) {
        super.removeViewsInLayout(start, count);
        invalidateCache();
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        final int count = getChildCount();

        boolean useBitmapCache = false;
        if (!isUpdatingCache) {
            if (!mIsCacheDirty) {
                // Check if one of the children (an icon or widget) is dirty
                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.isDirty()) {
                        mIsCacheDirty = true;
                        break;
                    }
                }
            }

            useBitmapCache = mIsCacheEnabled && getScaleX() < mMaxScaleForUsingBitmapCache;
            if (mForceCacheUpdate ||
                    (useBitmapCache && !mDisableCacheUpdates)) {
                // Sometimes we force a cache update-- this is used to make sure the cache will look as
                // up-to-date as possible right when we disable cache updates
                if (mIsCacheDirty) {
                    updateCache();
                }
                mForceCacheUpdate = false;
            }
        }

        if (useBitmapCache) {
            mCachePaint.setAlpha((int)(255*getAlpha()));
            canvas.drawBitmap(mCache, mCacheRect, mBackgroundRect, mCachePaint);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);

        // invalidate the cache to have it reflect the new item
        invalidateCache();

        if (child instanceof VisibilityChangedBroadcaster) {
            VisibilityChangedBroadcaster v = (VisibilityChangedBroadcaster) child;
            v.setVisibilityChangedListener(this);
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mBackgroundRect.set(0, 0, w, h);
        mCacheRect.set(0, 0, (int) (mBitmapCacheScale * w), (int) (mBitmapCacheScale * h));
        mCache = null;
        prepareCacheBitmap();
        invalidateCache();
    }
}


//Custom interfaces used to listen to "visibility changed" events of *children* of Views. Avoided
//using "onVisibilityChanged" in the names because there's a method of that name in framework
//(which can only can be used to listen to ancestors' "visibility changed" events)
interface VisibilityChangedBroadcaster {
    public void setVisibilityChangedListener(VisibilityChangedListener listener);
}

interface VisibilityChangedListener {
    public void receiveVisibilityChangedMessage(View v);
}