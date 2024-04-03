/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.util.window;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.Utilities.dpiFromPx;

import static com.android.launcher3.testing.shared.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_HEIGHT_LANDSCAPE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE;
import static com.android.launcher3.testing.shared.ResourceUtils.NAV_BAR_INTERACTION_MODE_RES_NAME;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT_LANDSCAPE;
import static com.android.launcher3.testing.shared.ResourceUtils.STATUS_BAR_HEIGHT_PORTRAIT;
import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;
import static com.android.launcher3.util.RotationUtils.deltaRotation;
import static com.android.launcher3.util.RotationUtils.rotateRect;
import static com.android.launcher3.util.RotationUtils.rotateSize;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.WindowBounds;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for mocking some window manager behaviours
 */
public class WindowManagerProxy implements ResourceBasedOverride {

    private static final String TAG = "WindowManagerProxy";
    public static final int MIN_TABLET_WIDTH = 600;

    public static final MainThreadInitializedObject<WindowManagerProxy> INSTANCE =
            forOverride(WindowManagerProxy.class, R.string.window_manager_proxy_class);

    protected final boolean mTaskbarDrawnInProcess;

    /**
     * Creates a new instance of proxy, applying any overrides
     */
    public static WindowManagerProxy newInstance(Context context) {
        return Overrides.getObject(WindowManagerProxy.class, context,
                R.string.window_manager_proxy_class);
    }

    public WindowManagerProxy() {
        this(false);
    }

    protected WindowManagerProxy(boolean taskbarDrawnInProcess) {
        mTaskbarDrawnInProcess = taskbarDrawnInProcess;
    }

    /**
     * Returns a map of normalized info of internal displays to estimated window bounds
     * for that display
     */
    public ArrayMap<CachedDisplayInfo, List<WindowBounds>> estimateInternalDisplayBounds(
            Context displayInfoContext) {
        CachedDisplayInfo info = getDisplayInfo(displayInfoContext).normalize();
        List<WindowBounds> bounds = estimateWindowBounds(displayInfoContext, info);
        ArrayMap<CachedDisplayInfo, List<WindowBounds>> result = new ArrayMap<>();
        result.put(info, bounds);
        return result;
    }

    /**
     * Returns the real bounds for the provided display after applying any insets normalization
     */
    @TargetApi(Build.VERSION_CODES.R)
    public WindowBounds getRealBounds(Context displayInfoContext, CachedDisplayInfo info) {
        if (!Utilities.ATLEAST_R) {
            Point smallestSize = new Point();
            Point largestSize = new Point();
            getDisplay(displayInfoContext).getCurrentSizeRange(smallestSize, largestSize);

            if (info.size.y > info.size.x) {
                // Portrait
                return new WindowBounds(info.size.x, info.size.y, smallestSize.x, largestSize.y,
                        info.rotation);
            } else {
                // Landscape
                return new WindowBounds(info.size.x, info.size.y, largestSize.x, smallestSize.y,
                        info.rotation);
            }
        }

        WindowMetrics windowMetrics = displayInfoContext.getSystemService(WindowManager.class)
                .getMaximumWindowMetrics();
        Rect insets = new Rect();
        normalizeWindowInsets(displayInfoContext, windowMetrics.getWindowInsets(), insets);
        return new WindowBounds(windowMetrics.getBounds(), insets, info.rotation);
    }

    /**
     * Returns an updated insets, accounting for various Launcher UI specific overrides like taskbar
     */
    @TargetApi(Build.VERSION_CODES.R)
    public WindowInsets normalizeWindowInsets(Context context, WindowInsets oldInsets,
            Rect outInsets) {
        if (!Utilities.ATLEAST_R || !mTaskbarDrawnInProcess) {
            outInsets.set(oldInsets.getSystemWindowInsetLeft(), oldInsets.getSystemWindowInsetTop(),
                    oldInsets.getSystemWindowInsetRight(), oldInsets.getSystemWindowInsetBottom());
            return oldInsets;
        }

        WindowInsets.Builder insetsBuilder = new WindowInsets.Builder(oldInsets);
        Insets navInsets = oldInsets.getInsets(WindowInsets.Type.navigationBars());

        Resources systemRes = context.getResources();
        Configuration config = systemRes.getConfiguration();

        boolean isTablet = config.smallestScreenWidthDp > MIN_TABLET_WIDTH;
        boolean isGesture = isGestureNav(context);
        boolean isPortrait = config.screenHeightDp > config.screenWidthDp;

        int bottomNav = isTablet
                ? 0
                : (isPortrait
                        ? getDimenByName(systemRes, NAVBAR_HEIGHT)
                        : (isGesture
                                ? getDimenByName(systemRes, NAVBAR_HEIGHT_LANDSCAPE)
                                : 0));
        Insets newNavInsets = Insets.of(navInsets.left, navInsets.top, navInsets.right, bottomNav);
        insetsBuilder.setInsets(WindowInsets.Type.navigationBars(), newNavInsets);
        insetsBuilder.setInsetsIgnoringVisibility(WindowInsets.Type.navigationBars(), newNavInsets);

        Insets statusBarInsets = oldInsets.getInsets(WindowInsets.Type.statusBars());

        Insets newStatusBarInsets = Insets.of(
                statusBarInsets.left,
                getStatusBarHeight(context, isPortrait, statusBarInsets.top),
                statusBarInsets.right,
                statusBarInsets.bottom);
        insetsBuilder.setInsets(WindowInsets.Type.statusBars(), newStatusBarInsets);
        insetsBuilder.setInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars(), newStatusBarInsets);

        // Override the tappable insets to be 0 on the bottom for gesture nav (otherwise taskbar
        // would count towards it). This is used for the bottom protection in All Apps for example.
        if (isGesture) {
            Insets oldTappableInsets = oldInsets.getInsets(WindowInsets.Type.tappableElement());
            Insets newTappableInsets = Insets.of(oldTappableInsets.left, oldTappableInsets.top,
                    oldTappableInsets.right, 0);
            insetsBuilder.setInsets(WindowInsets.Type.tappableElement(), newTappableInsets);
        }

        WindowInsets result = insetsBuilder.build();
        Insets systemWindowInsets = result.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        outInsets.set(systemWindowInsets.left, systemWindowInsets.top, systemWindowInsets.right,
                systemWindowInsets.bottom);
        return result;
    }

    protected int getStatusBarHeight(Context context, boolean isPortrait, int statusBarInset) {
        Resources systemRes = context.getResources();
        int statusBarHeight = getDimenByName(systemRes,
                isPortrait ? STATUS_BAR_HEIGHT_PORTRAIT : STATUS_BAR_HEIGHT_LANDSCAPE,
                STATUS_BAR_HEIGHT);

        return Math.max(statusBarInset, statusBarHeight);
    }

    /**
     * Returns a list of possible WindowBounds for the display keyed on the 4 surface rotations
     */
    protected List<WindowBounds> estimateWindowBounds(Context context,
            CachedDisplayInfo displayInfo) {
        int densityDpi = context.getResources().getConfiguration().densityDpi;
        int rotation = displayInfo.rotation;
        Rect safeCutout = displayInfo.cutout;

        int minSize = Math.min(displayInfo.size.x, displayInfo.size.y);
        int swDp = (int) dpiFromPx(minSize, densityDpi);

        Resources systemRes;
        {
            Configuration conf = new Configuration();
            conf.smallestScreenWidthDp = swDp;
            systemRes = context.createConfigurationContext(conf).getResources();
        }

        boolean isTablet = swDp >= MIN_TABLET_WIDTH;
        boolean isTabletOrGesture = isTablet
                || (Utilities.ATLEAST_R && isGestureNav(context));

        // Use the status bar height resources because current system API to get the status bar
        // height doesn't allow to do this for an arbitrary display, it returns value only
        // for the current active display (see com.android.internal.policy.StatusBarUtils)
        int statusBarHeightPortrait = getDimenByName(systemRes,
                STATUS_BAR_HEIGHT_PORTRAIT, STATUS_BAR_HEIGHT);
        int statusBarHeightLandscape = getDimenByName(systemRes,
                STATUS_BAR_HEIGHT_LANDSCAPE, STATUS_BAR_HEIGHT);

        int navBarHeightPortrait, navBarHeightLandscape, navbarWidthLandscape;

        navBarHeightPortrait = isTablet
                ? (mTaskbarDrawnInProcess
                        ? 0 : systemRes.getDimensionPixelSize(R.dimen.taskbar_size))
                : getDimenByName(systemRes, NAVBAR_HEIGHT);

        navBarHeightLandscape = isTablet
                ? (mTaskbarDrawnInProcess
                        ? 0 : systemRes.getDimensionPixelSize(R.dimen.taskbar_size))
                : (isTabletOrGesture
                        ? getDimenByName(systemRes, NAVBAR_HEIGHT_LANDSCAPE) : 0);
        navbarWidthLandscape = isTabletOrGesture
                ? 0
                : getDimenByName(systemRes, NAVBAR_LANDSCAPE_LEFT_RIGHT_SIZE);

        List<WindowBounds> result = new ArrayList<>(4);
        Point tempSize = new Point();
        for (int i = 0; i < 4; i++) {
            int rotationChange = deltaRotation(rotation, i);
            tempSize.set(displayInfo.size.x, displayInfo.size.y);
            rotateSize(tempSize, rotationChange);
            Rect bounds = new Rect(0, 0, tempSize.x, tempSize.y);

            int navBarHeight, navbarWidth, statusBarHeight;
            if (tempSize.y > tempSize.x) {
                navBarHeight = navBarHeightPortrait;
                navbarWidth = 0;
                statusBarHeight = statusBarHeightPortrait;
            } else {
                navBarHeight = navBarHeightLandscape;
                navbarWidth = navbarWidthLandscape;
                statusBarHeight = statusBarHeightLandscape;
            }

            Rect insets = new Rect(safeCutout);
            rotateRect(insets, rotationChange);
            insets.top = Math.max(insets.top, statusBarHeight);
            insets.bottom = Math.max(insets.bottom, navBarHeight);

            if (i == Surface.ROTATION_270 || i == Surface.ROTATION_180) {
                // On reverse landscape (and in rare-case when the natural orientation of the
                // device is landscape), navigation bar is on the right.
                insets.left = Math.max(insets.left, navbarWidth);
            } else {
                insets.right = Math.max(insets.right, navbarWidth);
            }
            result.add(new WindowBounds(bounds, insets, i));
        }
        return result;
    }

    /**
     * Wrapper around the utility method for easier emulation
     */
    protected int getDimenByName(Resources res, String resName) {
        return ResourceUtils.getDimenByName(resName, res, 0);
    }

    /**
     * Wrapper around the utility method for easier emulation
     */
    protected int getDimenByName(Resources res, String resName, String fallback) {
        int dimen = ResourceUtils.getDimenByName(resName, res, -1);
        return dimen > -1 ? dimen : getDimenByName(res, fallback);
    }

    protected boolean isGestureNav(Context context) {
        return ResourceUtils.getIntegerByName("config_navBarInteractionMode",
                context.getResources(), INVALID_RESOURCE_HANDLE) == 2;
    }

    /**
     * Returns a CachedDisplayInfo initialized for the current display
     */
    @TargetApi(Build.VERSION_CODES.S)
    public CachedDisplayInfo getDisplayInfo(Context displayInfoContext) {
        int rotation = getRotation(displayInfoContext);
        if (Utilities.ATLEAST_S) {
            WindowMetrics windowMetrics = displayInfoContext.getSystemService(WindowManager.class)
                    .getMaximumWindowMetrics();
            return getDisplayInfo(windowMetrics, rotation);
        } else {
            Point size = new Point();
            Display display = getDisplay(displayInfoContext);
            display.getRealSize(size);
            Rect cutoutRect = new Rect();
            return new CachedDisplayInfo(size, rotation, cutoutRect);
        }
    }

    /**
     * Returns a CachedDisplayInfo initialized for the current display
     */
    @TargetApi(Build.VERSION_CODES.S)
    protected CachedDisplayInfo getDisplayInfo(WindowMetrics windowMetrics, int rotation) {
        Point size = new Point(windowMetrics.getBounds().right, windowMetrics.getBounds().bottom);
        Rect cutoutRect = new Rect();
        DisplayCutout cutout = windowMetrics.getWindowInsets().getDisplayCutout();
        if (cutout != null) {
            cutoutRect.set(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                    cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
        }
        return new CachedDisplayInfo(size, rotation, cutoutRect);
    }

    /**
     * Returns bounds of the display associated with the context, or bounds of DEFAULT_DISPLAY
     * if the context isn't associated with a display.
     */
    public Rect getCurrentBounds(Context displayInfoContext) {
        Resources res = displayInfoContext.getResources();
        Configuration config = res.getConfiguration();

        float screenWidth = config.screenWidthDp * res.getDisplayMetrics().density;
        float screenHeight = config.screenHeightDp * res.getDisplayMetrics().density;

        return new Rect(0, 0, (int) screenWidth, (int) screenHeight);
    }

    /**
     * Returns rotation of the display associated with the context, or rotation of DEFAULT_DISPLAY
     * if the context isn't associated with a display.
     */
    public int getRotation(Context displayInfoContext) {
        return getDisplay(displayInfoContext).getRotation();
    }

    /**
     *
     * Returns the display associated with the context, or DEFAULT_DISPLAY if the context isn't
     * associated with a display.
     */
    protected Display getDisplay(Context displayInfoContext) {
        if (Utilities.ATLEAST_R) {
            try {
                return displayInfoContext.getDisplay();
            } catch (UnsupportedOperationException e) {
                // Ignore
            }
        }
        return displayInfoContext.getSystemService(DisplayManager.class).getDisplay(
                DEFAULT_DISPLAY);
    }

    /**
     * Returns the current navigation mode from resource.
     */
    public NavigationMode getNavigationMode(Context context) {
        int modeInt = ResourceUtils.getIntegerByName(NAV_BAR_INTERACTION_MODE_RES_NAME,
                context.getResources(), INVALID_RESOURCE_HANDLE);

        if (modeInt == INVALID_RESOURCE_HANDLE) {
            Log.e(TAG, "Failed to get system resource ID. Incompatible framework version?");
        } else {
            for (NavigationMode m : NavigationMode.values()) {
                if (m.resValue == modeInt) {
                    return m;
                }
            }
        }
        return Utilities.ATLEAST_S ? NavigationMode.NO_BUTTON :
                NavigationMode.THREE_BUTTONS;
    }
}
