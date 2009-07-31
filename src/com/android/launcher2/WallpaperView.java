/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Scroller;
import android.widget.TextView;
import android.os.Parcelable;
import android.os.Parcel;

import java.util.ArrayList;

/**
 * Wallpaper view shows the wallpaper bitmap, which is far layer in the parallax.
 */
public class WallpaperView extends View {

    private int mScreenCount;

    private Paint mPaint;
    private Bitmap mWallpaper;

    private int mWallpaperWidth;
    private int mWallpaperHeight;
    private float mWallpaperOffset;
    private boolean mWallpaperLoaded;

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     */
    public WallpaperView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Used to inflate the Workspace from XML.
     *
     * @param context The application's context.
     * @param attrs The attribtues set containing the Workspace's customization values.
     * @param defStyle Unused.
     */
    public WallpaperView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        initWorkspace();
    }

    /**
     * Initializes various states for this workspace.
     */
    private void initWorkspace() {
        mPaint = new Paint();
        mPaint.setDither(false);
    }

    /**
     * Set the background's wallpaper.
     */
    void loadWallpaper(Bitmap bitmap) {
        mWallpaper = bitmap;
        mWallpaperLoaded = true;
        requestLayout();
        invalidate();
    }

    void setScreenCount(int count) {
        mScreenCount = count;
    }

    @Override
    public boolean isOpaque() {
        return !mWallpaper.hasAlpha();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean restore = false;

        float x = mScrollX * mWallpaperOffset;
        if (x + mWallpaperWidth < mRight - mLeft) {
            x = mRight - mLeft - mWallpaperWidth;
        }

        canvas.drawBitmap(mWallpaper, x, (mBottom - mTop - mWallpaperHeight) / 2, mPaint);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        
        if (mWallpaperLoaded) {
            mWallpaperLoaded = false;
            mWallpaper = Utilities.centerToFit(mWallpaper, width, height, getContext());
            mWallpaperWidth = mWallpaper.getWidth();
            mWallpaperHeight = mWallpaper.getHeight();
        }

        final int wallpaperWidth = mWallpaperWidth;
        mWallpaperOffset = wallpaperWidth > width ? (mScreenCount * width - wallpaperWidth) /
                ((mScreenCount - 1) * (float) width) : 1.0f;
    }
}

