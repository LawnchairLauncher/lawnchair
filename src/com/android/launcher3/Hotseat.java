/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.MultiValueAlpha;

import java.util.function.Consumer;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    private static final int ALPHA_INDEX_STATE = 0;
    private static final int ALPHA_INDEX_REPLACE_TASKBAR = 1;
    private static final int NUM_ALPHA_CHANNELS = 2;

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace mWorkspace;
    private boolean mSendTouchToWorkspace;
    @Nullable
    private Consumer<Boolean> mOnVisibilityAggregatedCallback;

    private final MultiValueAlpha mMultiValueAlpha;
    private final View mQsb;
    private final int mQsbHeight;

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mMultiValueAlpha = new MultiValueAlpha(this, NUM_ALPHA_CHANNELS, MultiValueAlpha.Mode.MAX);
        mMultiValueAlpha.setUpdateVisibility(true);

        mQsb = LayoutInflater.from(context).inflate(R.layout.search_container_hotseat, this, false);
        mQsbHeight = mQsb.getLayoutParams().height;
        addView(mQsb);
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        InvariantDeviceProfile idp = mActivity.getDeviceProfile().inv;
        if (hasVerticalHotseat) {
            setGridSize(1, idp.numHotseatIcons);
        } else {
            setGridSize(idp.numHotseatIcons, FeatureFlags.ENABLE_DEVICE_SEARCH.get() ? 2 : 1);
        }
        showInlineQsb();
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            mQsb.setVisibility(View.VISIBLE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = (grid.isTaskbarPresent
                        ? grid.workspacePadding.bottom
                        : grid.hotseatBarSizePx)
                    + insets.bottom;
        }

        if (!grid.isTaskbarPresent) {
            // When taskbar is present, we set the padding separately to ensure a seamless visual
            // handoff between taskbar and hotseat during drag and drop.
            Rect padding = grid.getHotseatLayoutPadding();
            int paddingBottom = padding.bottom;
            if (FeatureFlags.ENABLE_DEVICE_SEARCH.get() && !grid.isVerticalBarLayout()) {
                paddingBottom -= grid.hotseatBarBottomPaddingPx;
            }
            setPadding(padding.left, padding.top, padding.right, paddingBottom);
        }

        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void setWorkspace(Workspace w) {
        mWorkspace = w;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        return event.getY() > getCellHeight();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);

        if (mOnVisibilityAggregatedCallback != null) {
            mOnVisibilityAggregatedCallback.accept(isVisible);
        }
    }

    /** Sets a callback to be called onVisibilityAggregated */
    public void setOnVisibilityAggregatedCallback(@Nullable Consumer<Boolean> callback) {
        mOnVisibilityAggregatedCallback = callback;
    }

    protected void showInlineQsb() {
        //Does nothing
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = getShortcutsAndWidgets().getMeasuredWidth();
        mQsb.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mQsbHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int qsbWidth = mQsb.getMeasuredWidth();
        int left = (r - l - qsbWidth) / 2;
        int right = left + qsbWidth;

        DeviceProfile dp = mActivity.getDeviceProfile();
        int freeSpace = dp.isTaskbarPresent
                ? dp.workspacePadding.bottom
                : dp.hotseatBarSizePx - dp.hotseatCellHeightPx - mQsbHeight;
        int bottom = b - t
                - (int) (freeSpace * QSB_CENTER_FACTOR)
                - dp.getInsets().bottom;
        int top = bottom - mQsbHeight;
        mQsb.layout(left, top, right, bottom);
    }

    /**
     * Returns the first View for which the given itemOperator returns true, or null.
     */
    public View getFirstItemMatch(Workspace.ItemOperator itemOperator) {
        return mWorkspace.getFirstMatch(new CellLayout[] { this }, itemOperator);
    }

    public MultiValueAlpha.AlphaProperty getStateAlpha() {
        return mMultiValueAlpha.getProperty(ALPHA_INDEX_STATE);
    }

    public MultiValueAlpha.AlphaProperty getReplaceTaskbarAlpha() {
        return mMultiValueAlpha.getProperty(ALPHA_INDEX_REPLACE_TASKBAR);
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }
}
