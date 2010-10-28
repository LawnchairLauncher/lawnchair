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

import com.android.launcher.R;
import com.android.launcher2.PagedView.PagedViewIconCache;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.Checkable;
import android.widget.TextView;



/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends TextView implements Checkable {
    private static final String TAG = "PagedViewIcon";

    // holographic outline
    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mCheckedOutline;
    private Bitmap mHolographicOutline;
    private Canvas mHolographicOutlineCanvas;
    private Rect mDrawableClipRect;
    private Bitmap mIcon;

    private Object mIconCacheKey;
    private PagedViewIconCache mIconCache;
    private int mScaledIconSize;

    private int mAlpha;
    private int mHolographicAlpha;

    private boolean mIsChecked;

    // Highlight colours
    private int mHoloBlurColor;
    private int mHoloOutlineColor;
    private int mCheckedBlurColor;
    private int mCheckedOutlineColor;

    private static final HandlerThread sWorkerThread = new HandlerThread("pagedviewicon-helper");
    static {
        sWorkerThread.start();
    }

    private static final int MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE = 1;

    private static final Handler sWorker = new Handler(sWorkerThread.getLooper()) {
        private DeferredHandler mHandler = new DeferredHandler();
        private Paint mPaint = new Paint();
        public void handleMessage(Message msg) {
            final PagedViewIcon icon = (PagedViewIcon) msg.obj;

            final Bitmap holographicOutline = Bitmap.createBitmap(
                    icon.mIcon.getWidth(), icon.mIcon.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas holographicOutlineCanvas = new Canvas(holographicOutline);
            holographicOutlineCanvas.drawBitmap(icon.mIcon, 0, 0, mPaint);

            sHolographicOutlineHelper.applyExpensiveOutlineWithBlur(holographicOutline,
                    holographicOutlineCanvas, icon.mHoloBlurColor, icon.mHoloOutlineColor);

            mHandler.post(new Runnable() {
                public void run() {
                    icon.mHolographicOutline = holographicOutline;
                    icon.mIconCache.addOutline(icon.mIconCacheKey, holographicOutline);
                    icon.invalidate();
                }
            });
        }
    };

    public PagedViewIcon(Context context) {
        this(context, null);
    }

    public PagedViewIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedViewIcon, defStyle, 0);
        mHoloBlurColor = a.getColor(R.styleable.PagedViewIcon_blurColor, 0);
        mHoloOutlineColor = a.getColor(R.styleable.PagedViewIcon_outlineColor, 0);
        mCheckedBlurColor = a.getColor(R.styleable.PagedViewIcon_checkedBlurColor, 0);
        mCheckedOutlineColor = a.getColor(R.styleable.PagedViewIcon_checkedOutlineColor, 0);
        mScaledIconSize =
            context.getResources().getDimensionPixelSize(R.dimen.temp_scaled_icon_size);

        a.recycle();

        if (sHolographicOutlineHelper == null) {
            sHolographicOutlineHelper = new HolographicOutlineHelper();
        }
        mDrawableClipRect = new Rect();

        setFocusable(true);
        setBackgroundDrawable(null);
    }

    private void queueHolographicOutlineCreation() {
        // Generate the outline in the background
        if (mHolographicOutline == null) {
            Message m = sWorker.obtainMessage(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE);
            m.obj = this;
            sWorker.sendMessage(m);
        }
    }

    public void applyFromApplicationInfo(ApplicationInfo info, PagedViewIconCache cache,
            boolean scaleUp) {
        mIconCache = cache;
        mIconCacheKey = info;
        mHolographicOutline = mIconCache.getOutline(mIconCacheKey);

        if (scaleUp) {
            mIcon = Bitmap.createScaledBitmap(info.iconBitmap, mScaledIconSize,
                    mScaledIconSize, true);
        } else {
            mIcon = info.iconBitmap;
        }
        setCompoundDrawablesWithIntrinsicBounds(null, new FastBitmapDrawable(mIcon), null, null);
        setText(info.title);
        setTag(info);

        queueHolographicOutlineCreation();
    }

    public void applyFromResolveInfo(ResolveInfo info, PackageManager packageManager,
            PagedViewIconCache cache, boolean scaleUp) {
        mIconCache = cache;
        mIconCacheKey = info;
        mHolographicOutline = mIconCache.getOutline(mIconCacheKey);

        mIcon = Utilities.createIconBitmap(info.loadIcon(packageManager), mContext);
        if (scaleUp) {
            mIcon = Bitmap.createScaledBitmap(mIcon, mScaledIconSize,
                    mScaledIconSize, true);
        }
        setCompoundDrawablesWithIntrinsicBounds(null, new FastBitmapDrawable(mIcon), null, null);
        setText(info.loadLabel(packageManager));
        setTag(info);

        queueHolographicOutlineCreation();
    }

    @Override
    public void setAlpha(float alpha) {
        final float viewAlpha = sHolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = sHolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        mAlpha = (int) (viewAlpha * 255);
        mHolographicAlpha = (int) (holographicAlpha * 255);
        super.setAlpha(viewAlpha);
    }

    public void invalidateCheckedImage() {
        if (mCheckedOutline != null) {
            mCheckedOutline.recycle();
            mCheckedOutline = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mAlpha > 0) {
            super.onDraw(canvas);
        }

        Bitmap overlay = null;

        // draw any blended overlays
        if (mCheckedOutline == null) {
            if (mHolographicOutline != null && mHolographicAlpha > 0) {
                mPaint.setAlpha(mHolographicAlpha);
                overlay = mHolographicOutline;
            }
        } else {
            mPaint.setAlpha(255);
            overlay = mCheckedOutline;
        }

        if (overlay != null) {
            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int compoundPaddingRight = getCompoundPaddingRight();
            int hspace = getWidth() - compoundPaddingRight - compoundPaddingLeft;
            canvas.drawBitmap(overlay,
                    compoundPaddingLeft + (hspace - overlay.getWidth()) / 2,
                    mPaddingTop,
                    mPaint);
        }
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sWorker.removeMessages(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE, this);
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        if (mIsChecked != checked) {
            mIsChecked = checked;

            if (mIsChecked) {
                mCheckedOutline = Bitmap.createBitmap(mIcon.getWidth(), mIcon.getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas checkedOutlineCanvas = new Canvas(mCheckedOutline);
                mPaint.setAlpha(255);
                checkedOutlineCanvas.drawBitmap(mIcon, 0, 0, mPaint);

                sHolographicOutlineHelper.applyExpensiveOutlineWithBlur(mCheckedOutline,
                        checkedOutlineCanvas, mCheckedBlurColor, mCheckedOutlineColor);
            } else {
                invalidateCheckedImage();
            }

            invalidate();
        }
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }
}
