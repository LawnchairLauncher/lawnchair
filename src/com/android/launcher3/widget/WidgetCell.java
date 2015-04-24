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
 * Represents the individual cell of the widget inside the widget tray.
 */
public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {

    private static final String TAG = "WidgetCell";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 70;
    private int mPresetPreviewSize;

    private ImageView mWidgetImage;
    private TextView mWidgetName;
    private TextView mWidgetDims;
    private final Rect mOriginalImagePadding = new Rect();

    private String mDimensionsFormatString;
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

        mWidgetImage = (ImageView) findViewById(R.id.widget_preview);
        mOriginalImagePadding.left = mWidgetImage.getPaddingLeft();
        mOriginalImagePadding.top = mWidgetImage.getPaddingTop();
        mOriginalImagePadding.right = mWidgetImage.getPaddingRight();
        mOriginalImagePadding.bottom = mWidgetImage.getPaddingBottom();

        // Ensure we are using the right text size
        DeviceProfile profile = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        mWidgetName = ((TextView) findViewById(R.id.widget_name));
        mWidgetDims = ((TextView) findViewById(R.id.widget_dims));
    }

    public void reset() {
        mWidgetImage.setImageDrawable(null);
        mWidgetName.setText(null);
        mWidgetDims.setText(null);
    }

    /**
     * Apply the widget provider info to the view.
     */
    public void applyFromAppWidgetProviderInfo(LauncherAppWidgetProviderInfo info,
            int maxWidth, WidgetPreviewLoader loader) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

        mIsAppWidget = true;
        mInfo = info;
        if (maxWidth > -1) {
            mWidgetImage.setMaxWidth(maxWidth);
        }
        // TODO(hyunyoungs): setup a cache for these labels.
        mWidgetName.setText(AppWidgetManagerCompat.getInstance(getContext()).loadLabel(info));
        int hSpan = Math.min(info.spanX, (int) grid.numColumns);
        int vSpan = Math.min(info.spanY, (int) grid.numRows);
        mWidgetDims.setText(String.format(mDimensionsFormatString, hSpan, vSpan));
        mWidgetPreviewLoader = loader;
    }

    /**
     * Apply the resolve info to the view.
     */
    public void applyFromResolveInfo(
            PackageManager pm, ResolveInfo info, WidgetPreviewLoader loader) {
        mIsAppWidget = false;
        mInfo = info;
        CharSequence label = info.loadLabel(pm);
        mWidgetName.setText(label);
        mWidgetDims.setText(String.format(mDimensionsFormatString, 1, 1));
        mWidgetPreviewLoader = loader;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        deletePreview(false);

        if (DEBUG) {
            Log.d(TAG, String.format("[tag=%s] onDetachedFromWindow", getTagToString()));
        }
    }

    public int[] getPreviewSize() {
        int[] maxSize = new int[2];
        maxSize[0] = mPresetPreviewSize;
        maxSize[1] = mPresetPreviewSize;
        return maxSize;
    }

    public void applyPreview(Bitmap bitmap) {
        FastBitmapDrawable preview = new FastBitmapDrawable(bitmap);
        if (DEBUG) {
            Log.d(TAG, String.format("[tag=%s] applyPreview preview: %s",
                    getTagToString(), preview));
        }
        if (preview != null) {
            mWidgetImage.setImageDrawable(preview);
            if (mIsAppWidget) {
                // center horizontally
                int[] imageSize = getPreviewSize();
                int centerAmount = (imageSize[0] - preview.getIntrinsicWidth()) / 2;
                mWidgetImage.setPadding(mOriginalImagePadding.left + centerAmount,
                        mOriginalImagePadding.top,
                        mOriginalImagePadding.right,
                        mOriginalImagePadding.bottom);
            }
            mWidgetImage.setAlpha(0f);
            mWidgetImage.animate().alpha(1.0f).setDuration(FADE_IN_DURATION_MS);
            // TODO(hyunyoungs): figure out why this has to be called explicitly.
            mWidgetImage.requestLayout();
        }
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


    private void deletePreview(boolean recycleImage) {
        mWidgetImage.setImageDrawable(null);

        if (mActiveRequest != null) {
            mActiveRequest.cancel(recycleImage);
            mActiveRequest = null;
        }
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
