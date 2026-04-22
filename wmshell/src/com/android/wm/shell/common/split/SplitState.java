/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.common.split;

import static com.android.wm.shell.shared.split.SplitScreenConstants.NOT_IN_SPLIT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_10_45_45;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_3_45_45_10;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SplitScreenState;

import android.annotation.NonNull;
import android.graphics.Rect;
import android.graphics.RectF;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A class that manages the "state" of split screen. See {@link SplitScreenState} for definitions.
 */
public class SplitState {
    private @SplitScreenState int mState = NOT_IN_SPLIT;
    private SplitSpec mSplitSpec;
    private final Set<SplitStateChangeListener> mListeners = new HashSet<>();


    /** Updates the current state of split screen on this device. */
    public void set(@SplitScreenState int newState) {
        mState = newState;
        notifyListeners();
    }

    /** Reports the current state of split screen on this device. */
    public @SplitScreenState int get() {
        return mState;
    }

    /** Sets NOT_IN_SPLIT when user exits split. */
    public void exit() {
        set(NOT_IN_SPLIT);
    }

    /** Refresh the valid layouts for this display/orientation. */
    public void populateLayouts(Rect displayBounds, int dividerSize, boolean isLeftRightSplit,
            Rect pinnedTaskbarInsets) {
        mSplitSpec =
                new SplitSpec(displayBounds, dividerSize, isLeftRightSplit, pinnedTaskbarInsets);
    }

    /** Returns the layout associated with a given split state. */
    public List<RectF> getLayout(@SplitScreenState int state) {
        return mSplitSpec.getSpec(state);
    }

    /** Returns the layout associated with the current split state. */
    public List<RectF> getCurrentLayout() {
        return getLayout(mState);
    }

    /** Returns whether a given Rect is partially offscreen on the current display. */
    boolean isOffscreen(Rect rect) {
        return mSplitSpec.isOffscreen(rect);
    }

    /** @return {@code true} if at least one app is partially offscreen in the current layout. */
    public boolean currentStateSupportsOffscreenApps() {
        return mState == SNAP_TO_2_10_90
                || mState == SNAP_TO_2_90_10
                || mState == SNAP_TO_3_10_45_45
                || mState == SNAP_TO_3_45_45_10;
    }

    /**
     * Registers a listener to receive notifications when the split state changes.
     * Multiple instances of the same listener will not be tolerated. Don't be weird.
     *
     * @param listener The listener to register.
     */
    public void registerSplitStateChangeListener(@NonNull SplitStateChangeListener listener) {
        mListeners.add(listener);
    }

    /**
     * Unregisters a listener, so it no longer receives notifications.
     *
     * @param listener The listener to unregister.
     */
    public void unregisterSplitStateChangeListener(@NonNull SplitStateChangeListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Notifies all registered listeners of the current split state.
     */
    private void notifyListeners() {
        for (SplitStateChangeListener listener : mListeners) {
            listener.onSplitStateChanged(mState);
        }
    }

    /**
     * An interface for listeners that want to be notified of split state changes.
     */
    public interface SplitStateChangeListener {
        /**
         * Called when the split state of the splitter changes.
         *
         * @param splitState The new split state.
         */
        void onSplitStateChanged(@SplitScreenState int splitState);
    }
}
