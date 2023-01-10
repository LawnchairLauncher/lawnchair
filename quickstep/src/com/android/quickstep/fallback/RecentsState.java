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
package com.android.quickstep.fallback;

import static com.android.launcher3.LauncherState.FLAG_CLOSE_POPUPS;
import static com.android.launcher3.uioverrides.states.BackgroundAppState.getOverviewScaleAndOffsetForBackgroundState;
import static com.android.launcher3.uioverrides.states.OverviewModalTaskState.getOverviewScaleAndOffsetForModalState;

import android.content.Context;
import android.graphics.Color;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.util.Themes;
import com.android.quickstep.RecentsActivity;

/**
 * State definition for Fallback recents
 */
public class RecentsState implements BaseState<RecentsState> {

    private static final int FLAG_MODAL = BaseState.getFlag(0);
    private static final int FLAG_CLEAR_ALL_BUTTON = BaseState.getFlag(1);
    private static final int FLAG_FULL_SCREEN = BaseState.getFlag(2);
    private static final int FLAG_OVERVIEW_ACTIONS = BaseState.getFlag(3);
    private static final int FLAG_SHOW_AS_GRID = BaseState.getFlag(4);
    private static final int FLAG_SCRIM = BaseState.getFlag(5);
    private static final int FLAG_LIVE_TILE = BaseState.getFlag(6);
    private static final int FLAG_OVERVIEW_UI = BaseState.getFlag(7);
    private static final int FLAG_TASK_THUMBNAIL_SPLASH = BaseState.getFlag(8);

    public static final RecentsState DEFAULT = new RecentsState(0,
            FLAG_DISABLE_RESTORE | FLAG_CLEAR_ALL_BUTTON | FLAG_OVERVIEW_ACTIONS | FLAG_SHOW_AS_GRID
                    | FLAG_SCRIM | FLAG_LIVE_TILE | FLAG_OVERVIEW_UI);
    public static final RecentsState MODAL_TASK = new ModalState(1,
            FLAG_DISABLE_RESTORE | FLAG_CLEAR_ALL_BUTTON | FLAG_OVERVIEW_ACTIONS | FLAG_MODAL
                    | FLAG_SHOW_AS_GRID | FLAG_SCRIM | FLAG_LIVE_TILE | FLAG_OVERVIEW_UI);
    public static final RecentsState BACKGROUND_APP = new BackgroundAppState(2,
            FLAG_DISABLE_RESTORE | FLAG_NON_INTERACTIVE | FLAG_FULL_SCREEN | FLAG_OVERVIEW_UI
                    | FLAG_TASK_THUMBNAIL_SPLASH);
    public static final RecentsState HOME = new RecentsState(3, 0);
    public static final RecentsState BG_LAUNCHER = new LauncherState(4, 0);
    public static final RecentsState OVERVIEW_SPLIT_SELECT = new RecentsState(5,
            FLAG_SHOW_AS_GRID | FLAG_SCRIM | FLAG_OVERVIEW_UI | FLAG_CLOSE_POPUPS
                    | FLAG_DISABLE_RESTORE);

    public final int ordinal;
    private final int mFlags;

    private static final float NO_OFFSET = 0;
    private static final float NO_SCALE = 1;

    public RecentsState(int id, int flags) {
        this.ordinal = id;
        this.mFlags = flags;
    }


    @Override
    public String toString() {
        return "Ordinal-" + ordinal;
    }

    @Override
    public final boolean hasFlag(int mask) {
        return (mFlags & mask) != 0;
    }

    @Override
    public int getTransitionDuration(Context context, boolean isToState) {
        return 250;
    }

    @Override
    public RecentsState getHistoryForState(RecentsState previousState) {
        return DEFAULT;
    }

    /**
     * For this state, how modal should over view been shown. 0 modalness means all tasks drawn,
     * 1 modalness means the current task is show on its own.
     */
    public float getOverviewModalness() {
        return hasFlag(FLAG_MODAL) ? 1 : 0;
    }

    public boolean isFullScreen() {
        return hasFlag(FLAG_FULL_SCREEN);
    }

    /**
     * For this state, whether clear all button should be shown.
     */
    public boolean hasClearAllButton() {
        return hasFlag(FLAG_CLEAR_ALL_BUTTON);
    }

    /**
     * For this state, whether overview actions should be shown.
     */
    public boolean hasOverviewActions() {
        return hasFlag(FLAG_OVERVIEW_ACTIONS);
    }

    /**
     * For this state, whether live tile should be shown.
     */
    public boolean hasLiveTile() {
        return hasFlag(FLAG_LIVE_TILE);
    }

    /**
     * For this state, what color scrim should be drawn behind overview.
     */
    public int getScrimColor(RecentsActivity activity) {
        return hasFlag(FLAG_SCRIM) ? Themes.getAttrColor(activity, R.attr.overviewScrimColor)
                : Color.TRANSPARENT;
    }

    public float[] getOverviewScaleAndOffset(RecentsActivity activity) {
        return new float[] { NO_SCALE, NO_OFFSET };
    }

    /**
     * For this state, whether tasks should layout as a grid rather than a list.
     */
    public boolean displayOverviewTasksAsGrid(DeviceProfile deviceProfile) {
        return hasFlag(FLAG_SHOW_AS_GRID) && deviceProfile.isTablet;
    }

    @Override
    public boolean showTaskThumbnailSplash() {
        return hasFlag(FLAG_TASK_THUMBNAIL_SPLASH);
    }

    /**
     * True if the state has overview panel visible.
     */
    public boolean overviewUi() {
        return hasFlag(FLAG_OVERVIEW_UI);
    }

    private static class ModalState extends RecentsState {

        public ModalState(int id, int flags) {
            super(id, flags);
        }

        @Override
        public float[] getOverviewScaleAndOffset(RecentsActivity activity) {
            return getOverviewScaleAndOffsetForModalState(activity);
        }
    }

    private static class BackgroundAppState extends RecentsState {
        public BackgroundAppState(int id, int flags) {
            super(id, flags);
        }

        @Override
        public float[] getOverviewScaleAndOffset(RecentsActivity activity) {
            return getOverviewScaleAndOffsetForBackgroundState(activity);
        }
    }

    private static class LauncherState extends RecentsState {
        LauncherState(int id, int flags) {
            super(id, flags);
        }

        @Override
        public float[] getOverviewScaleAndOffset(RecentsActivity activity) {
            return new float[] { NO_SCALE, 1 };
        }
    }
}
