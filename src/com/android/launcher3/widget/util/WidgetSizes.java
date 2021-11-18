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

import static android.appwidget.AppWidgetHostView.getDefaultPaddingForWidget;


import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Size;
import android.util.SizeF;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.model.WidgetItem;

import java.util.ArrayList;
import java.util.List;

/** A utility class for widget sizes related calculations. */
public final class WidgetSizes {

    /**
     * Returns the list of all possible sizes, in dp, for a widget of given spans on this device.
     *
     * <p>The returned sizes already take into account the system padding, and whether it is applied
     * or not in that specific configuration.
     */
    public static ArrayList<SizeF> getWidgetPaddedSizes(Context context, ComponentName provider,
            int spanX, int spanY) {
        Rect padding = getDefaultPaddingForWidget(context, provider, /* padding= */ null);

        ArrayList<SizeF> sizes = new ArrayList<>(2);
        final float density = context.getResources().getDisplayMetrics().density;
        final Point cellSize = new Point();

        for (DeviceProfile profile : LauncherAppState.getIDP(context).supportedProfiles) {
            Size widgetSizePx = getWidgetSizePx(profile, spanX, spanY, cellSize);
            if (!profile.shouldInsetWidgets()) {
                widgetSizePx = new Size(widgetSizePx.getWidth() - padding.left - padding.right,
                        widgetSizePx.getHeight() - padding.top - padding.bottom);
            }
            sizes.add(new SizeF(widgetSizePx.getWidth() / density,
                    widgetSizePx.getHeight() / density));
        }
        return sizes;
    }

    /** Returns the size, in pixels, a widget of given spans & {@code profile}. */
    public static Size getWidgetSizePx(DeviceProfile profile, int spanX, int spanY) {
        return getWidgetSizePx(profile, spanX, spanY, /* recycledCellSize= */ null);
    }

    /**
     * Returns the size, in pixels and removing padding, a widget of given spans & {@code profile}.
     */
    public static Size getWidgetPaddedSizePx(Context context, ComponentName component,
            DeviceProfile profile, int spanX, int spanY) {
        Size size = getWidgetSizePx(profile, spanX, spanY);
        if (profile.shouldInsetWidgets()) {
            return size;
        }
        Rect padding = getDefaultPaddingForWidget(context, component, /* padding= */ null);
        return new Size(size.getWidth() - padding.left - padding.right,
                size.getHeight() - padding.top - padding.bottom);
    }

    /**
     * Returns the size of a WidgetItem.
     */
    public static Size getWidgetItemSizePx(Context context, DeviceProfile profile,
            WidgetItem widgetItem) {
        if (widgetItem.isShortcut()) {
            int dimension = profile.allAppsIconSizePx + 2 * context.getResources()
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            return new Size(dimension, dimension);
        }
        return getWidgetPaddedSizePx(context, widgetItem.componentName, profile, widgetItem.spanX,
                widgetItem.spanY);
    }

    private static Size getWidgetSizePx(DeviceProfile profile, int spanX, int spanY,
            @Nullable Point recycledCellSize) {
        final int hBorderSpacing = (spanX - 1) * profile.cellLayoutBorderSpacingPx;
        final int vBorderSpacing = (spanY - 1) * profile.cellLayoutBorderSpacingPx;
        if (recycledCellSize == null) {
            recycledCellSize = new Point();
        }
        profile.getCellSize(recycledCellSize);
        return new Size(((spanX * recycledCellSize.x) + hBorderSpacing),
                ((spanY * recycledCellSize.y) + vBorderSpacing));
    }

    /**
     * Updates a given {@code widgetView} with size, {@code spanX}, {@code spanY}.
     *
     * <p>On Android S+, it also updates the given {@code widgetView} with a list of sizes derived
     * from {@code spanX}, {@code spanY} in all supported device profiles.
     */
    public static void updateWidgetSizeRanges(AppWidgetHostView widgetView, Context context,
            int spanX, int spanY) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
        int widgetId = widgetView.getAppWidgetId();
        if (widgetId <= 0) {
            return;
        }
        Bundle sizeOptions = getWidgetSizeOptions(context, widgetView.getAppWidgetInfo().provider,
                spanX, spanY);
        if (sizeOptions.<SizeF>getParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES).equals(
                widgetManager.getAppWidgetOptions(widgetId).<SizeF>getParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES))) {
            return;
        }
        widgetManager.updateAppWidgetOptions(widgetId, sizeOptions);
    }

    /**
     * Returns the bundle to be used as the default options for a widget with provided size.
     */
    public static Bundle getWidgetSizeOptions(Context context, ComponentName provider, int spanX,
            int spanY) {
        ArrayList<SizeF> paddedSizes = getWidgetPaddedSizes(context, provider, spanX, spanY);

        Rect rect = getMinMaxSizes(paddedSizes);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, rect.left);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, rect.top);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, rect.right);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, rect.bottom);
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, paddedSizes);
        return options;
    }

    /**
     * Returns the min and max widths and heights given a list of sizes, in dp.
     *
     * @param sizes List of sizes to get the min/max from.
     * @return A rectangle with the left (resp. top) is used for the min width (resp. height) and
     * the right (resp. bottom) for the max. The returned rectangle is set with 0s if the list is
     * empty.
     */
    private static Rect getMinMaxSizes(List<SizeF> sizes) {
        if (sizes.isEmpty()) {
            return new Rect();
        } else {
            SizeF first = sizes.get(0);
            Rect result = new Rect((int) first.getWidth(), (int) first.getHeight(),
                    (int) first.getWidth(), (int) first.getHeight());
            for (int i = 1; i < sizes.size(); i++) {
                result.union((int) sizes.get(i).getWidth(), (int) sizes.get(i).getHeight());
            }
            return result;
        }
    }
}
