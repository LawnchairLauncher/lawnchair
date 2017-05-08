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

package ch.deletescape.lawnchair.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewPropertyAnimator;
import android.widget.LinearLayout;
import android.widget.TextView;

import ch.deletescape.lawnchair.DeviceProfile;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.SimpleOnStylusPressListener;
import ch.deletescape.lawnchair.StylusEventHelper;
import ch.deletescape.lawnchair.WidgetPreviewLoader;
import ch.deletescape.lawnchair.WidgetPreviewLoader.PreviewLoadRequest;
import ch.deletescape.lawnchair.model.WidgetItem;

/**
 * Represents the individual cell of the widget inside the widget tray. The preview is drawn
 * horizontally centered, and scaled down if needed.
 * <p>
 * This view does not support padding. Since the image is scaled down to fit the view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD would need to
 * consider the appropriate scaling factor.
 */
public class WidgetCell extends LinearLayout implements OnLayoutChangeListener {

    private static final int FADE_IN_DURATION_MS = 90;

    /**
     * Widget cell width is calculated by multiplying this factor to grid cell width.
     */
    private static final float WIDTH_SCALE = 2.6f;

    /**
     * Widget preview width is calculated by multiplying this factor to the widget cell width.
     */
    private static final float PREVIEW_SCALE = 0.8f;

    private int mPresetPreviewSize;
    int cellSize;

    private WidgetImageView mWidgetImage;
    private TextView mWidgetName;
    private TextView mWidgetDims;

    private WidgetItem mItem;

    private WidgetPreviewLoader mWidgetPreviewLoader;
    private PreviewLoadRequest mActiveRequest;
    private StylusEventHelper mStylusEventHelper;

    private final Launcher mLauncher;

    public WidgetCell(Context context) {
        this(context, null);
    }

    public WidgetCell(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetCell(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mLauncher = Launcher.getLauncher(context);
        mStylusEventHelper = new StylusEventHelper(new SimpleOnStylusPressListener(this), this);

        setContainerWidth();
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(mLauncher.getAccessibilityDelegate());
    }

    private void setContainerWidth() {
        DeviceProfile profile = mLauncher.getDeviceProfile();
        cellSize = (int) (profile.cellWidthPx * WIDTH_SCALE);
        mPresetPreviewSize = (int) (cellSize * PREVIEW_SCALE);
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
        mWidgetImage.animate().cancel();
        mWidgetImage.setBitmap(null);
        mWidgetName.setText(null);
        mWidgetDims.setText(null);

        if (mActiveRequest != null) {
            mActiveRequest.cleanup();
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
            setTag(new PendingAddWidgetInfo(mLauncher, item.widgetInfo));
        }
    }

    public int[] getPreviewSize() {
        int[] maxSize = new int[2];

        maxSize[0] = mPresetPreviewSize;
        maxSize[1] = mPresetPreviewSize;
        return maxSize;
    }

    public void applyPreview(Bitmap bitmap) {
        if (bitmap != null) {
            mWidgetImage.setBitmap(bitmap);
            mWidgetImage.setAlpha(0f);
            ViewPropertyAnimator anim = mWidgetImage.animate();
            anim.alpha(1.0f).setDuration(FADE_IN_DURATION_MS);
        }
    }

    public void ensurePreview() {
        if (mActiveRequest != null) {
            return;
        }
        int[] size = getPreviewSize();
        mActiveRequest = mWidgetPreviewLoader.getPreview(mItem, size[0], size[1], this);
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
        return mStylusEventHelper.onMotionEvent(ev) || handled;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return WidgetCell.class.getName();
    }
}
