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

package com.android.wm.shell.splitscreen;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.window.RemoteTransition;

import com.android.internal.logging.InstanceId;
import com.android.wm.shell.common.split.SplitScreenConstants.PersistentSnapPosition;
import com.android.wm.shell.common.split.SplitScreenConstants.SplitPosition;
import com.android.wm.shell.shared.annotations.ExternalThread;

import java.util.concurrent.Executor;

/**
 * Interface to engage split-screen feature.
 * TODO: Figure out which of these are actually needed outside of the Shell
 */
@ExternalThread
public interface SplitScreen {
    /**
     * Stage type isn't specified normally meaning to use what ever the default is.
     * E.g. exit split-screen and launch the app in fullscreen.
     */
    int STAGE_TYPE_UNDEFINED = -1;
    /**
     * The main stage type.
     * @see MainStage
     */
    int STAGE_TYPE_MAIN = 0;

    /**
     * The side stage type.
     * @see SideStage
     */
    int STAGE_TYPE_SIDE = 1;

    @IntDef(prefix = { "STAGE_TYPE_" }, value = {
            STAGE_TYPE_UNDEFINED,
            STAGE_TYPE_MAIN,
            STAGE_TYPE_SIDE
    })
    @interface StageType {}

    /** Callback interface for listening to changes in a split-screen stage. */
    interface SplitScreenListener {
        default void onStagePositionChanged(@StageType int stage, @SplitPosition int position) {}
        default void onTaskStageChanged(int taskId, @StageType int stage, boolean visible) {}
        default void onSplitBoundsChanged(Rect rootBounds, Rect mainBounds, Rect sideBounds) {}
        default void onSplitVisibilityChanged(boolean visible) {}
    }

    /**
     * Callback interface for listening to requests to enter split select. Used for desktop -> split
     */
    interface SplitSelectListener {
        default boolean onRequestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                int splitPosition, Rect taskBounds) {
            return false;
        }
    }

    /** Launches a pair of tasks into splitscreen */
    void startTasks(int taskId1, @Nullable Bundle options1, int taskId2,
            @Nullable Bundle options2, @SplitPosition int splitPosition,
            @PersistentSnapPosition int snapPosition, @Nullable RemoteTransition remoteTransition,
            InstanceId instanceId);

    /** Registers listener that gets split screen callback. */
    void registerSplitScreenListener(@NonNull SplitScreenListener listener,
            @NonNull Executor executor);

    /** Unregisters listener that gets split screen callback. */
    void unregisterSplitScreenListener(@NonNull SplitScreenListener listener);

    interface SplitInvocationListener {
        /**
         * Called whenever shell starts or stops the split screen animation
         * @param animationRunning if {@code true} the animation has begun, if {@code false} the
         *                         animation has finished
         */
        default void onSplitAnimationInvoked(boolean animationRunning) { }
    }

    /**
     * Registers a {@link SplitInvocationListener} to notify when the animation to enter split
     * screen has started and stopped
     *
     * @param executor callbacks to the listener will be executed on this executor
     */
    void registerSplitAnimationListener(@NonNull SplitInvocationListener listener,
            @NonNull Executor executor);

    /** Called when device waking up finished. */
    void onFinishedWakingUp();

    /** Called when requested to go to fullscreen from the current active split app. */
    void goToFullscreenFromSplit();

    /** Called when splitscreen focused app is changed. */
    void setSplitscreenFocus(boolean leftOrTop);

    /** Get a string representation of a stage type */
    static String stageTypeToString(@StageType int stage) {
        switch (stage) {
            case STAGE_TYPE_UNDEFINED: return "UNDEFINED";
            case STAGE_TYPE_MAIN: return "MAIN";
            case STAGE_TYPE_SIDE: return "SIDE";
            default: return "UNKNOWN(" + stage + ")";
        }
    }
}
