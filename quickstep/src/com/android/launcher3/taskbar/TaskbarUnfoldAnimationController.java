/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.view.View;
import android.view.WindowManager;

import com.android.quickstep.util.LauncherViewsMoveFromCenterTranslationApplier;
import com.android.systemui.shared.animation.UnfoldMoveFromCenterAnimator;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.NaturalRotationUnfoldProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

import java.io.PrintWriter;

/**
 * Controls animation of taskbar icons when unfolding foldable devices
 */
public class TaskbarUnfoldAnimationController implements
        TaskbarControllers.LoggableTaskbarController {

    private final ScopedUnfoldTransitionProgressProvider mScopedUnfoldTransitionProgressProvider;
    private final NaturalRotationUnfoldProgressProvider mNaturalUnfoldTransitionProgressProvider;
    private final UnfoldMoveFromCenterAnimator mMoveFromCenterAnimator;
    private final TransitionListener mTransitionListener = new TransitionListener();
    private TaskbarViewController mTaskbarViewController;

    public TaskbarUnfoldAnimationController(BaseTaskbarContext context,
            ScopedUnfoldTransitionProgressProvider source,
            WindowManager windowManager,
            RotationChangeProvider rotationChangeProvider) {
        mScopedUnfoldTransitionProgressProvider = source;
        mNaturalUnfoldTransitionProgressProvider =
                new NaturalRotationUnfoldProgressProvider(context,
                        rotationChangeProvider,
                        source);
        mMoveFromCenterAnimator = new UnfoldMoveFromCenterAnimator(windowManager,
                new LauncherViewsMoveFromCenterTranslationApplier());
    }

    /**
     * Initializes the controller
     *
     * @param taskbarControllers references to all other taskbar controllers
     */
    public void init(TaskbarControllers taskbarControllers) {
        mNaturalUnfoldTransitionProgressProvider.init();
        mTaskbarViewController = taskbarControllers.taskbarViewController;
        mTaskbarViewController.addOneTimePreDrawListener(() ->
                mScopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(true));
        mNaturalUnfoldTransitionProgressProvider.addCallback(mTransitionListener);
    }

    /**
     * Destroys the controller
     */
    public void onDestroy() {
        mScopedUnfoldTransitionProgressProvider.setReadyToHandleTransition(false);
        mNaturalUnfoldTransitionProgressProvider.removeCallback(mTransitionListener);
        mNaturalUnfoldTransitionProgressProvider.destroy();
        mTaskbarViewController = null;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarUnfoldAnimationController:");
    }

    private class TransitionListener implements TransitionProgressListener {

        @Override
        public void onTransitionStarted() {
            mMoveFromCenterAnimator.updateDisplayProperties();
            View[] icons = mTaskbarViewController.getIconViews();
            for (View icon : icons) {
                // TODO(b/193794563) we should re-register views if they are re-bound/re-inflated
                //                   during the animation
                mMoveFromCenterAnimator.registerViewForAnimation(icon);
            }

            mMoveFromCenterAnimator.onTransitionStarted();
        }

        @Override
        public void onTransitionFinished() {
            mMoveFromCenterAnimator.onTransitionFinished();
            mMoveFromCenterAnimator.clearRegisteredViews();
        }

        @Override
        public void onTransitionProgress(float progress) {
            mMoveFromCenterAnimator.onTransitionProgress(progress);
        }
    }
}
