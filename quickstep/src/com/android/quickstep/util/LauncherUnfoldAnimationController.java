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
import android.os.Trace;
import android.util.FloatProperty;
import android.util.MathUtils;
import android.view.WindowManager;

import androidx.core.view.OneShotPreDrawListener;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Workspace;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.unfold.LauncherJankMonitorTransitionProgressListener;
import com.android.quickstep.util.unfold.PreemptiveUnfoldTransitionProgressProvider;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener;
import com.android.systemui.unfold.dagger.UnfoldMain;
import com.android.systemui.unfold.updates.RotationChangeProvider;
import com.android.systemui.unfold.util.NaturalRotationUnfoldProgressProvider;
import com.android.systemui.unfold.util.ScopedUnfoldTransitionProgressProvider;

/**
 * Controls animations that are happening during unfolding foldable devices
 */
public class LauncherUnfoldAnimationController implements OnDeviceProfileChangeListener {

    // Percentage of the width of the quick search bar that will be reduced
    // from the both sides of the bar when progress is 0
    private static final float MAX_WIDTH_INSET_FRACTION = 0.04f;
    private static final FloatProperty<Workspace<?>> WORKSPACE_SCALE_PROPERTY =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_UNFOLD_ANIMATION);
    private static final FloatProperty<Hotseat> HOTSEAT_SCALE_PROPERTY =
            HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_UNFOLD_ANIMATION);

    private final QuickstepLauncher mLauncher;
    private final ScopedUnfoldTransitionProgressProvider mProgressProvider;
    private final NaturalRotationUnfoldProgressProvider mNaturalOrientationProgressProvider;
    private final UnfoldMoveFromCenterHotseatAnimator mUnfoldMoveFromCenterHotseatAnimator;
    private final UnfoldMoveFromCenterWorkspaceAnimator mUnfoldMoveFromCenterWorkspaceAnimator;
    private final TransitionStatusProvider mExternalTransitionStatusProvider =
            new TransitionStatusProvider();
    private PreemptiveUnfoldTransitionProgressProvider mPreemptiveProgressProvider = null;
    private Boolean mIsTablet = null;

    private static final String TRACE_WAIT_TO_HANDLE_UNFOLD_TRANSITION =
            "LauncherUnfoldAnimationController#waitingForTheNextFrame";

    @Nullable
    private HorizontalInsettableView mQsbInsettable;

    public LauncherUnfoldAnimationController(
            QuickstepLauncher launcher,
            WindowManager windowManager,
            UnfoldTransitionProgressProvider unfoldTransitionProgressProvider,
            @UnfoldMain RotationChangeProvider rotationChangeProvider) {
        mLauncher = launcher;

        if (FeatureFlags.PREEMPTIVE_UNFOLD_ANIMATION_START.get()) {
            mPreemptiveProgressProvider = new PreemptiveUnfoldTransitionProgressProvider(
                    unfoldTransitionProgressProvider, launcher.getMainThreadHandler());
            mPreemptiveProgressProvider.init();

            mProgressProvider = new ScopedUnfoldTransitionProgressProvider(
                    mPreemptiveProgressProvider);
        } else {
            mProgressProvider = new ScopedUnfoldTransitionProgressProvider(
                    unfoldTransitionProgressProvider);
        }

        unfoldTransitionProgressProvider.addCallback(mExternalTransitionStatusProvider);
        unfoldTransitionProgressProvider.addCallback(
                new LauncherJankMonitorTransitionProgressListener(launcher::getRootView));

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

        mLauncher.addOnDeviceProfileChangeListener(this);
    }

    /**
     * Called when launcher is resumed
     */
    public void onResume() {
        Hotseat hotseat = mLauncher.getHotseat();
        if (hotseat != null && hotseat.getQsb() instanceof HorizontalInsettableView) {
            mQsbInsettable = (HorizontalInsettableView) hotseat.getQsb();
        }

        mProgressProvider.setReadyToHandleTransition(true);
    }

    private void preemptivelyStartAnimationOnNextFrame() {
        Trace.asyncTraceBegin(Trace.TRACE_TAG_APP,
                TRACE_WAIT_TO_HANDLE_UNFOLD_TRANSITION, /* cookie= */ 0);

        // Start the animation (and apply the transformations) in pre-draw listener to make sure
        // that the views are laid out as some transformations depend on the view sizes and position
        OneShotPreDrawListener.add(mLauncher.getWorkspace(),
                () -> {
                    Trace.asyncTraceEnd(Trace.TRACE_TAG_APP,
                            TRACE_WAIT_TO_HANDLE_UNFOLD_TRANSITION, /* cookie= */ 0);
                    mPreemptiveProgressProvider.preemptivelyStartTransition(
                            /* initialProgress= */ 0f);
                });
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
        mLauncher.removeOnDeviceProfileChangeListener(this);
    }

    /**
     * Called when launcher has finished binding its items
     */
    public void updateRegisteredViewsIfNeeded() {
        mUnfoldMoveFromCenterHotseatAnimator.updateRegisteredViewsIfNeeded();
        mUnfoldMoveFromCenterWorkspaceAnimator.updateRegisteredViewsIfNeeded();
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        if (!FeatureFlags.PREEMPTIVE_UNFOLD_ANIMATION_START.get()) {
            return;
        }

        if (mIsTablet != null && dp.isTablet != mIsTablet) {
            // We should preemptively start the animation only if:
            // - We changed to the unfolded screen
            // - SystemUI IPC connection is alive, so we won't end up in a situation that we won't
            //   receive transition progress events from SystemUI later because there was no
            //   IPC connection established (e.g. because of SystemUI crash)
            // - SystemUI has not already sent unfold animation progress events. This might happen
            //   if Launcher was not open during unfold, in this case we receive the configuration
            //   change only after we went back to home screen and we don't want to start the
            //   animation in this case.
            if (dp.isTablet
                    && SystemUiProxy.INSTANCE.get(mLauncher).isActive()
                    && !mExternalTransitionStatusProvider.hasRun()) {
                // Preemptively start the unfold animation to make sure that we have drawn
                // the first frame of the animation before the screen gets unblocked
                preemptivelyStartAnimationOnNextFrame();
            }

            if (!dp.isTablet) {
                mExternalTransitionStatusProvider.onFolded();
            }
        }

        mIsTablet = dp.isTablet;
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

        private static final float SCALE_LAUNCHER_FROM = 0.92f;

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
            setScale(MathUtils.constrainedMap(SCALE_LAUNCHER_FROM, 1, 0, 1, progress));
        }

        private void setScale(float value) {
            WORKSPACE_SCALE_PROPERTY.setValue(mLauncher.getWorkspace(), value);
            HOTSEAT_SCALE_PROPERTY.setValue(mLauncher.getHotseat(), value);
        }
    }

    /**
     * Class to track the current status of the external transition provider (the events are coming
     * from the SystemUI side through IPC), it allows to check if the transition has already
     * finished or currently running on the SystemUI side since last unfold.
     */
    private static class TransitionStatusProvider implements TransitionProgressListener {

        private boolean mHasRun = false;

        @Override
        public void onTransitionStarted() {
            markAsRun();
        }

        @Override
        public void onTransitionProgress(float progress) {
            markAsRun();
        }

        @Override
        public void onTransitionFinished() {
            markAsRun();
        }

        /**
         * Called when the device is folded, so we can reset the status of the animation
         */
        public void onFolded() {
            mHasRun = false;
        }

        /**
         * Returns true if there was an animation already (or it is currently running) after
         * unfolding the device
         */
        public boolean hasRun() {
            return mHasRun;
        }

        private void markAsRun() {
            mHasRun = true;
        }
    }
}
