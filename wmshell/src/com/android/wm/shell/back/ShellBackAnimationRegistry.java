/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.back;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Configuration;
import android.util.Log;
import android.util.SparseArray;
import android.window.BackNavigationInfo;

/** Registry for all types of default back animations */
public class ShellBackAnimationRegistry {
    private static final String TAG = "ShellBackPreview";

    private final SparseArray<BackAnimationRunner> mAnimationDefinition = new SparseArray<>();
    private ShellBackAnimation mDefaultCrossActivityAnimation;
    private final ShellBackAnimation mCustomizeActivityAnimation;
    private final ShellBackAnimation mCrossTaskAnimation;

    public ShellBackAnimationRegistry(
            @ShellBackAnimation.CrossActivity @Nullable ShellBackAnimation crossActivityAnimation,
            @ShellBackAnimation.CrossTask @Nullable ShellBackAnimation crossTaskAnimation,
            @ShellBackAnimation.DialogClose @Nullable ShellBackAnimation dialogCloseAnimation,
            @ShellBackAnimation.CustomizeActivity @Nullable
                    ShellBackAnimation customizeActivityAnimation,
            @ShellBackAnimation.ReturnToHome @Nullable
                    ShellBackAnimation defaultBackToHomeAnimation) {
        if (crossActivityAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_CROSS_ACTIVITY, crossActivityAnimation.getRunner());
        }
        if (crossTaskAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_CROSS_TASK, crossTaskAnimation.getRunner());
        }
        if (dialogCloseAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_DIALOG_CLOSE, dialogCloseAnimation.getRunner());
        }
        if (defaultBackToHomeAnimation != null) {
            mAnimationDefinition.set(
                    BackNavigationInfo.TYPE_RETURN_TO_HOME, defaultBackToHomeAnimation.getRunner());
        }

        mDefaultCrossActivityAnimation = crossActivityAnimation;
        mCustomizeActivityAnimation = customizeActivityAnimation;
        mCrossTaskAnimation = crossTaskAnimation;

        // TODO(b/236760237): register dialog close animation when it's completed.
    }

    void registerAnimation(
            @BackNavigationInfo.BackTargetType int type, @NonNull BackAnimationRunner runner) {
        mAnimationDefinition.set(type, runner);
        // Only happen in test
        if (BackNavigationInfo.TYPE_CROSS_ACTIVITY == type) {
            mDefaultCrossActivityAnimation = null;
        }
    }

    void unregisterAnimation(@BackNavigationInfo.BackTargetType int type) {
        mAnimationDefinition.remove(type);
        // Only happen in test
        if (BackNavigationInfo.TYPE_CROSS_ACTIVITY == type) {
            mDefaultCrossActivityAnimation = null;
        }
    }

    /**
     * Start the {@link BackAnimationRunner} associated with a back target type.
     *
     * @param type back target type
     * @return true if the animation is started, false if animation is not found for that type.
     */
    boolean startGesture(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        runner.startGesture();
        return true;
    }

    /**
     * Cancel the {@link BackAnimationRunner} associated with a back target type.
     *
     * @param type back target type
     * @return true if the animation is started, false if animation is not found for that type.
     */
    boolean cancel(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        runner.cancelAnimation();
        return true;
    }

    boolean isAnimationCancelledOrNull(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return true;
        }
        return runner.isAnimationCancelled();
    }

    boolean isWaitingAnimation(@BackNavigationInfo.BackTargetType int type) {
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            return false;
        }
        return runner.isWaitingAnimation();
    }

    void resetDefaultCrossActivity() {
        if (mDefaultCrossActivityAnimation == null
                || !mAnimationDefinition.contains(BackNavigationInfo.TYPE_CROSS_ACTIVITY)) {
            return;
        }
        mAnimationDefinition.set(
                BackNavigationInfo.TYPE_CROSS_ACTIVITY, mDefaultCrossActivityAnimation.getRunner());
    }

    void onConfigurationChanged(Configuration newConfig) {
        if (mCustomizeActivityAnimation != null) {
            mCustomizeActivityAnimation.onConfigurationChanged(newConfig);
        }
        if (mDefaultCrossActivityAnimation != null) {
            mDefaultCrossActivityAnimation.onConfigurationChanged(newConfig);
        }
        if (mCrossTaskAnimation != null) {
            mCrossTaskAnimation.onConfigurationChanged(newConfig);
        }
    }

    BackAnimationRunner getAnimationRunnerAndInit(BackNavigationInfo backNavigationInfo) {
        int type = backNavigationInfo.getType();
        // Initiate customized cross-activity animation, or fall back to cross activity animation
        if (type == BackNavigationInfo.TYPE_CROSS_ACTIVITY && mAnimationDefinition.contains(type)) {
            if (mCustomizeActivityAnimation != null
                    && mCustomizeActivityAnimation.prepareNextAnimation(
                            backNavigationInfo.getCustomAnimationInfo(), 0)) {
                mAnimationDefinition.get(type).resetWaitingAnimation();
                mAnimationDefinition.set(
                        BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                        mCustomizeActivityAnimation.getRunner());
            } else if (mDefaultCrossActivityAnimation != null) {
                mDefaultCrossActivityAnimation.prepareNextAnimation(null,
                        backNavigationInfo.getLetterboxColor());
            }
        }
        BackAnimationRunner runner = mAnimationDefinition.get(type);
        if (runner == null) {
            Log.e(
                    TAG,
                    "Animation didn't be defined for type "
                            + BackNavigationInfo.typeToString(type));
        }
        return runner;
    }
}
