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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

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
     */
    public static ArrayList<SizeF> getWidgetSizesDp(Context context, int spanX, int spanY) {
        ArrayList<SizeF> sizes = new ArrayList<>(2);
        final float density = context.getResources().getDisplayMetrics().density;

        for (DeviceProfile profile : LauncherAppState.getIDP(context).supportedProfiles) {
            Size widgetSizePx = getWidgetSizePx(profile, spanX, spanY);
            sizes.add(new SizeF(widgetSizePx.getWidth() / density,
                    widgetSizePx.getHeight() / density));
        }
        return sizes;
    }

    /** Returns the size, in pixels, a widget of given spans & {@code profile}. */
    public static Size getWidgetSizePx(DeviceProfile profile, int spanX, int spanY) {
        final int hBorderSpacing = (spanX - 1) * profile.cellLayoutBorderSpacePx.x;
        final int vBorderSpacing = (spanY - 1) * profile.cellLayoutBorderSpacePx.y;

        Point cellSize = profile.getCellSize();
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
     * {@link #getWidgetSizesDp(Context, int, int)} &
     */
    public static Size getWidgetItemSizePx(Context context, DeviceProfile profile,
            WidgetItem widgetItem) {
        if (widgetItem.isShortcut()) {
            int dimension = profile.allAppsIconSizePx + 2 * context.getResources()
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding);
            return new Size(dimension, dimension);
        }
        return getWidgetSizePx(profile, widgetItem.spanX, widgetItem.spanY);
    }

    /**
     * Updates a given {@code widgetView} with size, {@code spanX}, {@code spanY}.
     *
     * <p>On Android S+, it also updates the given {@code widgetView} with a list of sizes derived
     * from {@code spanX}, {@code spanY} in all supported device profiles.
     */
    public static void updateWidgetSizeRanges(AppWidgetHostView widgetView, Context context,
            int spanX, int spanY) {
        updateWidgetSizeRangesAsync(
                widgetView.getAppWidgetId(), widgetView.getAppWidgetInfo(), context, spanX, spanY);
    }

    /**
     * Updates a given {@code widgetId} with size, {@code spanX}, {@code spanY} asynchronously.
     *
     * <p>On Android S+, it also updates the given {@code widgetView} with a list of sizes derived
     * from {@code spanX}, {@code spanY} in all supported device profiles.
     */
    public static void updateWidgetSizeRangesAsync(int widgetId,
            AppWidgetProviderInfo info, Context context, int spanX, int spanY) {
        if (widgetId <= 0 || info == null) {
            return;
        }

        UI_HELPER_EXECUTOR.execute(() -> {
            AppWidgetManager widgetManager = AppWidgetManager.getInstance(context);
            Bundle sizeOptions = getWidgetSizeOptions(context, info.provider, spanX, spanY);
            if (sizeOptions.<SizeF>getParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES).equals(
                    widgetManager.getAppWidgetOptions(widgetId).<SizeF>getParcelableArrayList(
                            AppWidgetManager.OPTION_APPWIDGET_SIZES))) {
                return;
            }
            widgetManager.updateAppWidgetOptions(widgetId, sizeOptions);
        });
    }

    /**
     * Returns the bundle to be used as the default options for a widget with provided size.
     */
    public static Bundle getWidgetSizeOptions(Context context, ComponentName provider, int spanX,
            int spanY) {
        ArrayList<SizeF> paddedSizes = getWidgetSizesDp(context, spanX, spanY);

        Rect rect = getMinMaxSizes(paddedSizes);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, rect.left);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, rect.top);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, rect.right);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, rect.bottom);
        options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, paddedSizes);
        Log.d("b/267448330", "provider: " + provider + ", paddedSizes: " + paddedSizes
                + ", getMinMaxSizes: " + rect);
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
