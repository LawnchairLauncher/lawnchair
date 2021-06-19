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
package com.android.launcher3.util;

import static com.android.launcher3.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.Utilities.dpiFromPx;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsets.Type;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.launcher3.R;
import com.android.launcher3.ResourceUtils;
import com.android.launcher3.util.DisplayController.PortraitSize;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class to simulate window manager APIs until proper APIs are available
 */
@TargetApi(Build.VERSION_CODES.S)
public class WindowManagerCompat {

    public static final int MIN_TABLET_WIDTH = 600;

    /**
     * Returns a set of supported render sizes for a set of internal displays.
     * This is a temporary workaround which assumes only nav-bar insets change across displays
     * @param consumeTaskBar if true, it assumes that task bar is part of the app window
     *                       and ignores any insets because of task bar.
     */
    public static Set<WindowMetrics> getDisplayProfiles(
            Context windowContext, Collection<PortraitSize> allDisplaySizes,
            int densityDpi, boolean consumeTaskBar) {
        WindowInsets metrics = windowContext.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics().getWindowInsets();
        boolean hasNavbar = ResourceUtils.getIntegerByName(
                "config_navBarInteractionMode",
                windowContext.getResources(),
                INVALID_RESOURCE_HANDLE) != 0;

        WindowInsets.Builder insetsBuilder = new WindowInsets.Builder(metrics);

        Set<WindowMetrics> result = new HashSet<>();
        for (PortraitSize size : allDisplaySizes) {
            int swDP = (int) dpiFromPx(size.width, densityDpi);
            boolean isTablet = swDP >= MIN_TABLET_WIDTH;

            final Insets portraitNav, landscapeNav;
            if (isTablet && !consumeTaskBar) {
                portraitNav = landscapeNav = Insets.of(0, 0, 0, windowContext.getResources()
                        .getDimensionPixelSize(R.dimen.taskbar_size));
            } else if (hasNavbar) {
                portraitNav = Insets.of(0, 0, 0,
                        getSystemResource(windowContext, "navigation_bar_height", swDP));
                landscapeNav = isTablet
                        ? Insets.of(0, 0, 0, getSystemResource(windowContext,
                                "navigation_bar_height_landscape", swDP))
                        : Insets.of(0, 0, getSystemResource(windowContext,
                                "navigation_bar_width", swDP), 0);
            } else {
                portraitNav = landscapeNav = Insets.of(0, 0, 0, 0);
            }

            result.add(new WindowMetrics(
                    new Rect(0, 0, size.width, size.height),
                    insetsBuilder.setInsets(Type.navigationBars(), portraitNav).build()));
            result.add(new WindowMetrics(
                    new Rect(0, 0, size.height, size.width),
                    insetsBuilder.setInsets(Type.navigationBars(), landscapeNav).build()));
        }
        return result;
    }

    private static int getSystemResource(Context context, String key, int swDp) {
        int resourceId = context.getResources().getIdentifier(key, "dimen", "android");
        if (resourceId > 0) {
            Configuration conf = new Configuration();
            conf.smallestScreenWidthDp = swDp;
            return context.createConfigurationContext(conf)
                    .getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
}
