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

package com.cyanogenmod.trebuchet;

import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cyanogenmod.trebuchet.R;

/**
 * The linear layout used strictly for the widget/wallpaper tab of the customization tray
 */
public class PagedViewWidget extends LinearLayout {
    private static final String TAG = "Trebuchet.PagedViewWidgetLayout";

    private static boolean sDeletePreviewsWhenDetachedFromWindow = true;

    private String mDimensionsFormatString;
    CheckForShortPress mPendingCheckForShortPress = null;
    ShortPressListener mShortPressListener = null;
    boolean mShortPressTriggered = false;
    static PagedViewWidget sShortpressTarget = null;
    boolean mIsAppWidget;
    private final Rect mOriginalImagePadding = new Rect();

    public PagedViewWidget(Context context) {
        this(context, null);
    }

    public PagedViewWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PagedViewWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources r = context.getResources();
        mDimensionsFormatString = r.getString(R.string.widget_dims_format);

        setWillNotDraw(false);
        setClipToPadding(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        mOriginalImagePadding.left = image.getPaddingLeft();
        mOriginalImagePadding.top = image.getPaddingTop();
        mOriginalImagePadding.right = image.getPaddingRight();
        mOriginalImagePadding.bottom = image.getPaddingBottom();
    }

    public static void setDeletePreviewsWhenDetachedFromWindow(boolean value) {
        sDeletePreviewsWhenDetachedFromWindow = value;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (sDeletePreviewsWhenDetachedFromWindow) {
            final ImageView image = (ImageView) findViewById(R.id.widget_preview);
            if (image != null) {
                FastBitmapDrawable preview = (FastBitmapDrawable) image.getDrawable();
                if (preview != null && preview.getBitmap() != null) {
                    preview.getBitmap().recycle();
                }
                image.setImageDrawable(null);
            }
        }
    }

    public void applyFromAppWidgetProviderInfo(AppWidgetProviderInfo info,
            int maxWidth, int[] cellSpan) {
        mIsAppWidget = true;
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (maxWidth > -1) {
            image.setMaxWidth(maxWidth);
        }
        image.setContentDescription(info.label);
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(info.label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            int hSpan = Math.min(cellSpan[0], LauncherModel.getCellCountX());
            int vSpan = Math.min(cellSpan[1], LauncherModel.getCellCountY());
            dims.setText(String.format(mDimensionsFormatString, hSpan, vSpan));
        }
    }

    public void applyFromResolveInfo(PackageManager pm, ResolveInfo info) {
        mIsAppWidget = false;
        CharSequence label = info.loadLabel(pm);
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        image.setContentDescription(label);
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, 1, 1));
        }
    }

    public void applyFromLauncherAction(LauncherAction.Action info) {
        mIsAppWidget = false;
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        image.setContentDescription(getContext().getResources().getString(info.getString()));
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(getContext().getResources().getString(info.getString()));
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, 1, 1));
        }
    }

    public int[] getPreviewSize() {
        final ImageView i = (ImageView) findViewById(R.id.widget_preview);
        int[] maxSize = new int[2];
        maxSize[0] = i.getWidth() - mOriginalImagePadding.left - mOriginalImagePadding.right;
        maxSize[1] = i.getHeight() - mOriginalImagePadding.top;
        return maxSize;
    }

    void applyPreview(FastBitmapDrawable preview) {
        final PagedViewWidgetImageView image =
            (PagedViewWidgetImageView) findViewById(R.id.widget_preview);
        if (preview != null) {
            image.mAllowRequestLayout = false;
            image.setImageDrawable(preview);
            if (mIsAppWidget) {
                // center horizontally
                int[] imageSize = getPreviewSize();
                int centerAmount = (imageSize[0] - preview.getIntrinsicWidth()) / 2;
                image.setPadding(mOriginalImagePadding.left + centerAmount,
                        mOriginalImagePadding.top,
                        mOriginalImagePadding.right,
                        mOriginalImagePadding.bottom);
            }
            image.setAlpha(1f);
            image.mAllowRequestLayout = true;
        }
    }

    void setShortPressListener(ShortPressListener listener) {
        mShortPressListener = listener;
    }

    interface ShortPressListener {
        void onShortPress(View v);
        void cleanUpShortPress(View v);
    }

    class CheckForShortPress implements Runnable {
        public void run() {
            if (sShortpressTarget != null) return;
            if (mShortPressListener != null) {
                mShortPressListener.onShortPress(PagedViewWidget.this);
                sShortpressTarget = PagedViewWidget.this;
            }
            mShortPressTriggered = true;
        }
    }

    private void checkForShortPress() {
        if (sShortpressTarget != null) return;
        if (mPendingCheckForShortPress == null) {
            mPendingCheckForShortPress = new CheckForShortPress();
        }
        postDelayed(mPendingCheckForShortPress, 120);
    }

    /**
     * Remove the longpress detection timer.
     */
    private void removeShortPressCallback() {
        if (mPendingCheckForShortPress != null) {
          removeCallbacks(mPendingCheckForShortPress);
        }
    }

    private void cleanUpShortPress() {
        removeShortPressCallback();
        if (mShortPressTriggered) {
            if (mShortPressListener != null) {
                mShortPressListener.cleanUpShortPress(PagedViewWidget.this);
            }
            mShortPressTriggered = false;
        }
    }

    static void resetShortPressTarget() {
        sShortpressTarget = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_UP:
                cleanUpShortPress();
                break;
            case MotionEvent.ACTION_DOWN:
                checkForShortPress();
                break;
            case MotionEvent.ACTION_CANCEL:
                cleanUpShortPress();
                break;
            case MotionEvent.ACTION_MOVE:
                break;
        }

        // We eat up the touch events here, since the PagedView (which uses the same swiping
        // touch code as Workspace previously) uses onInterceptTouchEvent() to determine when
        // the user is scrolling between pages.  This means that if the pages themselves don't
        // handle touch events, it gets forwarded up to PagedView itself, and it's own
        // onTouchEvent() handling will prevent further intercept touch events from being called
        // (it's the same view in that case).  This is not ideal, but to prevent more changes,
        // we just always mark the touch event as handled.
        return true;
    }
}
