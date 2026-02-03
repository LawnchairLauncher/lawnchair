/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.pip2.phone.transition;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.Surface.ROTATION_0;

import android.annotation.NonNull;
import android.app.PictureInPictureParams;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.annotation.Nullable;

import com.android.internal.util.Preconditions;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;

/**
 * A set of utility methods to help resolve PiP transitions.
 */
public class PipTransitionUtils {

    /**
     * @return change for a pinned mode task; null if no such task is in the list of changes.
     */
    @Nullable
    public static TransitionInfo.Change getPipChange(TransitionInfo info) {
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
                return change;
            }
        }
        return null;
    }

    /**
     * @return change for a task with the provided token; null if no task with such token found.
     */
    @Nullable
    public static TransitionInfo.Change getChangeByToken(TransitionInfo info,
            WindowContainerToken token) {
        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getToken().equals(token)) {
                return change;
            }
        }
        return null;
    }

    /**
     * @return a change representing a config-at-end activity for ancestor.
     */
    @Nullable
    public static TransitionInfo.Change getDeferConfigActivityChange(TransitionInfo info,
            @NonNull WindowContainerToken ancestor) {
        final TransitionInfo.Change ancestorChange =
                PipTransitionUtils.getChangeByToken(info, ancestor);
        if (ancestorChange == null) return null;

        // Iterate through changes bottom-to-top, going up the parent chain starting with ancestor.
        TransitionInfo.Change lastPipChildChange = ancestorChange;
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            TransitionInfo.Change change = info.getChanges().get(i);
            if (change == ancestorChange) continue;

            if (change.getParent() != null
                    && change.getParent().equals(lastPipChildChange.getContainer())) {
                // Found a child of the last cached child along the ancestral chain.
                lastPipChildChange = change;
                if (change.getTaskInfo() == null
                        && change.hasFlags(TransitionInfo.FLAG_CONFIG_AT_END)) {
                    // If this is a config-at-end activity change, then we found the chain leaf.
                    return change;
                }
            }
        }
        return null;
    }

    /**
     * @return the leash to interact with the container this change represents.
     * @throws NullPointerException if the leash is null.
     */
    @NonNull
    public static SurfaceControl getLeash(TransitionInfo.Change change) {
        SurfaceControl leash = change.getLeash();
        Preconditions.checkNotNull(leash, "Leash is null for change=" + change);
        return leash;
    }

    /**
     * Get the rotation delta in a potential fixed rotation transition.
     *
     * Whenever PiP participates in fixed rotation, its actual orientation isn't updated
     * in the initial transition as per the async rotation convention.
     *
     * @param pipChange PiP change to verify that PiP task's rotation wasn't updated already.
     * @param pipDisplayLayoutState display layout state that PiP component keeps track of.
     */
    @Surface.Rotation
    public static int getFixedRotationDelta(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change pipChange,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState) {
        TransitionInfo.Change fixedRotationChange = findFixedRotationChange(info);
        int startRotation = pipChange.getStartRotation();
        if (pipChange.getEndRotation() != ROTATION_UNDEFINED
                && startRotation != pipChange.getEndRotation()) {
            // If PiP change was collected along with the display change and the orientation change
            // happened in sync with the PiP change, then do not treat this as fixed-rotation case.
            return ROTATION_0;
        }

        int endRotation = fixedRotationChange != null
                ? fixedRotationChange.getEndFixedRotation() : pipDisplayLayoutState.getRotation();
        int delta = endRotation == ROTATION_UNDEFINED ? ROTATION_0
                : startRotation - endRotation;
        return delta;
    }

    /**
     * Gets a change amongst the transition targets that is in a different final orientation than
     * the display, signalling a potential fixed rotation transition.
     */
    @Nullable
    public static TransitionInfo.Change findFixedRotationChange(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (change.getEndFixedRotation() != ROTATION_UNDEFINED) {
                return change;
            }
        }
        return null;
    }

    /**
     * @return {@link PictureInPictureParams} provided by the client from the PiP change.
     */
    @NonNull
    public static PictureInPictureParams getPipParams(@NonNull TransitionInfo.Change pipChange) {
        return pipChange.getTaskInfo().pictureInPictureParams != null
                ? pipChange.getTaskInfo().pictureInPictureParams
                : new PictureInPictureParams.Builder().build();
    }
}
