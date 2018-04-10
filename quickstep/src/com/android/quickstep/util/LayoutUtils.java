/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.RectF;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;

public class LayoutUtils {

    public static void calculateLauncherTaskSize(Context context, DeviceProfile dp, Rect outRect) {
        float extraSpace = dp.isVerticalBarLayout() ? 0 : dp.hotseatBarSizePx;
        calculateTaskSize(context, dp, extraSpace, outRect);
    }

    public static void calculateTaskSize(Context context, DeviceProfile dp,
            float extraVerticalSpace, Rect outRect) {
        float taskWidth, taskHeight, paddingHorz;
        Resources res = context.getResources();
        Rect insets = dp.getInsets();

        if (dp.isMultiWindowMode) {
            DeviceProfile fullDp = dp.getFullScreenProfile();
            // Use availableWidthPx and availableHeightPx instead of widthPx and heightPx to
            // account for system insets
            taskWidth = fullDp.availableWidthPx;
            taskHeight = fullDp.availableHeightPx;
            float halfDividerSize = res.getDimension(R.dimen.multi_window_task_divider_size) / 2;

            if (fullDp.isLandscape) {
                taskWidth = taskWidth / 2 - halfDividerSize;
            } else {
                taskHeight = taskHeight / 2 - halfDividerSize;
            }
            paddingHorz = res.getDimension(R.dimen.multi_window_task_card_horz_space);
        } else {
            taskWidth = dp.availableWidthPx;
            taskHeight = dp.availableHeightPx;
            paddingHorz = res.getDimension(dp.isVerticalBarLayout()
                    ? R.dimen.landscape_task_card_horz_space
                    : R.dimen.portrait_task_card_horz_space);
        }

        float topIconMargin = res.getDimension(R.dimen.task_thumbnail_top_margin);
        float paddingVert = res.getDimension(R.dimen.task_card_vert_space);

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
        float x = insets.left + (taskWidth - outWidth) / 2;
        float y = insets.top + Math.max(topIconMargin,
                (launcherVisibleHeight - extraVerticalSpace - outHeight) / 2);
        outRect.set(Math.round(x), Math.round(y),
                Math.round(x + outWidth), Math.round(y + outHeight));
    }
}
