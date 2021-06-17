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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;

import static com.android.launcher3.Utilities.dpiFromPx;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.WindowManagerCompat.MIN_TABLET_WIDTH;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Build;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.WindowMetrics;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.ApiWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
public class DisplayController implements DisplayListener, ComponentCallbacks {

    private static final String TAG = "DisplayController";

    public static final MainThreadInitializedObject<DisplayController> INSTANCE =
            new MainThreadInitializedObject<>(DisplayController::new);

    public static final int CHANGE_ACTIVE_SCREEN = 1 << 0;
    public static final int CHANGE_ROTATION = 1 << 1;
    public static final int CHANGE_FRAME_DELAY = 1 << 2;
    public static final int CHANGE_DENSITY = 1 << 3;
    public static final int CHANGE_SUPPORTED_BOUNDS = 1 << 4;

    public static final int CHANGE_ALL = CHANGE_ACTIVE_SCREEN | CHANGE_ROTATION
            | CHANGE_FRAME_DELAY | CHANGE_DENSITY | CHANGE_SUPPORTED_BOUNDS;

    private final Context mContext;
    private final DisplayManager mDM;

    // Null for SDK < S
    private final Context mWindowContext;

    private final ArrayList<DisplayInfoChangeListener> mListeners = new ArrayList<>();
    private Info mInfo;

    private DisplayController(Context context) {
        mContext = context;
        mDM = context.getSystemService(DisplayManager.class);

        Display display = mDM.getDisplay(DEFAULT_DISPLAY);
        if (Utilities.ATLEAST_S) {
            mWindowContext = mContext.createWindowContext(display, TYPE_APPLICATION, null);
            mWindowContext.registerComponentCallbacks(this);
        } else {
            mWindowContext = null;
            SimpleBroadcastReceiver configChangeReceiver =
                    new SimpleBroadcastReceiver(this::onConfigChanged);
            mContext.registerReceiver(configChangeReceiver,
                    new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED));
        }

        // Create a single holder for all internal displays. External display holders created
        // lazily.
        Set<PortraitSize> extraInternalDisplays = new ArraySet<>();
        for (Display d : mDM.getDisplays()) {
            if (ApiWrapper.isInternalDisplay(display) && d.getDisplayId() != DEFAULT_DISPLAY) {
                Point size = new Point();
                d.getRealSize(size);
                extraInternalDisplays.add(new PortraitSize(size.x, size.y));
            }
        }
        mInfo = new Info(getDisplayInfoContext(display), display, extraInternalDisplays);
        mDM.registerDisplayListener(this, UI_HELPER_EXECUTOR.getHandler());
    }

    @Override
    public final void onDisplayAdded(int displayId) { }

    @Override
    public final void onDisplayRemoved(int displayId) { }

    @WorkerThread
    @Override
    public final void onDisplayChanged(int displayId) {
        if (displayId != DEFAULT_DISPLAY) {
            return;
        }
        Display display = mDM.getDisplay(DEFAULT_DISPLAY);
        if (display == null) {
            return;
        }
        if (Utilities.ATLEAST_S) {
            // Only check for refresh rate. Everything else comes from component callbacks
            if (getSingleFrameMs(display) == mInfo.singleFrameMs) {
                return;
            }
        }
        handleInfoChange(display);
    }

    public static int getSingleFrameMs(Context context) {
        return INSTANCE.get(context).getInfo().singleFrameMs;
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

    /**
     * Only used for pre-S
     */
    private void onConfigChanged(Intent intent) {
        Configuration config = mContext.getResources().getConfiguration();
        if (mInfo.fontScale != config.fontScale || mInfo.densityDpi != config.densityDpi) {
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
        Info oldInfo = mInfo;
        Set<PortraitSize> extraDisplaysSizes = oldInfo.mAllSizes.size() > 1
                ? oldInfo.mAllSizes : Collections.emptySet();

        Context displayContext = getDisplayInfoContext(display);
        Info newInfo = new Info(displayContext, display, extraDisplaysSizes);
        int change = 0;
        if (!newInfo.mScreenSizeDp.equals(oldInfo.mScreenSizeDp)) {
            change |= CHANGE_ACTIVE_SCREEN;
        }
        if (newInfo.rotation != oldInfo.rotation) {
            change |= CHANGE_ROTATION;
        }
        if (newInfo.singleFrameMs != oldInfo.singleFrameMs) {
            change |= CHANGE_FRAME_DELAY;
        }
        if (newInfo.densityDpi != oldInfo.densityDpi || newInfo.fontScale != oldInfo.fontScale) {
            change |= CHANGE_DENSITY;
        }
        if (!newInfo.supportedBounds.equals(oldInfo.supportedBounds)) {
            change |= CHANGE_SUPPORTED_BOUNDS;
        }

        if (change != 0) {
            mInfo = newInfo;
            final int flags = change;
            MAIN_EXECUTOR.execute(() -> notifyChange(displayContext, flags));
        }
    }

    private void notifyChange(Context context, int flags) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onDisplayInfoChanged(context, mInfo, flags);
        }
    }

    public static class Info {

        public final int id;
        public final int singleFrameMs;

        // Configuration properties
        public final int rotation;
        public final float fontScale;
        public final int densityDpi;

        private final PortraitSize mScreenSizeDp;
        private final Set<PortraitSize> mAllSizes;

        public final Point currentSize;

        public final Set<WindowBounds> supportedBounds = new ArraySet<>();

        public Info(Context context, Display display) {
            this(context, display, Collections.emptySet());
        }

        private Info(Context context, Display display, Set<PortraitSize> extraDisplaysSizes) {
            id = display.getDisplayId();

            rotation = display.getRotation();

            Configuration config = context.getResources().getConfiguration();
            fontScale = config.fontScale;
            densityDpi = config.densityDpi;
            mScreenSizeDp = new PortraitSize(config.screenHeightDp, config.screenWidthDp);

            singleFrameMs = getSingleFrameMs(display);
            currentSize = new Point();

            display.getRealSize(currentSize);

            if (extraDisplaysSizes.isEmpty() || !Utilities.ATLEAST_S) {
                Point smallestSize = new Point();
                Point largestSize = new Point();
                display.getCurrentSizeRange(smallestSize, largestSize);

                int portraitWidth = Math.min(currentSize.x, currentSize.y);
                int portraitHeight = Math.max(currentSize.x, currentSize.y);

                supportedBounds.add(new WindowBounds(portraitWidth, portraitHeight,
                        smallestSize.x, largestSize.y));
                supportedBounds.add(new WindowBounds(portraitHeight, portraitWidth,
                        largestSize.x, smallestSize.y));
                mAllSizes = Collections.singleton(new PortraitSize(currentSize.x, currentSize.y));
            } else {
                mAllSizes = new ArraySet<>(extraDisplaysSizes);
                mAllSizes.add(new PortraitSize(currentSize.x, currentSize.y));
                Set<WindowMetrics> metrics = WindowManagerCompat.getDisplayProfiles(
                        context, mAllSizes, densityDpi,
                        ApiWrapper.TASKBAR_DRAWN_IN_PROCESS);
                metrics.forEach(wm -> supportedBounds.add(WindowBounds.fromWindowMetrics(wm)));
            }
        }

        /**
         * Returns true if the bounds represent a tablet
         */
        public boolean isTablet(WindowBounds bounds) {
            return dpiFromPx(Math.min(bounds.bounds.width(), bounds.bounds.height()),
                    densityDpi) >= MIN_TABLET_WIDTH;
        }
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

    private static int getSingleFrameMs(Display display) {
        float refreshRate = display.getRefreshRate();
        return refreshRate > 0 ? (int) (1000 / refreshRate) : 16;
    }
}
