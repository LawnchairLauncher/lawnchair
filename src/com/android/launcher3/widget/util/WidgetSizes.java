/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Size;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;

/** A utility class for widget sizes related calculations. */
public final class WidgetSizes {

    /** Returns the size, in pixels, a widget of given spans & {@code profile}. */
    public static Size getWidgetSizePx(DeviceProfile profile, int spanX, int spanY) {
        final int hBorderSpacing = (spanX - 1)
                * profile.getWorkspaceIconProfile().getCellLayoutBorderSpacePx().x;
        final int vBorderSpacing = (spanY - 1)
                * profile.getWorkspaceIconProfile().getCellLayoutBorderSpacePx().y;

        Point cellSize = profile.getWorkspaceIconProfile().getCellSize();
        Rect padding = profile.widgetPadding;

        return new Size(
                (spanX * cellSize.x) + hBorderSpacing - padding.left - padding.right,
                (spanY * cellSize.y) + vBorderSpacing - padding.top - padding.bottom);
    }

    /**
     * Returns the size of a {@link WidgetItem}.
     *
     * <p>This size is used by the widget picker. It should NEVER be shared with app widgets.
     *
     * <p>For sizes shared with app widgets, please refer to
     * {@link WidgetSizeHandler#getWidgetSizeOptions} &
     */
    public static Size getWidgetItemSizePx(Context context, DeviceProfile profile,
            WidgetItem widgetItem) {
        if (widgetItem.isShortcut()) {
            int dimension = profile.getAllAppsProfile().getIconSizePx() + 2 * context.getResources()
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            return new Size(dimension, dimension);
        }
        return getWidgetSizePx(profile, widgetItem.spanX, widgetItem.spanY);
    }

}
