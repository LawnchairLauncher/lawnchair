/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.launcher3.anim.AnimatedFloat.VALUE;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.taskbar.StashedHandleViewController.ALPHA_INDEX_NUDGED;
import static com.android.launcher3.taskbar.StashedHandleViewController.ALPHA_INDEX_STASHED;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.util.MultiValueAlpha;

public class NudgeView extends ImageView {

    private static final float DAMPING_RATIO = 0.6f;
    private static final float STIFFNESS = 380f;

    private AnimatorSet mAnimatorSet;
    private boolean mNudgeShown;
    private int mNudgePillWidth;
    private int mNudgePillHeight;
    private int mStashedHandleWidth;
    private int mStashedHandleHeight;
    private ImageView mNudgeIcon;
    private MultiValueAlpha mStashedHandleViewAlpha;

    private final AnimatedFloat mAlphaForHandleView = new AnimatedFloat(
            this::updateAlphaForHandleView);
    private final AnimatedFloat mAlphaForNudgeIcon = new AnimatedFloat(
            this::updateAlphaForNudgeIcon);
    private final AnimatedFloat mWidthForNudgePill = new AnimatedFloat(
            this::updateWidthForNudge);
    private final AnimatedFloat mHeightForNudgePill = new AnimatedFloat(
            this::updateHeightForNudge);

    public NudgeView(Context context) {
        this(context, null);
    }

    public NudgeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NudgeView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public NudgeView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        if (Flags.nudgePill()) {
            mNudgePillWidth = context.getResources().getDimensionPixelSize(
                    R.dimen.taskbar_nudge_pill_width);
            mNudgePillHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.taskbar_nudge_pill_height);
            mStashedHandleWidth = context.getResources().getDimensionPixelSize(
                    R.dimen.taskbar_nudge_pill_width);
            mStashedHandleHeight = context.getResources().getDimensionPixelSize(
                    R.dimen.taskbar_stashed_handle_height);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mNudgeIcon = findViewById(R.id.nudge_icon);
    }

    void updateNudgeIcon(boolean showNudge, @Nullable Drawable icon,
            @Nullable MultiValueAlpha stashedHandleViewAlpha) {
        if (!Flags.nudgePill() || stashedHandleViewAlpha == null) {
            return;
        }
        mStashedHandleViewAlpha = stashedHandleViewAlpha;
        // Set update visibility to false when showing the nudge otherwise
        // StashHandleView will be INVISIBLE at low alpha resulting in being unable to invoke CtS.
        mStashedHandleViewAlpha.setUpdateVisibility(false);
        mNudgeIcon.setImageDrawable(null);
        if (icon != null) {
            mNudgeIcon.setImageDrawable(icon);
        }
        mNudgeIcon.setVisibility(VISIBLE);
        if (showNudge && !mNudgeShown) {
            post(() -> animationBarToPill(true /* showNudge */));
        } else if (!showNudge){
            post(() -> animationBarToPill(false /* showNudge */));
        }
    }

    private void animationBarToPill(boolean showNudge) {
        if (!Flags.nudgePill()) {
            return;
        }
        if (mAnimatorSet != null && mAnimatorSet.isRunning()) {
            mAnimatorSet.cancel();
        }
        mNudgeShown = showNudge;
        // Alpha
        float targetIconAlpha = showNudge ? 1.0f : 0;
        float targetHandleViewAlpha = showNudge ? 0 : 1.0f;
        // Animate from navBar -> nudgePill if showNudge is true.
        float startWidth = showNudge ? mStashedHandleWidth : mNudgePillWidth;
        float startHeight = showNudge ? mStashedHandleHeight : mNudgePillHeight;
        float endWidth = showNudge ? mNudgePillWidth : mStashedHandleWidth;
        float endHeight = showNudge ? mNudgePillHeight : mStashedHandleHeight;

        ValueAnimator alphaNudgeIcon = new SpringAnimationBuilder(mContext)
                .setStartValue(mNudgeIcon.getAlpha())
                .setEndValue(targetIconAlpha)
                .setDampingRatio(DAMPING_RATIO)
                .setStiffness(STIFFNESS)
                .build(mAlphaForNudgeIcon, VALUE);
        if (!showNudge) {
            alphaNudgeIcon.addListener(forEndCallback(() -> {
                mNudgeIcon.setVisibility(GONE);
                mStashedHandleViewAlpha.setUpdateVisibility(true);
            }));
        }
        ValueAnimator alphaHandleView = new SpringAnimationBuilder(mContext)
                .setStartValue(mStashedHandleViewAlpha.get(ALPHA_INDEX_STASHED).getValue())
                .setEndValue(targetHandleViewAlpha)
                .setDampingRatio(DAMPING_RATIO)
                .setStiffness(STIFFNESS)
                .build(mAlphaForHandleView, VALUE);
        ValueAnimator widthAnimation = new SpringAnimationBuilder(mContext)
                .setStartValue(startWidth)
                .setEndValue(endWidth)
                .setDampingRatio(DAMPING_RATIO)
                .setStiffness(STIFFNESS)
                .build(mWidthForNudgePill, VALUE);
        ValueAnimator heightAnimation = new SpringAnimationBuilder(mContext)
                .setStartValue(startHeight)
                .setEndValue(endHeight)
                .setDampingRatio(DAMPING_RATIO)
                .setStiffness(STIFFNESS)
                .build(mHeightForNudgePill, VALUE);
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playTogether(
                alphaNudgeIcon, alphaHandleView,
                widthAnimation, heightAnimation
        );
        mAnimatorSet.start();
    }

    private void updateAlphaForHandleView() {
        if (!Flags.nudgePill()) {
            return;
        }
        float alpha = mAlphaForHandleView.value;
        mStashedHandleViewAlpha.get(ALPHA_INDEX_NUDGED).setValue(alpha);
    }

    private void updateAlphaForNudgeIcon() {
        if (!Flags.nudgePill()) {
            return;
        }
        float alpha = mAlphaForNudgeIcon.value;
        mNudgeIcon.setAlpha(alpha);
    }

    private void updateWidthForNudge() {
        if (!Flags.nudgePill()) {
            return;
        }
        float width = mWidthForNudgePill.value;
        ViewGroup.MarginLayoutParams nudgePillLayoutParams =
                (ViewGroup.MarginLayoutParams) mNudgeIcon.getLayoutParams();
        nudgePillLayoutParams.width = (int) width;
        mNudgeIcon.setLayoutParams(nudgePillLayoutParams);
    }

    private void updateHeightForNudge() {
        if (!Flags.nudgePill()) {
            return;
        }
        float height = mHeightForNudgePill.value;
        ViewGroup.MarginLayoutParams nudgePillLayoutParams =
                (ViewGroup.MarginLayoutParams) mNudgeIcon.getLayoutParams();
        nudgePillLayoutParams.height = (int) height;
        mNudgeIcon.setLayoutParams(nudgePillLayoutParams);
    }
}
