/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.content.Intent.ACTION_CONFIGURATION_CHANGED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static com.android.launcher3.ResourceUtils.INVALID_RESOURCE_HANDLE;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_2_BUTTON;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_3_BUTTON;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.getPackageFilter;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_LARGE_TABLET_WIDTH;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;

import com.android.launcher3.ResourceUtils;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
public class DisplayController implements ComponentCallbacks, SafeCloseable {

    private static final String TAG = "DisplayController";

    public static final MainThreadInitializedObject<DisplayController> INSTANCE =
            new MainThreadInitializedObject<>(DisplayController::new);

    public static final int CHANGE_ACTIVE_SCREEN = 1 << 0;
    public static final int CHANGE_ROTATION = 1 << 1;
    public static final int CHANGE_DENSITY = 1 << 2;
    public static final int CHANGE_SUPPORTED_BOUNDS = 1 << 3;
    public static final int CHANGE_NAVIGATION_MODE = 1 << 4;

    public static final int CHANGE_ALL = CHANGE_ACTIVE_SCREEN | CHANGE_ROTATION
            | CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS | CHANGE_NAVIGATION_MODE;

    private static final String ACTION_OVERLAY_CHANGED = "android.intent.action.OVERLAY_CHANGED";
    private static final String NAV_BAR_INTERACTION_MODE_RES_NAME = "config_navBarInteractionMode";
    private static final String TARGET_OVERLAY_PACKAGE = "android";

    private final Context mContext;
    private final DisplayManager mDM;

    // Null for SDK < S
    private final Context mWindowContext;

    // The callback in this listener updates DeviceProfile, which other listeners might depend on
    private DisplayInfoChangeListener mPriorityListener;
    private final ArrayList<DisplayInfoChangeListener> mListeners = new ArrayList<>();

    private final SimpleBroadcastReceiver mReceiver = new SimpleBroadcastReceiver(this::onIntent);

    private Info mInfo;
    private boolean mDestroyed = false;

    private DisplayController(Context context) {
        mContext = context;
        mDM = context.getSystemService(DisplayManager.class);

        Display display = mDM.getDisplay(DEFAULT_DISPLAY);
        if (Utilities.ATLEAST_S) {
            mWindowContext = mContext.createWindowContext(display, TYPE_APPLICATION, null);
            mWindowContext.registerComponentCallbacks(this);
        } else {
            mWindowContext = null;
            mReceiver.register(mContext, ACTION_CONFIGURATION_CHANGED);
        }

        // Initialize navigation mode change listener
        mContext.registerReceiver(mReceiver,
                getPackageFilter(TARGET_OVERLAY_PACKAGE, ACTION_OVERLAY_CHANGED));

        WindowManagerProxy wmProxy = WindowManagerProxy.INSTANCE.get(context);
        mInfo = new Info(getDisplayInfoContext(display), display,
                wmProxy, wmProxy.estimateInternalDisplayBounds(context));
    }

    /**
     * Returns the current navigation mode
     */
    public static NavigationMode getNavigationMode(Context context) {
        return INSTANCE.get(context).getInfo().navigationMode;
    }

    @Override
    public void close() {
        mDestroyed = true;
        if (mWindowContext != null) {
            mWindowContext.unregisterComponentCallbacks(this);
        } else {
            // TODO: unregister broadcast receiver
        }
    }

    /**
     * Interface for listening for display changes
     */
    public interface DisplayInfoChangeListener {

        /**
         * Invoked when display info has changed.
         * @param context updated context associated with the display.
         * @param info updated display information.
         * @param flags bitmask indicating type of change.
         */
        void onDisplayInfoChanged(Context context, Info info, int flags);
    }

    private void onIntent(Intent intent) {
        if (mDestroyed) {
            return;
        }
        boolean reconfigure = false;
        if (ACTION_OVERLAY_CHANGED.equals(intent.getAction())) {
            reconfigure = true;
        } else if (ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            Configuration config = mContext.getResources().getConfiguration();
            reconfigure = mInfo.fontScale != config.fontScale
                    || mInfo.densityDpi != config.densityDpi;
        }

        if (reconfigure) {
            Log.d(TAG, "Configuration changed, notifying listeners");
            Display display = mDM.getDisplay(DEFAULT_DISPLAY);
            if (display != null) {
                handleInfoChange(display);
            }
        }
    }

    @UiThread
    @Override
    @TargetApi(Build.VERSION_CODES.S)
    public final void onConfigurationChanged(Configuration config) {
        Display display = mWindowContext.getDisplay();
        if (config.densityDpi != mInfo.densityDpi
                || config.fontScale != mInfo.fontScale
                || display.getRotation() != mInfo.rotation
                || !mInfo.mScreenSizeDp.equals(
                        new PortraitSize(config.screenHeightDp, config.screenWidthDp))) {
            handleInfoChange(display);
        }
    }

    @Override
    public final void onLowMemory() { }

    public void setPriorityListener(DisplayInfoChangeListener listener) {
        mPriorityListener = listener;
    }

    public void addChangeListener(DisplayInfoChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeChangeListener(DisplayInfoChangeListener listener) {
        mListeners.remove(listener);
    }

    public Info getInfo() {
        return mInfo;
    }

    private Context getDisplayInfoContext(Display display) {
        return Utilities.ATLEAST_S ? mWindowContext : mContext.createDisplayContext(display);
    }

    @AnyThread
    private void handleInfoChange(Display display) {
        WindowManagerProxy wmProxy = WindowManagerProxy.INSTANCE.get(mContext);
        Info oldInfo = mInfo;

        Context displayContext = getDisplayInfoContext(display);
        Info newInfo = new Info(displayContext, display, wmProxy, oldInfo.mPerDisplayBounds);

        if (newInfo.densityDpi != oldInfo.densityDpi || newInfo.fontScale != oldInfo.fontScale
                || newInfo.navigationMode != oldInfo.navigationMode) {
            // Cache may not be valid anymore, recreate without cache
            newInfo = new Info(displayContext, display, wmProxy,
                    wmProxy.estimateInternalDisplayBounds(displayContext));
        }

        int change = 0;
        if (!newInfo.displayId.equals(oldInfo.displayId)) {
            change |= CHANGE_ACTIVE_SCREEN;
        }
        if (newInfo.rotation != oldInfo.rotation) {
            change |= CHANGE_ROTATION;
        }
        if (newInfo.densityDpi != oldInfo.densityDpi || newInfo.fontScale != oldInfo.fontScale) {
            change |= CHANGE_DENSITY;
        }
        if (newInfo.navigationMode != oldInfo.navigationMode) {
            change |= CHANGE_NAVIGATION_MODE;
        }
        if (!newInfo.supportedBounds.equals(oldInfo.supportedBounds)) {
            change |= CHANGE_SUPPORTED_BOUNDS;

            Point currentS = newInfo.currentSize;
            Point expectedS = oldInfo.mPerDisplayBounds.get(newInfo.displayId).first.size;
            if (newInfo.supportedBounds.size() != oldInfo.supportedBounds.size()) {
                Log.e("b/198965093",
                        "Inconsistent number of displays"
                                + "\ndisplay state: " + display.getState()
                                + "\noldInfo.supportedBounds: " + oldInfo.supportedBounds
                                + "\nnewInfo.supportedBounds: " + newInfo.supportedBounds);
            }
            if ((Math.min(currentS.x, currentS.y) != Math.min(expectedS.x, expectedS.y)
                    || Math.max(currentS.x, currentS.y) != Math.max(expectedS.x, expectedS.y))
                    && display.getState() == Display.STATE_OFF) {
                Log.e("b/198965093", "Display size changed while display is off, ignoring change");
                return;
            }
        }

        if (change != 0) {
            mInfo = newInfo;
            final int flags = change;
            MAIN_EXECUTOR.execute(() -> notifyChange(displayContext, flags));
        }
    }

    private void notifyChange(Context context, int flags) {
        if (mPriorityListener != null) {
            mPriorityListener.onDisplayInfoChanged(context, mInfo, flags);
        }

        int count = mListeners.size();
        for (int i = 0; i < count; i++) {
            mListeners.get(i).onDisplayInfoChanged(context, mInfo, flags);
        }
    }

    public static class Info {

        // Cached property
        public final int rotation;
        public final String displayId;
        public final Point currentSize;
        public final Rect cutout;

        // Configuration property
        public final float fontScale;
        public final int densityDpi;
        public final NavigationMode navigationMode;

        private final PortraitSize mScreenSizeDp;

        public final Set<WindowBounds> supportedBounds = new ArraySet<>();

        private final ArrayMap<String, Pair<CachedDisplayInfo, WindowBounds[]>> mPerDisplayBounds =
                new ArrayMap<>();

        public Info(Context context, Display display) {
            /* don't need system overrides for external displays */
            this(context, display, new WindowManagerProxy(), new ArrayMap<>());
        }

        // Used for testing
        public Info(Context context, Display display,
                WindowManagerProxy wmProxy,
                ArrayMap<String, Pair<CachedDisplayInfo, WindowBounds[]>> perDisplayBoundsCache) {
            CachedDisplayInfo displayInfo = wmProxy.getDisplayInfo(display);
            rotation = displayInfo.rotation;
            currentSize = displayInfo.size;
            displayId = displayInfo.id;
            cutout = displayInfo.cutout;

            Configuration config = context.getResources().getConfiguration();
            fontScale = config.fontScale;
            densityDpi = config.densityDpi;
            mScreenSizeDp = new PortraitSize(config.screenHeightDp, config.screenWidthDp);
            navigationMode = parseNavigationMode(context);

            mPerDisplayBounds.putAll(perDisplayBoundsCache);
            Pair<CachedDisplayInfo, WindowBounds[]> cachedValue = mPerDisplayBounds.get(displayId);

            WindowBounds realBounds = wmProxy.getRealBounds(context, display, displayInfo);
            if (cachedValue == null) {
                supportedBounds.add(realBounds);
            } else {
                // Verify that the real bounds are a match
                WindowBounds expectedBounds = cachedValue.second[displayInfo.rotation];
                if (!realBounds.equals(expectedBounds)) {
                    WindowBounds[] clone = new WindowBounds[4];
                    System.arraycopy(cachedValue.second, 0, clone, 0, 4);
                    clone[displayInfo.rotation] = realBounds;
                    cachedValue = Pair.create(displayInfo.normalize(), clone);
                    mPerDisplayBounds.put(displayId, cachedValue);
                }
            }
            mPerDisplayBounds.values().forEach(
                    pair -> Collections.addAll(supportedBounds, pair.second));
            Log.d("b/211775278", "displayId: " + displayId + ", currentSize: " + currentSize);
            Log.d("b/211775278", "perDisplayBounds: " + mPerDisplayBounds);
        }

        /**
         * Returns {@code true} if the bounds represent a tablet.
         */
        public boolean isTablet(WindowBounds bounds) {
            return dpiFromPx(Math.min(bounds.bounds.width(), bounds.bounds.height()),
                    densityDpi) >= MIN_TABLET_WIDTH;
        }

        /**
         * Returns {@code true} if the bounds represent a large tablet.
         */
        public boolean isLargeTablet(WindowBounds bounds) {
            return dpiFromPx(Math.min(bounds.bounds.width(), bounds.bounds.height()),
                    densityDpi) >= MIN_LARGE_TABLET_WIDTH;
        }
    }

    /**
     * Dumps the current state information
     */
    public void dump(PrintWriter pw) {
        Info info = mInfo;
        pw.println("DisplayController.Info:");
        pw.println("  id=" + info.displayId);
        pw.println("  rotation=" + info.rotation);
        pw.println("  fontScale=" + info.fontScale);
        pw.println("  densityDpi=" + info.displayId);
        pw.println("  navigationMode=" + info.navigationMode.name());
        pw.println("  currentSize=" + info.currentSize);
        pw.println("  supportedBounds=" + info.supportedBounds);
    }

    /**
     * Utility class to hold a size information in an orientation independent way
     */
    public static class PortraitSize {
        public final int width, height;

        public PortraitSize(int w, int h) {
            width = Math.min(w, h);
            height = Math.max(w, h);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PortraitSize that = (PortraitSize) o;
            return width == that.width && height == that.height;
        }

        @Override
        public int hashCode() {
            return Objects.hash(width, height);
        }
    }

    public enum NavigationMode {
        THREE_BUTTONS(false, 0, LAUNCHER_NAVIGATION_MODE_3_BUTTON),
        TWO_BUTTONS(true, 1, LAUNCHER_NAVIGATION_MODE_2_BUTTON),
        NO_BUTTON(true, 2, LAUNCHER_NAVIGATION_MODE_GESTURE_BUTTON);

        public final boolean hasGestures;
        public final int resValue;
        public final LauncherEvent launcherEvent;

        NavigationMode(boolean hasGestures, int resValue, LauncherEvent launcherEvent) {
            this.hasGestures = hasGestures;
            this.resValue = resValue;
            this.launcherEvent = launcherEvent;
        }
    }

    private static NavigationMode parseNavigationMode(Context context) {
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
        return Utilities.ATLEAST_S ? NavigationMode.NO_BUTTON : NavigationMode.THREE_BUTTONS;
    }
}
