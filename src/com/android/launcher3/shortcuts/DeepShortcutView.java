/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.support.annotation.IntDef;
import android.util.AttributeSet;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherViewPropertyAnimator;
import com.android.launcher3.R;
import com.android.launcher3.util.PillRevealOutlineProvider;

/**
 * A {@link android.widget.FrameLayout} that contains a {@link DeepShortcutView}.
 * This lets us animate the DeepShortcutView (icon and text) separately from the background.
 */
public class DeepShortcutView extends FrameLayout {

    private static final float HOVER_SCALE = 1.05f;

    // The direction this view should translate when animating the hover state.
    // This allows hovered shortcuts to "push" other shortcuts away.
    @IntDef({DIRECTION_UP, DIRECTION_NONE, DIRECTION_DOWN})
    public @interface TranslationDirection {}

    public static final int DIRECTION_UP = -1;
    public static final int DIRECTION_NONE = 0;
    public static final int DIRECTION_DOWN = 1;

    @TranslationDirection
    private int mTranslationDirection = DIRECTION_NONE;

    private int mSpacing;
    private int mRadius;
    private Rect mPillRect;
    private int mTop;
    private boolean mIsHoveringOver = false;
    private LauncherViewPropertyAnimator mHoverAnimator;

    private BubbleTextView mBubbleText;

    public DeepShortcutView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mSpacing = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
        mRadius = getResources().getDimensionPixelSize(R.dimen.bg_pill_radius);
        mPillRect = new Rect();
        mHoverAnimator = new LauncherViewPropertyAnimator(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBubbleText = (BubbleTextView) findViewById(R.id.deep_shortcut);
    }

    public BubbleTextView getBubbleText() {
        return mBubbleText;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mPillRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    public void setPivotX(float pivotX) {
        super.setPivotX(pivotX);
        mBubbleText.setPivotX(pivotX);
    }

    @Override
    public void setPivotY(float pivotY) {
        super.setPivotY(pivotY);
        mBubbleText.setPivotY(pivotY);
    }

    /**
     * Creates an animator to play when the shortcut container is being opened.
     *
     * @param animationIndex The index at which this animation will be started
     *                       relative to other DeepShortcutView open animations.
     */
    public Animator createOpenAnimation(int animationIndex, boolean isContainerAboveIcon) {
        final Resources res = getResources();
        setVisibility(INVISIBLE);

        AnimatorSet openAnimation = LauncherAnimUtils.createAnimatorSet();

        Animator reveal = new PillRevealOutlineProvider((int) getPivotX(), (int) getPivotY(),
                mPillRect, mRadius).createRevealAnimator(this);
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
            }
        });

        float transY = res.getDimensionPixelSize(R.dimen.deep_shortcut_anim_translation_y);
        Animator translationY = ObjectAnimator.ofFloat(this, TRANSLATION_Y,
                isContainerAboveIcon ? transY : -transY, 0);

        // Only scale mBubbleText (the icon and text, not the background).
        mBubbleText.setScaleX(0);
        mBubbleText.setScaleY(0);
        LauncherViewPropertyAnimator scale = new LauncherViewPropertyAnimator(mBubbleText)
                .scaleX(1).scaleY(1);

        openAnimation.playTogether(reveal, translationY, scale);
        openAnimation.setStartDelay(animationIndex * res.getInteger(
                R.integer.config_deepShortcutOpenStagger));
        openAnimation.setDuration(res.getInteger(R.integer.config_deepShortcutOpenDuration));
        openAnimation.setInterpolator(new DecelerateInterpolator());
        return openAnimation;
    }

    /**
     * Updates the state of this view based on touches over the container before user lifts finger.
     *
     * @param containerContainsTouch whether the {@link DeepShortcutsContainer} this shortcut
     *                               is inside contains the current touch
     * @param isBelowHoveredShortcut whether a sibling shortcut before this one in the
     *                               view hierarchy is being hovered over
     * @param touchY the y coordinate of the touch, relative to the {@link DeepShortcutsContainer}
     *               this shortcut is inside
     * @return whether this shortcut is being hovered over
     */
    public boolean updateHoverState(boolean containerContainsTouch, boolean isBelowHoveredShortcut,
            float touchY) {
        if (!containerContainsTouch) {
            mIsHoveringOver = false;
            mTranslationDirection = DIRECTION_NONE;
        } else if (isBelowHoveredShortcut) {
            mIsHoveringOver = false;
            mTranslationDirection = DIRECTION_DOWN;
        } else {
            // Include space around the view when determining hover state to avoid gaps.
            mTop = (int) (getY() - getTranslationY());
            mIsHoveringOver = (touchY >= mTop - mSpacing / 2)
                    && (touchY < mTop + getHeight() + mSpacing / 2);
            mTranslationDirection = mIsHoveringOver ? DIRECTION_NONE : DIRECTION_UP;
        }
        animateHoverState();
        return mIsHoveringOver;
    }

    /**
     * If this shortcut is being hovered over, we scale it up. If another shortcut is being hovered
     * over, we translate this one away from it to account for its increased size.
     */
    private void animateHoverState() {
        if (mHoverAnimator.isRunning()) {
            return;
        }
        float scale = mIsHoveringOver ? HOVER_SCALE : 1f;
        float translateY = (HOVER_SCALE - 1f) * getHeight() * mTranslationDirection;
        mHoverAnimator.scaleX(scale).scaleY(scale).translationY(translateY)
                .setDuration(getResources().getInteger(R.integer.config_deepShortcutHoverDuration))
                .start();
    }

    public boolean isHoveringOver() {
        return mIsHoveringOver;
    }
}
