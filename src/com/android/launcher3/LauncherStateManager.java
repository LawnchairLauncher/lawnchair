/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.launcher3.LauncherState.NORMAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import com.android.launcher3.anim.AnimationLayerSet;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.uioverrides.UiFactory;

/**
 * TODO: figure out what kind of tests we can write for this
 *
 * Things to test when changing the following class.
 *   - Home from workspace
 *          - from center screen
 *          - from other screens
 *   - Home from all apps
 *          - from center screen
 *          - from other screens
 *   - Back from all apps
 *          - from center screen
 *          - from other screens
 *   - Launch app from workspace and quit
 *          - with back
 *          - with home
 *   - Launch app from all apps and quit
 *          - with back
 *          - with home
 *   - Go to a screen that's not the default, then all
 *     apps, and launch and app, and go back
 *          - with back
 *          -with home
 *   - On workspace, long press power and go back
 *          - with back
 *          - with home
 *   - On all apps, long press power and go back
 *          - with back
 *          - with home
 *   - On workspace, power off
 *   - On all apps, power off
 *   - Launch an app and turn off the screen while in that app
 *          - Go back with home key
 *          - Go back with back key  TODO: make this not go to workspace
 *          - From all apps
 *          - From workspace
 *   - Enter and exit car mode (becuase it causes an extra configuration changed)
 *          - From all apps
 *          - From the center workspace
 *          - From another workspace
 */
public class LauncherStateManager {

    public static final String TAG = "StateManager";

    private final AnimationConfig mConfig = new AnimationConfig();
    private final Handler mUiHandler;
    private final Launcher mLauncher;

    private StateHandler[] mStateHandlers;
    private LauncherState mState = NORMAL;

    private StateListener mStateListener;

    public LauncherStateManager(Launcher l) {
        mUiHandler = new Handler(Looper.getMainLooper());
        mLauncher = l;
    }

    public LauncherState getState() {
        return mState;
    }

    private StateHandler[] getStateHandlers() {
        if (mStateHandlers == null) {
            mStateHandlers = UiFactory.getStateHandler(mLauncher);
        }
        return mStateHandlers;
    }

    public void setStateListener(StateListener stateListener) {
        mStateListener = stateListener;
    }

    /**
     * @see #goToState(LauncherState, boolean, Runnable)
     */
    public void goToState(LauncherState state) {
        goToState(state, true, 0, null);
    }

    /**
     * @see #goToState(LauncherState, boolean, Runnable)
     */
    public void goToState(LauncherState state, boolean animated) {
        goToState(state, animated, 0, null);
    }

    /**
     * Changes the Launcher state to the provided state.
     *
     * @param animated false if the state should change immediately without any animation,
     *                true otherwise
     * @paras onCompleteRunnable any action to perform at the end of the transition, of null.
     */
    public void goToState(LauncherState state, boolean animated, Runnable onCompleteRunnable) {
        goToState(state, animated, 0, onCompleteRunnable);
    }

    /**
     * Changes the Launcher state to the provided state after the given delay.
     */
    public void goToState(LauncherState state, long delay, Runnable onCompleteRunnable) {
        goToState(state, true, delay, onCompleteRunnable);
    }

    /**
     * Changes the Launcher state to the provided state after the given delay.
     */
    public void goToState(LauncherState state, long delay) {
        goToState(state, true, delay, null);
    }

    private void goToState(LauncherState state, boolean animated, long delay,
            Runnable onCompleteRunnable) {
        if (mLauncher.isInState(state) && mConfig.mCurrentAnimation == null) {

            // Run any queued runnable
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        // Cancel the current animation
        mConfig.reset();

        if (!animated) {
            onStateTransitionStart(state);
            for (StateHandler handler : getStateHandlers()) {
                handler.setState(state);
            }
            if (mStateListener != null) {
                mStateListener.onStateSetImmediately(state);
            }
            onStateTransitionEnd(state);

            // Run any queued runnable
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        // Since state NORMAL can be reached from multiple states, just assume that the
        // transition plays in reverse and use the same duration as previous state.
        mConfig.duration = state == NORMAL ? mState.transitionDuration : state.transitionDuration;

        AnimatorSet animation = createAnimationToNewWorkspaceInternal(
                state, new AnimatorSetBuilder(), onCompleteRunnable);
        Runnable runnable = new StartAnimRunnable(animation, state.getFinalFocus(mLauncher));
        if (delay > 0) {
            mUiHandler.postDelayed(runnable, delay);
        } else {
            mUiHandler.post(runnable);
        }
    }

    /**
     * Creates a {@link AnimatorPlaybackController} that can be used for a controlled
     * state transition.
     * @param state the final state for the transition.
     * @param duration intended duration for normal playback. Use higher duration for better
     *                accuracy.
     */
    public AnimatorPlaybackController createAnimationToNewWorkspace(
            LauncherState state, long duration) {
        return createAnimationToNewWorkspace(state, new AnimatorSetBuilder(), duration);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(
            LauncherState state, AnimatorSetBuilder builder, long duration) {
        mConfig.reset();
        mConfig.userControlled = true;
        mConfig.duration = duration;
        return AnimatorPlaybackController.wrap(
                createAnimationToNewWorkspaceInternal(state, builder, null), duration);
    }

    protected AnimatorSet createAnimationToNewWorkspaceInternal(final LauncherState state,
            AnimatorSetBuilder builder, final Runnable onCompleteRunnable) {
        final AnimationLayerSet layerViews = new AnimationLayerSet();

        for (StateHandler handler : getStateHandlers()) {
            builder.startTag(handler);
            handler.setStateWithAnimation(state, layerViews, builder, mConfig);
        }

        final AnimatorSet animation = builder.build();
        animation.addListener(layerViews);
        animation.addListener(new AnimationSuccessListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // Change the internal state only when the transition actually starts
                onStateTransitionStart(state);
                if (mStateListener != null) {
                    mStateListener.onStateTransitionStart(state);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (mStateListener != null) {
                    mStateListener.onStateTransitionComplete(mState);
                }
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                // Run any queued runnables
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                onStateTransitionEnd(state);
            }
        });
        mConfig.setAnimation(animation);
        return mConfig.mCurrentAnimation;
    }

    private void onStateTransitionStart(LauncherState state) {
        mState.onStateDisabled(mLauncher);
        mState = state;
        mState.onStateEnabled(mLauncher);
        mLauncher.getAppWidgetHost().setResumed(state == LauncherState.NORMAL);

        if (state.disablePageClipping) {
            // Only disable clipping if needed, otherwise leave it as previous value.
            mLauncher.getWorkspace().setClipChildren(false);
        }
    }

    private void onStateTransitionEnd(LauncherState state) {
        mLauncher.getWorkspace().setClipChildren(!state.disablePageClipping);
        mLauncher.getUserEventDispatcher().resetElapsedContainerMillis();
    }

    /**
     * Cancels the current animation.
     */
    public void cancelAnimation() {
        mConfig.reset();
    }

    private class StartAnimRunnable implements Runnable {

        private final AnimatorSet mAnim;
        private final View mViewToFocus;

        public StartAnimRunnable(AnimatorSet anim, View viewToFocus) {
            mAnim = anim;
            mViewToFocus = viewToFocus;
        }

        @Override
        public void run() {
            if (mConfig.mCurrentAnimation != mAnim) {
                return;
            }
            if (mViewToFocus != null) {
                mViewToFocus.requestFocus();
            }
            mAnim.start();
        }
    }

    public static class AnimationConfig extends AnimatorListenerAdapter {
        public long duration;
        public boolean userControlled;

        private AnimatorSet mCurrentAnimation;

        public void reset() {
            duration = 0;
            userControlled = false;

            if (mCurrentAnimation != null) {
                mCurrentAnimation.setDuration(0);
                mCurrentAnimation.cancel();
                mCurrentAnimation = null;
            }
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (mCurrentAnimation == animation) {
                mCurrentAnimation = null;
            }
        }

        public void setAnimation(AnimatorSet animation) {
            mCurrentAnimation = animation;
            mCurrentAnimation.addListener(this);
        }
    }

    public interface StateHandler {

        /**
         * Updates the UI to {@param state} without any animations
         */
        void setState(LauncherState state);

        /**
         * Sets the UI to {@param state} by animating any changes.
         */
        void setStateWithAnimation(LauncherState toState, AnimationLayerSet layerViews,
                AnimatorSetBuilder builder, AnimationConfig config);
    }

    public interface StateListener {

        /**
         * Called when the state is set without an animation.
         */
        void onStateSetImmediately(LauncherState state);

        void onStateTransitionStart(LauncherState toState);
        void onStateTransitionComplete(LauncherState finalState);
    }
}
