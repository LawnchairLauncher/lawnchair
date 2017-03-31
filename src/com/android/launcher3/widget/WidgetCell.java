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
import android.graphics.Bitmap;
import android.os.CancellationSignal;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.SimpleOnStylusPressListener;
import com.android.launcher3.StylusEventHelper;
import com.android.launcher3.WidgetPreviewLoader;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.model.WidgetItem;

/**
 * Represents the individual cell of the widget inside the widget tray. The preview is drawn
 * horizontally centered, and scaled down if needed.
 *
 * This view does not support padding. Since the image is scaled down to fit the view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD would need to
 * consider the appropriate scaling factor.
 */
public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {

    private static final String TAG = "WidgetCell";
    private static final boolean DEBUG = false;

    private static final int FADE_IN_DURATION_MS = 90;

    /** Widget cell width is calculated by multiplying this factor to grid cell width. */
    private static final float WIDTH_SCALE = 2.6f;

    /** Widget preview width is calculated by multiplying this factor to the widget cell width. */
    private static final float PREVIEW_SCALE = 0.8f;

    protected int mPresetPreviewSize;
    private int mCellSize;

    private WidgetImageView mWidgetImage;
    private TextView mWidgetName;
    private TextView mWidgetDims;

    protected WidgetItem mItem;

    private WidgetPreviewLoader mWidgetPreviewLoader;
    private StylusEventHelper mStylusEventHelper;

    protected CancellationSignal mActiveRequest;
    private boolean mAnimatePreview = true;

    protected final BaseActivity mActivity;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mActivity = BaseActivity.fromContext(context);
        mStylusEventHelper = new StylusEventHelper(new SimpleOnStylusPressListener(this), this);

        setContainerWidth();
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
    }

    private void setContainerWidth() {
        DeviceProfile profile = mActivity.getDeviceProfile();
        mCellSize = (int) (profile.cellWidthPx * WIDTH_SCALE);
        mPresetPreviewSize = (int) (mCellSize * PREVIEW_SCALE);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWidgetImage = (WidgetImageView) findViewById(R.id.widget_preview);
        mWidgetName = ((TextView) findViewById(R.id.widget_name));
        mWidgetDims = ((TextView) findViewById(R.id.widget_dims));
    }

    /**
     * Called to clear the view and free attached resources. (e.g., {@link Bitmap}
     */
    public void clear() {
        if (DEBUG) {
            Log.d(TAG, "reset called on:" + mWidgetName.getText());
        }
        mWidgetImage.animate().cancel();
        mWidgetImage.setBitmap(null, null);
        mWidgetName.setText(null);
        mWidgetDims.setText(null);

        if (mActiveRequest != null) {
            mActiveRequest.cancel();
            mActiveRequest = null;
        }
    }

    public void applyFromCellItem(WidgetItem item, WidgetPreviewLoader loader) {
        mItem = item;
        mWidgetName.setText(mItem.label);
        mWidgetDims.setText(getContext().getString(R.string.widget_dims_format,
                mItem.spanX, mItem.spanY));
        mWidgetDims.setContentDescription(getContext().getString(
                R.string.widget_accessible_dims_format, mItem.spanX, mItem.spanY));
        mWidgetPreviewLoader = loader;

        if (item.activityInfo != null) {
            setTag(new PendingAddShortcutInfo(item.activityInfo));
        } else {
            setTag(new PendingAddWidgetInfo(item.widgetInfo));
        }
    }

    public WidgetImageView getWidgetView() {
        return mWidgetImage;
    }

    public void setAnimatePreview(boolean shouldAnimate) {
        mAnimatePreview = shouldAnimate;
    }

    public void applyPreview(Bitmap bitmap) {
        applyPreview(bitmap, true);
    }

    public void applyPreview(Bitmap bitmap, boolean animate) {
        if (bitmap != null) {
            mWidgetImage.setBitmap(bitmap,
                    DrawableFactory.get(getContext()).getBadgeForUser(mItem.user, getContext()));
            if (mAnimatePreview) {
                mWidgetImage.setAlpha(0f);
                ViewPropertyAnimator anim = mWidgetImage.animate();
                anim.alpha(1.0f).setDuration(FADE_IN_DURATION_MS);
            } else {
                mWidgetImage.setAlpha(1f);
            }
        }
    }

    public void ensurePreview() {
        ensurePreview(true);
    }

    public void ensurePreview(boolean animate) {
        if (mActiveRequest != null) {
            return;
        }
        mActiveRequest = mWidgetPreviewLoader.getPreview(
                mItem, mPresetPreviewSize, mPresetPreviewSize, this, animate);
    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        removeOnLayoutChangeListener(this);
        ensurePreview();
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean handled = super.onTouchEvent(ev);
        if (mStylusEventHelper.onMotionEvent(ev)) {
            return true;
        }
        return handled;
    }

    /**
     * Helper method to get the string info of the tag.
     */
    private String getTagToString() {
        if (getTag() instanceof PendingAddWidgetInfo ||
                getTag() instanceof PendingAddShortcutInfo) {
            return getTag().toString();
        }
        return "";
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams params) {
        params.width = params.height = mCellSize;
        super.setLayoutParams(params);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WidgetCell.class.getName();
    }
}
