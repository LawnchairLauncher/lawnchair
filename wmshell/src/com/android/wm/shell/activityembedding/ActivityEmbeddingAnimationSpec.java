/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.activityembedding;


import static android.app.ActivityOptions.ANIM_CUSTOM;

import static com.android.internal.policy.TransitionAnimation.WALLPAPER_TRANSITION_NONE;
import static com.android.wm.shell.transition.TransitionAnimationHelper.loadAttributeAnimation;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionTypeFromInfo;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Rect;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.window.TransitionInfo;

import com.android.internal.policy.TransitionAnimation;
import com.android.window.flags.Flags;
import com.android.wm.shell.shared.TransitionUtil;

/** Animation spec for ActivityEmbedding transition. */
// TODO(b/206557124): provide an easier way to customize animation
class ActivityEmbeddingAnimationSpec {

    private static final String TAG = "ActivityEmbeddingAnimSpec";
    private static final int CHANGE_ANIMATION_DURATION = 517;
    private static final int CHANGE_ANIMATION_FADE_DURATION = 80;
    private static final int CHANGE_ANIMATION_FADE_OFFSET = 30;

    private final Context mContext;
    private final TransitionAnimation mTransitionAnimation;
    private final Interpolator mFastOutExtraSlowInInterpolator;
    private final LinearInterpolator mLinearInterpolator;
    private float mTransitionAnimationScaleSetting;

    ActivityEmbeddingAnimationSpec(@NonNull Context context) {
        mContext = context;
        mTransitionAnimation = new TransitionAnimation(mContext, false /* debug */, TAG);
        mFastOutExtraSlowInInterpolator = AnimationUtils.loadInterpolator(
                mContext, android.R.interpolator.fast_out_extra_slow_in);
        mLinearInterpolator = new LinearInterpolator();
    }

    /**
     * Sets transition animation scale settings value.
     * @param scale The setting value of transition animation scale.
     */
    void setAnimScaleSetting(float scale) {
        mTransitionAnimationScaleSetting = scale;
    }

    /** For window that doesn't need to be animated. */
    @NonNull
    static Animation createNoopAnimation(@NonNull TransitionInfo.Change change) {
        // Noop but just keep the window showing/hiding.
        final float alpha = TransitionUtil.isClosingType(change.getMode()) ? 0f : 1f;
        return new AlphaAnimation(alpha, alpha);
    }

    /**
     * Animation that intended to show snapshot for closing animation because the closing end bounds
     * are changed.
     */
    @NonNull
    static Animation createShowSnapshotForClosingAnimation() {
        return new AlphaAnimation(1f, 1f);
    }

    /** Animation for window that is opening in a change transition. */
    @NonNull
    Animation createChangeBoundsOpenAnimation(@NonNull TransitionInfo.Change change,
            @NonNull Rect parentBounds) {
        // Use end bounds for opening.
        final Rect bounds = change.getEndAbsBounds();
        final int startLeft;
        final int startTop;
        if (parentBounds.top == bounds.top && parentBounds.bottom == bounds.bottom) {
            // The window will be animated in from left or right depending on its position.
            startTop = 0;
            startLeft = parentBounds.left == bounds.left ? -bounds.width() : bounds.width();
        } else {
            // The window will be animated in from top or bottom depending on its position.
            startTop = parentBounds.top == bounds.top ? -bounds.height() : bounds.height();
            startLeft = 0;
        }

        // The position should be 0-based as we will post translate in
        // ActivityEmbeddingAnimationAdapter#onAnimationUpdate
        final Animation animation = new TranslateAnimation(startLeft, 0, startTop, 0);
        animation.setInterpolator(mFastOutExtraSlowInInterpolator);
        animation.setDuration(CHANGE_ANIMATION_DURATION);
        animation.initialize(bounds.width(), bounds.height(), bounds.width(), bounds.height());
        animation.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        return animation;
    }

    /** Animation for window that is closing in a change transition. */
    @NonNull
    Animation createChangeBoundsCloseAnimation(@NonNull TransitionInfo.Change change,
            @NonNull Rect parentBounds) {
        // Use start bounds for closing.
        final Rect bounds = change.getStartAbsBounds();
        final int endTop;
        final int endLeft;
        if (parentBounds.top == bounds.top && parentBounds.bottom == bounds.bottom) {
            // The window will be animated out to left or right depending on its position.
            endTop = 0;
            endLeft = parentBounds.left == bounds.left ? -bounds.width() : bounds.width();
        } else {
            // The window will be animated out to top or bottom depending on its position.
            endTop = parentBounds.top == bounds.top ? -bounds.height() : bounds.height();
            endLeft = 0;
        }

        // The position should be 0-based as we will post translate in
        // ActivityEmbeddingAnimationAdapter#onAnimationUpdate
        final Animation animation = new TranslateAnimation(0, endLeft, 0, endTop);
        animation.setInterpolator(mFastOutExtraSlowInInterpolator);
        animation.setDuration(CHANGE_ANIMATION_DURATION);
        animation.initialize(bounds.width(), bounds.height(), bounds.width(), bounds.height());
        animation.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        return animation;
    }

    /**
     * Animation for window that is changing (bounds change) in a change transition.
     * @return the return array always has two elements. The first one is for the start leash, and
     *         the second one is for the end leash.
     */
    @NonNull
    Animation[] createChangeBoundsChangeAnimations(@NonNull TransitionInfo.Change change,
            @NonNull Rect parentBounds) {
        // Both start bounds and end bounds are in screen coordinates. We will post translate
        // to the local coordinates in ActivityEmbeddingAnimationAdapter#onAnimationUpdate
        final Rect startBounds = change.getStartAbsBounds();
        final Rect endBounds = change.getEndAbsBounds();
        float scaleX = ((float) startBounds.width()) / endBounds.width();
        float scaleY = ((float) startBounds.height()) / endBounds.height();
        // Start leash is a child of the end leash. Reverse the scale so that the start leash won't
        // be scaled up with its parent.
        float startScaleX = 1.f / scaleX;
        float startScaleY = 1.f / scaleY;

        // The start leash will be fade out.
        final AnimationSet startSet = new AnimationSet(false /* shareInterpolator */);
        final Animation startAlpha = new AlphaAnimation(1f, 0f);
        startAlpha.setInterpolator(mLinearInterpolator);
        startAlpha.setDuration(CHANGE_ANIMATION_FADE_DURATION);
        startAlpha.setStartOffset(CHANGE_ANIMATION_FADE_OFFSET);
        startSet.addAnimation(startAlpha);
        final Animation startScale = new ScaleAnimation(startScaleX, startScaleX, startScaleY,
                startScaleY);
        startScale.setInterpolator(mFastOutExtraSlowInInterpolator);
        startScale.setDuration(CHANGE_ANIMATION_DURATION);
        startSet.addAnimation(startScale);
        startSet.initialize(startBounds.width(), startBounds.height(), endBounds.width(),
                endBounds.height());
        startSet.scaleCurrentDuration(mTransitionAnimationScaleSetting);

        // The end leash will be moved into the end position while scaling.
        final AnimationSet endSet = new AnimationSet(true /* shareInterpolator */);
        endSet.setInterpolator(mFastOutExtraSlowInInterpolator);
        final Animation endScale = new ScaleAnimation(scaleX, 1, scaleY, 1);
        endScale.setDuration(CHANGE_ANIMATION_DURATION);
        endSet.addAnimation(endScale);
        // The position should be 0-based as we will post translate in
        // ActivityEmbeddingAnimationAdapter#onAnimationUpdate
        final Animation endTranslate = new TranslateAnimation(startBounds.left - endBounds.left, 0,
                startBounds.top - endBounds.top, 0);
        endTranslate.setDuration(CHANGE_ANIMATION_DURATION);
        endSet.addAnimation(endTranslate);
        endSet.initialize(startBounds.width(), startBounds.height(), parentBounds.width(),
                parentBounds.height());
        endSet.scaleCurrentDuration(mTransitionAnimationScaleSetting);

        return new Animation[]{startSet, endSet};
    }

    @NonNull
    Animation loadOpenAnimation(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change, @NonNull Rect wholeAnimationBounds) {
        final boolean isEnter = TransitionUtil.isOpeningType(change.getMode());
        final Animation customAnimation = loadCustomAnimation(info, change, isEnter);
        final Animation animation;
        if (customAnimation != null) {
            animation = customAnimation;
        } else if (shouldShowBackdrop(info, change)) {
            animation = mTransitionAnimation.loadDefaultAnimationRes(isEnter
                    ? com.android.internal.R.anim.task_fragment_clear_top_open_enter
                    : com.android.internal.R.anim.task_fragment_clear_top_open_exit);
        } else {
            // Use the same edge extension animation as regular activity open.
            animation = mTransitionAnimation.loadDefaultAnimationRes(isEnter
                    ? com.android.internal.R.anim.activity_open_enter
                    : com.android.internal.R.anim.activity_open_exit);
        }
        // Use the whole animation bounds instead of the change bounds, so that when multiple change
        // targets are opening at the same time, the animation applied to each will be the same.
        // Otherwise, we may see gap between the activities that are launching together.
        animation.initialize(wholeAnimationBounds.width(), wholeAnimationBounds.height(),
                wholeAnimationBounds.width(), wholeAnimationBounds.height());
        animation.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        return animation;
    }

    @NonNull
    Animation loadCloseAnimation(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change, @NonNull Rect wholeAnimationBounds) {
        final boolean isEnter = TransitionUtil.isOpeningType(change.getMode());
        final Animation customAnimation = loadCustomAnimation(info, change, isEnter);
        final Animation animation;
        if (customAnimation != null) {
            animation = customAnimation;
        } else if (shouldShowBackdrop(info, change)) {
            animation = mTransitionAnimation.loadDefaultAnimationRes(isEnter
                    ? com.android.internal.R.anim.task_fragment_clear_top_close_enter
                    : com.android.internal.R.anim.task_fragment_clear_top_close_exit);
        } else {
            // Use the same edge extension animation as regular activity close.
            animation = mTransitionAnimation.loadDefaultAnimationRes(isEnter
                    ? com.android.internal.R.anim.activity_close_enter
                    : com.android.internal.R.anim.activity_close_exit);
        }
        // Use the whole animation bounds instead of the change bounds, so that when multiple change
        // targets are closing at the same time, the animation applied to each will be the same.
        // Otherwise, we may see gap between the activities that are finishing together.
        animation.initialize(wholeAnimationBounds.width(), wholeAnimationBounds.height(),
                wholeAnimationBounds.width(), wholeAnimationBounds.height());
        animation.scaleCurrentDuration(mTransitionAnimationScaleSetting);
        return animation;
    }

    private boolean shouldShowBackdrop(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change) {
        final int type = getTransitionTypeFromInfo(info);
        final Animation a = loadAttributeAnimation(type, info, change, WALLPAPER_TRANSITION_NONE,
                mTransitionAnimation, false);
        return a != null && a.getShowBackdrop();
    }

    @Nullable
    private Animation loadCustomAnimation(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change change, boolean isEnter) {
        final TransitionInfo.AnimationOptions options;
        if (Flags.moveAnimationOptionsToChange()) {
            options = change.getAnimationOptions();
        } else {
            options = info.getAnimationOptions();
        }
        if (options == null || options.getType() != ANIM_CUSTOM) {
            return null;
        }
        final Animation anim = mTransitionAnimation.loadAnimationRes(options.getPackageName(),
                isEnter ? options.getEnterResId() : options.getExitResId());
        if (anim != null) {
            return anim;
        }
        // The app may be intentional to use an invalid resource as a no-op animation.
        // ActivityEmbeddingAnimationRunner#createOpenCloseAnimationAdapters will skip the
        // animation with duration 0. Then it will use prepareForJumpCut for empty adapters.
        return new AlphaAnimation(1f, 1f);
    }
}
