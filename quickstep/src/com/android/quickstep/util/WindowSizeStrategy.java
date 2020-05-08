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
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;
import static com.android.quickstep.util.LayoutUtils.getDefaultSwipeHeight;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;

/**
 * Utility class to wrap different layout behavior for Launcher and RecentsView
 * TODO: Merge is with {@link com.android.quickstep.BaseActivityInterface} once we remove the
 * state dependent members from {@link com.android.quickstep.LauncherActivityInterface}
 */
public abstract class WindowSizeStrategy {

    private final PointF mTempPoint = new PointF();
    public final boolean rotationSupportedByActivity;

    private WindowSizeStrategy(boolean rotationSupportedByActivity) {
        this.rotationSupportedByActivity = rotationSupportedByActivity;
    }

    /**
     * Sets the expected window size in multi-window mode
     */
    public abstract void getMultiWindowSize(Context context, DeviceProfile dp, PointF out);

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


    public static final WindowSizeStrategy LAUNCHER_ACTIVITY_SIZE_STRATEGY =
            new WindowSizeStrategy(true) {

        @Override
        public void getMultiWindowSize(Context context, DeviceProfile dp, PointF out) {
            DeviceProfile fullDp = dp.getFullScreenProfile();
            // Use availableWidthPx and availableHeightPx instead of widthPx and heightPx to
            // account for system insets
            out.set(fullDp.availableWidthPx, fullDp.availableHeightPx);
            float halfDividerSize = context.getResources()
                    .getDimension(R.dimen.multi_window_task_divider_size) / 2;

            if (fullDp.isLandscape) {
                out.x = out.x / 2 - halfDividerSize;
            } else {
                out.y = out.y / 2 - halfDividerSize;
            }
        }

        @Override
        float getExtraSpace(Context context, DeviceProfile dp) {
            if (dp.isVerticalBarLayout()) {
                return  0;
            } else {
                Resources res = context.getResources();
                if (showOverviewActions(context)) {
                    //TODO: this needs to account for the swipe gesture height and accessibility
                    // UI when shown.
                    return res.getDimensionPixelSize(R.dimen.overview_actions_height);
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
        public void getMultiWindowSize(Context context, DeviceProfile dp, PointF out) {
            out.set(dp.widthPx, dp.heightPx);
        }

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
