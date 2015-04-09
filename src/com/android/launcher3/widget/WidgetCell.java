/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.R;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.WidgetPreviewLoader.PreviewLoadRequest;
import com.android.launcher3.compat.AppWidgetManagerCompat;

/**
 * The linear layout used strictly for the widget tray.
 */
public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {

    private static final String TAG = "PagedViewWidget";
    private static final boolean DEBUG = false;

    // Temporary preset width and height of the image to keep them aligned.
    //private static final int PRESET_PREVIEW_HEIGHT = 480;
    //private static final int PRESET_PREVIEW_WIDTH = 480;

    private int mPresetPreviewSize;

    private static WidgetCell sShortpressTarget = null;

    private final Rect mOriginalImagePadding = new Rect();

    private String mDimensionsFormatString;
    private CheckForShortPress mPendingCheckForShortPress = null;
    private ShortPressListener mShortPressListener = null;
    private boolean mShortPressTriggered = false;
    private boolean mIsAppWidget;
    private Object mInfo;

    private WidgetPreviewLoader mWidgetPreviewLoader;
    private PreviewLoadRequest mActiveRequest;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        final Resources r = context.getResources();
        mDimensionsFormatString = r.getString(R.string.widget_dims_format);
        mPresetPreviewSize = r.getDimensionPixelSize(R.dimen.widget_preview_size);

        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(LauncherAppState.getInstance().getAccessibilityDelegate());

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        mOriginalImagePadding.left = image.getPaddingLeft();
        mOriginalImagePadding.top = image.getPaddingTop();
        mOriginalImagePadding.right = image.getPaddingRight();
        mOriginalImagePadding.bottom = image.getPaddingBottom();

        // Ensure we are using the right text size
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        TextView name = (TextView) findViewById(R.id.widget_name);
        if (name != null) {
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
        }
        TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setTextSize(TypedValue.COMPLEX_UNIT_PX, grid.iconTextSizePx);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (DEBUG) {
            Log.d(TAG, String.format("[tag=%s] onDetachedFromWindow", getTagToString()));
        }
        super.onDetachedFromWindow();
        deletePreview(false);
    }

    public void deletePreview(boolean recycleImage) {
        if (recycleImage) {
            final ImageView image = (ImageView) findViewById(R.id.widget_preview);
            if (image != null) {
                image.setImageDrawable(null);
            }
        }

        if (mActiveRequest != null) {
            mActiveRequest.cancel(recycleImage);
            mActiveRequest = null;
        }
    }

    public void applyFromAppWidgetProviderInfo(LauncherAppWidgetProviderInfo info,
            int maxWidth, WidgetPreviewLoader loader) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mIsAppWidget = true;
        mInfo = info;
        final ImageView image = (ImageView) findViewById(R.id.widget_preview);
        if (maxWidth > -1) {
            image.setMaxWidth(maxWidth);
        }
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(AppWidgetManagerCompat.getInstance(getContext()).loadLabel(info));
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            int hSpan = Math.min(info.spanX, (int) grid.numColumns);
            int vSpan = Math.min(info.spanY, (int) grid.numRows);
            dims.setText(String.format(mDimensionsFormatString, hSpan, vSpan));
        }
        mWidgetPreviewLoader = loader;
    }

    public void applyFromResolveInfo(
            PackageManager pm, ResolveInfo info, WidgetPreviewLoader loader) {
        mIsAppWidget = false;
        mInfo = info;
        CharSequence label = info.loadLabel(pm);
        final TextView name = (TextView) findViewById(R.id.widget_name);
        name.setText(label);
        final TextView dims = (TextView) findViewById(R.id.widget_dims);
        if (dims != null) {
            dims.setText(String.format(mDimensionsFormatString, 1, 1));
        }
        mWidgetPreviewLoader = loader;
    }

    public int[] getPreviewSize() {
        final ImageView i = (ImageView) findViewById(R.id.widget_preview);
        int[] maxSize = new int[2];
        maxSize[0] = mPresetPreviewSize;
        maxSize[1] = mPresetPreviewSize;
        return maxSize;
    }

    public void applyPreview(Bitmap bitmap) {
        FastBitmapDrawable preview = new FastBitmapDrawable(bitmap);
        final WidgetImageView image =
            (WidgetImageView) findViewById(R.id.widget_preview);
        if (DEBUG) {
            Log.d(TAG, String.format("[tag=%s] applyPreview preview: %s",
                    getTagToString(), preview));
        }
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
            image.requestLayout();
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
                mShortPressListener.onShortPress(WidgetCell.this);
                sShortpressTarget = WidgetCell.this;
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
                mShortPressListener.cleanUpShortPress(WidgetCell.this);
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

    public void ensurePreview() {
        if (mActiveRequest != null) {
            return;
        }
        int[] size = getPreviewSize();
        if (DEBUG) {
            Log.d(TAG, String.format("[tag=%s] ensurePreview (%d, %d):",
                    getTagToString(), size[0], size[1]));
        }

        if (size[0] <= 0 || size[1] <= 0) {
            addOnLayoutChangeListener(this);
            return;
        }
        Bitmap[] immediateResult = new Bitmap[1];
        mActiveRequest = mWidgetPreviewLoader.getPreview(mInfo, size[0], size[1], this,
                immediateResult);
        if (immediateResult[0] != null) {
            applyPreview(immediateResult[0]);
        }
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        removeOnLayoutChangeListener(this);
        ensurePreview();
    }

    public int getActualItemWidth() {
        ItemInfo info = (ItemInfo) getTag();
        int[] size = getPreviewSize();
        int cellWidth = LauncherAppState.getInstance()
                .getDynamicGrid().getDeviceProfile().cellWidthPx;

        return Math.min(size[0], info.spanX * cellWidth);
    }

    /**
     * Helper method to get the string info of the tag.
     */
    private String getTagToString() {
        if (getTag() instanceof PendingAddWidgetInfo) {
            return ((PendingAddWidgetInfo)getTag()).toString();
        } else if (getTag() instanceof PendingAddShortcutInfo) {
            return ((PendingAddShortcutInfo)getTag()).toString();
        }
        return "";
    }
}
