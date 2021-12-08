/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.quickstep.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.TaskOverlayFactory.OverlayUICallbacks;
import com.android.quickstep.util.LayoutUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * View for showing action buttons in Overview
 */
public class OverviewActionsView<T extends OverlayUICallbacks> extends FrameLayout
        implements OnClickListener, Insettable {

    private final Rect mInsets = new Rect();

    @IntDef(flag = true, value = {
            HIDDEN_NON_ZERO_ROTATION,
            HIDDEN_NO_TASKS,
            HIDDEN_NO_RECENTS,
            HIDDEN_FOCUSED_SCROLL,
            HIDDEN_SPLIT_SCREEN})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionsHiddenFlags { }

    public static final int HIDDEN_NON_ZERO_ROTATION = 1 << 0;
    public static final int HIDDEN_NO_TASKS = 1 << 1;
    public static final int HIDDEN_NO_RECENTS = 1 << 2;
    public static final int HIDDEN_FOCUSED_SCROLL = 1 << 3;
    public static final int HIDDEN_SPLIT_SCREEN = 1 << 4;

    @IntDef(flag = true, value = {
            DISABLED_SCROLLING,
            DISABLED_ROTATED,
            DISABLED_NO_THUMBNAIL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionsDisabledFlags { }

    public static final int DISABLED_SCROLLING = 1 << 0;
    public static final int DISABLED_ROTATED = 1 << 1;
    public static final int DISABLED_NO_THUMBNAIL = 1 << 2;

    private static final int INDEX_CONTENT_ALPHA = 0;
    private static final int INDEX_VISIBILITY_ALPHA = 1;
    private static final int INDEX_FULLSCREEN_ALPHA = 2;
    private static final int INDEX_HIDDEN_FLAGS_ALPHA = 3;

    private final MultiValueAlpha mMultiValueAlpha;
    private Button mSplitButton;

    @ActionsHiddenFlags
    private int mHiddenFlags;

    @ActionsDisabledFlags
    protected int mDisabledFlags;

    @Nullable
    protected T mCallbacks;

    @Nullable
    protected DeviceProfile mDp;

    public OverviewActionsView(Context context) {
        this(context, null);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, 0);
        mMultiValueAlpha = new MultiValueAlpha(this, 5);
        mMultiValueAlpha.setUpdateVisibility(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.action_screenshot).setOnClickListener(this);

        mSplitButton = findViewById(R.id.action_split);
        mSplitButton.setOnClickListener(this);
    }

    /**
     * Set listener for callbacks on action button taps.
     *
     * @param callbacks for callbacks, or {@code null} to clear the listener.
     */
    public void setCallbacks(T callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    public void onClick(View view) {
        if (mCallbacks == null) {
            return;
        }
        int id = view.getId();
        if (id == R.id.action_screenshot) {
            mCallbacks.onScreenshot();
        } else if (id == R.id.action_split) {
            mCallbacks.onSplit();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateVerticalMargin(SysUINavigationMode.getMode(getContext()));
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateVerticalMargin(SysUINavigationMode.getMode(getContext()));
        updateHorizontalPadding();
    }

    public void updateHiddenFlags(@ActionsHiddenFlags int visibilityFlags, boolean enable) {
        if (enable) {
            mHiddenFlags |= visibilityFlags;
        } else {
            mHiddenFlags &= ~visibilityFlags;
        }
        boolean isHidden = mHiddenFlags != 0;
        mMultiValueAlpha.getProperty(INDEX_HIDDEN_FLAGS_ALPHA).setValue(isHidden ? 0 : 1);
    }

    /**
     * Updates the proper disabled flag to indicate whether OverviewActionsView should be enabled.
     * Ignores DISABLED_ROTATED flag for determining enabled. Flag is used to enable/disable
     * buttons individually, currently done for select button in subclass.
     *
     * @param disabledFlags The flag to update.
     * @param enable        Whether to enable the disable flag: True will cause view to be disabled.
     */
    public void updateDisabledFlags(@ActionsDisabledFlags int disabledFlags, boolean enable) {
        if (enable) {
            mDisabledFlags |= disabledFlags;
        } else {
            mDisabledFlags &= ~disabledFlags;
        }
        boolean isEnabled = (mDisabledFlags & ~DISABLED_ROTATED) == 0;
        LayoutUtils.setViewEnabled(this, isEnabled);
    }

    public AlphaProperty getContentAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_CONTENT_ALPHA);
    }

    public AlphaProperty getVisibilityAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_VISIBILITY_ALPHA);
    }

    public AlphaProperty getFullscreenAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_FULLSCREEN_ALPHA);
    }

    private void updateHorizontalPadding() {
        setPadding(mInsets.left, 0, mInsets.right, 0);
    }

    /** Updates vertical margins for different navigation mode or configuration changes. */
    public void updateVerticalMargin(Mode mode) {
        if (mDp == null) {
            return;
        }
        LayoutParams actionParams = (LayoutParams) findViewById(
                R.id.action_buttons).getLayoutParams();
        actionParams.setMargins(
                actionParams.leftMargin, getOverviewActionsTopMarginPx(mode, mDp),
                actionParams.rightMargin, getOverviewActionsBottomMarginPx(mode, mDp));
    }

    /**
     * Set the device profile for this view to draw with.
     */
    public void setDp(DeviceProfile dp) {
        mDp = dp;
        updateVerticalMargin(SysUINavigationMode.getMode(getContext()));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp.isVerticalBarLayout() ? 0 : dp.overviewActionsButtonSpacing,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.weight = dp.isVerticalBarLayout() ? 1 : 0;
        findViewById(R.id.action_split_space).setLayoutParams(params);

        requestLayout();

        mSplitButton.setCompoundDrawablesWithIntrinsicBounds(
                (dp.isLandscape ? R.drawable.ic_split_horizontal : R.drawable.ic_split_vertical),
                0, 0, 0);
    }

    public void setSplitButtonVisible(boolean visible) {
        if (mSplitButton == null) {
            return;
        }

        mSplitButton.setVisibility(visible ? VISIBLE : GONE);
        findViewById(R.id.action_split_space).setVisibility(visible ? VISIBLE : GONE);
    }

    /** Get the top margin associated with the action buttons in Overview. */
    public static int getOverviewActionsTopMarginPx(
            SysUINavigationMode.Mode mode, DeviceProfile dp) {
        // In vertical bar, use the smaller task margin for the top regardless of mode
        if (dp.isVerticalBarLayout()) {
            return dp.overviewTaskMarginPx;
        }

        if (mode == SysUINavigationMode.Mode.THREE_BUTTONS) {
            return dp.overviewActionsMarginThreeButtonPx;
        }

        return dp.overviewActionsTopMarginGesturePx;
    }

    /** Get the bottom margin associated with the action buttons in Overview. */
    public static int getOverviewActionsBottomMarginPx(
            SysUINavigationMode.Mode mode, DeviceProfile dp) {
        int inset = dp.getInsets().bottom;

        if (dp.isVerticalBarLayout()) {
            return inset;
        }

        if (mode == SysUINavigationMode.Mode.THREE_BUTTONS) {
            return dp.overviewActionsMarginThreeButtonPx + inset;
        }

        return dp.overviewActionsBottomMarginGesturePx + inset;
    }
}
