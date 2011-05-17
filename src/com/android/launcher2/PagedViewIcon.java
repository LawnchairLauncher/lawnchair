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

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Checkable;

import com.android.launcher.R;


/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends CachedTextView implements Checkable {
    private static final String TAG = "PagedViewIcon";

    // holographic outline
    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mCheckedOutline;
    private Bitmap mHolographicOutline;
    private Bitmap mIcon;

    private PagedViewIconCache.Key mIconCacheKey;
    private PagedViewIconCache mIconCache;

    private int mAlpha = 255;
    private int mHolographicAlpha;

    private boolean mIsChecked;
    private ObjectAnimator mCheckedAlphaAnimator;
    private float mCheckedAlpha = 1.0f;
    private int mCheckedFadeInDuration;
    private int mCheckedFadeOutDuration;

    // Highlight colors
    private int mHoloBlurColor;
    private int mHoloOutlineColor;

    HolographicPagedViewIcon mHolographicOutlineView;

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

            sHolographicOutlineHelper.applyThickExpensiveOutlineWithBlur(holographicOutline,
                    holographicOutlineCanvas, icon.mHoloBlurColor, icon.mHoloOutlineColor);

            mHandler.post(new Runnable() {
                public void run() {
                    icon.mHolographicOutline = holographicOutline;
                    icon.mIconCache.addOutline(icon.mIconCacheKey, holographicOutline);
                    icon.getHolographicOutlineView().invalidate();
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
        a.recycle();

        if (sHolographicOutlineHelper == null) {
            sHolographicOutlineHelper = new HolographicOutlineHelper();
        }

        // Set up fade in/out constants
        final Resources r = context.getResources();
        final int alpha = r.getInteger(R.integer.config_dragAppsCustomizeIconFadeAlpha);
        if (alpha > 0) {
            mCheckedAlpha = r.getInteger(R.integer.config_dragAppsCustomizeIconFadeAlpha) / 256.0f;
            mCheckedFadeInDuration =
                r.getInteger(R.integer.config_dragAppsCustomizeIconFadeInDuration);
            mCheckedFadeOutDuration =
                r.getInteger(R.integer.config_dragAppsCustomizeIconFadeOutDuration);
        }

        mHolographicOutlineView = new HolographicPagedViewIcon(context, this);
    }

    protected HolographicPagedViewIcon getHolographicOutlineView() {
        return mHolographicOutlineView;
    }

    protected Bitmap getHolographicOutline() {
        return mHolographicOutline;
    }

    private boolean queueHolographicOutlineCreation() {
        // Generate the outline in the background
        if (mHolographicOutline == null) {
            Message m = sWorker.obtainMessage(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE);
            m.obj = this;
            sWorker.sendMessage(m);
            return true;
        }
        return false;
    }

    public void loadHolographicIcon() {
        if (mHolographicOutline == null) {
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            if (!queueHolographicOutlineCreation()) {
                getHolographicOutlineView().invalidate();
            }
        }
    }
    public void clearHolographicIcon() {
        mHolographicOutline = null;
        getHolographicOutlineView().invalidate();
    }

    public void applyFromApplicationInfo(ApplicationInfo info, PagedViewIconCache cache,
            boolean scaleUp, boolean createHolographicOutlines) {
        mIconCache = cache;
        mIconCacheKey = new PagedViewIconCache.Key(info);
        mIcon = info.iconBitmap;
        setCompoundDrawablesWithIntrinsicBounds(null, new FastBitmapDrawable(mIcon), null, null);
        setText(info.title);
        setTag(info);

        if (createHolographicOutlines) {
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            if (!queueHolographicOutlineCreation()) {
                getHolographicOutlineView().invalidate();
            }
        }
    }

    public void applyFromResolveInfo(ResolveInfo info, PackageManager packageManager,
            PagedViewIconCache cache, IconCache modelIconCache, boolean createHolographicOutlines) {
        ComponentName cn = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        mIcon = modelIconCache.getIcon(cn, info);
        setCompoundDrawablesWithIntrinsicBounds(null, new FastBitmapDrawable(mIcon), null, null);
        setText(info.loadLabel(packageManager));
        setTag(info);

        if (createHolographicOutlines) {
            mIconCache = cache;
            mIconCacheKey = new PagedViewIconCache.Key(info);
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            if (!queueHolographicOutlineCreation()) {
                getHolographicOutlineView().invalidate();
            }
        }
    }

    @Override
    public void setAlpha(float alpha) {
        final float viewAlpha = sHolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = sHolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        int newViewAlpha = (int) (viewAlpha * 255);
        int newHolographicAlpha = (int) (holographicAlpha * 255);
        if ((mAlpha != newViewAlpha) || (mHolographicAlpha != newHolographicAlpha)) {
            mAlpha = newViewAlpha;
            mHolographicAlpha = newHolographicAlpha;
            super.setAlpha(viewAlpha);
        }
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
        if (mCheckedOutline != null) {
            mPaint.setAlpha(255);
            overlay = mCheckedOutline;
        }

        if (overlay != null) {
            final int offset = getScrollX();
            final int compoundPaddingLeft = getCompoundPaddingLeft();
            final int compoundPaddingRight = getCompoundPaddingRight();
            int hspace = getWidth() - compoundPaddingRight - compoundPaddingLeft;
            canvas.drawBitmap(overlay,
                    offset + compoundPaddingLeft + (hspace - overlay.getWidth()) / 2,
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
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return FocusHelper.handlePagedViewIconKeyEvent(this, keyCode, event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return FocusHelper.handlePagedViewIconKeyEvent(this, keyCode, event)
                || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean isChecked() {
        return mIsChecked;
    }

    void setChecked(boolean checked, boolean animate) {
        if (mIsChecked != checked) {
            mIsChecked = checked;

            float alpha;
            int duration;
            if (mIsChecked) {
                alpha = mCheckedAlpha;
                duration = mCheckedFadeInDuration;
            } else {
                alpha = 1.0f;
                duration = mCheckedFadeOutDuration;
            }

            // Initialize the animator
            if (mCheckedAlphaAnimator != null) {
                mCheckedAlphaAnimator.cancel();
            }
            if (animate) {
                mCheckedAlphaAnimator = ObjectAnimator.ofFloat(this, "alpha", getAlpha(), alpha);
                mCheckedAlphaAnimator.setDuration(duration);
                mCheckedAlphaAnimator.start();
            } else {
                setAlpha(alpha);
            }

            invalidate();
        }
    }

    @Override
    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }
}
