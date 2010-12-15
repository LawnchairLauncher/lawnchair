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

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher.R;
import com.android.launcher2.PagedView.PagedViewIconCache;

/**
 * The linear layout used strictly for the widget/wallpaper tab of the customization tray
 */
public class PagedViewWidget extends LinearLayout {
    static final String TAG = "PagedViewWidgetLayout";

    private final Paint mPaint = new Paint();
    private static HolographicOutlineHelper sHolographicOutlineHelper;
    private Bitmap mHolographicOutline;
    private final Canvas mHolographicOutlineCanvas = new Canvas();
    private FastBitmapDrawable mPreview;

    private int mAlpha = 255;
    private int mHolographicAlpha;

    // Highlight colors
    private int mHoloBlurColor;
    private int mHoloOutlineColor;

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
            final Bitmap outline = Bitmap.createBitmap(widget.getWidth(), widget.getHeight(),
                    Bitmap.Config.ARGB_8888);

            widget.mHolographicOutlineCanvas.setBitmap(outline);
            widget.mHolographicOutlineCanvas.save();
            widget.mHolographicOutlineCanvas.translate(widget.mPaddingLeft, widget.mPaddingTop);
            widget.mPreview.setAlpha(255);
            widget.mPreview.draw(widget.mHolographicOutlineCanvas);
            widget.mPreview.setAlpha(prevAlpha);
            // Temporary workaround to make the default widget outlines visible
            widget.mHolographicOutlineCanvas.drawColor(Color.argb(156, 0, 0, 0), Mode.SRC_OVER);
            widget.mHolographicOutlineCanvas.restore();

            sHolographicOutlineHelper.applyThickExpensiveOutlineWithBlur(outline,
                    widget.mHolographicOutlineCanvas, widget.mHoloBlurColor,
                    widget.mHoloOutlineColor);

            mHandler.post(new Runnable() {
                public void run() {
                    widget.mHolographicOutline = outline;
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
        a.recycle();

        if (sHolographicOutlineHelper == null) {
            sHolographicOutlineHelper = new HolographicOutlineHelper();
        }

        setFocusable(true);
        setWillNotDraw(false);
        setClipToPadding(false);
    }

    private void queueHolographicOutlineCreation() {
        // Generate the outline in the background
        if (mHolographicOutline == null) {
            Message m = sWorker.obtainMessage(MESSAGE_CREATE_HOLOGRAPHIC_OUTLINE);
            m.obj = this;
            sWorker.sendMessage(m);
        }
    }

    public void applyFromAppWidgetProviderInfo(AppWidgetProviderInfo info,
            FastBitmapDrawable preview, int maxWidth, int[] cellSpan) {
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        image.setMaxWidth(maxWidth);
        image.setImageDrawable(preview);
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(info.label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        dims.setText(mContext.getString(R.string.widget_dims_format, cellSpan[0], cellSpan[1]));
        mPreview = preview;
    }

    public void applyFromWallpaperInfo(ResolveInfo info, PackageManager packageManager,
            FastBitmapDrawable preview, int maxWidth) {
        ImageView image = (ImageView) findViewById(R.id.wallpaper_preview);
        image.setMaxWidth(maxWidth);
        image.setImageDrawable(preview);
        TextView name = (TextView) findViewById(R.id.wallpaper_name);
        name.setText(info.loadLabel(packageManager));
        mPreview = preview;
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
    protected void onDraw(Canvas canvas) {
        if (mAlpha > 0) {
            super.onDraw(canvas);
        }

        // draw any blended overlays
        if (mHolographicOutline != null && mHolographicAlpha > 0) {
            mPaint.setAlpha(mHolographicAlpha);
            canvas.drawBitmap(mHolographicOutline, 0, 0, mPaint);
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
}
