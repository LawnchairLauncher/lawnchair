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

package com.android.launcher3.statemanager;

import static android.animation.ValueAnimator.areAnimatorsEnabled;

import static com.android.launcher3.Flags.enableStateManagerProtoLog;
import static com.android.launcher3.anim.AnimatorPlaybackController.callListenerCommandRecursively;
import static com.android.launcher3.states.StateAnimationConfig.HANDLE_STATE_APPLY;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_ALL_ANIMATIONS;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.FloatRange;

import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.states.StateAnimationConfig.AnimationFlags;
import com.android.launcher3.states.StateAnimationConfig.AnimationPropertyFlags;
import com.android.launcher3.util.StateManagerProtoLogProxy;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Class to manage transitions between different states for a StatefulActivity based on different
 * states
 * @param <S> Basestate used by the state manager
 * @param <T> container object used to manage state
 */
public class StateManager<S extends BaseState<S>, T extends StatefulContainer<S>> {

    public static final String TAG = "StateManager";
    // b/279059025, b/325463989
    private static final boolean DEBUG = true;

    private final AnimationState<S> mConfig = new AnimationState<>();
    private final Handler mUiHandler;
    private final T mContainer;
    private final ArrayList<StateListener<S>> mListeners = new ArrayList<>();
    private final S mBaseState;

    // Animators which are run on properties also controlled by state animations.
    private final AtomicAnimationFactory<S> mAtomicAnimationFactory;

    private StateHandler<S>[] mStateHandlers;
    private S mState;

    private S mLastStableState;
    private S mCurrentStableState;

    private S mRestState;

    public StateManager(T container, S baseState) {
        mUiHandler = new Handler(Looper.getMainLooper());
        mContainer = container;
        mBaseState = baseState;
        mState = mLastStableState = mCurrentStableState = baseState;
        mAtomicAnimationFactory = container.createAtomicAnimationFactory();
    }

    public S getState() {
        return mState;
    }

    public S getTargetState() {
        return mConfig.targetState;
    }

    public S getCurrentStableState() {
        return mCurrentStableState;
    }

    @Override
    public String toString() {
        return " StateManager(mLastStableState:" + mLastStableState
                + ", mCurrentStableState:" + mCurrentStableState
                + ", mState:" + mState
                + ", mRestState:" + mRestState
                + ", isInTransition:" + isInTransition() + ")";
    }

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "StateManager:");
        writer.println(prefix + "\tmLastStableState:" + mLastStableState);
        writer.println(prefix + "\tmCurrentStableState:" + mCurrentStableState);
        writer.println(prefix + "\tmState:" + mState);
        writer.println(prefix + "\tmRestState:" + mRestState);
        writer.println(prefix + "\tisInTransition:" + isInTransition());
    }

    public StateHandler<S>[] getStateHandlers() {
        if (mStateHandlers == null) {
            ArrayList<StateHandler<S>> handlers = new ArrayList<>();
            mContainer.collectStateHandlers(handlers);
            mStateHandlers = handlers.toArray(new StateHandler[handlers.size()]);
        }
        return mStateHandlers;
    }

    public void addStateListener(StateListener<S> listener) {
        mListeners.add(listener);
    }

    public void removeStateListener(StateListener<S> listener) {
        mListeners.remove(listener);
    }

    /**
     * Returns true if the state changes should be animated.
     */
    public boolean shouldAnimateStateChange() {
        return mContainer.shouldAnimateStateChange();
    }

    /**
     * @return {@code true} if the state matches the current state and there is no active
     *         transition to different state.
     */
    public boolean isInStableState(S state) {
        return mState == state && mCurrentStableState == state
                && (mConfig.targetState == null || mConfig.targetState == state);
    }

    /**
     * @return {@code true} If there is an active transition.
     */
    public boolean isInTransition() {
        return mConfig.currentAnimation != null;
    }

    /**
     * @see #goToState(S, boolean, AnimatorListener)
     */
    public void goToState(S state) {
        goToState(state, shouldAnimateStateChange());
    }

    /**
     * @see #goToState(S, boolean, AnimatorListener)
     */
    public void goToState(S state, AnimatorListener listener) {
        goToState(state, shouldAnimateStateChange(), listener);
    }

    /**
     * @see #goToState(S, boolean, AnimatorListener)
     */
    public void goToState(S state, boolean animated) {
        goToState(state, animated, 0, null);
    }

    /**
     * Changes the Launcher state to the provided state.
     *
     * @param animated false if the state should change immediately without any animation,
     *                true otherwise
     * @param listener any action to perform at the end of the transition, or null.
     */
    public void goToState(S state, boolean animated, AnimatorListener listener) {
        goToState(state, animated, 0, listener);
    }

    /**
     * Changes the Launcher state to the provided state after the given delay.
     */
    public void goToState(S state, long delay, AnimatorListener listener) {
        goToState(state, true, delay, listener);
    }

    /**
     * Changes the Launcher state to the provided state after the given delay.
     */
    public void goToState(S state, long delay) {
        goToState(state, true, delay, null);
    }

    public void reapplyState() {
        reapplyState(false);
    }

    public void reapplyState(boolean cancelCurrentAnimation) {
        boolean wasInAnimation = mConfig.currentAnimation != null;
        if (cancelCurrentAnimation && (mConfig.animProps & HANDLE_STATE_APPLY) == 0) {
            // Animation canceling can trigger a cleanup routine, causing problems when we are in a
            // launcher state that relies on member variable data. So if we are in one of those
            // states, accelerate the current animation to its end point rather than canceling it
            // outright.
            if (mState.shouldPreserveDataStateOnReapply() && mConfig.currentAnimation != null) {
                mConfig.currentAnimation.end();
            }
            mAtomicAnimationFactory.cancelAllStateElementAnimation();
            cancelAnimation();
        }
        if (mConfig.currentAnimation == null) {
            for (StateHandler<S> handler : getStateHandlers()) {
                handler.setState(mState);
            }
            if (wasInAnimation) {
                onStateTransitionEnd(mState);
            }
        }
    }

    /** Handles backProgress in predictive back gesture by passing it to state handlers. */
    public void onBackProgressed(
            S toState, @FloatRange(from = 0.0, to = 1.0) float backProgress) {
        for (StateHandler<S> handler : getStateHandlers()) {
            handler.onBackProgressed(toState, backProgress);
        }
    }

    /** Handles back cancelled event in predictive back gesture by passing it to state handlers. */
    public void onBackCancelled(S toState) {
        for (StateHandler<S> handler : getStateHandlers()) {
            handler.onBackCancelled(toState);
        }
    }

    private void goToState(
            S state, boolean animated, long delay, AnimatorListener listener) {
        if (enableStateManagerProtoLog()) {
            StateManagerProtoLogProxy.logGoToState(
                    mState, state, getTrimmedStackTrace("StateManager.goToState"));
        } else if (DEBUG) {
            Log.d(TAG, "goToState - fromState: " + mState + ", toState: " + state
                    + ", partial trace:\n" + getTrimmedStackTrace("StateManager.goToState"));
        }

        animated &= areAnimatorsEnabled();
        if (getState() == state) {
            if (mConfig.currentAnimation == null) {
                // Run any queued runnable
                if (listener != null) {
                    listener.onAnimationEnd(new AnimatorSet());
                }
                return;
            } else if ((!mConfig.isUserControlled() && animated && mConfig.targetState == state)
                    || mState.shouldPreserveDataStateOnReapply()) {
                // We are running the same animation as requested, and/or target state should not be
                // reset -- allow the current animation to complete instead of canceling it.
                if (listener != null) {
                    mConfig.currentAnimation.addListener(listener);
                }
                return;
            }
        }

        // Cancel the current animation. This will reset mState to mCurrentStableState, so store it.
        S fromState = mState;
        cancelAnimation();

        if (!animated) {
            mAtomicAnimationFactory.cancelAllStateElementAnimation();
            onStateTransitionStart(state);
            for (StateHandler<S> handler : getStateHandlers()) {
                handler.setState(state);
            }

            onStateTransitionEnd(state);

            // Run any queued runnable
            if (listener != null) {
                listener.onAnimationEnd(new AnimatorSet());
            }
            return;
        }

        if (delay > 0) {
            // Create the animation after the delay as some properties can change between preparing
            // the animation and running the animation.
            int startChangeId = mConfig.changeId;
            mUiHandler.postDelayed(() -> {
                if (mConfig.changeId == startChangeId) {
                    goToStateAnimated(state, fromState, listener);
                }
            }, delay);
        } else {
            goToStateAnimated(state, fromState, listener);
        }
    }

    private void goToStateAnimated(S state, S fromState,
            AnimatorListener listener) {
        // Since state mBaseState can be reached from multiple states, just assume that the
        // transition plays in reverse and use the same duration as previous state.
        mConfig.duration = state == mBaseState
                ? fromState.getTransitionDuration(mContainer, false /* isToState */)
                : state.getTransitionDuration(mContainer, true /* isToState */);
        prepareForAtomicAnimation(fromState, state, mConfig);
        AnimatorSet animation = createAnimationToNewWorkspaceInternal(state).buildAnim();
        if (listener != null) {
            animation.addListener(listener);
        }
        mUiHandler.post(new StartAnimRunnable(animation));
    }

    /**
     * Prepares for a non-user controlled animation from fromState to toState. Preparations include:
     * - Setting interpolators for various animations included in the state transition.
     * - Setting some start values (e.g. scale) for views that are hidden but about to be shown.
     */
    public void prepareForAtomicAnimation(S fromState, S toState,
            StateAnimationConfig config) {
        mAtomicAnimationFactory.prepareForAtomicAnimation(fromState, toState, config);
    }

    /**
     * Creates an animation representing atomic transitions between the provided states
     */
    public AnimatorSet createAtomicAnimation(
            S fromState, S toState, StateAnimationConfig config) {
        if (enableStateManagerProtoLog()) {
            StateManagerProtoLogProxy.logCreateAtomicAnimation(
                    mState, toState, getTrimmedStackTrace("StateManager.createAtomicAnimation"));
        } else if (DEBUG) {
            Log.d(TAG, "createAtomicAnimation - fromState: " + fromState + ", toState: " + toState
                    + ", partial trace:\n" + getTrimmedStackTrace(
                            "StateManager.createAtomicAnimation"));
        }

        PendingAnimation builder = new PendingAnimation(config.duration);
        prepareForAtomicAnimation(fromState, toState, config);

        for (StateHandler<S> handler : getStateHandlers()) {
            handler.setStateWithAnimation(toState, config, builder);
        }
        return builder.buildAnim();
    }

    /**
     * Creates a {@link AnimatorPlaybackController} that can be used for a controlled
     * state transition.
     * @param state the final state for the transition.
     * @param duration intended duration for state playback. Use higher duration for better
     *                accuracy.
     */
    public AnimatorPlaybackController createAnimationToNewWorkspace(
            S state, long duration) {
        return createAnimationToNewWorkspace(state, duration, 0 /* animFlags */);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(
            S state, long duration, @AnimationFlags int animFlags) {
        StateAnimationConfig config = new StateAnimationConfig();
        config.duration = duration;
        config.animFlags = animFlags;
        return createAnimationToNewWorkspace(state, config);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(S state,
            StateAnimationConfig config) {
        config.animProps |= StateAnimationConfig.USER_CONTROLLED;
        cancelAnimation();
        config.copyTo(mConfig);
        mConfig.playbackController = createAnimationToNewWorkspaceInternal(state)
                .createPlaybackController();
        return mConfig.playbackController;
    }

    private PendingAnimation createAnimationToNewWorkspaceInternal(final S state) {
        PendingAnimation builder = new PendingAnimation(mConfig.duration);
        if (!mConfig.hasAnimationFlag(SKIP_ALL_ANIMATIONS)) {
            for (StateHandler<S> handler : getStateHandlers()) {
                handler.setStateWithAnimation(state, mConfig, builder);
            }
        }
        builder.addListener(createStateAnimationListener(state));
        mConfig.setAnimation(builder.buildAnim(), state);
        return builder;
    }

    private AnimatorListener createStateAnimationListener(S state) {
        return new AnimationSuccessListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // Change the internal state only when the transition actually starts
                onStateTransitionStart(state);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                onStateTransitionEnd(state);
            }
        };
    }

    private void onStateTransitionStart(S state) {
        mState = state;
        mContainer.onStateSetStart(mState);

        if (enableStateManagerProtoLog()) {
            StateManagerProtoLogProxy.logOnStateTransitionStart(state);
        } else if (DEBUG) {
            Log.d(TAG, "onStateTransitionStart - state: " + state);
        }
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onStateTransitionStart(state);
        }
    }

    private void onStateTransitionEnd(S state) {
        // Only change the stable states after the transitions have finished
        if (state != mCurrentStableState) {
            mLastStableState = state.getHistoryForState(mCurrentStableState);
            mCurrentStableState = state;
        }

        mContainer.onStateSetEnd(state);
        if (state == mBaseState) {
            setRestState(null);
        }

        if (enableStateManagerProtoLog()) {
            StateManagerProtoLogProxy.logOnStateTransitionEnd(state);
        } else if (DEBUG) {
            Log.d(TAG, "onStateTransitionEnd - state: " + state);
        }
        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onStateTransitionComplete(state);
        }
    }

    public S getLastState() {
        return mLastStableState;
    }

    public void moveToRestState() {
        moveToRestState(shouldAnimateStateChange());
    }

    public void moveToRestState(boolean isAnimated) {
        if (mConfig.currentAnimation != null && mConfig.isUserControlled()) {
            // The user is doing something. Lets not mess it up
            return;
        }
        if (mState.shouldDisableRestore()) {
            goToState(getRestState(), isAnimated);
            // Reset history
            mLastStableState = mBaseState;
        }
    }

    public S getRestState() {
        return mRestState == null ? mBaseState : mRestState;
    }

    public void setRestState(S restState) {
        mRestState = restState;
    }

    /**
     * Cancels the current animation.
     */
    public void cancelAnimation() {
        if (enableStateManagerProtoLog()) {
            StateManagerProtoLogProxy.logCancelAnimation(
                    mConfig.currentAnimation != null,
                    getTrimmedStackTrace("StateManager.cancelAnimation"));
        } else if (DEBUG && mConfig.currentAnimation != null) {
            Log.d(TAG, "cancelAnimation - with ongoing animation"
                    + ", partial trace:\n" + getTrimmedStackTrace("StateManager.cancelAnimation"));
        }
        mConfig.reset();
        // It could happen that a new animation is set as a result of an endListener on the
        // existing animation.
        while (mConfig.currentAnimation != null || mConfig.playbackController != null) {
            mConfig.reset();
        }
    }

    /**
     * Sets the provided controller as the current user controlled state animation
     */
    public void setCurrentUserControlledAnimation(AnimatorPlaybackController controller) {
        setCurrentAnimation(controller, StateAnimationConfig.USER_CONTROLLED);
    }

    public void setCurrentAnimation(AnimatorPlaybackController controller,
            @AnimationPropertyFlags int animationProps) {
        clearCurrentAnimation();
        setCurrentAnimation(controller.getTarget());
        mConfig.animProps = animationProps;
        mConfig.playbackController = controller;
    }

    /**
     * @see #setCurrentAnimation(AnimatorSet, Animator...). Using this method tells the StateManager
     * that this is a custom animation to the given state, and thus the StateManager will add an
     * animation listener to call {@link #onStateTransitionStart} and {@link #onStateTransitionEnd}.
     * @param anim The custom animation to the given state.
     * @param toState The state we are animating towards.
     */
    public void setCurrentAnimation(AnimatorSet anim, S toState) {
        cancelAnimation();
        setCurrentAnimation(anim);
        anim.addListener(createStateAnimationListener(toState));
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

    /**
     * Cancels a currently running gesture animation
     */
    public void cancelStateElementAnimation(int index) {
        if (mAtomicAnimationFactory.mStateElementAnimators[index] != null) {
            mAtomicAnimationFactory.mStateElementAnimators[index].cancel();
        }
    }

    public Animator createStateElementAnimation(int index, float... values) {
        cancelStateElementAnimation(index);
        Animator anim = mAtomicAnimationFactory.createStateElementAnimation(index, values);
        mAtomicAnimationFactory.mStateElementAnimators[index] = anim;
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAtomicAnimationFactory.mStateElementAnimators[index] = null;
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

    private String getTrimmedStackTrace(String callingMethodName) {
        String stackTrace = Log.getStackTraceString(new Exception());
        return Arrays.stream(stackTrace.split("\\n"))
                .skip(2) // Removes the line "java.lang.Exception" and "getTrimmedStackTrace".
                .filter(traceLine -> !traceLine.contains(callingMethodName))
                .limit(3)
                .collect(Collectors.joining("\n"));
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

    private static class AnimationState<STATE_TYPE> extends StateAnimationConfig
            implements AnimatorListener {

        private static final StateAnimationConfig DEFAULT = new StateAnimationConfig();

        public AnimatorPlaybackController playbackController;
        public AnimatorSet currentAnimation;
        public STATE_TYPE targetState;

        // Id to keep track of config changes, to tie an animation with the corresponding request
        public int changeId = 0;

        /**
         * Cancels the current animation and resets config variables.
         */
        public void reset() {
            AnimatorSet anim = currentAnimation;
            AnimatorPlaybackController pc = playbackController;

            DEFAULT.copyTo(this);
            targetState = null;
            currentAnimation = null;
            playbackController = null;
            changeId++;

            if (pc != null) {
                pc.getAnimationPlayer().cancel();
                pc.dispatchOnCancel().dispatchOnEnd();
            } else if (anim != null) {
                anim.setDuration(0);
                if (!anim.isStarted()) {
                    // If the animation is not started the listeners do not get notified,
                    // notify manually.
                    callListenerCommandRecursively(anim, AnimatorListener::onAnimationCancel);
                    callListenerCommandRecursively(anim, AnimatorListener::onAnimationEnd);
                }
                anim.cancel();
            }
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

        public void setAnimation(AnimatorSet animation, STATE_TYPE targetState) {
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

    public interface StateHandler<STATE_TYPE> {

        /**
         * Updates the UI to {@param state} without any animations
         */
        void setState(STATE_TYPE state);

        /**
         * Sets the UI to {@param state} by animating any changes.
         */
        void setStateWithAnimation(
                STATE_TYPE toState, StateAnimationConfig config, PendingAnimation animation);

        /** Handles backProgress in predictive back gesture for target state. */
        default void onBackProgressed(
                STATE_TYPE toState, @FloatRange(from = 0.0, to = 1.0) float backProgress) {}

        /** Handles back cancelled event in predictive back gesture for target state.  */
        default void onBackCancelled(STATE_TYPE toState) {}
    }

    public interface StateListener<STATE_TYPE> {

        default void onStateTransitionStart(STATE_TYPE toState) { }

        default void onStateTransitionComplete(STATE_TYPE finalState) { }
    }

    /**
     * Factory class to configure and create atomic animations.
     */
    public static class AtomicAnimationFactory<STATE_TYPE> {

        protected static final int NEXT_INDEX = 0;

        private final Animator[] mStateElementAnimators;

        /**
         *
         * @param sharedElementAnimCount number of animations which run on state properties
         */
        public AtomicAnimationFactory(int sharedElementAnimCount) {
            mStateElementAnimators = new Animator[sharedElementAnimCount];
        }

        void cancelAllStateElementAnimation() {
            for (Animator animator : mStateElementAnimators) {
                if (animator != null) {
                    animator.cancel();
                }
            }
        }

        /**
         * Creates animations for elements which can be also be part of state transitions. The
         * actual definition of the animation is up to the app to define.
         *
         */
        public Animator createStateElementAnimation(int index, float... values) {
            throw new RuntimeException("Unknown gesture animation " + index);
        }

        /**
         * Prepares for a non-user controlled animation from fromState to this state. Preparations
         * include:
         * - Setting interpolators for various animations included in the state transition.
         * - Setting some start values (e.g. scale) for views that are hidden but about to be shown.
         */
        public void prepareForAtomicAnimation(
                STATE_TYPE fromState, STATE_TYPE toState, StateAnimationConfig config) { }
    }
}
