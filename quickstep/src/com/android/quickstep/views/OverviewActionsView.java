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
import android.widget.LinearLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
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

    public @interface ScreenshotButtonHiddenFlags { }
    public static final int FLAG_MULTIPLE_TASKS_HIDE_SCREENSHOT = 1 << 0;

    public @interface SplitButtonHiddenFlags { }
    public static final int FLAG_SMALL_SCREEN_HIDE_SPLIT = 1 << 0;
    public static final int FLAG_MULTIPLE_TASKS_HIDE_SPLIT = 1 << 1;

    public @interface SplitButtonDisabledFlags { }
    public static final int FLAG_SINGLE_TASK_DISABLE_SPLIT = 1 << 0;

    public @interface AppPairButtonHiddenFlags { }
    public static final int FLAG_SINGLE_TASK_HIDE_APP_PAIR = 1 << 0;
    public static final int FLAG_SMALL_SCREEN_HIDE_APP_PAIR = 1 << 1;
    public static final int FLAG_3P_LAUNCHER_HIDE_APP_PAIR = 1 << 2;

    private MultiValueAlpha mMultiValueAlpha;

    protected LinearLayout mActionButtons;
    // The screenshot button is implemented as a Button in launcher3 and NexusLauncher, but is an
    // ImageButton in go launcher (does not share a common class with Button). Take care when
    // casting this.
    private View mScreenshotButton;
    private Button mSplitButton;
    private Button mSaveAppPairButton;

    @ActionsHiddenFlags
    private int mHiddenFlags;

    @ActionsDisabledFlags
    protected int mDisabledFlags;

    @ScreenshotButtonHiddenFlags
    private int mScreenshotButtonHiddenFlags;

    @SplitButtonHiddenFlags
    private int mSplitButtonHiddenFlags;

    @AppPairButtonHiddenFlags
    private int mAppPairButtonHiddenFlags;

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
        mActionButtons = findViewById(R.id.action_buttons);
        mMultiValueAlpha = new MultiValueAlpha(mActionButtons, NUM_ALPHAS);
        mMultiValueAlpha.setUpdateVisibility(true);

        mScreenshotButton = findViewById(R.id.action_screenshot);
        mScreenshotButton.setOnClickListener(this);
        mSplitButton = findViewById(R.id.action_split);
        mSplitButton.setOnClickListener(this);
        mSaveAppPairButton = findViewById(R.id.action_save_app_pair);
        mSaveAppPairButton.setOnClickListener(this);
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
        } else if (id == R.id.action_save_app_pair) {
            mCallbacks.onSaveAppPair();
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
    }

    /**
     * Updates a batch of flags to hide and show actions buttons when a grouped task (split screen)
     * is focused.
     * @param isGroupedTask True if the focused task is a grouped task.
     */
    public void updateForGroupedTask(boolean isGroupedTask) {
        // Update flags to see if split button should be hidden.
        updateSplitButtonHiddenFlags(FLAG_MULTIPLE_TASKS_HIDE_SPLIT, isGroupedTask);
        // Update flags to see if screenshot button should be hidden.
        updateScreenshotButtonHiddenFlags(FLAG_MULTIPLE_TASKS_HIDE_SCREENSHOT, isGroupedTask);
        // Update flags to see if save app pair button should be hidden.
        updateAppPairButtonHiddenFlags(FLAG_SINGLE_TASK_HIDE_APP_PAIR, !isGroupedTask);
    }

    /**
     * Updates a batch of flags to hide and show actions buttons for tablet/non tablet case.
     */
    private void updateForIsTablet() {
        assert mDp != null;
        // Update flags to see if split button should be hidden.
        updateSplitButtonHiddenFlags(FLAG_SMALL_SCREEN_HIDE_SPLIT, !mDp.isTablet);
        // Update flags to see if save app pair button should be hidden.
        updateAppPairButtonHiddenFlags(FLAG_SMALL_SCREEN_HIDE_APP_PAIR, !mDp.isTablet);
    }

    /**
     * Updates flags to hide and show actions buttons for 1p/3p launchers.
     */
    public void updateFor3pLauncher(boolean is3pLauncher) {
        updateAppPairButtonHiddenFlags(FLAG_3P_LAUNCHER_HIDE_APP_PAIR, is3pLauncher);
    }

    /**
     * Updates the proper flags to indicate whether the "Screenshot" button should be hidden.
     *
     * @param flag   The flag to update.
     * @param enable Whether to enable the hidden flag: True will cause view to be hidden.
     */
    private void updateScreenshotButtonHiddenFlags(@ScreenshotButtonHiddenFlags int flag,
            boolean enable) {
        if (mScreenshotButton == null) return;
        if (enable) {
            mScreenshotButtonHiddenFlags |= flag;
        } else {
            mScreenshotButtonHiddenFlags &= ~flag;
        }
        int desiredVisibility = mScreenshotButtonHiddenFlags == 0 ? VISIBLE : GONE;
        if (mScreenshotButton.getVisibility() != desiredVisibility) {
            mScreenshotButton.setVisibility(desiredVisibility);
            mActionButtons.requestLayout();
        }
    }

    /**
     * Updates the proper flags to indicate whether the "Split screen" button should be hidden.
     *
     * @param flag   The flag to update.
     * @param enable Whether to enable the hidden flag: True will cause view to be hidden.
     */
    void updateSplitButtonHiddenFlags(@SplitButtonHiddenFlags int flag,
            boolean enable) {
        if (mSplitButton == null) return;
        if (enable) {
            mSplitButtonHiddenFlags |= flag;
        } else {
            mSplitButtonHiddenFlags &= ~flag;
        }
        int desiredVisibility = mSplitButtonHiddenFlags == 0 ? VISIBLE : GONE;
        if (mSplitButton.getVisibility() != desiredVisibility) {
            mSplitButton.setVisibility(desiredVisibility);
            mActionButtons.requestLayout();
        }
    }

    /**
     * Updates the proper flags to indicate whether the "Save app pair" button should be disabled.
     *
     * @param flag   The flag to update.
     * @param enable Whether to enable the hidden flag: True will cause view to be hidden.
     */
    private void updateAppPairButtonHiddenFlags(
            @AppPairButtonHiddenFlags int flag, boolean enable) {
        if (!FeatureFlags.enableAppPairs()) {
            return;
        }

        if (mSaveAppPairButton == null) return;
        if (enable) {
            mAppPairButtonHiddenFlags |= flag;
        } else {
            mAppPairButtonHiddenFlags &= ~flag;
        }
        int desiredVisibility = mAppPairButtonHiddenFlags == 0 ? VISIBLE : GONE;
        if (mSaveAppPairButton.getVisibility() != desiredVisibility) {
            mSaveAppPairButton.setVisibility(desiredVisibility);
            mActionButtons.requestLayout();
        }
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
        return mActionButtons.getVisibility();
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
        LayoutParams actionParams = (LayoutParams) mActionButtons.getLayoutParams();
        actionParams.setMargins(
                actionParams.leftMargin, mDp.overviewActionsTopMarginPx,
                actionParams.rightMargin, getBottomMargin());
    }

    private int getBottomMargin() {
        if (mDp == null) {
            return 0;
        }

        if (mDp.isTablet && Flags.enableGridOnlyOverview()) {
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
        updateForIsTablet();

        requestLayout();

        int splitIconRes = dp.isLeftRightSplit
                ? R.drawable.ic_split_horizontal
                : R.drawable.ic_split_vertical;
        mSplitButton.setCompoundDrawablesRelativeWithIntrinsicBounds(splitIconRes, 0, 0, 0);

        int appPairIconRes = dp.isLeftRightSplit
                ? R.drawable.ic_save_app_pair_left_right
                : R.drawable.ic_save_app_pair_up_down;
        mSaveAppPairButton.setCompoundDrawablesRelativeWithIntrinsicBounds(
                appPairIconRes, 0, 0, 0);
    }
}
