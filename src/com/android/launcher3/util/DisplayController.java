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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

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
import android.util.Log;
import android.view.Display;

import androidx.annotation.AnyThread;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import com.android.launcher3.Utilities;

import java.util.ArrayList;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
@SuppressLint("NewApi")
public class DisplayController implements DisplayListener, ComponentCallbacks {

    private static final String TAG = "DisplayController";

    public static final MainThreadInitializedObject<DisplayController> INSTANCE =
            new MainThreadInitializedObject<>(DisplayController::new);

    public static final int CHANGE_SIZE = 1 << 0;
    public static final int CHANGE_ROTATION = 1 << 1;
    public static final int CHANGE_FRAME_DELAY = 1 << 2;
    public static final int CHANGE_DENSITY = 1 << 3;

    public static final int CHANGE_ALL = CHANGE_SIZE | CHANGE_ROTATION
            | CHANGE_FRAME_DELAY | CHANGE_DENSITY;

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

        mInfo = createInfo(display);
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

        void onDisplayInfoChanged(Info info, int flags);
    }

    /**
     * Only used for pre-S
     */
    private void onConfigChanged(Intent intent) {
        Configuration config = mContext.getResources().getConfiguration();
        if (config.fontScale != config.fontScale || mInfo.densityDpi != config.densityDpi) {
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
                        Math.min(config.screenHeightDp, config.screenWidthDp),
                        Math.max(config.screenHeightDp, config.screenWidthDp))) {
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

    private Info createInfo(Display display) {
        return new Info(mContext.createDisplayContext(display), display);
    }

    @AnyThread
    private void handleInfoChange(Display display) {
        Info oldInfo = mInfo;
        Info newInfo = createInfo(display);
        int change = 0;
        if (newInfo.hasDifferentSize(oldInfo)) {
            change |= CHANGE_SIZE;
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

        if (change != 0) {
            mInfo = newInfo;
            final int flags = change;
            MAIN_EXECUTOR.execute(() -> notifyChange(flags));
        }
    }

    private void notifyChange(int flags) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onDisplayInfoChanged(mInfo, flags);
        }
    }

    public static class Info {

        public final int id;
        public final int singleFrameMs;

        // Configuration properties
        public final int rotation;
        public final float fontScale;
        public final int densityDpi;

        private final Point mScreenSizeDp;

        public final Point realSize;
        public final Point smallestSize;
        public final Point largestSize;

        public Info(Context context, Display display) {
            id = display.getDisplayId();

            rotation = display.getRotation();

            Configuration config = context.getResources().getConfiguration();
            fontScale = config.fontScale;
            densityDpi = config.densityDpi;
            mScreenSizeDp = new Point(
                    Math.min(config.screenHeightDp, config.screenWidthDp),
                    Math.max(config.screenHeightDp, config.screenWidthDp));

            singleFrameMs = getSingleFrameMs(display);

            realSize = new Point();
            smallestSize = new Point();
            largestSize = new Point();

            display.getRealSize(realSize);
            display.getCurrentSizeRange(smallestSize, largestSize);
        }

        private boolean hasDifferentSize(Info info) {
            if (!realSize.equals(info.realSize)
                    && !realSize.equals(info.realSize.y, info.realSize.x)) {
                Log.d(TAG, String.format("Display size changed from %s to %s",
                        info.realSize, realSize));
                return true;
            }

            if (!smallestSize.equals(info.smallestSize) || !largestSize.equals(info.largestSize)) {
                Log.d(TAG, String.format("Available size changed from [%s, %s] to [%s, %s]",
                        smallestSize, largestSize, info.smallestSize, info.largestSize));
                return true;
            }

            return false;
        }
    }

    private static int getSingleFrameMs(Display display) {
        float refreshRate = display.getRefreshRate();
        return refreshRate > 0 ? (int) (1000 / refreshRate) : 16;
    }
}
