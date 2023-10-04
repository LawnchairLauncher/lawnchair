/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar.bubbles;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconNormalizer;
import com.android.wm.shell.animation.Interpolators;

import java.util.EnumSet;

// TODO: (b/276978250) This is will be similar to WMShell's BadgedImageView, it'd be nice to share.

/**
 * View that displays a bubble icon, along with an app badge on either the left or
 * right side of the view.
 */
public class BubbleView extends ConstraintLayout {

    public static final int DEFAULT_PATH_SIZE = 100;

    /**
     * Flags that suppress the visibility of the 'new' dot or the app badge, for one reason or
     * another. If any of these flags are set, the dot will not be shown.
     * If {@link SuppressionFlag#BEHIND_STACK} then the app badge will not be shown.
     */
    enum SuppressionFlag {
        // TODO: (b/277815200) implement flyout
        // Suppressed because the flyout is visible - it will morph into the dot via animation.
        FLYOUT_VISIBLE,
        // Suppressed because this bubble is behind others in the collapsed stack.
        BEHIND_STACK,
    }

    private final EnumSet<SuppressionFlag> mSuppressionFlags =
            EnumSet.noneOf(SuppressionFlag.class);

    private final ImageView mBubbleIcon;
    private final ImageView mAppIcon;
    private final int mBubbleSize;

    private DotRenderer mDotRenderer;
    private DotRenderer.DrawParams mDrawParams;
    private int mDotColor;
    private Rect mTempBounds = new Rect();

    // Whether the dot is animating
    private boolean mDotIsAnimating;
    // What scale value the dot is animating to
    private float mAnimatingToDotScale;
    // The current scale value of the dot
    private float mDotScale;

    // TODO: (b/273310265) handle RTL
    // Whether the bubbles are positioned on the left or right side of the screen
    private boolean mOnLeft = false;

    private BubbleBarItem mBubble;

    public BubbleView(Context context) {
        this(context, null);
    }

    public BubbleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BubbleView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // We manage positioning the badge ourselves
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        LayoutInflater.from(context).inflate(R.layout.bubble_view, this);

        mBubbleSize = getResources().getDimensionPixelSize(R.dimen.bubblebar_icon_size);
        mBubbleIcon = findViewById(R.id.icon_view);
        mAppIcon = findViewById(R.id.app_icon_view);

        mDrawParams = new DotRenderer.DrawParams();

        setFocusable(true);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                BubbleView.this.getOutline(outline);
            }
        });
    }

    private void getOutline(Outline outline) {
        final int normalizedSize = IconNormalizer.getNormalizedCircleSize(mBubbleSize);
        final int inset = (mBubbleSize - normalizedSize) / 2;
        outline.setOval(inset, inset, inset + normalizedSize, inset + normalizedSize);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!shouldDrawDot()) {
            return;
        }

        getDrawingRect(mTempBounds);

        mDrawParams.dotColor = mDotColor;
        mDrawParams.iconBounds = mTempBounds;
        mDrawParams.leftAlign = mOnLeft;
        mDrawParams.scale = mDotScale;

        mDotRenderer.draw(canvas, mDrawParams);
    }

    /** Sets the bubble being rendered in this view. */
    void setBubble(BubbleBarBubble bubble) {
        mBubble = bubble;
        mBubbleIcon.setImageBitmap(bubble.getIcon());
        mAppIcon.setImageBitmap(bubble.getBadge());
        mDotColor = bubble.getDotColor();
        mDotRenderer = new DotRenderer(mBubbleSize, bubble.getDotPath(), DEFAULT_PATH_SIZE);
    }

    /**
     * Sets that this bubble represents the overflow. The overflow appears in the list of bubbles
     * but does not represent app content, instead it shows recent bubbles that couldn't fit into
     * the list of bubbles. It doesn't show an app icon because it is part of system UI / doesn't
     * come from an app.
     */
    void setOverflow(BubbleBarOverflow overflow, Bitmap bitmap) {
        mBubble = overflow;
        mBubbleIcon.setImageBitmap(bitmap);
        mAppIcon.setVisibility(GONE); // Overflow doesn't show the app badge
        setContentDescription(getResources().getString(R.string.bubble_bar_overflow_description));
    }

    /** Returns the bubble being rendered in this view. */
    @Nullable
    BubbleBarItem getBubble() {
        return mBubble;
    }

    void updateDotVisibility(boolean animate) {
        final float targetScale = shouldDrawDot() ? 1f : 0f;
        if (animate) {
            animateDotScale();
        } else {
            mDotScale = targetScale;
            mAnimatingToDotScale = targetScale;
            invalidate();
        }
    }

    void updateBadgeVisibility() {
        if (mBubble instanceof BubbleBarOverflow) {
            // The overflow bubble does not have a badge, so just bail.
            return;
        }
        BubbleBarBubble bubble = (BubbleBarBubble) mBubble;
        Bitmap appBadgeBitmap = bubble.getBadge();
        int translationX = mOnLeft
                ? -(bubble.getIcon().getWidth() - appBadgeBitmap.getWidth())
                : 0;
        mAppIcon.setTranslationX(translationX);
        mAppIcon.setVisibility(isBehindStack() ? GONE : VISIBLE);
    }

    /** Sets whether this bubble is in the stack & not the first bubble. **/
    void setBehindStack(boolean behindStack, boolean animate) {
        if (behindStack) {
            mSuppressionFlags.add(SuppressionFlag.BEHIND_STACK);
        } else {
            mSuppressionFlags.remove(SuppressionFlag.BEHIND_STACK);
        }
        updateDotVisibility(animate);
        updateBadgeVisibility();
    }

    /** Whether this bubble is in the stack & not the first bubble. **/
    boolean isBehindStack() {
        return mSuppressionFlags.contains(SuppressionFlag.BEHIND_STACK);
    }

    /** Whether the dot indicating unseen content in a bubble should be shown. */
    private boolean shouldDrawDot() {
        boolean bubbleHasUnseenContent = mBubble != null
                && mBubble instanceof BubbleBarBubble
                && mSuppressionFlags.isEmpty()
                && !((BubbleBarBubble) mBubble).getInfo().isNotificationSuppressed();

        // Always render the dot if it's animating, since it could be animating out. Otherwise, show
        // it if the bubble wants to show it, and we aren't suppressing it.
        return bubbleHasUnseenContent || mDotIsAnimating;
    }

    /** How big the dot should be, fraction from 0 to 1. */
    private void setDotScale(float fraction) {
        mDotScale = fraction;
        invalidate();
    }

    /**
     * Animates the dot to the given scale.
     */
    private void animateDotScale() {
        float toScale = shouldDrawDot() ? 1f : 0f;
        mDotIsAnimating = true;

        // Don't restart the animation if we're already animating to the given value.
        if (mAnimatingToDotScale == toScale || !shouldDrawDot()) {
            mDotIsAnimating = false;
            return;
        }

        mAnimatingToDotScale = toScale;

        final boolean showDot = toScale > 0f;

        // Do NOT wait until after animation ends to setShowDot
        // to avoid overriding more recent showDot states.
        clearAnimation();
        animate()
                .setDuration(200)
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setUpdateListener((valueAnimator) -> {
                    float fraction = valueAnimator.getAnimatedFraction();
                    fraction = showDot ? fraction : 1f - fraction;
                    setDotScale(fraction);
                }).withEndAction(() -> {
                    setDotScale(showDot ? 1f : 0f);
                    mDotIsAnimating = false;
                }).start();
    }


    @Override
    public String toString() {
        String toString = mBubble != null ? mBubble.getKey() : "null";
        return "BubbleView{" + toString + "}";
    }
}
