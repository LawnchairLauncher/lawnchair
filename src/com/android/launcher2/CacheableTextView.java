/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Bitmap.Config;
import android.text.Layout;
import android.util.AttributeSet;
import android.widget.TextView;

/*
 * This class is a bit of a hack, designed to speed up long text labels in Launcher. It caches the
 * text in a TextView to a bitmap and then just draws that Bitmap instead afterward, speeding up
 * rendering. Marquee scrolling is not currently supported.
 *
 */
public class CacheableTextView extends TextView {
    private Bitmap mCache;
    private final Paint mCachePaint = new Paint();
    private final Canvas mCacheCanvas = new Canvas();

    private int mPrevAlpha = -1;
    private boolean mIsBuildingCache;
    boolean mIsTextCacheDirty;
    float mTextCacheLeft;
    float mTextCacheTop;
    float mTextCacheScrollX;
    float mRectLeft, mRectTop;
    private float mPaddingH = 0;
    private float mPaddingV = 0;
    private CharSequence mText;

    public CacheableTextView(Context context) {
        super(context);
    }

    public CacheableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CacheableTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected int getCacheTopPadding() {
        return 0;
    }
    protected int getCacheLeftPadding() {
        return 0;
    }
    protected int getCacheRightPadding() {
        return 0;
    }
    protected int getCacheBottomPadding() {
        return 0;
    }

    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        mIsTextCacheDirty = true;
    }

    private void buildAndUpdateCache() {
        final Layout layout = getLayout();
        final int left = getCompoundPaddingLeft();
        final int top = getExtendedPaddingTop();
        final float prevAlpha = getAlpha();

        mTextCacheLeft = layout.getLineLeft(0) - getCacheLeftPadding();
        mTextCacheTop = top + layout.getLineTop(0) - mPaddingV - getCacheTopPadding();

        mRectLeft = mScrollX + getLeft();
        mRectTop = 0;
        mTextCacheScrollX = mScrollX;

        final float textCacheRight =
            Math.min(left + layout.getLineRight(0) + mPaddingH, mScrollX + mRight - mLeft) +
            getCacheRightPadding();
        final float textCacheBottom = top + layout.getLineBottom(0) + mPaddingV +
            getCacheBottomPadding();
        final float xCharWidth = getPaint().measureText("x");

        int width = (int) (textCacheRight - mTextCacheLeft + (2 * xCharWidth));
        int height = (int) (textCacheBottom - mTextCacheTop);

        if (width != 0 && height != 0) {
            if (mCache != null) {
                if (mCache.getWidth() != width || mCache.getHeight() != height) {
                    mCache.recycle();
                    mCache = null;
                }
            }
            if (mCache == null) {
                mCache = Bitmap.createBitmap(width, height, Config.ARGB_8888);
                mCacheCanvas.setBitmap(mCache);
            } else {
                mCacheCanvas.drawColor(0x00000000);
            }

            mCacheCanvas.save();
            mCacheCanvas.translate(-mTextCacheLeft, -mTextCacheTop);

            mIsBuildingCache = true;
            setAlpha(1.0f);
            draw(mCacheCanvas);
            setAlpha(prevAlpha);
            mIsBuildingCache = false;
            mCacheCanvas.restore();

            // A hack-- we set the text to be one space (we don't make it empty just to avoid any
            // potential issues with text measurement, like line height, etc.) so that the text view
            // doesn't draw it anymore, since it's been cached.
            mText = getText();
            setText(" ");
        }
    }

    public CharSequence getText() {
        return (mText == null) ? super.getText() : mText;
    }

    public void draw(Canvas canvas) {
        if (mIsTextCacheDirty && !mIsBuildingCache) {
            buildAndUpdateCache();
            mIsTextCacheDirty = false;
        }
        if (mCache != null && !mIsBuildingCache) {
            canvas.drawBitmap(mCache, mTextCacheLeft - mTextCacheScrollX + mScrollX,
                    mTextCacheTop, mCachePaint);
        }
        super.draw(canvas);
    }

    protected boolean isBuildingCache() {
        return mIsBuildingCache;
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (mPrevAlpha != alpha) {
            mPrevAlpha = alpha;
            mCachePaint.setAlpha(alpha);
            super.onSetAlpha(alpha);
        }
        return true;
    }
}
