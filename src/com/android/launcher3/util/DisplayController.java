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

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.config.FeatureFlags.ENABLE_TRANSIENT_TASKBAR;
import static com.android.launcher3.config.FeatureFlags.FORCE_PERSISTENT_TASKBAR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.PackageManagerHelper.getPackageFilter;
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
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
public class DisplayController implements ComponentCallbacks, SafeCloseable {

    private static final String TAG = "DisplayController";
    private static final boolean DEBUG = false;
    private static boolean sTransientTaskbarStatusForTests;

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
        Context displayInfoContext = getDisplayInfoContext(display);
        mInfo = new Info(displayInfoContext, wmProxy,
                wmProxy.estimateInternalDisplayBounds(displayInfoContext));
    }

    /**
     * Returns the current navigation mode
     */
    public static NavigationMode getNavigationMode(Context context) {
        return INSTANCE.get(context).getInfo().navigationMode;
    }

    /**
     * Returns whether taskbar is transient.
     */
    public static boolean isTransientTaskbar(Context context) {
        return INSTANCE.get(context).isTransientTaskbar();
    }

    /**
     * Returns whether taskbar is transient.
     */
    public boolean isTransientTaskbar() {
        // TODO(b/258604917): When running in test harness, use !sTransientTaskbarStatusForTests
        //  once tests are updated to expect new persistent behavior such as not allowing long press
        //  to stash.
        if (!Utilities.IS_RUNNING_IN_TEST_HARNESS && FORCE_PERSISTENT_TASKBAR.get()) {
            return false;
        }
        return getInfo().navigationMode == NavigationMode.NO_BUTTON
                && (Utilities.IS_RUNNING_IN_TEST_HARNESS
                    ? sTransientTaskbarStatusForTests
                    : ENABLE_TRANSIENT_TASKBAR.get());
    }

    /**
     * Enables transient taskbar status for tests.
     */
    @VisibleForTesting
    public static void enableTransientTaskbarForTests(boolean enable) {
        sTransientTaskbarStatusForTests = enable;
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

        Context displayInfoContext = getDisplayInfoContext(display);
        Info newInfo = new Info(displayInfoContext, wmProxy, oldInfo.mPerDisplayBounds);

        if (newInfo.densityDpi != oldInfo.densityDpi || newInfo.fontScale != oldInfo.fontScale
                || newInfo.navigationMode != oldInfo.navigationMode) {
            // Cache may not be valid anymore, recreate without cache
            newInfo = new Info(displayInfoContext, wmProxy,
                    wmProxy.estimateInternalDisplayBounds(displayInfoContext));
        }

        int change = 0;
        if (!newInfo.normalizedDisplayInfo.equals(oldInfo.normalizedDisplayInfo)) {
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
        if (!newInfo.supportedBounds.equals(oldInfo.supportedBounds)
                || !newInfo.mPerDisplayBounds.equals(oldInfo.mPerDisplayBounds)) {
            change |= CHANGE_SUPPORTED_BOUNDS;
        }
        if (DEBUG) {
            Log.d(TAG, "handleInfoChange - change: 0b" + Integer.toBinaryString(change));
        }

        if (change != 0) {
            mInfo = newInfo;
            final int flags = change;
            MAIN_EXECUTOR.execute(() -> notifyChange(displayInfoContext, flags));
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
        public final CachedDisplayInfo normalizedDisplayInfo;
        public final int rotation;
        public final Point currentSize;
        public final Rect cutout;

        // Configuration property
        public final float fontScale;
        private final int densityDpi;
        public final NavigationMode navigationMode;
        private final PortraitSize mScreenSizeDp;

        // WindowBounds
        public final WindowBounds realBounds;
        public final Set<WindowBounds> supportedBounds = new ArraySet<>();
        private final ArrayMap<CachedDisplayInfo, WindowBounds[]> mPerDisplayBounds =
                new ArrayMap<>();

        public Info(Context displayInfoContext) {
            /* don't need system overrides for external displays */
            this(displayInfoContext, new WindowManagerProxy(), new ArrayMap<>());
        }

        // Used for testing
        public Info(Context displayInfoContext,
                WindowManagerProxy wmProxy,
                Map<CachedDisplayInfo, WindowBounds[]> perDisplayBoundsCache) {
            CachedDisplayInfo displayInfo = wmProxy.getDisplayInfo(displayInfoContext);
            normalizedDisplayInfo = displayInfo.normalize();
            rotation = displayInfo.rotation;
            currentSize = displayInfo.size;
            cutout = displayInfo.cutout;

            Configuration config = displayInfoContext.getResources().getConfiguration();
            fontScale = config.fontScale;
            densityDpi = config.densityDpi;
            mScreenSizeDp = new PortraitSize(config.screenHeightDp, config.screenWidthDp);
            navigationMode = wmProxy.getNavigationMode(displayInfoContext);

            mPerDisplayBounds.putAll(perDisplayBoundsCache);
            WindowBounds[] cachedValue = mPerDisplayBounds.get(normalizedDisplayInfo);

            realBounds = wmProxy.getRealBounds(displayInfoContext, displayInfo);
            if (cachedValue == null) {
                // Unexpected normalizedDisplayInfo is found, recreate the cache
                Log.e(TAG, "Unexpected normalizedDisplayInfo found, invalidating cache");
                mPerDisplayBounds.clear();
                mPerDisplayBounds.putAll(wmProxy.estimateInternalDisplayBounds(displayInfoContext));
                cachedValue = mPerDisplayBounds.get(normalizedDisplayInfo);
                if (cachedValue == null) {
                    Log.e(TAG, "normalizedDisplayInfo not found in estimation: "
                            + normalizedDisplayInfo);
                    supportedBounds.add(realBounds);
                }
            }

            if (cachedValue != null) {
                // Verify that the real bounds are a match
                WindowBounds expectedBounds = cachedValue[displayInfo.rotation];
                if (!realBounds.equals(expectedBounds)) {
                    WindowBounds[] clone = new WindowBounds[4];
                    System.arraycopy(cachedValue, 0, clone, 0, 4);
                    clone[displayInfo.rotation] = realBounds;
                    mPerDisplayBounds.put(normalizedDisplayInfo, clone);
                }
            }
            mPerDisplayBounds.values().forEach(
                    windowBounds -> Collections.addAll(supportedBounds, windowBounds));
            if (DEBUG) {
                Log.d(TAG, "displayInfo: " + displayInfo);
                Log.d(TAG, "realBounds: " + realBounds);
                Log.d(TAG, "normalizedDisplayInfo: " + normalizedDisplayInfo);
                mPerDisplayBounds.forEach((key, value) -> Log.d(TAG,
                        "perDisplayBounds - " + key + ": " + Arrays.deepToString(value)));
            }
        }

        /**
         * Returns {@code true} if the bounds represent a tablet.
         */
        public boolean isTablet(WindowBounds bounds) {
            return smallestSizeDp(bounds) >= MIN_TABLET_WIDTH;
        }

        /**
         * Returns smallest size in dp for given bounds.
         */
        public float smallestSizeDp(WindowBounds bounds) {
            return dpiFromPx(Math.min(bounds.bounds.width(), bounds.bounds.height()), densityDpi);
        }

        public int getDensityDpi() {
            return densityDpi;
        }
    }

    /**
     * Dumps the current state information
     */
    public void dump(PrintWriter pw) {
        Info info = mInfo;
        pw.println("DisplayController.Info:");
        pw.println("  normalizedDisplayInfo=" + info.normalizedDisplayInfo);
        pw.println("  rotation=" + info.rotation);
        pw.println("  fontScale=" + info.fontScale);
        pw.println("  densityDpi=" + info.densityDpi);
        pw.println("  navigationMode=" + info.navigationMode.name());
        pw.println("  currentSize=" + info.currentSize);
        info.mPerDisplayBounds.forEach((key, value) -> pw.println(
                "  perDisplayBounds - " + key + ": " + Arrays.deepToString(value)));
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

}
