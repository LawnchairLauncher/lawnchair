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
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.NavigationMode;
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
            HIDDEN_SPLIT_SCREEN,
            HIDDEN_SPLIT_SELECT_ACTIVE,
            HIDDEN_ACTIONS_IN_MENU,
            HIDDEN_DESKTOP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionsHiddenFlags { }

    public static final int HIDDEN_NON_ZERO_ROTATION = 1 << 0;
    public static final int HIDDEN_NO_TASKS = 1 << 1;
    public static final int HIDDEN_NO_RECENTS = 1 << 2;
    public static final int HIDDEN_SPLIT_SCREEN = 1 << 3;
    public static final int HIDDEN_SPLIT_SELECT_ACTIVE = 1 << 4;
    public static final int HIDDEN_ACTIONS_IN_MENU = 1 << 5;
    public static final int HIDDEN_DESKTOP = 1 << 6;

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
    private static final int INDEX_SHARE_TARGET_ALPHA = 4;
    private static final int INDEX_SCROLL_ALPHA = 5;
    private static final int NUM_ALPHAS = 6;

    public @interface SplitButtonHiddenFlags { }
    public static final int FLAG_IS_NOT_TABLET = 1 << 0;

    public @interface SplitButtonDisabledFlags { }
    public static final int FLAG_SINGLE_TASK = 1 << 0;

    private MultiValueAlpha mMultiValueAlpha;
    private Button mSplitButton;

    @ActionsHiddenFlags
    private int mHiddenFlags;

    @ActionsDisabledFlags
    protected int mDisabledFlags;

    @SplitButtonHiddenFlags
    private int mSplitButtonHiddenFlags;

    @SplitButtonDisabledFlags
    private int mSplitButtonDisabledFlags;

    @Nullable
    protected T mCallbacks;

    @Nullable
    protected DeviceProfile mDp;
    private final Rect mTaskSize = new Rect();

    public OverviewActionsView(Context context) {
        this(context, null);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, 0);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mMultiValueAlpha = new MultiValueAlpha(findViewById(R.id.action_buttons), NUM_ALPHAS);
        mMultiValueAlpha.setUpdateVisibility(true);

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
        updateVerticalMargin(DisplayController.getNavigationMode(getContext()));
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateVerticalMargin(DisplayController.getNavigationMode(getContext()));
        updatePadding();
    }

    public void updateHiddenFlags(@ActionsHiddenFlags int visibilityFlags, boolean enable) {
        if (enable) {
            mHiddenFlags |= visibilityFlags;
        } else {
            mHiddenFlags &= ~visibilityFlags;
        }
        boolean isHidden = mHiddenFlags != 0;
        mMultiValueAlpha.get(INDEX_HIDDEN_FLAGS_ALPHA).setValue(isHidden ? 0 : 1);
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
        updateSplitButtonEnabledState();
    }

    /**
     * Updates the proper flags to indicate whether the "Split screen" button should be hidden.
     *
     * @param flag   The flag to update.
     * @param enable Whether to enable the hidden flag: True will cause view to be hidden.
     */
    public void updateSplitButtonHiddenFlags(@SplitButtonHiddenFlags int flag, boolean enable) {
        if (enable) {
            mSplitButtonHiddenFlags |= flag;
        } else {
            mSplitButtonHiddenFlags &= ~flag;
        }
        if (mSplitButton == null) return;
        boolean shouldBeVisible = mSplitButtonHiddenFlags == 0;
        mSplitButton.setVisibility(shouldBeVisible ? VISIBLE : GONE);
        findViewById(R.id.action_split_space).setVisibility(shouldBeVisible ? VISIBLE : GONE);
    }

    /**
     * Updates the proper flags to indicate whether the "Split screen" button should be disabled.
     *
     * @param flag   The flag to update.
     * @param enable Whether to enable the disable flag: True will cause view to be disabled.
     */
    public void updateSplitButtonDisabledFlags(@SplitButtonDisabledFlags int flag, boolean enable) {
        if (enable) {
            mSplitButtonDisabledFlags |= flag;
        } else {
            mSplitButtonDisabledFlags &= ~flag;
        }
        updateSplitButtonEnabledState();
    }

    public MultiProperty getContentAlpha() {
        return mMultiValueAlpha.get(INDEX_CONTENT_ALPHA);
    }

    public MultiProperty getVisibilityAlpha() {
        return mMultiValueAlpha.get(INDEX_VISIBILITY_ALPHA);
    }

    public MultiProperty getFullscreenAlpha() {
        return mMultiValueAlpha.get(INDEX_FULLSCREEN_ALPHA);
    }

    public MultiProperty getShareTargetAlpha() {
        return mMultiValueAlpha.get(INDEX_SHARE_TARGET_ALPHA);
    }

    public MultiProperty getIndexScrollAlpha() {
        return mMultiValueAlpha.get(INDEX_SCROLL_ALPHA);
    }

    /**
     * Returns the visibility of the overview actions buttons.
     */
    public @Visibility int getActionsButtonVisibility() {
        return findViewById(R.id.action_buttons).getVisibility();
    }

    /**
     * Offsets OverviewActionsView horizontal position based on 3 button nav container in taskbar.
     */
    private void updatePadding() {
        // If taskbar is in overview, overview action has dedicated space above nav buttons
        setPadding(mInsets.left, 0, mInsets.right, 0);
    }

    /** Updates vertical margins for different navigation mode or configuration changes. */
    public void updateVerticalMargin(NavigationMode mode) {
        if (mDp == null) {
            return;
        }
        LayoutParams actionParams = (LayoutParams) findViewById(
                R.id.action_buttons).getLayoutParams();
        actionParams.setMargins(
                actionParams.leftMargin, mDp.overviewActionsTopMarginPx,
                actionParams.rightMargin, getBottomMargin());
    }

    private int getBottomMargin() {
        if (mDp == null) {
            return 0;
        }

        if (mDp.isTablet && FeatureFlags.ENABLE_GRID_ONLY_OVERVIEW.get()) {
            return mDp.stashedTaskbarHeight;
        }

        // Align to bottom of task Rect.
        return mDp.heightPx - mTaskSize.bottom - mDp.overviewActionsTopMarginPx
                - mDp.overviewActionsHeight;
    }

    /**
     * Updates device profile and task size for this view to draw with.
     */
    public void updateDimension(DeviceProfile dp, Rect taskSize) {
        mDp = dp;
        mTaskSize.set(taskSize);
        updateVerticalMargin(DisplayController.getNavigationMode(getContext()));

        requestLayout();

        mSplitButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                (dp.isLandscape ? R.drawable.ic_split_horizontal : R.drawable.ic_split_vertical),
                0, 0, 0);
    }

    /**
     * Enables/disables the "Split" button based on the status of mSplitButtonDisabledFlags and
     * mDisabledFlags.
     */
    private void updateSplitButtonEnabledState() {
        if (mSplitButton == null) {
            return;
        }
        boolean isParentEnabled = (mDisabledFlags & ~DISABLED_ROTATED) == 0;
        boolean shouldBeEnabled = mSplitButtonDisabledFlags == 0 && isParentEnabled;
        mSplitButton.setEnabled(shouldBeEnabled);
    }

}
