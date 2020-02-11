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

package com.android.launcher3.graphics;

import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.LauncherState.OVERVIEW;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * View scrim which draws behind overview (recent apps).
 */
public class OverviewScrim extends Scrim {

    private @NonNull View mStableScrimmedView;
    // Might be higher up if mStableScrimmedView is invisible.
    private @Nullable View mCurrentScrimmedView;

    public OverviewScrim(View view) {
        super(view);
        mStableScrimmedView = mCurrentScrimmedView = mLauncher.getOverviewPanel();

        onExtractedColorsChanged(mWallpaperColorInfo);
    }

    public void onInsetsChanged(Rect insets) {
        mStableScrimmedView = (OVERVIEW.getVisibleElements(mLauncher) & HOTSEAT_ICONS) != 0
                ? mLauncher.getHotseat()
                : mLauncher.getOverviewPanel();
    }

    public void updateCurrentScrimmedView(ViewGroup root) {
        // Find the lowest view that is at or above the view we want to show the scrim behind.
        mCurrentScrimmedView = mStableScrimmedView;
        int currentIndex = root.indexOfChild(mCurrentScrimmedView);
        final int childCount = root.getChildCount();
        while (mCurrentScrimmedView.getVisibility() != VISIBLE && currentIndex < childCount) {
            currentIndex++;
            mCurrentScrimmedView = root.getChildAt(currentIndex);
        }
    }

    /**
     * @return The view to draw the scrim behind.
     */
    public View getScrimmedView() {
        return mCurrentScrimmedView;
    }
}
