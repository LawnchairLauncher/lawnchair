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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
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

    private static final int sPreviewFadeInDuration = 80;
    private static final int sPreviewFadeInStaggerDuration = 20;

    private final Paint mPaint = new Paint();
    private Bitmap mHolographicOutline;
    private HolographicOutlineHelper mHolographicOutlineHelper;
    private ImageView mPreviewImageView;
    private final RectF mTmpScaleRect = new RectF();

    private String mDimensionsFormatString;

    private int mAlpha = 255;
    private int mHolographicAlpha;

    private boolean mIsChecked;
    private ObjectAnimator mCheckedAlphaAnimator;
    private float mCheckedAlpha = 1.0f;
    private int mCheckedFadeInDuration;
    private int mCheckedFadeOutDuration;

    public PagedViewWidget(Context context) {
        this(context, null);
    }

    public PagedViewWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

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

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (image != null) {
            FastBitmapDrawable preview = (FastBitmapDrawable) image.getDrawable();
            if (preview != null && preview.getBitmap() != null) {
                preview.getBitmap().recycle();
            }
            image.setImageDrawable(null);
        }
    }

    public void applyFromAppWidgetProviderInfo(AppWidgetProviderInfo info,
            int maxWidth, int[] cellSpan, HolographicOutlineHelper holoOutlineHelper) {
        mHolographicOutlineHelper = holoOutlineHelper;
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (maxWidth > -1) {
            image.setMaxWidth(maxWidth);
        }
        image.setContentDescription(info.label);
        mPreviewImageView = image;
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(info.label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, cellSpan[0], cellSpan[1]));
        }
    }

    public void applyFromResolveInfo(PackageManager pm, ResolveInfo info,
            HolographicOutlineHelper holoOutlineHelper) {
        mHolographicOutlineHelper = holoOutlineHelper;
        CharSequence label = info.loadLabel(pm);
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        image.setContentDescription(label);
        mPreviewImageView = image;
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, 1, 1));
        }
    }

    void applyPreview(FastBitmapDrawable preview, int index, boolean scale) {
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (preview != null) {
            image.setImageDrawable(preview);
            image.setScaleType(scale ? ImageView.ScaleType.FIT_START : ImageView.ScaleType.MATRIX);
            image.setAlpha(0f);
            image.animate()
                 .alpha(1f)
                 .setDuration(sPreviewFadeInDuration + (index * sPreviewFadeInStaggerDuration))
                 .start();
        }
    }

    public void setHolographicOutline(Bitmap holoOutline) {
        mHolographicOutline = holoOutline;
        invalidate();
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
        return FocusHelper.handleAppsCustomizeKeyEvent(this, keyCode, event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return FocusHelper.handleAppsCustomizeKeyEvent(this, keyCode, event)
                || super.onKeyUp(keyCode, event);
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
            canvas.drawBitmap(mHolographicOutline, mPreviewImageView.getLeft(),
                    mPreviewImageView.getTop(), mPaint);
            canvas.restore();
        }
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        return true;
    }

    private void setChildrenAlpha(float alpha) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setAlpha(alpha);
        }
    }
    @Override
    public void setAlpha(float alpha) {
        final float viewAlpha = mHolographicOutlineHelper.viewAlphaInterpolator(alpha);
        final float holographicAlpha = mHolographicOutlineHelper.highlightAlphaInterpolator(alpha);
        int newViewAlpha = (int) (viewAlpha * 255);
        int newHolographicAlpha = (int) (holographicAlpha * 255);
        if ((mAlpha != newViewAlpha) || (mHolographicAlpha != newHolographicAlpha)) {
            mAlpha = newViewAlpha;
            mHolographicAlpha = newHolographicAlpha;
            setChildrenAlpha(viewAlpha);
            super.setAlpha(viewAlpha);
        }
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
