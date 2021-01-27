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

import static com.android.launcher3.uioverrides.states.BackgroundAppState.getOverviewScaleAndOffsetForBackgroundState;
import static com.android.launcher3.uioverrides.states.OverviewModalTaskState.getOverviewScaleAndOffsetForModalState;

import android.content.Context;

import com.android.launcher3.statemanager.BaseState;
import com.android.quickstep.RecentsActivity;

/**
 * State definition for Fallback recents
 */
public class RecentsState implements BaseState<RecentsState> {

    private static final int FLAG_MODAL = BaseState.getFlag(0);
    private static final int FLAG_HAS_BUTTONS = BaseState.getFlag(1);
    private static final int FLAG_FULL_SCREEN = BaseState.getFlag(2);

    public static final RecentsState DEFAULT = new RecentsState(0, FLAG_HAS_BUTTONS);
    public static final RecentsState MODAL_TASK = new ModalState(1,
            FLAG_DISABLE_RESTORE | FLAG_HAS_BUTTONS | FLAG_MODAL);
    public static final RecentsState BACKGROUND_APP = new BackgroundAppState(2,
            FLAG_DISABLE_RESTORE | FLAG_NON_INTERACTIVE | FLAG_FULL_SCREEN);

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
    public int getTransitionDuration(Context context) {
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

    public boolean hasButtons() {
        return hasFlag(FLAG_HAS_BUTTONS);
    }

    public float[] getOverviewScaleAndOffset(RecentsActivity activity) {
        return new float[] { NO_SCALE, NO_OFFSET };
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
}
