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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;

/**
 * Utility class to cache properties of default display to avoid a system RPC on every call.
 */
public class DefaultDisplay implements DisplayListener {

    public static final MainThreadInitializedObject<DefaultDisplay> INSTANCE =
            new MainThreadInitializedObject<>(DefaultDisplay::new);

    private static final String TAG = "DefaultDisplay";

    public static final int CHANGE_SIZE = 1 << 0;
    public static final int CHANGE_ROTATION = 1 << 1;
    public static final int CHANGE_FRAME_DELAY = 1 << 2;

    private final Context mContext;
    private final int mId;
    private final ArrayList<DisplayInfoChangeListener> mListeners = new ArrayList<>();
    private final Handler mChangeHandler;
    private Info mInfo;

    private DefaultDisplay(Context context) {
        mContext = context;
        mInfo = new Info(context);
        mId = mInfo.id;
        mChangeHandler = new Handler(this::onChange);

        context.getSystemService(DisplayManager.class)
                .registerDisplayListener(this, UI_HELPER_EXECUTOR.getHandler());
    }

    @Override
    public final void onDisplayAdded(int displayId) {  }

    @Override
    public final void onDisplayRemoved(int displayId) { }

    @Override
    public final void onDisplayChanged(int displayId) {
        if (displayId != mId) {
            return;
        }

        Info oldInfo = mInfo;
        Info info = new Info(mContext);

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
            mChangeHandler.sendEmptyMessage(change);
        }
    }

    public static int getSingleFrameMs(Context context) {
        return INSTANCE.get(context).getInfo().singleFrameMs;
    }

    public Info getInfo() {
        return mInfo;
    }

    public void addChangeListener(DisplayInfoChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeChangeListener(DisplayInfoChangeListener listener) {
        mListeners.remove(listener);
    }

    private boolean onChange(Message msg) {
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onDisplayInfoChanged(mInfo, msg.what);
        }
        return true;
    }

    public static class Info {

        public final int id;
        public final int rotation;
        public final int singleFrameMs;

        public final Point realSize;
        public final Point smallestSize;
        public final Point largestSize;

        public final DisplayMetrics metrics;

        private Info(Context context) {
            Display display = context.getSystemService(WindowManager.class).getDefaultDisplay();

            id = display.getDisplayId();
            rotation = display.getRotation();

            float refreshRate = display.getRefreshRate();
            singleFrameMs = refreshRate > 0 ? (int) (1000 / refreshRate) : 16;

            realSize = new Point();
            smallestSize = new Point();
            largestSize = new Point();
            display.getRealSize(realSize);
            display.getCurrentSizeRange(smallestSize, largestSize);

            metrics = new DisplayMetrics();
            display.getMetrics(metrics);
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

    /**
     * Interface for listening for display changes
     */
    public interface DisplayInfoChangeListener {

        void onDisplayInfoChanged(Info info, int flags);
    }
}
