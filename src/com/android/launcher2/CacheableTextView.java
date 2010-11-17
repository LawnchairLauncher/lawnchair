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

    private int mPrevAlpha = -1;
    private boolean mIsBuildingCache;
    boolean mWaitingToGenerateCache;
    float mTextCacheLeft;
    float mTextCacheTop;
    float mTextCacheScrollX;
    float mRectLeft, mRectTop;
    private float mPaddingH = 0;
    private float mPaddingV = 0;

    public CacheableTextView(Context context) {
        super(context);
    }

    public CacheableTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CacheableTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void buildAndEnableCache() {
        if (getLayout() == null) {
            mWaitingToGenerateCache = true;
            return;
        }

        final Layout layout = getLayout();

        final int left = getCompoundPaddingLeft();
        final int top = getExtendedPaddingTop();
        mTextCacheLeft = layout.getLineLeft(0);
        mTextCacheTop = top + layout.getLineTop(0) - mPaddingV;

        mRectLeft = mScrollX + getLeft();
        mRectTop = 0;
        mTextCacheScrollX = mScrollX;

        final float textCacheRight =
            Math.min(left + layout.getLineRight(0) + mPaddingH, mScrollX + mRight - mLeft);
        final float textCacheBottom = top + layout.getLineBottom(0) + mPaddingV;

        mCache = Bitmap.createBitmap((int) (textCacheRight - mTextCacheLeft),
                (int) (textCacheBottom - mTextCacheTop), Config.ARGB_8888);
        Canvas c = new Canvas(mCache);
        c.translate(-mTextCacheLeft, -mTextCacheTop);

        mIsBuildingCache = true;
        float alpha = getAlpha();
        setAlpha(1.0f);
        draw(c);
        setAlpha(alpha);
        mIsBuildingCache = false;
        mCachePaint.setFilterBitmap(true);

        // A hack-- we set the text to be one space (we don't make it empty just to avoid any
        // potential issues with text measurement, like line height, etc.) so that the text view
        // doesn't draw it anymore, since it's been cached. We have to manually rebuild
        // the cache whenever the text is changed (which is never in Launcher)
        setText(" ");
    }

    public void draw(Canvas canvas) {
        if (mWaitingToGenerateCache && !mIsBuildingCache) {
            buildAndEnableCache();
            mWaitingToGenerateCache = false;
        }
        if (mCache != null) {
            canvas.drawBitmap(mCache, mTextCacheLeft - mTextCacheScrollX + mScrollX,
                    mTextCacheTop, mCachePaint);
        }
        super.draw(canvas);
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