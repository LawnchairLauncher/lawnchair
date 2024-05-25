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

import static com.android.launcher3.InvariantDeviceProfile.TYPE_MULTI_DISPLAY;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_PHONE;
import static com.android.launcher3.InvariantDeviceProfile.TYPE_TABLET;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_DESKTOP_MODE_KEY;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_IN_DESKTOP_MODE;
import static com.android.launcher3.LauncherPrefs.TASKBAR_PINNING_KEY;
import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.config.FeatureFlags.enableTaskbarPinning;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.FlagDebugUtils.appendFlag;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import com.android.launcher3.InvariantDeviceProfile.DeviceType;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.window.CachedDisplayInfo;
import com.android.launcher3.util.window.WindowManagerProxy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
public class DisplayController implements ComponentCallbacks, SafeCloseable {

    private static final String TAG = "DisplayController";
    private static final boolean DEBUG = false;
    private static boolean sTransientTaskbarStatusForTests = true;

    // TODO(b/254119092) remove all logs with this tag
    public static final String TASKBAR_NOT_DESTROYED_TAG = "b/254119092";

    public static final MainThreadInitializedObject<DisplayController> INSTANCE =
            new MainThreadInitializedObject<>(DisplayController::new);

    public static final int CHANGE_ACTIVE_SCREEN = 1 << 0;
    public static final int CHANGE_ROTATION = 1 << 1;
    public static final int CHANGE_DENSITY = 1 << 2;
    public static final int CHANGE_SUPPORTED_BOUNDS = 1 << 3;
    public static final int CHANGE_NAVIGATION_MODE = 1 << 4;
    public static final int CHANGE_TASKBAR_PINNING = 1 << 5;
    public static final int CHANGE_DESKTOP_MODE = 1 << 6;

    public static final int CHANGE_ALL = CHANGE_ACTIVE_SCREEN | CHANGE_ROTATION
            | CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS | CHANGE_NAVIGATION_MODE
            | CHANGE_TASKBAR_PINNING | CHANGE_DESKTOP_MODE;

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

    private SharedPreferences.OnSharedPreferenceChangeListener
            mTaskbarPinningPreferenceChangeListener;

    @VisibleForTesting
    protected DisplayController(Context context) {
        mContext = context;
        mDM = context.getSystemService(DisplayManager.class);

        if (enableTaskbarPinning()) {
            attachTaskbarPinningSharedPreferenceChangeListener(mContext);
        }

        Display display = mDM.getDisplay(DEFAULT_DISPLAY);
        if (Utilities.ATLEAST_S) {
            mWindowContext = mContext.createWindowContext(display, TYPE_APPLICATION, null);
            mWindowContext.registerComponentCallbacks(this);
        } else {
            mWindowContext = null;
            mReceiver.register(mContext, ACTION_CONFIGURATION_CHANGED);
        }

        // Initialize navigation mode change listener
        mReceiver.registerPkgActions(mContext, TARGET_OVERLAY_PACKAGE, ACTION_OVERLAY_CHANGED);

        WindowManagerProxy wmProxy = WindowManagerProxy.INSTANCE.get(context);
        Context displayInfoContext = getDisplayInfoContext(display);
        mInfo = new Info(displayInfoContext, wmProxy,
                wmProxy.estimateInternalDisplayBounds(displayInfoContext));
        FileLog.i(TAG, "(CTOR) perDisplayBounds: " + mInfo.mPerDisplayBounds);
    }

    private void attachTaskbarPinningSharedPreferenceChangeListener(Context context) {
        mTaskbarPinningPreferenceChangeListener =
                (sharedPreferences, key) -> {
                    LauncherPrefs prefs = LauncherPrefs.get(mContext);
                    boolean isTaskbarPinningChanged = TASKBAR_PINNING_KEY.equals(key)
                            && mInfo.mIsTaskbarPinned != prefs.get(TASKBAR_PINNING);
                    boolean isTaskbarPinningDesktopModeChanged =
                            TASKBAR_PINNING_DESKTOP_MODE_KEY.equals(key)
                                    && mInfo.mIsTaskbarPinnedInDesktopMode != prefs.get(
                                    TASKBAR_PINNING_IN_DESKTOP_MODE);
                    if (isTaskbarPinningChanged || isTaskbarPinningDesktopModeChanged) {
                        handleInfoChange(mWindowContext.getDisplay());
                    }
                };

        LauncherPrefs.get(context).addListener(
                mTaskbarPinningPreferenceChangeListener, TASKBAR_PINNING);
        LauncherPrefs.get(context).addListener(
                mTaskbarPinningPreferenceChangeListener, TASKBAR_PINNING_IN_DESKTOP_MODE);
    }

    /**
     * Returns the current navigation mode
     */
    public static NavigationMode getNavigationMode(Context context) {
        return INSTANCE.get(context).getInfo().getNavigationMode();
    }

    /**
     * Returns whether taskbar is transient.
     */
    public static boolean isTransientTaskbar(Context context) {
        return INSTANCE.get(context).getInfo().isTransientTaskbar();
    }

    /**
     * Handles info change for desktop mode.
     */
    public static void handleInfoChangeForDesktopMode(Context context) {
        INSTANCE.get(context).handleInfoChange(context.getDisplay());
    }

    /**
     * Enables transient taskbar status for tests.
     */
    @VisibleForTesting
    public static void enableTransientTaskbarForTests(boolean enable) {
        sTransientTaskbarStatusForTests = enable;
    }

    /**
     * Returns whether the taskbar is pinned in gesture navigation mode.
     */
    public static boolean isPinnedTaskbar(Context context) {
        return INSTANCE.get(context).getInfo().isPinnedTaskbar();
    }

    @Override
    public void close() {
        mDestroyed = true;
        if (enableTaskbarPinning()) {
            LauncherPrefs.get(mContext).removeListener(
                    mTaskbarPinningPreferenceChangeListener, TASKBAR_PINNING);
            LauncherPrefs.get(mContext).removeListener(
                    mTaskbarPinningPreferenceChangeListener, TASKBAR_PINNING_IN_DESKTOP_MODE);
        }
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
        Log.d(TASKBAR_NOT_DESTROYED_TAG, "DisplayController#onConfigurationChanged: " + config);
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
    @VisibleForTesting
    public void handleInfoChange(Display display) {
        WindowManagerProxy wmProxy = WindowManagerProxy.INSTANCE.get(mContext);
        Info oldInfo = mInfo;

        Context displayInfoContext = getDisplayInfoContext(display);
        Info newInfo = new Info(displayInfoContext, wmProxy, oldInfo.mPerDisplayBounds);

        if (newInfo.densityDpi != oldInfo.densityDpi || newInfo.fontScale != oldInfo.fontScale
                || newInfo.getNavigationMode() != oldInfo.getNavigationMode()) {
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
        if (newInfo.getNavigationMode() != oldInfo.getNavigationMode()) {
            change |= CHANGE_NAVIGATION_MODE;
        }
        if (!newInfo.supportedBounds.equals(oldInfo.supportedBounds)
                || !newInfo.mPerDisplayBounds.equals(oldInfo.mPerDisplayBounds)) {
            change |= CHANGE_SUPPORTED_BOUNDS;
            FileLog.w(TAG,
                    "(CHANGE_SUPPORTED_BOUNDS) perDisplayBounds: " + newInfo.mPerDisplayBounds);
        }
        if ((newInfo.mIsTaskbarPinned != oldInfo.mIsTaskbarPinned)
                || (newInfo.mIsTaskbarPinnedInDesktopMode
                    != oldInfo.mIsTaskbarPinnedInDesktopMode)) {
            change |= CHANGE_TASKBAR_PINNING;
        }
        if (newInfo.mIsInDesktopMode != oldInfo.mIsInDesktopMode) {
            change |= CHANGE_DESKTOP_MODE;
        }

        if (DEBUG) {
            Log.d(TAG, "handleInfoChange - change: " + getChangeFlagsString(change));
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
        private final NavigationMode navigationMode;
        private final PortraitSize mScreenSizeDp;

        // WindowBounds
        public final WindowBounds realBounds;
        public final Set<WindowBounds> supportedBounds = new ArraySet<>();
        private final ArrayMap<CachedDisplayInfo, List<WindowBounds>> mPerDisplayBounds =
                new ArrayMap<>();

        private final boolean mIsTaskbarPinned;
        private final boolean mIsTaskbarPinnedInDesktopMode;

        private final boolean mIsInDesktopMode;

        public Info(Context displayInfoContext) {
            /* don't need system overrides for external displays */
            this(displayInfoContext, new WindowManagerProxy(), new ArrayMap<>());
        }

        // Used for testing
        public Info(Context displayInfoContext,
                WindowManagerProxy wmProxy,
                Map<CachedDisplayInfo, List<WindowBounds>> perDisplayBoundsCache) {
            CachedDisplayInfo displayInfo = wmProxy.getDisplayInfo(displayInfoContext);
            normalizedDisplayInfo = displayInfo.normalize(wmProxy);
            rotation = displayInfo.rotation;
            currentSize = displayInfo.size;
            cutout = WindowManagerProxy.getSafeInsets(displayInfo.cutout);

            Configuration config = displayInfoContext.getResources().getConfiguration();
            fontScale = config.fontScale;
            densityDpi = config.densityDpi;
            mScreenSizeDp = new PortraitSize(config.screenHeightDp, config.screenWidthDp);
            navigationMode = wmProxy.getNavigationMode(displayInfoContext);

            mPerDisplayBounds.putAll(perDisplayBoundsCache);
            List<WindowBounds> cachedValue = mPerDisplayBounds.get(normalizedDisplayInfo);

            realBounds = wmProxy.getRealBounds(displayInfoContext, displayInfo);
            if (cachedValue == null) {
                // Unexpected normalizedDisplayInfo is found, recreate the cache
                FileLog.e(TAG, "Unexpected normalizedDisplayInfo found, invalidating cache: "
                        + normalizedDisplayInfo);
                FileLog.e(TAG, "(Invalid Cache) perDisplayBounds : " + mPerDisplayBounds);
                mPerDisplayBounds.clear();
                mPerDisplayBounds.putAll(wmProxy.estimateInternalDisplayBounds(displayInfoContext));
                cachedValue = mPerDisplayBounds.get(normalizedDisplayInfo);
                if (cachedValue == null) {
                    FileLog.e(TAG, "normalizedDisplayInfo not found in estimation: "
                            + normalizedDisplayInfo);
                    supportedBounds.add(realBounds);
                }
            }

            if (cachedValue != null) {
                // Verify that the real bounds are a match
                WindowBounds expectedBounds = cachedValue.get(displayInfo.rotation);
                if (!realBounds.equals(expectedBounds)) {
                    List<WindowBounds> clone = new ArrayList<>(cachedValue);
                    clone.set(displayInfo.rotation, realBounds);
                    mPerDisplayBounds.put(normalizedDisplayInfo, clone);
                }
            }
            mPerDisplayBounds.values().forEach(supportedBounds::addAll);
            if (DEBUG) {
                Log.d(TAG, "displayInfo: " + displayInfo);
                Log.d(TAG, "realBounds: " + realBounds);
                Log.d(TAG, "normalizedDisplayInfo: " + normalizedDisplayInfo);
                Log.d(TAG, "perDisplayBounds: " + mPerDisplayBounds);
            }

            mIsTaskbarPinned = LauncherPrefs.get(displayInfoContext).get(TASKBAR_PINNING);
            mIsTaskbarPinnedInDesktopMode = LauncherPrefs.get(displayInfoContext).get(
                    TASKBAR_PINNING_IN_DESKTOP_MODE);
            mIsInDesktopMode = wmProxy.isInDesktopMode();
        }

        /**
         * Returns whether taskbar is transient.
         */
        public boolean isTransientTaskbar() {
            if (navigationMode != NavigationMode.NO_BUTTON) {
                return false;
            }
            if (Utilities.isRunningInTestHarness()) {
                // TODO(b/258604917): Once ENABLE_TASKBAR_PINNING is enabled, remove usage of
                //  sTransientTaskbarStatusForTests and update test to directly
                //  toggle shared preference to switch transient taskbar on/off.
                return sTransientTaskbarStatusForTests;
            }
            if (enableTaskbarPinning()) {
                if (mIsInDesktopMode) {
                    return !mIsTaskbarPinnedInDesktopMode;
                }
                return !mIsTaskbarPinned;
            }
            return true;
        }

        /**
         * Returns whether the taskbar is pinned in gesture navigation mode.
         */
        public boolean isPinnedTaskbar() {
            return navigationMode == NavigationMode.NO_BUTTON && !isTransientTaskbar();
        }

        public boolean isInDesktopMode() {
            return mIsInDesktopMode;
        }

        /**
         * Returns {@code true} if the bounds represent a tablet.
         */
        public boolean isTablet(WindowBounds bounds) {
            return smallestSizeDp(bounds) >= MIN_TABLET_WIDTH;
        }

        /** Getter for {@link #navigationMode} to allow mocking. */
        public NavigationMode getNavigationMode() {
            return navigationMode;
        }

        /**
         * Returns smallest size in dp for given bounds.
         */
        public float smallestSizeDp(WindowBounds bounds) {
            return dpiFromPx(Math.min(bounds.bounds.width(), bounds.bounds.height()), densityDpi);
        }

        /**
         * Returns all displays for the device
         */
        public Set<CachedDisplayInfo> getAllDisplays() {
            return Collections.unmodifiableSet(mPerDisplayBounds.keySet());
        }

        public int getDensityDpi() {
            return densityDpi;
        }

        public @DeviceType int getDeviceType() {
            int flagPhone = 1 << 0;
            int flagTablet = 1 << 1;

            int type = supportedBounds.stream()
                    .mapToInt(bounds -> isTablet(bounds) ? flagTablet : flagPhone)
                    .reduce(0, (a, b) -> a | b);
            if (type == (flagPhone | flagTablet)) {
                // device has profiles supporting both phone and tablet modes
                return TYPE_MULTI_DISPLAY;
            } else if (type == flagTablet) {
                return TYPE_TABLET;
            } else {
                return TYPE_PHONE;
            }
        }
    }

    /**
     * Returns the given binary flags as a human-readable string.
     * @see #CHANGE_ALL
     */
    public String getChangeFlagsString(int change) {
        StringJoiner result = new StringJoiner("|");
        appendFlag(result, change, CHANGE_ACTIVE_SCREEN, "CHANGE_ACTIVE_SCREEN");
        appendFlag(result, change, CHANGE_ROTATION, "CHANGE_ROTATION");
        appendFlag(result, change, CHANGE_DENSITY, "CHANGE_DENSITY");
        appendFlag(result, change, CHANGE_SUPPORTED_BOUNDS, "CHANGE_SUPPORTED_BOUNDS");
        appendFlag(result, change, CHANGE_NAVIGATION_MODE, "CHANGE_NAVIGATION_MODE");
        appendFlag(result, change, CHANGE_TASKBAR_PINNING, "CHANGE_TASKBAR_VARIANT");
        appendFlag(result, change, CHANGE_DESKTOP_MODE, "CHANGE_DESKTOP_MODE");
        return result.toString();
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
        pw.println("  navigationMode=" + info.getNavigationMode().name());
        pw.println("  isTaskbarPinned=" + info.mIsTaskbarPinned);
        pw.println("  isTaskbarPinnedInDesktopMode=" + info.mIsTaskbarPinnedInDesktopMode);
        pw.println("  isInDesktopMode=" + info.mIsInDesktopMode);
        pw.println("  currentSize=" + info.currentSize);
        info.mPerDisplayBounds.forEach((key, value) -> pw.println(
                "  perDisplayBounds - " + key + ": " + value));
        pw.println("  isTransientTaskbar=" + info.isTransientTaskbar());
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
