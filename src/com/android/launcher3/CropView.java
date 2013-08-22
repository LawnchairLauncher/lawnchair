/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;

import com.android.photos.views.TiledImageRenderer.TileSource;
import com.android.photos.views.TiledImageView;

public class CropView extends TiledImageView implements OnScaleGestureListener {

    private ScaleGestureDetector mScaleGestureDetector;
    private float mLastX, mLastY;
    private float mMinScale;
    private boolean mTouchEnabled = true;
    private RectF mTempEdges = new RectF();

    public CropView(Context context) {
        this(context, null);
    }

    public CropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
    }

    private void getEdgesHelper(RectF edgesOut) {
        final float width = getWidth();
        final float height = getHeight();
        final float imageWidth = mRenderer.source.getImageWidth();
        final float imageHeight = mRenderer.source.getImageHeight();
        final float scale = mRenderer.scale;
        float centerX = (width / 2f - mRenderer.centerX + (imageWidth - width) / 2f)
                * scale + width / 2f;
        float centerY = (height / 2f - mRenderer.centerY + (imageHeight - height) / 2f)
                * scale + height / 2f;
        float leftEdge = centerX - imageWidth / 2f * scale;
        float rightEdge = centerX + imageWidth / 2f * scale;
        float topEdge = centerY - imageHeight / 2f * scale;
        float bottomEdge = centerY + imageHeight / 2f * scale;

        edgesOut.left = leftEdge;
        edgesOut.right = rightEdge;
        edgesOut.top = topEdge;
        edgesOut.bottom = bottomEdge;
    }

    public RectF getCrop() {
        final RectF edges = mTempEdges;
        getEdgesHelper(edges);
        final float scale = mRenderer.scale;

        float cropLeft = -edges.left / scale;
        float cropTop = -edges.top / scale;
        float cropRight = cropLeft + getWidth() / scale;
        float cropBottom = cropTop + getHeight() / scale;

        return new RectF(cropLeft, cropTop, cropRight, cropBottom);
    }

    public void setTileSource(TileSource source, Runnable isReadyCallback) {
        super.setTileSource(source, isReadyCallback);
        updateMinScale(getWidth(), getHeight(), source);
    }

    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        updateMinScale(w, h, mRenderer.source);
    }

    private void updateMinScale(int w, int h, TileSource source) {
        synchronized (mLock) {
            if (source != null) {
                mMinScale = Math.max(w / (float) source.getImageWidth(),
                        h / (float) source.getImageHeight());
                mRenderer.scale = Math.max(mMinScale, mRenderer.scale);
            }
        }
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        // Don't need the lock because this will only fire inside of
        // onTouchEvent
        mRenderer.scale *= detector.getScaleFactor();
        mRenderer.scale = Math.max(mMinScale, mRenderer.scale);
        invalidate();
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    public void moveToUpperLeft() {
        if (getWidth() == 0 || getHeight() == 0) {
            final ViewTreeObserver observer = getViewTreeObserver();
            observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        moveToUpperLeft();
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                });
        }
        final RectF edges = mTempEdges;
        getEdgesHelper(edges);
        final float scale = mRenderer.scale;
        mRenderer.centerX += Math.ceil(edges.left / scale);
        mRenderer.centerY += Math.ceil(edges.top / scale);
    }

    public void setTouchEnabled(boolean enabled) {
        mTouchEnabled = enabled;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mTouchEnabled) {
            return true;
        }
        int action = event.getActionMasked();
        final boolean pointerUp = action == MotionEvent.ACTION_POINTER_UP;
        final int skipIndex = pointerUp ? event.getActionIndex() : -1;

        // Determine focal point
        float sumX = 0, sumY = 0;
        final int count = event.getPointerCount();
        for (int i = 0; i < count; i++) {
            if (skipIndex == i)
                continue;
            sumX += event.getX(i);
            sumY += event.getY(i);
        }
        final int div = pointerUp ? count - 1 : count;
        float x = sumX / div;
        float y = sumY / div;

        synchronized (mLock) {
            mScaleGestureDetector.onTouchEvent(event);
            switch (action) {
                case MotionEvent.ACTION_MOVE:
                    mRenderer.centerX += (mLastX - x) / mRenderer.scale;
                    mRenderer.centerY += (mLastY - y) / mRenderer.scale;
                    invalidate();
                    break;
            }
            if (mRenderer.source != null) {
                // Adjust position so that the wallpaper covers the entire area
                // of the screen
                final RectF edges = mTempEdges;
                getEdgesHelper(edges);
                final float scale = mRenderer.scale;
                if (edges.left > 0) {
                    mRenderer.centerX += Math.ceil(edges.left / scale);
                }
                if (edges.right < getWidth()) {
                    mRenderer.centerX += (edges.right - getWidth()) / scale;
                }
                if (edges.top > 0) {
                    mRenderer.centerY += Math.ceil(edges.top / scale);
                }
                if (edges.bottom < getHeight()) {
                    mRenderer.centerY += (edges.bottom - getHeight()) / scale;
                }
            }
        }

        mLastX = x;
        mLastY = y;
        return true;
    }
}
