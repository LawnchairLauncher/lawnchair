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
package com.android.quickstep.util;

import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.SysUINavigationMode.getMode;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.quickstep.util.LayoutUtils.getDefaultSwipeHeight;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.util.WindowBounds;
import com.android.quickstep.SysUINavigationMode.Mode;

/**
 * Utility class to wrap different layout behavior for Launcher and RecentsView
 * TODO: Merge is with {@link com.android.quickstep.BaseActivityInterface} once we remove the
 * state dependent members from {@link com.android.quickstep.LauncherActivityInterface}
 */
@TargetApi(Build.VERSION_CODES.R)
public abstract class WindowSizeStrategy {

    public final boolean rotationSupportedByActivity;

    private WindowSizeStrategy(boolean rotationSupportedByActivity) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
    }

    /**
     * Calculates the taskView size for the provided device configuration
     */
    public final void calculateTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        calculateTaskSize(context, dp, getExtraSpace(context, dp), outRect);
    }

    abstract float getExtraSpace(Context context, DeviceProfile dp);

    private void calculateTaskSize(
            Context context, DeviceProfile dp, float extraVerticalSpace, Rect outRect) {
        float taskWidth, taskHeight, paddingHorz;
        Resources res = context.getResources();
        Rect insets = dp.getInsets();
        final boolean showLargeTaskSize = showOverviewActions(context);

        if (dp.isMultiWindowMode) {
            WindowBounds bounds = SplitScreenBounds.INSTANCE.getSecondaryWindowBounds(context);
            taskWidth = bounds.availableSize.x;
            taskHeight = bounds.availableSize.y;
            paddingHorz = res.getDimension(R.dimen.multi_window_task_card_horz_space);
        } else {
            taskWidth = dp.availableWidthPx;
            taskHeight = dp.availableHeightPx;

            final int paddingResId;
            if (dp.isVerticalBarLayout()) {
                paddingResId = R.dimen.landscape_task_card_horz_space;
            } else if (showLargeTaskSize) {
                paddingResId = R.dimen.portrait_task_card_horz_space_big_overview;
            } else {
                paddingResId = R.dimen.portrait_task_card_horz_space;
            }
            paddingHorz = res.getDimension(paddingResId);
        }

        float topIconMargin = res.getDimension(R.dimen.task_thumbnail_top_margin);
        float paddingVert = showLargeTaskSize
                ? 0 : res.getDimension(R.dimen.task_card_vert_space);

        // Note this should be same as dp.availableWidthPx and dp.availableHeightPx unless
        // we override the insets ourselves.
        int launcherVisibleWidth = dp.widthPx - insets.left - insets.right;
        int launcherVisibleHeight = dp.heightPx - insets.top - insets.bottom;

        float availableHeight = launcherVisibleHeight
                - topIconMargin - extraVerticalSpace - paddingVert;
        float availableWidth = launcherVisibleWidth - paddingHorz;

        float scale = Math.min(availableWidth / taskWidth, availableHeight / taskHeight);
        float outWidth = scale * taskWidth;
        float outHeight = scale * taskHeight;

        // Center in the visible space
        float x = insets.left + (launcherVisibleWidth - outWidth) / 2;
        float y = insets.top + Math.max(topIconMargin,
                (launcherVisibleHeight - extraVerticalSpace - outHeight) / 2);
        outRect.set(Math.round(x), Math.round(y),
                Math.round(x) + Math.round(outWidth), Math.round(y) + Math.round(outHeight));
    }

    /**
     * Calculates the modal taskView size for the provided device configuration
     */
    public void calculateModalTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        float taskWidth, taskHeight, paddingHorz;
        Resources res = context.getResources();
        Rect insets = dp.getInsets();

        if (dp.isMultiWindowMode) {
            getMultiWindowSize(context, dp, mTempPoint);
            taskWidth = mTempPoint.x;
            taskHeight = mTempPoint.y;
            paddingHorz = res.getDimension(R.dimen.multi_window_task_card_horz_space);
        } else {
            taskWidth = dp.availableWidthPx;
            taskHeight = dp.availableHeightPx;

            final int paddingResId;
            if (dp.isVerticalBarLayout()) {
                paddingResId = R.dimen.landscape_task_card_horz_space;
            } else {
                paddingResId = R.dimen.portrait_modal_task_card_horz_space;
            }
            paddingHorz = res.getDimension(paddingResId);
        }

        // Note this should be same as dp.availableWidthPx and dp.availableHeightPx unless
        // we override the insets ourselves.
        int launcherVisibleWidth = dp.widthPx - insets.left - insets.right;
        int launcherVisibleHeight = dp.heightPx - insets.top - insets.bottom;

        // Calculate for the overview height.
        float overviewActionsHeight = getOverviewActionsHeight(context);
        float availableHeight = launcherVisibleHeight - overviewActionsHeight;
        float availableWidth = launcherVisibleWidth - paddingHorz;

        float scale = Math.min(availableWidth / taskWidth, availableHeight / taskHeight);
        float outWidth = scale * taskWidth;
        float outHeight = scale * taskHeight;

        // Center in the visible space
        float x = insets.left + (launcherVisibleWidth - outWidth) / 2;
        float y = insets.top + (launcherVisibleHeight - overviewActionsHeight - outHeight) / 2;
        outRect.set(Math.round(x), Math.round(y),
                Math.round(x) + Math.round(outWidth), Math.round(y) + Math.round(outHeight));
    }

    /** Gets the space that the overview actions will take, including margins. */
    public float getOverviewActionsHeight(Context context) {
        Resources res = context.getResources();
        float actionsBottomMargin = 0;
        if (getMode(context) == Mode.THREE_BUTTONS) {
            actionsBottomMargin = res.getDimensionPixelSize(
                R.dimen.overview_actions_bottom_margin_three_button);
        } else {
            actionsBottomMargin = res.getDimensionPixelSize(
                R.dimen.overview_actions_bottom_margin_gesture);
        }
        float overviewActionsHeight = actionsBottomMargin
                + res.getDimensionPixelSize(R.dimen.overview_actions_height);
        return overviewActionsHeight;
    }

    public static final WindowSizeStrategy LAUNCHER_ACTIVITY_SIZE_STRATEGY =
            new WindowSizeStrategy(true) {

        @Override
        float getExtraSpace(Context context, DeviceProfile dp) {
            if (dp.isVerticalBarLayout()) {
                return  0;
            } else {
                Resources res = context.getResources();
                if (showOverviewActions(context)) {
                    //TODO: this needs to account for the swipe gesture height and accessibility
                    // UI when shown.
                    return getOverviewActionsHeight(context);
                } else {
                    return getDefaultSwipeHeight(context, dp) + dp.workspacePageIndicatorHeight
                            + res.getDimensionPixelSize(
                                    R.dimen.dynamic_grid_hotseat_extra_vertical_size)
                            + res.getDimensionPixelSize(
                                    R.dimen.dynamic_grid_hotseat_bottom_padding);
                }
            }
        }
    };

    public static final WindowSizeStrategy FALLBACK_RECENTS_SIZE_STRATEGY =
            new WindowSizeStrategy(false) {

        @Override
        float getExtraSpace(Context context, DeviceProfile dp) {
            return showOverviewActions(context)
                    ? context.getResources().getDimensionPixelSize(R.dimen.overview_actions_height)
                    : 0;
        }
    };

    static boolean showOverviewActions(Context context) {
        return ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(context);
    }
}
