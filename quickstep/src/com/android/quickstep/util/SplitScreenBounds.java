/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.view.WindowInsets.Type;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.R;
import com.android.launcher3.util.DefaultDisplay;
import com.android.launcher3.util.WindowBounds;

import java.util.ArrayList;

/**
 * Utility class to hold the information abound a window bounds for split screen
 */
@TargetApi(Build.VERSION_CODES.R)
public class SplitScreenBounds {

    public static final SplitScreenBounds INSTANCE = new SplitScreenBounds();
    private final ArrayList<OnChangeListener> mListeners = new ArrayList<>();

    @Nullable
    private WindowBounds mBounds;

    private SplitScreenBounds() { }

    @UiThread
    public void setSecondaryWindowBounds(@NonNull WindowBounds bounds) {
        if (!bounds.equals(mBounds)) {
            mBounds = bounds;
            for (OnChangeListener listener : mListeners) {
                listener.onSecondaryWindowBoundsChanged();
            }
        }
    }

    public @NonNull WindowBounds getSecondaryWindowBounds(Context context) {
        if (mBounds == null) {
            mBounds = createDefaultWindowBounds(context);
        }
        return mBounds;
    }

    /**
     * Creates window bounds as 50% of device size
     */
    private static WindowBounds createDefaultWindowBounds(Context context) {
        WindowMetrics wm = context.getSystemService(WindowManager.class).getMaximumWindowMetrics();
        Insets insets = wm.getWindowInsets().getInsets(Type.systemBars());

        WindowBounds bounds = new WindowBounds(wm.getBounds(),
                new Rect(insets.left, insets.top, insets.right, insets.bottom));
        int rotation = DefaultDisplay.INSTANCE.get(context).getInfo().rotation;
        int halfDividerSize = context.getResources()
                .getDimensionPixelSize(R.dimen.multi_window_task_divider_size) / 2;

        if (rotation == ROTATION_0 || rotation == ROTATION_180) {
            bounds.bounds.top = bounds.insets.top + bounds.availableSize.y / 2 + halfDividerSize;
            bounds.insets.top = 0;
        } else {
            bounds.bounds.left = bounds.insets.left + bounds.availableSize.x / 2 + halfDividerSize;
            bounds.insets.left = 0;
        }
        return new WindowBounds(bounds.bounds, bounds.insets);
    }

    public void addOnChangeListener(OnChangeListener listener) {
        mListeners.add(listener);
    }

    public void removeOnChangeListener(OnChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Interface to receive window bounds changes
     */
    public interface OnChangeListener {

        /**
         * Called when window bounds for secondary window changes
         */
        void onSecondaryWindowBoundsChanged();
    }
}
