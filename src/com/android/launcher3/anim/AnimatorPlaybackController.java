/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.anim;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.Utilities.boundToRange;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;

import com.android.launcher3.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Helper class to control the playback of an {@link AnimatorSet}, with custom interpolators
 * and durations.
 *
 * Note: The implementation does not support start delays on child animations or
 * sequential playbacks.
 */
public class AnimatorPlaybackController implements ValueAnimator.AnimatorUpdateListener {

    /**
     * Creates an animation controller for the provided animation.
     * The actual duration does not matter as the animation is manually controlled. It just
     * needs to be larger than the total number of pixels so that we don't have jittering due
     * to float (animation-fraction * total duration) to int conversion.
     */
    public static AnimatorPlaybackController wrap(AnimatorSet anim, long duration) {
        ArrayList<Holder> childAnims = new ArrayList<>();
        addAnimationHoldersRecur(anim, duration, SpringProperty.DEFAULT, childAnims);

        return new AnimatorPlaybackController(anim, duration, childAnims);
    }

    // Progress factor after which an animation is considered almost completed.
    private static final float ANIMATION_COMPLETE_THRESHOLD = 0.95f;

    private final ValueAnimator mAnimationPlayer;
    private final long mDuration;

    private final AnimatorSet mAnim;
    private final Holder[] mChildAnimations;

    protected float mCurrentFraction;
    private Runnable mEndAction;

    protected boolean mTargetCancelled = false;

    /** package private */
    AnimatorPlaybackController(AnimatorSet anim, long duration, ArrayList<Holder> childAnims) {
        mAnim = anim;
        mDuration = duration;

        mAnimationPlayer = ValueAnimator.ofFloat(0, 1);
        mAnimationPlayer.setInterpolator(LINEAR);
        mAnimationPlayer.addListener(new OnAnimationEndDispatcher());
        mAnimationPlayer.addUpdateListener(this);

        mAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                mTargetCancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mTargetCancelled = false;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                mTargetCancelled = false;
            }
        });

        mChildAnimations = childAnims.toArray(new Holder[childAnims.size()]);
    }

    public AnimatorSet getTarget() {
        return mAnim;
    }

    public long getDuration() {
        return mDuration;
    }

    public TimeInterpolator getInterpolator() {
        return mAnim.getInterpolator() != null ? mAnim.getInterpolator() : LINEAR;
    }

    /**
     * Starts playing the animation forward from current position.
     */
    public void start() {
        mAnimationPlayer.setFloatValues(mCurrentFraction, 1);
        mAnimationPlayer.setDuration(clampDuration(1 - mCurrentFraction));
        mAnimationPlayer.start();
    }

    /**
     * Starts playing the animation backwards from current position
     */
    public void reverse() {
        mAnimationPlayer.setFloatValues(mCurrentFraction, 0);
        mAnimationPlayer.setDuration(clampDuration(mCurrentFraction));
        mAnimationPlayer.start();
    }

    /**
     * Starts playing the animation with the provided velocity optionally playing any
     * physics based animations.
     * @param goingToEnd Whether we are going to the end (progress = 1) or not (progress = 0).
     * @param velocityPxPerMs The velocity at which to start the animation, in pixels / millisecond.
     * @param endDistance The distance (pixels) that the animation will travel from progress 0 to 1.
     * @param animationDuration The duration of the non-physics based animation.
     */
    public void startWithVelocity(Context context, boolean goingToEnd,
            float velocityPxPerMs, float endDistance, long animationDuration) {
        float distanceInverse = 1 / Math.abs(endDistance);
        float velocityProgressPerMs = velocityPxPerMs * distanceInverse;

        float oneFrameProgress = velocityProgressPerMs * getSingleFrameMs(context);
        float nextFrameProgress = boundToRange(getProgressFraction()
                + oneFrameProgress, 0f, 1f);

        // Update setters for spring
        int springFlag = goingToEnd
                ? SpringProperty.FLAG_CAN_SPRING_ON_END
                : SpringProperty.FLAG_CAN_SPRING_ON_START;

        long springDuration = animationDuration;
        for (Holder h : mChildAnimations) {
            if ((h.springProperty.flags & springFlag) != 0) {
                SpringAnimationBuilder s = new SpringAnimationBuilder(context)
                        .setStartValue(mCurrentFraction)
                        .setEndValue(goingToEnd ? 1 : 0)
                        .setStartVelocity(velocityProgressPerMs)
                        .setMinimumVisibleChange(distanceInverse)
                        .setDampingRatio(h.springProperty.mDampingRatio)
                        .setStiffness(h.springProperty.mStiffness)
                        .computeParams();

                long expectedDurationL = s.getDuration();
                springDuration = Math.max(expectedDurationL, springDuration);

                float expectedDuration = expectedDurationL;
                h.mapper = (progress, globalEndProgress) -> {
                    if (expectedDuration <= 0 || oneFrameProgress >= 1) {
                        return 1;
                    } else {
                        // Start from one frame ahead of the current position.
                        return Utilities.mapToRange(
                                mAnimationPlayer.getCurrentPlayTime() / expectedDuration,
                                0, 1,
                                Math.abs(oneFrameProgress), 1,
                                LINEAR);
                    }
                };
                h.anim.setInterpolator(s::getInterpolatedValue);
            }
        }

        mAnimationPlayer.setFloatValues(nextFrameProgress, goingToEnd ? 1f : 0f);

        if (springDuration <= animationDuration) {
            mAnimationPlayer.setDuration(animationDuration);
            mAnimationPlayer.setInterpolator(scrollInterpolatorForVelocity(velocityPxPerMs));
        } else {
            // Since spring requires more time to run, we let the other animations play with
            // current time and interpolation and by clamping the duration.
            mAnimationPlayer.setDuration(springDuration);

            float cutOff = animationDuration / (float) springDuration;
            mAnimationPlayer.setInterpolator(
                    clampToProgress(scrollInterpolatorForVelocity(velocityPxPerMs), 0, cutOff));
        }
        mAnimationPlayer.start();
    }

    /**
     * Tries to finish the running animation if it is close to completion.
     */
    public void forceFinishIfCloseToEnd() {
        if (mAnimationPlayer.isRunning()
                && mAnimationPlayer.getAnimatedFraction() > ANIMATION_COMPLETE_THRESHOLD) {
            mAnimationPlayer.end();
        }
    }

    /**
     * Pauses the currently playing animation.
     */
    public void pause() {
        // Reset property setters
        for (Holder h : mChildAnimations) {
            h.reset();
        }
        mAnimationPlayer.cancel();
    }

    /**
     * Returns the underlying animation used for controlling the set.
     */
    public ValueAnimator getAnimationPlayer() {
        return mAnimationPlayer;
    }

    /**
     * Sets the current animation position and updates all the child animators accordingly.
     */
    public void setPlayFraction(float fraction) {
        mCurrentFraction = fraction;
        // Let the animator report the progress but don't apply the progress to child
        // animations if it has been cancelled.
        if (mTargetCancelled) {
            return;
        }
        float progress = boundToRange(fraction, 0, 1);
        for (Holder holder : mChildAnimations) {
            holder.setProgress(progress);
        }
    }

    public float getProgressFraction() {
        return mCurrentFraction;
    }

    public float getInterpolatedProgress() {
        return getInterpolator().getInterpolation(mCurrentFraction);
    }

    /**
     * Sets the action to be called when the animation is completed. Also clears any
     * previously set action.
     */
    public void setEndAction(Runnable runnable) {
        mEndAction = runnable;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        setPlayFraction((float) valueAnimator.getAnimatedValue());
    }

    protected long clampDuration(float fraction) {
        float playPos = mDuration * fraction;
        if (playPos <= 0) {
            return 0;
        } else {
            return Math.min((long) playPos, mDuration);
        }
    }

    public AnimatorPlaybackController dispatchOnStart() {
        callListenerCommandRecursively(mAnim, AnimatorListener::onAnimationStart);
        return this;
    }

    public AnimatorPlaybackController dispatchOnCancel() {
        callListenerCommandRecursively(mAnim, AnimatorListener::onAnimationCancel);
        return this;
    }

    public AnimatorPlaybackController dispatchOnEnd() {
        callListenerCommandRecursively(mAnim, AnimatorListener::onAnimationEnd);
        return this;
    }

    public void dispatchSetInterpolator(TimeInterpolator interpolator) {
        callAnimatorCommandRecursively(mAnim, a -> a.setInterpolator(interpolator));
    }

    /**
     * Recursively calls a command on all the listeners of the provided animation
     */
    public static void callListenerCommandRecursively(
            Animator anim, BiConsumer<AnimatorListener, Animator> command) {
        callAnimatorCommandRecursively(anim, a-> {
            for (AnimatorListener l : nonNullList(a.getListeners())) {
                command.accept(l, a);
            }
        });
    }

    private static void callAnimatorCommandRecursively(Animator anim, Consumer<Animator> command) {
        command.accept(anim);
        if (anim instanceof AnimatorSet) {
            for (Animator child : nonNullList(((AnimatorSet) anim).getChildAnimations())) {
                callAnimatorCommandRecursively(child, command);
            }
        }
    }

    /**
     * Only dispatches the on end actions once the animator and all springs have completed running.
     */
    private class OnAnimationEndDispatcher extends AnimationSuccessListener {

        boolean mDispatched = false;

        @Override
        public void onAnimationStart(Animator animation) {
            mCancelled = false;
            mDispatched = false;
        }

        @Override
        public void onAnimationSuccess(Animator animator) {
            // We wait for the spring (if any) to finish running before completing the end callback.
            if (!mDispatched) {
                dispatchOnEnd();
                if (mEndAction != null) {
                    mEndAction.run();
                }
                mDispatched = true;
            }
        }
    }

    private static <T> List<T> nonNullList(ArrayList<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Interface for mapping progress to animation progress
     */
    private interface ProgressMapper {

        ProgressMapper DEFAULT = (progress, globalEndProgress) ->
                progress > globalEndProgress ? 1 : (progress / globalEndProgress);

        float getProgress(float progress, float globalProgress);
    }

    /**
     * Holder class for various child animations
     */
    static class Holder {

        public final ValueAnimator anim;

        public final SpringProperty springProperty;

        public final TimeInterpolator interpolator;

        public final float globalEndProgress;

        public ProgressMapper mapper;

        Holder(Animator anim, float globalDuration, SpringProperty springProperty) {
            this.anim = (ValueAnimator) anim;
            this.springProperty = springProperty;
            this.interpolator = this.anim.getInterpolator();
            this.globalEndProgress = anim.getDuration() / globalDuration;
            this.mapper = ProgressMapper.DEFAULT;
        }

        public void setProgress(float progress) {
            anim.setCurrentFraction(mapper.getProgress(progress, globalEndProgress));
        }

        public void reset() {
            anim.setInterpolator(interpolator);
            mapper = ProgressMapper.DEFAULT;
        }
    }

    static void addAnimationHoldersRecur(Animator anim, long globalDuration,
            SpringProperty springProperty, ArrayList<Holder> out) {
        long forceDuration = anim.getDuration();
        TimeInterpolator forceInterpolator = anim.getInterpolator();
        if (anim instanceof ValueAnimator) {
            out.add(new Holder(anim, globalDuration, springProperty));
        } else if (anim instanceof AnimatorSet) {
            for (Animator child : ((AnimatorSet) anim).getChildAnimations()) {
                if (forceDuration > 0) {
                    child.setDuration(forceDuration);
                }
                if (forceInterpolator != null) {
                    child.setInterpolator(forceInterpolator);
                }
                addAnimationHoldersRecur(child, globalDuration, springProperty, out);
            }
        } else {
            throw new RuntimeException("Unknown animation type " + anim);
        }
    }
}
