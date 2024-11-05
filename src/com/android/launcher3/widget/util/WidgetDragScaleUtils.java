/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget.util;

import static com.android.launcher3.widget.util.WidgetSizes.getWidgetSizePx;

import android.content.Context;
import android.util.Size;

import androidx.annotation.Px;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;

/** Utility classes to evaluate widget scale during drag and drops. **/
public final class WidgetDragScaleUtils {
    // Widgets are 5% scaled down relative to their size to have shadow display well inside the
    // drop target frame (if its possible to scale it down within visible area under the finger).
    private static final float WIDGET_SCALE_DOWN = 0.05f;

    /**
     * Returns the scale to be applied to given dragged view to scale it down relative to the
     * spring loaded workspace. Applies additional scale down offset to get it a little inside
     * the drop target frame. If the relative scale is smaller than minimum size needed to keep the
     * view visible under the finger, scale down is performed only until the minimum size.
     */
    @Px
    public static float getWidgetDragScalePx(Context context, DeviceProfile deviceProfile,
            @Px float draggedViewWidthPx, @Px float draggedViewHeightPx, ItemInfo itemInfo) {
        int minSize = context.getResources().getDimensionPixelSize(
                R.dimen.widget_drag_view_min_scale_down_size);
        Size widgetSizesPx = getWidgetSizePx(deviceProfile, itemInfo.spanX, itemInfo.spanY);

        // We add workspace spring load scale, since the widget's drop target is also scaled, so
        // the widget size is essentially that smaller.
        float desiredWidgetScale = deviceProfile.getWorkspaceSpringLoadScale(context)
                - WIDGET_SCALE_DOWN;
        float desiredWidgetWidthPx = Math.max(minSize,
                (desiredWidgetScale * widgetSizesPx.getWidth()));
        float desiredWidgetHeightPx = Math.max(minSize,
                desiredWidgetScale * widgetSizesPx.getHeight());

        final float bitmapAspectRatio = draggedViewWidthPx / draggedViewHeightPx;
        final float containerAspectRatio = desiredWidgetWidthPx / desiredWidgetHeightPx;

        // This downscales large views to fit inside drop target frame. Smaller drawable views may
        // be up-scaled if they are smaller than the min size;
        final float scale = bitmapAspectRatio >= containerAspectRatio ? desiredWidgetWidthPx
                / draggedViewWidthPx : desiredWidgetHeightPx / draggedViewHeightPx;
        // scale in terms of dp to be applied to the drag shadow during drag and drop
        return (draggedViewWidthPx * scale) - draggedViewWidthPx;
    }
}
