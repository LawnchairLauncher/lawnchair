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
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_COMPONENTS;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.states.StateAnimationConfig.AnimationFlags;

import java.io.PrintWriter;
import java.util.ArrayList;

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
 *   - Enter and exit car mode (becase it causes an extra configuration changed)
 *          - From all apps
 *          - From the center workspace
 *          - From another workspace
 */
public class LauncherStateManager {

    public static final String TAG = "StateManager";

    private final AnimationState mConfig = new AnimationState();
    private final Handler mUiHandler;
    private final Launcher mLauncher;
    private final ArrayList<StateListener> mListeners = new ArrayList<>();

    // Animators which are run on properties also controlled by state animations.
    private Animator[] mStateElementAnimators;

    private StateHandler[] mStateHandlers;
    private LauncherState mState = NORMAL;

    private LauncherState mLastStableState = NORMAL;
    private LauncherState mCurrentStableState = NORMAL;

    private LauncherState mRestState;

    public LauncherStateManager(Launcher l) {
        mUiHandler = new Handler(Looper.getMainLooper());
        mLauncher = l;
    }

    public LauncherState getState() {
        return mState;
    }

    public LauncherState getCurrentStableState() {
        return mCurrentStableState;
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "LauncherState:");
        writer.println(prefix + "\tmLastStableState:" + mLastStableState);
        writer.println(prefix + "\tmCurrentStableState:" + mCurrentStableState);
        writer.println(prefix + "\tmState:" + mState);
        writer.println(prefix + "\tmRestState:" + mRestState);
        writer.println(prefix + "\tisInTransition:" + (mConfig.currentAnimation != null));
    }

    public StateHandler[] getStateHandlers() {
        if (mStateHandlers == null) {
            mStateHandlers = mLauncher.createStateHandlers();
        }
        return mStateHandlers;
    }

    public void addStateListener(StateListener listener) {
        mListeners.add(listener);
    }

    public void removeStateListener(StateListener listener) {
        mListeners.remove(listener);
    }

    /**
     * Returns true if the state changes should be animated.
     */
    public boolean shouldAnimateStateChange() {
        return !mLauncher.isForceInvisible() && mLauncher.isStarted();
    }

    /**
     * @return {@code true} if the state matches the current state and there is no active
     *         transition to different state.
     */
    public boolean isInStableState(LauncherState state) {
        return mState == state && mCurrentStableState == state
                && (mConfig.targetState == null || mConfig.targetState == state);
    }

    /**
     * @see #goToState(LauncherState, boolean, Runnable)
     */
    public void goToState(LauncherState state) {
        goToState(state, shouldAnimateStateChange());
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

    public void reapplyState() {
        reapplyState(false);
    }

    public void reapplyState(boolean cancelCurrentAnimation) {
        boolean wasInAnimation = mConfig.currentAnimation != null;
        if (cancelCurrentAnimation) {
            cancelAllStateElementAnimation();
            cancelAnimation();
        }
        if (mConfig.currentAnimation == null) {
            for (StateHandler handler : getStateHandlers()) {
                handler.setState(mState);
            }
            if (wasInAnimation) {
                onStateTransitionEnd(mState);
            }
        }
    }

    private void goToState(LauncherState state, boolean animated, long delay,
            final Runnable onCompleteRunnable) {
        animated &= Utilities.areAnimationsEnabled(mLauncher);
        if (mLauncher.isInState(state)) {
            if (mConfig.currentAnimation == null) {
                // Run any queued runnable
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                return;
            } else if (!mConfig.userControlled && animated && mConfig.targetState == state) {
                // We are running the same animation as requested
                if (onCompleteRunnable != null) {
                    mConfig.currentAnimation.addListener(
                            AnimationSuccessListener.forRunnable(onCompleteRunnable));
                }
                return;
            }
        }

        // Cancel the current animation. This will reset mState to mCurrentStableState, so store it.
        LauncherState fromState = mState;
        mConfig.reset();

        if (!animated) {
            cancelAllStateElementAnimation();
            onStateTransitionStart(state);
            for (StateHandler handler : getStateHandlers()) {
                handler.setState(state);
            }

            onStateTransitionEnd(state);

            // Run any queued runnable
            if (onCompleteRunnable != null) {
                onCompleteRunnable.run();
            }
            return;
        }

        if (delay > 0) {
            // Create the animation after the delay as some properties can change between preparing
            // the animation and running the animation.
            int startChangeId = mConfig.changeId;
            mUiHandler.postDelayed(() -> {
                if (mConfig.changeId == startChangeId) {
                    goToStateAnimated(state, fromState, onCompleteRunnable);
                }
            }, delay);
        } else {
            goToStateAnimated(state, fromState, onCompleteRunnable);
        }
    }

    private void goToStateAnimated(LauncherState state, LauncherState fromState,
            Runnable onCompleteRunnable) {
        // Since state NORMAL can be reached from multiple states, just assume that the
        // transition plays in reverse and use the same duration as previous state.
        mConfig.duration = state == NORMAL
                ? fromState.getTransitionDuration(mLauncher)
                : state.getTransitionDuration(mLauncher);
        prepareForAtomicAnimation(fromState, state, mConfig);
        AnimatorSet animation = createAnimationToNewWorkspaceInternal(state).getAnim();
        if (onCompleteRunnable != null) {
            animation.addListener(AnimationSuccessListener.forRunnable(onCompleteRunnable));
        }
        mUiHandler.post(new StartAnimRunnable(animation));
    }

    /**
     * Prepares for a non-user controlled animation from fromState to toState. Preparations include:
     * - Setting interpolators for various animations included in the state transition.
     * - Setting some start values (e.g. scale) for views that are hidden but about to be shown.
     */
    public void prepareForAtomicAnimation(LauncherState fromState, LauncherState toState,
            StateAnimationConfig config) {
        toState.prepareForAtomicAnimation(mLauncher, fromState, config);
    }

    /**
     * Creates an animation representing atomic transitions between the provided states
     */
    public AnimatorSet createAtomicAnimation(
            LauncherState fromState, LauncherState toState, StateAnimationConfig config) {
        PendingAnimation builder = new PendingAnimation(config.duration);
        prepareForAtomicAnimation(fromState, toState, config);

        for (StateHandler handler : mLauncher.getStateManager().getStateHandlers()) {
            handler.setStateWithAnimation(toState, config, builder);
        }
        return builder.getAnim();
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
        return createAnimationToNewWorkspace(state, duration, ANIM_ALL_COMPONENTS);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(
            LauncherState state, long duration, @AnimationFlags int animComponents) {
        StateAnimationConfig config = new StateAnimationConfig();
        config.duration = duration;
        config.animFlags = animComponents;
        return createAnimationToNewWorkspace(state, config);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(LauncherState state,
            StateAnimationConfig config) {
        config.userControlled = true;
        mConfig.reset();
        config.copyTo(mConfig);
        mConfig.playbackController = createAnimationToNewWorkspaceInternal(state)
                .createPlaybackController();
        return mConfig.playbackController;
    }

    private PendingAnimation createAnimationToNewWorkspaceInternal(final LauncherState state) {
        PendingAnimation builder = new PendingAnimation(mConfig.duration);
        for (StateHandler handler : getStateHandlers()) {
            handler.setStateWithAnimation(state, mConfig, builder);
        }
        builder.getAnim().addListener(new AnimationSuccessListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // Change the internal state only when the transition actually starts
                onStateTransitionStart(state);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                onStateTransitionEnd(state);
            }
        });
        mConfig.setAnimation(builder.getAnim(), state);
        return builder;
    }

    private void onStateTransitionStart(LauncherState state) {
        mState = state;
        mLauncher.onStateSetStart(mState);

        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onStateTransitionStart(state);
        }
    }

    private void onStateTransitionEnd(LauncherState state) {
        // Only change the stable states after the transitions have finished
        if (state != mCurrentStableState) {
            mLastStableState = state.getHistoryForState(mCurrentStableState);
            mCurrentStableState = state;
        }

        mLauncher.onStateSetEnd(state);
        if (state == NORMAL) {
            setRestState(null);
        }

        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onStateTransitionComplete(state);
        }
    }

    public LauncherState getLastState() {
        return mLastStableState;
    }

    public void moveToRestState() {
        if (mConfig.currentAnimation != null && mConfig.userControlled) {
            // The user is doing something. Lets not mess it up
            return;
        }
        if (mState.shouldDisableRestore()) {
            goToState(getRestState());
            // Reset history
            mLastStableState = NORMAL;
        }
    }

    public LauncherState getRestState() {
        return mRestState == null ? NORMAL : mRestState;
    }

    public void setRestState(LauncherState restState) {
        mRestState = restState;
    }

    /**
     * Cancels the current animation.
     */
    public void cancelAnimation() {
        mConfig.reset();
    }

    public void setCurrentUserControlledAnimation(AnimatorPlaybackController controller) {
        clearCurrentAnimation();
        setCurrentAnimation(controller.getTarget());
        mConfig.userControlled = true;
        mConfig.playbackController = controller;
    }

    /**
     * Sets the animation as the current state animation, i.e., canceled when
     * starting another animation and may block some launcher interactions while running.
     *
     * @param childAnimations Set of animations with the new target is controlling.
     */
    public void setCurrentAnimation(AnimatorSet anim, Animator... childAnimations) {
        for (Animator childAnim : childAnimations) {
            if (childAnim == null) {
                continue;
            }
            if (mConfig.playbackController != null
                    && mConfig.playbackController.getTarget() == childAnim) {
                clearCurrentAnimation();
                break;
            } else if (mConfig.currentAnimation == childAnim) {
                clearCurrentAnimation();
                break;
            }
        }
        boolean reapplyNeeded = mConfig.currentAnimation != null;
        cancelAnimation();
        if (reapplyNeeded) {
            reapplyState();
            // Dispatch on transition end, so that any transient property is cleared.
            onStateTransitionEnd(mState);
        }
        mConfig.setAnimation(anim, null);
    }

    private void cancelAllStateElementAnimation() {
        if (mStateElementAnimators == null) {
            return;
        }

        for (Animator animator : mStateElementAnimators) {
            if (animator != null) {
                animator.cancel();
            }
        }
    }

    /**
     * Cancels a currently running gesture animation
     */
    public void cancelStateElementAnimation(int index) {
        if (mStateElementAnimators == null) {
            return;
        }
        if (mStateElementAnimators[index] != null) {
            mStateElementAnimators[index].cancel();
        }
    }

    public Animator createStateElementAnimation(int index, float... values) {
        cancelStateElementAnimation(index);
        LauncherAppTransitionManager latm = mLauncher.getAppTransitionManager();
        if (mStateElementAnimators == null) {
            mStateElementAnimators = new Animator[latm.getStateElementAnimationsCount()];
        }
        Animator anim = latm.createStateElementAnimation(index, values);
        mStateElementAnimators[index] = anim;
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mStateElementAnimators[index] = null;
            }
        });
        return anim;
    }

    private void clearCurrentAnimation() {
        if (mConfig.currentAnimation != null) {
            mConfig.currentAnimation.removeListener(mConfig);
            mConfig.currentAnimation = null;
        }
        mConfig.playbackController = null;
    }

    private class StartAnimRunnable implements Runnable {

        private final AnimatorSet mAnim;

        public StartAnimRunnable(AnimatorSet anim) {
            mAnim = anim;
        }

        @Override
        public void run() {
            if (mConfig.currentAnimation != mAnim) {
                return;
            }
            mAnim.start();
        }
    }

    private static class AnimationState extends StateAnimationConfig implements AnimatorListener {

        private static final StateAnimationConfig DEFAULT = new StateAnimationConfig();

        public AnimatorPlaybackController playbackController;
        public AnimatorSet currentAnimation;
        public LauncherState targetState;

        // Id to keep track of config changes, to tie an animation with the corresponding request
        public int changeId = 0;

        /**
         * Cancels the current animation and resets config variables.
         */
        public void reset() {
            DEFAULT.copyTo(this);
            targetState = null;

            if (playbackController != null) {
                playbackController.getAnimationPlayer().cancel();
                playbackController.dispatchOnCancel();
            } else if (currentAnimation != null) {
                currentAnimation.setDuration(0);
                currentAnimation.cancel();
            }

            currentAnimation = null;
            playbackController = null;
            changeId++;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (playbackController != null && playbackController.getTarget() == animation) {
                playbackController = null;
            }
            if (currentAnimation == animation) {
                currentAnimation = null;
            }
        }

        public void setAnimation(AnimatorSet animation, LauncherState targetState) {
            currentAnimation = animation;
            this.targetState = targetState;
            currentAnimation.addListener(this);
        }

        @Override
        public void onAnimationStart(Animator animator) { }

        @Override
        public void onAnimationCancel(Animator animator) { }

        @Override
        public void onAnimationRepeat(Animator animator) { }
    }

    public interface StateHandler {

        /**
         * Updates the UI to {@param state} without any animations
         */
        void setState(LauncherState state);

        /**
         * Sets the UI to {@param state} by animating any changes.
         */
        void setStateWithAnimation(
                LauncherState toState, StateAnimationConfig config, PendingAnimation animation);
    }

    public interface StateListener {

        default void onStateTransitionStart(LauncherState toState) { }

        default void onStateTransitionComplete(LauncherState finalState) { }
    }
}
