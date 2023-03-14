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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_UNFOLD_ANIMATION;
import static com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY;

import android.annotation.Nullable;
import android.util.FloatProperty;
import android.util.MathUtils;
import android.view.WindowManager;

import androidx.core.view.OneShotPreDrawListener;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.Workspace;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.NaturalRotationUnfoldProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

/**
 * Controls animations that are happening during unfolding foldable devices
 */
public class LauncherUnfoldAnimationController {

    // Percentage of the width of the quick search bar that will be reduced
    // from the both sides of the bar when progress is 0
    private static final float MAX_WIDTH_INSET_FRACTION = 0.15f;
    private static final FloatProperty<Workspace<?>> WORKSPACE_SCALE_PROPERTY =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_UNFOLD_ANIMATION);
    private static final FloatProperty<Hotseat> HOTSEAT_SCALE_PROPERTY =
            HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_UNFOLD_ANIMATION);

    private final Launcher mLauncher;
    private final ScopedUnfoldTransitionProgressProvider mProgressProvider;
    private final NaturalRotationUnfoldProgressProvider mNaturalOrientationProgressProvider;
    private final UnfoldMoveFromCenterHotseatAnimator mUnfoldMoveFromCenterHotseatAnimator;
    private final UnfoldMoveFromCenterWorkspaceAnimator mUnfoldMoveFromCenterWorkspaceAnimator;

    @Nullable
    private HorizontalInsettableView mQsbInsettable;

    public LauncherUnfoldAnimationController(
            Launcher launcher,
            WindowManager windowManager,
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider,
            RotationChangeProvider rotationChangeProvider) {
        mLauncher = launcher;
        mProgressProvider = new ScopedUnfoldTransitionProgressProvider(
                unfoldTransitionProgressProvider);
        mUnfoldMoveFromCenterHotseatAnimator = new UnfoldMoveFromCenterHotseatAnimator(launcher,
                windowManager, rotationChangeProvider);
        mUnfoldMoveFromCenterWorkspaceAnimator = new UnfoldMoveFromCenterWorkspaceAnimator(launcher,
                windowManager, rotationChangeProvider);
        mNaturalOrientationProgressProvider = new NaturalRotationUnfoldProgressProvider(launcher,
                rotationChangeProvider, mProgressProvider);
        mNaturalOrientationProgressProvider.init();

        // Animated in all orientations
        mProgressProvider.addCallback(mUnfoldMoveFromCenterWorkspaceAnimator);
        mProgressProvider.addCallback(new LauncherScaleAnimationListener());

        // Animated only in natural orientation
        mNaturalOrientationProgressProvider.addCallback(new QsbAnimationListener());
        mNaturalOrientationProgressProvider.addCallback(mUnfoldMoveFromCenterHotseatAnimator);
    }

    /**
     * Called when launcher is resumed
     */
    public void onResume() {
        Hotseat hotseat = mLauncher.getHotseat();
        if (hotseat != null && hotseat.getQsb() instanceof HorizontalInsettableView) {
            mQsbInsettable = (HorizontalInsettableView) hotseat.getQsb();
        }

        OneShotPreDrawListener.add(mLauncher.getWorkspace(),
                () -> mProgressProvider.setReadyToHandleTransition(true));
    }

    /**
     * Called when launcher activity is paused
     */
    public void onPause() {
        mProgressProvider.setReadyToHandleTransition(false);
        mQsbInsettable = null;
    }

    /**
     * Called when launcher activity is destroyed
     */
    public void onDestroy() {
        mProgressProvider.destroy();
        mNaturalOrientationProgressProvider.destroy();
    }

    /** Called when launcher finished binding its items. */
    public void updateRegisteredViewsIfNeeded() {
        mUnfoldMoveFromCenterHotseatAnimator.updateRegisteredViewsIfNeeded();
        mUnfoldMoveFromCenterWorkspaceAnimator.updateRegisteredViewsIfNeeded();
    }

    private class QsbAnimationListener implements TransitionProgressListener {

        @Override
        public void onTransitionStarted() {
        }

        @Override
        public void onTransitionFinished() {
            if (mQsbInsettable != null) {
                mQsbInsettable.setHorizontalInsets(0);
            }
        }

        @Override
        public void onTransitionProgress(float progress) {
            if (mQsbInsettable != null) {
                float insetPercentage = (1 - progress) * MAX_WIDTH_INSET_FRACTION;
                mQsbInsettable.setHorizontalInsets(insetPercentage);
            }
        }
    }

    private class LauncherScaleAnimationListener implements TransitionProgressListener {

        @Override
        public void onTransitionStarted() {
            mLauncher.getWorkspace().setPivotToScaleWithSelf(mLauncher.getHotseat());
        }

        @Override
        public void onTransitionFinished() {
            setScale(1);
        }

        @Override
        public void onTransitionProgress(float progress) {
            setScale(MathUtils.constrainedMap(0.85f, 1, 0, 1, progress));
        }

        private void setScale(float value) {
            WORKSPACE_SCALE_PROPERTY.setValue(mLauncher.getWorkspace(), value);
            HOTSEAT_SCALE_PROPERTY.setValue(mLauncher.getHotseat(), value);
        }
    }
}
