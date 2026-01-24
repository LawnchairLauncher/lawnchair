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
package com.android.wm.shell.bubbles.animation;

import static com.android.wm.shell.bubbles.BubbleExpandedView.BACKGROUND_ALPHA;
import static com.android.wm.shell.bubbles.BubbleExpandedView.BOTTOM_CLIP_PROPERTY;
import static com.android.wm.shell.bubbles.BubbleExpandedView.CONTENT_ALPHA;
import static com.android.wm.shell.bubbles.BubbleExpandedView.MANAGE_BUTTON_ALPHA;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PointF;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.animation.FlingAnimationUtils;
import com.android.wm.shell.bubbles.BubbleExpandedView;
import com.android.wm.shell.bubbles.BubblePositioner;
import com.android.wm.shell.shared.animation.Interpolators;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ExpandedViewAnimationController} that uses a collapse animation to
 * hide the {@link BubbleExpandedView}
 */
public class ExpandedViewAnimationControllerImpl implements ExpandedViewAnimationController {

    private static final float COLLAPSE_THRESHOLD = 0.02f;

    private static final int COLLAPSE_DURATION_MS = 250;

    private static final int MANAGE_BUTTON_ANIM_DURATION_MS = 78;

    private static final int CONTENT_OPACITY_ANIM_DELAY_MS = 93;
    private static final int CONTENT_OPACITY_ANIM_DURATION_MS = 78;

    private static final int BACKGROUND_OPACITY_ANIM_DELAY_MS = 172;
    private static final int BACKGROUND_OPACITY_ANIM_DURATION_MS = 78;

    /** Animation fraction threshold for content alpha animation when stack collapse should begin */
    private static final float STACK_COLLAPSE_THRESHOLD = 0.5f;

    private static final FloatPropertyCompat<ExpandedViewAnimationControllerImpl>
            COLLAPSE_HEIGHT_PROPERTY =
            new FloatPropertyCompat<ExpandedViewAnimationControllerImpl>("CollapseSpring") {
                @Override
                public float getValue(ExpandedViewAnimationControllerImpl controller) {
                    return controller.getCollapsedAmount();
                }

                @Override
                public void setValue(ExpandedViewAnimationControllerImpl controller,
                        float value) {
                    controller.setCollapsedAmount(value);
                }
            };

    private final int mMinFlingVelocity;
    private float mSwipeUpVelocity;
    private float mSwipeDownVelocity;
    private final BubblePositioner mPositioner;
    private final FlingAnimationUtils mFlingAnimationUtils;
    private int mDraggedAmount;
    private float mCollapsedAmount;
    @Nullable
    private BubbleExpandedView mExpandedView;
    @Nullable
    private AnimatorSet mCollapseAnimation;
    private boolean mNotifiedAboutThreshold;
    private SpringAnimation mBackToExpandedAnimation;
    @Nullable
    private ObjectAnimator mBottomClipAnim;

    public ExpandedViewAnimationControllerImpl(Context context, BubblePositioner positioner) {
        mFlingAnimationUtils = new FlingAnimationUtils(context.getResources().getDisplayMetrics(),
                COLLAPSE_DURATION_MS / 1000f);
        mMinFlingVelocity = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mPositioner = positioner;
    }

    private static void adjustAnimatorSetDuration(AnimatorSet animatorSet,
            float durationAdjustment) {
        for (Animator animator : animatorSet.getChildAnimations()) {
            animator.setStartDelay((long) (animator.getStartDelay() * durationAdjustment));
            animator.setDuration((long) (animator.getDuration() * durationAdjustment));
        }
    }

    @Override
    public void setExpandedView(BubbleExpandedView expandedView) {
        if (mExpandedView != null) {
            if (mCollapseAnimation != null) {
                mCollapseAnimation.cancel();
            }
            if (mBackToExpandedAnimation != null) {
                mBackToExpandedAnimation.cancel();
            }
            reset();
        }
        mExpandedView = expandedView;
    }

    @Override
    public void updateDrag(float distance) {
        if (mExpandedView != null) {
            mDraggedAmount = OverScroll.dampedScroll(distance, mExpandedView.getContentHeight());

            ProtoLog.d(WM_SHELL_BUBBLES,
                    "updateDrag: distance=%f dragged=%d", distance, mDraggedAmount);

            setCollapsedAmount(mDraggedAmount);

            if (!mNotifiedAboutThreshold && isPastCollapseThreshold()) {
                mNotifiedAboutThreshold = true;
                ProtoLog.d(WM_SHELL_BUBBLES, "notifying over collapse threshold");
                vibrateIfEnabled();
            }
        }
    }

    @Override
    public void setSwipeVelocity(float velocity) {
        if (velocity < 0) {
            mSwipeUpVelocity = Math.abs(velocity);
            mSwipeDownVelocity = 0;
        } else {
            mSwipeUpVelocity = 0;
            mSwipeDownVelocity = velocity;
        }
    }

    @Override
    public boolean shouldCollapse() {
        if (mSwipeDownVelocity > mMinFlingVelocity) {
            // Swipe velocity is positive and over fling velocity.
            // This is a swipe down, always reset to expanded state, regardless of dragged amount.
            ProtoLog.d(WM_SHELL_BUBBLES,
                    "not collapsing expanded view, swipe down velocity=%f minV=%d",
                    mSwipeDownVelocity, mMinFlingVelocity);
            return false;
        }

        if (mSwipeUpVelocity > mMinFlingVelocity) {
            // Swiping up and over fling velocity, collapse the view.
            ProtoLog.d(WM_SHELL_BUBBLES,
                    "collapse expanded view, swipe up velocity=%f minV=%d",
                    mSwipeUpVelocity, mMinFlingVelocity);
            return true;
        }

        if (isPastCollapseThreshold()) {
            ProtoLog.d(WM_SHELL_BUBBLES,
                    "collapse expanded view, past threshold, dragged=%d", mDraggedAmount);
            return true;
        }

        ProtoLog.d(WM_SHELL_BUBBLES, "not collapsing expanded view");

        return false;
    }

    @Override
    public void animateCollapse(Runnable startStackCollapse, Runnable after,
            PointF collapsePosition) {
        ProtoLog.d(WM_SHELL_BUBBLES, "expandedView animate collapse swipeVel=%f minFlingVel=%d"
                        + " collapsePosition=%f,%f", mSwipeUpVelocity, mMinFlingVelocity,
                collapsePosition.x, collapsePosition.y);
        if (mExpandedView != null) {
            // Mark it as animating immediately to avoid updates to the view before animation starts
            mExpandedView.setAnimating(true);

            if (mCollapseAnimation != null) {
                mCollapseAnimation.cancel();
            }
            mCollapseAnimation = createCollapseAnimation(mExpandedView, startStackCollapse, after);

            if (mSwipeUpVelocity >= mMinFlingVelocity) {
                int contentHeight = mExpandedView.getContentHeight();

                // Use a temp animator to get adjusted duration value for swipe.
                // This new value will be used to adjust animation times proportionally in the
                // animator set. If we adjust animator set duration directly, all child animations
                // will get the same animation time.
                ValueAnimator tempAnimator = new ValueAnimator();
                mFlingAnimationUtils.applyDismissing(tempAnimator, mCollapsedAmount, contentHeight,
                        mSwipeUpVelocity, (contentHeight - mCollapsedAmount));

                float durationAdjustment =
                        (float) tempAnimator.getDuration() / COLLAPSE_DURATION_MS;

                adjustAnimatorSetDuration(mCollapseAnimation, durationAdjustment);
                mCollapseAnimation.setInterpolator(tempAnimator.getInterpolator());
            }
            mCollapseAnimation.start();
        }
    }

    @Override
    public void animateBackToExpanded() {
        ProtoLog.d(WM_SHELL_BUBBLES, "expandedView animate back to expanded");
        BubbleExpandedView expandedView = mExpandedView;
        if (expandedView == null) {
            return;
        }

        expandedView.setAnimating(true);

        mBackToExpandedAnimation = new SpringAnimation(this, COLLAPSE_HEIGHT_PROPERTY);
        mBackToExpandedAnimation.setSpring(new SpringForce()
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY)
        );
        mBackToExpandedAnimation.addEndListener(new OneTimeEndListener() {
            @Override
            public void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value,
                    float velocity) {
                super.onAnimationEnd(animation, canceled, value, velocity);
                mNotifiedAboutThreshold = false;
                mBackToExpandedAnimation = null;
                expandedView.setAnimating(false);
            }
        });
        mBackToExpandedAnimation.setStartValue(mCollapsedAmount);
        mBackToExpandedAnimation.animateToFinalPosition(0);
    }

    @Override
    public void animateForImeVisibilityChange(boolean visible) {
        if (mExpandedView != null) {
            if (mBottomClipAnim != null) {
                mBottomClipAnim.cancel();
            }
            int clip = 0;
            if (visible) {
                // Clip the expanded view at the top of the IME view
                clip = mExpandedView.getContentBottomOnScreen() - mPositioner.getImeTop();
                // Don't allow negative clip value
                clip = Math.max(clip, 0);
            }
            mBottomClipAnim = ObjectAnimator.ofInt(mExpandedView, BOTTOM_CLIP_PROPERTY, clip);
            mBottomClipAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mBottomClipAnim = null;
                }
            });
            mBottomClipAnim.start();
        }
    }

    @Override
    public boolean shouldAnimateExpansion() {
        return false;
    }

    @Override
    public void animateExpansion(long startDelayMillis, Runnable after, PointF collapsePosition,
            PointF bubblePosition) {
        // TODO - animate
    }

    @Override
    public void reset() {
        ProtoLog.d(WM_SHELL_BUBBLES, "reset expandedView collapsed state");
        if (mExpandedView == null) {
            return;
        }
        mExpandedView.setAnimating(false);

        if (mCollapseAnimation != null) {
            mCollapseAnimation.cancel();
        }
        if (mBackToExpandedAnimation != null) {
            mBackToExpandedAnimation.cancel();
        }
        mExpandedView.setContentAlpha(1);
        mExpandedView.setBackgroundAlpha(1);
        mExpandedView.setManageButtonAlpha(1);
        setCollapsedAmount(0);
        mExpandedView.setBottomClip(0);
        mExpandedView.movePointerBy(0, 0);
        mCollapsedAmount = 0;
        mDraggedAmount = 0;
        mSwipeUpVelocity = 0;
        mSwipeDownVelocity = 0;
        mNotifiedAboutThreshold = false;
    }

    private float getCollapsedAmount() {
        return mCollapsedAmount;
    }

    private void setCollapsedAmount(float collapsed) {
        if (mCollapsedAmount != collapsed) {
            float previous = mCollapsedAmount;
            mCollapsedAmount = collapsed;

            if (mExpandedView != null) {
                if (previous == 0) {
                    // View was not collapsed before. Apply z order change
                    mExpandedView.setSurfaceZOrderedOnTop(true);
                    mExpandedView.setAnimating(true);
                }

                mExpandedView.setTopClip((int) mCollapsedAmount);
                // Move up with translationY. Use negative collapsed value
                mExpandedView.setContentTranslationY(-mCollapsedAmount);
                mExpandedView.setManageButtonTranslationY(-mCollapsedAmount);

                if (mCollapsedAmount == 0) {
                    // View is no longer collapsed. Revert z order change
                    mExpandedView.setSurfaceZOrderedOnTop(false);
                    mExpandedView.setAnimating(false);
                }
            }
        }
    }

    private boolean isPastCollapseThreshold() {
        if (mExpandedView != null) {
            return mDraggedAmount > mExpandedView.getContentHeight() * COLLAPSE_THRESHOLD;
        }
        return false;
    }

    private AnimatorSet createCollapseAnimation(BubbleExpandedView expandedView,
            Runnable startStackCollapse, Runnable after) {
        List<Animator> animatorList = new ArrayList<>();
        animatorList.add(createHeightAnimation(expandedView));
        animatorList.add(createManageButtonAnimation());
        ObjectAnimator contentAlphaAnimation = createContentAlphaAnimation();
        final boolean[] notified = {false};
        contentAlphaAnimation.addUpdateListener(animation -> {
            if (!notified[0] && animation.getAnimatedFraction() > STACK_COLLAPSE_THRESHOLD) {
                notified[0] = true;
                // Notify bubbles that they can start moving back to the collapsed position
                startStackCollapse.run();
            }
        });
        animatorList.add(contentAlphaAnimation);
        animatorList.add(createBackgroundAlphaAnimation());

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                after.run();
            }
        });
        animatorSet.playTogether(animatorList);
        return animatorSet;
    }

    private ValueAnimator createHeightAnimation(BubbleExpandedView expandedView) {
        ValueAnimator animator = ValueAnimator.ofInt((int) mCollapsedAmount,
                expandedView.getContentHeight());
        animator.setInterpolator(Interpolators.EMPHASIZED_ACCELERATE);
        animator.setDuration(COLLAPSE_DURATION_MS);
        animator.addUpdateListener(anim -> setCollapsedAmount((int) anim.getAnimatedValue()));
        return animator;
    }

    private ObjectAnimator createManageButtonAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mExpandedView, MANAGE_BUTTON_ALPHA, 0f);
        animator.setDuration(MANAGE_BUTTON_ANIM_DURATION_MS);
        animator.setInterpolator(Interpolators.LINEAR);
        return animator;
    }

    private ObjectAnimator createContentAlphaAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mExpandedView, CONTENT_ALPHA, 0f);
        animator.setDuration(CONTENT_OPACITY_ANIM_DURATION_MS);
        animator.setInterpolator(Interpolators.LINEAR);
        animator.setStartDelay(CONTENT_OPACITY_ANIM_DELAY_MS);
        return animator;
    }

    private ObjectAnimator createBackgroundAlphaAnimation() {
        ObjectAnimator animator = ObjectAnimator.ofFloat(mExpandedView, BACKGROUND_ALPHA, 0f);
        animator.setDuration(BACKGROUND_OPACITY_ANIM_DURATION_MS);
        animator.setInterpolator(Interpolators.LINEAR);
        animator.setStartDelay(BACKGROUND_OPACITY_ANIM_DELAY_MS);
        return animator;
    }

    @SuppressLint("MissingPermission")
    private void vibrateIfEnabled() {
        if (mExpandedView != null) {
            mExpandedView.performHapticFeedback(HapticFeedbackConstants.DRAG_CROSSING);
        }
    }
}
