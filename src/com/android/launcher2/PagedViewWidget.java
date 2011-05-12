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
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher.R;

/**
 * The linear layout used strictly for the widget/wallpaper tab of the customization tray
 */
public class PagedViewWidget extends LinearLayout implements Checkable {
    static final String TAG = "PagedViewWidgetLayout";

    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mHolographicOutline;
    private final Canvas mHolographicOutlineCanvas = new Canvas();
    private FastBitmapDrawable mPreview;
    private ImageView mPreviewImageView;
    private final RectF mTmpScaleRect = new RectF();
    private final Rect mEraseStrokeRect = new Rect();
    private final Paint mEraseStrokeRectPaint = new Paint();

    private PagedViewIconCache.Key mIconCacheKey;
    private PagedViewIconCache mIconCache;
    private String mDimensionsFormatString;

    private int mAlpha = 255;
    private int mHolographicAlpha;

    // Highlight colors
    private int mHoloBlurColor;
    private int mHoloOutlineColor;

    private boolean mIsChecked;
    private ObjectAnimator mCheckedAlphaAnimator;
    private float mCheckedAlpha = 1.0f;
    private int mCheckedFadeInDuration;
    private int mCheckedFadeOutDuration;

    private static final HandlerThread sWorkerThread = new HandlerThread("pagedviewwidget-helper");
    static {
        sWorkerThread.start();
    }

    private static final int MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE = 1;

    private static final Handler sWorker = new Handler(sWorkerThread.getLooper()) {
        private DeferredHandler mHandler = new DeferredHandler();
        public void handleMessage(Message msg) {
            final PagedViewWidget widget = (PagedViewWidget) msg.obj;
            final int prevAlpha = widget.mPreview.getAlpha();
            final int width = Math.max(widget.mPreview.getIntrinsicWidth(),
                    widget.getMeasuredWidth());
            final int height = Math.max(widget.mPreview.getIntrinsicHeight(),
                    widget.getMeasuredHeight());
            final Bitmap outline = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            widget.mHolographicOutlineCanvas.setBitmap(outline);
            widget.mHolographicOutlineCanvas.save();
            widget.mHolographicOutlineCanvas.translate(widget.mPaddingLeft, widget.mPaddingTop);
            widget.mPreview.setAlpha(255);
            widget.mPreview.draw(widget.mHolographicOutlineCanvas);
            widget.mPreview.setAlpha(prevAlpha);
            // Temporary workaround to make the default widget outlines visible
            widget.mHolographicOutlineCanvas.drawColor(Color.argb(156, 0, 0, 0), Mode.SRC_OVER);
            widget.mHolographicOutlineCanvas.restore();

            // To account for the fact that some previews run up straight to the edge (we subtract
            // the edge from the holographic preview (before we apply the holograph)
            widget.mEraseStrokeRect.set(0, 0, width, height);
            widget.mHolographicOutlineCanvas.drawRect(widget.mEraseStrokeRect,
                    widget.mEraseStrokeRectPaint);

            sHolographicOutlineHelper.applyThickExpensiveOutlineWithBlur(outline,
                    widget.mHolographicOutlineCanvas, widget.mHoloBlurColor,
                    widget.mHoloOutlineColor);

            mHandler.post(new Runnable() {
                public void run() {
                    widget.mHolographicOutline = outline;
                    widget.mIconCache.addOutline(widget.mIconCacheKey, outline);
                    widget.invalidate();
                }
            });
        }
    };

    public PagedViewWidget(Context context) {
        this(context, null);
    }

    public PagedViewWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.PagedViewWidget,
                defStyle, 0);
        mHoloBlurColor = a.getColor(R.styleable.PagedViewWidget_blurColor, 0);
        mHoloOutlineColor = a.getColor(R.styleable.PagedViewWidget_outlineColor, 0);
        mEraseStrokeRectPaint.setStyle(Paint.Style.STROKE);
        mEraseStrokeRectPaint.setStrokeWidth(HolographicOutlineHelper.MIN_OUTER_BLUR_RADIUS);
        mEraseStrokeRectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mEraseStrokeRectPaint.setFilterBitmap(true);
        mEraseStrokeRectPaint.setAntiAlias(true);
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
        mDimensionsFormatString = r.getString(R.string.widget_dims_format);

        setWillNotDraw(false);
        setClipToPadding(false);
    }

    private void queueHolographicOutlineCreation() {
        // Generate the outline in the background
        if (mHolographicOutline == null && mPreview != null) {
            Message m = sWorker.obtainMessage(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE);
            m.obj = this;
            sWorker.sendMessage(m);
        }
    }

    public void applyFromAppWidgetProviderInfo(AppWidgetProviderInfo info,
            FastBitmapDrawable preview, int maxWidth, int[] cellSpan,
            PagedViewIconCache cache, boolean createHolographicOutline) {
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (maxWidth > -1) {
            image.setMaxWidth(maxWidth);
        }
        image.setImageDrawable(preview);
        mPreviewImageView = image;
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(info.label);
        name.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        dims.setText(String.format(mDimensionsFormatString, cellSpan[0], cellSpan[1]));
        dims.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        if (createHolographicOutline) {
            mIconCache = cache;
            mIconCacheKey = new PagedViewIconCache.Key(info);
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            mPreview = preview;
        }
    }

    public void applyFromResolveInfo(PackageManager pm, ResolveInfo info,
            FastBitmapDrawable preview, PagedViewIconCache cache, boolean createHolographicOutline){
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        image.setImageDrawable(preview);
        mPreviewImageView = image;
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(info.loadLabel(pm));
        name.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, 1, 1));
            dims.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        if (createHolographicOutline) {
            mIconCache = cache;
            mIconCacheKey = new PagedViewIconCache.Key(info);
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            mPreview = preview;
        }
    }

    public void applyFromWallpaperInfo(ResolveInfo info, PackageManager packageManager,
            FastBitmapDrawable preview, int maxWidth, PagedViewIconCache cache,
            boolean createHolographicOutline) {
        ImageView image = (ImageView) findViewById(R.id.wallpaper_preview);
        image.setMaxWidth(maxWidth);
        image.setImageDrawable(preview);
        mPreviewImageView = image;
        TextView name = (TextView) findViewById(R.id.wallpaper_name);
        name.setText(info.loadLabel(packageManager));
        name.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        if (createHolographicOutline) {
            mIconCache = cache;
            mIconCacheKey = new PagedViewIconCache.Key(info);
            mHolographicOutline = mIconCache.getOutline(mIconCacheKey);
            mPreview = preview;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We eat up the touch events here, since the PagedView (which uses the same swiping
        // touch code as Workspace previously) uses onInterceptTouchEvent() to determine when
        // the user is scrolling between pages.  This means that if the pages themselves don't
        // handle touch events, it gets forwarded up to PagedView itself, and it's own
        // onTouchEvent() handling will prevent further intercept touch events from being called
        // (it's the same view in that case).  This is not ideal, but to prevent more changes,
        // we just always mark the touch event as handled.
        return super.onTouchEvent(event) || true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (LauncherApplication.isScreenXLarge()) {
            return FocusHelper.handlePagedViewWidgetKeyEvent(this, keyCode, event)
                    || super.onKeyDown(keyCode, event);
        } else {
            return FocusHelper.handlePagedViewGridLayoutWidgetKeyEvent(this, keyCode, event)
                    || super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (LauncherApplication.isScreenXLarge()) {
            return FocusHelper.handlePagedViewWidgetKeyEvent(this, keyCode, event)
                    || super.onKeyUp(keyCode, event);
        } else {
            return FocusHelper.handlePagedViewGridLayoutWidgetKeyEvent(this, keyCode, event)
                    || super.onKeyUp(keyCode, event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mAlpha > 0) {
            super.onDraw(canvas);
        }

        // draw any blended overlays
        if (mHolographicOutline != null && mHolographicAlpha > 0) {
            // Calculate how much to scale the holographic preview
            mTmpScaleRect.set(0,0,1,1);
            mPreviewImageView.getImageMatrix().mapRect(mTmpScaleRect);

            mPaint.setAlpha(mHolographicAlpha);
            canvas.save();
            canvas.scale(mTmpScaleRect.right, mTmpScaleRect.bottom);
            canvas.drawBitmap(mHolographicOutline, 0, 0, mPaint);
            canvas.restore();
        }
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
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
            setChildrenAlpha(viewAlpha);
            super.setAlpha(viewAlpha);
        }
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (w > 0 && h > 0) {
            queueHolographicOutlineCreation();
        }

        super.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sWorker.removeMessages(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE, this);
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
    public boolean isChecked() {
        return mIsChecked;
    }

    @Override
    public void toggle() {
        setChecked(!mIsChecked);
    }
}
