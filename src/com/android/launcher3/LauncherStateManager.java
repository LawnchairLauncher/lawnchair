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
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.IntDef;

import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.anim.PropertySetter.AnimatedPropertySetter;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.uioverrides.UiFactory;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
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

    // We separate the state animations into "atomic" and "non-atomic" components. The atomic
    // components may be run atomically - that is, all at once, instead of user-controlled. However,
    // atomic components are not restricted to this purpose; they can be user-controlled alongside
    // non atomic components as well. Note that each gesture model has exactly one atomic component,
    // ATOMIC_OVERVIEW_SCALE_COMPONENT *or* ATOMIC_OVERVIEW_PEEK_COMPONENT.
    @IntDef(flag = true, value = {
            NON_ATOMIC_COMPONENT,
            ATOMIC_OVERVIEW_SCALE_COMPONENT,
            ATOMIC_OVERVIEW_PEEK_COMPONENT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface AnimationComponents {}
    public static final int NON_ATOMIC_COMPONENT = 1 << 0;
    public static final int ATOMIC_OVERVIEW_SCALE_COMPONENT = 1 << 1;
    public static final int ATOMIC_OVERVIEW_PEEK_COMPONENT = 1 << 2;

    public static final int ANIM_ALL = NON_ATOMIC_COMPONENT | ATOMIC_OVERVIEW_SCALE_COMPONENT
            | ATOMIC_OVERVIEW_PEEK_COMPONENT;

    private final AnimationConfig mConfig = new AnimationConfig();
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
        writer.println(prefix + "\tisInTransition:" + (mConfig.mCurrentAnimation != null));
    }

    public StateHandler[] getStateHandlers() {
        if (mStateHandlers == null) {
            mStateHandlers = UiFactory.getStateHandler(mLauncher);
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
        boolean wasInAnimation = mConfig.mCurrentAnimation != null;
        if (cancelCurrentAnimation) {
            cancelAllStateElementAnimation();
            cancelAnimation();
        }
        if (mConfig.mCurrentAnimation == null) {
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
            if (mConfig.mCurrentAnimation == null) {
                // Run any queued runnable
                if (onCompleteRunnable != null) {
                    onCompleteRunnable.run();
                }
                return;
            } else if (!mConfig.userControlled && animated && mConfig.mTargetState == state) {
                // We are running the same animation as requested
                if (onCompleteRunnable != null) {
                    mConfig.mCurrentAnimation.addListener(new AnimationSuccessListener() {
                        @Override
                        public void onAnimationSuccess(Animator animator) {
                            onCompleteRunnable.run();
                        }
                    });
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
            int startChangeId = mConfig.mChangeId;
            mUiHandler.postDelayed(() -> {
                if (mConfig.mChangeId == startChangeId) {
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
        mConfig.duration = state == NORMAL ? fromState.transitionDuration : state.transitionDuration;

        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        prepareForAtomicAnimation(fromState, state, builder);
        AnimatorSet animation = createAnimationToNewWorkspaceInternal(
                state, builder, onCompleteRunnable);
        mUiHandler.post(new StartAnimRunnable(animation));
    }

    /**
     * Prepares for a non-user controlled animation from fromState to toState. Preparations include:
     * - Setting interpolators for various animations included in the state transition.
     * - Setting some start values (e.g. scale) for views that are hidden but about to be shown.
     */
    public void prepareForAtomicAnimation(LauncherState fromState, LauncherState toState,
            AnimatorSetBuilder builder) {
        toState.prepareForAtomicAnimation(mLauncher, fromState, builder);
    }

    public AnimatorSet createAtomicAnimation(LauncherState fromState, LauncherState toState,
            AnimatorSetBuilder builder, @AnimationComponents int atomicComponent, long duration) {
        prepareForAtomicAnimation(fromState, toState, builder);
        AnimationConfig config = new AnimationConfig();
        config.animComponents = atomicComponent;
        config.duration = duration;
        for (StateHandler handler : mLauncher.getStateManager().getStateHandlers()) {
            handler.setStateWithAnimation(toState, builder, config);
        }
        return builder.build();
    }

    /**
     * Creates a {@link AnimatorPlaybackController} that can be used for a controlled
     * state transition. The UI is force-set to fromState before creating the controller.
     * @param fromState the initial state for the transition.
     * @param state the final state for the transition.
     * @param duration intended duration for normal playback. Use higher duration for better
     *                accuracy.
     */
    public AnimatorPlaybackController createAnimationToNewWorkspace(
            LauncherState fromState, LauncherState state, long duration) {
        // Since we are creating a state animation to a different state, temporarily prevent state
        // change as part of config reset.
        LauncherState originalRestState = mRestState;
        mRestState = state;
        mConfig.reset();
        mRestState = originalRestState;

        for (StateHandler handler : getStateHandlers()) {
            handler.setState(fromState);
        }

        return createAnimationToNewWorkspace(state, duration);
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
        return createAnimationToNewWorkspace(state, duration, LauncherStateManager.ANIM_ALL);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(
            LauncherState state, long duration, @AnimationComponents int animComponents) {
        return createAnimationToNewWorkspace(state, new AnimatorSetBuilder(), duration, null,
                animComponents);
    }

    public AnimatorPlaybackController createAnimationToNewWorkspace(LauncherState state,
            AnimatorSetBuilder builder, long duration, Runnable onCancelRunnable,
            @AnimationComponents int animComponents) {
        mConfig.reset();
        mConfig.userControlled = true;
        mConfig.animComponents = animComponents;
        mConfig.duration = duration;
        mConfig.playbackController = AnimatorPlaybackController.wrap(
                createAnimationToNewWorkspaceInternal(state, builder, null), duration,
                onCancelRunnable);
        return mConfig.playbackController;
    }

    protected AnimatorSet createAnimationToNewWorkspaceInternal(final LauncherState state,
            AnimatorSetBuilder builder, final Runnable onCompleteRunnable) {

        for (StateHandler handler : getStateHandlers()) {
            handler.setStateWithAnimation(state, builder, mConfig);
        }

        final AnimatorSet animation = builder.build();
        animation.addListener(new AnimationSuccessListener() {

            @Override
            public void onAnimationStart(Animator animation) {
                // Change the internal state only when the transition actually starts
                onStateTransitionStart(state);
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
        mConfig.setAnimation(animation, state);
        return mConfig.mCurrentAnimation;
    }

    private void onStateTransitionStart(LauncherState state) {
        if (mState != state) {
            mState.onStateDisabled(mLauncher);
        }
        mState = state;
        mState.onStateEnabled(mLauncher);
        mLauncher.onStateSetStart(mState);

        if (state.disablePageClipping) {
            // Only disable clipping if needed, otherwise leave it as previous value.
            mLauncher.getWorkspace().setClipChildren(false);
        }
        UiFactory.onLauncherStateOrResumeChanged(mLauncher);

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

        state.onStateTransitionEnd(mLauncher);
        mLauncher.onStateSetEnd(state);

        if (state == NORMAL) {
            setRestState(null);
        }

        UiFactory.onLauncherStateOrResumeChanged(mLauncher);

        for (int i = mListeners.size() - 1; i >= 0; i--) {
            mListeners.get(i).onStateTransitionComplete(state);
        }

        AccessibilityManagerCompat.sendStateEventToTest(mLauncher, state.ordinal);
    }

    public void onWindowFocusChanged() {
        UiFactory.onLauncherStateOrFocusChanged(mLauncher);
    }

    public LauncherState getLastState() {
        return mLastStableState;
    }

    public void moveToRestState() {
        if (mConfig.mCurrentAnimation != null && mConfig.userControlled) {
            // The user is doing something. Lets not mess it up
            return;
        }
        if (mState.disableRestore) {
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
            } else if (mConfig.mCurrentAnimation == childAnim) {
                clearCurrentAnimation();
                break;
            }
        }
        boolean reapplyNeeded = mConfig.mCurrentAnimation != null;
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
        if (mConfig.mCurrentAnimation != null) {
            mConfig.mCurrentAnimation.removeListener(mConfig);
            mConfig.mCurrentAnimation = null;
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
            if (mConfig.mCurrentAnimation != mAnim) {
                return;
            }
            mAnim.start();
        }
    }

    public static class AnimationConfig extends AnimatorListenerAdapter {
        public long duration;
        public boolean userControlled;
        public AnimatorPlaybackController playbackController;
        public @AnimationComponents int animComponents = ANIM_ALL;
        private PropertySetter mPropertySetter;

        private AnimatorSet mCurrentAnimation;
        private LauncherState mTargetState;
        // Id to keep track of config changes, to tie an animation with the corresponding request
        private int mChangeId = 0;

        /**
         * Cancels the current animation and resets config variables.
         */
        public void reset() {
            duration = 0;
            userControlled = false;
            animComponents = ANIM_ALL;
            mPropertySetter = null;
            mTargetState = null;

            if (playbackController != null) {
                playbackController.getAnimationPlayer().cancel();
                playbackController.dispatchOnCancel();
            } else if (mCurrentAnimation != null) {
                mCurrentAnimation.setDuration(0);
                mCurrentAnimation.cancel();
            }

            mCurrentAnimation = null;
            playbackController = null;
            mChangeId ++;
        }

        public PropertySetter getPropertySetter(AnimatorSetBuilder builder) {
            if (mPropertySetter == null) {
                mPropertySetter = duration == 0 ? NO_ANIM_PROPERTY_SETTER
                        : new AnimatedPropertySetter(duration, builder);
            }
            return mPropertySetter;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (playbackController != null && playbackController.getTarget() == animation) {
                playbackController = null;
            }
            if (mCurrentAnimation == animation) {
                mCurrentAnimation = null;
            }
        }

        public void setAnimation(AnimatorSet animation, LauncherState targetState) {
            mCurrentAnimation = animation;
            mTargetState = targetState;
            mCurrentAnimation.addListener(this);
        }

        public boolean playAtomicOverviewScaleComponent() {
            return (animComponents & ATOMIC_OVERVIEW_SCALE_COMPONENT) != 0;
        }

        public boolean playAtomicOverviewPeekComponent() {
            return (animComponents & ATOMIC_OVERVIEW_PEEK_COMPONENT) != 0;
        }

        public boolean playNonAtomicComponent() {
            return (animComponents & NON_ATOMIC_COMPONENT) != 0;
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
        void setStateWithAnimation(LauncherState toState,
                AnimatorSetBuilder builder, AnimationConfig config);
    }

    public interface StateListener {

        void onStateTransitionStart(LauncherState toState);
        void onStateTransitionComplete(LauncherState finalState);
    }
}
