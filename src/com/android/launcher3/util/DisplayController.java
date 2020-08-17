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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;

import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
public class DisplayController implements DisplayListener {

    private static final String TAG = "DisplayController";

    public static final MainThreadInitializedObject<DisplayController> INSTANCE =
            new MainThreadInitializedObject<>(DisplayController::new);

    private final SparseArray<DisplayHolder> mOtherDisplays = new SparseArray<>(0);
    // We store the default display separately, to avoid null checks for primary use case.
    private final DisplayHolder mDefaultDisplay;

    private final ArrayList<DisplayListChangeListener> mListListeners = new ArrayList<>();

    private DisplayController(Context context) {
        mDefaultDisplay = new DisplayHolder(context, DEFAULT_DISPLAY);

        DisplayManager dm = context.getSystemService(DisplayManager.class);
        dm.registerDisplayListener(this, UI_HELPER_EXECUTOR.getHandler());
    }

    @Override
    public final void onDisplayAdded(int displayId) {
        DisplayHolder holder = new DisplayHolder(mDefaultDisplay.mDisplayContext, displayId);
        synchronized (mOtherDisplays) {
            mOtherDisplays.put(displayId, holder);
        }
        MAIN_EXECUTOR.execute(() -> mListListeners.forEach(l-> l.onDisplayAdded(holder)));
    }

    @Override
    public final void onDisplayRemoved(int displayId) {
        synchronized (mOtherDisplays) {
            mOtherDisplays.remove(displayId);
        }
        MAIN_EXECUTOR.execute(() -> mListListeners.forEach(l-> l.onDisplayRemoved(displayId)));
    }

    /**
     * Returns the holder corresponding to the given display
     */
    public DisplayHolder getHolder(int displayId) {
        if (displayId == mDefaultDisplay.mId) {
            return mDefaultDisplay;
        } else {
            synchronized (mOtherDisplays) {
                return mOtherDisplays.get(displayId);
            }
        }
    }

    /**
     * Adds a listener for display list changes
     */
    public void addListChangeListener(DisplayListChangeListener listener) {
        mListListeners.add(listener);
    }

    /**
     * Removes a previously added display list change listener
     */
    public void removeListChangeListener(DisplayListChangeListener listener) {
        mListListeners.remove(listener);
    }

    @Override
    public final void onDisplayChanged(int displayId) {
        DisplayHolder holder = getHolder(displayId);
        if (holder != null) {
            holder.handleOnChange();
        }
    }

    public static int getSingleFrameMs(Context context) {
        return getDefaultDisplay(context).getInfo().singleFrameMs;
    }

    public static DisplayHolder getDefaultDisplay(Context context) {
        return INSTANCE.get(context).mDefaultDisplay;
    }

    /**
     * A listener to receiving addition or removal of new displays
     */
    public interface DisplayListChangeListener {

        /**
         * Called when a new display is added
         */
        void onDisplayAdded(DisplayHolder holder);

        /**
         * Called when a previously added display is removed
         */
        void onDisplayRemoved(int displayId);
    }

    /**
     * Interface for listening for display changes
     */
    public interface DisplayInfoChangeListener {

        void onDisplayInfoChanged(Info info, int flags);
    }

    public static class DisplayHolder {

        public static final int CHANGE_SIZE = 1 << 0;
        public static final int CHANGE_ROTATION = 1 << 1;
        public static final int CHANGE_FRAME_DELAY = 1 << 2;

        public static final int CHANGE_ALL = CHANGE_SIZE | CHANGE_ROTATION | CHANGE_FRAME_DELAY;

        final Context mDisplayContext;
        final int mId;
        private final ArrayList<DisplayInfoChangeListener> mListeners = new ArrayList<>();
        private DisplayController.Info mInfo;

        public DisplayHolder(Context context, int id) {
            DisplayManager dm = context.getSystemService(DisplayManager.class);
            // Use application context to create display context so that it can have its own
            // Resources.
            mDisplayContext = context.getApplicationContext()
                    .createDisplayContext(dm.getDisplay(id));
            // Note that the Display object must be obtained from DisplayManager which is
            // associated to the display context, so the Display is isolated from Activity and
            // Application to provide the actual state of device that excludes the additional
            // adjustment and override.
            mInfo = new DisplayController.Info(mDisplayContext);
            mId = mInfo.id;
        }

        public void addChangeListener(DisplayInfoChangeListener listener) {
            mListeners.add(listener);
        }

        public void removeChangeListener(DisplayInfoChangeListener listener) {
            mListeners.remove(listener);
        }

        public DisplayController.Info getInfo() {
            return mInfo;
        }

        protected void handleOnChange() {
            Info oldInfo = mInfo;
            Info info = new Info(mDisplayContext);

            int change = 0;
            if (info.hasDifferentSize(oldInfo)) {
                change |= CHANGE_SIZE;
            }
            if (oldInfo.rotation != info.rotation) {
                change |= CHANGE_ROTATION;
            }
            if (info.singleFrameMs != oldInfo.singleFrameMs) {
                change |= CHANGE_FRAME_DELAY;
            }

            if (change != 0) {
                mInfo = info;
                final int flags = change;
                MAIN_EXECUTOR.execute(() -> notifyChange(flags));
            }
        }

        private void notifyChange(int flags) {
            for (int i = mListeners.size() - 1; i >= 0; i--) {
                mListeners.get(i).onDisplayInfoChanged(mInfo, flags);
            }
        }

    }

    public static class Info {

        public final int id;
        public final int rotation;
        public final int singleFrameMs;

        public final Point realSize;
        public final Point smallestSize;
        public final Point largestSize;

        public final DisplayMetrics metrics;

        @VisibleForTesting
        public Info(int id, int rotation, int singleFrameMs, Point realSize, Point smallestSize,
                Point largestSize, DisplayMetrics metrics) {
            this.id = id;
            this.rotation = rotation;
            this.singleFrameMs = singleFrameMs;
            this.realSize = realSize;
            this.smallestSize = smallestSize;
            this.largestSize = largestSize;
            this.metrics = metrics;
        }

        private Info(Context context) {
            this(context, context.getSystemService(DisplayManager.class)
                    .getDisplay(DEFAULT_DISPLAY));
        }

        public Info(Context context, Display display) {
            id = display.getDisplayId();
            rotation = display.getRotation();

            float refreshRate = display.getRefreshRate();
            singleFrameMs = refreshRate > 0 ? (int) (1000 / refreshRate) : 16;

            realSize = new Point();
            smallestSize = new Point();
            largestSize = new Point();
            display.getRealSize(realSize);
            display.getCurrentSizeRange(smallestSize, largestSize);

            metrics = context.getResources().getDisplayMetrics();
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
}
